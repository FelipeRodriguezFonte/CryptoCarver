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

## Segunda continuación: B y puerta previa a A

Autorizado UiStateSnapshot únicamente en la condición de captura HISTORY_RECIPE: añade sensibilidad por contenido de String con PrivateKeyMaterialDetector cuando hay redacción activa. La restauración y los demás modos quedan intactos. Se añade una envoltura de protección contra errores de análisis en el detector, sin cambiar sus reglas, y tests de entradas arbitrarias/largas. UiStateSnapshotTest se amplía por petición expresa, con notesArea neutro, sin tocar aserciones anteriores.

G1 inmediatamente posterior a B: 459 XML / 2957 pruebas / 8 fallos / 0 errores / 1 omitida / exit 1. La reproducción SD-JWT pasa completa; los ocho fallos son la reproducción ampliada pendiente de A. Se detiene la secuencia por puerta no limpia antes de A; detalles en wallet-0-history-g1-failures.md. No se trasladan métodos ni propietarios de claves Wallet.

## Encargo 80 — manejadores restantes

Base: `e4dc38e`. Los trece manejadores que seguían en `WalletController` y sus superficies. Ninguno recibe una clave privada como entrada prevista: las claves que piden SCA Verify y OpenID4VP son públicas (verificación). El riesgo es el mismo que en el encargo 79: una estructura pegada que lleva material privado dentro.

| Manejador | Entrada | Resultado (`fx:id`) | ¿Devuelve la estructura pegada? |
|---|---|---|---|
| `handleEidasCertInspect` | certificado PEM | `eidasCertOutputArea` | no (campos del certificado) |
| `handleTrustedListInspect`, `handleTrustedListVerify`, `handleTrustedListFind` | XML TS 119 612, certificado | `trustedListOutputArea` | no (campos de la lista) |
| `handleTrustedEntityListJsonInspect` | JSON TS 119 602 o JAdES compacto | `trustedListOutputArea` | **sí**: los `ServiceInformationExtensions` se copian literales |
| `handleCborInspect`, `handleCborToJson` | CBOR en hexadecimal | `cborOutputArea` | **sí**: todo el contenido |
| `handleCborFromJson` | JSON | `cborFromJsonOutputArea` | **sí**, codificado (las cadenas viajan en claro dentro del hexadecimal) |
| `handleScaBuild` | JSON del pago | `scaEntryArea`, y copia en `scaTransactionDataArea` | sí, en base64url |
| `handleScaVerify` | presentación SD-JWT, `transaction_data`, claves públicas | `scaOutputArea` | parcial (hallazgos) |
| `handleOid4vpInspect` | petición JWT, clave pública | `oid4vpOutputArea` | parcial (campos de la petición) |
| `handleAdesValidate`, `handleAdesEtsiReport` | documento firmado en base64 o hexadecimal | `adesOutputArea` | no (informe de validación) |

Todos publican por `publish(...)`: resultado del shell, historial, Shelf y visor expandido reciben el mismo texto que el área de resultado. Ninguno escribe ficheros. Las entradas llegan a las recetas del historial por `UiStateSnapshot`, que ya redacta por contenido las claves privadas (encargo 79) y sanea las URL con credenciales (encargo 81).

### Regla aplicada

`WalletPrivateMaterialPolicy.forDisplay(report, fuentes...)`: fuera de FULL_LAB, si `PrivateKeyMaterialDetector` encuentra material privado en el informe o en alguna fuente, el área de resultado y lo publicado llevan `module.wallet.privateJwkHidden`. Se aplica en los trece manejadores, no solo en los que la auditoría encontró expuestos. El detector no se amplía: para CBOR se le pasa además la representación JSON del propio CBOR (`CborInspector.toJson`), y para una presentación SD-JWT, su cabecera, payload, disclosures y claims de key binding ya decodificados.

Límite conocido, no cubierto: una clave privada en CBOR con etiquetas enteras (COSE_Key, parámetro `-4`) no tiene la forma `kty` + `d` que reconoce el detector y se sigue mostrando. Corregirlo exige ampliar el detector.
