# Encargo 56: Async Progress UI

Base main `966b730cf6de610f549680287e3334c144a42cdb`. Rama `codex/modern-main-async-progress`. ModernMainController: 2339 líneas antes. updateAsyncProgressDetails: 37 líneas (532–568); ámbito completo: show/update/hide/cancel (517–591), conservando isShutdown en el shell.

## Controles y estado vivo

| Entrada | Lecturas | Escrituras |
|---|---|---|
| show | nombre, locale, controles FXML | label/texto accesible, cancel enabled, box visible/managed |
| update | ProgressDetails, box.visible, controles FXML | label/texto accesible, bar ratio/accessible/visible/managed, spinner progress/accessible/visible/managed, box |
| hide | box actual | box visible/managed false; conserva valores hijos |
| cancel | executor actual vía Supplier<StatusReporter>, cancelCurrentOperation/isInCommitPhase, locale | label cancelling/finishing, cancel disabled en commit; hide si idle |

ProgressDetails es snapshot: operationName y formattedText son cadenas libres sin clasificación; bytesProcessed/totalBytes determinan ratio, elapsedTimeMs permite reconstruir texto seguro. Originalmente el shell no usa elapsedTimeMs y copia formattedText dos veces. El executor entrega callbacks en FX, rechaza updates obsoletos, publica 100% al éxito, oculta, restaura trigger y finalmente llama success/failure/cancel. Error y cancel no fuerzan 100%.

Construcción pura: record View de controles y coordinador con proveedores. Ratio y formato derivados del snapshot. Cableado: suppliers perezosos de View y StatusReporter, registro de callbacks en startup (sin cambiar su orden), lectura del perfil/locale al pintar y consulta de executor al cancelar. El coordinador no guarda controllers de módulos ni el shell; Supplier<StatusReporter> resuelve el host actual. Los campos FXML y sus nombres literales se mantienen.

## Caracterización antes de fijar SHA

Fixture FXML real en FX, configuración aislada, Shelf conservado y comprobado al restaurar, shutdown y restauración de locale. EN/ES, FULL_LAB/MASKED/REDACTED, determinate/indeterminate, nulls, cancel, commit, error y éxito reales mediante latches. Transcripción estable: tiempos controlados en snapshots y resultados booleanos en callbacks temporizados. No fijar SHA hasta revisar fallos.

CI de base verificado por API GitHub: run 37141775449, checks UX, JavaFX UI y PKCS#11 inventory success.

Primer intento de fixture: enterCommitPhase pertenece al executor, no a ProgressMonitor; corregido. Segundo intento: test.mode ejecutaba el task síncronamente e impedía observar el umbral; la fixture ahora lo desactiva solo durante execute y restaura inmediatamente el valor anterior.

Pase válido contra código de producción intacto de main: 3 tests, 3 fallos, 0 errores/omisiones. Dos fallos reales anotados ANTES de arreglar: (1) MASKED/REDACTED muestran operationName/formattedText libres, incluidos accesibles; (2) show tras hide de 100% deja bar visible/managed y spinner oculto de la operación anterior. Tercer fallo deliberado BASELINE_PENDING. Transcript previo no aceptado: SHA-256 `9119ca360952706a4712328feb0804a1960c5660d3e4f21bc7fe76656f1215d9`. Los estados de bar al terminar éxito/error/cancel/commit quedan observados, no se borran en hide.

Arreglo 1, privacidad: en perfiles restrictivos se sustituye el nombre libre por progress.operation y se reconstruye el formato desde bytes/total/elapsed; formattedText solo se conserva en FULL_LAB. Se actualiza también el accesible del spinner aunque esté oculto, para no retener texto privado de un perfil anterior. La regresión incluye transición FULL_LAB → MASKED/REDACTED. Pase: 3 tests, 2 fallos (barra anterior y BASELINE_PENDING); privacidad pasa. SHA provisional `505bb92e778ab6f30d9e4647817bf2a04c667f07d2ed16f7792c1cfd186f6b36`.

Arreglo 2, siguiente operación: show reinicia spinner indeterminado visible/managed y bar a -1 oculto/no managed, limpiando su accesible. hide conserva el último ratio (éxito/commit 1.0; error/cancel 0.25) dentro de box oculto. Pase previo a fijar: 3 tests, 1 fallo deliberado BASELINE_PENDING; ambos defectos pasan. SHA revisado `49bf6210ad4fc9261a53a23d509546e1f96164f872e6e8ba6d16e3abcdf349ee`. Se amplía cobertura de referencias FXML opcionales y restauración defensiva de Shelf sin mutarlo si no cambió.

Caracterización fijada antes de extracción: 4 tests, 0 fallos/errores/omisiones. Código previo a extracción: 2360 líneas. El SHA de 48 filas (UTF-8 con salto final) permanece `49bf6210ad4fc9261a53a23d509546e1f96164f872e6e8ba6d16e3abcdf349ee`.

