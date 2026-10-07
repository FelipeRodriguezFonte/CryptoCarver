# Process Designer: mapa de fase 7 — ejecución

Base `62588c0b68b33a6b72b3d8f0cd670212277418a8`, worktree `CryptoCarver-pd-4`, rama `codex/process-designer-4`. Controlador inicial: 1340 líneas.

## Límites y estado vivo

| Métodos originales | Estado leído/escrito | Naturaleza y orden |
|---|---|---|
| `handleDryRunProcess` (983–1025) | Guarda inspector/selección; crea definición con secretos transitorios; lee catálogo y etiquetas; escribe tabla, salida y estado | Validación pura + presentación. Purga configuración sensible de la copia antes de presentar. |
| `handleRunProcess` (1034–1128) | Misma copia efímera; conexiones AAD/IV; controles de ejecución; callbacks públicos; cancelación | Guardar, copiar, limpiar salida/tabla, primer fallo de preflight, controles, hilo, eventos, finalización. No reordenar. |
| `handleCancelProcess` (1027–1032) | Flag `volatile` + estado JavaFX | El flag pasa al coordinador; la actualización se agenda como antes. |
| `renderExecutionResult` (1148–1263), `appendFlowPortValue` (1266–1285), `formatFlow` (1288–1291) | Eventos finales en orden; resultados; configuración efímera; AppSettings; selección e inspector nonce | Clasificación y formato originales sin cambios; tabla y traza según FULL_LAB/MASKED/REDACTED. Wrapper con firma original. |
| `configureExecutionStatusTable` (1293–1330) y visor expandido (857–865) | Columnas, tabla live, visor y ventana | Factories y botones de inspección obtienen la tabla perezosamente. El handler de visor conserva su validación y delega únicamente la apertura. |

`nodes`, conexiones, selección y secretos live siguen en el controlador. El coordinador no recibe el controlador ni almacena View: usa una record con suppliers de controles, selección y callbacks, y acciones de guardado/preflight. El View de ejecución dura durante el trabajo asíncrono. Los callbacks se consultan al invocarse, para respetar cambios posteriores a initialize. La tabla se consulta al pulsar inspección. Getter del coordinador perezoso.

StatusReporter no existe ni se usa en este controlador/flujo: no se añade un Supplier ficticio ni se modifica StatusReporter. AppSettings se consulta al renderizar; no se captura un perfil al inicializar. initialize queda intacto. El hilo agrega el evento, llama al callback y agenda progreso, en ese orden; la finalización presenta, purga secretos y llama al callback final.

## Caracterización y privacidad

Se reutiliza `ProcessDesignerExecutionCharacterizationUITest`, mergeado: ejecución completa, dry-run, filtro texto/vacío y secretos suministrados sintéticos para los tres perfiles, visor expandido, tabla, traza, estado, telemetría, undo, historial y Shelf. Restaura AppSettings/Shelf/historial; normaliza duraciones y sufijos de excepciones; no fija rutas, fechas, IDs aleatorios ni mensajes dependientes del proveedor. Digests esperados:

- `b925165010bd8ff31c1645f094b3a8073ad12bfa834347cb91212425f987afd3`
- `c8160b2b00c26b16ef19b4a43063f2c9685e058e112806dbbacf9f810596f5d1`

Los fallos anteriores a fijarlos están en `process-designer-6-characterization-failures.md`. Se verifica la base antes de extraer, sin modificar expectativas. Los cuatro tests de redacción, telemetría, secretos suministrados y caracterización se mantienen íntegros.

## Aserción de propietario

`SpecializedFeedbackHeadlessTest` debe buscar `module.process.feedback.nodeError`, `aad`, `iv` e `ivLabel` en ProcessExecutionCoordinator tras mover el preflight; `failed` se usa tanto en ProcessPreflightPresenter como en el render del coordinador; `connectionReversed` pertenece a ProcessConnectionCoordinator. Se hará explícita cada asignación de propietario, manteniendo las aserciones de presencia y paridad EN/ES. No se usan comentarios del controlador para simular propiedad.

## Puertas

Tras extracción, tres Maven seriales con informes borrados antes de cada ejecución: headless low-cpu, opt-in UI low-cpu y UI con opciones CI. Una imposibilidad de pantalla o un fallo distinto de ExpandedViewerLifecycleUITest detiene el encargo antes de fase 8.
