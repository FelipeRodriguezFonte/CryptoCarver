# Encargo 78 — fase 5: initialize

Base: `d685778`; JOSEController: 1055 líneas. Se leyeron los mapas 1–4 y los informes 71/72 antes de comenzar. Se conserva el patrón de vista viva de esos coordinadores, sin pasar JOSEController al coordinador nuevo.

## Orden original de ejecución

1. Enlazar `ModuleI18n` con `joseContainer`/catálogo JOSE.
2. Texto `jwtAcceptNoneCheck`.
3. Retener y registrar el listener débil de locale. Al dispararse: presentación JWK; etiquetas de capacidades; inicialización de curvas JWK; filtros detached; celdas de opciones inseguras en siete combos; avisos; limpieza del estado detached solo si está en blanco. Se conserva la reinstalación de listeners/filtros al cambiar idioma.
4. Poblar y seleccionar primero JWT algoritmo 1, después algoritmo 2, solo si listas vacías.
5. JWE algoritmo de clave; algoritmo de contenido (`A256GCM`).
6. Seis formatos de secreto: JWE cifrado, JWE descifrado, JWT firma, JWT validación, detached, nested. Orden del enum y `selectFirst` intactos.
7. PBES2 por defecto solo con campo vacío.
8. Algoritmo detached.
9. Serialización JWE; después JWS y detached.
10. Nested firma (`HS256`), clave, contenido (`A256GCM`).
11. Tipo JWK: poblar, registrar listener de selectedItem, seleccionar primero. Listener ignora null y refresca etiqueta, prompt y botones de conversión.
12. Uso JWK (`sig`); refrescar etiquetas de capacidades.
13. Algoritmos de rotación JWKS, orden literal actual y primer elemento.
14. `JoseJwkCoordinator.initializeCurveControls`: curva por defecto; disabled según tipo; listeners tipo→disabled, curva→tipo/uso/rotación y rotación→tipo/uso/curva (con protección de sincronización). Permanece en su propietario actual.
15. `JoseJwtCoordinator.initializeDetachedHeaderControls`: filtros de teclado y menú contextual según perfil. Permanece en su propietario actual.
16. Para cada combo, instalar celdas y luego listener de value, en este orden: JWT1, JWT2, detached, nested firma, JWE clave, nested clave, JWKS rotación. Cada listener refresca avisos.
17. Listeners de texto: JWT key1, JWT key2, detached signing key, nested signing key; refrescar avisos iniciales.
18. Bindings de ingesta, cada uno registra listener y evalúa estado inicial: JWT key, JWT validate token, JWT validate key, JWE public key, JWE input, JWE private key. Todos pasan statusLabel null; su estímulo conserva el texto y no publica estado/historial.
19. Tabla JWA, solo si vacía: factories de columnas y lista actual. Conservada en controller, intercalada en su punto original mediante llamada con nombre.
20. Combo de plantillas, solo si vacío: lista actual y handler Action. Mantiene las cuatro ramas, tiempo actual y UUID originales.

## Frontera

`JoseInitializationCoordinator`: record View de campos vivos, Supplier<View>, Supplier<StatusReporter>, referencias a los coordinadores JWK/JWT existentes (sin capturar controller). Conserva fuertemente ModuleI18n.Binding y Consumer de locale. Recibe el reporter vivo aunque este cableado no lo necesita directamente. Mueve población de combos, defaults, listeners, bindings y los helpers de presentación JWK/capacidades/avisos/celdas. Los algoritmos y advertencias siguen calculados por JoseCoordinatorSupport/JweComposer/JoseKeyMaterial. FXML/API, handlers, tabla JWA, reporter y coordinadores operacionales permanecen controller. initialize será una secuencia de llamadas nombradas en este orden; delegados de una línea para las llamadas a inicialización JWK/detached. Los tres wrappers privados ajenos al arranque (jwtTokenSecurityWarning/addSecurityWarning/metadataWarning) se conservan; no se aprovecha la extracción para retirarlos.

## Propietarios de claves

Cada clave siguiente pasa de JOSEController a JoseInitializationCoordinator porque pertenece a los helpers de etiquetas/presentación/celdas del arranque (sin cambiar traducciones):

