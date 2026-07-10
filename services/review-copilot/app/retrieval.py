"""BM25 retrieval over the policy corpus, implemented from scratch.

Procedure-code lookup is the primary router; BM25 is the fallback when a
request arrives without a recognized code (e.g., free-text referrals), and
it powers the /policies/search endpoint.
"""

from __future__ import annotations

import math
import re
from collections import Counter
from dataclasses import dataclass, field

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def tokenize(text: str) -> list[str]:
    return _TOKEN_RE.findall(text.lower())


@dataclass
class Bm25Index:
    k1: float = 1.5
    b: float = 0.75
    _docs: list[list[str]] = field(default_factory=list)
    _doc_ids: list[str] = field(default_factory=list)
    _df: Counter = field(default_factory=Counter)

    def add(self, doc_id: str, text: str) -> None:
        tokens = tokenize(text)
        self._docs.append(tokens)
        self._doc_ids.append(doc_id)
        for term in set(tokens):
            self._df[term] += 1

    def _idf(self, term: str) -> float:
        n, df = len(self._docs), self._df.get(term, 0)
        return math.log((n - df + 0.5) / (df + 0.5) + 1.0)

    def search(self, query: str, top_k: int = 3) -> list[tuple[str, float]]:
        if not self._docs:
            return []
        query_terms = tokenize(query)
        avgdl = sum(len(d) for d in self._docs) / len(self._docs)
        scores: list[tuple[str, float]] = []
        for doc_id, doc in zip(self._doc_ids, self._docs):
            tf = Counter(doc)
            score = 0.0
            for term in query_terms:
                if term not in tf:
                    continue
                idf = self._idf(term)
                numerator = tf[term] * (self.k1 + 1)
                denominator = tf[term] + self.k1 * (1 - self.b + self.b * len(doc) / avgdl)
                score += idf * numerator / denominator
            scores.append((doc_id, score))
        scores.sort(key=lambda pair: pair[1], reverse=True)
        return [(doc_id, score) for doc_id, score in scores[:top_k] if score > 0]
