#!/usr/bin/env bash
# Run the firmware host unit tests (keymap, kbd)
#   scripts/test-firmware.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

ensure_idf
[[ -n "${IDF_PATH:-}" ]] || die "IDF_PATH is not set (needed for Unity)"

# TinyUSB headers come from managed_components, fetched on configure
if [[ ! -d "$FIRMWARE/managed_components" ]]; then
    log "Fetching managed components"
    idf reconfigure
fi

log "Running firmware host tests"
make -C "$FIRMWARE/test/host" test IDF_PATH="$IDF_PATH"
