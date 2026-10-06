#!/usr/bin/env bash
# Build the desktop app package into dist/
#   scripts/build-desktop.sh [dmg|msi|deb]
# Default depends on the OS: macOS -> dmg, Linux -> deb, Windows -> msi.
# Packages can only be built on their own OS.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

FORMAT="${1:-}"
if [[ "$FORMAT" == -h || "$FORMAT" == --help ]]; then
    sed -n '2,5p' "$0"
    exit 0
fi
if [[ -z "$FORMAT" ]]; then
    case "$(uname -s)" in
        Darwin) FORMAT=dmg ;;
        Linux) FORMAT=deb ;;
        MINGW*|MSYS*|CYGWIN*) FORMAT=msi ;;
        *) die "unknown OS $(uname -s), pass the format explicitly" ;;
    esac
fi
case "$FORMAT" in
    dmg) TASK=packageDmg ;;
    msi) TASK=packageMsi ;;
    deb) TASK=packageDeb ;;
    *) die "unknown format: $FORMAT (dmg|msi|deb)" ;;
esac

log "Building desktop $FORMAT"
gradle ":desktopApp:$TASK"
collect "" "$APP"/desktopApp/build/compose/binaries/main/"$FORMAT"/*."$FORMAT"
