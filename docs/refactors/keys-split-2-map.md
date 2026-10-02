# Keys split, fase 2: mapa previo

Base main / 6f86a9b; 6061 líneas en KeysController. Inventario de referencias con rg y rangos actuales delimitados por llaves de método.

## AsymmetricKeyGeneration

| Método | Inicio | Fin |
|---|---:|---:|
| `initializeRSA` | 1588 | 1595 |
| `initializeDSA` | 1600 | 1607 |
| `initializeECDSAFp` | 1612 | 1619 |
| `initializeEd25519` | 1624 | 1627 |
| `handleGenerateRSA` | 2594 | 2669 |
| `handleGenerateDSA` | 2674 | 2749 |
| `handleGenerateECDSAFp` | 2754 | 2820 |
| `handleGenerateEd25519` | 2825 | 2883 |
| `renderGeneratedKeyPair` | 2885 | 2888 |
| `handleGenerateEdDSA` | 2893 | 2895 |
| `updateAsymmetricSummaryCard` | 4708 | 4718 |

| Campo usado | Tipo | Declaración | FXML | Referencia en otros métodos |
|---|---|---:|---|---|
| `mainController` | `StatusReporter` | 172 | False | True |
| `rsaSummaryCard` | `VBox` | 207 | True | True |
| `rsaSummaryAlgoLabel` | `Label` | 208 | True | False |
| `rsaSummaryFingerprintLabel` | `Label` | 209 | True | False |
| `rsaSummaryPubLenLabel` | `Label` | 210 | True | False |
| `rsaSummaryPrivLenLabel` | `Label` | 211 | True | False |
| `rsaSummaryCreatedLabel` | `Label` | 212 | True | False |
| `rsaSummarySavedStatusLabel` | `Label` | 213 | True | False |
| `ecdsaSummaryCard` | `VBox` | 226 | True | True |
| `ecdsaSummaryAlgoLabel` | `Label` | 227 | True | False |
| `ecdsaSummaryFingerprintLabel` | `Label` | 228 | True | False |
| `ecdsaSummaryPubLenLabel` | `Label` | 229 | True | False |
| `ecdsaSummaryPrivLenLabel` | `Label` | 230 | True | False |
| `ecdsaSummaryCreatedLabel` | `Label` | 231 | True | False |
| `ecdsaSummarySavedStatusLabel` | `Label` | 232 | True | False |
| `dsaSummaryCard` | `VBox` | 245 | True | True |
| `dsaSummaryAlgoLabel` | `Label` | 246 | True | False |
| `dsaSummaryFingerprintLabel` | `Label` | 247 | True | False |
| `dsaSummaryPubLenLabel` | `Label` | 248 | True | False |
| `dsaSummaryPrivLenLabel` | `Label` | 249 | True | False |
| `dsaSummaryCreatedLabel` | `Label` | 250 | True | False |
| `dsaSummarySavedStatusLabel` | `Label` | 251 | True | False |
| `eddsaSummaryCard` | `VBox` | 264 | True | True |
| `eddsaSummaryAlgoLabel` | `Label` | 265 | True | False |
| `eddsaSummaryFingerprintLabel` | `Label` | 266 | True | False |
| `eddsaSummaryPubLenLabel` | `Label` | 267 | True | False |
| `eddsaSummaryPrivLenLabel` | `Label` | 268 | True | False |
| `eddsaSummaryCreatedLabel` | `Label` | 269 | True | False |
| `eddsaSummarySavedStatusLabel` | `Label` | 270 | True | False |
| `currentRsaSummary` | `GeneratedAsymmetricKeySummary` | 283 | False | True |
| `currentEcdsaSummary` | `GeneratedAsymmetricKeySummary` | 284 | False | True |
| `currentDsaSummary` | `GeneratedAsymmetricKeySummary` | 285 | False | True |
| `currentEddsaSummary` | `GeneratedAsymmetricKeySummary` | 286 | False | True |
| `rsaKeySizeCombo` | `ComboBox<Integer>` | 449 | True | True |
| `rsaPublicKeyArea` | `TextArea` | 451 | True | True |
| `rsaPrivateKeyArea` | `TextArea` | 453 | True | True |
| `dsaKeySizeCombo` | `ComboBox<String>` | 457 | True | True |
| `dsaPublicKeyArea` | `TextArea` | 459 | True | True |
| `dsaPrivateKeyArea` | `TextArea` | 461 | True | True |
| `ecdsaFpCurveCombo` | `ComboBox<String>` | 465 | False | False |
| `ecdsaFpPublicKeyArea` | `TextArea` | 466 | False | True |
| `ecdsaFpPrivateKeyArea` | `TextArea` | 467 | False | True |
| `ed25519PublicKeyArea` | `TextArea` | 470 | False | True |
| `ed25519PrivateKeyArea` | `TextArea` | 471 | False | True |
| `lastGeneratedKeyPair` | `KeyPair` | 521 | False | True |
| `lastKeyType` | `String` | 522 | False | False |
| `rsaGenerateBtn` | `Button` | 2585 | True | False |
| `dsaGenerateBtn` | `Button` | 2586 | True | False |

