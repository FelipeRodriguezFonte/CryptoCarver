# Espacio de cifrado simétrico

Esta referencia sigue `SymmetricCipher`, `SymmetricCipherCoordinator`, `SymmetricFieldsPresenter` y el perfil Symmetric Ciphers registrado para formatos.

## Parámetros y aplicabilidad

La clave manual se introduce en hexadecimal. AES-128/192/256 usa claves de 16/24/32 bytes; DES usa 8; 3DES acepta 16 o 24 bytes. El origen HSM simulado selecciona una referencia local de Key Lab. El contexto de ayuda registra solo códigos públicos de selectores y si hay material; nunca lee clave, payload, tag, AAD, portapapeles ni rutas.

ECB no usa IV y la interfaz advierte sobre bloques de texto claro repetidos. CBC usa IV y puede usar el padding elegido. CTR, CFB, OFB y GCM fijan `NoPadding` en la interfaz. Los algoritmos de flujo deshabilitan modo y padding, conservando sus valores anteriores. La compatibilidad adicional depende de la implementación/proveedor criptográfico activo; el selector no filtra todas las combinaciones de algoritmo por bloques y modo.

Los tamaños generados de IV/nonce son recomendaciones: AES por bloques 16 bytes; DES/3DES y Salsa20 8; GCM y variantes ChaCha20 12; XChaCha20-Poly1305 24. ChaCha20 sin autenticación acepta nonce de 8 o 12 bytes en el núcleo; su generador crea 12. ChaCha20-Poly1305 requiere 12 bytes y XChaCha20-Poly1305 requiere 24. El núcleo GCM acepta nonce de 8, 12 o 16 bytes y genera 12. GCM y las variantes Poly1305 muestran tag de 16 bytes. Salsa20/ChaCha20 sin Poly1305 y los modos por bloques distintos de GCM no autentican ciphertext.

### Opciones de padding

`NoPadding` no añade bytes. Si la transformación por bloques lo requiere, como ECB/CBC sin padding, la entrada debe estar alineada a bloques. La interfaz fuerza `NoPadding` para CTR, CFB, OFB y GCM, y deshabilita padding en algoritmos de flujo. En transformaciones ECB/CBC compatibles, PKCS5/PKCS7 añade un bloque completo si los datos ya estaban alineados; cada byte añadido contiene el número de bytes de relleno. La aplicación asigna la opción `PKCS7Padding` al nombre de transformación `PKCS5Padding` del proveedor. ISO10126 usa bytes aleatorios seguidos por la longitud del padding; ISO7816-4 usa `0x80` seguido de ceros. ZeroBytePadding añade ceros solo cuando hacen falta y, al descifrar, elimina los ceros finales; por eso los datos originales que terminan en cero son ambiguos. El proveedor determina si funciona una combinación concreta de algoritmo, modo y padding.

GCM admite AAD opcional. En la ruta GCM por bloques, AAD se interpreta como Hex y, si falla el análisis, se usa US-ASCII. Los handlers dedicados actuales de ChaCha20-Poly1305 y XChaCha20-Poly1305 muestran AAD pero no lo pasan a la operación. Es una limitación documentada de la implementación, no un cambio de comportamiento de la ayuda.

No reutilices un nonce con la misma clave en GCM ni en cifrados de flujo. La aplicación avisa de reutilización durante la sesión actual; esto no hace segura la reutilización.

## Ejemplo reproducible

Usa solo datos sintéticos: AES-256/GCM; clave `000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F`; nonce `000102030405060708090A0B`; AAD `101112`; payload UTF-8 `test`. El cifrado devuelve ciphertext y un tag separado de 16 bytes. Conserva ciphertext, nonce, tag y AAD. Al descifrar con la misma clave y valores se recupera `test`. Alterar un byte del tag debe fallar la autenticación. Una clave AES con longitud incorrecta se rechaza antes de una operación válida.

El formato global del payload controla su decodificación/codificación a bytes. Clave, IV/nonce, tag y AAD GCM por bloques tienen manejo hexadecimal propio.
