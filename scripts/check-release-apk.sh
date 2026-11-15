#!/usr/bin/env bash
# Release APK guards beyond the permission allowlist (docs/handoff/SECURITY_AUDIT.md F6, F8):
# - the APK is not debuggable (run-as could read the database of a debuggable build);
# - its dex code does not reference sockets, HTTP, TLS, WebView or runtime class loaders.
#   Without INTERNET Android blocks network access anyway; this shows the code doesn't even try.
# Usage: scripts/check-release-apk.sh <release apk> [<release apk>...]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
if [[ $# -eq 0 ]]; then
    echo "usage: $0 <apk> [<apk>...]" >&2
    exit 2
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f "$root/local.properties" ]]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' "$root/local.properties")"
fi
build_tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)"
aapt2="$build_tools/aapt2"
[[ -x "$aapt2" ]] || { echo "error: aapt2 not found; set ANDROID_HOME" >&2; exit 2; }

forbidden='Ljava/net/(Socket|ServerSocket|HttpURLConnection|URLConnection|URL;|DatagramSocket)|Ljavax/net/|Landroid/webkit/|Ldalvik/system/(Dex|Path|InMemoryDex)ClassLoader'

status=0
for apk in "$@"; do
    [[ -f "$apk" ]] || { echo "error: no such APK: $apk" >&2; exit 2; }
    echo "$apk"
    if "$aapt2" dump badging "$apk" | grep -q '^application-debuggable'; then
        echo "  FAIL debuggable: a release APK must never be debuggable" >&2
        status=1
    else
        echo "  ok   not debuggable"
    fi
    hits="$(unzip -p "$apk" 'classes*.dex' | grep -aoE "$forbidden" | sort | uniq -c || true)"
    if [[ -n "$hits" ]]; then
        echo "  FAIL network or code-loading references in dex:" >&2
        echo "$hits" | sed 's/^/       /' >&2
        status=1
    else
        echo "  ok   no network, WebView or class-loader references"
    fi
done
exit "$status"
