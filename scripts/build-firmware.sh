#!/usr/bin/env bash
# Build the ESP32 firmware and put a merged image (flash at 0x0) into dist/
#   scripts/build-firmware.sh [-t TARGET]
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

while [[ $# -gt 0 ]]; do
    case "$1" in
        -t|--target) TARGET="${2:?missing target}"; shift 2 ;;
        -h|--help) sed -n '2,3p' "$0"; exit 0 ;;
        *) die "unknown argument: $1" ;;
    esac
done

ensure_idf
log "Building firmware for $TARGET"
idf build
idf merge-bin -o "espkbd-firmware-$TARGET.bin"
collect "espkbd-firmware-$TARGET.bin" "$FIRMWARE/build-$TARGET/espkbd-firmware-$TARGET.bin"
