#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
source scripts/toolchain.sh
[ "$(uname -s)" = Darwin ] || { echo "Este script requiere macOS." >&2; exit 1; }
find_java
find_maven

# Configuration
APP_NAME="CryptoCarver"
MAIN_CLASS="com.cryptocarver.Launcher"
ICON_SOURCE="src/main/resources/icons/app-icon.png"
ICON_TARGET="src/main/resources/icons/app-icon.icns"
OUTPUT_DIR="${PACKAGE_OUTPUT_DIR:-dist}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"

JPACKAGE="$JAVA_HOME/bin/jpackage"
if [ ! -x "$JPACKAGE" ]; then
    echo "Error: se necesita jpackage, incluido en un JDK 17+. Revisa JAVA_HOME." >&2
    exit 1
fi

APP_VERSION=$("$MAVEN_BIN" -q -DforceStdout help:evaluate -Dexpression=project.version)
if [ -z "$APP_VERSION" ]; then
    echo "Error: Unable to resolve the Maven project version."
    exit 1
fi
MAIN_JAR="target/cryptocarver-${APP_VERSION}.jar"

# 1. Build with Maven
echo "Warning: This script performs a clean build. Do not run it concurrently with an active development instance."
echo "[1/3] Building project with Maven..."
if [ "${PACKAGE_SKIP_BUILD:-false}" != true ]; then
    "$MAVEN_BIN" clean package -DskipTests
fi

if [ ! -f "$MAIN_JAR" ]; then
    echo "Error: Build failed. $MAIN_JAR not found."
    exit 1
fi


# 0. Icon Generation
echo "[0/3] Checking icons..."
APP_ICON=""
if [ -f "$ICON_TARGET" ]; then
    APP_ICON="$ICON_TARGET"
    echo "Using existing ICNS icon: $APP_ICON"
elif [ -f "$ICON_SOURCE" ]; then
    echo "ICNS icon not found, but PNG exists. Attempting to generate..."
    
    # Create temporary iconset directory
    ICONSET_DIR="target/icons.iconset"
    mkdir -p "$ICONSET_DIR"
    
    # Generate scaled images
    sips -z 16 16     "$ICON_SOURCE" --out "$ICONSET_DIR/icon_16x16.png" > /dev/null
    sips -z 32 32     "$ICON_SOURCE" --out "$ICONSET_DIR/icon_16x16@2x.png" > /dev/null
    sips -z 32 32     "$ICON_SOURCE" --out "$ICONSET_DIR/icon_32x32.png" > /dev/null
    sips -z 64 64     "$ICON_SOURCE" --out "$ICONSET_DIR/icon_32x32@2x.png" > /dev/null
    sips -z 128 128   "$ICON_SOURCE" --out "$ICONSET_DIR/icon_128x128.png" > /dev/null
    sips -z 256 256   "$ICON_SOURCE" --out "$ICONSET_DIR/icon_128x128@2x.png" > /dev/null
    sips -z 256 256   "$ICON_SOURCE" --out "$ICONSET_DIR/icon_256x256.png" > /dev/null
    sips -z 512 512   "$ICON_SOURCE" --out "$ICONSET_DIR/icon_256x256@2x.png" > /dev/null
    sips -z 512 512   "$ICON_SOURCE" --out "$ICONSET_DIR/icon_512x512.png" > /dev/null
    sips -z 1024 1024 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_512x512@2x.png" > /dev/null
    
    # Create icns
    if iconutil -c icns "$ICONSET_DIR" -o target/app-icon.icns; then
        echo "Successfully generated $ICON_TARGET"
        APP_ICON="target/app-icon.icns"
    else
        echo "Error: Failed to generate the required macOS ICNS icon." >&2
        exit 1
    fi
else
    echo "Error: No macOS icon found at $ICON_TARGET or source PNG at $ICON_SOURCE" >&2
    exit 1
fi


# 2. Cleanup previous build is skipped to be non-destructive
echo "[2/3] Skipping cleanup to preserve previous releases..."

# 3. Run jpackage
echo "[3/3] Creating macOS ${PACKAGE_TYPE} package..."

