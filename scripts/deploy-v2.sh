#!/usr/bin/env bash
# Deploys worker build v2 (PEP check on) while applications are in flight.
# Usage: scripts/deploy-v2.sh        deploy v2
#        scripts/deploy-v2.sh v1     back to v1, for rehearsals (run make reset first)
# Also used by make restart-worker.
#
# LOS_FEATURE_PEP only decides which path new runs take.
# Workflow.getVersion in VerificationWorkflowImpl keeps runs that started on v1 on v1.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-v2}"
PEP=$([ "$VERSION" = "v2" ] && echo true || echo false)
SESSION=fusion-los-worker

make -s build

echo "Stopping the current worker"
pkill -TERM -f 'profiles.active=[w]orker' || true
for _ in $(seq 1 20); do
  pgrep -f 'profiles.active=[w]orker' >/dev/null || break
  sleep 0.5
done
# Never run old and new builds side by side on the task queue.
pkill -KILL -f 'profiles.active=[w]orker' || true
tmux kill-session -t "$SESSION" 2>/dev/null || true

echo "Starting worker $VERSION in tmux session $SESSION"
tmux new-session -d -s "$SESSION" -c "$PWD" "LOS_FEATURE_PEP=$PEP make worker; exec ${SHELL:-sh}"

for _ in $(seq 1 60); do
  if tmux capture-pane -t "$SESSION" -p | grep -q "Worker $VERSION polling"; then
    echo "Worker $VERSION is polling"
    exit 0
  fi
  sleep 1
done
echo "Worker did not start within 60s; check: tmux attach -t $SESSION" >&2
exit 1
