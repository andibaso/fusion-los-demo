#!/usr/bin/env bash
# Saves the event histories of one application (parent and stage children) into histories/
# for ReplayTest. Usage: scripts/export-history.sh APP-2026-000123 v1
set -euo pipefail
cd "$(dirname "$0")/.."

ID="${1:?application id}"
LABEL="${2:-run}"
mkdir -p histories

for WF in "$ID" "$ID-intake" "$ID-verification" "$ID-underwriting" "$ID-offer" "$ID-disbursement"; do
  OUT="histories/$LABEL-$WF.json"
  if docker compose exec -T temporal temporal workflow show --workflow-id "$WF" --output json > "$OUT" 2>/dev/null; then
    echo "saved $OUT"
  else
    rm -f "$OUT"
  fi
done
