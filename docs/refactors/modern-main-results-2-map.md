# Modern main results — fase 2

## Inventario previo

ModernMainController: 2751 líneas. La captura usa ResultAreaTracker; la publicación, los menús y las acciones finales permanecen en ResultPublicationCoordinator y ResultViewerCoordinator. No se cambia navegación ni crypto/.

## Área activa y orden de captura

| Superficie | Copy / Expand | Add to Shelf |
| --- | --- | --- |
| Hash, Cipher, Authentication, Payments, Generic y demás módulos con TextArea | Snapshot publicado primero; sin él, área enfocada registrada de solo lectura y después resultado visible más largo | Área actualizada visible registrada de solo lectura, después enfocada y después resultado visible más largo; finalmente snapshot de la misma pantalla |
| Keys asimétricas | Área publicKeyArea/privateKeyArea enfocada visible gana incluso con snapshot; sin foco, snapshot | Rutas RSA/ECDSA/DSA/EdDSA Key Generation delegan a Keys, que selecciona material explícito |
| Keys simétricas, Key Generation | Snapshot; la clave vive en TextField fuera del tracker | Delegación a Keys.handleGlobalSymmetricShelfAction |
| Generic Workbench | Regla general de TextArea/snapshot | Un resultado actualizado/enfocado válido gana; si falta, Workbench visible con vista de material activa |

El tracker ignora controles editables para captura; el descubrimiento exige identificadores output/result/report/details/ciphertext/publicKeyArea/privateKeyArea. Comprueba visible en todos los ancestros y TitledPane expandido. Sin snapshot, Copy/Expand eligen primero foco y luego el texto visible más largo; Shelf exige además contenido no vacío. Un snapshot con payload hace que preferred devuelva null, salvo foco de par de claves. El texto enriquecido publicado precede a bytes y a resumen de detalles. Shelf solo acepta payload, nunca un resumen sin artefacto. shelfSnapshot exige lastPublishedScreen == currentActiveOperation.

Las reglas son comunes a los módulos y leen el árbol del panel activo; no existe una tabla de áreas por módulo en el shell. La selección concreta del panel sigue siendo responsabilidad de la navegación de fase 1.

## Visibilidad y material privado

| Perfil | SECRET | SENSITIVE | PUBLIC |
| --- | --- | --- | --- |
| FULL_LAB | Texto completo | Texto completo | Texto completo |
| MASKED | ***MASKED*** | ***MASKED*** | Texto completo |
| REDACTED | Vacío | ***MASKED*** | Texto completo |

El renderer publicado aplica primero la clasificación máxima de detalles y después la del payload elegido. El renderer de área usa classificationForResultArea: privateKeyArea → SECRET, publicKeyArea → PUBLIC, área actualizada con snapshot → clasificación publicada, heurística de id (privatekey/secret/kdf/pin/pass/pwd/cvv/dukpt/keywrap → SECRET; key/mac/iv/cipher → SENSITIVE), después snapshot o PUBLIC.

Shelf elimina vacío, máscara y marcador PRIVATE KEY MATERIAL / NOT RECORDED. ResultViewerCoordinator vuelve a comprobar los marcadores; solo añade una clave privada PEM completa desde captura completa y FULL_LAB, como entrada de sesión. ClipboardShelfManager es otra barrera de persistencia. Los perfiles restringidos bloquean selecciones SECRET/SENSITIVE. Copy y Expand comparten resolveCurrentOutputText; los bloqueos finales y el visor permanecen en el shell.

## Estado compartido, proveedores y código muerto

Se leen resultAreaTracker (registro, foco, última actualización), lastPublishedResultSnapshot, lastPublishedScreen, currentActiveOperation, mainPane, genericContainer, genericContainerController, keysController y AppSettings.secretVisibilityProfile. La extracción recibirá Supplier de todos esos valores; el constructor solo guarda proveedores. Los efectos son status, diálogo informativo y captura Shelf segura mediante callbacks.

isContainerVisible también sirve handleClearInput y permanece compartido fuera del coordinador. renderPublishedResult y classifyPublishedResult tienen consumidores externos y permanecen como puntos de entrada del shell. Los métodos privados del alcance tienen llamadas internas o reflexión de tests; se conservan delegados. createResultContextMenu parece un delegado sin llamadas: búsqueda en src/main, recursos FXML y src/test no encuentra llamadores; queda fuera del alcance y no se elimina. No se borra código muerto en esta fase.

## Revisión de caracterización

Pendiente de la transcripción antes de fijar SHA-256. Se comprobarán diferencias deliberadas entre payload publicado y área renderizada, áreas ocultas, marcadores y claves privadas; cualquier fuga o selección incorrecta se registrará aquí como fallo y se corregirá después de la extracción, en commit propio.
