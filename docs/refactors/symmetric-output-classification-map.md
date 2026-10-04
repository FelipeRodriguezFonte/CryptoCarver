# Mapa: clasificación de salida del cifrado simétrico

Base: `main` en `88d04fadaa7ab33bd8a0c37995e8c930d7f68978`. Este documento caracteriza el estado actual; no cambia código ni fija todavía el criterio definitivo.

## Hallazgo principal

`OperationResult.Builder` inicializa tanto `outputClassification` como `enrichedOutputClassification` en `PUBLIC` ([OperationResult.java:59–60](../../src/main/java/com/cryptocarver/model/OperationResult.java#L59)). La sobrecarga `.output(byte[])` delega explícitamente a `.output(value, PUBLIC)` ([OperationResult.java:70–76](../../src/main/java/com/cryptocarver/model/OperationResult.java#L70)). No se infiere que una salida sea sensible por llamarse «decrypt» ni por contener texto claro.

En el coordinador simétrico, Salsa20 es la única excepción: sus dos manejadores pasan `SENSITIVE` explícitamente. Las salidas de cifrado y descifrado de los demás manejadores quedan `PUBLIC`. La causa inmediata es que esas llamadas usan `.output(...)` sin clasificación, cuyo valor predeterminado es público; la clasificación actual de Salsa20 viene del cambio que añadió la clasificación explícita.

`ResultPresentationPolicy.classifyPublishedResult` calcula la clasificación efectiva como el máximo de `output`, `enrichedOutput` y los detalles ([ResultPresentationPolicy.java:35–45](../../src/main/java/com/cryptocarver/model/ResultPresentationPolicy.java#L35)). Los detalles del coordinador simétrico son públicos; por tanto, hoy la clasificación efectiva coincide con la de `output` salvo que exista una salida enriquecida, que también es pública en los manejadores AEAD.

## Matriz actual

`PUBLIC` significa que los consumidores de resultado la muestran y permiten capturarla bajo los tres perfiles. `SENSITIVE` significa visible en `FULL_LAB`, enmascarada como `***MASKED***` en `MASKED` y `REDACTED`.

| Algoritmo / ruta | Operación | Clasificación efectiva actual | Sitio que publica |
|---|---|---|---|
| DES, 3DES (Triple DES), AES-128, AES-192, AES-256; modos genéricos | Cifrar | `PUBLIC` | [`SymmetricCipherCoordinator.java:199–201`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L199) usa `.output(ciphertext)` |
| DES, 3DES, AES; modo GCM genérico | Descifrar | `PUBLIC` (`output` y `enrichedOutput`) | [`SymmetricCipherCoordinator.java:347–351`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L347) usa `.output(plaintext)` y asigna `displayGCMResult(plaintext, false)` como salida enriquecida; ese informe contiene `PLAINTEXT` ([líneas 500–535](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L500)) |
| DES, 3DES, AES; modos genéricos distintos de GCM | Descifrar | `PUBLIC` | [`SymmetricCipherCoordinator.java:347–351`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L347) usa `.output(plaintext)` |
| ChaCha20 | Cifrar | `PUBLIC` | [`SymmetricCipherCoordinator.java:553–558`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L553), `.output(ciphertext)` |
| ChaCha20 | Descifrar | `PUBLIC` | [`SymmetricCipherCoordinator.java:577–582`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L577), `.output(plaintext)` |
| Salsa20 | Cifrar | `SENSITIVE` | [`SymmetricCipherCoordinator.java:597–602`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L597) |
| Salsa20 | Descifrar | `SENSITIVE` | [`SymmetricCipherCoordinator.java:644–649`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L644) |
| ChaCha20-Poly1305 | Cifrar | `PUBLIC` (`output` y `enrichedOutput`) | [`SymmetricCipherCoordinator.java:624–630`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L624); el informe enriquecido muestra ciphertext y tag ([líneas 485–498](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L485)) |
| ChaCha20-Poly1305 | Descifrar | `PUBLIC` | [`SymmetricCipherCoordinator.java:678–683`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L678) |
| XChaCha20-Poly1305 | Cifrar | `PUBLIC` (`output` y `enrichedOutput`) | [`SymmetricCipherCoordinator.java:707–713`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L707) |
| XChaCha20-Poly1305 | Descifrar | `PUBLIC` | [`SymmetricCipherCoordinator.java:742–747`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L742) |

La lista de algoritmos y la distribución entre manejadores genéricos, de flujo y AEAD se encuentran en [`SymmetricCipher.java:30–39`](../../src/main/java/com/cryptocarver/crypto/SymmetricCipher.java#L30) y [`SymmetricCipherCoordinator.java:134–147`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L134). AES-GCM entra por la ruta genérica de bloques: su informe enriquecido de descifrado también incluye el texto claro, pero mantiene el `PUBLIC` predeterminado.

## Efecto en los consumidores y perfiles

| Consumidor | `PUBLIC` actual | `SENSITIVE` actual | Fuente de decisión |
|---|---|---|---|
| Historial de operación | El detalle `Output` se crea como `SENSITIVE` para cualquier resultado, independientemente de la clasificación de salida. Se enmascara bajo `MASKED` y `REDACTED`; en `FULL_LAB` queda visible. | Igual. | [`ResultPresentationPolicy.java:68–80`](../../src/main/java/com/cryptocarver/model/ResultPresentationPolicy.java#L68) y [`ModernMainController.java:1011–1028`](../../src/main/java/com/cryptocarver/ui/ModernMainController.java#L1011). |
| Receta editable guardada en historial | `FULL_LAB` conserva los valores; los perfiles restringidos sustituyen campos sensibles por `[REDACTED_SECRET]`. Los nombres de campos con `key`, `iv`, `nonce`, `input` o `payload` se consideran sensibles. | Igual. | [`UiStateSnapshot.java:49–56`](../../src/main/java/com/cryptocarver/ui/UiStateSnapshot.java#L49), [líneas 96–103](../../src/main/java/com/cryptocarver/ui/UiStateSnapshot.java#L96), [líneas 155–180](../../src/main/java/com/cryptocarver/ui/UiStateSnapshot.java#L155). |
| Shelf | Puede capturarse en los tres perfiles. | Se bloquea bajo `MASKED` y `REDACTED`; `FULL_LAB` lo permite según las reglas de Shelf. | [`ResultPresentationPolicy.java:28–33`](../../src/main/java/com/cryptocarver/model/ResultPresentationPolicy.java#L28), [`ResultCaptureCoordinator.java:158–160`](../../src/main/java/com/cryptocarver/ui/ResultCaptureCoordinator.java#L158), [`ResultViewerCoordinator.java:69–85`](../../src/main/java/com/cryptocarver/ui/ResultViewerCoordinator.java#L69). |
| Barra de estado | En estas rutas solo recibe textos constantes del tipo «Encrypted/Decrypted using …», sin bytes de salida, claves ni nonces. | Igual; la clasificación no altera el estado. | [`ResultPublicationCoordinator.java:40–45`](../../src/main/java/com/cryptocarver/ui/ResultPublicationCoordinator.java#L40) y estados por manejador en [`SymmetricCipherCoordinator.java:552–582`](../../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java#L552). |
| Visor expandido | Muestra el resultado en los tres perfiles, también el texto claro de las rutas actuales `PUBLIC`. | Muestra el resultado en `FULL_LAB`; reemplaza el contenido por `***MASKED***` bajo `MASKED` y `REDACTED`. | [`ModernMainController.java:1368–1383`](../../src/main/java/com/cryptocarver/ui/ModernMainController.java#L1368), [`ResultCaptureCoordinator.java:112–118`](../../src/main/java/com/cryptocarver/ui/ResultCaptureCoordinator.java#L112), [`OperationResultRenderer.java:43–63`](../../src/main/java/com/cryptocarver/ui/OperationResultRenderer.java#L43) y [líneas 112–121](../../src/main/java/com/cryptocarver/ui/OperationResultRenderer.java#L112). |

El exportador del historial filtra por clasificación de cada detalle: en `MASKED` enmascara lo que no sea `PUBLIC`; en `REDACTED` enmascara `SENSITIVE` y omite `SECRET` ([`HistoryRecordExporter.java:58–85`](../../src/main/java/com/cryptocarver/utils/HistoryRecordExporter.java#L58)). Por tanto, el payload de salida en historial ya queda protegido en perfiles restringidos, aunque la salida publicada tenga clasificación `PUBLIC`. Esa protección del historial no se propaga automáticamente al Shelf ni al visor expandido.

En resumen para las salidas simétricas actuales: el historial protege el texto claro en perfiles restringidos; los estados no incluyen el contenido; Shelf y visor expandido sí exponen actualmente las salidas `PUBLIC` de descifrado. Salsa20 ya queda bloqueado/enmascarado en esos dos consumidores porque su salida de descifrado es `SENSITIVE`.

## Comparación con otras operaciones

El repositorio no aplica una regla global según la operación; decide por tipo de artefacto y módulo:

| Área | Ejemplo y clasificación actual | Qué indica |
|---|---|---|
| Hashing | El digest se publica con `.output(hash)` y por tanto `PUBLIC` ([`HashingCoordinator.java:227–231`](../../src/main/java/com/cryptocarver/ui/HashingCoordinator.java#L227)). | El resumen calculado se trata como resultado público; el input se registra por separado como detalle sensible del historial. |
| JOSE | La salida de JWE descifrado y el payload de JWT anidado verificado usan `.output(payload)` sin clasificación ([`JOSEController.java:1105–1108`](../../src/main/java/com/cryptocarver/ui/JOSEController.java#L1105), [líneas 1200–1208](../../src/main/java/com/cryptocarver/ui/JOSEController.java#L1200)). | Hay texto descifrado publicado como `PUBLIC` fuera del módulo simétrico; la práctica no es uniforme. |
| COSE | Tanto el mensaje cifrado de Encrypt0 como el payload recuperado por Decrypt0 se publican como `SECRET` ([`COSEController.java:337–342`](../../src/main/java/com/cryptocarver/ui/COSEController.java#L337), [líneas 369–373](../../src/main/java/com/cryptocarver/ui/COSEController.java#L369)). | Otro módulo protege también los artefactos cifrados; es una política más estricta que la propuesta de ciphertext público. |
| EMV | El helper [`EMVController.java:553–559`](../../src/main/java/com/cryptocarver/ui/EMVController.java#L553) recibe una decisión explícita `secret`; por ejemplo, publica un LUK como `SECRET` ([líneas 583–587](../../src/main/java/com/cryptocarver/ui/EMVController.java#L583)) y un digest como `PUBLIC` ([líneas 644–653](../../src/main/java/com/cryptocarver/ui/EMVController.java#L644)). | Clasifica por sensibilidad del valor producido, no por si hubo cálculo criptográfico. |
| Claves | La derivación KDF y la importación de una clave recuperada marcan `output` y salida enriquecida como `SECRET` ([`KdfKeyWrapCoordinator.java:388–398`](../../src/main/java/com/cryptocarver/ui/KdfKeyWrapCoordinator.java#L388), [`RsaKeyExchangeCoordinator.java:121–129`](../../src/main/java/com/cryptocarver/ui/RsaKeyExchangeCoordinator.java#L121)). | El material de clave se protege explícitamente como `SECRET`, que es más restrictivo que `SENSITIVE`. |

La comparación no da una respuesta única preexistente: hashing/JOSE suelen dejar `PUBLIC` por defecto, COSE clasifica como `SECRET` incluso el cifrado, y EMV/claves expresan la política explícitamente según el artefacto.

## Criterio propuesto para aprobación

1. El texto claro recuperado por cualquier operación de descifrado simétrico se publica como `SENSITIVE`.
2. El ciphertext y su tag de autenticación se publican como `PUBLIC`.
3. Un informe enriquecido de descifrado que incluya texto claro recibe también `SENSITIVE`; el informe enriquecido de cifrado que solo muestre ciphertext/tag recibe `PUBLIC`.
4. Claves, nonces e información de autenticación secreta no se agregan a `output` ni a informes públicos; el criterio de esta propuesta se limita a clasificar la salida.

Si se aprueba, el cambio de Salsa20 cifrado de `SENSITIVE` a `PUBLIC` es deliberado para cumplir la regla ciphertext público; Salsa20 descifrado permanece `SENSITIVE`. El test existente `salsa20RestrictedProfilesProtectDecryptedOutputFromHistoryShelfAndStatus` caracteriza la protección del texto claro descifrado ([`SymmetricCipherCharacterizationUITest.java:271–317`](../../src/test/java/com/cryptocarver/ui/SymmetricCipherCharacterizationUITest.java#L271)); no se ha editado ni ejecutado en esta fase.

La propuesta es más estricta con salidas simétricas de descifrado que los ejemplos actuales de JOSE y hashing. No se infiere que esos otros módulos deban cambiarse: cualquier ampliación de alcance requeriría una decisión aparte.

## Alcance y estado

- Solo se creó este mapa en esta fase; no se modificó código de producción, tests ni archivos bajo `crypto/`.
- No se añadieron ni ejecutaron tests.
- La implementación, los tests rojos/caracterización y cualquier cambio de digest quedan pendientes de aprobación explícita del criterio.