Handlers en keys.fxml: `handleGenerateRSA`, `handleGenerateDSA`, `handleGenerateEdDSA`.
Handlers en certificates.fxml: .

## KdfKeyWrap

| Método | Inicio | Fin |
|---|---:|---:|
| `initializeKDF` | 3404 | 3487 |
| `updateKdfFormatHints` | 3489 | 3498 |
| `updateKdfEncodedFieldHint` | 3500 | 3506 |
| `validateKdfEncodedField` | 3508 | 3521 |
| `handleGenerateKdfSalt` | 3524 | 3533 |
| `updateKDFParameters` | 3538 | 3590 |
| `initializeKeyWrap` | 3593 | 3602 |
| `handleKeyWrap` | 3605 | 3642 |
| `handleDeriveKey` | 3647 | 3801 |
| `clearKdfValidation` | 3803 | 3809 |
| `showKdfValidation` | 3811 | 3822 |
| `parseData` | 3827 | 3842 |
| `buildHKDFResult` | 3844 | 3870 |
| `buildContextKdfResult` | 3872 | 3888 |
| `appendKdfField` | 3890 | 3898 |
| `buildPBKDF2Result` | 3900 | 3926 |
| `buildSCryptResult` | 3928 | 3956 |
| `buildArgon2Result` | 3958 | 3986 |

| Campo usado | Tipo | Declaración | FXML | Referencia en otros métodos |
|---|---|---:|---|---|
| `mainController` | `StatusReporter` | 172 | False | True |
| `kdfAlgorithmCombo` | `ComboBox<String>` | 395 | True | True |
| `kdfInputFormatCombo` | `ComboBox<String>` | 397 | True | True |
| `kdfSaltFormatCombo` | `ComboBox<String>` | 399 | True | True |
| `kdfInfoFormatCombo` | `ComboBox<String>` | 401 | True | True |
| `kdfInputField` | `TextField` | 403 | True | True |
| `kdfSaltField` | `TextField` | 405 | True | True |
| `kdfInfoField` | `TextField` | 407 | True | True |
| `kdfIterationsField` | `TextField` | 409 | True | True |
| `kdfOutputLengthField` | `TextField` | 411 | True | True |
| `kdfResultArea` | `TextArea` | 413 | True | True |
| `kdfInputHelpLabel` | `Label` | 415 | True | False |
| `kdfValidationLabel` | `Label` | 417 | True | False |
| `kdfIterationsLabel` | `Label` | 419 | True | False |
| `kdfSaltBox` | `VBox` | 421 | True | False |
| `kdfInfoBox` | `VBox` | 423 | True | False |
| `kdfInputBadgeLabel` | `Label` | 425 | True | True |
| `kdfSaltBadgeLabel` | `Label` | 427 | True | True |
| `kdfInfoBadgeLabel` | `Label` | 429 | True | True |
| `kdfInputBadge` | `com.cryptocarver.ui.component.MaterialFieldBadge` | 432 | False | True |
| `kdfSaltBadge` | `com.cryptocarver.ui.component.MaterialFieldBadge` | 433 | False | True |
| `kdfInfoBadge` | `com.cryptocarver.ui.component.MaterialFieldBadge` | 434 | False | True |
| `keyWrapModeCombo` | `ComboBox<String>` | 437 | True | True |
| `keyWrapUnwrapCheck` | `CheckBox` | 439 | True | True |
| `keyWrapKekField` | `TextField` | 441 | True | True |
| `keyWrapDataField` | `TextField` | 443 | True | True |
| `keyWrapResultArea` | `TextArea` | 445 | True | True |

Handlers en keys.fxml: `handleGenerateKdfSalt`, `handleDeriveKey`.
Handlers en certificates.fxml: .

## Cms

