# Process Designer: mapa de fase 1

Base: `main` en `6bc64a63164551c00274d0be25ccd6ba808d5ace`. Alcance: las secciones `Undo / Redo Command Pattern` y `Duplicate & Tidy Layout` de `ProcessDesignerController`.

## Estado vivo que tocan estas secciones

| Estado | Ubicación actual | Uso en esta fase |
|---|---|---|
| Definición activa | `nodes`, `connections`, `processNameField`; se lee como `ProcessDefinition` mediante `toDefinition()` | Las órdenes guardan instantáneas antes/después. Duplicar añade un nodo y ordenar cambia coordenadas. |
| Pilas de edición | `ProcessUndoRedoCoordinator.undoStack`, `redoStack` | Se conserva un máximo de 60 órdenes; una edición nueva vacía redo. Undo/redo mueven órdenes entre pilas y restauran una definición. |
| Canvas | `workflowCanvas`, `views`, `inputPortHandles`; `updateCanvasGeometry()` y `redraw()` | Duplicar/ordenar recalculan geometría y vuelven a dibujar. Restaurar por undo/redo también actualiza estas vistas mediante `load()`. |
| Selección | `selected`, `selectedNodeIds`, `selectedConnection` | Duplicar usa `selected`; la copia queda seleccionada. Restaurar una definición limpia la selección. |
| Valores secretos transitorios | `transientSecrets` | Son `char[]` fuera de `ProcessDefinition`. `load()` los limpia. La copia de nodos no debe heredar claves sensibles ni marcadores de secretos/puertos cuyo valor o enlace no se copia. |
| Modificación/guardado | No hay una bandera `dirty` o `modified` en el controlador. `processStatusLabel` informa ejecución/reset; no representa estado de edición. | La fase no añade estado de modificación nuevo. |

## Lógica de datos y cableado de UI

### Undo / redo

`recordStateChange(desc, before)` obtiene la definición posterior, clona ambas definiciones y añade una orden; después descarta redo y aplica el límite de 60. `handleUndo()` y `handleRedo()` cambian de pila y restauran por `load()`. `load()` sustituye listas, borra selección y secretos transitorios, y actualiza selección, tamaño y dibujo del canvas.

La política de instantáneas, límite y movimiento entre pilas es lógica coordinable sin JavaFX. El proveedor de definición y el restaurador son puertos (`Supplier`/`Consumer`); el controlador mantiene el enlace con `toDefinition()` y `load()`. `DesignerCommand` y `executeCommand(DesignerCommand)` son públicos y deben conservar su firma.

### Duplicar y ordenar

Duplicar crea una identidad nueva, conserva tipo/etiqueta/configuración no sensible y desplaza la posición 30 px en ambos ejes. No duplica enlaces. Al no copiar enlaces ni `transientSecrets`, también elimina marcadores `*FromFlow`, `*FromSecrets` y claves sensibles de la copia. La creación/selección del nodo, el refresco de geometría, el dibujo y el registro undo son cableado de UI; formar la copia y limpiar metadatos que dependen de enlaces/secretos es lógica de datos.

Ordenar calcula el orden topológico, asigna capas por profundidad y coloca las filas con separación fija. El cálculo/actualización de coordenadas puede vivir en un coordinador de layout; recalcular el canvas, dibujar y registrar la edición siguen conectados a la vista mediante delegados.

## Cobertura existente y huecos

| Suite existente | Cobertura actual | Hueco que cubre la caracterización de fase 1 |
|---|---|---|
| `ProcessDesignerUndoRedoTest` | Añadir, duplicar, conectar, invertir y borrar enlaces, limpiar, mover y cambiar configuración; verifica undo/redo de varias acciones. | Borrado de nodo con sus enlaces y límite de 60 órdenes; mejor transcripción estable para el conjunto de acciones. |
| `ProcessDesignerDuplicatePortUITest` | Rechaza una segunda conexión al mismo puerto y permite sustituir el enlace predeterminado. | Duplicar nodos con/sin enlaces, puertos de entrada múltiples y marcadores heredados. |
| `ProcessDesignerTraceRedactionTest` y `ProcessTelemetryRedactionTest` | Redacción de trazas y resultados de ejecución bajo perfiles de visibilidad. | Verificar que undo/redo y duplicación no publican valores sensibles en el historial, telemetría o barra de estado con `MASKED` y `REDACTED`. |

`ProcessDesignerUndoTidyCharacterizationUITest` añade esos casos y fija transcripciones mediante SHA-256. Los fixtures aíslan `AppSettings`, usan un historial temporal y restauran el estado del Shelf.

Digests de las transcripciones (SHA-256 sobre las filas unidas por `\n`, sin salto final):

| Escenario | SHA-256 |
|---|---|
| Alta, movimiento, conexión, borrado y límite | `bd51e23d16b05e0820fdddabddbbd1153413949ffd2d534d2f7a9346cac2e79f` |
| Duplicados con y sin enlaces y puertos múltiples | `e6c767633e5bd532a6e05f55f8dbe54ed2b883fb960fd562a22aa2acc3a95da1` |
| Ordenación, undo y redo | `63030e66741efc18ab040a3c7d3e0eca6982b6a51e85bf7bfc8592278b4f42ed` |
| Secretos transitorios con `MASKED` y `REDACTED` | `222095cc203b765651dac4f9924e20c7ea54ee7205942a524b9cab20d0ef2528` |

## Extracción aplicada

- `ProcessUndoRedoCoordinator`: instantáneas, comandos, pilas, límite y restauración mediante una `View` con proveedores/delegados. La vista se pasa en cada llamada y no se retiene. El controlador conserva sus métodos/API y los delega en una línea; no se pasa ni se captura un controlador.
- `ProcessLayoutCoordinator`: copia de nodos desconectada y cálculo topológico de posiciones. Recibe el nodo/definición por proveedores y delega añadir, seleccionar, refrescar y registrar estado. Es stateless, no conoce controles JavaFX y no recibe ni captura un controlador.

Se limita la extracción a esas dos secciones. `selectNodeById`, `connectToPort`, `createNodeView` y `showPreflightFailure` quedan fuera.
