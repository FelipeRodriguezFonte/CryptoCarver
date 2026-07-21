#!/usr/bin/env bash
set -euo pipefail

# Creates a self-contained Linux app-image by default. Set PACKAGE_TYPE=deb or
# PACKAGE_TYPE=rpm on a matching distribution to build an installer package.
APP_NAME="CryptoCarver"
MAIN_CLASS="com.cryptoforge.Launcher"
OUTPUT_DIR="${PACKAGE_OUTPUT_DIR:-dist}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"
ICON_PATH="src/main/resources/icons/app-icon.png"

MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
if [ -z "$MAVEN_BIN" ] && [ -x /opt/homebrew/bin/mvn ]; then
  MAVEN_BIN=/opt/homebrew/bin/mvn
fi
if [ -z "$MAVEN_BIN" ]; then
  echo "Maven was not found. Set MAVEN_BIN or add mvn to PATH." >&2
  exit 1
fi

JPACKAGE="${JPACKAGE:-$(command -v jpackage || true)}"
if [ -z "$JPACKAGE" ]; then
  echo "Error: jpackage was not found in PATH." >&2
  echo "Please install JDK 17 or newer (e.g., via your package manager or SDKMAN)." >&2
  exit 1
fi

JAVA_VERSION=$("$JPACKAGE" --version 2>&1 | awk '{print $1}')
JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "Error: Detected JDK version $JAVA_VERSION, but JDK 17 or higher is required." >&2
    echo "Please install JDK 17+ and ensure it is in PATH." >&2
    exit 1
fi

VERSION="$("$MAVEN_BIN" -q -DforceStdout help:evaluate -Dexpression=project.version)"
if [ -z "$VERSION" ]; then
  echo "Unable to resolve the Maven project version." >&2
  exit 1
fi

echo "Warning: This script performs a clean build. Do not run it concurrently with an active development instance."
"$MAVEN_BIN" clean package -DskipTests
MAIN_JAR="target/cryptocarver-${VERSION}.jar"
if [ ! -f "$MAIN_JAR" ]; then
  echo "Expected JAR was not produced: $MAIN_JAR" >&2
  exit 1
fi

ARGS=(
  --name "$APP_NAME"
  --app-version "$VERSION"
  --input target
  --main-jar "$(basename "$MAIN_JAR")"
  --main-class "$MAIN_CLASS"
  --type "$PACKAGE_TYPE"
  --dest "$OUTPUT_DIR"
  --java-options "-Xmx512m"
)
if [ -f "$ICON_PATH" ]; then
  ARGS+=(--icon "$ICON_PATH")
fi
if [ "$PACKAGE_TYPE" = "app-image" ] || [ "$PACKAGE_TYPE" = "deb" ] || [ "$PACKAGE_TYPE" = "rpm" ]; then
  ARGS+=(--linux-shortcut --linux-menu-group CryptoCarver --linux-app-category Utility)
fi

"$JPACKAGE" "${ARGS[@]}"
echo "Linux ${PACKAGE_TYPE} created in $OUTPUT_DIR"
