# Capturas de la calculadora externa para HCE y tokenización

Esta campaña convierte el bloque C en vectores reproducibles. No describe los
algoritmos: fija entradas y pide todas las salidas e intermedios que la herramienta externa
la calculadora externa conoce. La implementación sólo empieza después de
recibir esas capturas.

## Entrega común

Antes de calcular, enviar `HCE-INDEX-00`: ventana completa de **About/Version**
y árbol de menús EMV/Contactless abierto donde se vean los nombres exactos de
las herramientas CVC3, Data Storage y Visa LUK/MSD. Los nombres de campo cambian
entre versiones; esta captura evita confundir dos perfiles parecidos.

Para cada ID de abajo devolver:

1. ventana entera, sin recortar desplegables ni paneles laterales;
2. versión exacta de la herramienta externa y nombre completo de la pestaña;
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

## 3b. Mastercard Data Storage — resultado (26-09-2026)

Pantalla real: EMV → Data Storage Partial Key → MasterCard, pestañas DSPK,
DS Summary (OWHF1) y DS Digest (OWHF2), con ejemplo precargado.

- **DSPK** y **OWHF2**: siguen EMV Book C-2 v2.6 §8.2 literalmente y reproducen
  el ejemplo (DS ID `5168624300900697` → DSPK `66887C5600B47C5600B40CC2`; OID
  `8199829983998499`, entrada `1223344556677889` → digest `659C8EBFAA816DB5`).
  Implementados en `MastercardDataStorage` y nodos `MC_DS_*`.
- **DS Summary (OWHF1)**: no está en Book C-2 (lo calcula la tarjeta). Resuelto
  con dos capturas; la segunda cambia importe (`9902`), moneda (`978`), RCP
  (`40`), indicador (`02`) y DS UN (`99887766`) frente a UN (`11223344`), y todos
  los intermedios cuadran:
  A = (nibble alto del RCP OR indicador de GAC) || moneda (`9840`, `6978`);
  X1 = DS Summary 1 ⊕ (`00000000` || 2 últimos bytes del importe || DS UN[3..4]);
  X2 = A || UN || DS UN[1..2]; K1 = DSPKL || DS UN[1..2]; K2 = DSPKR || DS UN[3..4];
  K3 = DES(K1)[X1] ⊕ X1; resumen = DES(K3)[DES⁻¹(K2)[DES(K3)[X2]]] ⊕ X1 ⊕ X2
  (`FC96571A6E95FFA4`, `80D66F2CFC670881`). Implementado en
  `MastercardDataStorage.summary`, nodo `MC_DS_SUMMARY` y la sección de la pantalla EMV.

## 4. Visa LUK

### Resultado (25-09-2026) — resuelto

Pantalla real: EMV → HCE → Visa, pestañas UDK, LUK key, MSD y qVSDC con un
ejemplo precargado. Se capturaron sus valores por defecto; el fabricante publica
un segundo ejemplo (UDK `C8B5…D30B`, año `0`, horas `5702`, contador `01` → LUK
`3EA7…43BC`, MSD `675`). `VisaHceOperations` reproduce ambos
(`VisaHceOperationsTest`) y hay nodos `VISA_HCE_*`.

- **UDK**: opción A desde la MDK con PAN||PSN, paridad impar.
- **LUK**: TDES(UDK) sobre `1` || Y HHHH CC con relleno `80` (mitad izquierda)
  y sobre `2` || Y HHHH CC (derecha). La herramienta usa el año tal cual lo
  recibe (acepta `26`, 2 dígitos, aunque la norma es el último dígito).
- **MSD**: el ATC sustituye los 4 primeros dígitos del tipo de dispositivo;
  TDES con la LUK; decimalización de CVV; 3 dígitos.
- **qVSDC**: ISO 9797-1 alg. 3, relleno 2, con la LUK sobre datos del terminal
  (9F02 9F03 9F1A 95 5F2A 9A 9C 9F37) y del chip (82 9F36 CVR).

La herramienta se cerró sola una vez al cambiar de pestaña; al reabrirla el
campo «Hourly Counter» venía vacío y hay que rellenarlo.


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

### Resultados (26-09-2026) — capturas parciales

`HCE-INDEX-00` sólo pudo verificarse parcialmente: la ventana principal indica
versión `21.06` y muestra los menús superiores `Main`, `Generic`, `Cipher`,
`Keys`, `Payments`, `EMV` y `Development`. Con el foco cedido por el usuario se
abrió `Payments → Card Validation`: su submenú contiene `CWVs`, `AMEX CSCs` y
`MasterCard dynamic CVC3`. El menú `EMV` muestra `Application Cryptograms`,
`SDA`, `DDA`, `ICC Dynamic Number`, `Data Storage Partial Key`, `Secure
Messaging`, `HCE`, `CAP Token Computation`, `ATR Parser`, `EMV Data Parser`,
`EMV Tag dictionary` y `APDU response query`. El submenú `ICC Dynamic Number`
sólo ofreció `MasterCard`. No se inspeccionaron los demás submenús de `EMV`.
No se guardó una imagen de la ventana porque su título identifica al
fabricante.

