# Capturas de PIN blocks heredados

Esta campaña fija las mismas entradas para todos los casos. La captura de la herramienta externa permitirá contrastar el bloque en claro y, cuando se ofrezca, el bloque cifrado. No usar claves de producción.

## Entrega común

Para cada identificador, enviar la ventana completa con versión, nombre exacto del formato seleccionado, todos los campos y sus valores por defecto, resultado y opción de copiar o exportar si existe. Si un campo no aparece, indicarlo; no sustituirlo por otro. Conservar visible cualquier ajuste de relleno, longitud o variante.

| Entrada | Valor para todos los casos |
|---|---|
| PIN | `1234` |
| PAN de pruebas | `4111111111111111` |
| Clave de pruebas (TDES doble) | `0123456789ABCDEFFEDCBA9876543210` |

Cada caso debe copiar: **bloque PIN en claro** y, si la herramienta externa lo ofrece, **bloque PIN cifrado**. El bloque en claro identifica el formato; el cifrado solo es comparable si la clave y el modo de cifrado son visibles.

## Resultados (capturados el 2026-09-26)

Pantalla de la herramienta externa: *Payments → PIN Blocks → PIN Blocks General*, pestaña *Encode*. PIN `1234`. Solo hay bloque en claro: esa pantalla no cifra. Se usaron dos PAN: `4111111111111111`, que es el de la campaña, y `4000123456789017`, para distinguir qué doce dígitos del PAN entran en el XOR. Con el primero, casi todos los dígitos son `1` y eso no se puede ver.

La herramienta externa no ofrece Plus Network, ni Docutel por separado, ni AS2805 8.1. Su lista de formatos es: ISO 0 a 4, ANSI X9.8, «Docutel & Diebold & NCR ATMs», ECI-1 a ECI-4, IBM 3621, 3624, 4704 y 5906, VISA-1 a VISA-4, y «Europay/MasterCard (Pay Now & Pay Later)». La pantalla *Payments → AS2805* emula comandos de HSM con claves bajo LMK y no da un bloque 8.1 en claro.

| Caso | Formato en la herramienta | PAN | Bloque en claro | CryptoCarver |
|---|---|---|---|---|
| `PINBLK-DIEBOLD-00` | Docutel & Diebold & NCR ATMs, relleno `F` | 4111… | `1234FFFFFFFFFFFF` | Coincide con Diebold |
| `PINBLK-DOCUTEL-00` | la misma entrada que Diebold | 4111… | `1234FFFFFFFFFFFF` | **No comparable**: la herramienta no separa Docutel. CryptoCarver sigue el formato 02 del manual del fabricante (longitud, PIN rellenado con ceros hasta seis dígitos y nueve dígitos de relleno). Queda sin verificar |
| `PINBLK-PLUS-00` | no existe | — | — | Sin captura directa. La construcción de Plus sale idéntica a VISA-4 (ver abajo) |
| `PINBLK-EUROPAY-BANKSYS-00` | Europay/MasterCard (Pay Now & Pay Later) | 4111… | `241225EEEEEEEEEE` | **Nuevo formato**: ISO-0 con nibble de control 2. Coincide |
| | | 4000… | `241235DCBA9876FE` | Coincide |
| `PINBLK-AS2805-81-00` | no existe | — | — | Sigue pendiente |
| `PINBLK-ECI1-00` | ECI-1 | 4111… | `041225EEEEEEEEEE` | = ISO-0. Coincide |
| | | 4000… | `041235DCBA9876FE` | = ISO-0. Coincide |
| `PINBLK-ECI4-00` | ECI-4 | 4111… | `141234FC5B883236` | Estructura ISO-1 (1, longitud, PIN y relleno aleatorio). Se decodifica bien |
| `PINBLK-VISA4-00` | VISA-4 | 4111… | `041275EEEEEEEEEE` | **No es ISO-0**: usa los doce dígitos de la izquierda del PAN, igual que Plus. Corregido |
| | | 4000… | `041274FFEDCBA987` | Coincide tras la corrección |

Otros formatos capturados en la misma sesión:

| Formato | PAN | Relleno | Bloque en claro | CryptoCarver |
|---|---|---|---|---|
| VISA-1 | 4111… | — | `041225EEEEEEEEEE` | = ISO-0. Coincide |
| VISA-2 | 4111… | `0` (con `F` da el error 1322: el relleno debe ser decimal) | `4123400000000000` | Se decodifica bien; CryptoCarver rellena con `5` al codificar |
| VISA-3 | 4111… | `0` | `1234F00000000000` | Se decodifica bien; CryptoCarver rellena con `5` al codificar |
| ECI-2 | 4111… | aleatorio | `1234121BA681C8B3` | Antes el decode lo rechazaba porque exigía `F`. Ya acepta cualquier relleno |
| ECI-3 | 4111… | aleatorio | `412342B5DED1F46D` | Igual que ECI-2: corregido |

Los vectores están fijados en `LegacyPinBlockFormatTest.matchesExternalToolCaptures`.

## Casos originales de la campaña

### Formatos solicitados

#### `PINBLK-DOCUTEL-00` — Docutel

Seleccionar la variante Docutel que muestre la herramienta externa e indicar si incorpora longitud del PIN, delimitador o relleno numérico configurable. Conservar el relleno mostrado.

Resultado en la tabla de arriba.

#### `PINBLK-DIEBOLD-00` — Diebold

Seleccionar Diebold y registrar el carácter de relleno y cualquier número de coordinación que aparezca.

Resultado en la tabla de arriba.

#### `PINBLK-PLUS-00` — Plus Network

Seleccionar Plus Network y dejar visible qué doce dígitos del PAN intervienen.

Resultado en la tabla de arriba.

#### `PINBLK-EUROPAY-BANKSYS-00` — Europay/Banksys

Seleccionar la opción literal disponible e incluir cualquier dato adicional que exija la herramienta externa. La estructura sigue pendiente de una especificación pública concreta.

Resultado en la tabla de arriba.

#### `PINBLK-AS2805-81-00` — AS2805 8.1

Seleccionar exactamente la variante «8.1» si existe e incluir el código de formato visible. La estructura sigue pendiente de una especificación pública concreta.

Resultado en la tabla de arriba.

### Alias con equivalencia no verificada

Estos tres alias conservan el comportamiento actual en CryptoCarver. Las capturas deben comprobar la equivalencia sin asumir que el nombre basta como prueba.

#### `PINBLK-ECI1-00` — ECI-1

Resultado en la tabla de arriba.

#### `PINBLK-ECI4-00` — ECI-4

Resultado en la tabla de arriba.

#### `PINBLK-VISA4-00` — VISA-4

Resultado en la tabla de arriba.