| Método | Inicio | Fin |
|---|---:|---:|
| `initializeCMS` | 4020 | 4058 |
| `handleCadesTimestampOptionChanged` | 4061 | 4073 |
| `handleCMSourceChanged` | 4075 | 4081 |
| `handleLoadCMSKeys` | 4083 | 4097 |
| `handleCMSEncryptSourceChanged` | 4099 | 4105 |
| `handleLoadCMSEncryptKeys` | 4107 | 4121 |
| `handleCMSSign` | 4126 | 4212 |
| `CadesSignResult` (record, no método) | 4214 | 4214 |
| `handleCMSVerify` | 4219 | 4310 |
| `handleCMSVerifyOnline` | 4312 | 4352 |
| `handleUpgradeCadesLt` | 4360 | 4416 |
| `decodeCmsArmored` | 4418 | 4423 |
| `handleCMSEncrypt` | 4425 | 4481 |
| `handleCMSDecrypt` | 4483 | 4538 |
| `parsePrivateKeyFromPEM` | 4541 | 4560 |

| Campo usado | Tipo | Declaración | FXML | Referencia en otros métodos |
|---|---|---:|---|---|
| `mainController` | `StatusReporter` | 172 | False | True |
| `cmsInputArea` | `TextArea` | 3992 | False | False |
| `cmsOutputArea` | `TextArea` | 3993 | False | False |
| `cmsDetachedCheck` | `CheckBox` | 3994 | False | False |
| `cmsCadesBesCheck` | `CheckBox` | 3995 | False | False |
| `cmsCadesTCheck` | `CheckBox` | 3996 | False | False |
| `cmsCadesTsaUrlField` | `TextField` | 3997 | False | False |
| `cmsCadesTsaBox` | `javafx.scene.layout.HBox` | 3998 | False | False |
| `cmsSignCertArea` | `TextArea` | 4000 | False | False |
| `cmsSignKeyArea` | `TextArea` | 4001 | False | False |
| `cmsEncryptCertArea` | `TextArea` | 4002 | False | False |
| `cmsDecryptKeyArea` | `TextArea` | 4003 | False | False |
| `cmsSignSourcePkcs11Radio` | `javafx.scene.control.RadioButton` | 4004 | False | False |
| `cmsSignLocalGrid` | `javafx.scene.layout.GridPane` | 4005 | False | False |
| `cmsSignPkcs11Box` | `javafx.scene.layout.HBox` | 4006 | False | False |
| `cmsSignKeyAliasCombo` | `javafx.scene.control.ComboBox<String>` | 4007 | False | False |
| `cmsVerifyDataArea` | `javafx.scene.control.TextArea` | 4008 | False | False |
| `cmsEncryptSourcePkcs11Radio` | `javafx.scene.control.RadioButton` | 4010 | False | False |
| `cmsEncryptLocalGrid` | `javafx.scene.layout.GridPane` | 4011 | False | False |
| `cmsEncryptPkcs11Box` | `javafx.scene.layout.HBox` | 4012 | False | False |
| `cmsEncryptKeyAliasCombo` | `javafx.scene.control.ComboBox<String>` | 4013 | False | False |
| `cmsSignButton` | `javafx.scene.control.Button` | 4014 | False | False |
| `cmsOnlineRevocationCheck` | `CheckBox` | 4015 | False | False |

Handlers en keys.fxml: .
Handlers en certificates.fxml: `handleCadesTimestampOptionChanged`, `handleCMSourceChanged`, `handleLoadCMSKeys`, `handleCMSEncryptSourceChanged`, `handleLoadCMSEncryptKeys`, `handleCMSSign`, `handleCMSVerify`, `handleUpgradeCadesLt`, `handleCMSEncrypt`, `handleCMSDecrypt`.

## CertificateChain

| Método | Inicio | Fin |
|---|---:|---:|
| `initializeCertificateChainValidation` | 1727 | 1731 |
| `initializeCertificateChain` | 4566 | 4570 |
| `handleValidateCertificateChain` | 4572 | 4660 |

| Campo usado | Tipo | Declaración | FXML | Referencia en otros métodos |
|---|---|---:|---|---|
| `mainController` | `StatusReporter` | 172 | False | True |
| `chainInputArea` | `TextArea` | 516 | False | False |
| `chainCrlInputArea` | `TextArea` | 517 | False | False |
| `chainResultArea` | `TextArea` | 518 | False | False |

Handlers en keys.fxml: .
Handlers en certificates.fxml: `handleValidateCertificateChain`.

## Inicialización, propiedad y dependencias

