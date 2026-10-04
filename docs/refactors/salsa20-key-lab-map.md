# Salsa20 con claves del Key Lab: mapa

## Flujo actual de ChaCha20

- `SymmetricCipherCoordinator.handleSymmetricEncrypt()` y `handleSymmetricDecrypt()` obtienen el ID de Key Lab con `getHsmKeyId()`. Si la fuente es manual, decodifican el campo de clave; si es Key Lab, pasan el ID y dejan que el proveedor compruebe el uso permitido.
- `handleChaCha20Encrypt()` conserva `warnIfNonceReused("ChaCha20", ...)` y, con un ID, llama a `SimulatedHsmProvider.encryptChaCha20`. El proveedor usa `getUsableKey(keyId, KeyUsage.ENCRYPT)` y delega en `SymmetricCipher.encryptChaCha20` con los bytes de la clave.
- `handleChaCha20Decrypt()` bifurca igual, llamando a `decryptChaCha20`; el proveedor exige `KeyUsage.DECRYPT` y delega en `SymmetricCipher.decryptChaCha20`.
- Tras operar, el coordinador actualiza el área de salida, la barra de estado y publica el resultado para inspector/historial. El nonce no se agrega a esos resultados. La opción A debe copiar esta bifurcación y conservar `warnIfNonceReused` al cifrar.

## Estado actual de Salsa20

- El coordinador encamina Salsa20 a `handleSalsa20Encrypt`/`handleSalsa20Decrypt`, pero ambos llaman a `salsa20Key(hsmKeyId, manualKey)`. Si la clave viene de Key Lab, ese helper lanza `Salsa20 is not available for Key Lab keys; use a manual key` antes de invocar la operación criptográfica.
- No se encontró un test existente que fije literalmente ese mensaje. `SymmetricCipherCharacterizationUITest` caracteriza Salsa20 con clave manual y nonce de 8 bytes; `CipherFieldsCharacterizationUITest` caracteriza los controles y la selección de claves Key Lab, pero no combina esa selección con Salsa20.
- `SymmetricCipher.java` comprueba en `encryptSalsa20` y `decryptSalsa20` que la clave mida 32 bytes y el nonce 8 bytes. Los errores actuales son `Salsa20 requires 256-bit (32 byte) key` y `Salsa20 requires 64-bit (8 byte) nonce`. Esta tarea no cambia ese archivo.
- No se añade Salsa20 como tipo de clave generable/creable en el Key Lab. Se permite usar cualquier clave del Key Lab de 32 bytes, incluido material guardado como AES-256 o ChaCha20.

## Alcance y pruebas

- El proveedor HSM debe añadir solamente `encryptSalsa20`/`decryptSalsa20`, siguiendo los métodos ChaCha20 y validando `ENCRYPT`/`DECRYPT` mediante `getUsableKey`.
- El coordinador debe sustituir el helper que rechaza la clave por la bifurcación de Key Lab/manual de ChaCha20 en ambos caminos. Se conserva la alerta por reutilización de nonce.
- La caracterización usará solo claves, nonce y texto inventados; cubrirá round-trip, equivalencia frente a clave manual, clave de 16 bytes en EN y ES, permisos de uso y ausencia de filtraciones bajo `MASKED`/`REDACTED` en barra de estado, historial y Shelf.
- Los tests que cambien configuración, Shelf o historial guardarán el estado anterior y lo restaurarán en `finally`/`@AfterEach`.
- Las únicas modificaciones permitidas dentro de `crypto/` son en `src/main/java/com/cryptocarver/crypto/hsm/SimulatedHsmProvider.java`.
