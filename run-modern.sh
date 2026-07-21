#!/bin/bash

echo "================================================"
echo "  CryptoCarver - MODERN UI"
echo "================================================"
echo ""
echo "Rebuilding and launching modern UI..."
MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
if [ -z "$MAVEN_BIN" ] && [ -x /opt/homebrew/bin/mvn ]; then MAVEN_BIN=/opt/homebrew/bin/mvn; fi
if [ -z "$MAVEN_BIN" ]; then echo "Maven was not found. Set MAVEN_BIN or add mvn to PATH." >&2; exit 127; fi
# The project is frequently run straight from the working tree.  A normal
# incremental Maven run can otherwise keep stale/conflicted .class files in
# target/ (for example a controller referring to a newly added helper class),
# which only becomes visible after the user presses a button.  Cleaning here
# keeps the runtime classpath identical to the current sources.
"$MAVEN_BIN" clean javafx:run
