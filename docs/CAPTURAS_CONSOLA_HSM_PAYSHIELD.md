# Capturas de la consola HSM externa para cerrar el banco payShield

Esta lista es ejecutable en orden. Cada valor producido se reutiliza por su
identificador (`A0-0.keyUnderLmk`, por ejemplo), para no inventar una clave bajo
LMK que dependa del juego de LMK configurado en la consola HSM externa.

## Entrega común a todas las capturas

Configurar una sola sesión payShield y no cambiarla entre casos:

- Vendor/protocol: **Thales payShield**.
- Message header: `0000`.
- TCP length prefix: dejar el valor de la sesión, pero anotarlo. Si está activo,
  copiar tanto los bytes de longitud como el payload ASCII.
- Trailer: vacío.
- Character set: ASCII.

Devolver para cada ID:

1. captura de **la ventana entera**, incluidos versión de la herramienta externa, perfil de
   LMK, desplegables, campos deshabilitados y valores intermedios;
2. petición y respuesta crudas, sin espacios añadidos;
3. petición y respuesta en hexadecimal si la consola lo ofrece;
4. texto del error de la consola HSM externa, aunque la respuesta sea correcta;
5. nombre exacto y versión del perfil/emulador al que se envió.

No enviar claves de producción. Todos los valores de este documento son de
laboratorio.

## 1. `NC` — cerrar el par y sustituir la captura sin procedencia

ID: `NC-00`.

| Campo | Valor |
|---|---|
| Command | `NC` — Perform diagnostics |
| Header | `0000` |
| Command data/body | vacío |
| Trailer | vacío |

Copiar la petición y la respuesta completas. La petición esperada sin prefijo
TCP es `0000NC`. La respuesta que hoy tiene el repositorio es
`0000ND007B44AC1DDEE2A94B0007-E000`, pero **no se debe pegar como entrada**: el
objetivo es obtener una captura nueva e independiente. La ventana debe mostrar
qué etiqueta da la consola HSM externa a `7B44AC1DDEE2A94B` y a `0007-E000`.

## 2. `A0` — crear el material reutilizable

Ejecutar tres casos cambiando sólo `Mode`:

| ID | Campo | Valor |
|---|---|---|
| `A0-0` | Mode | `0` |
| `A0-1` | Mode | `1` |
| `A0-2` | Mode | `2` |
| todos | Key type | `002` (ZMK) |
| todos | Key scheme / output scheme | `U` (double-length variant key) |
| todos | Header | `0000` |
| todos | Optional data / trailer | vacío |

En los modos que habiliten campos adicionales, usar:

- ZMK/key-encrypting-key: `A0-0.keyUnderLmk`;
- scheme of that ZMK: `U`;
- component count: `2`, si aparece;
- component 1: `0123456789ABCDEFFEDCBA9876543210`;
- component 2: `00112233445566778899AABBCCDDEEFF`;
- cualquier otro campo habilitado: conservar el valor por defecto y dejarlo
  visible en la captura.

Copiar por separado la clave bajo LMK, la clave bajo ZMK si se produce y el KCV.
Si un modo no acepta esos datos o significa otra cosa en esa versión, no probar
combinaciones: capturar la ventana con el modo seleccionado y el mensaje de la
herramienta.

## 3. `BU` — KCV comprobable

ID: `BU-00`.

| Campo | Valor |
|---|---|
| Command | `BU` — Generate key check value |
| Header | `0000` |
| Key type | `002` |
| Key scheme | `U` |
| Key under LMK | `A0-0.keyUnderLmk` |
| KCV/check method | `ENC ZERO` / default, dejando visible el literal exacto |
| Trailer / optional data | vacío |

Copiar el KCV completo que muestre la ventana, no sólo los seis primeros
caracteres. Adjuntar también `A0-0` permite comprobar que el `BU` se aplicó a la
misma clave.

## 4. `A6` — importar bajo ZMK

ID: `A6-00`.

