#!/usr/bin/env bash
# Build the Android APK into dist/
#   scripts/build-apk.sh [debug|release]
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TYPE="${1:-debug}"
case "$TYPE" in
    debug) TASK=assembleDebug ;;
    release) TASK=assembleRelease ;;
    -h|--help) sed -n '2,3p' "$0"; exit 0 ;;
    *) die "unknown build type: $TYPE (debug|release)" ;;
esac

log "Building $TYPE APK"
gradle ":androidApp:$TASK"
[[ "$TYPE" == release ]] && log "Note: no signing config, the release APK is unsigned"
collect "espkbd-$TYPE.apk" "$APP"/androidApp/build/outputs/apk/"$TYPE"/*.apk
