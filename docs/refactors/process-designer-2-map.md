# Process Designer: mapa de fase 2

Base: `main` en `26abc5f0a4f6f7b3504ea864f2e8127659d2fee2`. Rama de trabajo: `codex/process-designer-2`. Línea base: `ProcessDesignerController.java`, 1803 líneas.

## Alcance y discrepancia de nombres

El encargo identifica `selectNodeById` como un bloque de 272 líneas. En esta base, `selectNodeById(String)` ocupa 8 líneas (1331–1338) y solo busca el nodo, llama a `select(node)` y redibuja. La lógica extensa de inspector y selección está en `select(ProcessDefinition.Node)` (445–475) y `updateSelectionUi()` (1020–1082). Para preservar el objetivo semántico «inspector y selección de nodos», la fase caracteriza y extrae esos tres métodos; `selectNodeById` seguirá siendo público y conservará su firma. No hay un bloque de 272 líneas llamado `selectNodeById` en la base solicitada.

## Estado vivo leído y escrito

| Estado | Lectura | Escritura | Observación |
|---|---|---|---|
| `nodes`, `selectedNodeIds`, `selected`, `selectedConnection` | Resolución de ID, orden del par, selección simple/múltiple y conexión seleccionada | `select` añade o reinicia IDs y limpia `selectedConnection`; seleccionar enlace (fuera de esta extracción) limpia selección de nodos; actualizar UI no modifica el grafo | La identidad y el orden de `selectedNodeIds` afectan cuál nodo es fuente/destino. |
| `connections` | Detecta enlaces entre los dos nodos, puertos ocupados y destino/source | Ninguna en estos métodos | La ocupación y compatibilidad condicionan el botón o menú disponible. |
| `transientSecrets`, `dynamicInspectorRenderer` | El inspector actual se guarda antes de cambiar de nodo | `select` crea un mapa de sesión y un renderer para el nodo nuevo | El valor secreto permanece como `char[]` de sesión; no se debe copiar al modelo ni a la transcripción. |
| Inspector FXML | Renderer dinámico, nombre, etiqueta del nodo y labels de contrato | `select` muestra/oculta grupo de nombre, actualiza etiqueta/nombre, contenido de inspector y contratos | Es cableado JavaFX. El guardado previo delega en `saveSelectedNodeSettings()`, que queda fuera. |
| Acciones de conexión/borrado/inversión | Handler de puertos, representación de salida, enlaces y selecciones | `updateSelectionUi` cambia texto, visibilidad, gestión, habilitación y acciones de botones/menú | Todo depende de controles JavaFX; el cálculo de puertos disponibles y el orden source/destination es lógica de datos separable. |
| Canvas | El ID público resuelve en `nodes` | `selectNodeById` selecciona y solicita `redraw()` | El redibujado actualiza resaltado, curvas y geometría, y queda como callback de vista. |
| Telemetría, historial persistente y barra de estado | Ninguno de los métodos de selección los consulta | Ninguno de estos métodos escribe en ellos | El test de secretos debe comprobar que guardar/cambiar selección mantiene el secreto fuera de la definición/transcripción y de esas superficies. |

## Lógica de datos frente a cableado de UI

- Encontrar un nodo por ID y actualizar el conjunto/selección es coordinación de selección. `selectNodeById` no contiene las 272 líneas indicadas en el encargo; su comportamiento real es una búsqueda y dos callbacks.
- Obtener el par ordenado, hallar source/destination, resolver el descriptor y calcular puertos libres compatibles puede aislarse como lógica coordinada. `ProcessEngine` calcula la representación de salida; fallos de handler se tratan como representación desconocida.
- Guardar el inspector anterior, crear/instalar `NodeInspectorRenderer`, actualizar labels/contratos, crear `MenuItem`, fijar acciones, visibilidad/gestión y redibujar son cableado de UI.
- Riesgo de orden: `select` guarda el inspector viejo antes de cambiar `selected`, puede mantener la selección de dos nodos y construye acciones que capturan el nombre del puerto disponible. La caracterización fijará estos efectos y las transiciones de selección vacía, simple, doble y por ID.
- La vista del coordinador será un `record View` con suppliers/getters perezosos y callbacks; se pasará por llamada, no se retendrá. `ProcessDesignerController` conservará los métodos y su API pública, delegando con métodos de una línea.

## Cobertura de partida y huecos

`ProcessDesignerConnectionUITest` cubre selección de dos nodos para conectar desde teclado. `ProcessDesignerUX12UITest` cubre `selectNodeById` desde una fila de validación. `ProcessDesignerSuppliedSecretPreflightUITest` y `ProcessDesignerUndoTidyCharacterizationUITest` cubren secretos de sesión, pero no una transcripción de transiciones del inspector. Los tests de canvas ya observan vistas y selección mediante mouse.

La caracterización de fase 2 debe añadir una transcripción SHA-256 para selección simple/doble, botón/menú según puertos compatibles, selección por ID existente/inexistente, conexión seleccionada/selección vacía y actualización del inspector. Debe usar secretos sintéticos y restaurar `AppSettings`, el Shelf y el historial. Los hallazgos de producto se anotarán antes de corregirlos, en commits separados.

## Decisión de separabilidad

La selección de nodo y la presentación de inspector son separables mediante callbacks y estado consultado al invocar el coordinador. La selección de conexiones queda en el controlador porque comparte el ciclo de guardado de inspector y selección, pero no se moverá en esta fase. Cualquier acoplamiento de orden descubierto en la caracterización obliga a documentar/saltar la extracción, no a cambiar ese orden.

## Extracción aplicada

`ProcessSelectionCoordinator` recibe un `View` nuevo por invocación. Sus suppliers consultan nodos, conexiones, selección, secretos transitorios y controles JavaFX al usarlos; callbacks conectan guardado, renderer, cálculo de contratos, redibujado, conexión y texto localizado. La vista no se guarda en el coordinador. El controlador conserva `select(ProcessDefinition.Node)`, `selectNodeById(String)` y `updateSelectionUi()` como delegados de una línea; la firma pública de `selectNodeById` no cambia. `orderedConnectionPair`, la selección de conexión, el guardado del inspector y el cálculo de etiquetas/representación siguen como puertos del controlador.

La caracterización previa no encontró defectos. El SHA de la transcripción antes y después de extraer es `76ea24e9f0beb0df58d8ad4b8e0fd978e7968ac2086c3d659173bd6c22d88fdd`. El controlador queda en 1739 líneas (−64 respecto a la base). El test caracterizador enfocado pasó después de la extracción; la puerta completa de dos suites se ejecuta antes de cerrar la fase.
