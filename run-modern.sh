#!/usr/bin/env bash
set -euo pipefail

echo "================================================"
echo "  CryptoCarver - MODERN UI"
echo "================================================"
echo ""
echo "Rebuilding and launching modern UI..."
cd "$(dirname "$0")"
source scripts/toolchain.sh
find_java
find_maven

# Finder can recreate target/.DS_Store while maven-clean-plugin is deleting
# target, making an otherwise valid rebuild fail with "Failed to delete target".
# Moving the complete build directory first gives Maven a clean classpath
# without racing Finder's metadata writer.
# Keep the staging directory beside target. Moving it into $TMPDIR can cross
# filesystems on macOS and silently become a slow recursive copy.
BUILD_STAGING_DIR="$(mktemp -d "$PWD/.cryptocarver-build.XXXXXX")" || exit 1
cleanup_staged_build() {
    rm -rf "$BUILD_STAGING_DIR"
}
trap cleanup_staged_build EXIT INT TERM

if [ -e target ]; then
    echo "[INFO] Staging previous build output..."
    if ! mv target "$BUILD_STAGING_DIR/target"; then
        echo "Could not stage the previous target directory for a clean rebuild." >&2
        exit 1
    fi
fi

echo "[INFO] Compiling and starting JavaFX (tests are skipped for launch)..."
"$MAVEN_BIN" -DskipTests compile javafx:run
LAUNCH_STATUS=$?
if [ "$LAUNCH_STATUS" -ne 0 ]; then
    echo "[ERROR] CryptoCarver did not start (Maven exit code: $LAUNCH_STATUS)." >&2
fi
exit "$LAUNCH_STATUS"
