#!/usr/bin/env bash
# Build and flash the firmware, optionally opening the serial monitor
#   scripts/flash-firmware.sh [-t TARGET] [-p PORT] [--monitor]
# PORT defaults to $ESPPORT, otherwise idf.py autodetects it
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

PORT="${ESPPORT:-}"
MONITOR=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -t|--target) TARGET="${2:?missing target}"; shift 2 ;;
        -p|--port) PORT="${2:?missing port}"; shift 2 ;;
        -m|--monitor) MONITOR=(monitor); shift ;;
        -h|--help) sed -n '2,4p' "$0"; exit 0 ;;
        *) die "unknown argument: $1" ;;
    esac
done

ensure_idf
PORT_ARGS=()
[[ -n "$PORT" ]] && PORT_ARGS=(-p "$PORT")
log "Flashing firmware for $TARGET${PORT:+ to $PORT}"
idf "${PORT_ARGS[@]+"${PORT_ARGS[@]}"}" flash "${MONITOR[@]+"${MONITOR[@]}"}"