| Campo | Valor |
|---|---|
| Command | `A6` — Import key |
| Header | `0000` |
| Key type | `000` (ZPK) |
| Output/key-under-LMK scheme | `U` |
| ZMK under LMK | `A0-0.keyUnderLmk` |
| ZMK scheme | `U` |
| Key under ZMK | `A0-1.keyUnderZmk`; si `A0-1` no lo produce, usar el campo de helper de la consola HSM externa para envolver la clave de prueba de abajo |
| Clear test key del helper | `00112233445566778899AABBCCDDEEFF` |
| Imported key scheme | `U` |
| Optional data / trailer | vacío |

Copiar la clave importada bajo LMK y su KCV, además de cualquier valor claro o
intermedio que la consola HSM externa enseñe al lado.

### Respuesta de error real

ID: `A6-ERR-01`. Duplicar `A6-00` y cambiar **sólo** `Key under ZMK`: sustituir
su último carácter hexadecimal por `G`. No corregir el campo cuando la consola
avise; enviar la trama si permite transmitirla. Si la UI impide enviarla,
sustituir sólo el último nibble por `0` y transmitir el criptograma de longitud
válida pero incorrecto.

Copiar la respuesta cruda, el código de error y el texto que la herramienta externa le asigne.
Esta captura es la fuente para añadir esa entrada a `PayShieldErrorCatalog`.

## 5. `A8` — exportar la misma clave

ID: `A8-00`.

| Campo | Valor |
|---|---|
| Command | `A8` — Export key |
| Header | `0000` |
| Key type | `000` (ZPK) |
| Key under LMK | `A6-00.keyUnderLmk` |
| Key-under-LMK scheme | `U` |
| ZMK under LMK | `A0-0.keyUnderLmk` |
| ZMK scheme | `U` |
| Exported key scheme | `U` |
| Optional data / trailer | vacío |

El valor exportado bajo ZMK debería poder compararse con el valor de entrada de
`A6-00`; copiar ambos aunque la consola HSM externa ya indique match.

## 6. `CA` — traducción de bloque de PIN

ID: `CA-00`.

| Campo | Valor |
|---|---|
| Command | `CA` — Translate PIN block |
| Header | `0000` |
| Source key type | `001` (TPK), o el literal equivalente que ofrezca la UI |
| Source key under LMK | generar antes con `A0`, mode `0`, type `001`, scheme `U` |
| Destination key type | `000` (ZPK) |
| Destination key under LMK | `A6-00.keyUnderLmk` |
| Source PIN-block format | `01` (ISO-0) |
| Destination PIN-block format | `01` (ISO-0) |
| PIN | `1234` sólo en el helper de PIN block, nunca en la trama CA |
| PAN / account number | `4111111111111111` |
| Clear ISO-0 block expected from helper | `041225EEEEEEEEEE` |
| Encrypted source PIN block | resultado del helper con la source key anterior |
| Optional data / trailer | vacío |

Copiar la trama, el bloque de entrada, el bloque traducido y los dos KCV. Si la
UI llama `TPK` o `ZPK` a los campos en vez de mostrar tipos numéricos, conservar
esos nombres en la captura.

## 7. `CY` — verificación de CVV

Preparar primero una CVK de prueba con `A0`, mode `0`, key type `402`, scheme
`U`; guardar su salida como `A0-CVK.keyUnderLmk`.

ID: `CY-00`.

| Campo | Valor |
|---|---|
| Command | `CY` — Verify CVV |
| Header | `0000` |
| CVK A/B or CVK pair under LMK | `A0-CVK.keyUnderLmk` |
| PAN | `4111111111111111` |
| Expiry | `2512` |
| Service code | `000` |
| CVV | generarlo primero con `CW` en la misma ventana y los mismos datos |
| Optional data / trailer | vacío |

Para que el vector sea reproducible hay que copiar también la petición/respuesta
de `CW` usada para obtener el CVV, aunque `CW` no sea todavía una captura
prioritaria. Devolver KCV de la CVK, CVV calculado y resultado de verificación.

## Criterio de aceptación de una captura

Una captura cierra un formato sólo cuando permite guardar en un test:

- versión y herramienta de origen;
- todos los inputs visibles;
- petición literal;
- respuesta literal;
- campos de salida e intermedios;
- interpretación del código de error.

Un pantallazo recortado al resultado o una trama sin inputs sirve para explorar,
pero no se incorporará como vector externo.