- `module.jose.acceptNone` → `JoseInitializationCoordinator`.
- `module.jose.inputPem` → `JoseInitializationCoordinator`.
- `module.jose.inputPemPrompt` → `JoseInitializationCoordinator`.
- `module.jose.inputSecret` → `JoseInitializationCoordinator`.
- `module.jose.inputSecretPrompt` → `JoseInitializationCoordinator`.
- `module.jose.inspectMetadata` → `JoseInitializationCoordinator`.
- `module.jose.jwkKeyOps` → `JoseInitializationCoordinator`.
- `module.jose.jwkKeyOpsPrompt` → `JoseInitializationCoordinator`.
- `module.jose.jwkToPem` → `JoseInitializationCoordinator`.
- `module.jose.jwkToSecret` → `JoseInitializationCoordinator`.
- `module.jose.jwkUse` → `JoseInitializationCoordinator`.
- `module.jose.jwtAccessToken` → `JoseInitializationCoordinator`.
- `module.jose.jwtAllowedAlgorithms` → `JoseInitializationCoordinator`.
- `module.jose.jwtAuthorizationCode` → `JoseInitializationCoordinator`.
- `module.jose.jwtExpectedContentType` → `JoseInitializationCoordinator`.
- `module.jose.jwtExpectedJkt` → `JoseInitializationCoordinator`.
- `module.jose.jwtExpectedNonce` → `JoseInitializationCoordinator`.
- `module.jose.jwtExpectedType` → `JoseInitializationCoordinator`.
- `module.jose.jwtExpectedX5t` → `JoseInitializationCoordinator`.
- `module.jose.jwtIgnoreCrit` → `JoseInitializationCoordinator`.
- `module.jose.jwtRfc9068` → `JoseInitializationCoordinator`.
- `module.jose.jwtUnderstoodCrit` → `JoseInitializationCoordinator`.
- `module.jose.pemToJwk` → `JoseInitializationCoordinator`.
- `module.jose.protectedHeaderAdditional` → `JoseInitializationCoordinator`.
- `module.jose.secretToJwk` → `JoseInitializationCoordinator`.
- `module.jose.trustHeaderKey` → `JoseInitializationCoordinator`.
- `module.jose.unsafeMarker` → `JoseInitializationCoordinator`.

`JoseJwkCoordinator`: conserva `module.jose.okpCurve`, feedback de JWK y claves de sus operaciones. `JoseJwtCoordinator`: conserva `module.jose.x5cAnchors`, `module.jose.x5cDate`, `module.jose.protectedHeaderAdditional` compartida y `module.jose.detachedHeaderCaptureHidden`, feedback de JWT. `JoseCoordinatorSupport`: conserva `module.jose.warning.none`, `module.jose.warning.shortHmac`, `module.jose.warning.rsa15`, `module.jose.warning.oaepSha1` y `module.jose.jwkMetadataWarning`; no se trasladan. `ModuleTextCatalog` conserva su catálogo y ModuleI18n sus bindings.

Claves verificadas por `SpecializedFeedbackHeadlessTest`, sin reasignación: `module.jose.feedback.fileRead`, `copyEmpty`, `keyFormat` permanecen JOSEController; `statusJweDecrypted` permanece JoseJweCoordinator; `algorithmRequired` y `keyAdded` permanecen JoseJwkCoordinator; `statusDetachedGenerated`, `statusJwtGenerated`, `statusJwtValidation` y `statusNested` permanecen JoseJwtCoordinator. Ninguna de las claves que mueve esta fase es exigida por clase en ese test. Ux24HeadlessTest/Ux25HeadlessTest comprueban traducciones y fx:id, y ModernMainControllerFxmlStaticTest comprueba el shell/FXML: no se modifican. Búsqueda de Files.readString/readAllBytes y referencias JOSE en todos los tests antes de extraer.

## Caracterización existente y suplemento

JoseCapabilitiesCharacterizationUITest cubre algoritmos, avisos y EN/ES; JoseOkpCharacterizationUITest cubre curvas; JoseHistoryAliasUITest cubre recetas de historial; JWT/JWE/JWK/InspectorCharacterizationUITest cubren operaciones y privacidad. Se conservan intactos. El test nuevo JoseInitializationCharacterizationUITest añade inventario inicial completo de controles nombrados y anónimos del árbol lógico FXML; visible, managed, disabled, texto/prompt, selección/índice/opciones, checkboxes y filas de tabla. Estímulos independientes con FXML nuevo: siete combos de avisos, nueve campos de texto (incluye seis bindings y cuatro avisos con JWT key compartido), tipo OCT/OKP, curva, rotación, cuatro plantillas, locale ES y dos filtros detached bajo MASKED/REDACTED.

