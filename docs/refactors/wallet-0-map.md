# Encargo 79 — fase 0, mapa previo a extracción

Base: `eb3d7448`. WalletController: 971 líneas. Los 25 manejadores FXML se conservan.

## Entradas y confianza

Secretas: `sdJwtIssuerKeyArea`, `sdJwtHolderKeyArea`, `mdocIssuerKeyArea`, `statusListKeyArea` (claves privadas). `mdocDeviceKeyArea` espera exclusivamente clave **pública**; auditar también el rechazo de material privado. `sdJwtVerifyIssuerKeyArea`, `sdJwtVerifyHolderKeyArea`, `mdocVerifyIssuerKeyArea`, `statusListVerifyKeyArea`, `scaIssuerKeyArea`, `scaHolderKeyArea`, `oid4vpKeyArea` esperan claves públicas; material privado pegado sigue siendo secreto aunque el importador lo rechace. SCA y OID4VP permanecen fuera de extracción. Certificados públicos no son secretos. Claims, disclosures, documentos y tokens son entradas no confiables: pueden contener JWK privado anidado o PEM; no basta buscar el PEM del firmante en bytes internos. Los campos de entrada de claves son editores, no resultados; su contenido escrito por el usuario se distingue de una propagación a salidas.

## Manejadores y campos

| Manejador | Campos FXML leídos/escritos (incluidos helpers específicos) | Destinos |
|---|---|---|
| handleSdJwtIssue | sdJwtAlgoCombo, sdJwtIssuerKeyArea, sdJwtClaimsArea, sdJwtDisclosableArea, sdJwtVctField, sdJwtDecoyField, sdJwtIssueOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleSdJwtPresent | sdJwtAlgoCombo, sdJwtPresentInputArea, sdJwtRevealArea, sdJwtAudienceField, sdJwtNonceField, sdJwtHolderKeyArea, sdJwtPresentOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleSdJwtVerify | sdJwtAlgoCombo, sdJwtVerifyInputArea, sdJwtVerifyIssuerKeyArea, sdJwtVerifyHolderKeyArea, sdJwtVerifyAudienceField, sdJwtVerifyNonceField, sdJwtVerifyOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleSdJwtInspect | sdJwtInspectInputArea, sdJwtInspectOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleMdocIssue | mdocDocTypeField, mdocDigestCombo, mdocIssuerKeyArea, mdocSignerCertArea, mdocDeviceKeyArea, mdocClaimsArea, mdocValidityField, mdocIssueOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleMdocVerify | mdocVerifyInputArea, mdocVerifyIssuerKeyArea, mdocVerifyOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleMdocInspect | mdocVerifyInputArea, mdocVerifyIssuerKeyArea, mdocVerifyOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleStatusListIssue | statusListBitsCombo, statusListStatusesArea, statusListUriField, statusListAlgoCombo, statusListKeyArea, statusListOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleStatusListResolve | statusListAlgoCombo, statusListTokenArea, statusListIndexField, statusListVerifyKeyArea, statusListResolveOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleStatusListDescribe | statusListTokenArea, statusListResolveOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleEidasCertInspect | eidasCertArea, eidasCertOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleTrustedListInspect | trustedListXmlArea, trustedListOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleTrustedEntityListJsonInspect | trustedEntityListJsonArea, trustedEntityListSignerCertArea, trustedEntityListSearchCertArea, trustedListOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleTrustedListVerify | trustedListXmlArea, trustedListOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleTrustedListFind | trustedListCertArea, trustedListOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleCborInspect | cborInputArea, cborViewCombo, cborOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleCborToJson | cborInputArea, cborOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleCborFromJson | cborJsonArea, cborFromJsonOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleScaBuild | scaTypeCombo, scaCredentialIdsField, scaPayloadArea, scaEntryArea, scaTransactionDataArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleScaVerify | sdJwtAlgoCombo, scaPresentationArea, scaTransactionDataArea, scaIssuerKeyArea, scaHolderKeyArea, scaAudienceField, scaNonceField, scaResponseModeField, scaOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleOid4vpInspect | sdJwtAlgoCombo, oid4vpRequestArea, oid4vpKeyArea, oid4vpOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleAdesValidate | adesFileNameField, adesDocumentArea, adesOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleAdesEtsiReport | adesFileNameField, adesDocumentArea, adesOutputArea | Control de salida; estado; publish → inspector, historial, Shelf, expandido |
| handleClear | trustedEntityListJsonArea, trustedEntityListSignerCertArea, trustedEntityListSearchCertArea, sdJwtIssuerKeyArea, sdJwtClaimsArea, sdJwtDisclosableArea, sdJwtVctField, sdJwtDecoyField, sdJwtIssueOutputArea, sdJwtPresentInputArea, sdJwtRevealArea, sdJwtAudienceField, sdJwtNonceField, sdJwtHolderKeyArea, sdJwtPresentOutputArea, sdJwtVerifyInputArea, sdJwtVerifyIssuerKeyArea, sdJwtVerifyHolderKeyArea, sdJwtVerifyAudienceField, sdJwtVerifyNonceField, sdJwtVerifyOutputArea, sdJwtInspectInputArea, sdJwtInspectOutputArea, mdocIssuerKeyArea, mdocSignerCertArea, mdocDeviceKeyArea, mdocClaimsArea, mdocValidityField, mdocIssueOutputArea, mdocVerifyInputArea, mdocVerifyIssuerKeyArea, mdocVerifyOutputArea, statusListStatusesArea, statusListUriField, statusListKeyArea, statusListOutputArea, statusListTokenArea, statusListIndexField, statusListVerifyKeyArea, statusListResolveOutputArea, eidasCertArea, eidasCertOutputArea, trustedListXmlArea, trustedListCertArea, trustedListOutputArea, cborInputArea, cborOutputArea, cborJsonArea, cborFromJsonOutputArea, scaPayloadArea, scaEntryArea, scaPresentationArea, scaTransactionDataArea, scaIssuerKeyArea, scaHolderKeyArea, scaAudienceField, scaNonceField, scaOutputArea, oid4vpRequestArea, oid4vpKeyArea, oid4vpOutputArea, adesDocumentArea, adesOutputArea | Limpia controles; estado |
| handleLoadExample | sdJwtClaimsArea, sdJwtDisclosableArea, sdJwtVctField, mdocClaimsArea, statusListStatusesArea, statusListUriField, statusListIndexField, cborInputArea, cborJsonArea | Ejemplos en entradas; estado |

