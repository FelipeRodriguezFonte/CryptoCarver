# Valores de control de medios de pago — 2026-09-25

## De dónde venía la validación

Las capturas de una herramienta externa del 19 y 20-09-2026 solo cerraron
**formatos de protección de claves**: Atalla AKB, Thales Key Block 3DES/AES,
variante LMK de Thales, Futurex y SafeNet. Las capturas de MAC y bloques de
PIN, las de CVC3/DS/LUK y las de comandos de HSM se pidieron pero no llegaron.

Antes de este documento, las operaciones de pago tenían solo cuatro vectores
externos: el IPEK de X9.24, el MAC X9.19, AES DUKPT (X9.24-3) y los PIN blocks
VISA-2/3 de EMV.COOL. Todo lo demás solo se comprobaba contra el propio
código.

## Método

`scripts/payment_reference.py` es una implementación nueva, escrita a partir
de las normas sobre pycryptodome y sin mirar el código Java. Se lanzaron las
mismas entradas contra las dos implementaciones.

1. La referencia reproduce los seis vectores publicados que usa el documento:
   KCV `08D7B4`, KCV AES `C6A13B`, CVV `561`, MAC X9.19 `A1C72E74EA3FA9B6`,
   IPEK `6AC292FA…` y la clave PIN de KSN 1 de X9.24 Annex A (`042666B4…`,
   PIN cifrado `1B9C1845EB993A7A`).
2. Las cifras en las que Java y la referencia coinciden quedan fijadas en
   `PaymentControlValuesTest` (16 tests en verde).
3. Las cifras en las que no coinciden aparecen abajo, junto con la norma que
   decide cuál es la correcta.

Constantes: clave `0123456789ABCDEFFEDCBA9876543210`, AES
`000102030405060708090A0B0C0D0E0F`, PAN `4111111111111111`, PIN `1234`,
CVK A/B `0123456789ABCDEF` / `FEDCBA9876543210`.

## Valores de control que coinciden

| Operación | Entrada | Valor | Origen |
|---|---|---|---|
| KCV TDES | K2 | `08D7B4` | publicado |
| KCV AES (bloque cero) | AES128 | `C6A13B` | publicado (FIPS-197) |
| ISO-0 | PIN 1234, PAN | `041225EEEEEEEEEE` | cruzado |
| ISO-2 | PIN 1234 | `241234FFFFFFFFFF` | cruzado |
| CVV | PAN 4123456789012345, 8701, 101 | `561` | publicado |
| CVV / iCVV / CVV2 | PAN, 2512, 201 / 999 / 000 | `139` / `133` / `765` | cruzado |
| PVV Visa | PIN 1234, PVKI 1, PVK=K2 | `9464` | cruzado |
| IBM 3624 | dectab `0123456789012345` | natural `5775`, offset `6569` | cruzado |
| Retail MAC (ISO 9797-1 alg 3), relleno 1 | `Hello, world!` | `ABF9AFD03C6F5F70` | cruzado |
| Retail MAC, relleno 2 | ídem | `94A00FFEFE29FE69` | cruzado |
| CBC-MAC TDES (alg 1), relleno 2 | ídem | `09D0D7B78B4447DC` | cruzado |
| AES-CMAC | ídem, AES128 | `BF170E4054A64585…` | cruzado |
| DUKPT TDES IPEK | BDK K2, KSN `FFFF9876543210E00000` | `6AC292FAA1315B4D858AB3A3D7D5933A` | publicado |
| DUKPT PIN key, KSN 1 | | `042666B49184CF5C68DE9628D0397B36` | publicado |
| DUKPT PIN / MAC key, KSN 2 | | `C46551CEF9FD244F…3B38` / `…DBB0…C4C7` | cruzado |
| DUKPT PIN / MAC key, KSN 8 | | `27F66D5244FF621E…427F` / `…9DE1…BD80` | cruzado |
| EMV opción A (MK-AC, sin paridad) | IMK K2, PSN 00 | `30089565674D73ED841A6F0637029C18` | cruzado |
| EMV session key CSK (con paridad) | ATC 0001 | `38F14068B3EA57C194F8E3A20D51E3E6` | cruzado |
| ARQC, relleno 1 / 2 | CDOL de 37 bytes (ver test) | `E8499E593250A030` / `A8DB2B65F9C821F1` | cruzado |
| ARPC método 1 | ARC `00` | `ADCB085B842E0A9D` | cruzado |

## Discrepancias: bugs reales (corregidos)

