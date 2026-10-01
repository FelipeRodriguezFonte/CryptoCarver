#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
source scripts/toolchain.sh
find_java
# Un JAR ya compilado no requiere Maven ni red para arrancar.
for jar in target/cryptocarver-*.jar; do
    if [ -f "$jar" ]; then exec "$JAVA_CMD" -jar "$jar"; fi
done
find_maven
exec "$MAVEN_BIN" -DskipTests compile javafx:run
