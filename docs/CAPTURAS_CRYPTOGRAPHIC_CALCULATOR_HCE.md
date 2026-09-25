# Capturas de Cryptographic Calculator para HCE y tokenización

Esta campaña convierte el bloque C en vectores reproducibles. No describe los
algoritmos: fija entradas y pide todas las salidas e intermedios que BP-Tools
Cryptographic Calculator conoce. La implementación sólo empieza después de
recibir esas capturas.

## Entrega común

Antes de calcular, enviar `HCE-INDEX-00`: ventana completa de **About/Version**
y árbol de menús EMV/Contactless abierto donde se vean los nombres exactos de
las herramientas CVC3, Data Storage y Visa LUK/MSD. Los nombres de campo cambian
entre versiones; esta captura evita confundir dos perfiles parecidos.

Para cada ID de abajo devolver:

1. ventana entera, sin recortar desplegables ni paneles laterales;
2. versión exacta de BP-Tools y nombre completo de la pestaña;
3. todos los inputs, incluso los que la herramienta rellene por defecto;
4. resultado final y **todos los valores intermedios** que imprima;
5. export/copy-text de la herramienta, si existe;
6. una nota indicando qué campos estaban deshabilitados.

No cambiar un valor por otro “equivalente” y no usar claves de producción. Si
un campo no existe, no buscar un sustituto: conservar la ventana y anotarlo.

## Constantes de la campaña

| Identificador | Valor |
|---|---|
| `K.TDES2` | `0123456789ABCDEFFEDCBA9876543210` |
| `K.AES128` | `000102030405060708090A0B0C0D0E0F` |
| `PAN` | `4111111111111111` |
| `PSN` | `00` |
| `ATC.1` | `0001` |
| `UN.0` | `00000000` |
| `UN.1` | `00000001` |
| `PIN` | `1234` |
| `EXPIRY` | `2512` |
| `SERVICE_CODE` | `201` |
| `TRACK2` | `4111111111111111D25122010000000000000F` |
| `TRACK1` | `B4111111111111111^TEST/CARD^2512201000000000000000000000000` |
| `DATA.16` | `000102030405060708090A0B0C0D0E0F` |

Cuando la UI separe una clave doble en mitades A/B, introducir los primeros 16
hex de `K.TDES2` en A y los últimos 16 en B. No duplicar una mitad.

---

## 1. Mastercard CVC3

Ruta esperada: **EMV / Contactless → Mastercard CVC3**. Usar el literal que
muestre `HCE-INDEX-00` si difiere.

### `MC-CVC3-00` — vector base

| Campo de la herramienta | Valor |
|---|---|
| Profile / scheme | Mastercard CVC3 / M/Chip, el perfil explícito de CVC3 |
| IMK-CVC3 / MK-CVC3 | `K.TDES2` |
| PAN | `PAN` |
| PAN sequence number | `PSN` |
| ATC | `ATC.1` |
| Unpredictable Number | `UN.0` |
| Track 1 | `TRACK1`, si acepta la pista completa |
| Track 2 | `TRACK2`, si acepta la pista completa |
| IVCVC3 Track 1 | conservar el valor por defecto y dejarlo visible |
| IVCVC3 Track 2 | conservar el valor por defecto y dejarlo visible |
| Derivation option / diversification mode | valor por defecto visible |

Copiar por separado, con sus etiquetas exactas:

- ICC Master Key / derived CVC3 key;
- dato de diversificación usado;
- clave de sesión si aparece;
- IVCVC3 de track 1 y track 2;
- bloque de entrada al cifrado/MAC;
- CVC3 de track 1 y CVC3 de track 2;
- posiciones o reglas de inserción en las pistas, si las muestra.

### `MC-CVC3-UN-01` — aislar el UN

Duplicar `MC-CVC3-00` y cambiar sólo Unpredictable Number de `UN.0` a `UN.1`.
Todo lo demás debe quedar idéntico. Devolver los mismos intermedios; este par
demuestra exactamente qué resultados dependen del UN.

### `MC-CVC3-ATC-02` — aislar el ATC

Duplicar `MC-CVC3-00` y cambiar sólo ATC de `0001` a `0002`. No cambiar UN ni
pistas. Devolver los mismos intermedios.

### Resultado (25-09-2026) — aparcado

Pantalla real: Payments → Card Validation → MasterCard dynamic CVC3. Un solo
campo «Track 1/2 Data» en hexadecimal, con relleno ISO/IEC 7816-4 método 2
obligatorio (múltiplo de 16).

