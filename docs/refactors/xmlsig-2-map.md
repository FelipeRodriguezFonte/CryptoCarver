# Fase 2 — claves de firma y almacenes de confianza

Base de la fase: `f85ffd2` (fase 1 extraída, tres puertas limpias: G1 467 informes / 2980 pruebas / 0 fallos / 1 omitida; G2 y G3 143 / 593 / 0).

## Qué se mueve a `XmlSignatureKeyMaterialCoordinator`

| Manejador | Campos FXML | Entradas sensibles | Dependencias |
|---|---|---|---|
| `handleBrowseXMLKey` | `xmlSignKeyPathField` | — | selector de fichero del controlador (`chooseFile`) |
| `handleLoadXMLKeys` | `xmlSignSourcePkcs11Radio`, `xmlSignKeyPathField`, `xmlSignKeyPasswordField`, `xmlSignKeyAliasCombo` | contraseña del PKCS#12; sesión PKCS#11 | `XMLSignatureOperations.getKeyAliases`, `Pkcs11SessionManager`, reporter |
| `handleBrowseXMLTrustStore` | `xmlVerifyTrustStorePathField` | — | `chooseFile` |
| `handleBrowseTimestampTrustStore` | `xmlTimestampTrustStoreField` | — | `chooseFile` |
| `handleLoadXMLTrustStoreProfile` | `xmlVerifyTrustStoreProfileCombo`, `xmlVerifyTrustStorePathField`, `xmlVerifyTrustStorePasswordField` | contraseña del truststore (se borra al cargar un perfil) | `AppSettings.getTrustStoreProfiles`, reporter |

## Qué se queda en el controlador

Los cinco puntos de entrada `@FXML` como delegados de una línea, `chooseFile` (lo comparten los Browse de las tres fases), `initialize` (que rellena `xmlVerifyTrustStoreProfileCombo`), `handleXMLSignSourceChanged`, y todo lo de TSA y sello de tiempo hasta la fase 3. `XmlSignatureSigningCoordinator` sigue pidiendo la carga de alias a través del delegado `handleLoadXMLKeys` del controlador.

## Propietario de cada clave en `SpecializedFeedbackHeadlessTest`

| Clave | Antes de la fase | Después | Motivo |
|---|---|---|---|
| `module.xml.feedback.keyStoreRequired` | XMLSignatureController y XmlSignatureSigningCoordinator | XmlSignatureKeyMaterialCoordinator y XmlSignatureSigningCoordinator | el uso del controlador estaba en `handleLoadXMLKeys` (rama PKCS#11) |
| `module.xml.feedback.aliasRequired` | XMLSignatureController y XmlSignatureSigningCoordinator | XmlSignatureKeyMaterialCoordinator y XmlSignatureSigningCoordinator | ídem (keystore sin alias) |

Las claves `module.xml.error.keyStorePassword`, `module.xml.error.inputTitle`, `module.xml.error.generic`, `module.xml.operationFailed`, `module.xml.status.success` y `module.xml.feedback.trustStoreLoaded` se mueven con su lógica y no están en ningún test de presencia en fuente. El coordinador nuevo se añade también a la comprobación de que no se usa `module.xml.error.required`.

## Caracterización

`XMLSignaturePhase2CharacterizationUITest`, con el shell y el FXML reales, un PKCS#12 inventado generado en el test y `AppSettings` aislado. Sin diálogos: los tres Browse solo abren un `FileChooser` y no se ejecutan. La rama PKCS#11 de `handleLoadXMLKeys` necesita un token y queda sin caracterizar; se mueve literal.

Transcripción fijada (SHA-256 `8de3a5f03ef1355e6dc0b62c3adc0b6d326a0a674f40395f17b61295b32aec97`): títulos de error, líneas de estado localizadas en inglés, número de alias, índice seleccionado y efecto sobre los campos del truststore. No se fijan rutas, contraseñas ni textos de excepción.
