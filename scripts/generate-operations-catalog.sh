#!/bin/bash
set -e

echo "Generating Operations Catalog..."

cd "$(dirname "$0")/.."
source scripts/toolchain.sh
find_java
find_maven


echo "Compiling test sources..."
"$MAVEN_BIN" -q test-compile

echo "Generating catalog..."
"$MAVEN_BIN" -q exec:java -Dexec.mainClass="com.cryptocarver.model.CatalogGenerator" -Dexec.classpathScope="test"

echo "Done."
