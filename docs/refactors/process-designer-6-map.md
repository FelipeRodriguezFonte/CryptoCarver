# Process Designer: mapa de fase 6

Base: `main` (`9558bb0b`), rama de trabajo `codex/process-designer-3-v2`. `ProcessDesignerController.java` parte de 1340 líneas. Esta fase aborda solo ejecución completa, ejecución en seco, cancelación, tabla de telemetría y traza. La paleta y los eventos del canvas quedan para sus fases propias.

## Métodos y límites actuales

- `handleDryRunProcess()` (983–1028) guarda el inspector, crea una definición efímera, calcula el resumen, elimina de esa copia las claves sensibles y presenta filas, texto y estado.
- `handleCancelProcess()` (1027–1032) marca cancelación y agenda el texto de estado en JavaFX.
- `handleRunProcess()` (1034–1128) guarda el inspector, crea la definición efímera, limpia la salida y la tabla, valida cifrados en orden, prepara progreso/cancelación y callbacks, ejecuta `ProcessEngine` en un hilo y agenda el cierre en JavaFX.
- `handleOpenExpandedExecutionResult()` (857–865) lee la salida actual y abre el visor expandido o informa que debe ejecutarse primero.
- `renderExecutionResult()` (1148–1263) filtra eventos `RUNNING`, llena la tabla y construye la traza según el perfil de visibilidad. Los tests existentes llaman este método directamente; se conserva como wrapper.
- `appendFlowPortValue()` (1266–1285) aplica visibilidad a puertos conectados y añade el valor de flujo disponible.
- `formatFlow()` (1288–1291) representa formato y tamaño.
- `configureExecutionStatusTable()` (1293–1335) conecta columnas y botones de inspección.

Los números son orientativos y se volverán a medir al cerrar la fase.

## Estado vivo que usa cada flujo

| Estado | Lectura | Escritura | Clasificación |
|---|---|---|---|
| `selected`, campos del inspector, `nodes`, conexiones | `saveSelectedNodeSettings()` y `toExecutableDefinition()` | Se persisten los campos en el modelo live antes de crear la copia ejecutable | Cableado UI antes de validación/ejecución; no reordenar. |
| `transientSecrets` | `toExecutableDefinition()` incorpora copias de los valores suministrados | Se purga únicamente la configuración sensible de la copia efímera tras dry-run o al acabar la presentación de ejecución | Estado sensible. Nunca moverlo a la traza persistente ni cambiar el orden de limpieza. |
| Algoritmos y conexiones AAD/IV | El preflight recorre la definición efímera en orden | En fallo temprano modifica la tabla/salida vía `showPreflightFailure`; no altera botones, progreso, historial ni telemetría | Decisión de dominio seguida de presentación UI. Se informa el primer fallo. |
| Cancelación | `ExecutionContext` consulta el flag volátil durante el trabajo | Reset tras pasar preflight; el handler de cancelar lo activa; la finalización lo consulta | Estado concurrente del coordinador. Mantener `volatile` y el comportamiento actual. |
| Visor expandido | Lee `executionOutputArea` y la ventana de `workflowCanvas`; las celdas de inspección leen la tabla | Abre el contenido en un `ExpandedTextViewer` o informa que la traza está vacía | Presentación UI. Mantener propietario, título y comportamiento de error. |
| Eventos y resultado | Cola concurrente, `FlowValue`, nodos de la definición | Los eventos se envían primero al callback público y luego a la actualización UI de progreso | Cableado asíncrono; conservar callback y orden de actualización. |
| Controles de ejecución | Lee tabla, columnas, botones, progreso, estado, salida, nodo seleccionado y control de inspector | Limpia/puebla tabla y salida, cambia estados de controles y puede restaurar el nonce del nodo cifrador seleccionado | Presentación UI. Los controles se obtienen de forma perezosa por llamada. |
| Callbacks públicos | `onNodeExecutionEvent`, `onExecutionFinished` | No los cambia; consulta los campos al invocarlos para respetar reasignaciones hechas después de `initialize()` | Compatibilidad observable por los tests y el anfitrión. |
| Perfil de visibilidad | `AppSettings.getSecretVisibilityProfile()` al renderizar | No lo cambia | Lógica de clasificación y presentación. Conservar íntegros `FULL_LAB`, `MASKED` y `REDACTED`. |
| Historial y Shelf | Sin acceso directo en estos métodos | No deben recibir la definición efímera ni secretos | Comprobar con fixtures aislados y secretos sintéticos. |

`StatusReporter` no participa en este flujo; no se modifica.

## Orden observable que debe permanecer

1. Guardar la selección antes de crear la definición ejecutable.
2. En `handleRunProcess`, limpiar salida/tabla antes del preflight y detenerse en el primer fallo antes de cambiar botones, progreso, estado o publicar eventos.
3. Al pasar preflight, resetear la cancelación, habilitar/deshabilitar controles, poner progreso a cero y mostrar estado «running».
4. Dentro del listener de ejecución, agregar el evento, consultar y ejecutar el callback público, y después agendar el progreso/estado en JavaFX.
5. La finalización agendada actualiza botones y resultado/cancelación, después llama a `renderExecutionResult`, borra secretos de la definición efímera y, al final, invoca `onExecutionFinished`.
6. La traza descarta estados `RUNNING`, conserva orden de final-eventos y clasifica clave, nonce, material generado y valores de puerto de acuerdo con el perfil. No se cambia ninguna regla de redacción.

## Lógica frente a cableado de UI

El preflight de cifrado, el cálculo del resumen dry-run, la selección de eventos finales, la clasificación de valores y el formato de la traza son lógica del coordinador. Interactuar con JavaFX, crear el hilo, usar `Platform.runLater`, configurar columnas, presentar preflight y capturar ventanas son cableado UI. `ProcessExecutionCoordinator` recibirá una `record View` con callbacks y suppliers perezosos por invocación; no retendrá el `View`, controles ni una referencia al controlador. El controlador tendrá un getter perezoso para el coordinador. Los métodos existentes quedan como delegados de una línea, incluido `renderExecutionResult`, cuya visibilidad/firmas observadas se preservan.

## Caracterización y puertas de privacidad

La caracterización de ejecución fijará por SHA-256 una transcripción determinista de ejecución completa, dry-run, primer fallo de preflight, filtros de paleta y superficies de privacidad para `FULL_LAB`, `MASKED` y `REDACTED`. Los valores aleatorios, duraciones, rutas, fechas, orden no definido y textos variables del JDK/proveedor no forman parte del digest; las excepciones se normalizan como `<jdk-exception>`. Los secretos usados son sintéticos. Bajo `MASKED`/`REDACTED` se inspeccionan explícitamente inspector, salida expandida, barra de estado, telemetría, traza, historial, Shelf y estructuras de undo.

No se edita ni se relaja `ProcessDesignerTraceRedactionTest`, `ProcessTelemetryRedactionTest` o `ProcessDesignerSuppliedSecretPreflightUITest`. También se mantienen como cobertura `ProcessDesignerWindowUITest`, `ProcessDesignerUX12UITest`, `ProcessDesignerPreflightCharacterizationUITest` y `ProcessDesignerPaletteUITest`.

Cada commit separará mapa, caracterización, extracción y cualquier reparación justificada por un fallo anotado antes de arreglarlo. Tras extracción se ejecutan en serie las tres puertas Maven indicadas en el encargo y se registra su recuento.
