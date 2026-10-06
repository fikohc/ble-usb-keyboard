# Shared helpers, sourced by the other scripts. Nothing outside the repo is
# looked up unless pointed to by an env variable or argument.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$ROOT/dist"
FIRMWARE="$ROOT/firmware"
APP="$ROOT/app"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# Make idf.py available: already on PATH, or $IDF_EXPORT, or $IDF_PATH/export.sh
ensure_idf() {
    if command -v idf.py >/dev/null 2>&1; then
        return
    fi
    local script=""
    if [[ -n "${IDF_EXPORT:-}" ]]; then
        script="$IDF_EXPORT"
    elif [[ -n "${IDF_PATH:-}" ]]; then
        script="$IDF_PATH/export.sh"
    else
        die "idf.py not found: activate ESP-IDF, or set IDF_EXPORT (activate/export script) or IDF_PATH"
    fi
    [[ -f "$script" ]] || die "ESP-IDF export script not found: $script"
    log "Activating ESP-IDF from $script"

    # Source it in an interactive-looking child ($0 = bash; EIM activators refuse
    # to run otherwise) and import the resulting environment
    local dump name value
    # shellcheck disable=SC2016
    dump="$(bash -c 'source "$1" >/dev/null 2>&1 || exit 1
        for v in $(compgen -e); do printf "%s=%s\0" "$v" "${!v}"; done' bash "$script" | tr '\0' '\1')" \
        || die "sourcing $script failed"
    while IFS= read -r -d $'\1' pair; do
        name="${pair%%=*}"
        value="${pair#*=}"
        case "$name" in SHLVL|_|PWD|OLDPWD) continue ;; esac
        export "$name=$value"
    done <<<"$dump"

    if ! command -v idf.py >/dev/null 2>&1; then
        # EIM activators define idf.py as a shell function, which doesn't survive the
        # import; the venv python is on PATH by now
        [[ -f "${IDF_PATH:-}/tools/idf.py" ]] || die "idf.py not available after sourcing $script"
        idf.py() { python3 "$IDF_PATH/tools/idf.py" "$@"; }
    fi
}

# idf.py for one chip: separate build dir, sdkconfig and dependency lock per target,
# so switching targets never touches firmware/sdkconfig, firmware/build or dependencies.lock
idf() {
    local build="$FIRMWARE/build-$TARGET"
    mkdir -p "$build"
    # Start from the committed lock to get the same component versions
    [[ -f "$build/dependencies.lock" ]] || cp "$FIRMWARE/dependencies.lock" "$build/"
    idf.py -C "$FIRMWARE" -B "$build" \
        -D IDF_TARGET="$TARGET" -D SDKCONFIG="$build/sdkconfig" \
        -D ESPKBD_DEPENDENCIES_LOCK="$build/dependencies.lock" "$@"
}

TARGET="${IDF_TARGET:-esp32s3}"

gradle() {
    (cd "$APP" && ./gradlew "$@")
}

# Copy build outputs matching a glob into dist/, optionally renaming a single file
collect() {
    local dest="$1"
    shift
    mkdir -p "$DIST"
    [[ $# -gt 0 && -e "$1" ]] || die "no build output found"
    if [[ -n "$dest" ]]; then
        cp "$1" "$DIST/$dest"
        log "Output: dist/$dest"
    else
        cp "$@" "$DIST/"
        for f in "$@"; do log "Output: dist/$(basename "$f")"; done
    fi
}
