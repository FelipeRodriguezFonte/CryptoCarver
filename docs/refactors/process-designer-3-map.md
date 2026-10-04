# Process Designer: mapa de fase 3

Base para esta fase: rama `codex/process-designer-2`, fase 2 cerrada en `4987e73`. `ProcessDesignerController.java` parte de 1739 líneas.

## Alcance real en la base

El método `connectToPort(String)` ocupa 45 líneas (1095–1139 en la base de fase). El flujo completo de conexión, contando `startConnectionDrag`, actualización/cancelación del trazo, completar por puerto, completar sobre nodo y la política del puerto por defecto, ocupa aproximadamente 177 líneas. La fase mueve ese flujo y sus dos campos de estado (`connectionDragSourceNode`, `interactiveConnectionCurve`) a `ProcessConnectionCoordinator`; conserva el registro de círculos de entrada (`inputPortHandles`) con el renderizador del canvas para la fase 4.

## Estado vivo leído y escrito

| Estado | Lectura | Escritura | Observación |
|---|---|---|---|
| `nodes`, `connections` | Resolver el destino, su handler y puertos; encontrar fuente/destino; comprobar ocupación y representación compatible | Quitar enlace predeterminado anterior cuando se reemplaza; añadir enlace nuevo | El port por defecto debe concordar con `ProcessEngine.validate`; conexiones con puertos explícitos no se reemplazan. |
| `selectedNodeIds`, `selected` | `connectToPort` obtiene el par ordenado y fuente/destino | Completar arrastre establece el par; conectar deja seleccionado destino o fuente reutilizable de clave y limpia el par | Fuentes reutilizables de clave son AES_KEY_GENERATE, KDF_PBKDF2 y RSA_KEYPAIR_GENERATE. |
| Configuración del nodo | Para destino/fuente y si hay `keyFromFlow` | Al conectar al puerto `key`, pone `keyFromFlow=true` en el destino | Es metadato de flujo, no un valor secreto. No se elimina al reemplazar o borrar una conexión en este fragmento; caracterizar y documentar antes de decidir si es defecto. |
| Arrastre: `connectionDragSourceNode`, `interactiveConnectionCurve` | Mover el puntero, completar al soltar sobre nodo/puerto y detectar autoenlace | Iniciar crea la curva, actualizar cambia sus extremos; cancelar quita la curva y limpia ambos campos | La curva es hija del canvas; el estado se comparte entre handlers de mouse, Escape y renderizado. |
| `inputPortHandles` | Inicio/cancelación recorre los círculos y lee `PortHandleData` | Redraw vacía y `createNodeView` los registra; arrastre ajusta opacidad para compatibilidad | Se conserva en el controlador/renderizador en esta fase para no anticipar la fase 4. |
| Canvas, salida de ejecución | Feedback de incompatibilidad, ambigüedad, puerto ocupado y éxito; curva de arrastre/context menu | Añadir/quitar curva temporal; escribir feedback; abrir menú contextual para puertos múltiples | Cableado JavaFX y side effects visuales. `connectToPort` actualiza UI, redibuja y registra undo tras mutar el grafo. |
| Undo, secretos, telemetría e historial persistente | `toDefinition()` toma la foto previa | `recordStateChange` guarda antes/después al conectar con éxito | Las conexiones no copian valores de secretos. La rama temprana de puerto ocupado no añade enlace ni orden. No se ejecuta telemetría ni se escribe historial persistente. |

## Lógica de datos frente a cableado de UI

