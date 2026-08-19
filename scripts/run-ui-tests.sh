#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

if ! command -v xvfb-run >/dev/null 2>&1; then
  echo "[ui-tests] xvfb-run is required; install Xvfb on the Linux runner." >&2
  exit 2
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "[ui-tests] mvn is required; install Maven or use a Maven-enabled runner." >&2
  exit 2
fi

export LIBGL_ALWAYS_SOFTWARE=1
XVFB_SERVER_ARGS="${XVFB_SERVER_ARGS:--screen 0 1920x1080x24}"
# UI tests must not open modal JavaFX dialogs: they block the FX thread and
# make the suite depend on manual interaction. Controllers expose test.mode
# specifically to emit non-modal diagnostics instead.
MAVEN_ARGS=(-q -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw test)
if (($# > 0)); then
  MAVEN_ARGS+=("$@")
fi

echo "[ui-tests] running opt-in JavaFX suite with Xvfb"
exec xvfb-run --auto-servernum --server-args="$XVFB_SERVER_ARGS" mvn "${MAVEN_ARGS[@]}"
