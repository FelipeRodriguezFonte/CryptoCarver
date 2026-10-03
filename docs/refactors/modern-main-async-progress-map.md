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
