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

## Resultado del intento del 26-09-2026 (Claude)

La consola HSM externa (versión 21.06) solo envía comandos por TCP a un HSM
o emulador externo; no trae emulador propio. Su único perfil, `Default`, apunta
a `127.0.0.1:9999`, con timeout de 1 s, cabecera `00000000` y trailer
desactivado. Al enviar `NO - HSM Status` (modo `00`), la consola respondió
«connection check failed». En esta máquina no hay nada escuchando, así que
**no se ha capturado ninguna respuesta** y ningún ID de esta lista queda
cerrado. Para cerrarlos hace falta un payShield real o un emulador de
terceros de procedencia conocida. Una respuesta de un simulador propio no
cuenta como vector externo.

## Capturas contra un simulador de terceros (26-09-2026, Claude)

**Montaje.** La consola HSM externa (21.06), en la máquina virtual, envió los
comandos a un simulador payShield 10K de código abierto que corría en el Mac:
el de PayProbe, commit `bbb28a48`, con licencia PolyForm Noncommercial. Su
código no se incorpora al repositorio; aquí solo se registran las tramas. La
conexión pasó por un proxy TCP local que guardó los bytes exactos.

Configuración:
- Consola: cabecera `00000000` (8 caracteres, su valor por defecto), sin trailer
  y con prefijo TCP de 2 bytes big-endian.
- Simulador: `header_bytes` 8, LMK de test `0123456789ABCDEFFEDCBA9876543210`,
  firmware `0007-E000` y `variant_lmk` desactivado.

**Qué vale como evidencia y qué no:**
- Las **peticiones** las construyó la consola HSM externa con los valores de
  ejemplo de su formulario. Son evidencia externa de cómo arma cada comando.
- Las **respuestas** son del simulador, no de un payShield. Sus claves van
  cifradas con una sola LMK fija y no con el esquema de variantes real, así que
  no sirven como vector de un equipo real. Lo que sí aporta es comprobar si la
  consola parsea esa respuesta con los mismos campos que espera
  `PayShieldBodySchemas`.

Tramas literales (prefijo TCP en hexadecimal y después el mensaje ASCII; `→`
petición, `←` respuesta):

```text
→ 000C 00000000NO00
← 0020 00000000NP001101280007-E00000000
→ 000F 00000000A00000U
← 0033 00000000A100UF95168C4319FCCC3F9577272B7FDF14E36CBAB
→ 002E 00000000BU001U294E6024662EB037097C4C8D5CEEA6ED
← 0012 00000000BV00BB7158
→ 0061 00000000EEU8AC91C79495A9FC3021AE502DDEDD9800000FFFFFFFF0409541092192939C3DE67FC7B2B2B07006859718N
← 000C 00000000EF30
→ 0043 00000000CWUEB0F2056EDC79C4BFB52B5B4D3E68D881234567890123456;1510109
← 000F 00000000CX00079
→ 0046 00000000CYUEB0F2056EDC79C4BFB52B5B4D3E68D886841234567890123456;1510109
← 000C 00000000CZ01
→ 0046 00000000CYUEB0F2056EDC79C4BFB52B5B4D3E68D880791234567890123456;1510109
← 000C 00000000CZ00
→ 007B 00000000CAU8EEB4727D594D80799EE7394B5B33DC9UA045EA2A914D7A950C3052AFCF151261129BBBE380C3E5D2400101987654321098;987654321098
← 000C 00000000CB15
→ 007B 00000000CCU00DEB679DB51D99B53A78112D755769BUA045EA2A914D7A950C3052AFCF151261129BBBE380C3E5D2400101987654321098;987654321098
← 000C 00000000CD15
→ 0103 00000000M601031003U0D48F907F0DC6B8E7CD333317595FCC600CC02107238000102C000111670343010001006660000000000000000000912065731000092155726091206703400393138303030303230343030303133303039373334202020203032390301000000000020202020202020200000000000000000020000000000
← 000C 00000000M715
→ 000A 00000000NC
← 0025 00000000ND0008D7B4FB629D08850007-E000
→ 0050 00000000A6000U1BF1879107A29B475E07CB594A8D67A4XB38BBEBCEE6C5A5484393BBCC4F0D9F8U
← 0033 00000000A700UA987CC2719103EB11FCA7463273B2A6ED6A020
→ 0050 00000000A8002UBB839220AE2F70A754F05D356107D6E3U98DCBCBB630FF4831E05A912D1B042C8U
← 000C 00000000A930
```

Cómo parseó la consola cada respuesta:

| Comando | Respuesta | Campos que muestra la consola | Resultado |
|---|---|---|---|
| `NO` | `NP00` | I/O buffer `1`, Ethernet `1`, sockets `01`, firmware `280007-E0`, DSP `0`, DSP firmware `0000` | La consola avisa de «Parsing error. Parsed end of message: 32»: la respuesta del simulador no tiene la forma que ella espera |
| `NC` | `ND00` | LMK check `08D7B4FB629D0885` (16), firmware `0007-E000` (9) | Coincide con el esquema `ND` de `PayShieldBodySchemas` |
| `A0` modo 0, tipo `000`, esquema `U` | `A100` | clave `UF95168C4319FCCC3F9577272B7FDF14E`, KCV `36CBAB` | Parseada sin error |
| `BU` tipo `00`, longitud `1` | `BV00` | KCV `BB7158` | Parseada sin error |
| `CW` | `CX00` | CVV `079` | Parseada sin error |
| `CY` con CVV `684` / `079` | `CZ01` / `CZ00` | error `01` «CVV failed verification» / `00` | Parseada sin error |
| `A6` tipo `000`, esquema `U` | `A700` | clave `UA987CC2719103EB11FCA7463273B2A6E`, KCV `D6A020` | Parseada sin error |
| `CA`, `CC` | `CB15`, `CD15` | error `15` | El simulador rechaza el campo «Destination PAN» que añade la consola tras `;` |
| `M6` | `M715` | error `15` | El simulador no acepta la petición de la consola |
| `A8`, `EE` | `A930`, `EF30` | error `30` | Comandos no implementados en el simulador |

**Cruce con la criptografía de CryptoCarver** (claves descifradas con la LMK de
test del simulador, en 3DES ECB):
- NC: 3DES(LMK, 0) = `08D7B4FB629D0885`, el «LMK check» devuelto.
- A0: clave en claro `AD910110291FE6349DA438A1E6203E40`, KCV `36CBAB`, coincide.
- BU: clave en claro `52664598B1734B56665BF777B66E0923`, KCV `BB7158`, coincide.
- CW: CVK en claro `1696CECE6555717F07873F6AD8F46726`; con PAN
  `1234567890123456`, caducidad `1510` y código de servicio `109`,
  `PaymentOperations.generateCVV` da `079`, el mismo CVV que devolvió el
  simulador.

La consola se ha dejado como estaba: `127.0.0.1:9999`, timeout 1 s y sin debug.