| Pista | UN | ATC | CVC3 |
|---|---|---|---|
| track 2 `4111…000F` + `8000000000` | 00000000 | 0001 | 19882 |
| track 1 (ASCII en hex) + `8000000000` | 00000000 | 0001 | 17781 |
| track 2 | 00000001 | 0002 | 19882 |
| track 2 | 12345678 | FFFF | 19882 |

- La clave derivada (`31089464674C73EC851A6E0737029D19`, KCV `9595`) es la
  clave de tarjeta EMV opción A que ya calcula el código, con paridad impar.
- **La herramienta ignora UN y ATC al generar**: el CVC3 no cambia. No sirve
  como referencia del CVC3 dinámico real, que cifra IVCVC3 || UN || ATC.
- Ninguna construcción probada reproduce 19882/17781 (IVCVC3 por CBC-MAC DES,
  TDES o ISO 9797-1 alg. 3, con y sin relleno, bytes izquierdos o derechos;
  CVC3 como cifrado de IVCVC3 con UN y ATC a cero, en decimal de 2 bytes).
- La pestaña Validate trae un ejemplo precargado (IMK
  `01234567899876543210012345678998`, PAN `5413123456784808`, ATC `005E`,
  CVC3 `587`) con los campos descolocados; no se ha podido usar.

Hace falta otra fuente (un HSM o una especificación con vector) antes de
implementar el CVC3.

## 2. Mastercard PIN-CVC3

Ruta esperada: la opción **PIN-CVC3** de la misma familia Mastercard. No usar el
CVC3 de pista como si fuese PIN-CVC3 si la versión sólo ofrece uno de ellos.

### `MC-PINCVC3-00`

| Campo | Valor |
|---|---|
| MK/IMK de PIN-CVC3 | `K.TDES2` |
| PAN | `PAN` |
| PSN | `PSN` |
| PIN | `PIN` |
| ATC | `ATC.1` |
| UN | `UN.0` |
| PIN block/profile option | valor por defecto visible |

Copiar PIN-CVC3 final, ICC/derived key, clave de sesión, bloque de PIN
formateado, dato previo a la operación criptográfica y cualquier decimalización
o recorte que enseñe la herramienta.

Repetir como `MC-PINCVC3-UN-01` cambiando sólo `UN.0` por `UN.1`.

---

## 3. Mastercard Data Storage

Primero enviar `MC-DS-INDEX-00`: menú Data Storage abierto y cada pestaña
visible. Se esperan al menos DSPK, DS Summary y DS Digest, pero no se deben
deducir equivalencias por el nombre.

### `MC-DS-DSPK-00`

| Campo | Valor |
|---|---|
| DS master key / IMK | `K.AES128` si la pestaña exige AES; `K.TDES2` si exige TDES |
| PAN | `PAN` |
| PSN | `PSN` |
| ATC | `ATC.1` |
| DS ID / slot / version | valor por defecto visible |
| Input data | `DATA.16` cuando el campo admita 16 bytes libres |
| Derivation option | valor por defecto visible |

Copiar DSPK, dato de derivación completo, claves intermedias y KCV de cada clave
si aparece. La captura debe dejar inequívoco qué algoritmo de clave seleccionó
la herramienta.

### `MC-DS-SUMMARY-00`

Abrir **DS Summary** y reutilizar exactamente los inputs de `MC-DS-DSPK-00`.
Cuando pida DSPK, usar la salida identificada `MC-DS-DSPK-00.dspk`; cuando la
calcule internamente, dejar el campo como venga y capturar el DSPK mostrado.

Usar `DATA.16` como DS data/input cuando exista ese campo. Conservar visibles
DS ID, versión, operador, slot y cualquier contador. Copiar:

- DS Summary final;
- DSPK realmente usado;
- bloque formateado antes de cifrar/MAC;
- IV, padding, contador y claves derivadas que aparezcan.

### `MC-DS-DIGEST-00`

Mismos valores que `MC-DS-SUMMARY-00`. Si pide DS Summary, usar
`MC-DS-SUMMARY-00.summary`; si lo calcula, comprobar que coincide y capturarlo.
Copiar DS Digest, input exacto al digest/MAC y todos los intermedios.

### `MC-DS-DATA-01`

Duplicar Summary y Digest cambiando sólo `DATA.16` por
`000102030405060708090A0B0C0D0E0E`. Sirve para distinguir un campo autenticado
de uno meramente mostrado.

