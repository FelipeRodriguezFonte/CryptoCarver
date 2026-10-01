#!/usr/bin/env bash
# Utilidades compartidas; no ejecutan instalaciones ni modifican preferencias.
find_maven() {
    MAVEN_BIN="${MAVEN_BIN:-$(command -v mvn || true)}"
    if [ -z "$MAVEN_BIN" ]; then
        for candidate in /opt/homebrew/bin/mvn /usr/local/bin/mvn; do
            if [ -x "$candidate" ]; then MAVEN_BIN="$candidate"; break; fi
        done
    fi
    if [ -z "$MAVEN_BIN" ] || ! command -v "$MAVEN_BIN" >/dev/null 2>&1; then
        echo "Error: se necesita Maven 3.8+. Instálalo desde https://maven.apache.org/ o con brew install maven; añade mvn al PATH o define MAVEN_BIN." >&2
        return 1
    fi
}

find_java() {
    if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -s)" = Darwin ]; then
        JAVA_HOME="$(/usr/libexec/java_home -v '17+' 2>/dev/null)" || {
            echo "Error: java_home no encontró un JDK 17+. Instala Temurin 17 o superior desde https://adoptium.net/ y define JAVA_HOME." >&2
            return 1
        }
    fi
    if [ -n "${JAVA_HOME:-}" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
    else
        JAVA_CMD="$(command -v java || true)"
    fi
    if [ -z "$JAVA_CMD" ] || [ ! -x "$JAVA_CMD" ]; then
        echo "Error: se necesita Java/JDK 17+. Instala Temurin desde https://adoptium.net/ y revisa JAVA_HOME/PATH." >&2
        return 1
    fi
    JAVA_VERSION="$("$JAVA_CMD" -version 2>&1 | awk -F '\"' '/version/ {print $2; exit}')"
    JAVA_MAJOR="${JAVA_VERSION%%.*}"
    case "$JAVA_MAJOR" in ''|*[!0-9]*) JAVA_MAJOR=0 ;; esac
    if [ "$JAVA_MAJOR" -lt 17 ]; then
        echo "Error: Java ${JAVA_VERSION:-desconocido} detectado; se necesita Java/JDK 17+. Revisa JAVA_HOME/PATH." >&2
        return 1
    fi
    if [ -n "${JAVA_HOME:-}" ]; then export JAVA_HOME; export PATH="$JAVA_HOME/bin:$PATH"; fi
}
