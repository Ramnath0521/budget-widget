#!/usr/bin/env bash
# The app never logs SMS text, amounts or merchants (PRIVACY.md, docs/handoff/SECURITY_AUDIT.md F7).
# The simplest guarantee: no android.util.Log, println or printStackTrace in app code, except the
# files listed here after review. Tests and the unmodified upstream parser are not checked.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

# Reviewed: logs only class names.
allowed='^\./core/data/src/main/kotlin/io/github/swashbuckler104/budgetwidget/core/data/inbox/InboxEvents\.kt:'

hits="$(grep -rnE '\b(Log\.(v|d|i|w|e|wtf)\(|println\(|printStackTrace\(|System\.(out|err)\.)' --include='*.kt' . \
    --exclude-dir=third_party --exclude-dir=build --exclude-dir=.gradle --exclude-dir=test \
    --exclude-dir=androidTest --exclude-dir=parser-core --exclude-dir=build-logic --exclude-dir=baselineprofile \
    | grep -vE "$allowed" || true)"

if [[ -n "$hits" ]]; then
    echo "FAIL: logging in app code (review it, then add the file to the allowlist in $0):" >&2
    echo "$hits" >&2
    exit 1
fi
echo "ok: no logging in app code outside the reviewed allowlist"
