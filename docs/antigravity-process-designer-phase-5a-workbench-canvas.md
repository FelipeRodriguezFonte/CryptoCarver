# Process Designer — Fase 5A: Workbench, lienzo expandible e inspector por esquema

Esta fase no añade ninguna operación criptográfica nueva. Convierte el Process Designer
en un banco de trabajo utilizable y sustituye el inspector codificado a mano por uno
dirigido por esquema, que es el requisito previo para la Fase 5B.

Lee antes `docs/PROCESS_DESIGNER_GAP_ANALYSIS_PHASE_5.md`: contiene el diagnóstico con
evidencia en archivo y línea que justifica cada requisito de abajo.

No regreses `FlowValue`, `Representation`, los contratos de puertos, la negociación de
`ProcessEngine.validate()`, `FileWritePolicy`, la no persistencia de secretos, la
semántica de Dry Run ni el contrato de telemetría `NodeExecutionEvent`.

---

## 1. Objetivo

1. El diseñador deja de vivir dentro de un `TitledPane` colapsado del acordeón
   **Generic** y pasa a disponer de toda el área de contenido, más una ventana propia.
2. El lienzo crece con el contenido, admite zoom y paneo, y ningún nodo puede quedar
   fuera de alcance.
3. La configuración de un nodo se describe con un **descriptor declarativo**; el
   inspector se genera a partir de él. `select()` deja de crecer con cada tipo nuevo.
4. La paleta pasa de menús anidados a un panel buscable alimentado por el mismo
   descriptor.

---

## 2. Alcance obligatorio

### 2.1 Superficie de trabajo

- Registrar el Process Designer como **módulo navegable propio** en
  `UiNavigationRegistry` y en el `NavigationRail`, con el mismo patrón que los paneles
  ICSF. Al navegar a él debe ocupar el área de contenido completa.
- Mantener compatibilidad: la entrada actual en `generic.fxml:7` puede convertirse en un
  lanzador que navegue al módulo, pero **no puede quedar un segundo diseñador vivo con
  estado duplicado**. Si se mantienen ambos puntos de entrada, debe compartirse una única
  instancia de estado.
- Añadir **«Abrir en ventana»**: un `Stage` propio, redimensionable y maximizable,
  siguiendo el precedente de `ClipboardShelfWindow`. La ventana y la vista incrustada
  operan sobre el mismo proceso; cerrar la ventana no pierde el trabajo.
- Añadir **modo concentración** (`Shortcut+Shift+F`): oculta barra de herramientas y
  paneles de ejecución y deja el lienzo a pantalla completa del módulo. Reversible con la
  misma combinación y con `Escape`.

### 2.2 Lienzo expandible

- Tras cada cambio de disposición, el lienzo debe recalcular su tamaño:
  `prefWidth  = max(anchoViewport, maxNodeX + anchoNodo + margen)` y
  `prefHeight = max(altoViewport, maxNodeY + altoNodo + margen)`, con margen ≥ 200 px.
  **Ningún nodo puede quedar fuera del área desplazable.**
- `fitToWidth` debe pasar a `false` para habilitar desplazamiento horizontal.
- **Zoom**: transformación `Scale` sobre el contenido del lienzo, rango 25 %–400 %.
  - `Ctrl`/`Cmd` + rueda, anclado al puntero;
  - botones `−` / `+` y porcentaje visible y editable;
  - `Shortcut+0` restablece al 100 %;
  - `Shortcut+Shift+0` ajusta al contenido (*fit to content*).
- **Paneo**: barra espaciadora + arrastre, o arrastre con botón central. El paneo del
  `ScrollPane` no debe activarse cuando el gesto empieza sobre un nodo o un puerto.
- **Arrastre correcto bajo zoom**: sustituir el cálculo en coordenadas de escena de
  `ProcessDesignerController.java:1203-1204` por conversión explícita
  `workflowCanvas.sceneToLocal(...)`. El nodo debe seguir al puntero exactamente a
  cualquier escala.
- **Rejilla**: fondo con rejilla visible y ajuste opcional a 10 px, con conmutador
  persistido en la sesión.
- Minimapa: deseable, **no bloqueante**.

### 2.3 Rendimiento del lienzo

- Prohibido llamar a `redraw()` o a `ProcessEngine.validate()` desde un manejador de
  `MOUSE_DRAGGED`. Mover un nodo debe actualizar `layoutX`/`layoutY` de la vista existente
  y recalcular únicamente las conexiones incidentes.
