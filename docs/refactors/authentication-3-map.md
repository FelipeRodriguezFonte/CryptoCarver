# Authentication: mapa de fase 3

Base de esta fase: rama codex/authentication-1, después de fase 2. AuthenticationController.java tiene 955 líneas. Base original de la tanda: main en 7aa3519b2eb77ba4edf9604435d3f21365053b2d.

## Límites verificados

Conteo sobre el archivo de fase 2, desde la declaración de cada método hasta su llave de cierre:

| Método | Líneas | Conteo |
|---|---:|---:|
| handleLoadSignPrivateKey() | 504–508 | 5 |
| handleLoadSignPublicKey() | 513–518 | 6 |
| resolveWindow(...) | 520–522 | 3 |
| handlePasteSignPrivateKey() | 526–530 | 5 |
| handlePasteSignPublicKey() | 534–539 | 6 |
| handlePopulateSigPrivKeyShelf() | 542–546 | 5 |
| handlePopulateSigPubKeyShelf() | 549–554 | 6 |
| loadPrivateKey(String) | 587–626 | 40 |
| loadPublicKey(String) | 634–679 | 46 |
| getKeySize(Object) | 684–696 | 13 |
| loadGeneratedKeyPair(...) | 177–189 | 13 |

El límite elegido abarca la carga y selección de material de firma por archivo, portapapeles, Shelf y par generado, además de los dos parsers, la caché asociada y el cálculo de tamaño. Las firmas públicas de AuthenticationController se conservan como delegados y puente FXML/workbench. Las operaciones de selección MAC de Key Lab y PKCS#11 son responsabilidad de AuthenticationMacCoordinator de fase 2 y no entran aquí.

## Estado vivo leído y escrito

| Estado o servicio | Lectura | Escritura | Observación |
|---|---|---|---|
| signatureAlgorithmCombo | loadPrivateKey/loadPublicKey eligen importador RSA, EC o Ed25519 | Ninguna | Se lee en el momento de completar la selección; si no hay valor se usa RSA. |
| signaturePrivateKeyArea, signaturePublicKeyArea | IngestionUIHelper lee el campo al terminar el archivo, pegado o selección Shelf | IngestionUIHelper modifica el campo; loadGeneratedKeyPair escribe ambos PEM | Los PEM contienen material sensible y no deben aparecer en superficies de captura. |
| AuthenticationKeyState.privateKey/publicKey | La fase 1 los lee al firmar/verificar cuando el campo de texto está vacío | Los cargadores actualizan la clave correspondiente; al fallar el parseo anulan solo esa entrada; loadGeneratedKeyPair actualiza ambas | Es estado compartido con AuthenticationSignatureCoordinator, no debe duplicarse ni quedar obsoleto. |
| signatureKeyStatusLabel | loadPublicKey consulta el texto para saber si debe conservar el estado Private | El loader privado escribe Private; el loader público puede agregar Public si ese texto sigue ahí; loadGeneratedKeyPair escribe su propio mensaje | Por la ruta real de FXML, IngestionUIHelper actualiza primero el label a partir del material nuevo. La selección privada→pública termina en Public solamente; pública→privada termina en Private solamente. Se preservan ambos resultados observables. |
| sigPrivKeyShelfMenu, sigPubKeyShelfMenu, Clipboard, FileChooser | IngestionUIHelper filtra tipos compatibles y obtiene el material seleccionado | Escribe el PEM al control y luego invoca el cargador | Son conectores de UI. El test usa una entrada privada sintética, vacía el Shelf antes de auditar superficies y restaura el Shelf anterior al terminar. |
| mainController (StatusReporter) | Nada en el cálculo de parseo; el flujo de carga usa el servicio | Estado de clave cargada y error legible | Supplier perezoso, actualizado por init(...). No pasar un controlador al coordinador. |
| getKeySize | Lee interfaces RSAKey o ECKey | Ninguna | Lógica pura, devuelve bits o cero para algoritmos sin tamaño soportado. |
| Key Lab / Simulated HSM | El flujo de clave de firma no los consulta | Ninguna | Las claves de firma generadas llegan desde el workbench por loadGeneratedKeyPair; la selección MAC de Key Lab queda fuera de esta fase. |
| AppSettings / visibilidad | No se consultan durante la carga | No se escriben | El material cargado no publica OperationResult; la auditoría prueba que no entra a resultados capturables por estado compartido anterior. |
| Historial, Shelf, inspector, barra, visor expandido y telemetría/logs | Ninguna directa | Carga de claves no debe publicar PEM | La prueba usa claves de prueba inventadas, limpia cualquier entrada sintética del Shelf antes de auditar y comprueba que las superficies no reciben PEM privado bajo FULL_LAB, MASKED ni REDACTED. |

