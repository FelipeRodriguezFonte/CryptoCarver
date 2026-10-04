# Process Designer: mapa de fase 1

Base: `main` en `6bc64a63164551c00274d0be25ccd6ba808d5ace`. Alcance: las secciones `Undo / Redo Command Pattern` y `Duplicate & Tidy Layout` de `ProcessDesignerController`.

## Estado vivo que tocan estas secciones

| Estado | Ubicación actual | Uso en esta fase |
|---|---|---|
| Definición activa | `nodes`, `connections`, `processNameField`; se lee como `ProcessDefinition` mediante `toDefinition()` | Las órdenes guardan instantáneas antes/después. Duplicar añade un nodo y ordenar cambia coordenadas. |
| Pilas de edición | `undoStack`, `redoStack` (`ArrayDeque<DesignerCommand>`) | Se conserva un máximo de 60 órdenes; una edición nueva vacía redo. Undo/redo mueven órdenes entre pilas y restauran una definición. |
| Canvas | `workflowCanvas`, `views`, `inputPortHandles`; `updateCanvasGeometry()` y `redraw()` | Duplicar/ordenar recalculan geometría y vuelven a dibujar. Restaurar por undo/redo también actualiza estas vistas mediante `load()`. |
| Selección | `selected`, `selectedNodeIds`, `selectedConnection` | Duplicar usa `selected`; la copia queda seleccionada. Restaurar una definición limpia la selección. |
| Valores secretos transitorios | `transientSecrets` | Son `char[]` fuera de `ProcessDefinition`. `load()` los limpia. La copia de nodos no debe heredar claves sensibles ni marcadores de secretos/puertos cuyo valor o enlace no se copia. |
| Modificación/guardado | No hay una bandera `dirty` o `modified` en el controlador. `processStatusLabel` informa ejecución/reset; no representa estado de edición. | La fase no añade estado de modificación nuevo. |

## Lógica de datos y cableado de UI

### Undo / redo

`recordStateChange(desc, before)` obtiene la definición posterior, clona ambas definiciones y añade una orden; después descarta redo y aplica el límite de 60. `handleUndo()` y `handleRedo()` cambian de pila y restauran por `load()`. `load()` sustituye listas, borra selección y secretos transitorios, y actualiza selección, tamaño y dibujo del canvas.

La política de instantáneas, límite y movimiento entre pilas es lógica coordinable sin JavaFX. El proveedor de definición y el restaurador son puertos (`Supplier`/`Consumer`); el controlador mantiene el enlace con `toDefinition()` y `load()`. `DesignerCommand` y `executeCommand(DesignerCommand)` son públicos y deben conservar su firma.

### Duplicar y ordenar

Duplicar crea una identidad nueva, conserva tipo/etiqueta/configuración no sensible y desplaza la posición 30 px en ambos ejes. No duplica enlaces. La creación/selección del nodo, el refresco de geometría, el dibujo y el registro undo son cableado de UI; formar la copia y limpiar metadatos que dependen de enlaces/secretos es lógica de datos.

Ordenar calcula el orden topológico, asigna capas por profundidad y coloca las filas con separación fija. El cálculo/actualización de coordenadas puede vivir en un coordinador de layout; recalcular el canvas, dibujar y registrar la edición siguen conectados a la vista mediante delegados.

## Cobertura existente y huecos

| Suite existente | Cobertura actual | Hueco que cubre la caracterización de fase 1 |
|---|---|---|
| `ProcessDesignerUndoRedoTest` | Añadir, duplicar, conectar, invertir y borrar enlaces, limpiar, mover y cambiar configuración; verifica undo/redo de varias acciones. | Borrado de nodo con sus enlaces y límite de 60 órdenes; mejor transcripción estable para el conjunto de acciones. |
| `ProcessDesignerDuplicatePortUITest` | Rechaza una segunda conexión al mismo puerto y permite sustituir el enlace predeterminado. | Duplicar nodos con/sin enlaces, puertos de entrada múltiples y marcadores heredados. |
| `ProcessDesignerTraceRedactionTest` y `ProcessTelemetryRedactionTest` | Redacción de trazas y resultados de ejecución bajo perfiles de visibilidad. | Verificar que undo/redo y duplicación no publican valores sensibles en el historial, telemetría o barra de estado con `MASKED` y `REDACTED`. |

`ProcessDesignerUndoTidyCharacterizationUITest` añade esos casos y fija transcripciones mediante SHA-256. Los fixtures aíslan `AppSettings`, usan un historial temporal y restauran el estado del Shelf.

## Extracción prevista

- `ProcessUndoRedoCoordinator`: instantáneas, comandos, pilas, límite y restauración mediante una `View` con proveedores/delegados. El controlador conserva sus métodos/API y los delega en una línea; no se pasa un controlador al coordinador.
- `ProcessLayoutCoordinator`: copia de nodos desconectada y cálculo topológico de posiciones. Recibe el nodo/definición por proveedores y delega añadir, seleccionar, refrescar y registrar estado; no conoce controles JavaFX ni recibe un controlador.

Se limita la extracción a esas dos secciones. `selectNodeById`, `connectToPort`, `createNodeView` y `showPreflightFailure` quedan fuera.
