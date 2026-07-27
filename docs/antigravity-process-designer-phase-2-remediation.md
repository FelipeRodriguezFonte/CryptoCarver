# Encargo para Antigravity: correcciones de Fase 2 y paleta de nodos escalable

Este documento complementa `docs/antigravity-process-designer-phase-2.md`. Implementar los puntos siguientes antes de declarar la Fase 2 terminada.

## Resultado de revisión

La base actual es válida: existen `FlowValue`, `Representation`, una SPI `ProcessNodeHandler`, codecs básicos y ejecución en segundo plano. Sin embargo, no satisface todavía el contrato de validación, seguridad de ficheros ni observabilidad por nodo.

No resolver estos puntos con simples mensajes de UI. Deben quedar garantizados en el modelo/motor para cualquier consumidor, incluida una futura API o CLI.

## A. Correcciones obligatorias del motor

### A.1 Validación previa completa

Antes de ejecutar cualquier nodo o efectuar cualquier I/O, añadir `ProcessEngine.validate(ProcessDefinition)` (o un validador equivalente) que devuelva diagnósticos estructurados.

Debe verificar:

- IDs de nodo no vacíos y únicos.
- Cada `Connection.from` y `Connection.to` existe.
- No hay autoenlaces.
- No hay ciclos.
- Un nodo de entrada no recibe conexiones; un nodo de salida no tiene salidas si su contrato no lo permite.
- Cada nodo con entrada única tiene, como máximo, una conexión entrante. No se permite ignorar silenciosamente la segunda conexión.
- La representación de salida del origen es compatible con la entrada del destino.
- Todos los campos obligatorios de nodo (p. ej. `path`, algoritmo, formato) son válidos antes de comenzar.

Cambiar la SPI para que describa contratos reales. Ejemplo orientativo:

```java
interface ProcessNodeHandler {
    Set<String> supportedTypes();
    Set<Representation> acceptedInputs(ProcessNode node);
    Representation outputRepresentation(ProcessNode node, Representation input);
    FlowValue execute(ProcessNode node, FlowValue input, ExecutionContext context) throws Exception;
}
```

No usar `accepts(...) -> true` para todos los handlers. Un decodificador Base64 debe aceptar texto Base64; un `HEX_DECODE`, texto Hex; `Hash` puede aceptar bytes de cualquier representación si usa su payload binario.

Los errores deben identificar enlace y nodos, por ejemplo:

`Invalid connection: “Hex decode” expects HEX text but receives BINARY from “File input”.`

### A.2 Seguridad de salida a fichero

La protección de sobrescritura no puede vivir únicamente en JavaFX.

- Introducir `FileWritePolicy` en el contexto de ejecución: `FAIL_IF_EXISTS`, `ALLOW_OVERWRITE`.
- El valor por defecto del motor debe ser `FAIL_IF_EXISTS`.
- La UI pregunta al usuario y, solo tras confirmación, vuelve a invocar el motor con `ALLOW_OVERWRITE` para ese destino explícito.
- Para `File output`, usar `FileChooser.showSaveDialog`, no `showOpenDialog`.
- Escribir de forma atómica: temporal en el mismo directorio + move al destino cuando sea posible.
- Informar ruta, nodo y causa sin exponer datos sensibles.

### A.3 Estados reales por nodo

Añadir eventos de ejecución al motor, no estados simulados al finalizar:

```java
enum NodeExecutionState { PENDING, RUNNING, SUCCESS, ERROR, SKIPPED }
record NodeExecutionEvent(String nodeId, NodeExecutionState state, Duration duration, String safeMessage) { }
```

- Emitir `RUNNING` inmediatamente antes de cada handler.
- Emitir `SUCCESS` con duración tras cada handler.
- Ante fallo, emitir `ERROR` para el nodo actual y `SKIPPED` para los que ya no se ejecutarán.
- La `Task` JavaFX debe enviar esas actualizaciones al hilo FX mediante `Platform.runLater` o una cola observable.
- No marcar todos los nodos como `SUCCESS` o `ERROR/ABORTED` de forma masiva.

## B. UX: sustituir la hilera de botones por una paleta categorizada