El fixture existente restaura idioma/perfil, exige Shelf inalterado y usa historial en memoria; los estímulos no escriben historial real. UiTestLifecycleExtension restaura AppSettings y libera FXML. No hay ficheros propios de entrada ni claves reales.

Claves literales que permanecen en JOSEController (handlers/validación/copia/carga, enumeradas una a una):

- `module.jose.clearStatus` → `JOSEController`.
- `module.jose.feedback.copied` → `JOSEController`.
- `module.jose.feedback.copyEmpty` → `JOSEController`.
- `module.jose.feedback.fileRead` → `JOSEController`.
- `module.jose.feedback.importedJwk` → `JOSEController`.
- `module.jose.feedback.importedPem` → `JOSEController`.
- `module.jose.feedback.inputRequired` → `JOSEController`.
- `module.jose.feedback.keyFormat` → `JOSEController`.
- `module.jose.resetStatus` → `JOSEController`.

Digest definitivo antes de extraer: `61e52224fbedb92086d81b19dbd2f3b2aad54df991d7e9dcb8c637e5b011b6df`.

Campos FXML exactos de View (propiedad de la declaración e inyección permanece JOSEController; acceso de arranque/presentación pasa al coordinador):

`joseContainer`, `jwtAcceptNoneCheck`, `jwtAlgoCombo`, `jwtAlgo2Combo`, `jweKeyAlgoCombo`, `jweContentAlgoCombo`, `jweKeyFormatCombo`, `jweDecryptKeyFormatCombo`, `jwtSecretFormatCombo`, `jwtValidateSecretFormatCombo`, `detachedSecretFormatCombo`, `nestedSecretFormatCombo`, `detachedAlgoCombo`, `jweSerializationCombo`, `jwsSerializationCombo`, `detachedSerializationCombo`, `nestedSignAlgoCombo`, `nestedKeyAlgoCombo`, `nestedContentAlgoCombo`, `jwkKeyTypeCombo`, `jwkUseCombo`, `jwksRotateAlgoCombo`, `jwtTemplateCombo`, `jwePbes2IterField`, `jwtPayloadArea`, `jwkInputArea`, `jwkInputLabel`, `jwkCurveLabel`, `jwkCurveCombo`, `pemToJwkBtn`, `jwkToPemBtn`, `jwkUseLabel`, `jwkKeyOpsLabel`, `jwkInspectMetadataBtn`, `jwkKeyOpsField`, `jwtAllowedAlgorithmsLabel`, `jwtExpectedTypeLabel`, `jwtExpectedContentTypeLabel`, `jwtExpectedNonceLabel`, `jwtAccessTokenLabel`, `jwtAuthorizationCodeLabel`, `jwtExpectedJktLabel`, `jwtExpectedX5tLabel`, `jwtUnderstoodCritLabel`, `jwtProtectedHeaderLabel`, `jwtRfc9068Check`, `jwtIgnoreCritCheck`, `jwtTrustHeaderKeyCheck`, `jwtKeyArea`, `jwtKeyArea2`, `detachedSigningKeyArea`, `nestedSigningKeyArea`, `jwtSecurityWarningLabel`, `detachedSecurityWarningLabel`, `nestedSecurityWarningLabel`, `jweSecurityWarningLabel`, `detachedStatusLabel`, `jwtValidateTokenArea`, `jwtValidateKeyArea`, `jwePublicKeyArea`, `jweInputArea`, `jwePrivateKeyArea`

Los getters JWK/JWT se evalúan al construir el coordinador; sus constructores solo asignan proveedores/referencias, sin acceder a controles ni registrar eventos. Todos los efectos sobre la vista siguen en los puntos numerados. La tabla y sus campos `jwaTable`, `jwaNameCol`, `jwaTypeCol`, `jwaDescCol` permanecen controller.

Verificación tras extracción: digest definitivo y JoseCapabilitiesCharacterizationUITest/JoseOkpCharacterizationUITest/contratos fuente-FXML verdes, sin cambiar tests existentes.

Puertas finales: G1 455 XML / 2930 pruebas / 0 fallos / 0 errores / 1 omitida / exit 0; G2 y G3 133 / 553 / 0 / 0 / 0 / exit 0. Excepción GC no utilizada. JOSEController: 1055 → 859 líneas; coordinador nuevo: 312.
