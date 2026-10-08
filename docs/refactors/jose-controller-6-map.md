# Encargo 78 — fase 6: showSection

Base de fase: `b193a67` (fase 1 y sus tres puertas limpias). JOSEController: 859 líneas. initialize y su coordinación ya caracterizados; no se alteran en esta fase.

## Orden original numerado

1. Si joseContainer existe: managed=true, después visible=true.
2. Si jwtSection existe: managed=false, después visible=false.
3. Si jweSection existe: managed=false, después visible=false.
4. Si jwkSection existe: managed=false, después visible=false.
5. Si jwaSection existe: managed=false, después visible=false.
6. Si inspectorSection existe: managed=false, después visible=false.
7. Si sectionName es null, terminar (container queda expuesto y secciones ocultas).
8. Evaluar en orden, con else-if: startsWith("JWT"), startsWith("JWE"), startsWith("JWK"), startsWith("JWA"), startsWith("Token Inspector"). Para la primera coincidencia y si su panel existe: managed=true, después visible=true.
9. Si no coincide ningún prefijo, terminar con todas las secciones ocultas. Comparación sensible a mayúsculas, sin trim ni conversión; se aceptan los prefijos exactos y cualquier sufijo, incluidos los nombres canónicos de navegación. No convertir a enum/switch de igualdad ni añadir aliases.

## Frontera y propietarios

JoseSectionCoordinator recibe Supplier<View> vivo, View(VBox joseContainer, VBox jwtSection, VBox jweSection, VBox jwkSection, VBox jwaSection, VBox inspectorSection) y Supplier<StatusReporter> según el patrón existente. getter perezoso en controller; showSection público permanece delegado de una línea. Se traslada exactamente su cuerpo, sin simplificar/resetear con bucles ni cambiar el orden de los setters. No registra listeners, ni bindings, ni claves de idioma: la selección utiliza los mismos literales técnicos.

Las seis declaraciones e inyecciones FXML siguen en JOSEController y mantienen nombres/tipos/fx:id. Todos los demás campos FXML y listeners permanecen en sus propietarios de fase 5/coordinadores operacionales. No se trasladan claves en esta fase. Claves literales que siguen en JOSEController:

- `module.jose.clearStatus` → `JOSEController`.
- `module.jose.feedback.copied` → `JOSEController`.
- `module.jose.feedback.copyEmpty` → `JOSEController`.
- `module.jose.feedback.fileRead` → `JOSEController`.
- `module.jose.feedback.importedJwk` → `JOSEController`.
- `module.jose.feedback.importedPem` → `JOSEController`.
- `module.jose.feedback.inputRequired` → `JOSEController`.
- `module.jose.feedback.keyFormat` → `JOSEController`.
- `module.jose.resetStatus` → `JOSEController`.

Las claves de presentación de fase 5 siguen en JoseInitializationCoordinator; las claves de JWT/JWE/JWK siguen en sus coordinadores y los avisos en JoseCoordinatorSupport. SpecializedFeedbackHeadlessTest mantiene todos sus propietarios documentados en mapa 5 (fileRead/copyEmpty/keyFormat controller; statusJweDecrypted JWE; algorithmRequired/keyAdded JWK; statusDetachedGenerated/statusJwtGenerated/statusJwtValidation/statusNested JWT). Ux24HeadlessTest/Ux25HeadlessTest y ModernMainControllerFxmlStaticTest no requieren cambios. Se ejecutarán de forma focal antes de las puertas.

## Caracterización antes de extraer

Nuevo JoseSectionCharacterizationUITest: todas las rutas JOSE de UiNavigationRegistry ordenadas por nombre, cinco prefijos aceptados y sufijos inventados, desconocido, minúsculas, vacío y null. Las rutas que no empiezan por un prefijo reconocido se capturan con el comportamiento actual de desconocido; no se añaden equivalencias. Cada estímulo usa FXML real nuevo y empieza con todos los paneles visibles/managed y el contenedor oculto: verifica su reset completo y conserva la secuencia de eventos managed/visible, además del estado final de cada uno de los seis paneles.

AppSettings/idioma restaurados; Shelf exigido inalterado; historial del reporter aislado en memoria. UiTestLifecycleExtension libera los FXML y ficheros de settings temporales; sin ficheros de entrada, claves reales ni mensajes de proveedor en la transcripción. No se duplica la caracterización operacional existente.

Digest fijado y verde antes de extracción: `781e560222264aed10bc3396167b9bafa86991fe0e679204608024052963c81e`. Trasladado el cuerpo mediante sustitución de tokens identificadores, conservando literales y comentarios sin cambios.

Tras la extracción pasan ambos digests (arranque y secciones), 2 XML / 2 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0. JOSEController: 859 → 810 líneas. JoseSectionCoordinator: 79 líneas.

Contratos fuente/FXML ejecutados aisladamente antes de las puertas: SpecializedFeedbackHeadlessTest, Ux24HeadlessTest, Ux25HeadlessTest, ModernMainControllerFxmlStaticTest; 4 XML / 39 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0. Ningún test existente modificado.

Puertas finales: G1 456 XML / 2931 pruebas / 0 fallos / 0 errores / 1 omitida / exit 0; G2 y G3 134 / 554 / 0 / 0 / 0 / exit 0. Excepción GC no utilizada. Higiene final: 0 estilos FXML inline y 325/325 emojis.