## Extracción

ShellAsyncProgressCoordinator: record View con cinco controles; Supplier<StatusReporter> consultado al cancelar, sin campo de controller ni executor cacheado. El getter del coordinador es perezoso; asyncProgressView reconstruye referencias FXML en cada delegado. show/update/hide/cancel son delegados de una línea. Los callbacks del executor siguen registrados desde ShellStartupCoordinator, isShutdown/lifecycle/getOperationExecutor siguen en el shell. Locale y perfil se consultan al pintar. No hay renombrado de campos FXML ni cambio de sus cadenas literales.

Pase dirigido después de extracción con -Plow-cpu: 9 tests (AsyncProgress 4, ShellStartup 5), 0 fallos/errores/omisiones. SHA async idéntico al fijado antes de extraer; SHA startup `116b7045624997160c793f67818c6c4f42dd912650bd30b225a4633e1d58f4ba` conservado.

## Informe final

Base CI verde: [run 37141775449](https://github.com/FelipeRodriguezFonte/CryptoCarver/actions/runs/37141775449), tres checks success para `966b730`. Entorno local: macOS, Temurin 21.0.8; JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home. Suites ejecutadas secuencialmente, con directorios de informes separados y -Plow-cpu:

| Pase | Comando | Tests | Clases | Fallos | Errores | Omitidos | Exit |
|---|---|---:|---:|---:|---:|---:|---:|
| UI | `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dgroups=ui` | 472 | 94 | 3 | 0 | 0 | 1 |
| Completa | `mvn -o -q test -Plow-cpu` | 2815 | 408 | 0 | 0 | 1 | 0 |
| Main, comprobación dirigida | `mvn -o -q test -Plow-cpu -Dtest=ExpandedViewerLifecycleUITest` | 3 | 1 | 3 | 0 | 0 | 1 |

Los tres fallos UI son preexistentes y se reprodujeron con el mismo entorno en una copia aislada creada con git archive de main `966b730`: ExpandedViewerLifecycleUITest.closingTableViewerDropsItsSnapshotAndScene, closingTextViewerReleasesItsSceneWhileViewerRemainsAlive y shellShutdownClosesAnOpenResultWindow. En los tres, assertReleased todavía encuentra el Stage cerrado fuertemente alcanzable. En la suite completa las tres pruebas pasan: el resultado varía entre pases/orden de ejecución; no se atribuye una causa no comprobada ni se modifica ese código. Informes preservados en target/async-progress-ui-reports, target/surefire-reports y target/async-progress-main-lifecycle-reports. Única omisión completa: Pkcs11SessionEncapsulationTest.testSoftHsmUpdateCertificateChain.

AsyncProgressCharacterizationUITest (4) y ShellStartupCharacterizationUITest (5) pasan en ambos gates. Los defectos de progreso se observaron antes de modificar producción: el commit de caracterización 12fafb4 tiene src/main idéntico a main; blob original de ModernMainController `6a5a23c3775ecaafaa7b3a58326de8fcb1568b12` en ambas revisiones.

Líneas ModernMainController: **2339 → 2278 (−61)**. Tras los arreglos y antes de extraer: 2360 → 2278 (−82). Coordinador nuevo: **120 líneas**. updateAsyncProgressDetails original: **37 → 1 línea**. show/hide/cancel también quedan como delegados de una línea.

SHA-256 comprobados tras ambos gates, sin cambios de código entre ellos:

| Artefacto | SHA-256 |
|---|---|
| Async transcript, 48 filas UTF-8 con salto final | `49bf6210ad4fc9261a53a23d509546e1f96164f872e6e8ba6d16e3abcdf349ee` |
| Startup transcript | `116b7045624997160c793f67818c6c4f42dd912650bd30b225a4633e1d58f4ba` |
| ModernMainController.java | `da18fa63b2e5a0a2eb480ffb6391e995e37c80abb5584a552003c7738d8f91ee` |
| ShellAsyncProgressCoordinator.java | `23cdc9e3e481ef68e27cdd87ea88c15b6a520ef9a0671735994a1e4fd244f07e` |
| AsyncProgressCharacterizationUITest.java | `9a299ead1f9423c6673fdb552d75f4b7280cc5a74f8a318bbd0e150b100e795b` |

Rama codex/modern-main-async-progress con seis commits: caracterización/fallos, arreglo de privacidad, arreglo del inicio de barra, fijación del digest, extracción e informe. Un commit por arreglo, cada fallo anotado previamente. Diff contra main sin errores de whitespace. Solo cuatro archivos del encargo; sin cambios en crypto/, imágenes, .local.md ni secretos reales. AppSettings y Shelf restaurados por la fixture. Rama sin cambios pendientes tras el commit del informe.
