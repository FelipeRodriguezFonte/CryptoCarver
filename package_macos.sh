#!/bin/bash

# Configuration
APP_NAME="CryptoCarver"
MAIN_CLASS="com.cryptocarver.Launcher"
ICON_SOURCE="src/main/resources/icons/app-icon.png"
ICON_TARGET="src/main/resources/icons/app-icon.icns"
OUTPUT_DIR="${PACKAGE_OUTPUT_DIR:-dist}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"

# Ensure JAVA_HOME is set
if [ -z "$JAVA_HOME" ]; then
    echo "JAVA_HOME not set. Attempting to detect..."
    export JAVA_HOME=$(/usr/libexec/java_home)
    echo "Detected JAVA_HOME: $JAVA_HOME"
fi

if [ -z "$JAVA_HOME" ]; then
    echo "Error: JAVA_HOME could not be detected."
    echo "Please set JAVA_HOME to your JDK 21+ installation."
    exit 1
fi

JPACKAGE="$JAVA_HOME/bin/jpackage"

# Check if jpackage exists
if [ ! -x "$JPACKAGE" ]; then
    echo "Error: jpackage not found at $JPACKAGE"
    echo "Please ensure you are using JDK 17 or later (e.g., from SDKMAN or Homebrew)."
    exit 1
fi

JAVA_VERSION=$("$JPACKAGE" --version 2>&1 | awk '{print $1}')
JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "Error: Detected JDK version $JAVA_VERSION, but JDK 17 or higher is required." >&2
    echo "Please install JDK 17+ and ensure it is in PATH (or JAVA_HOME)." >&2
    exit 1
fi

echo "=========================================="
echo "  Building CryptoCarver (macOS)"
echo "=========================================="

MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
if [ -z "$MAVEN_BIN" ] && [ -x /opt/homebrew/bin/mvn ]; then MAVEN_BIN=/opt/homebrew/bin/mvn; fi
if [ -z "$MAVEN_BIN" ]; then
    echo "Error: Maven was not found. Set MAVEN_BIN or add mvn to PATH."
    exit 1
fi

APP_VERSION=$("$MAVEN_BIN" -q -DforceStdout help:evaluate -Dexpression=project.version)
if [ -z "$APP_VERSION" ]; then
    echo "Error: Unable to resolve the Maven project version."
    exit 1
fi
MAIN_JAR="target/cryptocarver-${APP_VERSION}.jar"

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
    if iconutil -c icns "$ICONSET_DIR" -o "$ICON_TARGET"; then
        echo "Successfully generated $ICON_TARGET"
        APP_ICON="$ICON_TARGET"
    else
        echo "Error: Failed to generate the required macOS ICNS icon." >&2
        exit 1
    fi
else
    echo "Error: No macOS icon found at $ICON_TARGET or source PNG at $ICON_SOURCE" >&2
    exit 1
fi


# 1. Build with Maven
echo "Warning: This script performs a clean build. Do not run it concurrently with an active development instance."
echo "[1/3] Building project with Maven..."
"$MAVEN_BIN" clean package -DskipTests

if [ ! -f "$MAIN_JAR" ]; then
    echo "Error: Build failed. $MAIN_JAR not found."
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

# Build jpackage arguments
JPACKAGE_ARGS=(
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --input target
  --main-jar "$(basename "$MAIN_JAR")"
  --main-class "$MAIN_CLASS"
  --type "$PACKAGE_TYPE"
  --dest "$JPACKAGE_WORK_DIR"
  # jpackage's automatic jdeps scan of the shaded uber-jar does not reliably
  # detect every JDK module the app touches at runtime (AWT/Taskbar, ImageIO,
  # PKCS#11, XML DOM, JAAS, etc.), so the full set is listed explicitly here
  # rather than relying on that detection alone.
  --add-modules "java.se,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,jdk.unsupported.desktop,jdk.security.auth,jdk.accessibility,jdk.xml.dom,jdk.naming.dns"
  --java-options "--enable-preview"
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
        BUILT_BUNDLE="$JPACKAGE_WORK_DIR/${APP_NAME}.app"
        if [ ! -d "$BUILT_BUNDLE" ]; then
            echo "FAILED. Expected app bundle not found: $BUILT_BUNDLE" >&2
            exit 1
        fi
        APP_BUNDLE="$OUTPUT_DIR/${APP_NAME}.app"
        echo "[INFO] Copying signed app bundle into $APP_BUNDLE ..."
        rm -rf "$APP_BUNDLE"
        cp -R "$BUILT_BUNDLE" "$APP_BUNDLE"
        echo ""
        echo "SUCCESS! macOS app bundle built: $APP_BUNDLE"
        echo "Open $APP_BUNDLE to let macOS identify the application as CryptoCarver."
    else
        echo "[INFO] Copying ${PACKAGE_TYPE} output into $OUTPUT_DIR ..."
        cp -R "$JPACKAGE_WORK_DIR"/. "$OUTPUT_DIR"/
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
