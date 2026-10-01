#!/usr/bin/env bash
# Diagnóstico de lectura: no instala ni cambia settings.xml.
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/toolchain.sh
failures=0
missing() { echo "FALTA: $*"; failures=$((failures + 1)); }
echo "Sistema: $(uname -s); arquitectura: $(uname -m)"
case "$(uname -s)" in
    Darwin) echo "Instalador macOS: Apple Silicon=arm64; Intel=x64." ;;
    Linux) echo "Compila en Linux para obtener nativos JavaFX de Linux." ;;
    *) echo "Sistema no cubierto por los scripts Unix; consulta docs/WINDOWS.md." ;;
esac
if find_java; then
    echo "Java: $JAVA_VERSION ($JAVA_CMD)"
    if command -v javac >/dev/null 2>&1; then javac -version 2>&1; else missing "JDK 17+ (javac). Instala Temurin desde https://adoptium.net/."; fi
    if command -v jpackage >/dev/null 2>&1; then
        echo "jpackage: $(jpackage --version)"
    else
        missing "jpackage para crear instaladores; está incluido en JDK 17+. No es necesario para ejecutar un JAR."
    fi
else
    missing "Java/JDK 17+."
fi
if find_maven; then
    maven_version="$("$MAVEN_BIN" -version 2>&1)"
    echo "$maven_version"
    version="$(printf '%s\n' "$maven_version" | sed -n 's/^Apache Maven \([0-9][0-9.]*\).*/\1/p')"
    major="${version%%.*}"; rest="${version#*.}"; minor="${rest%%.*}"
    case "$major:$minor" in *[!0-9:]*|:) missing "No se pudo comprobar Maven 3.8+." ;;
        *) if [ "$major" -lt 3 ] || { [ "$major" -eq 3 ] && [ "$minor" -lt 8 ]; }; then missing "Maven 3.8+; actual=$version."; fi ;;
    esac
else
    missing "Maven 3.8+."
fi
# HEAD público, sin credenciales, body, cookies ni datos del usuario; máximo 8 s.
if command -v curl >/dev/null 2>&1 && curl --disable --head --fail --silent --show-error --connect-timeout 3 --max-time 8 \
    https://repo.maven.apache.org/maven2/ >/dev/null; then
    echo "Maven Central: accesible."
else
    missing "Acceso a Maven Central. En redes corporativas revisa el proxy en ~/.m2/settings.xml (<proxies>), o usa el instalador de Releases."
fi
available="$(df -Pk . | awk 'END {print $4}')"
echo "Disco disponible: $available KiB (recomendado al menos 2 GiB para compilar y empaquetar)."
if [ "$available" -lt 2097152 ]; then missing "Espacio: libera al menos 2 GiB en este volumen."; fi
[ "$failures" -eq 0 ] && echo "Diagnóstico correcto." || echo "Comprobaciones pendientes: $failures"
[ "$failures" -eq 0 ]
