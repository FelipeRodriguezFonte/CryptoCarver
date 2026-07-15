#!/bin/bash

echo "================================================"
echo "  CryptoCarver - MODERN UI"
echo "================================================"
echo ""
echo "Launching modern UI..."
MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
if [ -z "$MAVEN_BIN" ] && [ -x /opt/homebrew/bin/mvn ]; then MAVEN_BIN=/opt/homebrew/bin/mvn; fi
if [ -z "$MAVEN_BIN" ]; then echo "Maven was not found. Set MAVEN_BIN or add mvn to PATH." >&2; exit 127; fi
"$MAVEN_BIN" javafx:run
