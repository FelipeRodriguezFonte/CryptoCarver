# Bloques PIN en claro — C080

Esta pantalla construye o interpreta un campo/bloque PIN en Hex claro. Un campo PIN claro no está cifrado ni demuestra que esté protegido. Codificación y decodificación tienen selectores de formato independientes; el selector de relleno solo afecta a la codificación y únicamente a formatos que lo exponen.

## Entradas y resultados

- PIN es texto decimal. La UI del bloque claro comprueba 4–12 dígitos; varios formatos heredados limitan más en el motor (VISA-2/ECI-3/Docutel 4–6; ECI-2 exactamente 4). El PIN no se copia al contexto de ayuda.
- PAN son 13–19 dígitos decimales cuando se proporciona. Es obligatorio para los formatos vinculados al PAN. El codificador también rechaza un PAN no vacío mal formado en formatos cuya implementación lo ignora. Antes de codificar/decodificar se eliminan espacios del PAN.
- Un bloque habitual ocupa 8 bytes / 16 caracteres Hex. Los campos ISO-4 claros de PIN y PAN ocupan cada uno 16 bytes / 32 caracteres Hex. Los caracteres Hex no son bytes.
- Para decodificar hacen falta bloque y, en formatos vinculados al PAN, PAN. La UI actual de ISO-4 también requiere PAN no vacío y acepta un campo PAN de 32 caracteres Hex; el decodificador del campo PIN ISO-4 lee ese campo y no usa esa entrada.
- El resultado muestra bloque/PIN y detalles de la UI actual; puede contener valores sensibles. La ayuda no lee campos ni el texto del resultado.

## Comportamiento según el catálogo implementado

| Formato | Comportamiento PAN en esta implementación | Representación clara / regla efectiva del PIN |
|---|---|---|
| Format 0 (ISO-0), ANSI X9.8, VISA-1, ECI-1 | 12 dígitos del PAN desde la derecha (antes del dígito de control), prefijo `0000` y XOR con el campo PIN | Campo ISO-0 de 8 bytes; control 0, longitud, dígitos y relleno fijo `F` |
| Format 1 (ISO-1), ECI-4 | No usa PAN | Campo de 8 bytes; longitud y dígitos con relleno hexadecimal aleatorio |
| Format 2 (ISO-2) | No usa PAN | Campo de 8 bytes; control 2, longitud, dígitos y relleno fijo `F` |
| Format 3 (ISO-3) | Los mismos 12 dígitos a la derecha, antes del dígito de control, prefijo `0000` y XOR | Campo de 8 bytes con relleno Hex aleatorio antes del XOR |
| Format 4 (ISO-4) | Representa el PAN completo: primer nibble longitud menos 12; se completa a la izquierda hasta 12 dígitos y a la derecha con ceros hasta 32 caracteres Hex. Incluye el dígito de control. | Campo PIN claro de 16 bytes con control 4 y longitud, relleno fijo `A` hasta nibble 15 y Hex aleatorio en nibbles 16–31. No es un bloque ISO-4 protegido en claro. El campo PAN separado ocupa 16 bytes. |
| IBM 3624 | El codificador ignora el PAN | Campo de 8 bytes; dígitos PIN y relleno `F`, sin nibble de longitud/control |
| VISA-2 | El codificador ignora el PAN | 4–6 dígitos; longitud, dígitos, ceros hasta nibble 6 y relleno decimal repetido |
| VISA-3 | El codificador ignora el PAN | 4–12 dígitos, delimitador `F` y después relleno repetido |
| ECI-2 (no vincula PAN) | El codificador ignora el PAN | Exactamente 4 dígitos PIN seguidos de relleno hexadecimal |
| ECI-3 (no vincula PAN) | El codificador ignora el PAN | 4–6 dígitos, nibble de longitud y relleno hexadecimal |
| Docutel (Format 02) | El codificador ignora el PAN | 4–6 dígitos; longitud, ceros hasta seis dígitos y nueve nibbles decimales de relleno |
| Diebold (Format 03) | El codificador ignora el PAN | 4–12 dígitos seguidos por nibbles `F`, sin nibble de longitud |
| VISA-4, Plus Network (Format 04) | Primeros 12 dígitos del PAN desde la izquierda, excluido el dígito de control final; prefijo `0000` y XOR | Campo de 8 bytes; control 0, longitud, PIN y relleno `F` |
| Europay/MasterCard (Pay Now & Pay Later) | Los mismos 12 dígitos desde la derecha antes del dígito de control que ISO-0 | Estructura ISO-0 con control 2 |

El combo de relleno se configura desde `PinBlockFormat.paddingOptions()` y el valor inicial desde `defaultPadding()`. Las opciones son nibble fijo, `RANDOM_HEX` (0–F) o `RANDOM_DECIMAL` (0–9), pero cada formato expone solo su subconjunto. VISA-2/VISA-3 permiten dígitos decimales y `RANDOM_DECIMAL` (predeterminado `5`); ECI-2/ECI-3/Diebold permiten nibbles Hex y ambos rellenos aleatorios (predeterminado `F`); Docutel permite dígitos decimales y `RANDOM_DECIMAL` (predeterminado `RANDOM_DECIMAL`). Los demás formatos no exponen relleno configurable. El formato puede aplicar relleno interno fijo/aleatorio aunque el combo esté deshabilitado.

## Ejemplos y diagnósticos

Ida y vuelta sintética: PIN `1234`, PAN `4761739001010010`, ISO-0. La codificación produce un bloque claro de 8 bytes/16 caracteres Hex. Decodifica la salida con ISO-0 y el mismo PAN para recuperar `1234`. ISO-2 no incluye PAN en la construcción del bloque PIN.

Casos negativos: `12A4` no es un PIN decimal; PAN vacío se rechaza para ISO-0; `A` no es relleno válido para VISA-2. Decodificar rechaza bloques no Hex y longitudes distintas de 16 caracteres Hex, o 32 para ISO-4. Son reglas actuales de UI/motor; la ayuda no cambia el comportamiento criptográfico.

## Fuentes

- `PinBlockFormat.displayNames()`, `usesPan()`, `paddingOptions()` y `defaultPadding()`.
- `PinBlockCoordinator`: entrada, selectores independientes y validaciones actuales de UI.
- `PaymentOperations`: codificadores/decodificadores reales y construcción de campos PAN.
- `PinBlockCharacterizationUITest`, `PaymentOperationsLegacyPinBlockTest` y `LegacyPinBlockFormatTest`.
- Los perfiles ISO están identificados en `PinBlockFormat`; formatos heredados 02–04 remiten a payShield Host Programmer's Manual 1270A542-038 v3.5, pp. 171–172, y las variantes capturadas se identifican en `docs/CAPTURAS_PIN_BLOCKS_HEREDADOS.md`.
