# Process Designer: mapa de fase 9 — eventos del lienzo

Base de fase `7c9647e`, controlador 965 líneas. Worktree CryptoCarver-pd-4, rama codex/process-designer-4. initCanvasEventHandlers está en líneas 167–230.

## Separabilidad y orden

Es separable sin cambiar el orden observable. initialize instala primero Scale y focusTraversable, llama initCanvasEventHandlers y luego updateCanvasGeometry; ese cableado permanece idéntico. Dentro del método se registran setOnScroll, setOnMouseClicked, setOnKeyPressed y setOnMouseMoved en ese orden. Son propiedades de eventos distintos, no listeners cuya competencia dependa del orden de registro. No se añaden filtros, handlers secundarios, runLater ni reentrancia nueva. La prioridad del único else-if de teclado se conserva íntegramente.

Los handlers de arrastre/selección en hijos siguen en ProcessCanvasRenderer/ProcessConnectionCoordinator. La rueda consume solo control/shortcut; la rueda normal se deja al ScrollPane como antes. El click solo limpia cuando target es la identidad del Pane instalado: los clicks de hijos siguen su propagación original. El teclado conserva delete/backspace, Z/Shift-Z/Y/D, Escape y flechas/Shift. MouseMoved lee la conexión live y convierte sceneToLocal en el mismo pane transformado. No se mueve el arrastre de hijos ni se sustituye el pane.

## Estado vivo

| Handler | Lecturas | Escrituras / lógica |
|---|---|---|
| Scroll | Modificadores/deltaY y currentZoom live | setZoom(+/-0.08), consume. Lógica de evento y UI. |
| Click | Identidad target/pane | selected=null, selectedNodeIds.clear, selectedConnection=null, UI, redraw, mismo orden. |
| Teclado | KeyCode/modificadores; selección live; curva activa live | Acciones existentes delete/undo/redo/duplicate; Escape limpia y cancela antes de UI/redraw; flechas mutan x/y por 1/10 antes de geometry/redraw y consume. |
| MouseMoved | Drag activo/source live, coordenadas scene y Scale del pane | updateInteractiveCurve con punto local; no consume. |

ProcessCanvasEventsCoordinator recibe una record View con suppliers de pane, zoom, selección y estado de conexión, callbacks de operaciones y actualizador de curva. No retiene controlador ni View; handlers retienen los callbacks necesarios y el pane concreto donde se instalaron, como antes. Getter perezoso y delegado de una línea. Los estados live permanecen en sus propietarios; no se copia selección ni zoom al instalar.

No lee definición ejecutable, telemetría, secretos suministrados, StatusReporter, AppSettings, historial o Shelf directamente. Las acciones delegadas conservan sus propietarios. No se inventa Supplier<StatusReporter>. initialize y API pública sin cambios.

## Caracterización y puertas

Cobertura reutilizada: ProcessCanvasZoomTest (arrastre a 0.5/1/2), ProcessCanvasGeometryTest, ProcessCanvasPerformanceTest, ProcessDesignerCanvasCharacterizationUITest (digest de selección/conexión/arrastre/undo/redo), Window y UX12. El digest canvas existente es `e8d1bafcbc0a35484c18428979328002eb7658976c380b61f40c2a63118ff014`.

Se añade ProcessDesignerCanvasEventsCharacterizationUITest antes de extraer: handlers instalados, consumo de rueda y teclas, flechas con y sin Shift, atajos Z/Shift-Z/Y/D, delete/backspace, target de click, selección vacía y Escape durante curva interactiva. Las coordenadas sceneToLocal se validan por igualdad con el transform, y en el digest se fija solo el resultado lógico de esa comprobación: no valores dependientes de ventanas/OS. Los atajos sintéticos activan control y meta para ser portables entre Linux y Mac. SHA provisional anotado antes de fijarse; no IDs, rutas, tiempos, excepciones ni aleatoriedad.

La nueva prueba restaura AppSettings, Shelf e historial aislado. Ejecución y paleta se revalidan intactas; privacidad MASKED/REDACTED en historial, Shelf, estado, visor expandido, filas/telemetría/traza mediante la caracterización existente. Tres puertas Maven seriales, informes borrados por ejecución, regla de parada original.
