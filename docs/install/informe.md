# Informe del encargo 44

Trabajo realizado directamente en Sol, desde `92e7b3b`, en `luna/github-install`.
No se hizo push, no se crearon etiquetas ni se publicó ninguna release.
No se modificaron `crypto/`, FXML, CSS, `.mvn/` ni los argumentos JVM de Surefire.
`runUiTests=true` permanece como valor predeterminado.

## Instalación y diagnóstico

Directorio de pruebas: `/tmp/cryptocarver-install44`. Logs locales fuera del repositorio;
se incluyen aquí los resultados relevantes para que el informe no dependa de conservarlos.

```bash
git clone https://github.com/FelipeRodriguezFonte/CryptoCarver.git /tmp/cryptocarver-install44/source
# Una segunda clonación eliminó las variables de entorno del usuario:
env -i HOME=/tmp/cryptocarver-install44/home PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  git clone https://github.com/FelipeRodriguezFonte/CryptoCarver.git /tmp/cryptocarver-install44/clean
```

Ambos clones estaban en `92e7b3b`. Primero se exploró el checkout todavía sin cambios
con `env -i`, HOME temporal y `-Dmaven.repo.local=/tmp/cryptocarver-install44/m2`, inicialmente
vacío. Descargó dependencias, compiló y falló durante tests:

```text
ModernMainControllerUITest.testAsymmetricKeyGenerationWorkbenchUI:3080->runAndWait:150
IllegalState JavaFX runLater execution timed out
Tests run: 2532, Failures: 0, Errors: 1, Skipped: 1
Total time: 05:44 min
```

Después se ejecutaron literalmente `mvn clean package` y `mvn javafx:run` en el clon
`source`, con el mismo repositorio temporal ya descargado, sin variables del usuario:

```text
Tests run: 2532, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
Total time: 04:18 min
Modern UI launched successfully!
```

Esto no prueba que el Mac del trabajo pueda acceder a esos repositorios, ni reproduce
de forma estable el timeout: la misma base pasó al repetir. No se cambió el test de
generación de claves ni su timeout. Instalar ahora evita completamente esa dependencia
de la suite JavaFX. Una prueba adicional en el clon `clean`, con repositorio
`clean-m2` inicialmente vacío, figura en la tabla final de validación.

En el clon `source`:

```bash
mvn -Dmaven.repo.local=/tmp/cryptocarver-install44/m2 clean package -DskipTests
java -jar target/cryptocarver-2.4.0.jar
```

El paquete terminó con `BUILD SUCCESS`, **16,899 s**, sin tests. El JAR arrancó con
`Modern UI launched successfully!` y permaneció activo durante la observación de 15 s;
se terminó únicamente ese proceso de prueba. También se terminó el launcher de Maven
una vez comprobado el arranque. Las advertencias de CSS (conversión de radios/colores)
son anteriores al cambio y aparecen tanto en el clon original como en la app empaquetada;
no se declara una limpieza visual completa, fuera del alcance permitido.

## Scripts y empaquetado

La auditoría completa, tamaños de archivos y dependencias está en
[diagnostico.md](diagnostico.md). `scripts/toolchain.sh` centraliza la búsqueda de Maven:
PATH, `/opt/homebrew/bin`, `/usr/local/bin`, conservando `MAVEN_BIN` explícito.
Valida Java 17+; cuando falta JAVA_HOME en macOS usa `java_home -v '17+'` y explica
cómo instalar un JDK si falla. Así Maven y el launcher usan el mismo JDK.
Los scripts modificados se sitúan en la raíz del proyecto antes de operar.

`run.sh` ejecuta el JAR compilado; no necesita Maven ni red en ese caso.
`package_macos.sh` aborta ante fallos, usa JDK 17+, conserva el empaquetado en temporal
para evitar metadatos de Finder y copia **solo el JAR ejecutable** al input de jpackage.
No incluye clases de tests, informes, test-home ni el JAR original dentro de la app.
El icono generado se guarda en `target`, después del clean, sin escribir binarios en fuentes.
Se retira `--enable-preview`: el código se compila con `release=17` sin funciones preview.
`PACKAGE_SKIP_BUILD=true` permite empaquetar el JAR previamente probado en CI.

`doctor.sh` verifica sistema/arquitectura, Java, javac, jpackage, Maven 3.8+, Maven Central
y disco. Usa HEAD público con máximo de 8 s; no instala, no envía datos del usuario ni
credenciales. En este Mac detectó Temurin 21.0.8, Maven 3.9.11, jpackage y Central accesible,
con disco suficiente. `JAVA_HOME=/invalid bash run.sh` aborta con el mensaje JDK 17+.

## CI: correcciones y bloqueo pendiente

