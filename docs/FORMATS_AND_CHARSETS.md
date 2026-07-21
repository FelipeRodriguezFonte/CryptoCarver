# Catálogo de formatos y charsets

Este catálogo describe las representaciones disponibles en CryptoCarver. Un
formato representa bytes; no identifica por sí mismo si esos bytes son texto,
una clave, una firma o datos cifrados.

## Representaciones del conversor

| Formato | Uso y regla principal |
|---|---|
| Hexadecimal | Dos dígitos hexadecimales por byte. Espacios y saltos de línea se ignoran cuando procede. |
| Base64 | RFC 4648 convencional. |
| Base64URL | Alfabeto URL seguro (`-`, `_`), sin padding para salida. Adecuado para JOSE. |
| Base32 | RFC 4648 Base32. |
| Base58 | Alfabeto Base58, sin caracteres ambiguos. |
| Base58Check | Base58 con checksum; útil para representaciones tipo Bitcoin. |
| Binary | Bits `0`/`1`, agrupados opcionalmente por espacios. |
| Decimal | Bytes decimales separados por espacio, coma o salto de línea. |
| Text (UTF-8) | Texto UTF-8 validado estrictamente. |
| Text (ASCII) | Sólo caracteres ASCII de 7 bits. |
| Text (ISO-8859-1) | Texto Latin-1, sin sustitución silenciosa de caracteres. |

El conversor rechaza entradas no válidas en vez de corregirlas silenciosamente.
En particular, Base64URL y UTF-8 se validan de forma estricta.

## Charsets de ficheros

La conversión de ficheros permite elegir UTF-8, ASCII, ISO-8859-1, ISO-8859-15,
Windows-1252, UTF-16, UTF-16BE, UTF-16LE, UTF-32, Cp850 y Cp437. Para entradas
grandes utiliza flujo controlado y falla si el charset origen contiene secuencias
malformadas; el destino existente se conserva si la operación falla o se cancela.

## EBCDIC

EBCDIC se habilita de forma explícita en **Manual Conversion**. No se infiere a
partir de bytes que “parecen” EBCDIC. Se puede decodificar EBCDIC a UTF-8 o
codificar UTF-8 a EBCDIC; para este último caso la salida debe ser una
representación de bytes, no texto UTF-8.

| Code page disponible |
|---|
| IBM037 — US/Canada |
| IBM273 — Germany |
| IBM277 — Denmark/Norway |
| IBM278 — Finland/Sweden |
| IBM280 — Italy |
| IBM284 — Spain |
| IBM285 — United Kingdom |
| IBM297 — France |
| IBM420 — Arabic |
| IBM424 — Hebrew |
| IBM500 — International |
| IBM838 — Thai |
| IBM870 — Latin-2 (Central Europe) |
| IBM871 — Iceland |
| IBM875 — Greek |
| IBM918 — Pakistan/Urdu |
| IBM1025 — Cyrillic |
| IBM1026 — Turkish |
| IBM1047 — Latin-1 Open Systems |
| IBM1140 — US/Canada Euro |
| IBM1141 — Germany Euro |
| IBM1142 — Denmark/Norway Euro |
| IBM1143 — Finland/Sweden Euro |
| IBM1144 — Italy Euro |
| IBM1145 — Spain Euro |
| IBM1146 — United Kingdom Euro |
| IBM1147 — France Euro |
| IBM1148 — International Euro |
| IBM1149 — Iceland Euro |

## Formatos especializados

- **BCD / COMP-3:** representaciones decimales empaquetadas para escenarios de
  host y pagos. El signo debe estar presente cuando el formato lo exige.
- **Endian:** la herramienta cambia el orden de bytes; no convierte el valor de
  texto de manera implícita.
- **Hexadecimal comprimido (2-row):** intercala dos filas de host de igual
  longitud. `AFD12` y `CA123` se expanden a `ACFAD11223`; la operación inversa
  genera las dos filas originales.
- **URL encoding y Quoted-Printable:** son codificaciones de texto, no cifrado.
  Indica UTF-8 como charset del texto de origen salvo que el protocolo defina otro.

## Regla práctica

Si un dato recién descifrado no tiene sentido como UTF-8, míralo primero como Hex
en **Byte Inspector** y compara los charsets candidatos. Sólo activa EBCDIC cuando
conozcas o hayas validado el code page del sistema origen.
