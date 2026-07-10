"""Clinical review engine: policy loading, criteria matching, recommendation.

Pipeline: route to policy (procedure code, BM25 fallback) -> evaluate each
criterion against structured diagnoses and the clinical note -> emit an
evidence-cited recommendation.

Stance: the engine recommends APPROVE or PEND. It never denies — denial is
reserved for a licensed human reviewer.

Evidence discipline (the anti-hallucination contract): every citation in the
output is a verbatim substring of either the request's clinical note or the
policy text. The eval harness enforces this.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

import yaml

from .llm import judge_criterion
from .retrieval import Bm25Index

POLICY_DIR = Path(__file__).parent / "policies"


@dataclass
class Criterion:
    id: str
    text: str
    kind: str  # diagnosis_prefix | note_keywords
    values: list[str]


@dataclass
class Policy:
    policy_id: str
    title: str
    procedure_codes: list[str]
    criteria: list[Criterion]
    body: str


@dataclass
class CriterionResult:
    id: str
    text: str
    met: bool
    evidence: str | None
    source: str | None = None  # keyword | diagnosis | llm


@dataclass
class Recommendation:
    recommendation: str  # approve | pend
    policy_id: str | None
    policy_title: str | None
    criteria: list[CriterionResult]
    rationale: str


def load_policies(policy_dir: Path = POLICY_DIR) -> list[Policy]:
    policies = []
    for path in sorted(policy_dir.glob("*.md")):
        raw = path.read_text(encoding="utf-8")
        match = re.match(r"^---\n(.*?)\n---\n(.*)$", raw, re.DOTALL)
        if not match:
            raise ValueError(f"Policy {path.name} is missing YAML frontmatter")
        meta = yaml.safe_load(match.group(1))
        criteria = [Criterion(**c) for c in meta["criteria"]]
        policies.append(Policy(
            policy_id=meta["policy_id"],
            title=meta["title"],
            procedure_codes=[str(c) for c in meta["procedure_codes"]],
            criteria=criteria,
            body=match.group(2).strip(),
        ))
    return policies


class ReviewEngine:

    def __init__(self, policy_dir: Path = POLICY_DIR):
        self.policies = load_policies(policy_dir)
        self.by_code: dict[str, Policy] = {
            code: policy for policy in self.policies for code in policy.procedure_codes
        }
        self.index = Bm25Index()
        for policy in self.policies:
            self.index.add(policy.policy_id, f"{policy.title}\n{policy.body}")
        self.by_id = {policy.policy_id: policy for policy in self.policies}

    def route(self, procedure_code: str | None, free_text: str = "") -> Policy | None:
        if procedure_code and procedure_code in self.by_code:
            return self.by_code[procedure_code]
        if free_text:
            hits = self.index.search(free_text, top_k=1)
            if hits:
                return self.by_id[hits[0][0]]
        return None

    def review(self, procedure_code: str | None, diagnosis_codes: list[str],
               clinical_note: str = "") -> Recommendation:
        policy = self.route(procedure_code, clinical_note)
        if policy is None:
            return Recommendation(
                recommendation="pend",
                policy_id=None,
                policy_title=None,
                criteria=[],
                rationale=f"No policy on file for procedure code {procedure_code!r}; "
                          "routed to human clinical review.",
            )

        results = [self._evaluate(c, diagnosis_codes, clinical_note) for c in policy.criteria]
        all_met = all(r.met for r in results)
        unmet = [r.id for r in results if not r.met]
        rationale = (
            f"All {len(results)} criteria of policy {policy.policy_id} are met; recommend approval."
            if all_met else
            f"Criteria {', '.join(unmet)} of policy {policy.policy_id} are not evidenced in the "
            "submission; recommend pending for human clinical review (never auto-deny)."
        )
        return Recommendation(
            recommendation="approve" if all_met else "pend",
            policy_id=policy.policy_id,
            policy_title=policy.title,
            criteria=results,
            rationale=rationale,
        )

    def _evaluate(self, criterion: Criterion, diagnosis_codes: list[str],
                  clinical_note: str) -> CriterionResult:
        if criterion.kind == "diagnosis_prefix":
            for dx in diagnosis_codes:
                for prefix in criterion.values:
                    if dx.upper().startswith(prefix.upper()):
                        return CriterionResult(criterion.id, criterion.text, True,
                                               f"diagnosis {dx} matches prefix {prefix}",
                                               source="diagnosis")
            return CriterionResult(criterion.id, criterion.text, False, None)

        if criterion.kind == "note_keywords":
            note_lower = clinical_note.lower()
            for keyword in criterion.values:
                pos = note_lower.find(keyword.lower())
                if pos >= 0:
                    evidence = self._sentence_around(clinical_note, pos)
                    return CriterionResult(criterion.id, criterion.text, True, evidence,
                                           source="keyword")
            # Keyword miss: let the LLM judge paraphrases. Its evidence quote is
            # verified as a verbatim substring of the note (see llm.py); on any
            # failure we keep the deterministic "not met" result.
            llm_judgment = judge_criterion(criterion.text, clinical_note)
            if llm_judgment is not None:
                met, quote = llm_judgment
                if met:
                    return CriterionResult(criterion.id, criterion.text, True, quote,
                                           source="llm")
            return CriterionResult(criterion.id, criterion.text, False, None)

        raise ValueError(f"Unknown criterion kind: {criterion.kind}")

    @staticmethod
    def _sentence_around(text: str, pos: int) -> str:
        """Return the verbatim sentence containing pos — citations must be real substrings."""
        start = max(text.rfind(".", 0, pos), text.rfind("\n", 0, pos)) + 1
        end_dot = text.find(".", pos)
        end_nl = text.find("\n", pos)
        candidates = [e for e in (end_dot, end_nl) if e != -1]
        end = min(candidates) + 1 if candidates else len(text)
        return text[start:end].strip()
