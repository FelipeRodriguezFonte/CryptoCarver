# Process Designer — Phase 3.5: claridad de formatos y trazabilidad de ejecución

Esta fase corrige la semántica visible de los datos y hace comprensible la ejecución de un workflow. Debe realizarse antes de Phase 4 WS-Security: no es aceptable construir nuevas capacidades XML sobre representaciones confusas.

No modificar la criptografía de Phase 3, los contratos de puertos, la persistencia segura ni la política de secretos, excepto cuando sea imprescindible para representar correctamente los formatos.

## 1. Problema que se corrige

Actualmente un fichero de entrada aparece como `[BINARY]`, lo cual es correcto por defecto. Sin embargo, `Hex encode` aparece como `[TEXT_UTF8]`, aunque el valor semántico que produce es una cadena hexadecimal. Eso confunde la construcción del flujo y puede ocultar errores de conexión.

Regla central: la etiqueta del canvas debe expresar la **representación semántica del valor**, no el detalle de que internamente se transporte como bytes UTF-8.

Ejemplo correcto:

```text
File input (raw bytes) [BINARY]
  → Hex encode [HEX]
  → Hex decode [BINARY]
  → SHA-256 [BINARY]
  → Base64 encode [BASE64]
  → Console output [BASE64]
```

## 2. Contrato de representaciones

Mantener o ajustar el modelo con estas semánticas:

| Representación | Significado | Render humano |
|---|---|---|
| `BINARY` | bytes arbitrarios, no texto | preview Hex, con longitud |
| `TEXT_UTF8` | texto UTF-8 | texto con charset indicado |
| `HEX` | caracteres hexadecimales canónicos | cadena Hex |
| `BASE64` | caracteres Base64 estándar | cadena Base64 |
| `BASE64URL` | caracteres Base64 URL-safe | cadena Base64URL |

No tratar `HEX`, `BASE64` y `BASE64URL` como `TEXT_UTF8` solo porque se codifiquen como caracteres UTF-8.

### 2.1 Contratos obligatorios por nodo

- Console input → `TEXT_UTF8`.
- File input, modo `Binary` → `BINARY`.
- File input, modo `Text` → `TEXT_UTF8`, usando el charset seleccionado.
- Console output y File output preservan la representación recibida.
- Hash → `BINARY`.
- Encrypt / Decrypt / MAC / Sign → `BINARY`.
- Verify → `TEXT_UTF8` (`VALID` / `INVALID`).
- Hex encode → `HEX`.
- Hex decode → `BINARY`.
- Base64 encode → `BASE64`.
- Base64 decode → `BINARY`.
- Base64URL encode → `BASE64URL`.
- Base64URL decode → `BINARY`.
- UTF-8 encode → `BINARY`.
- UTF-8 decode → `TEXT_UTF8`.

### 2.2 Compatibilidad y entrada manual

- Los nodos decode deben aceptar su representación semántica correspondiente (`HEX`, `BASE64`, `BASE64URL`).
- Para permitir que el usuario pegue texto en un Console input, también pueden aceptar `TEXT_UTF8` como entrada de decode, validando el contenido; esta excepción debe estar documentada y mostrarse en la ayuda del nodo.
- Los procesos guardados de versiones anteriores que tenían un encode representado como `TEXT_UTF8` deben continuar cargando. No modificar el significado de payload ni romper conexiones legacy.
- Si se cambia el formato persistido, versionar y documentar migración. Si no hace falta cambiar JSON, no subir la versión.

## 3. File input/output: modo explícito

### 3.1 Inspector

Para `FILE_INPUT` y `FILE_OUTPUT`, añadir un selector visible:

- `Binary (raw bytes)` — valor por defecto.
- `Text`.

Cuando está seleccionado `Binary`:

- ocultar y desgestionar (`visible=false`, `managed=false`) charset;
- explicar: “The file is read/written as raw bytes.”

Cuando está seleccionado `Text`:

- mostrar charset;
- representar salida como `TEXT_UTF8`;
- leer y escribir según el charset seleccionado.

No mostrar un selector de charset activo para un File input binario porque induce a error: no afecta a los bytes en ese modo.

### 3.2 Canvas

Etiquetas legibles:

- `File input · raw bytes [BINARY]`
- `File input · UTF-8 text [TEXT_UTF8]`
- `Hex encode [HEX]`
- `Base64 encode [BASE64]`

No sustituir el nombre de la representación por explicaciones largas en cada arista; usar el badge corto y un tooltip de ayuda.

## 4. Trazabilidad de ejecución

### 4.1 Panel de estado

Reemplazar o complementar la lista difícil de leer por una tabla compacta y desplazable con columnas:

- `#` — orden de ejecución;
- `Step` — etiqueta del nodo;
- `Operation` — tipo legible;
- `Input` — representación y tamaño;
- `Output` — representación y tamaño;
- `Status` — PENDING/RUNNING/SUCCESS/ERROR/SKIPPED;
- `Duration`.

