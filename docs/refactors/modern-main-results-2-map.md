# Modern main results — fase 2

## Inventario previo

ModernMainController: 2751 líneas. La captura usa ResultAreaTracker; la publicación, los menús y las acciones finales permanecen en ResultPublicationCoordinator y ResultViewerCoordinator. No se cambia navegación ni crypto/.

## Área activa y orden de captura

| Superficie | Copy / Expand | Add to Shelf |
| --- | --- | --- |
| Hash, Cipher, Authentication, Payments, Generic y demás módulos con TextArea | Snapshot publicado primero; sin él, área enfocada registrada de solo lectura y después resultado visible más largo | Área actualizada visible registrada de solo lectura, después enfocada y después resultado visible más largo; finalmente snapshot de la misma pantalla |
| Keys asimétricas | Área publicKeyArea/privateKeyArea enfocada visible gana incluso con snapshot; sin foco, snapshot | Rutas RSA/ECDSA/DSA/EdDSA Key Generation delegan a Keys, que selecciona material explícito |
| Keys simétricas, Key Generation | Snapshot; la clave vive en generatedKeyField (TextArea de solo lectura cuyo id no cumple el filtro del tracker) | Delegación a Keys.handleGlobalSymmetricShelfAction |
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

Transcripción revisada antes de fijar SHA-256: seis manejadores reales (Hashing, Manual Conversion, AES CBC, Key Generation, RSA con pestaña PRIVATE y Clear PIN Blocks), cada uno con FULL_LAB/MASKED/REDACTED. Se registra contenido capturado por Copy/Expand, resolver Shelf, entrada real (contenido, formato y clasificación), estado y datos de entrada inventados. Las claves aleatorias se normalizan y se comprueban contra su valor original; el bloque PIN inventado se comprueba exactamente y se reemplaza por token en la transcripción para no divulgar material privado. Logs stdout/stderr se capturan y se comprueba ausencia de las fixtures secretas; Shelf restringido se comprueba para las claves generadas. Los dos bugs de la transcripción se conservan solo provisionalmente, identificados explícitamente, hasta sus commits de corrección.

Los límites se repiten por perfil: sin resultado, pantalla diferente de la publicación, área no visible y pantalla visible distinta del payload. Copy/Expand captura PUBLISHED-OUTPUT y Shelf VISIBLE-SCREEN-OUTPUT en el caso registrado y visible: diferencia prevista, no fallo. Una publicación de otra pantalla no se añade al Shelf. Las pestañas privadas de Keys quedan protegidas en los perfiles restringidos, y Shelf privado solo existe en sesión FULL_LAB. En el shell cargado, Copy/Expand FULL_LAB captura el resultado publicado de RSA, distinto del informe privado del control; la ruta Shelf de Keys captura PEM privado completo. El resultado publicado incluye el mismo material privado de la operación activa; no es un resultado de otro módulo.

Se guarda una copia normalizada revisable en src/test/resources/com/cryptocarver/ui/result-capture-baseline.txt. SHA-256 del texto UTF-8 unido por LF sin LF final: `f9e29640f71909496daf18fd67a1098cf7653940603114e722a102522c84fc18`. Las dos ejecuciones de ResultCaptureCharacterizationUITest pasan (1 test, 0 fallos, 0 errores).

## Fallos confirmados antes de corregir

| Fallo | Severidad | Evidencia anterior al arreglo | Corrección prevista |
| --- | --- | --- | --- |
| PEM privado en área genérica PUBLIC | Alta: fuga a Copy/Expand y posible persistencia Shelf | ResultCaptureSecurityUITest.privateMaterialInGenericAreaCannotEscapeRestrictedProfiles falla en MASKED porque el resolver entrega el PEM inventado | Clasificar por contenido reconocible privado antes de heurísticas de id/snapshot; FULL_LAB usa Shelf de sesión |
| Payload privado publicado PUBLIC | Alta: fuga a Copy/Expand y Shelf | ResultCaptureSecurityUITest.privatePayloadWithPublicMetadataCannotEscapeRestrictedProfiles falla en MASKED porque el resolver entrega el PEM inventado | Aplicar barrera de contenido privado a la captura del payload publicado y a su clasificación |

