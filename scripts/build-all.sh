#!/usr/bin/env bash
# Run tests, then build firmware, APK and the desktop package for this OS into dist/
#   scripts/build-all.sh [-t TARGET] [--skip-tests]
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

SCRIPTS="$ROOT/scripts"
SKIP_TESTS=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        -t|--target) TARGET="${2:?missing target}"; shift 2 ;;
        --skip-tests) SKIP_TESTS=1; shift ;;
        -h|--help) sed -n '2,3p' "$0"; exit 0 ;;
        *) die "unknown argument: $1" ;;
    esac
done

if [[ $SKIP_TESTS -eq 0 ]]; then
    "$SCRIPTS/test-firmware.sh"
    "$SCRIPTS/test-app.sh"
fi
"$SCRIPTS/build-firmware.sh" -t "$TARGET"
"$SCRIPTS/build-apk.sh"
"$SCRIPTS/build-desktop.sh"

log "Done:"
ls -1 "$DIST"