- La revalidación completa se ejecuta al soltar el nodo, o con antirrebote ≥ 150 ms, o
  cuando cambia la topología del grafo — nunca por píxel.
- **Criterio de aceptación medible**: en un proceso de 40 nodos, un gesto de arrastre que
  produzca 100 eventos `MOUSE_DRAGGED` debe provocar **como máximo una** invocación de
  `ProcessEngine.validate()`. Se comprueba con un contador instrumentado en la prueba.

### 2.4 Conexiones

- Cada puerto de entrada y la salida se representan como **manejadores circulares
  visibles**, con `Tooltip` que muestre el nombre del puerto y las representaciones
  aceptadas.
- Arrastrar desde la salida hasta un puerto de entrada crea la conexión. Durante el
  gesto, los puertos incompatibles se atenúan y los compatibles se resaltan; se usa el
  mismo criterio que aplica `ProcessEngine.validate()`, no una copia.
- Si el usuario suelta sobre un destino inválido, no se crea la conexión y se muestra el
  motivo exacto devuelto por el motor.
- **No eliminar** el flujo actual «seleccionar 2 bloques → Connect»: es la ruta accesible
  por teclado y hay pruebas que dependen de ella.
- Las conexiones se dibujan como curvas cúbicas, se resaltan al pasar el ratón, se
  seleccionan con clic, muestran el nombre del puerto destino y se borran con `Supr`.

### 2.5 Inspector

- Envolver `nodeInspector` en un `ScrollPane` con `fitToWidth="true"`.
- Los botones de acción («Save block settings», «Connect to…», «Reverse», «Delete») pasan
  a un pie fijo que **no se desplaza** y por tanto nunca queda recortado.
- El cuerpo del inspector se genera dinámicamente a partir del descriptor del nodo
  (§2.6). Los 27 grupos de campos codificados en `process_designer.fxml:104-260` se
  retiran salvo los que el renderizador reutilice como plantillas.

### 2.6 Descriptor de nodo y catálogo — el habilitador

Introducir en `com.cryptocarver.model.process` un modelo declarativo. Nombres y forma
concretos, ajustables sólo si se justifica:

```java
public enum ParameterKind {
    TEXT, MULTILINE, NUMBER, HEX, COMBO, CHECKBOX, PASSWORD, FILE_OPEN, FILE_SAVE
}

public record NodeParameter(
    String key,                 // clave en node.configuration
    String labelKey,            // clave i18n, nunca literal
    ParameterKind kind,
    List<String> options,       // sólo COMBO
    String defaultValue,
    boolean sensitive,          // jamás serializado
    String helpKey,             // opcional
    String visibleWhen          // opcional, "otraClave=valor"
) {}

public record NodeDescriptor(
    String type,                // "HASH", "PIN_BLOCK_ENCODE", ...
    String category,            // grupo de la paleta
    String labelKey,
    String descriptionKey,
    String icon,
    List<NodeParameter> parameters
) {}
```

- `ProcessNodeHandler` gana `default List<NodeDescriptor> descriptors() { return List.of(); }`.
  **Los ocho handlers existentes deben migrarse**: al terminar la fase, todo tipo
  devuelto por `supportedTypes()` tiene descriptor. Una prueba lo verifica.
- Nuevo `NodeCatalog` que agrega los descriptores de los handlers registrados en
  `ProcessEngine`. Es la **única** fuente de verdad para: paleta, inspector, valores por
  defecto al crear un nodo, lista blanca de serialización y claves i18n.
- Nuevo `NodeInspectorRenderer` que construye el formulario desde el descriptor y
  lee/escribe `node.configuration` de forma genérica.
- `addNode()` deja de contener el bloque de valores por defecto codificados
  (`ProcessDesignerController.java:1113-1156`): los toma del descriptor.
- `select()` y `saveSelectedNodeSettings()` dejan de contener cadenas de comparación por
  tipo. Se admite lógica específica sólo donde el esquema no alcance, y en ese caso debe
  aislarse en un método con nombre y comentario que explique por qué.

**Manejo de secretos.** Los parámetros `sensitive` se pintan como `PasswordField`, **no
se escriben nunca en `node.configuration`** y viven en un mapa transitorio en memoria
`Map<nodeId, Map<String, char[]>>` que se vacía al cargar un proceso, al limpiar el
lienzo y al cerrar. `ProcessDefinitionCodec` debe rechazar cualquier clave marcada como
sensible aunque llegue en el mapa.

