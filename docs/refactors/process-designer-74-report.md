# Encargo 74 — informe final

Trabajo limitado a `/Users/feliperodriguezfonte/dev/CryptoCarver-pd-4`, rama `codex/process-designer-4`, base `62588c0b68b33a6b72b3d8f0cd670212277418a8`. No merges, push ni cambios de rama. No se delegó.

## Resultado por fase

| Fase | Estado | Líneas controlador antes/después | Motivo |
|---|---|---|---|
| 1: ejecución / mapa 7 | Parcial: mapa y caracterización verificada; extracción retirada | 1340 / 1340 (borrador ensayado: 1030) | Primera puerta falla por asignación de propietario incorrecta en mi ajuste de SpecializedFeedbackHeadlessTest. Regla de parada aplicada. |
| 2: paleta / mapa 8 | Saltada; no iniciada | 1340 / 1340 | No superar la puerta anterior impide iniciar la siguiente fase. |
| 3: eventos / mapa 9 | Saltada; no iniciada | 1340 / 1340 | Mismo bloqueo; no se afirma que los eventos sean inseparables. |

initialize no se modificó. No se entregan coordinadores nuevos ni tests ajustados: ambos cambios de fuente ensayados y el fichero nuevo se retiraron íntegramente al cerrar Maven. Solo permanecen documentación y evidencia verificadas.

## Caracterización y digests

Antes de extraer, `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=ProcessDesignerExecutionCharacterizationUITest,ProcessDesignerPaletteUITest`: 2 informes, 3 pruebas, 0 fallos, 0 errores, 0 omitidas, exit 0. JavaFX pudo ejecutarse en pantalla; no hubo bloqueo de entorno ni compilación.

Digests completos verificados por el test existente, sin modificar expectativas:

- Ejecución completa + dry-run + filtro texto/vacío: `b925165010bd8ff31c1645f094b3a8073ad12bfa834347cb91212425f987afd3`.
- Secretos sintéticos suministrados + preflight + FULL_LAB/MASKED/REDACTED: `c8160b2b00c26b16ef19b4a43063f2c9685e058e112806dbbacf9f810596f5d1`.

Se mantiene la normalización portable de duraciones y excepciones JDK/proveedor. No se fijan rutas, fechas, valores aleatorios ni identificadores de ejecución. Los fallos anteriores a fijar esos digests siguen documentados en process-designer-6-characterization-failures.md. La ejecución base de este encargo no produjo nuevos fallos de caracterización.

La caracterización verifica explícitamente que la clave inventada no aparece bajo MASKED/REDACTED en historial, Shelf, estado, visor expandido, filas, telemetría ni traza; restaura AppSettings, Shelf e historial. La extracción no llegó a una puerta UI completa, por lo que no se presenta como verificada su privacidad. Las fases 2 y 3 no tienen evidencia posterior a cambios porque no se iniciaron.

## Puertas por fase

Los informes se borraron antes de cada Maven y se contaron solo los XML nuevos de esa ejecución. Un único Maven activo en cada momento.

| Fase | Comando / puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Resultado |
|---|---|---:|---:|---:|---:|---:|---|
| 1 | `mvn -o -q test -Plow-cpu` | 445 | 2911 | 1 | 0 | 1 | exit 1; bloqueo |
| 1 | `mvn -o -q test -Plow-cpu -DrunUiTests=true` | — | — | — | — | — | No ejecutada tras parada |
| 1 | `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test` | — | — | — | — | — | No ejecutada tras parada |
| 2 | Las tres puertas anteriores | — | — | — | — | — | No ejecutadas; fase no iniciada |
| 3 | Las tres puertas anteriores | — | — | — | — | — | No ejecutadas; fase no iniciada |

Bloqueo concreto: `SpecializedFeedbackHeadlessTest.specializedValidationFeedbackHasDistinctEnglishAndSpanishKeys`: `ProcessConnectionCoordinator must use module.process.feedback.connectionReversed`. Esa clave sigue en ProcessDesignerController, mientras que el ajuste ensayado la buscaba en ProcessConnectionCoordinator. Es un error introducido en este encargo, no preexistente. El mapa 7 corrige la propiedad: nodeError/aad/iv/ivLabel pasan a ejecución; failed se verifica en controlador y ejecución; connectionReversed permanece en controlador. No se ha relajado ni eliminado una aserción.

No se observó un fallo de ExpandedViewerLifecycleUITest. La advertencia de traducción Wallet / eIDAS ya estaba en la caracterización de la base; no se intentó corregirla. No se atribuye el bloqueo a JDK 25 ni a main.

## Higiene, archivos y commits

Los comandos exactos del job quality-gates de `.github/workflows/ui-tests.yml` pasan: estilos FXML en línea **0**, emojis **325 / 325**. Se verificaron también después de retirar la extracción. No se añadieron claves de idioma, imágenes, .local.md ni secretos reales.

Confirmado con diff respecto a la base: no se tocó ningún fichero de crypto/, pom.xml, ModernMainController, UiStateSnapshot, StatusReporter ni OperationResult. No hay cambios finales en código de producción ni tests. El controlador conserva 1340 líneas. La rama queda limpia tras el commit del informe; los logs e informes Maven permanecen en target, ignorado por Git.

Commits del encargo:

1. `ebd204a` — docs: map process execution extraction phase 7.
2. `14dd46f` — test: record verified execution characterization baseline.
3. Commit que contiene este informe — docs: record phase 7 gate failure and stop assignment 74 (corrige mapa y documenta retirada).
