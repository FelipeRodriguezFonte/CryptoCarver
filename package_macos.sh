#!/bin/bash

# Configuration
APP_NAME="CryptoCarver"
APP_VERSION="2.3.0"
MAIN_JAR="target/cryptocarver-2.3.0.jar"
MAIN_CLASS="com.cryptoforge.Launcher"
ICON_SOURCE="src/main/resources/icons/app-icon.png"
ICON_TARGET="src/main/resources/icons/app-icon.icns"
OUTPUT_DIR="dist"

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
    echo "Please ensure you are using JDK 14 or later."
    exit 1
fi

echo "=========================================="
echo "  Building CryptoCarver (macOS)"
echo "=========================================="

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
        echo "Warning: Failed to generate ICNS. Using PNG (might show default Java icon)."
        APP_ICON="$ICON_SOURCE"
    fi
else
    echo "Warning: No icon file found at $ICON_SOURCE"
fi


# 1. Build with Maven
echo "[1/3] Building project with Maven..."
mvn clean package -DskipTests

if [ ! -f "$MAIN_JAR" ]; then
    echo "Error: Build failed. $MAIN_JAR not found."
    exit 1
fi

# 2. Cleanup previous build
if [ -d "$OUTPUT_DIR" ]; then
    echo "[2/3] Cleaning previous build..."
    rm -rf "$OUTPUT_DIR"
fi

# 3. Run jpackage
echo "[3/3] Creating macOS application bundle..."

# Build jpackage arguments
JPACKAGE_ARGS=(
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --input target
  --main-jar "cryptocarver-2.3.0.jar"
  --main-class "$MAIN_CLASS"
  --type app-image
  --dest "$OUTPUT_DIR"
  --java-options "--enable-preview"
  --java-options "-Xmx512m"
  --verbose
)

if [ -n "$APP_ICON" ]; then
    JPACKAGE_ARGS+=(--icon "$APP_ICON")
fi

"$JPACKAGE" "${JPACKAGE_ARGS[@]}"

if [ $? -eq 0 ]; then
    echo ""
    echo "SUCCESS! Application built at: $OUTPUT_DIR/$APP_NAME.app"
    echo "You can run it with: open $OUTPUT_DIR/$APP_NAME.app"
else
    echo ""
    echo "FAILED. Please check the error messages above."
    exit 1
fi