**Compatibilidad obligatoria.** La migración preserva el comportamiento: los tipos
actuales conservan exactamente las mismas claves de `configuration` y los mismos valores
por defecto, y todo `.cfprocess.json` existente abre sin cambios. Se comprueba con prueba
de fichero dorado.

### 2.7 Paleta buscable

- Panel acoplado a la izquierda del lienzo con campo de búsqueda y agrupación por
  categoría. Cada entrada muestra icono, nombre y descripción de una línea.
- El filtro busca en nombre, tipo, categoría y descripción, sin distinguir mayúsculas ni
  acentos.
- Añadir nodo por doble clic o arrastrando la entrada al lienzo, en la posición del
  puntero.
- La paleta se construye **desde `NodeCatalog`**, nunca a mano. Prueba: número de entradas
  de la paleta == tamaño del catálogo.
- Los `MenuButton` anidados actuales («Inputs», «Conversions», «Crypto», «Outputs») se
  retiran; «Presets» se conserva.

### 2.8 Productividad

- **Deshacer / rehacer** (`Shortcut+Z` / `Shortcut+Shift+Z`) sobre una pila de comandos
  explícita, mínimo 50 pasos, cubriendo: añadir, borrar, mover, conectar, desconectar,
  invertir conexión y cambiar configuración.
- **Selección múltiple**: rectángulo elástico y `Shift`+clic; mover el grupo; `Supr`
  borra todo lo seleccionado.
- **Duplicar** nodo (`Shortcut+D`) y copiar/pegar dentro del lienzo.
- **Organizar** («Tidy»): disposición automática por columnas según el orden topológico
  que ya calcula `ProcessValidator.computeTopologicalOrder`.
- **Teclado**: `Supr` borra, flechas desplazan 1 px (10 px con `Shift`), `Escape` limpia
  la selección, `Tab` recorre los nodos.

### 2.9 i18n y accesibilidad

- Todo literal nuevo pasa por `ModuleTextCatalog.processDesigner()`,
  `messages.properties` y `messages_es.properties`. Cero literales en inglés incrustados
  en FXML o en el controlador.
- Todo control interactivo nuevo declara texto accesible, siguiendo
  `Ux16AccessibilityContractTest` y `Ux23AccessibilityLiveUITest`.
- Los colores nuevos del lienzo deben pasar la puerta de contraste vigente.
- Todo control que se oculte debe llevar `visible=false` **y** `managed=false`.

---

## 3. Fuera de alcance

- Cualquier operación criptográfica nueva (es la Fase 5B).
- Ejecución remota, red, colaboración multiusuario o control de versiones de procesos.
- Cambiar el formato `.cfprocess.json` más allá de lo estrictamente necesario; si se
  incrementa `ProcessDefinition.version`, debe leer sin pérdida las versiones anteriores.
- Sustituir la biblioteca de UI o introducir un motor de grafos de terceros.
- Minimapa (deseable, no obligatorio).

---

## 4. Pruebas obligatorias

Sin red y sin recursos dependientes de la máquina. Cada requisito lleva prueba positiva y
prueba de rechazo o de no regresión.

### 4.1 Lienzo (headless donde sea posible)

- `ProcessCanvasGeometryTest`: tras colocar un nodo en `y = 2000`, el tamaño preferido del
  lienzo lo contiene y el nodo permanece dentro de los límites desplazables. **Esta prueba
  debe fallar contra el código actual**: es la regresión descrita en el análisis §2.1.
- Nodo en `x = 1400` accesible con desplazamiento horizontal.
- `ProcessCanvasZoomTest`: arrastre simulado a escala 0,5 / 1 / 2 produce el mismo
  desplazamiento lógico de `node.x` / `node.y` que el desplazamiento del puntero.
- `ProcessCanvasPerformanceTest`: 40 nodos, 100 eventos de arrastre, contador de
  `ProcessEngine.validate()` ≤ 1.

### 4.2 Catálogo e inspector

- `NodeCatalogTest`:
  - todo tipo de `supportedTypes()` de cada handler registrado tiene descriptor;
  - no hay tipos duplicados entre handlers;
  - toda `NodeParameter.key` de un descriptor corresponde a una clave que el handler lee;
  - toda `labelKey` / `descriptionKey` existe en `messages.properties` y en
    `messages_es.properties`.
