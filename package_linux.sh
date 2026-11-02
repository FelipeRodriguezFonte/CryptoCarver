#!/usr/bin/env bash
set -euo pipefail

# Creates a self-contained Linux app-image and its .tar.gz by default. Set
# PACKAGE_TYPE=deb or PACKAGE_TYPE=rpm on a matching distribution to build an
# installer package. Each architecture is packaged on its own machine.
APP_NAME="CryptoCarver"
MAIN_CLASS="com.cryptocarver.Launcher"
OUTPUT_DIR="${PACKAGE_OUTPUT_DIR:-dist}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"
ICON_PATH="src/main/resources/icons/app-icon.png"

cd "$(dirname "$0")"
source scripts/toolchain.sh
find_java
find_maven


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

# jpackage copies its whole --input directory: give it only the executable JAR.
INPUT_DIR="target/jpackage-input"
mkdir -p "$INPUT_DIR"
cp "$MAIN_JAR" "$INPUT_DIR/"

mkdir -p "$OUTPUT_DIR"
ARGS=(
  --name "$APP_NAME"
  --app-version "$VERSION"
  --input "$INPUT_DIR"
  --main-jar "$(basename "$MAIN_JAR")"
  --main-class "$MAIN_CLASS"
  --type "$PACKAGE_TYPE"
  --dest "$OUTPUT_DIR"
  --java-options "-Xmx512m"
)
if [ -f "$ICON_PATH" ]; then
  ARGS+=(--icon "$ICON_PATH")
fi
# jpackage rejects the installer-only options when the type is app-image.
if [ "$PACKAGE_TYPE" = "deb" ] || [ "$PACKAGE_TYPE" = "rpm" ]; then
  ARGS+=(--linux-package-name cryptocarver --linux-shortcut --linux-menu-group CryptoCarver --linux-app-category Utility)
fi
# JavaFX loads these from its JAR at runtime, so jpackage cannot detect them.
# Debian-family t64 packages (Kali, Ubuntu 24.04) still provide libgtk-3-0.
if [ "$PACKAGE_TYPE" = "deb" ]; then
  ARGS+=(--linux-package-deps "libgtk-3-0, libgl1, libxtst6, libxxf86vm1, fontconfig, fonts-symbola")
fi
if [ "$PACKAGE_TYPE" = "app-image" ]; then
  rm -rf "${OUTPUT_DIR:?}/$APP_NAME"
fi

"$JPACKAGE" "${ARGS[@]}"
if [ "$PACKAGE_TYPE" = "app-image" ]; then
  ARCHIVE="$OUTPUT_DIR/$APP_NAME-$VERSION-linux-$(uname -m).tar.gz"
  tar -C "$OUTPUT_DIR" -czf "$ARCHIVE" "$APP_NAME"
  echo "Linux archive created: $ARCHIVE"
fi
echo "Linux ${PACKAGE_TYPE} created in $OUTPUT_DIR"