# jpackage's ad-hoc codesign step fails whenever the destination bundle carries
# Finder/FileProvider metadata (com.apple.FinderInfo, com.apple.fileprovider.*).
# If this repo lives under a cloud-synced folder (iCloud Drive, Internxt Drive,
# etc.), that daemon re-tags files within moments of creation, so signing
# in-place under $OUTPUT_DIR is unreliable no matter how often xattrs are
# cleared beforehand. Building and signing in a plain local temp directory
# sidesteps the daemon entirely; only the already-signed result is then copied
# into $OUTPUT_DIR, so the destination folder's sync status no longer matters.
JPACKAGE_WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/cryptocarver-jpackage.XXXXXX")"
cleanup_jpackage_work_dir() {
    rm -rf "$JPACKAGE_WORK_DIR"
}
trap cleanup_jpackage_work_dir EXIT

# Solo el JAR ejecutable; excluir tests, informes y el JAR sin sombrear.
mkdir -p "$JPACKAGE_WORK_DIR/input" "$JPACKAGE_WORK_DIR/output"
cp "$MAIN_JAR" "$JPACKAGE_WORK_DIR/input/"

# Build jpackage arguments
JPACKAGE_ARGS=(
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --input "$JPACKAGE_WORK_DIR/input"
  --main-jar "$(basename "$MAIN_JAR")"
  --main-class "$MAIN_CLASS"
  --type "$PACKAGE_TYPE"
  --dest "$JPACKAGE_WORK_DIR/output"
  # jpackage's automatic jdeps scan of the shaded uber-jar does not reliably
  # detect every JDK module the app touches at runtime (AWT/Taskbar, ImageIO,
  # PKCS#11, XML DOM, JAAS, etc.), so the full set is listed explicitly here
  # rather than relying on that detection alone.
  --add-modules "java.se,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,jdk.unsupported.desktop,jdk.security.auth,jdk.accessibility,jdk.xml.dom,jdk.naming.dns"
  --java-options "-Xmx512m"
  --verbose
)

if [ -n "$APP_ICON" ]; then
    JPACKAGE_ARGS+=(--icon "$APP_ICON")
fi

"$JPACKAGE" "${JPACKAGE_ARGS[@]}"
JPACKAGE_EXIT=$?

if [ $JPACKAGE_EXIT -eq 0 ]; then
    mkdir -p "$OUTPUT_DIR"
    if [ "$PACKAGE_TYPE" = "app-image" ]; then
        BUILT_BUNDLE="$JPACKAGE_WORK_DIR/output/${APP_NAME}.app"
        if [ ! -d "$BUILT_BUNDLE" ]; then
            echo "FAILED. Expected app bundle not found: $BUILT_BUNDLE" >&2
            exit 1
        fi
        APP_BUNDLE="$OUTPUT_DIR/${APP_NAME}.app"
        echo "[INFO] Copying signed app bundle into $APP_BUNDLE ..."
        # A previous app-image can contain read-only files (bundled JDK legal notices) that
        # make `rm -rf` fail partway through without aborting the script (rm keeps going and
        # only reports errors), leaving a stale nested directory behind. Clear write
        # protection first, and always merge-copy with a trailing "/." on the source so a
        # bundle left behind by a still-failed removal gets overwritten in place instead of
        # nested inside itself.
        chmod -R u+w "$APP_BUNDLE" 2>/dev/null || true
        rm -rf "$APP_BUNDLE"
        mkdir -p "$APP_BUNDLE"
        cp -R "$BUILT_BUNDLE"/. "$APP_BUNDLE"/
        echo ""
        echo "SUCCESS! macOS app bundle built: $APP_BUNDLE"
        echo "Open $APP_BUNDLE to let macOS identify the application as CryptoCarver."
    else
        echo "[INFO] Copying ${PACKAGE_TYPE} output into $OUTPUT_DIR ..."
        cp -R "$JPACKAGE_WORK_DIR/output"/. "$OUTPUT_DIR"/
        echo ""
        echo "SUCCESS! macOS ${PACKAGE_TYPE} built in: $OUTPUT_DIR"
    fi
    echo "A generic JAR launched with java -jar may be identified by macOS as java."
    echo "Use PACKAGE_TYPE=dmg ./package_macos.sh to create a distributable DMG."
else
    echo ""
    echo "FAILED. Please check the error messages above."
    exit 1
fi
