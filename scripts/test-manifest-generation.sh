#!/usr/bin/env bash
set -euo pipefail

echo "Running release manifest test..."

TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT

touch "$TEST_DIR/cryptocarver-9.9.9.jar"
touch "$TEST_DIR/cryptocarver-sbom.json"

# Call manifest creator using the temporary directory as output root
RELEASE_OUTPUT_ROOT="$TEST_DIR" bash scripts/create-release-manifest.sh "9.9.9" "experimental" "$TEST_DIR/cryptocarver-9.9.9.jar" "$TEST_DIR/cryptocarver-sbom.json" > /dev/null

RELEASE_DIR="$TEST_DIR/release-9.9.9"
if [ ! -d "$RELEASE_DIR" ]; then
    echo "Error: Release directory not created."
    exit 1
fi

if [ ! -f "$RELEASE_DIR/SHA256SUMS" ]; then
    echo "Error: SHA256SUMS not created."
    exit 1
fi

if [ ! -f "$RELEASE_DIR/RELEASE_MANIFEST.md" ]; then
    echo "Error: RELEASE_MANIFEST.md not created."
    exit 1
fi

if ! grep -q "Version: \`9.9.9\`" "$RELEASE_DIR/RELEASE_MANIFEST.md"; then
    echo "Error: Version missing from manifest."
    exit 1
fi

if ! grep -q "Channel: \`experimental\`" "$RELEASE_DIR/RELEASE_MANIFEST.md"; then
    echo "Error: Channel missing from manifest."
    exit 1
fi

if ! grep -q "cryptocarver-9.9.9.jar" "$RELEASE_DIR/SHA256SUMS"; then
    echo "Error: JAR checksum missing."
    exit 1
fi

(cd "$RELEASE_DIR" && shasum -a 256 -c SHA256SUMS > /dev/null 2>&1)
if [ $? -ne 0 ]; then
    echo "Error: shasum verification failed."
    exit 1
fi

# Clean up
rm -rf "$RELEASE_DIR"
echo "Manifest generation tests passed!"
