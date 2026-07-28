#!/bin/bash

echo "================================================"
echo "  CryptoCarver - MODERN UI"
echo "================================================"
echo ""
echo "Rebuilding and launching modern UI..."
MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
if [ -z "$MAVEN_BIN" ] && [ -x /opt/homebrew/bin/mvn ]; then MAVEN_BIN=/opt/homebrew/bin/mvn; fi
if [ -z "$MAVEN_BIN" ]; then echo "Maven was not found. Set MAVEN_BIN or add mvn to PATH." >&2; exit 127; fi

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
