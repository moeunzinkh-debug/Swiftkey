#!/usr/bin/env bash
# CI wrapper around backmerge-main-to-dev.sh.
#
# Why this exists: the raw Actions log lives on a blob host that is not reachable through
# the REST API, so when the back-merge step failed (release v1.5.0 run 34983639229 and
# release v1.6.0 run 35048531358) all that was visible programmatically was
# "Process completed with exit code 1". This wrapper mirrors the script's output into a
# temp file and, on failure, re-emits its tail as ONE ::error annotation — which IS
# readable from the check-runs API and shows up on the run page.
#
# Usage: backmerge-ci.sh   (same environment variables as backmerge-main-to-dev.sh)
set -uo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly backmerge_script="$script_dir/backmerge-main-to-dev.sh"

log_file="$(mktemp "${TMPDIR:-/tmp}/backmerge-log-XXXXXX")"
trap 'rm -f -- "$log_file"' EXIT

bash "$backmerge_script" 2>&1 | tee "$log_file"
status=${PIPESTATUS[0]}

if (( status != 0 )); then
  echo "Back-merge exited with ${status}." >&2
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    # Workflow-command escaping: % -> %25, newlines -> %0A. Keeping it to one annotation
    # (instead of one per line) keeps the run page readable.
    detail="$(tail -n 40 "$log_file" | sed -e 's/%/%25/g' -e 's/$/%0A/' | tr -d '\n')"
    echo "::error title=back-merge main into dev failed (exit ${status})::${detail}"
  fi
fi

exit "$status"