Los cuatro están corregidos y sus valores de referencia fijados en
`PaymentControlValuesTest`. Las tablas muestran el valor que daba Java antes
del arreglo.

- ARPC método 2: `EMVOperations.generateARPC_Method2(sk, arqc, csu)` calcula el
  MAC de A1.2.2. Pantalla y nodo piden ahora el ARQC; el ARC solo lo usa el método 1.
- ISO-4: el campo PAN sale de `PinBlock.encodePanFieldIso4`. Sin clave, Pagos
  muestra el campo PIN y el campo PAN; con clave AES cifra el bloque completo.
  Sin clave no se puede descodificar un bloque cifrado, y ahora lo dice.
- CMAC: Pagos ofrece `CMAC-TDES` y `CMAC-AES` y usa la clave entera. El KCV
  CMAC ya no trunca AES-192/256.

### 1. ARPC método 2 — incorrecto

`EMVOperations.generateARPC_Method2` cifra `ARC || CSU` en un solo bloque.
EMV Book 2 A1.2.2 define otra cosa: **ARPC = MAC (ISO 9797-1 alg 3, relleno 2)
sobre `ARQC || CSU || Proprietary Auth Data`, truncado a 4 bytes**. No usa el
ARC y sí usa el ARQC.

| | Valor (ARQC `A8DB2B65F9C821F1`, CSU `00820000`) |
|---|---|
| Referencia | `54DB2625` |
| Java | `A875E3A2586F0894` |

Cualquier tarjeta CCD/M-Chip Advance rechazaría el ARPC que genera Java.

### 2. ISO-4: el campo PAN solo es correcto para PAN de 16 dígitos

ISO 9564-1 define el primer nibble del campo PAN como **longitud del PAN − 12**.
El código escribe siempre `4`, y eso solo coincide cuando el PAN tiene 16
dígitos. La UI (`PaymentsController`) calcula bien la `M` para mostrarla, pero
el XOR usa la del código, así que lo que enseña y lo que calcula no son lo mismo.

| PAN | Referencia | Java |
|---|---|---|
| 13 dígitos | `1411111111111100…` | `4411111111111100…` |
| 19 dígitos | `7411111111111111113…` | `4411111111111111113…` |

### 3. ISO-4: no hay cifrado AES

Un bloque de formato 4 es `E_K( E_K(campo PIN) ⊕ campo PAN )`. `PaymentOperations`
solo hace el XOR en claro, y no hay AES en ninguna parte del flujo ISO-4. Para
contrastar una implementación completa:

| Campo | Valor |
|---|---|
| Campo PIN (con relleno aleatorio fijado) | `441234AAAAAAAAAA0123456789ABCDEF` |
| Campo PAN (16 dígitos) | `44111111111111111000000000000000` |
| Bloque cifrado con AES128 | `70487881E82D3F1EF3A87678147EDAA7` |

### 4. CMAC "ISO 9797-1 Alg 5" en Pagos: siempre AES-128

`PaymentOperations.generateCMAC` toma los 16 primeros bytes de la clave y hace
AES-CMAC:

- Con una clave TDES de 16 bytes calcula AES-CMAC con ella y lo presenta como
  CMAC de la clave TDES. Java da `36F12DD8`; el TDES-CMAC correcto es
  `339F7934E25410EA`.
- Con una clave AES-192 o AES-256 la trunca a 128 bits sin avisar.

`KeyOperations.calculateKCV_CMAC` hace el mismo truncado, así que el KCV CMAC
de una clave AES-256 sale mal.

## Cerrado con las capturas de KCV del 25-09-2026

Nueve claves generadas por la herramienta externa y dos validadas con ella,
`0123456789ABCDEFFEDCBA9876543210` y `0101010101010101FEFEFEFEFEFEFEFE`
(`KcvCaptureTest`). En validación la herramienta calcula todo sobre la clave
tal cual. Con la segunda (dos mitades DES débiles) da CKCV (TDEA) N/A.

- **Familia DES** (VISA/ATALLA, FUTUREX, CKCV TDEA): se calcula sobre la clave
  que muestra la herramienta. Las cuatro dobles coinciden.
- **Familia AES** (AES, SHA-256, CMAC, CKCV AES): la herramienta la calcula
  sobre otra clave, por dos motivos que son suyos y no de la norma:
  - usa la clave **antes** de forzar la paridad (recuperada probando las
    variantes del bit de paridad; una sola reproduce los cuatro valores);
  - con claves de 256 bits usa solo los **primeros 24 bytes**, como AES-192.
    Las generadas sin paridad lo confirman directamente.

  Con esa clave, los ocho casos coinciden. CryptoCarver calcula sobre la clave
  entera que recibe.