---

## 4. Visa LUK

Ruta esperada: **EMV / Contactless / Visa → LUK**.

### `VISA-LUK-00`

| Campo | Valor |
|---|---|
| Visa profile | qVSDC/LUK explícito, no MSD |
| IMK / token master key | `K.AES128` si la UI exige AES; `K.TDES2` si exige TDES |
| PAN / token PAN | `PAN` |
| PSN | `PSN` |
| ATC / key index / generation counter | `ATC.1` sólo en el campo cuyo literal sea ATC; los demás por defecto |
| Derivation data / account parameters | valor por defecto visible |
| Key length / derivation option | valor por defecto visible |

Copiar LUK, ICC/token master key, dato de diversificación, contador o índice de
generación, KCV y cualquier clave intermedia. La ventana debe mostrar si el
resultado es AES o TDES; no basta con la longitud inferida.

### `VISA-LUK-COUNTER-01`

Duplicar `VISA-LUK-00` y cambiar sólo el contador que la UI identifique como
generation/key index de `0001` a `0002`. Si no existe un campo con ese sentido,
no cambiar el ATC por intuición: enviar la ventana y omitir este caso.

---

## 5. Visa MSD y qVSDC con la LUK anterior

### `VISA-MSD-00`

| Campo | Valor |
|---|---|
| Profile | Visa MSD |
| LUK | `VISA-LUK-00.luk`, si la pestaña la recibe; si la deriva, mismos inputs de `VISA-LUK-00` |
| PAN / token PAN | `PAN` |
| PSN | `PSN` |
| ATC | `ATC.1` |
| UN | `UN.0` si existe |
| Track 2 | `TRACK2` |
| Expiry | `EXPIRY` si está separado |
| Service code | `SERVICE_CODE` si está separado |

Copiar pista dinámica completa, dCVV/CVV dinámico, LUK realmente usada, clave de
sesión, input criptográfico y posiciones modificadas de la pista.

Repetir como `VISA-MSD-UN-01` cambiando sólo `UN.0` por `UN.1`, pero únicamente
si la pestaña MSD expone UN.

### `VISA-QVSDC-00`

Mismos PAN, PSN, ATC, UN y LUK que `VISA-MSD-00`, seleccionando explícitamente
qVSDC. Conservar por defecto TTQ, AIP, CVR/IAD y CDOL si aparecen, todos visibles
en la captura. Copiar criptograma, IAD/CVR, clave de sesión, input completo al
criptograma y cada intermedio mostrado.

---

## 6. Inventario restante del bloque C

Estas fichas no se sustituyen entre sí. Ejecutarlas sólo si `HCE-INDEX-00`
confirma que la versión instalada ofrece la operación:

| ID | Operación | Entradas fijas | Salidas que deben verse |
|---|---|---|---|
| `ICC-DYNAMIC-00` | ICC Dynamic Number | `K.TDES2`, `PAN`, `PSN`, `ATC.1`, `UN.0` | ICC key, session key, input formateado, dynamic number |
| `MC-CAP-00` | Mastercard CAP / SecureCode | `K.TDES2`, `PAN`, `PSN`, `ATC.1`, `UN.0`, `PIN` cuando lo pida | derived keys, challenge/input block, CAP/SecureCode result |
| `AMEX-CSC1-00` | AMEX CSC v1 | `K.TDES2`, `PAN`, `EXPIRY`, `SERVICE_CODE`, defaults visibles | derived key, formatted input, CSC |
| `AMEX-CSC2-00` | AMEX CSC v2 | los mismos valores que v1 | derived key, formatted input, CSC |

Para AMEX, una segunda pareja cambia sólo `SERVICE_CODE` de `201` a `000`. Para
ICC Dynamic Number y CAP, un segundo caso cambia sólo `UN.0` por `UN.1`.

## Criterio de aceptación

Una ficha sólo se convierte en vector de test cuando permite escribir, sin
suposiciones:

- producto, versión, pestaña y perfil exactos;
- algoritmo y longitud de cada clave;
- todos los inputs y opciones;
- resultado final;
- claves, bloques y datos intermedios;
- segundo caso con una sola variable cambiada cuando se solicita.

Si BP-Tools no muestra un intermedio, la ficha sigue siendo útil, pero se marca
esa ausencia: no se reconstruye el valor con una implementación propia y se
presenta después como evidencia externa.
