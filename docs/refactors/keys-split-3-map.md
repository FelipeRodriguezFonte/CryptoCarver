# Mapa de Keys, fase 3

Base: `a548f8b`, 4727 líneas; referencias al controlador antes de la extracción. Inventario obtenido con `rg` y análisis de declaraciones.

## Decisión de estado compartido

`KeysWorkspaceState` contendrá `lastGeneratedKeyPair`, `lastKeyType`, los cinco `current*Summary` y el material simétrico generado. Generación escribe; resumen/Shelf y guardado en Key Lab leen el material y los resúmenes. El getter del último par y el proveedor conectado a CipherController leen el par. Generate Certificate y CSR generan sus propios pares y no sustituyen el último par de generación asimétrica. Mantendrá las mismas reglas de invalidación y borrado de bytes. No contendrá nodos ni proveedores HSM.

Los controles siguen en la fachada y se ofrecen mediante records de vista obtenidos por proveedores; los cuatro controladores incluidos se resuelven al usar el proveedor. `AppSettings` y el proveedor HSM mantienen sus propietarios actuales.

## Key blocks de pagos

### Métodos

- `handleThalesEncrypt`: línea 4399 (handler en keys.fxml)
- `handleThalesDecrypt`: línea 4411 (handler en keys.fxml)
- `handleThalesDescribe`: línea 4424 (handler en keys.fxml)
- `handleThalesLookup`: línea 4433 (handler en keys.fxml)
- `handleThalesLoadExample`: línea 4447 (handler en keys.fxml)
- `initializeThalesControls`: línea 4466
- `thalesLmk`: línea 4474
- `thalesKeyTypeCode`: línea 4478
- `thalesScheme`: línea 4483
- `thalesText`: línea 4489
- `runThales`: línea 4497
- `handleKeyBlockInspect`: línea 4530 (handler en keys.fxml)
- `handleKeyBlockUnwrap`: línea 4554 (handler en keys.fxml)
- `handleKeyBlockExample`: línea 4573 (handler en keys.fxml)
- `atallaCombos`: línea 4602
- `initializeAtalla`: línea 4607
- `atallaHeaderFromCombos`: línea 4628
- `atallaCombosFromHeader`: línea 4648
- `atallaExplainHeader`: línea 4672
- `handleAtallaGenerate`: línea 4683 (handler en keys.fxml)
- `handleAtallaUnwrap`: línea 4696 (handler en keys.fxml)
- `handleAtallaExample`: línea 4709 (handler en keys.fxml)
- `atallaPublish`: línea 4718

### Campos utilizados

