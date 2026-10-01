# Diagnóstico de instalación desde GitHub (encargo 44)

Base: `main`, `92e7b3b`; rama de trabajo: `luna/github-install`. Fecha: 1 de octubre de 2026.

## Entorno y prueba limpia

Mac Apple Silicon, macOS 26.6.2. `mvn -version`: Maven 3.9.11, Java 25 de Homebrew.
`java -version` en el entorno habitual: Java 8 de SDKMAN. Hay Temurin 21.0.8 arm64 registrado
con `/usr/libexec/java_home`. La compilación usa `maven.compiler.release=17`; JavaFX 21
requiere Java 17. JDK 17 es el mínimo, también para jpackage.

Se clonó `https://github.com/FelipeRodriguezFonte/CryptoCarver.git` en
`/tmp/cryptocarver-install44/source`, en `92e7b3b`. Una segunda clonación usa `env -i`,
HOME temporal y PATH del sistema. La red funciona sin las variables del usuario:
Maven Central y el repositorio de Shibboleth descargan dependencias. Un `curl` con
el entorno habitual agotó la conexión en 5 segundos; con `env -i` funcionó.

Las salidas y comandos completos de las pruebas están resumidos en `informe.md`.
La primera exploración con repositorio Maven vacío se inició en el checkout de trabajo
antes de editarlo; se distingue de la repetición en el clon de GitHub para no presentarla
como instalación limpia. No se puede extrapolar este Mac a la red corporativa.

## Dependencias de los scripts originales

| Archivo | Dependencias / supuestos encontrados |
|---|---|
| `run.sh` | Java del PATH (puede ser Java 8), recomienda 21 pese al mínimo 17; Maven del PATH; no valida Maven; falta selección de JDK. |
| `run-modern.sh` | Maven PATH y solo fallback Apple Silicon `/opt/homebrew/bin`; trabaja en PWD, no en la raíz del script; mktemp/mv/rm. |
| `run_simple.sh` | `java_home -v 17+` incondicional cuando falta JAVA_HOME, incluso en Linux; no valida la versión de un JAVA_HOME existente. |
| `run-cli.sh` | Maven PATH y solo fallback Apple Silicon; Java implícito de Maven. |
| `package_macos.sh` | `java_home` sin mínimo; mensaje 21+ contradictorio; jpackage 17+; sips/iconutil; códigos de error de Maven ignorados; `--input target` incluye clases, informes, tests y JAR original. |
| `generate_icons.sh` | sips e iconutil, incluidos en macOS (no en Linux); rutas relativas al PWD; escribe ICNS en fuentes. |
| `scripts/build-release.sh` | Maven PATH y fallback solo arm64; suite completa; manifiesto usa Java PATH; el JAR con JavaFX no es independiente de plataforma. |
| `scripts/generate-operations-catalog.sh` | Maven PATH y fallback solo arm64; plugin exec descargable; rutas relativas. |
| `scripts/run-ui-tests.sh` | Maven PATH, Xvfb/xauth y librerías GTK/GL de Linux; renderizado software; necesita pantalla virtual 1920×1080. |
| `scripts/quality-gate.sh` | Maven PATH, xmllint (macOS lo incluye; Linux requiere libxml2-utils), git; el texto «headless» no corresponde al test por defecto con UI. |
| `scripts/create-release-manifest.sh` | shasum o sha256sum, Java PATH, git opcional, find; no rutas personales. |
| `scripts/test-manifest-generation.sh` | Bash, mktemp, grep, shasum; ejecutarlo desde la raíz. |
| `scripts/generate_roadmap_pdf.py` | Python, ReportLab y fuentes del sistema; no forma parte de la instalación. |
| `scripts/payment_reference.py` | Python y PyCryptodome; referencia de desarrollo, no instalación. |
| `package_linux.sh` | fallback Homebrew innecesario en Linux; jpackage; herramientas de deb/rpm opcionales. |

El JAR sombreado incorpora nativos JavaFX del sistema/arquitectura de compilación.
Cada instalador debe construirse en su arquitectura nativa y contener su runtime.
`iconutil` y `sips` vienen con macOS; `rg` no. `/usr/libexec/java_home` solo existe en macOS.

## CI: evidencia y causas

Se leyeron los logs mediante el conector GitHub del
[run 36880281178](https://github.com/FelipeRodriguezFonte/CryptoCarver/actions/runs/36880281178),
commit `92e7b3b`. No se lanzó ni relanzó ningún workflow.

- Job `110430000539`, higiene: `line 2: rg: command not found`, salida 127.
  Además, `rg` devuelve 1 cuando no hay coincidencias: con pipefail el presupuesto
  cero también abortaría. Hay que tratar ausencia de coincidencias sin ocultar errores.
- Job `110430000936`, JavaFX: `Tests run: 375, Failures: 3, Errors: 1, Skipped: 0`.
  `MaterialFieldBadgeTest` presupone redacción sin seleccionar MASKED: el perfil por
  defecto FULL_LAB conserva entradas. Es una precondición obsoleta del test.
  `ModernShellCharacterizationUITest` exige ancho preferido +2; GTK asigna exactamente
  85 px al botón de 85 px preferidos. La comprobación debe medir el texto visible real.
  `ClippedTextRegressionUITest` detecta recortes reales con métricas tipográficas Linux:
  rail de 60 px, icono de paleta, botones de inspector y nombre largo del workbench.
  Se corrigen tamaños/ajuste en Java, conservando el detector y sus estados.
- El job SoftHSM2 no falló. Los tres jobs muestran avisos por actions v4 / Node 20.

## Ficheros versionados generados o grandes

`git ls-tree -rl 92e7b3b` y `git ls-files`: no hay archivos versionados en `dist/`,
`target/`, `tmp/`, ni `dependency-reduced-pom.xml` ni google-java-format JAR.
Permanece `output/pdf/CryptoCarver_Roadmap_Evolucion.pdf`: **115.053 bytes**;
lo consume un tutorial, según `.gitignore`. No se ha borrado.

Los mayores ficheros son material de documentación o vectores de prueba, no basura:

| Archivo | Bytes |
|---|---:|
| `tutoriales/pdfs/11-pagos.pdf` | 6.364.873 |
| `tutoriales/pdfs/24-analizar-un-archivo-cifrado.pdf` | 4.231.045 |
| `src/test/resources/pqc/kat/subset_sha2-128f-simple.rsp` | 4.124.369 |
| `tutoriales/pdfs/02-operaciones-genericas.pdf` | 3.576.918 |
| `tutoriales/pdfs/07-seguridad-xml.pdf` | 2.050.210 |
| `src/test/resources/pqc/kat/PQCsignKAT_Dilithium2.rsp` | 1.941.571 |

No se borra ni incorpora ningún binario en este encargo.
