#!/bin/bash
# Simple runner for CryptoCarver

# Ensure we are in the script directory
cd "$(dirname "$0")"

# Path to the executable JAR
# Check if JAR exists in current directory (portable mode)
if [ -f "cryptocarver-2.3.0.jar" ]; then
    JAR_FILE="cryptocarver-2.3.0.jar"
# Check if JAR exists in target directory (maven project mode)
elif [ -f "target/cryptocarver-2.3.0.jar" ]; then
    JAR_FILE="target/cryptocarver-2.3.0.jar"
else
    JAR_FILE="target/cryptocarver-2.3.0.jar" # Default for error message
fi

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: $JAR_FILE not found."
    echo "Please run 'mvn clean package -DskipTests' to build the project,"
    echo "OR copy 'cryptocarver-2.3.0.jar' to this directory."
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