Requisitos:

- encabezado fijo;
- colores accesibles, pero el texto de estado nunca depende solo del color;
- selección de una fila resalta el nodo correspondiente en canvas;
- el panel puede plegarse/expandirse como el inspector;
- conservar información de error segura por nodo.

### 4.2 Registro de ejecución en consola

No escribir únicamente “Process completed successfully” y el valor final. Escribir una traza ordenada y clara, por ejemplo:

```text
Process completed successfully.

[1] File input · raw bytes
    output: BINARY, 42 bytes

[2] Hex encode
    input:  BINARY, 42 bytes
    output: HEX, 84 characters

[3] SHA-256
    input:  HEX, 84 characters
    output: BINARY, 32 bytes

[4] Console output
    input: BINARY, 32 bytes
    value (HEX): E633F4FC...
```

Reglas:

- etiquetar cada output terminal con su etiqueta y tipo de nodo;
- incluir representación, unidad correcta (`bytes` o `characters`) y tamaño;
- para `BINARY`, preview Hex truncado; para texto/HEX/Base64, preview textual truncado;
- configurar un límite de preview (por ejemplo 256 caracteres) y mostrar `… (truncated)`;
- no imprimir claves, contraseñas, PIN, XML SOAP completo sensible ni stack traces;
- File output debe indicar ruta, representación y número de bytes escritos, pero no volcar el fichero entero;
- errores deben indicar paso, operación y mensaje seguro.

### 4.3 Eventos del motor

Extender `NodeExecutionEvent` o crear un resultado inmutable por nodo para transportar, sin payload sensible:

- id de nodo;
- ordinal de ejecución;
- etiqueta y tipo;
- estado;
- representación de entrada/salida;
- tamaño de entrada/salida;
- duración;
- detalle seguro.

No calcular el resumen leyendo controles JavaFX desde el hilo de fondo. El motor produce metadatos; el controlador los representa en el JavaFX Application Thread.

## 5. UX educativa

- Añadir tooltip a cada representación: `BINARY`, `TEXT_UTF8`, `HEX`, `BASE64`, `BASE64URL`.
- En inspector, mostrar “Input contract” y “Output contract” del nodo seleccionado. Ejemplo:

  ```text
  Input: BINARY or TEXT_UTF8
  Output: HEX
  ```

- Si una conexión es aceptada por una compatibilidad de texto (p. ej. Console input → Hex decode), mostrar una nota: “Text will be interpreted as hexadecimal characters.”
- Si una conexión no es compatible, mostrar antes de ejecutar la representación producida y la esperada.

## 6. Pruebas obligatorias

### 6.1 Motor

- File input binario → Hex encode: la salida es `Representation.HEX`, no `TEXT_UTF8`.
- File input binario → Base64/Base64URL encode: salidas `BASE64`/`BASE64URL`.
- Decode desde representación semántica correspondiente funciona.
- Decode desde Console input `TEXT_UTF8` sigue funcionando como compatibilidad y rechaza contenido inválido.
- Hash, encrypt, decrypt, MAC y sign conservan `BINARY`.
- File input en modo Text con charset seleccionado produce texto y File output Text escribe con ese charset.
- Validación detecta incompatibilidades con un mensaje que contiene representación de origen, representación esperada y nodo/puerto.

### 6.2 Observabilidad

- Cada evento SUCCESS contiene representación y tamaño de entrada/salida correctos.
- La traza final enumera nodos en orden de ejecución.
- Console output incluye etiqueta, representación y preview correcto.
- Binary preview está en Hex y se trunca de forma determinista.
- La traza no contiene clave manual, contraseña de keystore ni key password.

### 6.3 JavaFX

- File input Binary oculta charset; File input Text lo muestra.
- Badge canvas de Hex encode dice `[HEX]`.
- Badge Base64 encode dice `[BASE64]`.
- Estado de ejecución muestra columnas/metadatos o equivalente accesible.
- Seleccionar una fila de estado resalta el nodo asociado.
- Panel de estado se puede plegar/expandir.

## 7. Documentación

Actualizar `docs/process-designer-architecture.md` con la tabla de representaciones, los contratos por nodo, compatibilidad de decode y el esquema de eventos de ejecución.

Crear `docs/process-designer-observability.md` con ejemplos de traza y reglas de redacción segura.

## 8. Criterios de entrega

Entregar:

1. Archivos modificados y motivo.
2. Decisiones de compatibilidad de representaciones.
3. Captura o descripción precisa de la nueva tabla/panel de ejecución.
4. Resultado literal de:

   ```bash
   mvn test -Dtest=ProcessEngineTest,ProcessDesignerControllerTest,OperationRegistryTest
   mvn compile test
   git diff --check
   ```

5. No declarar finalizada si Hex/Base64 siguen apareciendo como `TEXT_UTF8`, si el charset se muestra en Binary mode, o si la salida no identifica paso, representación y tamaño.
