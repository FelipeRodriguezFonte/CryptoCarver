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

`SpecializedFeedbackHeadlessTest` debe buscar `module.process.feedback.nodeError`, `aad`, `iv` e `ivLabel` en ProcessExecutionCoordinator tras mover el preflight; `failed` se localiza en el View del controlador para ProcessPreflightPresenter y en el render del coordinador; `connectionReversed` permanece en el controlador (`handleReverseConnection`, línea 773 de la base). Se hará explícita cada asignación de propietario, manteniendo las aserciones de presencia y paridad EN/ES. No se usan comentarios del controlador para simular propiedad.

## Puertas

Tras extracción, tres Maven seriales con informes borrados antes de cada ejecución: headless low-cpu, opt-in UI low-cpu y UI con opciones CI. Una imposibilidad de pantalla o un fallo distinto de ExpandedViewerLifecycleUITest detiene el encargo antes de fase 8.

## Bloqueo y retirada

La primera puerta de extracción detectó una asignación incorrecta de propietario en el ajuste del test: se buscó `connectionReversed` en ProcessConnectionCoordinator, aunque sigue en el controlador. Es un error de este encargo, no un fallo preexistente. Se activa la regla de parada; no se inicia fase 8. La extracción y el ajuste del test se retiran íntegramente. Quedan solamente el mapa corregido y la evidencia de caracterización base, con controlador de 1340 líneas. Una reanudación deberá mover solo nodeError/aad/iv/ivLabel, comprobar failed en sus dos propietarios y mantener connectionReversed en el controlador.

## Continuación autorizada

Se retoma tras la retirada documentada: el ajuste asigna nodeError/aad/iv/ivLabel/failed a ProcessExecutionCoordinator, y mantiene failed/connectionReversed en ProcessDesignerController. Antes de las tres puertas, `mvn -o -q test -Plow-cpu -Dtest=SpecializedFeedbackHeadlessTest` pasa 2/2, sin errores ni omisiones. No se elimina ninguna comprobación de idioma o presencia.

El coordinador toma como punto de partida el borrador backup/process-designer-3-wip, cotejado con la base. La tabla se obtiene del supplier al pulsar inspección; no se retiene un snapshot de tabla. El controlador conserva wrappers, handlers y initialize. Líneas tras extracción: 1030, desde 1340. Las reglas de redacción y los tests de privacidad permanecen sin modificaciones.

## Cierre de continuación

Las tres puertas pasan en serie: headless 445 informes / 2911 pruebas / 0 fallos / 0 errores / 1 omitida; UI opt-in 125 / 541 / 0 / 0 / 0; UI con opciones CI 125 / 541 / 0 / 0 / 0. Digests de ejecución y privacidad intactos y comprobados en ambas puertas UI. SpecializedFeedbackHeadlessTest pasó 2/2 previamente. No hubo fallo intermitente de ExpandedViewerLifecycleUITest. La extracción queda verificada y separada de paleta/eventos.
