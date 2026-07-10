# Contributing to ClearAuth

## Ground rules

1. **Never-deny invariant.** The engine and copilot recommend approve/pend only. PRs that introduce automated denial will not be merged.
2. **Evidence contract.** Any recommendation surfaced to a reviewer must cite evidence that is a verbatim substring of the submission or a structured diagnosis match. The eval harness enforces this in CI.
3. **Synthetic data only.** No PHI, ever — including in issues and test fixtures.

## Dev setup

```bash
# FHIR core (Java 17+)
cd services/fhir-core && mvn verify

# Review copilot (Python 3.10+)
cd services/review-copilot
pip install -r requirements-dev.txt
python -m pytest tests/ -q
python eval/run_eval.py

# Full stack
docker compose up --build
```

## Pull requests

- Keep the two CI jobs green (`fhir-core`, `review-copilot`).
- Add eval cases when you touch matching logic; accuracy threshold is 90%.
- New policies go in `services/review-copilot/app/policies/*.md` (frontmatter + human-readable body) and, if they gate a procedure code, in `services/fhir-core/src/main/resources/criteria/policies.yaml`.
