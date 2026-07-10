# Security Policy

## Data

ClearAuth ships with **synthetic data only**. Nothing in this repository is PHI.
If you deploy it against real member data you are the HIPAA covered entity /
business associate — the checklist below is the minimum.

## Production deployment checklist

- **Transport:** terminate TLS in front of both services (reverse proxy or mesh); never expose 8080/8090 directly.
- **AuthN/Z:** set `CLEARAUTH_APIKEY` as a baseline gate, and put a real OAuth2 / SMART-on-FHIR authorization server in front of `/fhir/**` for provider-facing access. The bundled API-key filter is a shared-environment baseline, not an identity system.
- **Database:** use `docker-compose.prod.yml` (PostgreSQL) with a strong `DB_PASSWORD`; H2 is for demos and tests only.
- **Secrets:** environment variables only (`.env` is gitignored); never commit keys. Rotate any `ANTHROPIC_API_KEY` used by the copilot.
- **Audit:** the decision log (`pa_request` table) is append-oriented; back it up — CMS-0057-F metrics reporting and RADV-style audits depend on it.
- **LLM boundary:** the copilot sends the clinical note to the configured LLM provider when `ANTHROPIC_API_KEY` is set. Do not enable it with real PHI unless you have a BAA with the provider. Unset the key for a fully local, deterministic pipeline.
- **Never-deny invariant:** no code path may auto-deny. Treat any change that introduces an automated denial as a breaking, reviewable design change.

## Reporting a vulnerability

Open a private security advisory on GitHub or email the maintainer. Please do
not open public issues for exploitable problems.
