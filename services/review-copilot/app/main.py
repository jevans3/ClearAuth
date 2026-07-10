"""ClearAuth Review Copilot — evidence-cited prior-auth review recommendations."""

from __future__ import annotations

from dataclasses import asdict

from fastapi import FastAPI
from pydantic import BaseModel, Field

from .engine import ReviewEngine
from .llm import llm_available

app = FastAPI(
    title="ClearAuth Review Copilot",
    description="Recommends approve/pend for prior authorization requests with "
                "evidence cited from the clinical note and the governing policy. "
                "Never recommends denial — that decision is reserved for humans.",
    version="0.2.0",
)

engine = ReviewEngine()


class ReviewRequest(BaseModel):
    procedure_code: str | None = Field(None, examples=["70551"])
    diagnosis_codes: list[str] = Field(default_factory=list, examples=[["G43.909"]])
    clinical_note: str = ""


@app.get("/health")
def health():
    return {
        "status": "ok",
        "policies_loaded": len(engine.policies),
        "llm_enabled": llm_available(),
    }


@app.post("/review")
def review(request: ReviewRequest):
    recommendation = engine.review(
        procedure_code=request.procedure_code,
        diagnosis_codes=request.diagnosis_codes,
        clinical_note=request.clinical_note,
    )
    return asdict(recommendation)


@app.get("/policies/search")
def search_policies(q: str, top_k: int = 3):
    hits = engine.index.search(q, top_k=top_k)
    return [
        {"policy_id": pid, "score": round(score, 3), "title": engine.by_id[pid].title}
        for pid, score in hits
    ]
