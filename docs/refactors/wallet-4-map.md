# Fase 4 — eIDAS y Trusted Lists (encargo 80)

Base de la fase: `1b2ac58` (fase 0 corregida).

## Qué se mueve a `WalletTrustCoordinator`

| Manejador | Campos FXML | Fachada |
|---|---|---|
| `handleEidasCertInspect` | `eidasCertArea`, `eidasCertOutputArea` | `EidasCertificateInspector.describe` |
| `handleTrustedListInspect` | `trustedListXmlArea`, `trustedListOutputArea` | `TrustedListInspector.describe` |
| `handleTrustedListVerify` | ídem | `TrustedListInspector.verifySignature` |
| `handleTrustedListFind` | más `trustedListCertArea` | `TrustedListInspector.parse`, `findCertificate` |
| `handleTrustedEntityListJsonInspect` | `trustedEntityListJsonArea`, `trustedEntityListSignerCertArea`, `trustedEntityListSearchCertArea`, `trustedListOutputArea` | `TrustedEntityListJsonInspector.describe` |

Con ellos se mueve el auxiliar `trustedListXml`. La llamada a `WalletPrivateMaterialPolicy.forDisplay` de cada manejador viaja con él, sin cambios.

## Auxiliares compartidos

Los tres coordinadores de este encargo extienden `WalletCoordinatorBase`, que reúne una sola copia de `t`, `textOf`, `valueOf`, `isBlank`, `blankToNull`, `lines`, `parseCertificate`, `publish`, `fail`, `showValidation` y `updateStatus`. Son copias literales de los métodos del controlador; el registro de errores conserva la categoría de log `WalletController`. Los tres coordinadores del encargo 79 no se tocan y mantienen sus copias propias.

## Qué se queda en el controlador

Los cinco puntos de entrada `@FXML` como delegados de una línea, los campos FXML y, hasta terminar el encargo, los auxiliares que aún usan CBOR, SCA, `handleClear` y `handleLoadExample`.

## Claves de idioma

Se mueven con su lógica `module.wallet.certificateRequired`, `trustedListRequired`, `trustedEntityListRequired`, `notInTrustedList`, `status.inspected` y `status.verified`. Ningún test de presencia en fuente (`SpecializedFeedbackHeadlessTest` y similares) asigna claves `module.wallet.*` a `WalletController`, así que no hay propietarios que reasignar. Comprobado con una búsqueda de `WalletController` en `src/test`.

## Caracterización

`WalletTrustCharacterizationUITest`, SHA-256 `a8e749cfa04a0372d6b8af2762cddac2c56bc7b805a30fb7da787937e049fc07`, en inglés y español y en los tres perfiles: validaciones, operación correcta y entrada no válida de cada manejador, con un certificado autofirmado inventado, una Trusted List XML sin firmar y una lista TS 119 602 local. La transcripción fija campo y texto de cada validación, operación publicada, estado y detalles públicos; el texto de los informes se compara con una llamada independiente a la fachada y no entra en el digest (lleva fechas y huellas del certificado). No se fijan textos de excepción.

Sin cubrir: Trusted List y Trusted Entity List firmadas, y la búsqueda de certificado en la lista JSON.