## Lógica de operación frente a cableado

- Elegir el importador según algoritmo, parsear PEM, actualizar/anular el caché de claves y calcular tamaño son lógica del coordinador.
- Abrir el selector de archivos, leer portapapeles/Shelf y escribir los campos/etiquetas son operaciones de UI que se mantienen como rutas FXML hacia el coordinador, usando proveedores vivos del View.
- AuthenticationKeyState queda como único caché mutable compartido con el coordinador de firma. No se cambia crypto/.
- loadGeneratedKeyPair es una API pública ya usada por el workbench. Su firma se conserva; el coordinador aplica la misma validación de par y actualiza ambos campos y claves.

## Decisión de separabilidad

Los cargadores son separables si reciben proveedores de la vista y del AuthenticationKeyState compartido. La etiqueta común muestra el tipo de la última selección, mientras ambos cachés conservan las claves; la secuencia de IngestionUIHelper se preserva junto con esa regla. La caracterización verifica las dos permutaciones, el borrado de la entrada de caché que falla, el resultado de loadGeneratedKeyPair, firma/verificación con ambas claves y la ausencia de PEM privado en superficies de historial, Shelf, inspector, barra, visor y logs. Si no se preserva ese transcript, no se extrae.

## Caracterización

AuthenticationKeyCharacterizationUITest usa un par RSA generado para el test. Prueba selección de privada/pública desde Shelf en ambos órdenes bajo FULL_LAB, carga del par generado bajo MASKED y REDACTED, errores de parseo privado/público legibles en EN/ES, firma/verificación después de cada ruta y ausencia de PEM privado/codificación DER en las superficies restringidas. El Shelf se vacía antes de auditar. No se detectó defecto de producto que requiera arreglo. El SHA-256 protegido es cb01b7abb468fe19fabe21f76351bfc80a20c0706809ce5f6a06c275c090c805.

## Extracción aplicada

AuthenticationKeyCoordinator recibe un record View con Supplier de algoritmo, controles PEM, menús Shelf, etiqueta y AuthenticationKeyState, junto a Supplier<StatusReporter>. El getter perezoso contiene proveedores de referencias a los controles y al holder compartido; no captura AuthenticationController. Los métodos públicos loadGeneratedKeyPair y las rutas FXML de archivo/portapapeles/Shelf permanecen en AuthenticationController como delegados de una línea. El reporter se resuelve al invocar, de forma que init(...) puede sustituirlo sin retener un controlador.

El transcript mantuvo el digest cb01b7abb468fe19fabe21f76351bfc80a20c0706809ce5f6a06c275c090c805 antes y después de extraer. AuthenticationController pasa de 955 a 814 líneas (−141). Los estados de etiqueta observados para selección Shelf siguen siendo Public solamente al elegir privada→pública y Private solamente al elegir pública→privada.

## Puerta de control de fase 3

- `mvn -o -q test -Plow-cpu`: 2864 tests, 0 fallos, 0 errores, 1 omitido (422 informes Surefire actualizados en esta ejecución).
- `mvn -o -q test -Plow-cpu -DrunUiTests=true`: 520 tests, 0 fallos, 0 errores, 0 omitidos (107 informes UI actualizados; recuento distinto de la suite normal).
- ExpandedViewerLifecycleUITest pasó (3 tests). No hubo fallos que comparar contra main.
- AuthenticationSignatureCharacterizationUITest, AuthenticationMacCharacterizationUITest y AuthenticationKeyCharacterizationUITest pasaron; sus asserts verificaron los SHA-256 completos fijados arriba después de la extracción.
- Rama `codex/authentication-1`; ambas puertas están limpias.

## Nota de portabilidad (CI Linux, Java 17)

El transcript original incluía el texto de la excepción de JDK/BouncyCastle (`IOException : null`), que varía según la versión de Java y rompía el digest en el CI de Linux. Las líneas `malformed-private` y `malformed-public` sustituyen ese tramo por `<jdk-exception>`; el resto del transcript es idéntico. El SHA-256 protegido pasa de `cb01b7abb468fe19fabe21f76351bfc80a20c0706809ce5f6a06c275c090c805` a `9d07a8f5e036b5f8c9a634e5ddfc2ebb69e340613fe0e338e55936e980ad42b9`.
