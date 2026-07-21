#!/usr/bin/env bash
set -euo pipefail

# Produces the two platform-independent release artifacts and their checksums.
# Platform installers are built afterwards with package_macos.sh or
# package_windows.bat and can be added to the same manifest explicitly.

MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
if [ -z "$MAVEN_BIN" ] && [ -x /opt/homebrew/bin/mvn ]; then
  MAVEN_BIN=/opt/homebrew/bin/mvn
fi
if [ -z "$MAVEN_BIN" ]; then
  echo "Maven was not found. Set MAVEN_BIN or add mvn to PATH." >&2
  exit 1
fi

RELEASE_CHANNEL="${RELEASE_CHANNEL:-laboratory}"
case "$RELEASE_CHANNEL" in
  laboratory) RELEASE_PROFILES="release-artifacts" ;;
  stable) RELEASE_PROFILES="release-artifacts,release-stable" ;;
  experimental) RELEASE_PROFILES="release-artifacts,release-experimental" ;;
  *)
    echo "RELEASE_CHANNEL must be laboratory, stable or experimental." >&2
    exit 2
    ;;
esac

"$MAVEN_BIN" -P"$RELEASE_PROFILES" clean package
VERSION="$("$MAVEN_BIN" -q -DforceStdout help:evaluate -Dexpression=project.version)"
if [ -z "$VERSION" ]; then
  echo "Unable to resolve the Maven project version." >&2
  exit 1
fi

bash scripts/create-release-manifest.sh "$VERSION" "$RELEASE_CHANNEL" \
  "target/cryptocarver-${VERSION}.jar" \
  target/cryptocarver-sbom.json

echo "Release deliverables: dist/release-${VERSION} (channel: ${RELEASE_CHANNEL})"
