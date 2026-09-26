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

### Resultados (26-09-2026) — captura bloqueada

`HCE-INDEX-00` sólo pudo verificarse parcialmente: la ventana principal indica
versión `21.06` y muestra los menús superiores `Main`, `Generic`, `Cipher`,
`Keys`, `Payments`, `EMV` y `Development`. La interfaz de control de la máquina
virtual no entregó las teclas a la ventana, incluso tras darle foco desde su
barra de título. Por ello no se pudieron abrir los submenús de `Payments` y
`EMV` ni confirmar los nombres de las operaciones. No se guardó una imagen de
la ventana porque su título identifica al fabricante.

| Caso | Entradas previstas | Pantalla y resultado observados | Estado |
|---|---|---|---|
| `AMEX-CSC1-00` | `K.TDES2`, `PAN`, `EXPIRY`, `SERVICE_CODE=201` y `000` | No se abrió la operación; sin campos, intermedios ni CSC observados | Sin captura; no implementable |
| `AMEX-CSC2-00` | Los mismos dos valores de `SERVICE_CODE` | No se abrió la operación; sin campos, intermedios ni CSC observados | Sin captura; no implementable |
| `ICC-DYNAMIC-00` | `K.TDES2`, `PAN`, `PSN`, `ATC.1`, `UN.0` y `UN.1` | No se abrió la operación; sin campos, claves, bloques ni número dinámico observados | Sin captura; no implementable |
| `MC-CAP-00` | `K.TDES2`, `PAN`, `PSN`, `ATC.1`, `UN.0` y `UN.1`; `PIN` si se solicita | No se abrió la operación; sin campos, claves, bloques ni resultado observados | Sin captura; no implementable |

No hay vector que permita deducir el formato de entrada, la derivación de
claves o el cálculo de ninguna de estas operaciones. Quedan pendientes también
las variaciones de una sola entrada exigidas por la campaña.

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
