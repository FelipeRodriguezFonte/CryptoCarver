#!/bin/bash
# CryptoCarver Launcher for macOS/Linux

# Colors
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo "${GREEN}"
echo "  CryptoCarver - Advanced Crypto Test Tool"
echo "================================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 21 or higher from https://adoptium.net/"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "WARNING: Java version $JAVA_VERSION detected. Java 21+ recommended."
fi

echo "Starting CryptoCarver..."
echo ""

cd "$(dirname "$0")"
mvn javafx:run

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Failed to start application"
    echo "Make sure Maven is installed and try: mvn clean install"
    exit 1
fi