- Asimétrica: initialize de FXML llama initializeRSA/DSA/ECDSAFp/Ed25519; ecdsaFp* y ed25519* son aliases no FXML de controles FXML. lastGeneratedKeyPair/lastKeyType y current*Summary son estado compartido con resúmenes, clear, export y Shelf (fase 3). RSA y DSA usan OperationExecutor, EC y EdDSA son síncronos. La generación no añade claves asimétricas a Key Lab: Key Lab contiene material simétrico. La pantalla muestra información y PEM; no ofrece selector DER/hex/JWK para estas generaciones.
- KDF/key wrap: initialize de FXML llama initializeKDF y initializeKeyWrap; controles @FXML se conservan. Los tres MaterialFieldBadge son estado exclusivo del bloque. parseData y build*Result son exclusivos y puros. Hay 10 KDF y dos modos AES-KW/KWP. Error de KEK pasa por showError; KDF usa etiqueta local y field-error.
- CMS: 22 controles no FXML en KeysController se reciben por initializeCMS desde CertificatesController.init. Esos controles sí son @FXML en CertificatesController y UiStateSnapshot los visita allí. Usa Pkcs11SessionManager por resolución al ejecutar, almacén de ajustes para TSA, OperationExecutor para firma y revocación online, FileChooser para evidencia LT. parsePrivateKeyFromPEM y decodeCmsArmored son exclusivos. No hay controlador incluido necesario para ejecutar estos handlers.
- Cadena: 3 controles no FXML recibidos desde CertificatesController.init vía initializeCertificateChain; el contrato clásico initializeCertificateChainValidation también los reasigna. Su argumento trustAnchorArea se trata como entrada CRL en el código existente: preservar el contrato, no reinterpretarlo. Construcción de cadenas/CRL, validación y texto son puros; visibilidad, estado y publicación son UI.
- initializeCertificateGen configura controles externos de certificados (fase 3), no generación de pares asimétricos. CertificatesController consume el par mediante getLastGeneratedKeyPair y mantiene sus propias inyecciones.
- Includes de keys.fxml: pkcs11_profiles, icsf_token, icsf_batch, icsf_keywrap, con sus controladores inyectados. Includes de certificates.fxml: pades, asic, cms_inspector, asn1. Ninguno de los cuatro bloques captura o utiliza estos controladores; su cableado queda en la fachada/CertificatesController. Si surgiera una dependencia se resolverá con proveedor.
- ModernMainController.loadSymmetricKeysContent materializa Keys y Certificates y llama keys.init y certificates.init; navegación perezosa resuelve rutas. connectShellServices conecta otros módulos, no existe una clase ShellServices independiente. La paleta usa UiNavigationRegistry; Process Designer llama crypto desde model/process/handlers, no estos handlers.
- Inspector/histórico/sesiones comparten publish y OperationResult; Shelf usa summaries/current snapshot. UiStateSnapshot debe conservar nombres @FXML, SECRET/public classifications y campos portables de los dos módulos. ModuleI18n permanece en la frontera FXML, t y showError/updateStatus se pasan como callbacks. No se añade HsmProvider ni se duplica estado de almacenes/Key Lab.
- Auxiliares únicos a mover: renderGeneratedKeyPair/updateAsymmetricSummaryCard, parseData/build* KDF, decodeCmsArmored/parsePrivateKeyFromPEM. Auxiliares comunes a mantener: showError, updateStatus, t y callbacks de estado asimétrico compartido.

## Fase 3: áreas que permanecerán

| Área | Rango de base actual | Campos y dependencias |
|---|---|---|
| Inicialización, generación simétrica, componentes, validación, material y almacenes/PKCS#11 | 48–2582 | Controles al principio, summaries, AppSettings, HsmProvider/PKCS#11, callbacks externos |
| Certificados, CSR, CRL y validación individual | 2903–3192 y contratos 1636–1850 | Controles externos recibidos, par generado compartido, almacenes |
| Fachadas TR-31/RSA exchange/TR-34 | 3193–3400 | 39 campos FXML; coordinadores de fase 1 |
| Auxiliares globales, resúmenes, export, Shelf y Key Lab | 4664–5714 | current*Summary, KeyMaterial, navegación, secrets y persistencia |
| Variant LMK, Key Block LMK y Atalla | 5715–6061 | 26 campos FXML y sincronización Atalla |

Los campos se declararán todavía en la fachada aunque su ejecución se extraiga; no contar sólo declaraciones del rango como propiedad del bloque. Los controles de CMS/cadena se pueden guardar como vista en su coordinador porque la inyección y snapshot oficiales pertenecen a CertificatesController.
