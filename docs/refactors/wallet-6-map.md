# Fase 6 — SCA, OpenID4VP y AdES (encargo 80)

Base de la fase: el commit de extracción de CBOR.

## Qué se mueve a `WalletScaCoordinator`

| Manejador | Campos FXML | Fachada |
|---|---|---|
| `handleScaBuild` | `scaTypeCombo`, `scaCredentialIdsField`, `scaPayloadArea`, `scaEntryArea`, `scaTransactionDataArea` | `Ts12ScaOperations.encodeTransactionData` |
| `handleScaVerify` | `scaPresentationArea`, `scaTransactionDataArea`, `scaIssuerKeyArea`, `scaHolderKeyArea`, `scaAudienceField`, `scaNonceField`, `scaResponseModeField`, `scaOutputArea`, `sdJwtAlgoCombo` | `Ts12ScaOperations.verify`, `describe`, `JOSEService.createVerifier` |
| `handleOid4vpInspect` | `oid4vpRequestArea`, `oid4vpKeyArea`, `oid4vpOutputArea`, `sdJwtAlgoCombo` | `OpenId4VpInspector.describe` |
| `handleAdesValidate`, `handleAdesEtsiReport` | `adesFileNameField`, `adesDocumentArea`, `adesOutputArea` | `AdesValidationOperations.validate`, `describe` |

Con ellos se mueven `runAdes` y `decodeDocument`. `sdJwtAlgoCombo` pertenece al panel SD-JWT, pero SCA Verify y OpenID4VP leen de él el algoritmo; el coordinador lo recibe en su `View` sin cambiar ese acoplamiento.

Extiende `WalletCoordinatorBase`. La política de material privado viaja con cada manejador; en SCA Build, una entrada oculta no se copia al panel de verificación.

## Qué se queda en el controlador

Los cinco puntos de entrada `@FXML` como delegados; `initialize` (que rellena `scaTypeCombo`), `showSection`, `handleClear` y `handleLoadExample`, con los auxiliares que estos cuatro siguen usando: `fill`, `hide`, `show`, `textOf`, `setText`, `isBlank`, `clear`, `updateStatus` y `t`. Los auxiliares que ya no usa nadie en el controlador (`lines`, `parseCertificate`, `valueOf`, `blankToNull`, `publish`, `fail`, `showValidation`, `logFailure`) y sus importaciones se eliminan.

## Claves de idioma

`module.wallet.claimsRequired`, `sdJwtRequired`, `keyRequired`, `requestRequired`, `documentRequired`, `status.issued`, `status.verified` y `status.inspected` se mueven con su lógica. Sin propietarios que reasignar.

## Caracterización

`WalletScaCharacterizationUITest`, SHA-256 `6922b34954cd3c9a884f92653ddd413ef4f60d5abaa4d0739e690ca626403caf`, en inglés y español y en los tres perfiles:

- SCA Build: validación, entrada correcta (igual a la de la fachada), copia al panel de verificación solo si está vacío, JSON no válido.
- SCA Verify: validaciones, una presentación SD-JWT con key binding generada en el test que sostiene el enlace dinámico, la misma presentación contra otro pago (no lo sostiene) y una presentación no válida.
- OpenID4VP: validación, petición firmada con y sin clave de verificación.
- AdES: validación, y un XML firmado con XAdES en el propio test, validado e informado en formato ETSI.

La transcripción fija campo y texto de cada validación, operación publicada, estado y detalles públicos (`Dynamic link`, `Signatures`). Los informes llevan fechas, identificadores y huellas y no entran en el digest.
