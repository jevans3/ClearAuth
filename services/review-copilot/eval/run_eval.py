#!/usr/bin/env python3
"""Eval harness for the review engine.

Measures recommendation quality against labeled synthetic cases and enforces
the evidence contract (every citation must be a verbatim substring of the
clinical note, or a structured diagnosis-match statement).

Exits non-zero if accuracy < threshold or any evidence check fails, so CI
gates on model/rule regressions.

Usage: python -m eval.run_eval [--threshold 0.9]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.engine import ReviewEngine  # noqa: E402

CASES = Path(__file__).parent / "cases.json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--threshold", type=float, default=0.9)
    args = parser.parse_args()

    engine = ReviewEngine()
    cases = json.loads(CASES.read_text())

    correct = 0
    evidence_violations: list[str] = []
    confusion = {"approve": {"approve": 0, "pend": 0}, "pend": {"approve": 0, "pend": 0}}
    failures: list[str] = []

    for case in cases:
        request = case["request"]
        rec = engine.review(
            procedure_code=request["procedure_code"],
            diagnosis_codes=request["diagnosis_codes"],
            clinical_note=request.get("clinical_note", ""),
        )
        expected, got = case["expected"], rec.recommendation
        confusion[expected][got] += 1
        if got == expected:
            correct += 1
        else:
            failures.append(f"  {case['id']}: expected {expected}, got {got} — {rec.rationale}")

        note = request.get("clinical_note", "")
        for criterion in rec.criteria:
            if criterion.met and criterion.source in {"keyword", "llm"}:
                if criterion.evidence not in note:
                    evidence_violations.append(f"  {case['id']}/{criterion.id}: {criterion.evidence!r}")

    total = len(cases)
    accuracy = correct / total
    tp = confusion["approve"]["approve"]
    fp = confusion["pend"]["approve"]
    fn = confusion["approve"]["pend"]
    precision = tp / (tp + fp) if tp + fp else 1.0
    recall = tp / (tp + fn) if tp + fn else 1.0

    print(f"cases: {total}")
    print(f"accuracy: {accuracy:.1%}")
    print(f"approve precision: {precision:.1%}  (false approvals are the metric that matters)")
    print(f"approve recall: {recall:.1%}")
    print(f"evidence contract violations: {len(evidence_violations)}")
    if failures:
        print("misclassifications:")
        print("\n".join(failures))
    if evidence_violations:
        print("evidence violations:")
        print("\n".join(evidence_violations))

    if evidence_violations:
        return 2
    if accuracy < args.threshold:
        print(f"FAIL: accuracy below threshold {args.threshold:.0%}")
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
