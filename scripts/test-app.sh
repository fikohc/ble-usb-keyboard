#!/usr/bin/env bash
# Run the app's shared unit tests on the JVM
#   scripts/test-app.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

log "Running app tests"
gradle :shared:jvmTest