- `mainController`: StatusReporter, línea 287, no @FXML
- `thalesLmkField`: TextField, línea 4384, @FXML
- `thalesKeyTypeField`: TextField, línea 4385, @FXML
- `thalesSchemeCombo`: ComboBox<String>, línea 4386, @FXML
- `thalesClearKeyField`: TextField, línea 4387, @FXML
- `thalesCryptogramField`: TextField, línea 4388, @FXML
- `thalesCheckValueField`: TextField, línea 4389, @FXML
- `thalesComponentCheck`: CheckBox, línea 4390, @FXML
- `thalesResultArea`: TextArea, línea 4391, @FXML
- `MANUAL_LMK_28_29`: String, línea 4394, no @FXML
- `MANUAL_MK_SMI`: String, línea 4395, no @FXML
- `MANUAL_CRYPTOGRAM`: String, línea 4396, no @FXML
- `MANUAL_CHECK_VALUE`: String, línea 4397, no @FXML
- `keyBlockInputArea`: TextArea, línea 4518, @FXML
- `keyBlockResultArea`: TextArea, línea 4519, @FXML
- `keyBlockLmkField`: TextField, línea 4520, @FXML
- `KEY_BLOCK_TEST_LMK`: String, línea 4525, no @FXML
- vector público de key block: String, línea 4527, no @FXML
- `atallaTemplateCombo`: ComboBox<AtallaAkbHeader.Template>, línea 4584, @FXML
- `atalla0Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4585, @FXML
- `atalla1Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4586, @FXML
- `atalla2Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4587, @FXML
- `atalla3Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4588, @FXML
- `atalla4Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4589, @FXML
- `atalla5Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4590, @FXML
- `atalla6Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4591, @FXML
- `atalla7Combo`: ComboBox<AtallaAkbHeader.Option>, línea 4592, @FXML
- `atallaHeaderField`: TextField, línea 4593, @FXML
- `atallaMeaningArea`: TextArea, línea 4594, @FXML
- `atallaMfkField`: TextField, línea 4595, @FXML
- `atallaKeyField`: TextField, línea 4596, @FXML
- `atallaBlockArea`: TextArea, línea 4597, @FXML
- `atallaResultArea`: TextArea, línea 4598, @FXML
- `atallaSyncing`: boolean, línea 4600, no @FXML
## Key Lab

### Métodos

- `handleSaveGeneratedKeyToLab`: línea 2059 (handler en keys.fxml)
- `initializeKeyLab`: línea 3765
- `updateVisibilityControls`: línea 3841
- `updateNewKeySizes`: línea 3861
- `refreshKeyLabTable`: línea 3879
- `showKeyLabDetails`: línea 3907
- `updateKeyLabUseActions`: línea 3942
- `setKeyLabUsageControls`: línea 3961
- `setUsageControl`: línea 3970
- `keyLabActionReason`: línea 3977
- `clearKeyLabDetails`: línea 3986
- `handleUseKeyLabInCipher`: línea 4005 (handler en keys.fxml)
- `handleUseKeyLabInMac`: línea 4010 (handler en keys.fxml)
- `useSelectedKeyLabEntry`: línea 4015
- `handleKeyLabGenerate`: línea 4047 (handler en keys.fxml)
- `handleKeyLabImport`: línea 4110 (handler en keys.fxml)
- `handleKeyLabReveal`: línea 4162 (handler en keys.fxml)
- `handleKeyLabCopyId`: línea 4184 (handler en keys.fxml)
- `handleKeyLabSaveMetadata`: línea 4197 (handler en keys.fxml)
- `handleKeyLabArchive`: línea 4244 (handler en keys.fxml)
- `handleKeyLabDelete`: línea 4270 (handler en keys.fxml)
- `handleImportKeyLabMetadata`: línea 4290 (handler en keys.fxml)
- `handleExportKeyLabMetadata`: línea 4310 (handler en keys.fxml)
- `selectKeyInKeyLab`: línea 4329

### Campos utilizados

- `dialogService`: DialogService, línea 34, no @FXML
- `keyLabPane`: TitledPane, línea 256, @FXML
- `keyLabSearchField`: TextField, línea 257, @FXML
- `keyLabStatusFilterCombo`: ComboBox<String>, línea 258, @FXML
- `keyLabTable`: TableView<KeyMaterial>, línea 259, @FXML
- `keyLabNewNameField`: TextField, línea 260, @FXML
- `keyLabNewAlgoCombo`: ComboBox<String>, línea 261, @FXML
- `keyLabNewSizeCombo`: ComboBox<String>, línea 262, @FXML
- `keyLabImportBytesField`: PasswordField, línea 263, @FXML
- `keyLabImportBtn`: Button, línea 264, @FXML
- `keyLabDetailIdField`: TextField, línea 265, @FXML
- `keyLabDetailNameField`: TextField, línea 266, @FXML
- `keyLabDetailAlgoLabel`: Label, línea 267, @FXML
- `keyLabDetailBitsLabel`: Label, línea 268, @FXML
- `keyLabUsageEncryptCheck`: CheckBox, línea 269, @FXML
- `keyLabUsageDecryptCheck`: CheckBox, línea 270, @FXML
- `keyLabUsageMacCheck`: CheckBox, línea 271, @FXML
- `keyLabUsageWrapCheck`: CheckBox, línea 272, @FXML
- `keyLabUsageUnwrapCheck`: CheckBox, línea 273, @FXML
- `keyLabDetailExportabilityLabel`: Label, línea 274, @FXML
- `keyLabDetailKcvLabel`: Label, línea 275, @FXML
- `keyLabDetailFingerprintLabel`: Label, línea 276, @FXML
- `keyLabDetailOriginLabel`: Label, línea 277, @FXML
- `keyLabDetailCreatedLabel`: Label, línea 278, @FXML
- `keyLabDetailModifiedLabel`: Label, línea 279, @FXML
- `keyLabDetailStatusLabel`: Label, línea 280, @FXML
- `keyLabDetailValueField`: TextField, línea 281, @FXML
- `keyLabRevealBtn`: Button, línea 282, @FXML
- `keyLabArchiveBtn`: Button, línea 283, @FXML
- `keyLabUseCipherBtn`: Button, línea 284, @FXML
- `keyLabUseMacBtn`: Button, línea 285, @FXML
- `mainController`: StatusReporter, línea 287, no @FXML
- `hsmRefreshCallback`: Runnable, línea 288, no @FXML
- `lastGeneratedSymmetricKeyBytes`: byte[], línea 299, no @FXML
- `lastGeneratedSymmetricKeyType`: String, línea 300, no @FXML
- `summarySavedStatusLabel`: Label, línea 310, @FXML
- `currentGeneratedKeySummary`: GeneratedKeySummary, línea 319, no @FXML
- `rsaSendPrivateShelfBtn`: Button, línea 335, @FXML
- `ecdsaSendPrivateShelfBtn`: Button, línea 354, @FXML
- `dsaSendPrivateShelfBtn`: Button, línea 373, @FXML
- `eddsaSendPrivateShelfBtn`: Button, línea 392, @FXML
## Resúmenes, exportación y Shelf

### Métodos

- `acceptAsymmetricGeneration`: línea 160
- `hideGeneratedKeySummary`: línea 2242
- `updateGeneratedKeySummaryCard`: línea 2250
- `handleCopyGeneratedKey`: línea 2265 (handler en keys.fxml)
- `handleCopyGeneratedKcv`: línea 2281 (handler en keys.fxml)
- `handleCopyGeneratedSummary`: línea 2292 (handler en keys.fxml)
- `handleOpenValidationAndKcv`: línea 2319 (handler en keys.fxml)
- `handleKcvLengthToggle`: línea 2334 (handler en keys.fxml)
- `selectedKcvLength`: línea 2345
- `copyToClipboard`: línea 2349
- `handleClear`: línea 3342
- `handleClearAsymmetric`: línea 3360
- `copyPublicKey`: línea 3386
- `copyPrivateKey`: línea 3395
- `copyAsymmetricSummary`: línea 3410
- `exportPublicPem`: línea 3435
- `exportPrivatePem`: línea 3456
- `sendPublicKeyToShelf`: línea 3483
- `sendAsymmetricKeyToShelf`: línea 3496
- `revealShelfEntry`: línea 3544
- `summaryForGeneration`: línea 3550
- `tabsForGeneration`: línea 3561
- `handleGlobalSymmetricShelfAction`: línea 3574
- `handleGlobalAsymmetricShelfAction`: línea 3604
- `useInSignatures`: línea 3623
- `useInCertificates`: línea 3637
- `handleCopyRsaPublicKey`: línea 3649 (handler en keys.fxml)
- `handleCopyRsaPrivateKey`: línea 3650 (handler en keys.fxml)
- `handleCopyRsaSummary`: línea 3651 (handler en keys.fxml)
- `handleExportRsaPublicPem`: línea 3652 (handler en keys.fxml)
- `handleExportRsaPrivatePem`: línea 3653 (handler en keys.fxml)
- `handleSendRsaPublicToShelf`: línea 3654 (handler en keys.fxml)
- `handleSendRsaPrivateToShelf`: línea 3655 (handler en keys.fxml)
- `handleUseRsaInCipher`: línea 3656 (handler en keys.fxml)
- `handleUseRsaInSignatures`: línea 3666 (handler en keys.fxml)
- `handleUseRsaInCertificates`: línea 3667 (handler en keys.fxml)
- `handleClearRsa`: línea 3668 (handler en keys.fxml)
- `handleCopyEcdsaPublicKey`: línea 3677 (handler en keys.fxml)
- `handleCopyEcdsaPrivateKey`: línea 3678 (handler en keys.fxml)
- `handleCopyEcdsaSummary`: línea 3679 (handler en keys.fxml)
- `handleExportEcdsaPublicPem`: línea 3680 (handler en keys.fxml)
- `handleExportEcdsaPrivatePem`: línea 3681 (handler en keys.fxml)
- `handleSendEcdsaPublicToShelf`: línea 3682 (handler en keys.fxml)
- `handleSendEcdsaPrivateToShelf`: línea 3683 (handler en keys.fxml)
- `handleUseEcdsaInSignatures`: línea 3684 (handler en keys.fxml)
- `handleUseEcdsaInCertificates`: línea 3685 (handler en keys.fxml)
- `handleClearEcdsa`: línea 3686 (handler en keys.fxml)
- `handleCopyDsaPublicKey`: línea 3697 (handler en keys.fxml)
- `handleCopyDsaPrivateKey`: línea 3698 (handler en keys.fxml)
- `handleCopyDsaSummary`: línea 3699 (handler en keys.fxml)
- `handleExportDsaPublicPem`: línea 3700 (handler en keys.fxml)
- `handleExportDsaPrivatePem`: línea 3701 (handler en keys.fxml)
- `handleSendDsaPublicToShelf`: línea 3702 (handler en keys.fxml)
- `handleSendDsaPrivateToShelf`: línea 3703 (handler en keys.fxml)
- `handleUseDsaInSignatures`: línea 3704 (handler en keys.fxml)
- `handleUseDsaInCertificates`: línea 3705 (handler en keys.fxml)
- `handleClearDsa`: línea 3706 (handler en keys.fxml)
- `handleCopyEddsaPublicKey`: línea 3715 (handler en keys.fxml)
- `handleCopyEddsaPrivateKey`: línea 3716 (handler en keys.fxml)
- `handleCopyEddsaSummary`: línea 3717 (handler en keys.fxml)
- `handleExportEddsaPublicPem`: línea 3718 (handler en keys.fxml)
- `handleExportEddsaPrivatePem`: línea 3719 (handler en keys.fxml)
- `handleSendEddsaPublicToShelf`: línea 3720 (handler en keys.fxml)
- `handleSendEddsaPrivateToShelf`: línea 3721 (handler en keys.fxml)
- `handleUseEddsaInSignatures`: línea 3722 (handler en keys.fxml)
- `handleUseEddsaInCertificates`: línea 3723 (handler en keys.fxml)
- `handleClearEd25519`: línea 3724 (handler en keys.fxml)
- `getOutputText`: línea 3734

### Campos utilizados

- `ecdsaPublicKeyArea`: TextArea, línea 246, @FXML
- `ecdsaPrivateKeyArea`: TextArea, línea 247, @FXML
- `eddsaPublicKeyArea`: TextArea, línea 248, @FXML
- `eddsaPrivateKeyArea`: TextArea, línea 249, @FXML
- `rsaKeyMaterialTabs`: TabPane, línea 250, @FXML
- `ecdsaKeyMaterialTabs`: TabPane, línea 251, @FXML
- `dsaKeyMaterialTabs`: TabPane, línea 252, @FXML
- `eddsaKeyMaterialTabs`: TabPane, línea 253, @FXML
- `mainController`: StatusReporter, línea 287, no @FXML
- `generatedKeyField`: TextArea, línea 295, @FXML
- `lastGeneratedSymmetricKeyBytes`: byte[], línea 299, no @FXML
- `lastGeneratedSymmetricKeyType`: String, línea 300, no @FXML
- `generatedKeySummaryCard`: VBox, línea 303, @FXML
- `summaryAlgoLabel`: Label, línea 304, @FXML
- `summaryLengthLabel`: Label, línea 305, @FXML
- `summaryKcvLabel`: Label, línea 306, @FXML
- `summaryFingerprintLabel`: Label, línea 307, @FXML
- `summaryParityLabel`: Label, línea 308, @FXML
- `summaryOriginLabel`: Label, línea 309, @FXML
- `summarySavedStatusLabel`: Label, línea 310, @FXML
- `validationPane`: TitledPane, línea 311, @FXML
- `useFourByteKcvCheck`: CheckBox, línea 312, @FXML
- `currentGeneratedKeySummary`: GeneratedKeySummary, línea 319, no @FXML
- `rsaSummaryCard`: VBox, línea 322, @FXML
- `ecdsaSummaryCard`: VBox, línea 341, @FXML
- `dsaSummaryCard`: VBox, línea 360, @FXML
- `eddsaSummaryCard`: VBox, línea 379, @FXML
- `currentRsaSummary`: GeneratedAsymmetricKeySummary, línea 398, no @FXML
- `currentEcdsaSummary`: GeneratedAsymmetricKeySummary, línea 399, no @FXML
- `currentDsaSummary`: GeneratedAsymmetricKeySummary, línea 400, no @FXML
- `currentEddsaSummary`: GeneratedAsymmetricKeySummary, línea 401, no @FXML
- `keyInputField`: TextField, línea 404, @FXML
- `validationResultArea`: TextArea, línea 406, @FXML
- `componentResultsArea`: TextArea, línea 496, @FXML
- `component1Field`: TextField, línea 498, @FXML
- `component2Field`: TextField, línea 500, @FXML
- `component3Field`: TextField, línea 502, @FXML
- `rsaPublicKeyArea`: TextArea, línea 562, @FXML
- `rsaPrivateKeyArea`: TextArea, línea 564, @FXML
- `dsaPublicKeyArea`: TextArea, línea 570, @FXML
- `dsaPrivateKeyArea`: TextArea, línea 572, @FXML
- `ecdsaFpPublicKeyArea`: TextArea, línea 577, no @FXML
- `ecdsaFpPrivateKeyArea`: TextArea, línea 578, no @FXML
- `ed25519PublicKeyArea`: TextArea, línea 581, no @FXML
- `ed25519PrivateKeyArea`: TextArea, línea 582, no @FXML
- `lastGeneratedKeyPair`: KeyPair, línea 629, no @FXML
- `lastKeyType`: String, línea 630, no @FXML
## Simétrica, componentes y KCV

### Métodos

- `initialize`: línea 893
- `handleGenerateKey`: línea 1990 (handler en keys.fxml)
- `handleValidateKey`: línea 2360 (handler en keys.fxml)
- `handleSplitKey`: línea 2500 (handler en keys.fxml)
- `handleCombineComponents`: línea 2600 (handler en keys.fxml)

### Campos utilizados

- `mainController`: StatusReporter, línea 287, no @FXML
- `keyTypeCombo`: ComboBox<String>, línea 291, @FXML
- `forceOddParityCheck`: javafx.scene.control.CheckBox, línea 293, @FXML
- `generatedKeyField`: TextArea, línea 295, @FXML
- `saveGeneratedKeyButton`: Button, línea 297, @FXML
- `lastGeneratedSymmetricKeyBytes`: byte[], línea 299, no @FXML
- `lastGeneratedSymmetricKeyType`: String, línea 300, no @FXML
- `currentGeneratedKeySummary`: GeneratedKeySummary, línea 319, no @FXML
- `keyInputField`: TextField, línea 404, @FXML
- `validationResultArea`: TextArea, línea 406, @FXML
- `numComponentsCombo`: ComboBox<String>, línea 492, @FXML
- `keyToSplitField`: TextArea, línea 494, @FXML
- `componentResultsArea`: TextArea, línea 496, @FXML
- `component1Field`: TextField, línea 498, @FXML
- `component2Field`: TextField, línea 500, @FXML
- `component3Field`: TextField, línea 502, @FXML
- `component4Field`: TextField, línea 504, @FXML
- `component5Field`: TextField, línea 506, @FXML
## Almacenes, material y PKCS#11

### Métodos

- `init`: línea 723
- `initializeKeyMaterialInspector`: línea 931
- `initializeKeyPairComparator`: línea 936
- `initializeKeyStoreInspector`: línea 942
- `initializePkcs11Inspector`: línea 956
- `initializePkcs11Signing`: línea 973
- `initializePkcs11Certificates`: línea 988
- `initializePkcs11Jwt`: línea 994
- `initializePkcs11Cms`: línea 1004
- `initializePkcs11Wrap`: línea 1010
- `connectPkcs11`: línea 1047
- `disconnectPkcs11`: línea 1112
- `choosePkcs11Library`: línea 1125
- `handleSavePkcs11Profile`: línea 1135 (handler en keys.fxml)
- `handleDeletePkcs11Profile`: línea 1161 (handler en keys.fxml)
- `handlePkcs11ProfileSelection`: línea 1169
- `refreshPkcs11Profiles`: línea 1183
- `disconnectPkcs11Internal`: línea 1195
- `safePkcs11Message`: línea 1199
- `refreshPkcs11SigningKeys`: línea 1204
- `refreshPkcs11CertificateAliases`: línea 1221
- `refreshPkcs11WrapKeyAliases`: línea 1240
- `selectComboValue`: línea 1271
- `wrapWithPkcs11`: línea 1279
- `unwrapWithPkcs11`: línea 1304
- `requireComboValue`: línea 1339
- `showPkcs11CertificateChain`: línea 1347
- `handleUpdatePkcs11CertificateChain`: línea 1365 (handler en keys.fxml)
- `isVerifiedIssuer`: línea 1445
- `generatePkcs11Jwt`: línea 1457
- `generatePkcs11Cms`: línea 1474
- `signWithPkcs11`: línea 1493
- `verifyWithPkcs11`: línea 1510
- `requirePkcs11SigningAlias`: línea 1529
- `requirePkcs11Text`: línea 1537
- `requirePkcs11TextPayload`: línea 1543
- `handleInspectKeyMaterial`: línea 1550 (handler en keys.fxml)
- `handleCompareKeyPair`: línea 1582 (handler en keys.fxml)
- `handleInspectKeyStore`: línea 1605 (handler en keys.fxml)
- `chooseKeyStore`: línea 1638
- `saveKeyStoreProfile`: línea 1648
- `loadKeyStoreProfile`: línea 1659
- `refreshKeyStoreProfiles`: línea 1670
- `parsePublicMaterial`: línea 1676
- `parsePrivateMaterial`: línea 1686

### Campos utilizados

- `dialogService`: DialogService, línea 34, no @FXML
- `keysRoot`: VBox, línea 230, @FXML
- `pkcs11ProfilesController`: Pkcs11ProfilesController, línea 232, @FXML
- `icsfTokenPaneController`: IcsfTokenController, línea 236, @FXML
- `icsfBatchPaneController`: IcsfBatchController, línea 238, @FXML
- `icsfKeyWrapPaneController`: IcsfKeyWrapController, línea 240, @FXML
- `mainController`: StatusReporter, línea 287, no @FXML
- `hsmRefreshCallback`: Runnable, línea 288, no @FXML
- `keyMaterialInputArea`: TextArea, línea 410, @FXML
- `keyMaterialReportArea`: TextArea, línea 412, @FXML
- `keyComparePublicArea`: TextArea, línea 414, @FXML
- `keyComparePrivateArea`: TextArea, línea 416, @FXML
- `keyCompareResultArea`: TextArea, línea 418, @FXML
- `keyStoreTypeCombo`: ComboBox<String>, línea 420, @FXML
- `keyStorePasswordField`: PasswordField, línea 422, @FXML
- `keyStoreUnsafeExtractCheck`: CheckBox, línea 424, @FXML
- `keyStorePathField`: TextField, línea 426, @FXML
- `keyStoreReportArea`: TextArea, línea 428, @FXML
- `keyStoreProfileCombo`: ComboBox<String>, línea 430, @FXML
- `keyStoreProfileNameField`: TextField, línea 432, @FXML
- `pkcs11NameField`: TextField, línea 434, @FXML
- `pkcs11LibraryField`: TextField, línea 436, @FXML
- `pkcs11SlotField`: TextField, línea 438, @FXML
- `pkcs11PinField`: PasswordField, línea 440, @FXML
- `pkcs11ProfileCombo`: ComboBox<String>, línea 442, @FXML
- `pkcs11ReportArea`: TextArea, línea 444, @FXML
- `pkcs11SigningKeyCombo`: ComboBox<String>, línea 446, @FXML
- `pkcs11SignatureAlgorithmCombo`: ComboBox<String>, línea 448, @FXML
- `pkcs11DataArea`: TextArea, línea 450, @FXML
- `pkcs11SignatureArea`: TextArea, línea 452, @FXML
- `pkcs11CertificateAliasCombo`: ComboBox<String>, línea 454, @FXML
- `pkcs11CertificateArea`: TextArea, línea 456, @FXML
- `pkcs11JwtAlgorithmCombo`: ComboBox<String>, línea 458, @FXML
- `pkcs11JwtPayloadArea`: TextArea, línea 460, @FXML
- `pkcs11JwtOutputArea`: TextArea, línea 462, @FXML
- `pkcs11CmsDataArea`: TextArea, línea 464, @FXML
- `pkcs11CmsDetachedCheck`: CheckBox, línea 466, @FXML
- `pkcs11CmsOutputArea`: TextArea, línea 468, @FXML
- `pkcs11WrappingKeyCombo`: ComboBox<String>, línea 470, @FXML
- `pkcs11WrapKeyCombo`: ComboBox<String>, línea 472, @FXML
- `pkcs11WrapTransformationCombo`: ComboBox<String>, línea 474, @FXML
- `pkcs11WrapResultArea`: TextArea, línea 476, @FXML
- `pkcs11UnwrappingKeyCombo`: ComboBox<String>, línea 478, @FXML
- `pkcs11UnwrapDataArea`: TextArea, línea 480, @FXML
- `pkcs11UnwrapTransformationCombo`: ComboBox<String>, línea 482, @FXML
- `pkcs11UnwrapAlgorithmField`: TextField, línea 484, @FXML
- `pkcs11UnwrapTypeCombo`: ComboBox<String>, línea 486, @FXML
- `pkcs11UnwrapResultArea`: TextArea, línea 488, @FXML
## Certificados, CSR y CRL

### Métodos

- `initializeCertificateGen`: línea 1739
- `initializeCertificateGen`: línea 1770
- `initializeCertificateParse`: línea 1782
- `initializeCertificateComparator`: línea 1787
- `initializeCertificateIssuer`: línea 1793
- `initializeCrlManagement`: línea 1812
- `initializeCertificateChainValidation`: línea 1830
- `handleIssueCertificateFromCsr`: línea 1832
- `handleGenerateCrl`: línea 1880
- `handleRevokeCrl`: línea 1904
- `handleCompareCertificates`: línea 1950
- `initializeValidateCertificate`: línea 1974
- `handleGenerateCertificate`: línea 2722
- `handleGenerateCSR`: línea 2816
- `applySanConfiguration`: línea 2878
- `commaSeparatedValues`: línea 2884
- `handleParseCertificate`: línea 2892
- `handleValidateCertificate`: línea 2946

### Campos utilizados

- `LOG`: Logger, línea 33, no @FXML
- `certificateChainCoordinator`: CertificateChainCoordinator, línea 216, no @FXML
- `mainController`: StatusReporter, línea 287, no @FXML
- `keyTypeCombo`: ComboBox<String>, línea 291, @FXML
- `certCNField`: TextField, línea 585, no @FXML
- `certOrgField`: TextField, línea 586, no @FXML
- `certOUField`: TextField, línea 587, no @FXML
- `certLocalityField`: TextField, línea 588, no @FXML
- `certStateField`: TextField, línea 589, no @FXML
- `certCountryField`: TextField, línea 590, no @FXML
- `certEmailField`: TextField, línea 591, no @FXML
- `certValidityField`: TextField, línea 592, no @FXML
- `certKeyTypeCombo`: ComboBox<String>, línea 593, no @FXML
- `certSignAlgoCombo`: ComboBox<String>, línea 594, no @FXML
- `certOutputArea`: TextArea, línea 595, no @FXML
- `certSanDnsField`: TextField, línea 596, no @FXML
- `certSanIpField`: TextField, línea 597, no @FXML
- `certRootCaCheck`: CheckBox, línea 598, no @FXML
- `certInputArea`: TextArea, línea 601, no @FXML
- `certParseResultArea`: TextArea, línea 602, no @FXML
- `certCompareLeftArea`: TextArea, línea 603, no @FXML
- `certCompareRightArea`: TextArea, línea 604, no @FXML
- `certCompareResultArea`: TextArea, línea 605, no @FXML
- `certIssueCsrArea`: TextArea, línea 606, no @FXML
- `certIssueCaCertArea`: TextArea, línea 607, no @FXML
- `certIssueCaKeyArea`: TextArea, línea 608, no @FXML
- `certIssueValidityField`: TextField, línea 609, no @FXML
- `certIssueSignatureField`: TextField, línea 610, no @FXML
- `certIssueResultArea`: TextArea, línea 611, no @FXML
- `certIssueProfileCombo`: ComboBox<String>, línea 612, no @FXML
- `certIssuePathLengthField`: TextField, línea 613, no @FXML
- `crlIssuerCertArea`: TextArea, línea 616, no @FXML
- `crlIssuerKeyArea`: TextArea, línea 617, no @FXML
- `crlExistingCrlArea`: TextArea, línea 618, no @FXML
- `crlRevokeSerialField`: TextField, línea 619, no @FXML
- `crlRevokeReasonCombo`: ComboBox<String>, línea 620, no @FXML
- `crlResultArea`: TextArea, línea 621, no @FXML
- `valCertInput`: TextArea, línea 624, no @FXML
- `valIssuerInput`: TextArea, línea 625, no @FXML
- `valResultArea`: TextArea, línea 626, no @FXML

## Lectores y escritores de estado compartido

- `lastGeneratedSymmetricKeyBytes` escribe: initialize, handleGenerateKey; lee: handleSaveGeneratedKeyToLab, handleGlobalSymmetricShelfAction.
- `lastGeneratedSymmetricKeyType` escribe: initialize, handleGenerateKey; lee: handleSaveGeneratedKeyToLab, handleGlobalSymmetricShelfAction.
- `currentGeneratedKeySummary` escribe: handleGenerateKey, hideGeneratedKeySummary, handleCopyGeneratedKey, handleCopyGeneratedKcv, handleCopyGeneratedSummary, handleOpenValidationAndKcv; lee: initialize, handleSaveGeneratedKeyToLab, handleKcvLengthToggle.
- `currentRsaSummary` escribe: acceptAsymmetricGeneration, initialize, handleClearAsymmetric, handleUseRsaInCipher, handleClearRsa; lee: summaryForGeneration, handleCopyRsaPublicKey, handleCopyRsaPrivateKey, handleCopyRsaSummary, handleExportRsaPublicPem, handleExportRsaPrivatePem, handleSendRsaPublicToShelf, handleSendRsaPrivateToShelf, handleUseRsaInSignatures, handleUseRsaInCertificates.
- `currentEcdsaSummary` escribe: acceptAsymmetricGeneration, initialize, handleClearAsymmetric, handleClearEcdsa; lee: summaryForGeneration, handleCopyEcdsaPublicKey, handleCopyEcdsaPrivateKey, handleCopyEcdsaSummary, handleExportEcdsaPublicPem, handleExportEcdsaPrivatePem, handleSendEcdsaPublicToShelf, handleSendEcdsaPrivateToShelf, handleUseEcdsaInSignatures, handleUseEcdsaInCertificates.
- `currentDsaSummary` escribe: acceptAsymmetricGeneration, initialize, handleClearAsymmetric, handleClearDsa; lee: summaryForGeneration, handleCopyDsaPublicKey, handleCopyDsaPrivateKey, handleCopyDsaSummary, handleExportDsaPublicPem, handleExportDsaPrivatePem, handleSendDsaPublicToShelf, handleSendDsaPrivateToShelf, handleUseDsaInSignatures, handleUseDsaInCertificates.
- `currentEddsaSummary` escribe: acceptAsymmetricGeneration, handleClearAsymmetric, handleClearEd25519; lee: summaryForGeneration, handleCopyEddsaPublicKey, handleCopyEddsaPrivateKey, handleCopyEddsaSummary, handleExportEddsaPublicPem, handleExportEddsaPrivatePem, handleSendEddsaPublicToShelf, handleSendEddsaPrivateToShelf, handleUseEddsaInSignatures, handleUseEddsaInCertificates.
- `lastGeneratedKeyPair` escribe: acceptAsymmetricGeneration; lee: getLastGeneratedKeyPair.
- `lastKeyType` escribe: acceptAsymmetricGeneration; lee: .

Key Lab: los handlers de alta/importación/metadatos/archivo/borrado escriben SimulatedHsmProvider; refreshKeyLabTable, detalle, revelar, uso en cifrado/MAC y guardado generado leen. KeyMaterial pertenece al proveedor, la tabla conserva selección y metadatos. Almacenes: inspect/choose/profile leen/escriben ruta y perfiles; PKCS#11 connect/disconnect cambia HsmService, operaciones de firma/wrap/certificados leen el proveedor activo. Ningún coordinador nuevo retendrá un controlador incluido.

## Contratos públicos y consumidores

Inventario de consumidores de producción por llamada al controlador compartido; las firmas y los fx:id permanecen en la fachada. Las líneas de métodos y campos anteriores se refieren a la declaración original, incluida su anotación cuando la hay.

- `getLastGeneratedKeyPair`: `ModernMainController.java`.
- `init`: `ModernMainController.java`.
- `showSymmetricSection`: `ModernMainController.java`.
- `showAsymmetricSection`: `ModernMainController.java`.
- `isSymmetricSectionVisible`: `ModernMainController.java`.
- `expandSymmetricPane`: `ModernMainController.java`.
- `expandAsymmetricPane`: `ModernMainController.java`.
- `initializeCertificateGen`: `CertificatesController.java`.
- `initializeCertificateGen`: `CertificatesController.java`.
- `initializeCertificateParse`: `CertificatesController.java`.
- `initializeCertificateComparator`: `CertificatesController.java`.
- `initializeCertificateIssuer`: `CertificatesController.java`.
- `initializeCrlManagement`: `CertificatesController.java`.
- `handleIssueCertificateFromCsr`: `CertificatesController.java`.
- `handleGenerateCrl`: `CertificatesController.java`.
- `handleRevokeCrl`: `CertificatesController.java`.
- `handleCompareCertificates`: `CertificatesController.java`.
- `initializeValidateCertificate`: `CertificatesController.java`.
- `handleGenerateCertificate`: `CertificatesController.java`.
- `handleGenerateCSR`: `CertificatesController.java`.
- `handleParseCertificate`: `CertificatesController.java`.
- `handleValidateCertificate`: `CertificatesController.java`.
- `initializeCMS`: `CertificatesController.java`.
- `handleCadesTimestampOptionChanged`: `CertificatesController.java`.
- `handleCMSourceChanged`: `CertificatesController.java`.
- `handleLoadCMSKeys`: `CertificatesController.java`.
- `handleCMSEncryptSourceChanged`: `CertificatesController.java`.
- `handleLoadCMSEncryptKeys`: `CertificatesController.java`.
- `handleCMSSign`: `CertificatesController.java`.
- `handleCMSVerify`: `CertificatesController.java`.
- `handleUpgradeCadesLt`: `CertificatesController.java`.
- `handleCMSEncrypt`: `CertificatesController.java`.
- `handleCMSDecrypt`: `CertificatesController.java`.
- `initializeCertificateChain`: `CertificatesController.java`.
- `handleValidateCertificateChain`: `CertificatesController.java`.
- `handleClear`: `ModernMainController.java`.
- `handleClearAsymmetric`: `ModernMainController.java`.
- `handleGlobalSymmetricShelfAction`: `ModernMainController.java`.
- `handleGlobalAsymmetricShelfAction`: `ModernMainController.java`.
- `updateVisibilityControls`: `ModernMainController.java`.
- `refreshKeyLabTable`: `ModernMainController.java`.

Los restantes handlers públicos conservan sus entradas FXML; los métodos de inicialización de la interfaz clásica mantienen sus firmas aunque no tengan consumidor en el shell moderno. Los tests de fases 1/2/3, ShelfStaleSnapshotUITest, Ux28bAsymmetricShelfLiveUITest y los tests de integración de sesiones/configuración ejercitan los contratos y los fx:id.

- `CertificatesController.init`: aporta las vistas externas por `initializeCertificate*`, `initializeCrlManagement`, `initializeValidateCertificate`, `initializeCMS` y las operaciones delegadas.
- `ModernMainController.loadSymmetricKeysContent` / `connectShellServices`: carga del módulo, `init`, conexión de callbacks y navegación de secciones. `CipherController` recibe un proveedor de `getLastGeneratedKeyPair`.
- `ClipboardTargetNavigator`: carga referencias y destinos, incluido `selectKeyInKeyLab` y `fillTR31KeyBlockInput`.
- Shelf: `handleGlobalSymmetricShelfAction` y `handleGlobalAsymmetricShelfAction` conservan el resultado de generación, sin depender del snapshot de otra pantalla (`29c699a`).
- Sesiones y configuración: `UiStateSnapshot` y `ScreenConfigurationCoordinator` inspeccionan los campos FXML. Key Lab excluye el campo de importación de bytes; los secretos de histórico se redactan según el perfil.
- Process Designer y paleta: rutas de `UiNavigationRegistry` y navegación del shell; no acceden al nuevo estado compartido.
- Histórico e inspector: el contrato `StatusReporter.publish(OperationResult)` permanece.

## Auxiliares que se incorporan a las áreas

- Área simétrica: `setupHexValidation(TextField)` (4349), `setupHexValidation(TextArea)` (4362), `isValidHex` (4375). El predicado sólo comprueba el alfabeto hexadecimal durante la escritura; las operaciones validan después longitud y formato completo.
- Área almacenes: handlers de conexión, selección de fichero/perfil, firma, certificados, JWT, CMS, wrap y unwrap (854–866). La conexión/desconexión conserva la notificación de refresco HSM después de la operación.
- Lectura PEM y verificación de emisor se comparten en `KeysMaterialSupport`, sin JavaFX; el predicado hexadecimal se comparte en `KeysHexValidation`, también sin JavaFX.
