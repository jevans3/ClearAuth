from fastapi.testclient import TestClient

from app.engine import ReviewEngine
from app.main import app

client = TestClient(app)
engine = ReviewEngine()


def test_mri_brain_approved_with_dx_and_conservative_therapy():
    rec = engine.review(
        procedure_code="70551",
        diagnosis_codes=["G43.909"],
        clinical_note="Patient with chronic migraine. Failed medication trial of "
                      "topiramate and propranolol over 8 weeks.",
    )
    assert rec.recommendation == "approve"
    assert rec.policy_id == "MP-IMG-001"
    assert all(c.met for c in rec.criteria)


def test_mri_brain_pended_without_note_evidence():
    rec = engine.review(procedure_code="70551", diagnosis_codes=["G43.909"], clinical_note="")
    assert rec.recommendation == "pend"
    unmet = [c for c in rec.criteria if not c.met]
    assert [c.id for c in unmet] == ["C2"]


def test_never_denies():
    rec = engine.review(procedure_code="70551", diagnosis_codes=[], clinical_note="")
    assert rec.recommendation in {"approve", "pend"}


def test_unknown_code_routes_to_pend():
    rec = engine.review(procedure_code="99999", diagnosis_codes=["Z00.00"])
    assert rec.recommendation == "pend"
    assert rec.policy_id is None


def test_evidence_is_verbatim_substring_of_note():
    note = ("Severe knee pain. X-ray shows bone-on-bone contact medially. "
            "Completed 3 months of physical therapy and corticosteroid injection "
            "without relief.")
    rec = engine.review(procedure_code="27447", diagnosis_codes=["M17.11"], clinical_note=note)
    assert rec.recommendation == "approve"
    for criterion in rec.criteria:
        if criterion.source == "keyword":
            assert criterion.evidence in note


def test_bm25_routes_free_text_without_code():
    rec = engine.review(
        procedure_code=None,
        diagnosis_codes=["M17.11"],
        clinical_note="Requesting total knee arthroplasty; severe osteoarthritis, "
                      "bone-on-bone, failed conservative management over 3 months.",
    )
    assert rec.policy_id == "MP-ORT-012"
    assert rec.recommendation == "approve"


def test_api_review_endpoint():
    response = client.post("/review", json={
        "procedure_code": "72148",
        "diagnosis_codes": ["M54.16"],
        "clinical_note": "Radiculopathy. 6 weeks of physical therapy and NSAIDs without improvement.",
    })
    assert response.status_code == 200
    body = response.json()
    assert body["recommendation"] == "approve"
    assert body["policy_id"] == "MP-IMG-002"


def test_api_health_and_policy_search():
    assert client.get("/health").json()["policies_loaded"] >= 3
    hits = client.get("/policies/search", params={"q": "knee replacement osteoarthritis"}).json()
    assert hits and hits[0]["policy_id"] == "MP-ORT-012"