- `NodeInspectorRendererTest` (UI): para cada descriptor del catálogo, el formulario
  generado contiene un control por parámetro, del tipo declarado; los `sensitive` son
  `PasswordField`.
- `ProcessDefinitionSecretsTest`: para cada descriptor, rellenar **todos** los parámetros
  sensibles, serializar y afirmar que ninguno aparece en el JSON; deserializar y afirmar
  que los campos vuelven vacíos.
- `ProcessDesignerLegacyProcessTest`: un `.cfprocess.json` dorado generado antes de esta
  fase se abre y produce mapas de `configuration` idénticos nodo a nodo.

### 4.3 Interacción

- `ProcessDesignerPaletteUITest`: la búsqueda filtra; el doble clic añade el nodo; el
  recuento de entradas coincide con `NodeCatalog`.
- `ProcessDesignerConnectionUITest`: arrastre puerto-a-puerto válido crea la conexión;
  arrastre a puerto incompatible no la crea y muestra el motivo del motor; la ruta
  accesible «seleccionar 2 → Connect» sigue funcionando.
- `ProcessDesignerUndoRedoTest`: cada tipo de comando se deshace y se rehace dejando el
  `ProcessDefinition` exactamente igual (comparación estructural, no textual).
- `ProcessDesignerWindowUITest`: «Abrir en ventana» abre un `Stage`, el proceso se
  conserva y no se duplica el estado.

### 4.4 No regresión

Deben seguir en verde sin modificarlas para que pasen:

```
ProcessEngineTest, ProcessEnginePhase35FileTextTest, ProcessValidatorTest,
WssNodeHandlerTest, ProcessDesignerControllerTest, ProcessDesignerUX12UITest,
FxmlQualityGateTest, FxmlContractTest, ModuleI18nLiveUITest, IncludedPaneI18nUITest,
Ux16AccessibilityContractTest, Ux23AccessibilityLiveUITest, UiNavigationRegistryTest,
NavigationRailContentSyncTest
```

Si se añade un FXML de producción nuevo, hay que darlo de alta en la lista
`FXML_FILES` de `FxmlQualityGateTest`.

---

## 5. Documentación

- Actualizar `docs/process-designer-architecture.md` con el modelo de descriptores,
  `NodeCatalog`, el renderizador del inspector y la política de parámetros sensibles.
  Aprovechar para corregir la firma obsoleta del SPI que aparece en ese documento
  (`acceptedInputs` ya no existe; hoy es `inputPorts` con `PortDefinition`).
- Crear `docs/process-designer-node-authoring.md`: cómo añadir un tipo de nodo nuevo con
  el modelo declarativo, en pasos numerados y con un ejemplo mínimo completo. Este
  documento es el contrato de trabajo de la Fase 5B.
- Actualizar `README.md` y `CHANGELOG.md`.

---

## 6. Entrega y evidencia

Antes de declarar la fase completada, entregar:

1. Archivos modificados y motivo de cada uno.
2. Recuento antes/después de líneas de `select()` y `saveSelectedNodeSettings()`, y de
   campos `@FXML` del controlador. **Se espera una reducción sustancial**; un aumento es
   señal de que la migración no se ha hecho.
3. Lista completa de los tipos de nodo migrados a descriptor, con su fichero.
4. Decisiones tomadas sobre secretos, compatibilidad de `.cfprocess.json` y navegación.
5. Resultado literal de:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   mvn -o test
   mvn -o -DrunUiTests=true -Dtest.mode=true test
   git diff --check
   ```

6. Toda prueba fallida se reporta por separado, con causa y evidencia. No se ocultan ni
   se marcan como `@Disabled`.

**No declares la fase completa** si falta cualquiera de: lienzo que crece, zoom con
arrastre correcto, inspector dirigido por descriptor con los ocho handlers migrados,
paleta alimentada por el catálogo, o la prueba que demuestra que los secretos no se
serializan.

---

## 7. Regla de trabajo

Para cada requisito: implementar, añadir prueba positiva, añadir prueba de rechazo o de
no regresión, ejecutar y aportar evidencia. Si el modelo de descriptores propuesto no
cubre algún tipo existente sin perder comportamiento, **detente y presenta la alternativa
antes de codificar**; no resuelvas la excepción devolviendo la cadena de `if` por tipo.
