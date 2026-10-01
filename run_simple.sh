#!/bin/bash
# Simple runner for CryptoCarver

# Ensure we are in the script directory
cd "$(dirname "$0")"

# Locate the executable JAR without duplicating the Maven project version.
JAR_FILE=""
for candidate in cryptocarver-*.jar target/cryptocarver-*.jar; do
    case "$candidate" in *-original.jar) continue ;; esac
    if [ -f "$candidate" ]; then JAR_FILE="$candidate"; break; fi
done

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: CryptoCarver executable JAR not found."
    echo "Please run 'mvn clean package -DskipTests' to build the project,"
    echo "OR copy 'cryptocarver-<version>.jar' to this directory."
    exit 1
fi

source scripts/toolchain.sh
find_java || exit 1
exec "$JAVA_CMD" -jar "$JAR_FILE"