`AMEX CSCs` mostró la pestaña `Generate`, versiones `CSC ver. 1` y `CSC ver. 2`,
y estos valores precargados: CSC Key `0123456789ABCDEFFEDCBA9876543210` (32),
PAN `371234567890123` (15), Exp. date `9912` (4) y Service Code `702` (3).
Los cinco tipos de `Verification Value Type` estaban deshabilitados en v1 y
se habilitaron en v2. El PAN de 16 dígitos de la campaña no cupo en el campo
AMEX; para los cálculos se usó `411111111111111` (15 dígitos), eliminando el
último `1`. Esta desviación se conserva explícita en vez de presentar los
resultados como si usaran el PAN original.

El panel de v1 imprimió literalmente las entradas `CSC Key`, `PAN`,
`Expiration date`, `Service Code` y `Value Type: CSC ver.1`, seguidas de
`CSC-5`, `CSC-4` y `CSC-3`. No imprimió clave derivada, dato de derivación,
bloque formateado ni operación criptográfica intermedia.

| Caso | Clave CSC | PAN | Caducidad | Código de servicio | CSC-5 | CSC-4 | CSC-3 |
|---|---|---|---|---|---|---|---|
| `AMEX-CSC1-00` | `0123456789ABCDEFFEDCBA9876543210` | `411111111111111` | `2512` | `201` | `08746` | `2908` | `854` |
| `AMEX-CSC1-SC-01` | igual | igual | igual | `000` | `08746` | `2908` | `854` |

Ambos cálculos de v1 devolvieron los mismos CSC al cambiar sólo el código de
servicio. `CSC ver. 2`, con los mismos valores de entrada y tipo visible `CSC`,
mostró el diálogo «Unhandled exception» al calcular tanto con `201` como con
`000`; el panel no imprimió resultado para esa versión. «Omitir» permitió
recuperar el formulario después del primer fallo. No se reconstruyó ningún
intermedio ausente.

`EMV → ICC Dynamic Number → MasterCard` abrió la pestaña `ICC Dynamic Number`.
Los campos visibles son `MK-DN`, `PAN/PAN Seq.No`, `ATC` y `Unpredictable nr.`;
sus valores precargados eran, respectivamente, una clave de 32 caracteres,
`9503493362330001` (16), `0001` (4) y `92ED9B7F` (8). El campo combinado
`PAN/PAN Seq.No` rechazó `411111111111111100` con el error de que sus 18
caracteres debían ser exactamente 16. Por decisión del usuario se introdujo
el PAN de campaña de 16 dígitos en ese campo, sin PSN separado. El panel
rotuló este valor simplemente como `PAN`; no es posible atribuir un PSN.

El panel imprimió `MK-DN`, `PAN`, `ATC`, `Unpredictable nr.`, `Session Key` y
`Dynamic Number`, sin clave ICC derivada ni bloque formateado de entrada.

| Caso | MK-DN | Campo PAN/PAN Seq.No | ATC | UN | Session Key | Dynamic Number |
|---|---|---|---|---|---|---|
| `ICC-DYNAMIC-00` | `0123456789ABCDEFFEDCBA9876543210` | `4111111111111111` | `0001` | `00000000` | `69D9405C8462F4109CB20DC8B99F3BD9` | `6AB2` |
| `ICC-DYNAMIC-UN-01` | igual | igual | igual | `00000001` | `69D9405C8462F4109CB20DC8B99F3BD9` | `816A` |

`EMV → CAP Token Computation` es una pantalla distinta de la generación de
CAP/SecureCode planteada en `MC-CAP-00`: sólo ofrece `IPB` (36 caracteres
según el contador del campo; el panel imprime 38 caracteres en `IPB data`),
`IAF` (`40`), `PAN sn` (`00`), `CID` (`80`), `ATC` (`0001`),
`AC` (`5AC19AC9FE1360F3`) e `IAD` (`06010A03A41000`) en su ejemplo
precargado. No presenta MK, PAN completo, UN, PIN, derivación de claves ni
desafío. Con esos valores precargados, el panel mostró `Token data`, `Binary
Token data`, `IPB data`, `Binary IPB data`, `Compressed data` y `Token: 1385`.
Esta observación identifica la herramienta disponible, pero no constituye el
vector `MC-CAP-00`: tampoco permite el segundo caso que cambia sólo UN.

Salida completa del panel para el ejemplo precargado, copiada como texto:

```text
[2026-09-26 13:19:56]
EMV Cryptography: CAP Token derivation finished
****************************************
Token data:        008000015AC19AC9FE1360F306010A03A41000
Binary Token data:
0000 0000 1000 0000 0000 0000 0000 0001
0101 1010 1100 0001 1001 1010 1100 1001
1111 1110 0001 0011 0110 0000 1111 0011
0000 0110 0000 0001 0000 1010 0000 0011
1010 0100 0001 0000 0000 0000
IPB data:          00007FFFFF0000000000000000000020800000
Binary IPB data:
0000 0000 0000 0000 0111 1111 1111 1111
1111 1111 0000 0000 0000 0000 0000 0000
0000 0000 0000 0000 0000 0000 0000 0000
0000 0000 0000 0000 0000 0000 0010 0000
1000 0000 0000 0000 0000 0000
Compressed data:   0000000000000010101101001
Token:             1385
```

