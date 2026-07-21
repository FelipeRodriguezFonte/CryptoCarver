#!/bin/bash
set -e

echo "Generating Operations Catalog..."

MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
if [ -z "$MAVEN_BIN" ] && [ -x /opt/homebrew/bin/mvn ]; then
  MAVEN_BIN=/opt/homebrew/bin/mvn
fi
if [ -z "$MAVEN_BIN" ]; then
  echo "Maven was not found. Set MAVEN_BIN or add mvn to PATH." >&2
  exit 127
fi

echo "Compiling test sources..."
"$MAVEN_BIN" -q test-compile

echo "Generating catalog..."
"$MAVEN_BIN" -q exec:java -Dexec.mainClass="com.cryptoforge.model.CatalogGenerator" -Dexec.classpathScope="test"

echo "Done."