- **KCV CMAC**: AES-CMAC sobre entrada vacía, como hace el código.
- **CKCV (TDEA)** y **CKCV (AES)**: CMAC sobre un bloque de ceros, 5 bytes
  (ANSI X9.24-1:2017). Añadidos a la app (validación de claves y nodo KCV).
- **KCV FUTUREX**: TDES de la clave entera sobre `0123456789ABCDEF`, 2 bytes.
  El código tomaba los bytes 2 y 4 de DES(K1, 0); corregido.
- **KCV IBM** y **ATALLA R**: seis muestras (`677A`/`6523`, `FF6E`/`D6E3`,
  `E0FE`/`7846`, `8FA8`/`1E7A`, `F9AE`/`CBBE` con la clave de test clásica,
  `B85B`/`46A5` con la de mitades débiles) y ninguna construcción probada las
  reproduce: cifrado y descifrado de bloques fijos con la clave, sus mitades y
  variantes, vectores de control de IBM CCA, selección de bytes y de nibbles,
  MDC-2/MDC-4, CRC-16, CMAC y hashes. Siguen sin verificar.

## Confirmado con captura

- **ISO-4 AES** (25-09-2026): clave `00112233445566778899AABBCCDDEEFF`, PAN de
  18 dígitos `432198765432109870`, campo PIN `441234AAAAAAAAAA146C6601F4A8035C`.
  Campo PAN `6432…`, intermedios A `2938DEEA…` y B `4D0AC76D…`, bloque
  `88E33C3ACF404F234F10F889C364E377`. Coincide con el arreglo del campo PAN y
  del cifrado.

- **ISO-4, PAN de 13 dígitos** (25-09-2026): `4000123456789` da el campo PAN
  `14000123456789000000000000000000`.
- **ARPC método 2** (25-09-2026): ARQC `A8DB2B65F9C821F1`, CSU `00820000`; la
  herramienta da `54DB2625` seguido del CSU (formato de la etiqueta 91). Se
  introdujo la clave de sesión `38F14068…E3E6`; el registro de la herramienta
  imprime otra (`0D382020…C76B`), que no interviene en el cálculo.
- **dCVV** (25-09-2026), sin resolver. CVK `0123456789ABCDEF0123456789ABCDEF`,
  PAN `4111111111111111`; la herramienta usa caducidad, código de servicio y ATC:

  | Caducidad | Código de servicio | ATC | dCVV |
  |---|---|---|---|
  | 1225 | 001 | 0001 | 938 |
  | 1225 | 001 | 0002 | 488 |
  | 1225 | 001 | 0010 | 634 |
  | 1225 | 101 | 0010 | 970 |
  | 1226 | 101 | 0010 | 282 |

  Ninguna construcción probada reproduce las cinco: el algoritmo CVV con esos
  datos en cualquier orden y relleno, con la clave tal cual o derivada por
  tarjeta (opción A, PSN 00/01/vacío, con y sin paridad), con claves de sesión
  por ATC, ATC en hexadecimal o decimal, y cualquier posición de la salida
  decimalizada. El código actual (`generateDCVV`) tampoco.

## Cruzado después

- **MAC ISO 9797-1 alg 2 y 4**, relleno 1 y 2, con K2: `6095F103D29D763B`,
  `8E0F10E4E8EF75B6`, `B7C3774D7101150B`, `B6E74442CF5607FA`. Coinciden.

## Sin verificar (falta fuente)

- **dCVV**: `generateDCVV` concatena PAN + PSN + caducidad + los 3 primeros
  caracteres del ATC. No tengo fuente pública fiable del formato Visa, y
  truncar el ATC por la izquierda es sospechoso. Necesita una captura de la herramienta externa.
- **Datos de validación IBM 3624** (`0000` + 12 dígitos del PAN): es una
  convención del emisor. payShield usa un campo propio con relleno `N`.
- **MAC ISO 9797-1 alg 6**: la derivación de claves de la segunda instancia
  (K y K' complementadas) es la del código; no hay fuente que la confirme.

## Capturas de la herramienta externa que cerrarían lo pendiente

Con las constantes de arriba:

1. **dCVV**: el texto de ayuda de la pantalla (botón «i») y el CVV normal
   con los mismos datos.
2. **KCV IBM y ATALLA R** de una clave simple (8 bytes), p. ej.
   `0123456789ABCDEF` y `0000000000000000`.
