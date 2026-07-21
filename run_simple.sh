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

# Ensure running with correct Java version (Java 17+)
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "Detecting Java 17+..."
    export JAVA_HOME=$(/usr/libexec/java_home -v 17+)
fi

if [ -z "$JAVA_HOME" ]; then
    echo "Warning: JAVA_HOME for Java 17+ not found. Attempting to use default 'java'..."
    JAVA_CMD="java"
else
    echo "Using JAVA_HOME: $JAVA_HOME"
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

echo "Starting CryptoCarver..."
"$JAVA_CMD" -jar "$JAR_FILE"