Ejecución previa de regresiones: 2 tests, 2 fallos, 0 errores. Fixtures inventadas, sin imprimir valores en mensajes de aserción. Son dos caminos diferentes; tendrán commits de corrección separados. Las aserciones miran el texto que consume Expand y el portapapeles real, y comprueban ausencia de entradas Shelf con perfiles restringidos. El primer test también exige entrada privada de sesión en FULL_LAB.

El comentario heredado del handler habla de TextField; el FXML actual declara generatedKeyField como TextArea. La razón de la ruta especial sigue vigente: el id no satisface isLikelyResultArea. Se conserva el comentario durante la extracción literal y se documenta aquí la discrepancia.

La revisión de los manejadores reales añade un tercer fallo de severidad alta: Encode PIN Block y Decode PIN Block publican material en claro como PUBLIC. El bloque ISO-0 con su PAN permite recuperar el PIN. La captura publicada y la clasificación del área actualizada deben tratar esos dos resultados como SECRET, sin cambiar los controladores de publicación ni crypto/. Hash y ciphertext AES PUBLIC son válidos; RSA público en Shelf bajo perfiles restringidos también es válido. La ausencia de entrada en Shelf para una clave simétrica restringida es un bloqueo correcto, no una entrada de longitud seis.

## Extracción

ResultCaptureCoordinator recibe proveedores vivos de Keys, Generic, host Generic, mainPane, tracker, snapshot, pantalla publicada, operación activa y perfil. El constructor no evalúa ninguno; los efectos se suministran como callbacks de estado, información y Shelf segura. ResultCaptureCoordinatorTest cambia snapshot/perfil después de construir el coordinador y comprueba las capturas, además de rechazar evaluación temprana del proveedor.

ModernMainController conserva los trece métodos del alcance como delegados de una línea. No se movieron campos consultados por reflexión: no hizo falta adaptar tests existentes ni FXML. Se renombró la variable local del perfil a `profile` para evitar que ocultase el Supplier `visibility`; no se sustituyeron identificadores dentro de cadenas literales. isContainerVisible conserva su equivalente local y el auxiliar compartido del shell permanece para Clear Input.

Extracción: 2751 → 2623 líneas en ModernMainController; coordinador inicial 218 líneas. Verificación: ResultCaptureCharacterizationUITest y ResultCaptureCoordinatorTest, 2 tests, 0 fallos, 0 errores. SHA de caracterización intacto: `f9e29640f71909496daf18fd67a1098cf7653940603114e722a102522c84fc18`.

## Corrección 1: material privado en área genérica

classificationForResultArea comprueba primero el contenido: marcador de material privado o delimitador PRIVATE KEY----- (incluidos encabezados/pies incompletos). Esto precede al id y al snapshot. MASKED/REDACTED protegen incluso áreas mal rotuladas como públicas. FULL_LAB mantiene la captura y la clasificación SECRET hace que un PEM completo entre únicamente en el Shelf de sesión.

Regresión explícita: privateMaterialInGenericAreaCannotEscapeRestrictedProfiles, que fallaba antes, ahora comprueba ambos perfiles restringidos para PEM y marcador, portapapeles real, captura del visor y Shelf vacío, además de sesión privada FULL_LAB. Ejecución con caracterización: 2 tests, 0 fallos, 0 errores.

Solo cambian dos filas normalizadas (generic private PEM candidate en MASKED y REDACTED, exposición true → false). SHA anterior `f9e29640f71909496daf18fd67a1098cf7653940603114e722a102522c84fc18` → `15f9b8043aca2c9bb321205b121e7bb6272b162ce49f2115fd9092216e54237b`. El cambio se debe a la corrección de fuga, no a la extracción.