- La política de puerto por defecto (puerto único; `payload` si existen `payload` y `key`; de otro modo ambiguo), la elegibilidad de puertos según ocupación/representación, la elección de conexión sustituible, la semántica de la marca `keyFromFlow` y la decisión de conservar una fuente reutilizable son lógica de estado del flujo.
- Buscar destinos, resolver `ProcessNodeHandler`/`Representation`, mutar `nodes`/`connections`/selección y registrar undo son coordinables mediante proveedores y delegados.
- Construir/añadir la curva JavaFX, mover sus extremos, atenuar círculos, mostrar menú contextual y escribir feedback son cableado de UI. El cálculo de los puntos de control de curva se comparte con el renderer; durante esta fase será un callback y se revisará al extraer el canvas.
- Las operaciones deben mantener su orden: cancelar drag antes de completar, asignar el par de selección antes de resolver el puerto, y no registrar undo ni redibujar cuando se rechaza una conexión ocupada/incompatible.
- `Escape` actualmente quita curva y estado y luego redibuja el canvas; `cancelConnectionDrag()` restaura los círculos a 1.0. La transcripción caracteriza la cancelación al soltar fuera y al completar sobre un puerto ocupado; Escape queda conectado al mismo callback de cancelación antes del redibujado.
- El coordinador tendrá `record View` efímero con suppliers/getters perezosos y callbacks de estado/UI. No retendrá vista ni controlador. Las funciones que el renderer de fase 4 necesite consultar (nodo de origen y trazo activo) se expondrán como getters del coordinador sin cambiar la API pública del controlador.

## Cobertura de partida y huecos

`ProcessDesignerConnectionUITest` cubre conexión por arrastre, feedback de incompatibilidad y la ruta de seleccionar dos nodos/conectar. `ProcessDesignerDuplicatePortUITest` cubre rechazo de puertos ocupados y reemplazo de enlace predeterminado. `ProcessDesignerUndoTidyCharacterizationUITest` usa completar por puerto para preparar undo/layout, pero no fija una transcripción propia del arrastre.

La nueva `ProcessDesignerConnectionCharacterizationUITest` añadirá una transcripción SHA-256 para empezar/mover/cancelar/soltar, compatibilidad y opacidad de puertos, completar puertos explícitos, reemplazo/rechazo, feedback y selección/undo. Usará fixtures JavaFX reales; no guardará secretos ni cambiará tests existentes. Aserciones de producto que fallen se documentarán en `process-designer-3-characterization-failures.md` antes de corrección o de fijar SHA.

## Decisión de separabilidad

El flujo de conexión puede moverse solo si mantiene el mismo orden de selección, mutación, feedback, dibujo y undo. La transcripción cubre cancelación al soltar en vacío y al intentar completar sobre un puerto ocupado; el handler Escape ahora delega en el mismo cancelador antes de volver a dibujar. Si los tests muestran que la curva, los círculos y el renderer dependen de un orden imposible de preservar con callbacks, se documentará y se saltará la extracción.

## Hallazgo y arreglo previo a la extracción

La caracterización encontró que Undo retiraba un enlace al puerto `key` pero dejaba `keyFromFlow=true`. `connectToPort()` tomaba una definición superficial antes de mutar la configuración viva. El hallazgo se anotó en `process-designer-3-characterization-failures.md`; el arreglo separado `a186257` toma `snapshot(toDefinition())` antes de mutar. La caracterización confirmó que Undo quita enlace y marca y Redo los restaura.

## Extracción aplicada

`ProcessConnectionCoordinator` posee ahora únicamente la fuente de arrastre y la curva temporal. La lógica de `startConnectionDrag`, movimiento/cancelación/compleción, cálculo de puertos por defecto, compatibilidad/ocupación, política de `keyFromFlow`, selección posterior y `connectToPort` vive en ese coordinador. El `View` se crea para cada llamada y contiene suppliers para grafo, selección y controles, además de callbacks para geometría de curva, inspector, feedback, dibujo y undo; el coordinador no guarda la vista ni una referencia al controlador. `isDragging()`, `dragSourceNode()` e `interactiveCurve()` permiten que los handlers existentes y el próximo renderer consulten el estado.

`inputPortHandles`, `PortHandleData`, `addConnectionView`, `updateCurveControls` y `createNodeView` permanecen en el canvas/controlador para la fase 4. Los métodos de conexión del controlador conservan su visibilidad y firma y delegan en una línea. La caracterización mantiene el SHA `e8baa0c2d865ec4f5267eb2cc6aeecdaae884985cb7b6be4686151adb1b97310` tras la extracción. `ProcessDesignerController.java` queda en 1590 líneas (1739 antes de fase 3, −149 líneas). El test caracterizador enfocado pasa; las dos suites de puerta se ejecutan antes de cerrar la fase.
