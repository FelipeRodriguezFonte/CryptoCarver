# Bloques PIN protegidos — C081

El selector de formato es compartido por protección y decodificación. La pantalla construye/muestra el campo/bloque PIN del formato. En formatos distintos de ISO-4, una clave cifra ese bloque claro. ISO-4 con clave invoca su helper AES dedicado, que genera un campo PIN con relleno aleatorio para cifrar. El comportamiento actual sin clave publica un campo/bloque PIN claro e informa `No key supplied`. No llames cifrado a ese resultado.

## Campos y rutas de protección

- PIN: dígitos decimales; la longitud aceptada depende del `PinBlockFormat` seleccionado. PAN se requiere solo en formatos cuyo catálogo indica `usesPan()`, aunque ISO-4 vincula el PAN dentro de AES.
- Las entradas Hex de clave de codificación y decodificación son distintas. Para formatos distintos de ISO-4 la ruta real es ECB sin relleno: clave de 8 bytes selecciona DES; 16 o 24 bytes seleccionan TDES. Sus representaciones Hex tienen 16, 32 o 48 caracteres. El prompt de la UI dice `16/32/48 hex (TDES)`, pero el caso de 16 caracteres usa la rama DES.
- ISO-4 usa AES mediante `PinBlock.encipherPinblockIso4`; acepta claves AES de 16, 24 o 32 bytes (32/48/64 caracteres Hex). El bloque de salida ocupa 16 bytes / 32 caracteres Hex. ISO-4 usa campos PIN y PAN de 16 bytes en la transformación AES.
- Los demás bloques protegidos ocupan 8 bytes / 16 caracteres Hex. Un selector controla ambas direcciones, pero las entradas de clave son independientes.
- C081 no muestra selector de relleno: al proteger se usa el valor predeterminado del formato, independientemente del selector de C080. VISA-2/3 usan 5; ECI-2/3 usan F; Docutel usa `RANDOM_DECIMAL`. Los formatos sin predeterminado declarado siguen su comportamiento interno. La decodificación usa la construcción del formato seleccionado; modificar el selector de relleno de bloques claros no reinterpreta la entrada.
- Sin clave de decodificación, la entrada se interpreta como campo/bloque claro. Un ISO-4 protegido necesita clave AES y el PAN coincidente. La ruta actual del motor puede leer un campo PIN ISO-4 claro sin AES.
- El texto de resultado puede contener material PIN en claro o protegido. En ISO-4 con clave, el handler genera por separado el campo PIN claro que muestra y el campo con relleno aleatorio que cifra el helper AES; pueden diferir. La ayuda recibe solo el código de formato y dos indicadores independientes de clave vacía/no vacía; nunca lee clave, PIN, PAN, bloque ni resultado.

## Ejemplos y diagnósticos

Ida y vuelta TDES: ISO-0, PIN sintético `1234`, PAN `4761739001010010` y una clave TDES de prueba válida de 16 bytes. Protege en un bloque de 8 bytes; decodifica con el mismo formato, PAN y clave en el campo de decodificación para recuperar `1234`.

Ida y vuelta AES: ISO-4, PIN `1234`, el mismo PAN y una clave AES válida de prueba de 16 bytes. Protege en un bloque de 16 bytes/32 caracteres Hex y decodifica con el mismo PAN y la clave de decodificación.

Estado sin clave: con ISO-0 y sin clave de codificación, la pantalla produce un bloque claro de 16 caracteres Hex e indica `No key supplied`. Con ISO-4 sin clave devuelve el campo PIN claro de 32 caracteres Hex, no un bloque protegido.

Casos negativos: una clave TDES de 10 bytes se rechaza; un bloque mal formado/no Hex falla al decodificar; un bloque ISO-4 de tamaño incorrecto falla; una clave o PAN distintos no recuperan el PIN esperado. La ayuda no ejecuta handlers.

## Fuentes

- `PinBlockCoordinator.handleEncodeEncryptedPinBlock()` y `handleDecodeEncryptedPinBlock()` determinan separación de claves, comportamiento sin clave y estado de resultado.
- `PaymentOperations.encryptDesEcb()` / `decryptDesEcb()` eligen DES solo para claves de 8 bytes y TDES en los demás tamaños válidos; `encipherPinBlockISO4()` / `decipherPinBlockISO4()` llaman la ruta AES ISO-4.
- `com.cryptocarver.pin.PinBlock` define validación/tamaño AES y transformación ISO-4.
- `PinBlockCharacterizationUITest` cubre ida/vuelta TDES/AES, claves ausentes/inválidas, bloques mal formados y salida sin clave; `PaymentControlValuesTest` contiene vectores AES ISO-4.