Logs confirmados del [run original](https://github.com/FelipeRodriguezFonte/CryptoCarver/actions/runs/36880281178):

- **UX quality gates**: `rg: command not found`, salida 127. Se reemplaza por grep recursivo,
  ignorando archivos binarios y tratando únicamente la salida 1 como ausencia de coincidencias.
  Se mantienen los umbrales: estilos inline = 0 y emojis ≤12.
  **La corrección revela un segundo fallo: 325 emojis, frente a 12.** Ripgrep y grep coinciden
  en 325 cuando se ignoran los archivos binarios. Hay emojis en FXML y en código Java de UI,
  registros y handlers. No se oculta el fallo, no se altera el umbral y no se escapan caracteres
  para engañar al contador. Cumplir el presupuesto requiere un encargo de sustitución de iconos
  que permita tocar FXML. Por tanto **no se afirma que toda la CI quede verde**.
- **JavaFX UI suite**: dos tests de secretos no seleccionaban MASKED aunque FULL_LAB conserva
  material para reconstruir recetas. Se selecciona MASKED antes de cada prueba de esa clase y
  se restaura el perfil anterior después. Las aserciones de redacción se conservan.
  El test del botón de diálogo exigía ancho preferido +2, condición que GTK no garantiza.
  Ahora comprueba el texto completo realmente mostrado por el skin y el mínimo de 80 px.
  Los recortes reales se corrigen en Java: rail ajustado a métricas de fuente, icono de paleta
  con su ancho preferido y textos del inspector/workbench que admiten varias líneas.
  El detector de recortes, los estados y el tamaño de ventana de su test se conservan.
  Las 3 clases afectadas pasan **26 tests, 0 fallos/errores/omitidos** con renderizado software.
- **SoftHSM2**: el job original pasó; solo se actualizan actions, sin modificar sus comandos.
  No hay SoftHSM2/Xvfb Linux disponibles en este Mac para ejecutar ese job.

`bash scripts/run-ui-tests.sh` en macOS sale 2: `xvfb-run is required` (esperado).
La reproducción sobre el escritorio usa sus opciones Maven sin Xvfb:
`mvn -o -q -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw test`.
La ejecución focalizada añade `-Dtest=MaterialFieldBadgeTest,ModernShellCharacterizationUITest,ClippedTextRegressionUITest`.
La reproducción completa del comando original, en Java 21 y con renderizado software,
agotó el heap: `OutOfMemoryError` en `InvokeLaterDispatcher` y en el hilo de Surefire.
Se terminó esa ejecución al quedar sin progreso; no es una suite completada.
Se probó inicialmente `-DreuseForks=false`, pero el POM fijaba literalmente `true`:
los XML demostraban un único launcher y se repitió el OOM. Se parametriza esa opción
como `surefire.reuseForks`, con valor predeterminado `true`.
El job de interfaz ahora selecciona `-Dgroups=ui` y usa `-Dsurefire.reuseForks=false`: cada clase
libera su toolkit y estado de renderizado al terminar el proceso. El heap de 3 GiB,
los timeouts y el `mvn test` predeterminado no cambian. La nueva reproducción local es:
`mvn -o -q -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test`.

Los comandos Linux apt-get, dpkg, readlink -f y timeout deben verificarse en GitHub.
No se ejecutó GitHub Actions y no se puede asegurar aquí el resultado GTK/Xvfb.
Los comandos `gh release create/upload` no se ejecutan localmente: publicar o alterar
releases está expresamente fuera de este encargo. Se comprueba su sintaxis de shell,
y su funcionamiento con GITHUB_TOKEN deberá verificarse en el job de publicación.

## Releases

Se usa `macos-15` (arm64) y `macos-15-intel` (x64): son equivalentes vigentes nativos
según [runner-images](https://github.com/actions/runner-images).
[macOS 14 está en retirada](https://github.com/actions/runner-images/issues/13518),
y no se reutiliza el runner retirado macos-13.
Las versiones mayores actuales verificadas son
[checkout v7](https://github.com/actions/checkout),
[setup-java v6](https://github.com/actions/setup-java),
[upload-artifact v7](https://github.com/actions/upload-artifact) y
[download-artifact v8](https://github.com/actions/download-artifact).

`release.yml` responde a etiquetas `v*` y disparo manual. Lee la versión del POM,
valida arquitectura/etiqueta y ejecuta `mvn -B clean package -DrunUiTests=false` en ambos
runners con JDK 17. Cada JAR contiene los nativos JavaFX de su arquitectura.
Genera DMG con runtime, renombra a `CryptoCarver-<versión>-macos-{arm64,x64}.dmg`,
sube artefactos y espera a que **ambos** terminen antes de crear un borrador con checksums.
Solo `publish` tiene `contents: write`; el único token es `github.token`.
El disparo manual exige una etiqueta existente sobre el commit compilado; no crea etiquetas.
Una repetición puede actualizar assets de un borrador; se niega a alterar una release publicada.

Se validaron ambos YAML con Ruby/Psych y **actionlint 1.7.12**; también los 10 bloques Bash
multilínea con `bash -n`. Las herramientas descargadas para validar permanecen en `/tmp`.
No se añadieron binarios al repositorio.

## Prueba manual de app arm64

`JAVA_HOME=$(/usr/libexec/java_home -v '17+') PACKAGE_OUTPUT_DIR=/tmp/cryptocarver-install44/package bash package_macos.sh`
generó la app (~147 MiB). `file` confirma **arm64** tanto para el launcher como para libjvm.

Se lanzó `Contents/MacOS/CryptoCarver` con:

```text
HOME=/tmp/cryptocarver-install44/app-home
PATH=/tmp/cryptocarver-install44/empty-path  (directorio vacío)
JAVA_TOOL_OPTIONS=-Duser.home=/tmp/cryptocarver-install44/app-home
```

Es importante fijar también `user.home`: HOME por sí solo no simula una cuenta nueva para
Java en macOS. El log confirma la opción y se creó `.cryptocarver/settings.json` solamente
bajo el HOME de prueba. Se verificó la ventana por accesibilidad y captura, y navegar a
**Genérico → Hashing** funcionó. No se creó una cuenta macOS real ni se probó Gatekeeper
con una descarga externa: la app local no tiene la cuarentena de una descarga de GitHub.

## Límites y siguiente paso

No se probaron Mac Intel, Windows, Linux, instalación corporativa, notarización ni ejecución
en GitHub. Los instaladores Windows/Linux quedan para otro encargo, después de resolver el
presupuesto de higiene y verificar macOS en Actions. No se modifica docs/WINDOWS.md porque
no cambia ningún comando ni requisito Windows.

## Pasos del usuario (no ejecutados)

1. Revisar los commits y el bloqueo de emojis. Para publicar la rama:
   `git push -u origin luna/github-install`.
2. Integrar los cambios revisados en main y enviar main. Comprobar la CI; el control
   de emojis seguirá fallando hasta resolver el bloqueo descrito.
3. Con `pom.xml` en 2.4.0 y el commit integrado seleccionado:
   `git tag v2.4.0 && git push origin v2.4.0`.
   Si esa etiqueta ya existe, no la fuerces: usa una nueva versión en el POM.
4. Revisar el workflow de instaladores, descargar los dos DMG y comprobar SHA256SUMS.
   Probar uno en Apple Silicon y otro en Intel. Revisar la release **en borrador** y
   publicarla manualmente cuando ambos instaladores estén validados.
5. Para repetir a mano, seleccionar la etiqueta existente en `Instaladores macOS → Run workflow`.

## Validación final

La instalación del clon `clean`, con `env -i` y `-Dmaven.repo.local=/tmp/cryptocarver-install44/clean-m2`
inicialmente vacío, ejecutó literalmente `clean package`: **2.532 tests, 0 fallos, 0 errores,
1 omitido, BUILD SUCCESS, 5 min 24 s**, incluida la descarga de dependencias.
La repetición `javafx:run` y el JAR del clon `source` arrancaron correctamente, como se indica arriba.
El DMG local `CryptoCarver-2.4.0.dmg` se generó con `PACKAGE_TYPE=dmg PACKAGE_SKIP_BUILD=true`,
~84 MiB. `hdiutil verify` informó `checksum ... is VALID`.

| Comprobación | Resultado | Tiempo |
|---|---|---:|
| Compilación/tests de release, sin interfaz | 2.532 tests; 350 omitidos, 0 fallos/errores (2.182 ejecutados) | 106,36 s |
| JavaFX de CI, con forks separados por clase | 375 tests; 0 fallos/errores/omitidos; 73 launchers distintos | 222,38 s |
| Suite completa predeterminada (`mvn -o -q test`) | 2.532 tests; 0 fallos/errores, 1 omitido; un único launcher | 242,97 s |
| Doctor, Bash, YAML/actionlint, manifiesto y DMG | Correctos; se conserva el fallo de presupuesto de emojis | — |

Las suites finales se ejecutan con Temurin 21.0.8, sin perfil low-cpu y con los
argumentos JVM existentes. Los comandos Maven de CI se probaron con `-o` para usar
la caché local; solo se sustituye Xvfb por el escritorio macOS.


## Commits

| Commit | Cambio |
|---|---|
| `3a1095f` | docs(install): diagnose macOS setup and failed CI jobs |
| `2d51e89` | fix(install): select portable Maven and JDK 17 toolchains |
| `02c96aa` | feat(install): add read-only environment doctor |
| `99f2d14` | fix(ci): run hygiene budgets without ripgrep |
| `bdf2643` | fix(ci): ignore binary metadata in hygiene counts |
| `792c006` | fix(ci): make JavaFX geometry and history tests portable |
| `af13f72` | ci(pkcs11): upgrade checkout and Java setup actions |
| `4d571fb` | feat(release): build native macOS DMGs and draft releases |
| `e857a31` | docs(install): explain macOS releases, source builds and validation |
| `125eda6` | fix(ui): preserve palette glyph width after CSS application |
| `be89479` | fix(ci): isolate JavaFX software rendering between test classes |
| `1d5472d` | fix(ci): make Surefire fork reuse configurable for UI isolation |

La actualización final de este informe se guarda en un commit de documentación adicional.
El historial completo se consulta con `git log --oneline 92e7b3b..luna/github-install`.