## Rutas a superficies

Todos los éxitos de operaciones (salvo Clear y Load Example) hacen `outputArea.setText` sin política local y `publish`: OperationResult con output UTF-8 y detalles por defecto. `updateStatus` recibe una clave traducida fija; `publish` termina con `module.wallet.status.done`. `showValidation` usa InlineErrorPresenter.redactSecrets y StatusReporter.showError. `logFailure` incluye excepción: log persistible, revisar por separado si hay error.

El shell conecta StatusReporter al módulo: publica en inspector y HistoryManager, resuelve el resultado para Copy/Expand/Shelf mediante ResultCaptureCoordinator y ResultViewerCoordinator. La vista expandida tiene su propio TextArea. Shelf persiste las capturas explícitas; HistoryManager persiste la operación. UiStateSnapshot captura sesiones/recetas con política global. WalletController no escribe ficheros directamente; exportaciones/copias/sesiones y los almacenes del shell son rutas indirectas. La prueba debe inspeccionar controles y datos efectivamente persistidos, no declarar fuga por OperationResult interno.

Status List resolve recibe token local y usa su sub: no descarga URLs. URI `.invalid` es etiqueta del token, no destino de red.

## Contratos de fuente localizados

Revisados SpecializedFeedbackHeadlessTest, SpecializedI18nTest, Ux19SpecializedHeadlessTest, ModernMainControllerFxmlStaticTest, WalletControllerDefaultsTest y SessionTrailUITest. SpecializedFeedbackHeadlessTest no asigna claves Wallet actualmente. Los contratos FXML y recetas conservan WalletController y los fx:id. Antes de cada extracción se asignará cada literal module.wallet.* a su propietario en wallet-N-map.md; aún no hay traslado ni cambio de propietario. No se modifica ningún test existente en fase 0.

## Alcance y decisión

Auditar primero los diez manejadores SD-JWT/mdoc/Status List en MASKED, REDACTED y FULL_LAB con claves de laboratorio; probar también material privado anidado en credenciales inspeccionadas/verificadas. Una fuga confirmada en superficie visible/persistida detiene las fases 1–3. No se fija digest de una auditoría fallida. Advertencia de traducción preexistente “Wallet / eIDAS”: registrar, no corregir.

## Continuación autorizada, fase 0

El detector JOSE se mueve sin cambios de lógica a PrivateKeyMaterialDetector (utilidad package-private). JoseInspectorCoordinator delega. WalletController incorpora sdJwtReportForDisplay para Verify/Inspect: comprueba informe/token y estructuras decodificadas, conserva FULL_LAB y publica el mismo aviso que muestra el control bajo perfiles restringidos. El nuevo literal `module.wallet.privateJwkHidden` pertenece a WalletController; las versiones EN/ES y fallback están en los bundles. No se mueve ninguna otra clave ni se cambia un fx:id.

La validación original sigue fallando por la receta `WalletController.sdJwtClaimsArea`, que llega al historial independientemente del output. La auditoría ampliada descubre fugas en `mdocVerifyOutputArea` y `statusListResolveOutputArea`; ambos contienen elementos privados incluidos accidentalmente en la estructura de entrada. Véanse wallet-0-correction-validation.md y wallet-0-additional-characterization-failures.md. La segunda parada impide iniciar mapas de fases 1–3.

Precisión del mapa inicial: handleTrustedListFind también lee trustedListXmlArea mediante trustedListXml(); no cambia su propietario ni se toca ese manejador.
