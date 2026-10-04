# Process Designer: mapa de fase 4

Base para esta fase: rama `codex/process-designer-2`, fase 3 cerrada en `cace6a9`. `ProcessDesignerController.java` parte de 1590 líneas.

## Alcance real en la base

`createNodeView(ProcessDefinition.Node, Representation)` ocupa 174 líneas (536–709). El bloque `Redraw and Node/Connection Rendering` ocupa 281 líneas (504–784): `redraw`, `createNodeView`, actualización de curvas y creación de conexiones. La fase extrae ese bloque a `ProcessCanvasRenderer`.

## Estado vivo leído y escrito

| Estado | Lectura | Escritura | Observación |
|---|---|---|---|
| `nodes`, `connections`, definición actual | Validar una copia para inferir representaciones, dibujar conexiones y colocar nodos | No cambia el grafo durante `redraw`; los handlers de arrastre de nodo sí actualizan `node.x/y` | Validar la copia evita que `ProcessEngine.validate` complete puertos por defecto en la definición viva. |
| `views`, `inputPortHandles` | Borrado/redibujado y detección de puerto al soltar | Redraw vacía y reconstruye ambos índices | Pertenecen a las vistas. `handleDeleteSelected` borra un view antes del redraw. `handleClearCanvas` vacía el mapa y los hijos del pane, pero deja los círculos de entrada hasta el siguiente redraw; preservar este orden y estado en la extracción. |
| Selección: `selected`, `selectedNodeIds`, `selectedConnection` | Estilo activo/pendiente y color/grosor de enlace seleccionado | Eventos de click delegan selección al controlador | La selección inicial cambia controles sin forzar un redraw; el estilo del canvas se actualiza al próximo redraw. Mantener ese orden. |
| Posición de nodo y `snapToGrid` | Mouse press captura posición y coordenadas de escena; move calcula delta y aplica snap | Mouse move clampa coordenadas a ≥ 0, mueve view y extremos de curvas | Release actualiza geometría y guarda undo con la posición inicial restaurada en la foto previa. |
| Zoom/escala, tamaño de canvas | `workflowCanvas` tiene `Scale`; `updateCanvasGeometry()` recibe el redraw | El renderer no cambia la escala; el controlador calcula el tamaño expandido | `updateCanvasGeometry()` es público y queda como callback para no cambiar API ni mezclar esta sección con zoom/pan. |
| Estado de arrastre de conexión | Los handlers consultan `ProcessConnectionCoordinator` para source/estado/trazo | Callbacks inician, completan o cancelan el drag | El estado ya no vive en el controlador. El renderer crea los círculos, y el coordinador de conexiones los atenua/restaura por supplier. |
| `validationCounter` | `redraw` lo lee solo como contador de instrumentación | Incrementa una vez por redraw antes de intentar validar | El campo público se mantiene en `ProcessDesignerController`; el renderer lo incrementa mediante callback. |
| Controles UI | `workflowCanvas`, labels/tooltip de puertos | Se crean/añaden vistas, círculos, tooltips, curvas, estilos y handlers JavaFX | Todo el árbol visual, coordenadas de curvas y wiring de eventos son cableado JavaFX. |
| Secretos, telemetría, historial persistente | No los consulta ni renderiza | No los escribe | Los nodos se construyen desde la definición normal; `transientSecrets` no se entrega al renderer. |

## Lógica de datos frente a cableado de UI

- Validar una instantánea y asociar la representación resultante al ID del nodo es cálculo coordinable; manejar excepciones de validación dejando representaciones vacías mantiene el canvas dibujable.
- La curva entre dos nodos usa las coordenadas y el puerto destino; la asignación de extremos, offset vertical, controles de Bézier y hit-testing de puertos/nodos dependen de la vista.
- El desplazamiento aplica delta de escena→canvas, snap opcional a 10 px y clamp a cero; la restauración de una orden de movimiento debe conservar nodo/conexiones y recuperar la posición anterior.
- Los eventos de click/arrastre hacen callbacks al controlador o al `ProcessConnectionCoordinator`; el renderer no recibe ni captura el controlador. `View` se genera por llamada con suppliers de estado y callbacks. El índice de vistas y los círculos sí quedan como estado local del renderer.
- `updateCanvasGeometry()` sigue como método público del controlador, llamado como callback desde `redraw`, add/delete y arrastre.

## Cobertura de partida y huecos

`ProcessDesignerConnectionUITest` comprueba handles y conexión desde el canvas. `ProcessDesignerUndoTidyCharacterizationUITest` arrastra un view y verifica undo/redo de posición, aunque simula coordenadas manipulando el modelo. `ProcessDesignerUndoRedoTest` cubre movimiento y comando histórico; no fija representación, geometría de curva ni estilos.

La nueva `ProcessDesignerCanvasCharacterizationUITest` fijará por SHA-256 una transcripción de redibujado de definición conectada, badges de representación, curvas y etiquetas de puerto, handles, resaltado simple/múltiple y arrastre con snap/curvas/undo/redo. Usará el fixture UI, restaurará `AppSettings`, Shelf e historial; no requiere secretos. Fallos de producto se anotarán antes de corregirlos, en commits separados.

## Decisión de separabilidad

`ProcessCanvasRenderer` es separable usando el `ProcessConnectionCoordinator` ya extraído como un conjunto de callbacks/getters. El renderer no guardará un `View` ni una referencia al controlador. Si redibujar y mover nodos requieren un orden que no puede mantenerse al pasar callbacks, se documentará y se saltará la extracción.

## Cierre de fase 4

Extracción realizada en `5934d9d`: `ProcessCanvasRenderer` posee los views, handles de entrada, redibujado, creación de nodos/conexiones, geometría de curvas y handlers del canvas. El controlador entrega un `View` por operación y conserva sus métodos públicos; `validationCounter` se incrementa mediante callback. `handleClearCanvas` conserva su estado previo: limpia el mapa de vistas y el pane, pero no la lista de círculos hasta el próximo redraw.

`ProcessDesignerController` pasa de 1590 a 1333 líneas. La caracterización `ProcessDesignerCanvasCharacterizationUITest` mantiene el digest SHA-256 `e8d1bafcbc0a35484c18428979328002eb7658976c380b61f40c2a63118ff014` (64 caracteres); se verificó antes y después de extraer.

Puertas tras la extracción, ejecutadas por separado:

- `mvn -o -q test -Plow-cpu`: exit 0; 2860 tests, 0 fallos, 0 errores, 1 omitido.
- `mvn -o -q test -Plow-cpu -DrunUiTests=true`: exit 0; 2860 tests, 0 fallos, 0 errores, 1 omitido. `ExpandedViewerLifecycleUITest`: 3 tests, 0 fallos, 0 errores, 0 omitidos; no se necesitó cotejo con `main`.
