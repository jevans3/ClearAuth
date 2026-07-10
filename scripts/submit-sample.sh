#!/usr/bin/env bash
# Submit a prior authorization request bundle to ClearAuth.
# Usage: ./scripts/submit-sample.sh [bundle.json]
set -euo pipefail

BUNDLE="${1:-samples/pa-request-approve.json}"
BASE_URL="${CLEARAUTH_URL:-http://localhost:8080}"

curl -s -X POST "$BASE_URL/fhir/Claim/\$submit" \
  -H "Content-Type: application/fhir+json" \
  --data @"$BUNDLE" | jq .