| Caso | Entradas previstas | Pantalla y resultado observados | Estado |
|---|---|---|---|
| `AMEX-CSC1-00` | `K.TDES2`, PAN de 15 dígitos, `EXPIRY`, `SERVICE_CODE=201` y `000` | Ambos resultados CSC registrados arriba; sin claves ni bloques intermedios | Captura parcial; algoritmo no deducible |
| `AMEX-CSC2-00` | Los mismos valores, versión 2 y tipo `CSC` | Excepción no controlada al calcular `201` y `000`; sin resultado ni intermedios | Capturas de fallo; no implementable |
| `ICC-DYNAMIC-00` | `K.TDES2`, `PAN`, `ATC.1`, `UN.0` y `UN.1`; el campo combinado impide añadir `PSN` | Dos resultados registrados arriba; sin clave ICC ni bloque formateado | **Deducido** (ver abajo); implementable |
| `MC-CAP-00` | `K.TDES2`, `PAN`, `PSN`, `ATC.1`, `UN.0` y `UN.1`; `PIN` si se solicita | La pantalla `CAP Token Computation` no ofrece MK, PAN, UN ni PIN; el ejemplo precargado produjo Token `1385` | La generación de CAP de la campaña no existe; el **cálculo del token** sí se deduce del panel (ver abajo) |

### Deducción a partir de las capturas (revisión de Claude, 26-09-2026)

AMEX CSC v1 sigue sin poder deducirse: no hay intermedios y las
construcciones habituales no reproducen `08746`/`2908`/`854`.

**ICC Dynamic Number (MasterCard).** Las dos capturas se reproducen exactamente con:

- `Session Key` = derivación de clave ICC «opción A» de EMV a partir de `MK-DN`,
  con Y = el campo `PAN/PAN Seq.No` de 16 dígitos:
  `3DES(MK, Y) ‖ 3DES(MK, Y ⊕ FF…FF)` → `69D9405C8462F4109CB20DC8B99F3BD9`.
  No depende del ATC, y por eso no cambia entre los dos casos.
- `Dynamic Number` = los 2 primeros bytes de `3DES(Session Key, ATC ‖ 0000 ‖ UN)`:
  `3DES(SK, 0001000000000000)` = `6AB24A2E…` y
  `3DES(SK, 0001000000000001)` = `816AA3B4…`.

**CAP Token Computation.** El panel muestra todos los pasos:

- `Token data` = `PAN sn ‖ CID ‖ ATC ‖ AC ‖ IAD` (`00 80 0001 5AC19AC9FE1360F3 06010A03A41000`).
  El PSN va delante porque el IAF `40` indica que se incluye.
- `IPB data` = el IPB de entrada (18 bytes) rellenado con `00` por la derecha
  hasta la longitud de `Token data` (19 bytes).
- `Compressed data` = los bits de `Token data` cuya posición vale 1 en el IPB,
  en orden de MSB a LSB (25 bits: `0000000000000010101101001`).
- `Token` = ese número binario en decimal: `1385`.

**CAP con IAF `00`** (captura de Claude, 26-09-2026, 14:48). Mismo ejemplo
precargado cambiando solo IAF a `00`:

```text
Token data:        8000015AC19AC9FE1360F306010A03A41000
IPB data:          00007FFFFF00000000000000000000208000
Compressed data:   0000001010110101100000110
Token:             355078
```

Sin el bit `40` del IAF no se incluye el PSN, y el IPB (18 bytes) ya
mide lo mismo que los datos del token, así que no se rellena. Fijado en
`MastercardIccDynamicNumberTest.capturedCapTokenWithoutPanSequenceNumber`.

**AMEX CSC con el PAN del ejemplo precargado** (captura de Claude, 26-09-2026).
Clave `0123456789ABCDEFFEDCBA9876543210`, PAN `371234567890123`, caducidad
`9912`, código de servicio `702`. El panel solo imprime entradas y resultados.

| Versión | Tipo | CSC-5 | CSC-4 | CSC-3 |
|---|---|---|---|---|
| CSC ver. 1 | — | `61247` | `8720` | `552` |
| CSC ver. 2 | `CSC` | `21334` | `5068` | `221` |

Con este PAN la versión 2 sí calcula; con `411111111111111` lanzaba una
excepción. Sigue sin haber intermedios, así que AMEX CSC no se implementa.

## Criterio de aceptación

Una ficha sólo se convierte en vector de test cuando permite escribir, sin
suposiciones:

- producto, versión, pestaña y perfil exactos;
- algoritmo y longitud de cada clave;
- todos los inputs y opciones;
- resultado final;
- claves, bloques y datos intermedios;
- segundo caso con una sola variable cambiada cuando se solicita.

Si la herramienta externa no muestra un intermedio, la ficha sigue siendo útil, pero se marca
esa ausencia: no se reconstruye el valor con una implementación propia y se
presenta después como evidencia externa.