La barra actual con un botón por nodo no es aceptable para una superficie amplia ni para el crecimiento previsto. Sustituir el `FlowPane` de botones por una barra de paleta compacta y extensible.

### B.1 Diseño obligatorio

Mantener siempre visible una única fila compacta con estas categorías:

```text
[＋ Add node ▾]  [Inputs ▾]  [Conversions ▾]  [Crypto ▾]  [Outputs ▾]
                              [Run] [Reverse] [Inspector] [Save] [Open]
```

Usar `MenuButton`/`SplitMenuButton` JavaFX o un control equivalente nativo. No introducir una librería de UI externa.

Contenido inicial:

| Categoría | Entradas |
|---|---|
| Inputs | Console input, File input |
| Conversions | Base64 encode/decode, Base64URL encode/decode, Hex encode/decode, UTF-8 encode/decode |
| Crypto | Hash, Encrypt (planned), Decrypt (planned), Sign (planned), Verify (planned), MAC (planned), Verify MAC (planned) |
| Outputs | Console output, File output |

Los nodos `PLANNED` deben poder visualizarse para comunicar el roadmap, pero no crear una ejecución falsa. Al seleccionarlos, crear el nodo con etiqueta `PLANNED` y mostrar claramente que no se puede ejecutar, o mantenerlos deshabilitados dentro del menú con tooltip explicativo. Elegir uno de los dos comportamientos de forma consistente.

`Add node` puede ser un menú agregado con todas las categorías para acceso rápido, no un botón adicional por cada operación.

### B.2 Inserción y edición

- Al añadir un nodo, colocarlo en una posición libre visible del lienzo, evitando superposición con nodos existentes.
- Seleccionarlo inmediatamente y abrir el inspector si estaba oculto solo cuando la configuración sea obligatoria.
- El inspector debe ser contextual: mostrar únicamente campos aplicables al tipo de nodo.
- Mantener `Hide/Show inspector`; al ocultarlo, el lienzo debe recuperar todo el ancho disponible.
- Mostrar en el nodo badges o subtítulo de representación: `bytes`, `text/UTF-8`, `Base64`, `Hex`, etc.

### B.3 Enlaces y acciones

- Mantener la selección de dos nodos para conectar, pero mostrar una indicación compacta: `Source: Hash → target pending`.
- La flecha indica inequívocamente el sentido.
- `Reverse connection` se habilita al seleccionar la flecha o dos nodos ya conectados.
- Revertir una conexión debe volver a validar todo el grafo y marcar los nodos/enlaces inválidos; no cambiar silenciosamente un flujo en un estado inválido.

## C. Codecs y ficheros: requisitos de prueba

Añadir o ampliar pruebas para cubrir como mínimo:

1. `Hola → SHA-256 → Base64 encode` produce `5jP0/Hm63qHcXblwzzl8gki6xHzDrPmRW6YLXXaw6I8=`.
2. Base64, Base64URL y Hex hacen round-trip exacto de bytes arbitrarios, incluidos `0x00`, bytes no UTF-8 y entrada Base64URL sin padding.
3. `HEX_DECODE` y Base64 decode rechazan representación equivocada o formato inválido antes de escribir un fichero.
4. File input binario → codec → File output conserva bytes exactos.
5. File output existente falla por defecto y no altera el fichero; solo lo cambia con `ALLOW_OVERWRITE` explícito.
6. Una conexión inexistente, cíclica, duplicada o incompatible falla en `validate` antes de que se ejecute el primer handler.
7. Prueba JavaFX: los menús de paleta crean el tipo de nodo correcto, el inspector contextual se comporta correctamente, y los eventos por nodo llegan a la UI.
8. Guardar/cargar v1 y v2 conserva el grafo y nunca persiste resultado, valores de consola por defecto, claves, contraseñas o buffers sensibles.

## D. Entregables y reporte

- Implementación y pruebas.
- Documento técnico nuevo: `docs/process-designer-architecture.md`, con modelo de representaciones, SPI, políticas de fichero y extensión segura para cifrado/firma/WSS.
- Reporte final de Antigravity que enumere archivos modificados, pruebas ejecutadas y cualquier limitación que permanezca.
- Ejecutar `mvn test -Dtest=ProcessEngineTest,ProcessDesignerControllerTest` y `git diff --check`.
