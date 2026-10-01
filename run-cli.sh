#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
cd "$(dirname "$0")"
source scripts/toolchain.sh
find_java
find_maven
"$MAVEN_BIN" -q -DskipTests compile exec:java -Dexec.mainClass=com.cryptocarver.CryptoCarverCli -Dexec.args="$*"
