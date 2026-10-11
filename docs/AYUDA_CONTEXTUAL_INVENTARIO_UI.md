# Inventario verificable de UI para ayuda contextual

Generado por inspección XML de 30 FXML en `src/main/resources/fxml/` (10-10-2026). Es un mapa de controles, no una especificación de comportamiento criptográfico. Los enlaces de fuentes se resuelven desde este documento; funcionan también en otro checkout.

## Cómo usar este inventario

- Una fila representa un control operativo o una acción; textos puramente estáticos (`Label`, `Text`, encabezados decorativos) se omiten. `Tab` y `TitledPane` se usan como ruta de sección, no como campo.
- `fx:id` muestra el identificador literal. `—` significa que el FXML no asigna `fx:id`; para vincular ayuda estable, conviene añadir uno al control o usar un adaptador explícito, no buscar por el texto visible.
- La etiqueta prioriza `promptText`, luego `text`, luego `accessibleText`. Para `—` en etiqueta, se debe resolver con el `Label` vecino/controlador. `onAction` conserva el método de FXML incluido `#`; `—` indica que no está declarado allí (puede haber listeners en controlador/coordinador).
- `entrada` abarca controles de entrada/configuración. `salida/read-only` solo se marca cuando XML declara `editable="false"` o equivalente inequívoco; los demás `TextArea` pueden funcionar como entrada o salida según la sección y el controlador. No inferir solo por el tipo.
- Los valores de `ComboBox`/`ChoiceBox` que se pueblan en Java no se enumeran como comprobados. FXML suele definir solo el control; consultar el controlador/coordinador citado por `onAction` o por el nombre del módulo.
- Textos `%clave` son claves de bundle, no el texto localizado final. No se infieren listas de opciones ni compatibilidades criptográficas desde la presencia de selectores.

## Cobertura de archivos

- [`asic.fxml`](../src/main/resources/fxml/asic.fxml) — 28 controles operativos inventariados
- [`asn1.fxml`](../src/main/resources/fxml/asn1.fxml) — 20 controles operativos inventariados
- [`authentication.fxml`](../src/main/resources/fxml/authentication.fxml) — 36 controles operativos inventariados
- [`certificates.fxml`](../src/main/resources/fxml/certificates.fxml) — 82 controles operativos inventariados
- [`cipher.fxml`](../src/main/resources/fxml/cipher.fxml) — 75 controles operativos inventariados
- [`clipboard_shelf.fxml`](../src/main/resources/fxml/clipboard_shelf.fxml) — 18 controles operativos inventariados
- [`cms_inspector.fxml`](../src/main/resources/fxml/cms_inspector.fxml) — 13 controles operativos inventariados
- [`compare_results.fxml`](../src/main/resources/fxml/compare_results.fxml) — 4 controles operativos inventariados
- [`compressed_hex.fxml`](../src/main/resources/fxml/compressed_hex.fxml) — 5 controles operativos inventariados
- [`cose.fxml`](../src/main/resources/fxml/cose.fxml) — 32 controles operativos inventariados
- [`crypto_envelope_inspector.fxml`](../src/main/resources/fxml/crypto_envelope_inspector.fxml) — 9 controles operativos inventariados
- [`emv.fxml`](../src/main/resources/fxml/emv.fxml) — 146 controles operativos inventariados
- [`generic.fxml`](../src/main/resources/fxml/generic.fxml) — 100 controles operativos inventariados
- [`history.fxml`](../src/main/resources/fxml/history.fxml) — 17 controles operativos inventariados
- [`icsf_batch.fxml`](../src/main/resources/fxml/icsf_batch.fxml) — 18 controles operativos inventariados
- [`icsf_keywrap.fxml`](../src/main/resources/fxml/icsf_keywrap.fxml) — 34 controles operativos inventariados
- [`icsf_token.fxml`](../src/main/resources/fxml/icsf_token.fxml) — 14 controles operativos inventariados
- [`jose.fxml`](../src/main/resources/fxml/jose.fxml) — 133 controles operativos inventariados
- [`key_certificate_workbench.fxml`](../src/main/resources/fxml/key_certificate_workbench.fxml) — 14 controles operativos inventariados
- [`keys.fxml`](../src/main/resources/fxml/keys.fxml) — 275 controles operativos inventariados
- [`main-view-modern.fxml`](../src/main/resources/fxml/main-view-modern.fxml) — 75 controles operativos inventariados
- [`openpgp.fxml`](../src/main/resources/fxml/openpgp.fxml) — 40 controles operativos inventariados
- [`pades.fxml`](../src/main/resources/fxml/pades.fxml) — 32 controles operativos inventariados
- [`payments.fxml`](../src/main/resources/fxml/payments.fxml) — 94 controles operativos inventariados
- [`pkcs11_profiles.fxml`](../src/main/resources/fxml/pkcs11_profiles.fxml) — 11 controles operativos inventariados
- [`pqc.fxml`](../src/main/resources/fxml/pqc.fxml) — 26 controles operativos inventariados
- [`process_designer.fxml`](../src/main/resources/fxml/process_designer.fxml) — 39 controles operativos inventariados
- [`wallet.fxml`](../src/main/resources/fxml/wallet.fxml) — 98 controles operativos inventariados
- [`wss_security.fxml`](../src/main/resources/fxml/wss_security.fxml) — 58 controles operativos inventariados
- [`xml_security.fxml`](../src/main/resources/fxml/xml_security.fxml) — 53 controles operativos inventariados

## Controles por FXML

### [`asic.fxml`](../src/main/resources/fxml/asic.fxml)
Controller declarado: `com.cryptocarver.ui.AsicController`.

**General**
- `handleClearButton` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**ASiC Containers**
- `asicInputPathField` — **TextField**, entrada; etiqueta/prompt: “Payload file or ASiC-S container path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose input…”; `onAction`: `#handleChooseInput`.
- `asicOutputPathField` — **TextField**, entrada; etiqueta/prompt: “New .asics output path (for create)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose output…”; `onAction`: `#handleChooseOutput`.
- `asicSourceLocalRadio` — **RadioButton**, entrada; etiqueta/prompt: “Local PKCS#12”; `onAction`: `#handleSourceChanged`.
- `asicSourcePkcs11Radio` — **RadioButton**, entrada; etiqueta/prompt: “PKCS#11 Token”; `onAction`: `#handleSourceChanged`.
- `asicPkcs12PathField` — **TextField**, entrada; etiqueta/prompt: “PKCS#12 signing key (for create)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose key…”; `onAction`: `#handleChoosePkcs12`.
- `asicPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “PKCS#12 password”; `onAction`: `—`.
- `asicPkcs11AliasCombo` — **ComboBox**, entrada; etiqueta/prompt: “Token signing alias”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Token Keys”; `onAction`: `#handleLoadTokenKeys`.
- `asicFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Signature format:”; `onAction`: `—`.
- `asicLevelCombo` — **ComboBox**, entrada; etiqueta/prompt: “Baseline level:”; `onAction`: `#handleProfileChanged`.
- `asicTsaUrlField` — **TextField**, entrada; etiqueta/prompt: “https://tsa.example.test”; `onAction`: `—`.
- `asicRevocationFilesField` — **TextField**, salida/read-only; etiqueta/prompt: “No local CRL/OCSP evidence selected”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Choose CRL/OCSP…”; `onAction`: `#handleChooseRevocationFiles`.
- `asicOnlineRevocationCheck` — **CheckBox**, entrada; etiqueta/prompt: “Validate revocation online (OCSP/CRL) — disabled by default”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Create ASiC-S”; `onAction`: `#handleCreate`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect / Verify”; `onAction`: `#handleInspect`.
- `asicEPayloadsField` — **TextField**, salida/read-only; etiqueta/prompt: “No ASiC-E payload files selected”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Choose payloads…”; `onAction`: `#handleChooseAsicEPayloads`.
- `—` — **Button**, acción; etiqueta/prompt: “Create ASiC-E”; `onAction`: `#handleCreateE`.
- `asicTrustStorePathField` — **TextField**, entrada; etiqueta/prompt: “Optional local truststore (.p12/.pfx or .jks)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose truststore…”; `onAction`: `#handleChooseTrustStore`.
- `asicTrustStorePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Truststore password”; `onAction`: `—`.
- `asicResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “ASiC result will appear here...”; `onAction`: `—`; editable=false.

### [`asn1.fxml`](../src/main/resources/fxml/asn1.fxml)
Controller declarado: `com.cryptocarver.ui.ASN1Controller`.

**🔍 ASN.1 Decoder › Decode**
- `asn1InputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Input Format”; `onAction`: `—`.
- `asn1TypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Example / Type”; `onAction`: `#handleLoadASN1Example`.
- `asn1InputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste ASN.1 data here...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Parse ASN.1”; `onAction`: `#handleParseASN1`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearASN1`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#resetModule`.
- `—` — **Button**, acción; etiqueta/prompt: “Export Tree”; `onAction`: `#handleExportASN1Tree`.
- `—` — **Button**, acción; etiqueta/prompt: “Export JSON”; `onAction`: `#handleExportASN1Json`.
- `—` — **Button**, acción; etiqueta/prompt: “Export Markdown”; `onAction`: `#handleExportASN1Markdown`.
- `—` — **Button**, acción; etiqueta/prompt: “Load OID Registry”; `onAction`: `#handleLoadASN1OidRegistry`.
- `—` — **Button**, acción; etiqueta/prompt: “Canonicalize DER”; `onAction`: `#handleCanonicalizeASN1Der`.
- `asn1StrictDerCheck` — **CheckBox**, entrada; etiqueta/prompt: “Strict DER Mode”; `onAction`: `—`.
- `asn1TreeView` — **TreeView**, entrada; etiqueta/prompt: “Structure Tree”; `onAction`: `—`.
- `asn1DetailsArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Details / Analysis”; `onAction`: `—`; editable=false.
**🔍 ASN.1 Decoder › Encode**
- `asn1EncodeTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “ASN.1 Type”; `onAction`: `—`.
- `asn1EncodeInputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Value Format”; `onAction`: `—`.
- `asn1EncodeInputArea` — **TextArea**, entrada; etiqueta/prompt: “Enter value to encode...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encode”; `onAction`: `#handleEncodeASN1`.
- `—` — **Button**, acción; etiqueta/prompt: “Copy Output”; `onAction`: `#handleCopyASN1Output`.
- `asn1EncodeOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Encoded Output (DER Hex)”; `onAction`: `—`; editable=false.

### [`authentication.fxml`](../src/main/resources/fxml/authentication.fxml)
Controller declarado: `com.cryptocarver.ui.AuthenticationController`.

**General**
- `authInputArea` — **TextArea**, entrada; etiqueta/prompt: “Enter the message to authenticate...”; `onAction`: `—`.
**🔏 Digital Signatures**
- `signatureTemplateCombo` — **ComboBox**, entrada; etiqueta/prompt: “Select a template...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Apply”; `onAction`: `#handleApplySignatureTemplate`.
- `—` — **MenuButton**, acción; etiqueta/prompt: “Manage Template”; `onAction`: `—`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Save Current as Personal Template...”; `onAction`: `#handleSaveSignatureTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Export Selected Template...”; `onAction`: `#handleExportSignatureTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Import Template File...”; `onAction`: `#handleImportSignatureTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Delete Selected Personal Template”; `onAction`: `#handleDeleteSignatureTemplate`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleResetSignatureDefaults`.
- `signatureAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
**🔏 Digital Signatures › Private Key**
- `signaturePrivateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Paste Private Key PEM here...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Paste Private Key from Clipboard”; `onAction`: `#handlePasteSignPrivateKey`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Private Key…”; `onAction`: `#handleLoadSignPrivateKey`.
- `sigPrivKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
**🔏 Digital Signatures › Public Key**
- `signaturePublicKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Paste Public Key PEM here...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Paste Public Key from Clipboard”; `onAction`: `#handlePasteSignPublicKey`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Public Key…”; `onAction`: `#handleLoadSignPublicKey`.
- `sigPubKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
**🔏 Digital Signatures**
- `—` — **Button**, acción; etiqueta/prompt: “Sign”; `onAction`: `#handleSign`.
- `signatureVerifyField` — **TextField**, entrada; etiqueta/prompt: “Paste existing signature to verify (Format must match 'Output Format' selection)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify Signature”; `onAction`: `#handleVerify`.
**🔐 Message Authentication Codes (MAC)**
- `authMacAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm”; `onAction`: `—`.
- `authMacTruncationCombo` — **ComboBox**, entrada; etiqueta/prompt: “Output length”; `onAction`: `—`.
- `macKeySourceCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key material”; `onAction`: `—`.
- `macSaveToLabBtn` — **Button**, acción; etiqueta/prompt: “Save key to Lab”; `onAction`: `#handleSaveMacKeyToHsm`.
- `authMacKeyField` — **TextField**, entrada; etiqueta/prompt: “Enter MAC key in hexadecimal”; `onAction`: `—`.
- `macHsmKeyCombo` — **ComboBox**, entrada; etiqueta/prompt: “macHsmKeyCombo”; `onAction`: `—`; visible=false inicial.
- `—` — **Button**, acción; etiqueta/prompt: “Paste MAC Key”; `onAction`: `#handlePasteMacKey`.
- `—` — **Button**, acción; etiqueta/prompt: “Load MAC Key…”; `onAction`: `#handleLoadMacKey`.
- `macInspectKeyBtn` — **Button**, acción; etiqueta/prompt: “Inspect in Key Lab”; `onAction`: `#handleInspectMacKey`; visible=false inicial.
- `macKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `authMacNonceField` — **TextField**, entrada; etiqueta/prompt: “12 unique bytes, e.g. 00112233445566778899AABB”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate MAC”; `onAction`: `#handleGenerateMAC`.
- `authMacVerifyField` — **TextField**, entrada; etiqueta/prompt: “Paste the expected MAC”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify”; `onAction`: `#handleVerifyMAC`.
**General**
- `authOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The generated value or verification result will appear here.”; `onAction`: `—`; editable=false.

### [`certificates.fxml`](../src/main/resources/fxml/certificates.fxml)
Controller declarado: `com.cryptocarver.ui.CertificatesController`.

**📜 Generate Certificate**
- `certCNField` — **TextField**, entrada; etiqueta/prompt: “localhost”; `onAction`: `—`.
- `certOrgField` — **TextField**, entrada; etiqueta/prompt: “Crypto Org”; `onAction`: `—`.
- `certOUField` — **TextField**, entrada; etiqueta/prompt: “IT Security”; `onAction`: `—`.
- `certCountryField` — **TextField**, entrada; etiqueta/prompt: “ES”; `onAction`: `—`.
- `certStateField` — **TextField**, entrada; etiqueta/prompt: “State/Province”; `onAction`: `—`.
- `certLocalityField` — **TextField**, entrada; etiqueta/prompt: “City”; `onAction`: `—`.
- `certEmailField` — **TextField**, entrada; etiqueta/prompt: “Optional”; `onAction`: `—`.
- `certValidityField` — **TextField**, entrada; etiqueta/prompt: “365”; `onAction`: `—`.
- `certKeyTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key Type:”; `onAction`: `—`.
- `certSignAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Sign Algorithm:”; `onAction`: `—`.
- `certSanDnsField` — **TextField**, entrada; etiqueta/prompt: “Comma-separated (e.g. api.example.test)”; `onAction`: `—`.
- `certSanIpField` — **TextField**, entrada; etiqueta/prompt: “Comma-separated (e.g. 127.0.0.1)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Certificate”; `onAction`: `#handleGenerateCertificate`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate CSR”; `onAction`: `#handleGenerateCSR`.
- `certRootCaCheck` — **CheckBox**, entrada; etiqueta/prompt: “Generate as laboratory Root CA (CA:TRUE, keyCertSign)”; `onAction`: `—`.
- `certOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Certificate will appear here...”; `onAction`: `—`; editable=false, visible=false inicial.
**🏛 Issue Certificate from CSR (Laboratory CA)**
- `certIssueCsrArea` — **TextArea**, entrada; etiqueta/prompt: “PKCS#10 CSR PEM”; `onAction`: `—`.
- `certIssueCaCertArea` — **TextArea**, entrada; etiqueta/prompt: “Issuer Root CA certificate PEM”; `onAction`: `—`.
- `certIssueCaKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Issuer Root CA private key PEM”; `onAction`: `—`.
- `certIssueValidityField` — **TextField**, entrada; etiqueta/prompt: “Days”; `onAction`: `—`; text=90.
- `certIssueSignatureField` — **TextField**, entrada; etiqueta/prompt: “Automatic or issuer signature algorithm”; `onAction`: `—`; text=Automatic.
- `certIssueProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Select Profile”; `onAction`: `—`.
- `certIssuePathLengthField` — **TextField**, entrada; etiqueta/prompt: “0”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Issue Laboratory Certificate”; `onAction`: `#handleIssueCertificateFromCsr`.
- `certIssueResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Validates the CSR signature before issuing. Use only a laboratory CA key.”; `onAction`: `—`; editable=false.
**🔍 Parse Certificate**
- `certTemplateCombo` — **ComboBox**, entrada; etiqueta/prompt: “Select a template...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Apply”; `onAction`: `#handleApplyCertTemplate`.
- `—` — **MenuButton**, acción; etiqueta/prompt: “Manage Template”; `onAction`: `—`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Save Current as Personal Template...”; `onAction`: `#handleSaveCertTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Export Selected Template...”; `onAction`: `#handleExportCertTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Import Template File...”; `onAction`: `#handleImportCertTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Delete Selected Personal Template”; `onAction`: `#handleDeleteCertTemplate`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleResetCertDefaults`.
- `certFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Certificate format:”; `onAction`: `—`.
- `certInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste certificate in PEM format...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Parse Certificate”; `onAction`: `#handleParseCertificate`.
- `certParseResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Parse results will appear here...”; `onAction`: `—`; editable=false, visible=false inicial.
**⚖ Compare Certificates**
- `certCompareLeftArea` — **TextArea**, entrada; etiqueta/prompt: “Left X.509 certificate PEM”; `onAction`: `—`.
- `certCompareRightArea` — **TextArea**, entrada; etiqueta/prompt: “Right X.509 certificate PEM”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Compare Certificates”; `onAction`: `#handleCompareCertificates`.
- `certCompareResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “certCompareResultArea”; `onAction`: `—`; editable=false.
**🛡 Validate Certificate**
- `valCertInput` — **TextArea**, entrada; etiqueta/prompt: “Paste X.509 Certificate...”; `onAction`: `—`.
- `valIssuerInput` — **TextArea**, entrada; etiqueta/prompt: “Paste Issuer Certificate (for signature verification)...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate Certificate”; `onAction`: `#handleValidateCertificate`.
- `valResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Validation Report”; `onAction`: `—`; editable=false.
**🔗 Validate Chain**
- `chainInputArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----...”; `onAction`: `—`.
- `chainCrlInputArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN X509 CRL-----...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate Chain”; `onAction`: `#handleValidateCertificateChain`.
- `—` — **Button**, acción; etiqueta/prompt: “Exportar informe (Markdown)”; `onAction`: `#handleExportChainReport`.
- `chainResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Validation results will appear here...”; `onAction`: `—`; editable=false, visible=false inicial.
**🚫 CRL Management**
- `crlIssuerCertArea` — **TextArea**, entrada; etiqueta/prompt: “Issuer Root CA certificate PEM”; `onAction`: `—`.
- `crlIssuerKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Issuer Root CA private key PEM”; `onAction`: `—`.
- `crlExistingCrlArea` — **TextArea**, entrada; etiqueta/prompt: “Existing CRL PEM (optional)”; `onAction`: `—`.
- `crlRevokeSerialField` — **TextField**, entrada; etiqueta/prompt: “Serial Number to Revoke (Hex)”; `onAction`: `—`.
- `crlRevokeReasonCombo` — **ComboBox**, entrada; etiqueta/prompt: “Reason”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Empty CRL”; `onAction`: `#handleGenerateCrl`.
- `—` — **Button**, acción; etiqueta/prompt: “Revoke & Update CRL”; `onAction`: `#handleRevokeCrl`.
- `crlResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Generate or update an offline CRL.”; `onAction`: `—`; editable=false.
**📦 CMS Operations**
- `cmsInputArea` — **TextArea**, entrada; etiqueta/prompt: “Enter raw data to Sign/Encrypt, or PKCS#7 PEM to Verify/Decrypt...”; `onAction`: `—`.
**📦 CMS Operations › Signature (Sign/Verify)**
- `cmsSignSourceLocalRadio` — **RadioButton**, entrada; etiqueta/prompt: “Local PEM”; `onAction`: `#handleCMSourceChanged`.
- `cmsSignSourcePkcs11Radio` — **RadioButton**, entrada; etiqueta/prompt: “PKCS#11 Token”; `onAction`: `#handleCMSourceChanged`.
- `cmsSignCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----...”; `onAction`: `—`.
- `cmsSignKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----...”; `onAction`: `—`.
- `cmsSignKeyAliasCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key Alias:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Token Keys”; `onAction`: `#handleLoadCMSKeys`.
- `cmsVerifyDataArea` — **TextArea**, entrada; etiqueta/prompt: “Enter original raw data here if verifying a detached signature...”; `onAction`: `—`.
- `cmsDetachedCheck` — **CheckBox**, entrada; etiqueta/prompt: “Detached Signature (Sign)”; `onAction`: `—`.
- `cmsCadesBesCheck` — **CheckBox**, entrada; etiqueta/prompt: “CAdES-BES”; `onAction`: `—`.
- `cmsCadesTCheck` — **CheckBox**, entrada; etiqueta/prompt: “CAdES-T (RFC 3161)”; `onAction`: `#handleCadesTimestampOptionChanged`.
- `—` — **Button**, acción; etiqueta/prompt: “Sign”; `onAction`: `#handleCMSSign`.
- `—` — **Button**, acción; etiqueta/prompt: “Add LT Evidence...”; `onAction`: `#handleUpgradeCadesLt`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify”; `onAction`: `#handleCMSVerify`.
- `cmsCadesTsaUrlField` — **TextField**, entrada; etiqueta/prompt: “https://your-tsa.example/”; `onAction`: `—`.
**📦 CMS Operations › Encryption (EnvelopedData)**
- `cmsEncryptSourceLocalRadio` — **RadioButton**, entrada; etiqueta/prompt: “Local PEM (Cert/Key)”; `onAction`: `#handleCMSEncryptSourceChanged`.
- `cmsEncryptSourcePkcs11Radio` — **RadioButton**, entrada; etiqueta/prompt: “PKCS#11 Token”; `onAction`: `#handleCMSEncryptSourceChanged`.
- `cmsEncryptCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----...”; `onAction`: `—`.
- `cmsDecryptKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----...”; `onAction`: `—`.
- `cmsEncryptKeyAliasCombo` — **ComboBox**, entrada; etiqueta/prompt: “Token Key Alias:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Token Keys”; `onAction`: `#handleLoadCMSEncryptKeys`.
- `—` — **Button**, acción; etiqueta/prompt: “Encrypt”; `onAction`: `#handleCMSEncrypt`.
- `—` — **Button**, acción; etiqueta/prompt: “Decrypt”; `onAction`: `#handleCMSDecrypt`.
**📦 CMS Operations**
- `cmsOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output / Result:”; `onAction`: `—`; editable=false.

### [`cipher.fxml`](../src/main/resources/fxml/cipher.fxml)
Controller declarado: `com.cryptocarver.ui.CipherController`.

**Templates**
- `cipherTemplateCombo` — **ComboBox**, entrada; etiqueta/prompt: “Select a template...”; `onAction`: `—`.
- `symTemplateHelpBtn` — **Button**, ayuda contextual; etiqueta: “?”; explica selección y alcance de las plantillas.
- `symApplyTemplateHelpBtn` — **Button**, ayuda contextual; etiqueta: “?”; explica aplicación de preset/plantilla y campos que puede limpiar.
- `—` — **Button**, acción; etiqueta/prompt: “Apply”; `onAction`: `#handleApplyCipherTemplate`.
- `cipherTemplateMenu` — **MenuButton**, acción; etiqueta/prompt: “Manage Template”; accesible con explicación general de gestión.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Save Current as Personal Template...”; `onAction`: `#handleSaveCipherTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Export Selected Template...”; `onAction`: `#handleExportCipherTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Import Template File...”; `onAction`: `#handleImportCipherTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Delete Selected Personal Template”; `onAction`: `#handleDeleteCipherTemplate`.
- `symResetDefaultsHelpBtn` — **Button**, ayuda contextual; etiqueta: “?”; explica el restablecimiento y los campos que limpia.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleResetCipherDefaults`.
**General**
- `symmetricAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `cipherModeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Mode:”; `onAction`: `—`.
- `paddingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Padding:”; `onAction`: `—`.
- `symKeySourceCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key Source”; `onAction`: `—`.
- `symmetricKeyField` — **TextField**, entrada; etiqueta/prompt: “Enter key in hex (required)...”; `onAction`: `—`.
- `symHsmKeyCombo` — **ComboBox**, entrada; etiqueta/prompt: “symHsmKeyCombo”; `onAction`: `—`; visible=false inicial.
- `inspectKeyBtn` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleInspectKey`; visible=false inicial.
- `saveToLabBtn` — **Button**, acción; etiqueta/prompt: “Save to Lab”; `onAction`: `#saveCurrentKeyToHsm`.
- `—` — **Button**, acción; etiqueta/prompt: “Paste Key”; `onAction`: `#handlePasteSymKey`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Key…”; `onAction`: `#handleLoadSymKey`.
- `symKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `ivField` — **TextField**, entrada; etiqueta/prompt: “Hex IV (required for CBC/CTR/GCM modes)...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate”; `onAction`: `#generateIV`.
- `gcmTagField` — **TextField**, entrada; etiqueta/prompt: “Hex Tag (16 bytes; required for AEAD Decryption)”; `onAction`: `—`.
- `aadField` — **TextField**, entrada; etiqueta/prompt: “Hex Additional Authenticated Data (optional)”; `onAction`: `—`.
- `cipherInputArea` — **TextArea**, entrada; etiqueta/prompt: “Input data”; `onAction`: `—`.
- `cipherOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output result”; `onAction`: `—`; editable=false.
- `cipherCopyResultButton` — **Button**, acción; etiqueta/prompt: “Copy”; `onAction`: `#handleCopyCipherResult`.
- `cipherShelfResultButton` — **Button**, acción; etiqueta/prompt: “Shelf”; `onAction`: `#handleShelfCipherResult`.
- `cipherExpandResultButton` — **Button**, acción; etiqueta/prompt: “Expand”; `onAction`: `#handleExpandCipherResult`.
- `cipherSaveResultButton` — **Button**, acción; etiqueta/prompt: “Save”; `onAction`: `#handleSaveCipherResult`.
- `cipherUseResultButton` — **Button**, acción; etiqueta/prompt: “Use as input”; `onAction`: `#handleUseCipherResultAsInput`.
- `symmetricEncryptButton` — **Button**, acción; etiqueta/prompt: “Encrypt”; `onAction`: `#handleSymmetricEncrypt`.
- `symmetricDecryptButton` — **Button**, acción; etiqueta/prompt: “Decrypt”; `onAction`: `#handleSymmetricDecrypt`.
**🔢 Format-Preserving Encryption (FF1 / FF3-1)**
- `fpeOperationCombo` — **ComboBox**, entrada; etiqueta/prompt: “Operation:”; `onAction`: `—`.
- `fpeAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `fpeKeyField` — **TextField**, entrada; etiqueta/prompt: “16, 24, or 32-byte AES key”; `onAction`: `—`.
- `fpeTweakField` — **TextField**, entrada; etiqueta/prompt: “Optional for FF1; exactly 7 bytes for FF3-1”; `onAction`: `—`.
- `fpeAlphabetPresetCombo` — **ComboBox**, entrada; etiqueta/prompt: “fpeAlphabetPresetCombo”; `onAction`: `—`.
- `fpeAlphabetField` — **TextField**, entrada; etiqueta/prompt: “Custom alphabet”; `onAction`: `—`.
- `fpeInputArea` — **TextArea**, entrada; etiqueta/prompt: “Input”; `onAction`: `—`.
- `fpeOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output”; `onAction`: `—`; editable=false.
- `fpeRunButton` — **Button**, acción; etiqueta/prompt: “Run FPE”; `onAction`: `#handleFpe`.

El máximo de tweak FF1 que muestra la ficha procede de `FormatPreservingEncryption.maximumTweakLength()`, la misma constante de solo lectura que usa la validación del motor.
**📁 File Cipher (Streaming)**
- `fileCipherAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Processes the file in chunks. GCM and ChaCha20-Poly1305 use a detached 16-byte tag.”; `onAction`: `—`.
- `fileCipherSourceField` — **TextField**, entrada; etiqueta/prompt: “Source file path”; `onAction`: `—`.
- `fileCipherChooseSourceBtn` — **Button**, acción; etiqueta/prompt: “…”; `onAction`: `#chooseFileCipherSource`.
- `fileCipherDestinationField` — **TextField**, entrada; etiqueta/prompt: “Output file path”; `onAction`: `—`.
- `fileCipherChooseDestinationBtn` — **Button**, acción; etiqueta/prompt: “…”; `onAction`: `#chooseFileCipherDestination`.
- `fileCipherTagField` — **TextField**, entrada; etiqueta/prompt: “Detached AEAD tag path (.tag; required for GCM / ChaCha)”; `onAction`: `—`.
- `fileCipherChooseTagBtn` — **Button**, acción; etiqueta/prompt: “…”; `onAction`: `#chooseFileCipherTag`.
- `fileCipherKeyField` — **TextField**, entrada; etiqueta/prompt: “Key in hexadecimal”; `onAction`: `—`.
- `fileCipherNonceField` — **TextField**, entrada; etiqueta/prompt: “IV / nonce in hexadecimal (fresh value for AEAD encryption)”; `onAction`: `—`.
- `fileCipherGenerateNonceBtn` — **Button**, acción; etiqueta/prompt: “Generate”; `onAction`: `#generateFileCipherNonce`.
- `fileCipherAadField` — **TextField**, entrada; etiqueta/prompt: “AAD in hexadecimal (optional, AEAD only)”; `onAction`: `—`.
- `fileCipherLinesCheck` — **CheckBox**, entrada; etiqueta/prompt: “Process each UTF-8 line independently (GCM/ChaCha: fresh nonce per line; CBC: supplied IV for all lines)”; `onAction`: `—`.
- `fileCipherLineEncodingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Line output:”; `onAction`: `—`.
- `fileCipherLineCharsetCombo` — **ComboBox**, entrada; etiqueta/prompt: “Text:”; `onAction`: `—`.
- `fileCipherCompactCbcCheck` — **CheckBox**, entrada; etiqueta/prompt: “Write line records without header (compact format)”; `onAction`: `—`.
- `fileCipherEncryptBtn` — **Button**, acción; etiqueta/prompt: “Encrypt File”; `onAction`: `#handleFileCipherEncrypt`.
- `fileCipherDecryptBtn` — **Button**, acción; etiqueta/prompt: “Decrypt File”; `onAction`: `#handleFileCipherDecrypt`.
- `fileCipherAnalyzeBtn` — **Button**, acción; etiqueta/prompt: “Analyze Encrypted File”; `onAction`: `#handleAnalyzeFileCipher`.
- `fileCipherExportRecipeBtn` — **Button**, acción; etiqueta/prompt: “Export Recipe...”; `onAction`: `#handleExportFileCipherRecipe`.
- `fileCipherImportRecipeBtn` — **Button**, acción; etiqueta/prompt: “Import Recipe...”; `onAction`: `#handleImportFileCipherRecipe`.
- `fileCipherResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Processes the file in chunks. GCM and ChaCha20-Poly1305 use a detached 16-byte tag.”; `onAction`: `—`; editable=false.
**🔑 Asymmetric Cipher (RSA)**
- `rsaPaddingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Padding:”; `onAction`: `—`.
- `asymmetricInputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Input Format:”; `onAction`: `—`.
- `asymmetricOutputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Output Format:”; `onAction`: `—`.
- `publicKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Public Key (PEM or Hex):”; `onAction`: `—`.
- `asymPastePublicKeyBtn` — **Button**, acción; etiqueta/prompt: “Paste Public Key”; `onAction`: `#handlePastePublicKey`.
- `asymLoadPublicKeyBtn` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleReadPublicKeyFile`.
- `cipherPubKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `privateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Private Key (PEM or Hex):”; `onAction`: `—`.
- `asymPastePrivateKeyBtn` — **Button**, acción; etiqueta/prompt: “Paste Private Key”; `onAction`: `#handlePastePrivateKey`.
- `asymLoadPrivateKeyBtn` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleReadPrivateKeyFile`.
- `cipherPrivKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `asymEncryptBtn` — **Button**, acción; etiqueta/prompt: “Encrypt (Public Key)”; `onAction`: `#handleAsymmetricEncrypt`.
- `asymDecryptBtn` — **Button**, acción; etiqueta/prompt: “Decrypt (Private Key)”; `onAction`: `#handleAsymmetricDecrypt`.

En `cipher.fxml`, los controles de FPE, File Cipher y RSA reciben botones `?` en tiempo de carga cuando se activa el modo de ayuda. En File Cipher, los botones Encrypt/Decrypt resuelven la ficha contextual según el modo efectivo: archivo completo o registros por línea (`encrypt_file`/`encrypt_lines`, `decrypt_file`/`decrypt_lines`). Un cambio programático o interactivo del selector refresca también el texto accesible del botón. El `?` de “Analyze Encrypted File” abre la ficha A008 sin invocar el analizador; los flags de esa ficha se derivan de los controles del propio formulario File Cipher. Los botones se insertan después del control y se liberan junto con el módulo.

Las consultas de clave, IV/nonce, AAD y resultado conservan la dirección actual de cifrado/descifrado. Si la ficha ya está abierta, cambiar el checkbox de líneas actualiza acción, campo y resumen sin volver a pulsar `?`; si la ayuda está desactivada, el cambio no abre una vista. A008 permanece activa durante ese cambio. Al regresar a File Cipher, la acción guardada se reconcilia con el valor real del checkbox, y una consulta de campo desde A008 conserva la dirección guardada y usa la variante actual.

En RSA, el tamaño mostrado se deriva de los metadatos públicos que usaría la operación: la clave cargada en el panel si su texto sigue coincidiendo, o la clave pública de la pareja compartida si el área está vacía. Texto no cargado o ausencia de un RSA público vigente elimina el tamaño; la consulta no analiza texto ni examina la clave privada. Cambiar padding y volver a la ruta actualiza el máximo.

### [`clipboard_shelf.fxml`](../src/main/resources/fxml/clipboard_shelf.fxml)
Controller declarado: `com.cryptocarver.ui.ClipboardShelfController`.

**General**
- `searchField` — **TextField**, entrada; etiqueta/prompt: “Search label, operation, tag, note...”; `onAction`: `—`.
- `pinnedFilterCombo` — **ComboBox**, entrada; etiqueta/prompt: “All (Pinned & Unpinned)”; `onAction`: `—`.
- `sourceFilterCombo` — **ComboBox**, entrada; etiqueta/prompt: “All Operations”; `onAction`: `—`.
- `formatFilterCombo` — **ComboBox**, entrada; etiqueta/prompt: “Any Format”; `onAction`: `—`.
- `classFilterCombo` — **ComboBox**, entrada; etiqueta/prompt: “Any Classification”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Open Copy Window”; `onAction`: `#handleOpenClipboardWindow`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear Shelf”; `onAction`: `#handleClearShelf`.
- `shelfTable` — **TableView**, entrada; etiqueta/prompt: “shelfTable”; `onAction`: `—`.
- `—` — **TableView**, entrada; etiqueta/prompt: “sin etiqueta/prompt en FXML”; `onAction`: `—`.
- `pinBtn` — **Button**, acción; etiqueta/prompt: “Pin”; `onAction`: `#handleTogglePin`; disable=true.
- `editTagsNoteBtn` — **Button**, acción; etiqueta/prompt: “Edit Note & Tags”; `onAction`: `#handleEditTagsNote`; disable=true.
- `compareBtn` — **Button**, acción; etiqueta/prompt: “Compare...”; `onAction`: `#handleCompare`; disable=true.
- `useInMenu` — **MenuButton**, acción; etiqueta/prompt: “Use in...”; `onAction`: `—`; disable=true.
- `copyBtn` — **Button**, acción; etiqueta/prompt: “Copy”; `onAction`: `#handleCopy`; disable=true.
- `expandBtn` — **Button**, acción; etiqueta/prompt: “Open Expanded”; `onAction`: `#handleOpenExpanded`; disable=true.
- `renameBtn` — **Button**, acción; etiqueta/prompt: “Rename”; `onAction`: `#handleRename`; disable=true.
- `deleteBtn` — **Button**, acción; etiqueta/prompt: “Delete”; `onAction`: `#handleDelete`; disable=true.
- `detailsArea` — **TextArea**, salida/read-only; etiqueta/prompt: “detailsArea”; `onAction`: `—`; editable=false.

### [`cms_inspector.fxml`](../src/main/resources/fxml/cms_inspector.fxml)
Controller declarado: `com.cryptocarver.ui.CmsInspectorController`.

**🔍 Inspect / Validate CMS**
- `—` — **Button**, acción; etiqueta/prompt: “Load CMS file...”; `onAction`: `#handleLoadCmsFile`.
- `cmsInputArea` — **TextArea**, entrada; etiqueta/prompt: “Or paste CMS bytes here...”; `onAction`: `—`.
- `cmsDetachedCheck` — **CheckBox**, entrada; etiqueta/prompt: “Provide detached content”; `onAction`: `#handleDetachedToggle`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Content file...”; `onAction`: `#handleLoadContentFile`.
- `cmsContentArea` — **TextArea**, entrada; etiqueta/prompt: “Or paste content here...”; `onAction`: `—`; visible=false inicial.
- `—` — **Button**, acción; etiqueta/prompt: “Select Truststore...”; `onAction`: `#handleSelectTruststore`.
- `truststorePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Password (not saved)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect CMS”; `onAction`: `#handleInspectCms`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate SignedData”; `onAction`: `#handleValidateSignedData`.
- `—` — **Button**, acción; etiqueta/prompt: “Export Report”; `onAction`: `#handleExportReport`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
- `cmsReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Inspection Results:”; `onAction`: `—`; editable=false.

### [`compare_results.fxml`](../src/main/resources/fxml/compare_results.fxml)
Controller declarado: `com.cryptocarver.ui.CompareResultsController`.

**General**
- `diffDetailsArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Diff Details”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Export Comparison Report...”; `onAction`: `#handleExportReport`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset”; `onAction`: `#handleReset`.
- `—` — **Button**, acción; etiqueta/prompt: “Close”; `onAction`: `#handleClose`.

### [`compressed_hex.fxml`](../src/main/resources/fxml/compressed_hex.fxml)
Controller declarado: `com.cryptocarver.ui.CompressedHexController`.

**↔️ Compressed Hex (2-row)**
- `compressedHexInputArea` — **TextArea**, entrada; etiqueta/prompt: “Two rows:
AFD12
CA123

or a normal hexadecimal value: ACFAD11223”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Two rows → Hex”; `onAction`: `#handleExpandCompressedHex`.
- `—` — **Button**, acción; etiqueta/prompt: “Hex → Two rows”; `onAction`: `#handleCompressHex`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearCompressedHex`.
- `compressedHexOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output:”; `onAction`: `—`; editable=false.

### [`cose.fxml`](../src/main/resources/fxml/cose.fxml)
Controller declarado: `com.cryptocarver.ui.COSEController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**Sign**
- `sign1AlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `sign1PrivateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `sign1PublicKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `sign1PayloadArea` — **TextArea**, entrada; etiqueta/prompt: “Text to sign”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Sign (COSE__Sign1)”; `onAction`: `#handleSign1`.
- `sign1OutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “COSE_Sign1 message (hex) will appear here”; `onAction`: `—`; editable=false.
**Verify**
- `verify1PublicKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `verify1MessageArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the hex-encoded COSE_Sign1 message”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify (COSE__Sign1)”; `onAction`: `#handleVerify1`.
- `verify1ResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Verification result will appear here”; `onAction`: `—`; editable=false.
**Create MAC**
- `mac0AlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `mac0KeyField` — **TextField**, entrada; etiqueta/prompt: “Symmetric MAC key in hex”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Key”; `onAction`: `#handleGenerateMac0Key`.
- `mac0PayloadArea` — **TextArea**, entrada; etiqueta/prompt: “Text to MAC”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Create (COSE__Mac0)”; `onAction`: `#handleMac0`.
- `mac0OutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “COSE_Mac0 message (hex) will appear here”; `onAction`: `—`; editable=false.
**Verify MAC**
- `verifyMac0KeyField` — **TextField**, entrada; etiqueta/prompt: “Symmetric MAC key in hex”; `onAction`: `—`.
- `verifyMac0MessageArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the hex-encoded COSE_Mac0 message”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify (COSE__Mac0)”; `onAction`: `#handleVerifyMac0`.
- `verifyMac0ResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Verification result will appear here”; `onAction`: `—`; editable=false.
**Encrypt**
- `encrypt0AlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `encrypt0KeyField` — **TextField**, entrada; etiqueta/prompt: “Symmetric key in hex”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Key”; `onAction`: `#handleGenerateEncrypt0Key`.
- `encrypt0PayloadArea` — **TextArea**, entrada; etiqueta/prompt: “Text to encrypt”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encrypt (COSE__Encrypt0)”; `onAction`: `#handleEncrypt0`.
- `encrypt0OutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “COSE_Encrypt0 message (hex) will appear here”; `onAction`: `—`; editable=false.
**Decrypt**
- `decrypt0KeyField` — **TextField**, entrada; etiqueta/prompt: “Symmetric key in hex”; `onAction`: `—`.
- `decrypt0MessageArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the hex-encoded COSE_Encrypt0 message”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Decrypt (COSE__Encrypt0)”; `onAction`: `#handleDecrypt0`.
- `decrypt0ResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Decrypted payload will appear here”; `onAction`: `—`; editable=false.

### [`crypto_envelope_inspector.fxml`](../src/main/resources/fxml/crypto_envelope_inspector.fxml)
Controller declarado: `com.cryptocarver.ui.CryptoEnvelopeInspectorController`.

**🔎 Crypto Envelope Inspector**
- `envelopeInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste a Crypto Envelope here...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleInspect`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
- `envelopeReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Header:”; `onAction`: `—`; editable=false.
- `envelopeUnwrapProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Profile:”; `onAction`: `—`.
- `envelopePrivateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY----- ...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Unwrap”; `onAction`: `#handleUnwrap`.
- `envelopeUnwrappedKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Recovered key (hex):”; `onAction`: `—`; editable=false.

### [`emv.fxml`](../src/main/resources/fxml/emv.fxml)
Controller declarado: `com.cryptocarver.ui.EMVController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**🔬 EMV TLV Inspector**
- `emvTlvInputArea` — **TextArea**, entrada; etiqueta/prompt: “9F02060000000001005F2A020978”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect EMV TLV”; `onAction`: `#handleInspectTlv`.
- `emvTlvResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “BER-TLV hexadecimal:”; `onAction`: `—`; editable=false.
- `emvDolTemplateField` — **TextField**, entrada; etiqueta/prompt: “DOL template, e.g. 9F02069F1A02”; `onAction`: `—`.
- `emvDolValuesArea` — **TextArea**, entrada; etiqueta/prompt: “9F02=000000000100
9F1A=0978”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Build DOL Data”; `onAction`: `#handleBuildDol`.
- `emvDolResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “CDOL/DDOL Builder”; `onAction`: `—`; editable=false.
**🔑 Session Key Derivation**
- `imkField` — **TextField**, entrada; etiqueta/prompt: “Issuer Master Key (16 bytes)”; `onAction`: `—`.
- `panFieldSession` — **TextField**, entrada; etiqueta/prompt: “Primary Account Number”; `onAction`: `—`.
- `panSeqFieldSession` — **TextField**, entrada; etiqueta/prompt: “00-99”; `onAction`: `—`.
- `iccMethodCombo` — **ComboBox**, entrada; etiqueta/prompt: “Derivation method:”; `onAction`: `—`.
- `emvAtcField` — **TextField**, entrada; etiqueta/prompt: “Application Transaction Counter (2 bytes)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Derive Keys”; `onAction`: `#handleDeriveSessionKey`.
- `sessionKeyResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Derive Session Key (SK)”; `onAction`: `—`; editable=false, visible=false inicial.
**💳 ARQC Generation**
- `skARQCField` — **TextField**, entrada; etiqueta/prompt: “16 bytes Session Key”; `onAction`: `—`.
- `arqcTerminalDataField` — **TextArea**, entrada; etiqueta/prompt: “Paste full terminal data hex string here to use it directly...”; `onAction`: `—`.
- `amountField` — **TextField**, entrada; etiqueta/prompt: “000000000100”; `onAction`: `—`.
- `amountOtherField` — **TextField**, entrada; etiqueta/prompt: “000000000000”; `onAction`: `—`.
- `currencyField` — **TextField**, entrada; etiqueta/prompt: “0978 (EUR)”; `onAction`: `—`.
- `countryField` — **TextField**, entrada; etiqueta/prompt: “0724 (ES)”; `onAction`: `—`.
- `atcARQCField` — **TextField**, entrada; etiqueta/prompt: “0001”; `onAction`: `—`.
- `tvrField` — **TextField**, entrada; etiqueta/prompt: “0000000000”; `onAction`: `—`.
- `txDateField` — **TextField**, entrada; etiqueta/prompt: “250101”; `onAction`: `—`.
- `txTypeField` — **TextField**, entrada; etiqueta/prompt: “00”; `onAction`: `—`.
- `unField` — **TextField**, entrada; etiqueta/prompt: “Unpredictable Number (4 bytes)”; `onAction`: `—`.
- `iccDataField` — **TextField**, entrada; etiqueta/prompt: “Optional (Tag 9F10?)”; `onAction`: `—`.
- `arqcPaddingMethodCombo` — **ComboBox**, entrada; etiqueta/prompt: “Padding:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate ARQC”; `onAction`: `#handleGenerateARQC`.
- `arqcResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Transaction Data (Individual Fields):”; `onAction`: `—`; editable=false, visible=false inicial.
**🔄 ARQC Validation & ARPC**
- `skARPCField` — **TextField**, entrada; etiqueta/prompt: “16 bytes Session Key”; `onAction`: `—`.
- `arqcField` — **TextField**, entrada; etiqueta/prompt: “8 bytes ARQC”; `onAction`: `—`.
- `arcField` — **TextField**, entrada; etiqueta/prompt: “e.g. 3030 (Approved)”; `onAction`: `—`.
- `csuField` — **TextField**, entrada; etiqueta/prompt: “4 bytes Card Status Update”; `onAction`: `—`.
- `propAuthDataField` — **TextField**, entrada; etiqueta/prompt: “Proprietary Authentication Data (Hex)”; `onAction`: `—`.
- `arpcMethodCombo` — **ComboBox**, entrada; etiqueta/prompt: “Method:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate ARPC”; `onAction`: `#handleGenerateARPC`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify ARQC (Sim)”; `onAction`: `#handleVerifyARQC`.
- `arpcResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “arpcResultArea”; `onAction`: `—`; editable=false, visible=false inicial.
**🔐 Secure Messaging (Issuer Script)**
- `smSchemeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Scheme:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Example”; `onAction`: `#handleSmLoadExample`.
- `smMkSmiField` — **TextField**, entrada; etiqueta/prompt: “Issuer MAC master key, 16 bytes”; `onAction`: `—`.
- `smMkSmcField` — **TextField**, entrada; etiqueta/prompt: “Issuer encryption master key, 16 bytes”; `onAction`: `—`.
- `smPanSeqField` — **TextField**, entrada; etiqueta/prompt: “16 digits of PAN and sequence number”; `onAction`: `—`.
- `smUdkSmiField` — **TextField**, entrada; etiqueta/prompt: “Card MAC key, 16 bytes”; `onAction`: `—`.
- `smUdkSmcField` — **TextField**, entrada; etiqueta/prompt: “Card encryption key, 16 bytes”; `onAction`: `—`.
- `smAcField` — **TextField**, entrada; etiqueta/prompt: “Application cryptogram, 8 bytes”; `onAction`: `—`.
- `smCommandNumberField` — **TextField**, entrada; etiqueta/prompt: “0”; `onAction`: `—`.
- `smAtcField` — **TextField**, entrada; etiqueta/prompt: “2 bytes”; `onAction`: `—`.
- `smSkMacField` — **TextField**, entrada; etiqueta/prompt: “Filled by Derive Session Keys”; `onAction`: `—`.
- `smSkEncField` — **TextField**, entrada; etiqueta/prompt: “Filled by Derive Session Keys”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Derive Session Keys”; `onAction`: `#handleSmDeriveSessionKeys`.
- `smPinField` — **PasswordField**, entrada; etiqueta/prompt: “4 to 12 digits”; `onAction`: `—`.
- `smUdkAField` — **TextField**, entrada; etiqueta/prompt: “Card key the Visa PIN block is bound to, 16 bytes”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encipher PIN”; `onAction`: `#handleSmEncipherPin`.
- `smHeaderField` — **TextField**, entrada; etiqueta/prompt: “5 bytes, e.g. 8424000210”; `onAction`: `—`.
- `smDataField` — **TextField**, entrada; etiqueta/prompt: “Filled by Encipher PIN”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate MAC”; `onAction`: `#handleSmGenerateMac`.
- `smResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “3. Command MAC”; `onAction`: `—`; editable=false, visible=false inicial.
**📱 Visa HCE**
- `—` — **Button**, acción; etiqueta/prompt: “Load Example”; `onAction`: `#handleHceLoadExample`.
- `hceUdkField` — **TextField**, entrada; etiqueta/prompt: “16 bytes of card key in hex”; `onAction`: `—`.
- `hceYearField` — **TextField**, entrada; etiqueta/prompt: “1 or 2 decimal digits”; `onAction`: `—`.
- `hceHoursField` — **TextField**, entrada; etiqueta/prompt: “4 decimal digits”; `onAction`: `—`.
- `hceCounterField` — **TextField**, entrada; etiqueta/prompt: “2 decimal digits”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Derive LUK”; `onAction`: `#handleHceLuk`.
- `hceMsdLukField` — **TextField**, entrada; etiqueta/prompt: “16-byte LUK in hex; filled by step 1”; `onAction`: `—`.
- `hceMsdAtcField` — **TextField**, entrada; etiqueta/prompt: “2 bytes in hex”; `onAction`: `—`.
- `hceDeviceTypeField` — **TextField**, entrada; etiqueta/prompt: “8 bytes in hex”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate MSD”; `onAction`: `#handleHceMsd`.
- `hceQvsdcLukField` — **TextField**, entrada; etiqueta/prompt: “16-byte LUK in hex; filled by step 1”; `onAction`: `—`.
- `hceAmountField` — **TextField**, entrada; etiqueta/prompt: “6 bytes in hex”; `onAction`: `—`.
- `hceOtherAmountField` — **TextField**, entrada; etiqueta/prompt: “6 bytes in hex (other amount)”; `onAction`: `—`.
- `hceCountryField` — **TextField**, entrada; etiqueta/prompt: “2 bytes in hex (country)”; `onAction`: `—`.
- `hceTvrField` — **TextField**, entrada; etiqueta/prompt: “5 bytes in hex”; `onAction`: `—`.
- `hceCurrencyField` — **TextField**, entrada; etiqueta/prompt: “2 bytes in hex (currency)”; `onAction`: `—`.
- `hceDateField` — **TextField**, entrada; etiqueta/prompt: “3 bytes in hex”; `onAction`: `—`.
- `hceTypeField` — **TextField**, entrada; etiqueta/prompt: “1 byte in hex”; `onAction`: `—`.
- `hceUnField` — **TextField**, entrada; etiqueta/prompt: “4 bytes in hex”; `onAction`: `—`.
- `hceAipField` — **TextField**, entrada; etiqueta/prompt: “2 bytes in hex (AIP)”; `onAction`: `—`.
- `hceQvsdcAtcField` — **TextField**, entrada; etiqueta/prompt: “2 bytes in hex”; `onAction`: `—`.
- `hceCvrField` — **TextField**, entrada; etiqueta/prompt: “4 bytes in hex (CVR)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate qVSDC”; `onAction`: `#handleHceQvsdc`.
- `hceResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Chip data”; `onAction`: `—`; editable=false, visible=false inicial.
**💾 Mastercard Data Storage**
- `—` — **Button**, acción; etiqueta/prompt: “Load Example”; `onAction`: `#handleDsLoadExample`.
- `dsIdField` — **TextField**, entrada; etiqueta/prompt: “At least 6 bytes in hex”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Derive DSPK”; `onAction`: `#handleDsDspk`.
- `dsOperatorIdField` — **TextField**, entrada; etiqueta/prompt: “8-byte operator ID in hex”; `onAction`: `—`.
- `dsInputField` — **TextField**, entrada; etiqueta/prompt: “8-byte DS input in hex”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate digest”; `onAction`: `#handleDsDigest`.
- `dsSummary1Field` — **TextField**, entrada; etiqueta/prompt: “8 bytes in hex”; `onAction`: `—`.
- `dsAmountField` — **TextField**, entrada; etiqueta/prompt: “12 digits”; `onAction`: `—`.
- `dsCurrencyField` — **TextField**, entrada; etiqueta/prompt: “3 digits”; `onAction`: `—`.
- `dsRcpField` — **TextField**, entrada; etiqueta/prompt: “1 byte in hex”; `onAction`: `—`.
- `dsGacField` — **TextField**, entrada; etiqueta/prompt: “01 first, 02 second”; `onAction`: `—`.
- `dsDsUnField` — **TextField**, entrada; etiqueta/prompt: “4 bytes in hex”; `onAction`: `—`.
- `dsUnField` — **TextField**, entrada; etiqueta/prompt: “4 bytes in hex”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate summary”; `onAction`: `#handleDsSummary`.
- `dsResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “3. Summary (OWHF1)”; `onAction`: `—`; editable=false, visible=false inicial.
**ICC Dynamic Number**
- `—` — **Button**, acción; etiqueta/prompt: “Load Example”; `onAction`: `#handleIccDnLoadExample`.
- `dnMkField` — **PasswordField**, entrada; etiqueta/prompt: “MK-DN:”; `onAction`: `—`.
- `dnPanField` — **TextField**, entrada; etiqueta/prompt: “PAN field (16 digits):”; `onAction`: `—`.
- `dnAtcField` — **TextField**, entrada; etiqueta/prompt: “ATC:”; `onAction`: `—`.
- `dnUnField` — **TextField**, entrada; etiqueta/prompt: “UN:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate dynamic number”; `onAction`: `#handleIccDnCalculate`.
- `dnResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Calculate the Mastercard ICC Dynamic Number from MK-DN, PAN/PSN, ATC and UN.”; `onAction`: `—`; editable=false, visible=false inicial.
**CAP Token**
- `—` — **Button**, acción; etiqueta/prompt: “Load Example”; `onAction`: `#handleCapLoadExample`.
- `capIpbField` — **TextField**, entrada; etiqueta/prompt: “IPB:”; `onAction`: `—`.
- `capIafField` — **TextField**, entrada; etiqueta/prompt: “IAF:”; `onAction`: `—`.
- `capPanSnField` — **TextField**, entrada; etiqueta/prompt: “PAN sequence number:”; `onAction`: `—`.
- `capCidField` — **TextField**, entrada; etiqueta/prompt: “CID:”; `onAction`: `—`.
- `capAtcField` — **TextField**, entrada; etiqueta/prompt: “ATC:”; `onAction`: `—`.
- `capAcField` — **TextField**, entrada; etiqueta/prompt: “AC:”; `onAction`: `—`.
- `capIadField` — **TextField**, entrada; etiqueta/prompt: “IAD:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate CAP token”; `onAction`: `#handleCapCalculate`.
- `capResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Compresses token data with the IPB. The PAN sequence number is included only when IAF bit 40 is set.”; `onAction`: `—`; editable=false, visible=false inicial.
**🎞 Track 2 Data › Encode**
- `panTrack2Field` — **TextField**, entrada; etiqueta/prompt: “Primary Account Number”; `onAction`: `—`.
- `expiryTrack2Field` — **TextField**, entrada; etiqueta/prompt: “2512”; `onAction`: `—`.
- `serviceCodeFieldTrack2` — **TextField**, entrada; etiqueta/prompt: “201”; `onAction`: `—`.
- `track2DiscretionaryInputField` — **TextField**, entrada; etiqueta/prompt: “Optional”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encode Track 2”; `onAction`: `#handleEncodeTrack2`.
**🎞 Track 2 Data › Decode**
- `track2InputField` — **TextField**, entrada; etiqueta/prompt: “Paste Track 2 data”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Decode Track 2”; `onAction`: `#handleDecodeTrack2`.
**🎞 Track 2 Data**
- `track2ResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “track2ResultArea”; `onAction`: `—`; editable=false, visible=false inicial.
**🧾 Offline Data Authentication (SDA/DDA/CDA)**
- `—` — **Button**, acción; etiqueta/prompt: “Issue Test Card”; `onAction`: `#handleOdaIssueTestCard`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear ODA”; `onAction`: `#handleOdaClear`.
- `odaCaModulusArea` — **TextArea**, entrada; etiqueta/prompt: “Terminal CA key for this RID and index (8F)”; `onAction`: `—`.
- `odaCaExponentField` — **TextField**, entrada; etiqueta/prompt: “03 or 010001”; `onAction`: `—`.
- `odaIssuerCertificateArea` — **TextArea**, entrada; etiqueta/prompt: “As long as the CA modulus”; `onAction`: `—`.
- `odaIssuerRemainderField` — **TextField**, entrada; etiqueta/prompt: “Only when the key did not fit”; `onAction`: `—`.
- `odaIssuerExponentField` — **TextField**, entrada; etiqueta/prompt: “03 or 010001”; `onAction`: `—`.
- `odaIccCertificateArea` — **TextArea**, entrada; etiqueta/prompt: “As long as the issuer modulus”; `onAction`: `—`.
- `odaIccRemainderField` — **TextField**, entrada; etiqueta/prompt: “Only when the key did not fit”; `onAction`: `—`.
- `odaIccExponentField` — **TextField**, entrada; etiqueta/prompt: “03 or 010001”; `onAction`: `—`.
- `odaStaticDataArea` — **TextArea**, entrada; etiqueta/prompt: “AFL records in the order they were read, then the AIP if tag 9F4A asks for it”; `onAction`: `—`.
- `odaPanField` — **TextField**, entrada; etiqueta/prompt: “Checked against the issuer identifier and the ICC certificate”; `onAction`: `—`.
- `odaSsadArea` — **TextArea**, entrada; etiqueta/prompt: “As long as the issuer modulus”; `onAction`: `—`.
- `odaSdadArea` — **TextArea**, entrada; etiqueta/prompt: “As long as the ICC modulus”; `onAction`: `—`.
- `odaTerminalDataField` — **TextField**, entrada; etiqueta/prompt: “DDA hashes the whole DDOL; CDA hashes only tag 9F37”; `onAction`: `—`.
- `odaCidField` — **TextField**, entrada; etiqueta/prompt: “From the GENERATE AC response, for the CDA cross-check”; `onAction`: `—`.
- `odaTransactionDataArea` — **TextArea**, entrada; etiqueta/prompt: “PDOL values, CDOL1 values, then the tags, lengths and values the card returned except 9F4B”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Recover Keys”; `onAction`: `#handleOdaRecoverKeys`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify SDA”; `onAction`: `#handleOdaVerifySda`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify DDA”; `onAction`: `#handleOdaVerifyDda`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify CDA”; `onAction`: `#handleOdaVerifyCda`.
- `odaResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Signatures”; `onAction`: `—`; editable=false.

### [`generic.fxml`](../src/main/resources/fxml/generic.fxml)
Controller declarado: `com.cryptocarver.ui.GenericController`.

**🧩 Process Designer**
- `—` — **Button**, acción; etiqueta/prompt: “Open Process Designer”; `onAction`: `#handleLaunchProcessDesigner`.
**🔐 Hashing**
- `hashTemplateCombo` — **ComboBox**, entrada; etiqueta/prompt: “Select a template...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Apply”; `onAction`: `#handleApplyHashTemplate`.
- `—` — **MenuButton**, acción; etiqueta/prompt: “Manage Template”; `onAction`: `—`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Save Current as Personal Template...”; `onAction`: `#handleSaveHashTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Export Selected Template...”; `onAction`: `#handleExportHashTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Import Template File...”; `onAction`: `#handleImportHashTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Delete Selected Personal Template”; `onAction`: `#handleDeleteHashTemplate`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleResetHashDefaults`.
- `hashAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `hashInputArea` — **TextArea**, entrada; etiqueta/prompt: “Input:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate Hash”; `onAction`: `#handleCalculateHash`.
- `hashOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output:”; `onAction`: `—`; editable=false.
**📦 Batch Runner**
- `batchInputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Input:”; `onAction`: `—`.
- `batchOperationCombo` — **ComboBox**, entrada; etiqueta/prompt: “Operation:”; `onAction`: `—`.
- `batchColumnField` — **TextField**, entrada; etiqueta/prompt: “input”; `onAction`: `—`.
- `batchOutputColumnField` — **TextField**, entrada; etiqueta/prompt: “result”; `onAction`: `—`.
- `batchAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `batchRecordEncodingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Encoding:”; `onAction`: `—`.
- `batchKeyField` — **PasswordField**, entrada; etiqueta/prompt: “32 bytes (only in memory)”; `onAction`: `—`.
- `batchIvNonceField` — **TextField**, entrada; etiqueta/prompt: “Hex (Req. for CBC)”; `onAction`: `—`.
- `batchAadField` — **TextField**, entrada; etiqueta/prompt: “Hex (Optional for AEAD)”; `onAction`: `—`.
- `batchCharsetCombo` — **ComboBox**, entrada; etiqueta/prompt: “Charset:”; `onAction`: `—`.
- `batchStopOnErrorCheck` — **CheckBox**, entrada; etiqueta/prompt: “Stop on error”; `onAction`: `—`.
- `batchCompactModeCheck` — **CheckBox**, entrada; etiqueta/prompt: “Compact records”; `onAction`: `—`.
- `batchInputArea` — **TextArea**, entrada; etiqueta/prompt: “CSV: input
hello
or JSON Lines (.jsonl): {"input":"hello"}
{"input":"world"}”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load CSV / JSON Lines…”; `onAction`: `#handleBrowseBatchInput`.
- `—` — **Button**, acción; etiqueta/prompt: “Run Batch”; `onAction`: `#handleRunBatch`.
- `—` — **Button**, acción; etiqueta/prompt: “Dry Run”; `onAction`: `#handleDryRunBatch`.
- `—` — **Button**, acción; etiqueta/prompt: “Cancel”; `onAction`: `#handleCancelBatch`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset”; `onAction`: `#handleResetBatch`.
- `batchExportFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “batchExportFormatCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Export Results…”; `onAction`: `#handleExportBatchResults`.
- `batchResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Ready”; `onAction`: `—`; editable=false.
**🔄 File Conversion**
- `fileInputPathField` — **TextField**, entrada; etiqueta/prompt: “Input File Path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseInputFile`.
- `fileOutputPathField` — **TextField**, entrada; etiqueta/prompt: “Output File Path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseOutputFile`.
- `fileComparePathField` — **TextField**, entrada; etiqueta/prompt: “Second File Path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseCompareFile`.
- `fileInputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “In”; `onAction`: `—`.
- `fileOutputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Out”; `onAction`: `—`.
- `fileEncodingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Encoding:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Convert File”; `onAction`: `#handleConvertFile`.
- `—` — **Button**, acción; etiqueta/prompt: “Compare Files (streaming)”; `onAction`: `#handleCompareFiles`.
- `—` — **Button**, acción; etiqueta/prompt: “Hash File SHA-256 (streaming)”; `onAction`: `#handleHashFileStreaming`.
- `—` — **Button**, acción; etiqueta/prompt: “Preview First 4 KiB”; `onAction`: `#handlePreviewFileStreaming`.
- `fileResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “fileResultArea”; `onAction`: `—`; editable=false.
**🔤 Manual Conversion**
- `manualTemplateCombo` — **ComboBox**, entrada; etiqueta/prompt: “Select a template...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Apply”; `onAction`: `#handleApplyManualTemplate`.
- `—` — **MenuButton**, acción; etiqueta/prompt: “Manage Template”; `onAction`: `—`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Save Current as Personal Template...”; `onAction`: `#handleSaveManualTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Export Selected Template...”; `onAction`: `#handleExportManualTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Import Template File...”; `onAction`: `#handleImportManualTemplate`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Delete Selected Personal Template”; `onAction`: `#handleDeleteManualTemplate`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleResetManualDefaults`.
- `manualInputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Input:”; `onAction`: `—`.
- `manualInputArea` — **TextArea**, entrada; etiqueta/prompt: “manualInputArea”; `onAction`: `—`.
- `ebcdicConversionCheck` — **CheckBox**, entrada; etiqueta/prompt: “Enable EBCDIC conversion”; `onAction`: `—`.
- `ebcdicDirectionCombo` — **ComboBox**, entrada; etiqueta/prompt: “ebcdicDirectionCombo”; `onAction`: `—`; disable=true.
- `ebcdicCodePageCombo` — **ComboBox**, entrada; etiqueta/prompt: “Code page:”; `onAction`: `—`; disable=true.
- `manualOutputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Output:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Convert Data”; `onAction`: `#handleManualConvert`.
- `—` — **Button**, acción; etiqueta/prompt: “UTF-8 → Base64URL”; `onAction`: `#handleEncodeBase64Url`.
- `—` — **Button**, acción; etiqueta/prompt: “Base64URL → UTF-8”; `onAction`: `#handleDecodeBase64Url`.
- `—` — **Button**, acción; etiqueta/prompt: “UTF-8 → Base32”; `onAction`: `#handleEncodeBase32`.
- `—` — **Button**, acción; etiqueta/prompt: “Base32 → UTF-8”; `onAction`: `#handleDecodeBase32`.
- `endianWordSizeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Endian word:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Reverse byte order”; `onAction`: `#handleConvertEndian`.
- `—` — **Button**, acción; etiqueta/prompt: “UTF-8 → URL”; `onAction`: `#handleEncodeUrl`.
- `—` — **Button**, acción; etiqueta/prompt: “URL → UTF-8”; `onAction`: `#handleDecodeUrl`.
- `compressionFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Compression:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Compress”; `onAction`: `#handleCompressData`.
- `—` — **Button**, acción; etiqueta/prompt: “Decompress”; `onAction`: `#handleDecompressData`.
- `—` — **Button**, acción; etiqueta/prompt: “Decimal → BCD”; `onAction`: `#handleEncodeBcd`.
- `—` — **Button**, acción; etiqueta/prompt: “BCD → Decimal”; `onAction`: `#handleDecodeBcd`.
- `—` — **Button**, acción; etiqueta/prompt: “Decimal → COMP-3”; `onAction`: `#handleEncodeComp3`.
- `—` — **Button**, acción; etiqueta/prompt: “COMP-3 → Decimal”; `onAction`: `#handleDecodeComp3`.
- `bitShiftBitsField` — **TextField**, entrada; etiqueta/prompt: “1”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Shift left”; `onAction`: `#handleShiftLeft`.
- `—` — **Button**, acción; etiqueta/prompt: “Shift right”; `onAction`: `#handleShiftRight`.
- `—` — **Button**, acción; etiqueta/prompt: “Extract trace hex”; `onAction`: `#handleExtractTraceHex`.
- `manualOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “manualOutputArea”; `onAction`: `—`; editable=false.
**🆔 UUID Generator**
- `uuidOutputField` — **TextField**, salida/read-only; etiqueta/prompt: “Generate random UUID (v4):”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Generate UUID”; `onAction`: `#handleGenerateUUID`.
**🎲 Random Generator**
- `randomBytesField` — **TextField**, entrada; etiqueta/prompt: “16”; `onAction`: `—`.
- `randomFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Format:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Random”; `onAction`: `#handleGenerateRandom`.
- `randomOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output:”; `onAction`: `—`; editable=false.
**✅ Check Digits**
- `checkDigitAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `checkDigitInput` — **TextField**, entrada; etiqueta/prompt: “Enter number”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate”; `onAction`: `#handleCalculateCheckDigit`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate”; `onAction`: `#handleValidateCheckDigit`.
- `checkDigitOutput` — **TextField**, salida/read-only; etiqueta/prompt: “Output / Validation:”; `onAction`: `—`; editable=false.
**🔢 Modular Arithmetic**
- `modOperationCombo` — **ComboBox**, entrada; etiqueta/prompt: “Operation:”; `onAction`: `—`.
- `modOperandAField` — **TextField**, entrada; etiqueta/prompt: “Operand A (Hex):”; `onAction`: `—`.
- `modOperandBField` — **TextField**, entrada; etiqueta/prompt: “Operand B (Hex):”; `onAction`: `—`.
- `modModulusField` — **TextField**, entrada; etiqueta/prompt: “Modulus (Hex):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Calculate”; `onAction`: `#handleModularCalculate`.
- `modResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Result:”; `onAction`: `—`; editable=false.

### [`history.fxml`](../src/main/resources/fxml/history.fxml)
Controller declarado: `com.cryptocarver.ui.HistoryController`.

**General**
- `visibilityCombo` — **ComboBox**, entrada; etiqueta/prompt: “Values:”; `onAction`: `—`.
- `historyFilterField` — **TextField**, entrada; etiqueta/prompt: “Operation, date or property”; `onAction`: `—`.
- `historyModuleFilterCombo` — **ComboBox**, entrada; etiqueta/prompt: “All modules”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear filters”; `onAction`: `#handleClearHistoryFilter`.
- `compareBtn` — **Button**, acción; etiqueta/prompt: “Compare 2”; `onAction`: `#handleCompareHistory`.
- `—` — **Button**, acción; etiqueta/prompt: “Import Recipe”; `onAction`: `#importOperationRecipe`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear History”; `onAction`: `#handleClearHistory`.
- `openHistoryDetailBtn` — **Button**, acción; etiqueta/prompt: “Open Selected Value”; `onAction`: `#openSelectedHistoryDetail`.
- `addHistoryToShelfBtn` — **Button**, acción; etiqueta/prompt: “Add Output to Shelf”; `onAction`: `#handleAddSelectedHistoryToShelf`.
- `copyHistoryDetailBtn` — **Button**, acción; etiqueta/prompt: “Copy Selected Value”; `onAction`: `#copySelectedHistoryDetail`.
- `exportJsonRecordBtn` — **Button**, acción; etiqueta/prompt: “Export JSON Record”; `onAction`: `#exportSelectedHistoryRecord`.
- `exportVisibleJsonBtn` — **Button**, acción; etiqueta/prompt: “Export Visible JSON”; `onAction`: `#exportVisibleHistoryRecords`.
- `exportReportBtn` — **Button**, acción; etiqueta/prompt: “Export Markdown Report”; `onAction`: `#exportSelectedHistoryReport`.
- `copyReportBtn` — **Button**, acción; etiqueta/prompt: “Copy Markdown Report”; `onAction`: `#copySelectedHistoryReport`.
- `exportRecipeBtn` — **Button**, acción; etiqueta/prompt: “Export JSON Recipe”; `onAction`: `#exportSelectedHistoryRecipe`.
- `historyTable` — **TableView**, entrada; etiqueta/prompt: “historyTable”; `onAction`: `—`.
- `detailsTable` — **TableView**, entrada; etiqueta/prompt: “Operation Details”; `onAction`: `—`.

### [`icsf_batch.fxml`](../src/main/resources/fxml/icsf_batch.fxml)
Controller declarado: `com.cryptocarver.ui.IcsfBatchController`.

**🖥 ICSF / CCA Batch Analysis**
- `icsfBatchOriginCombo` — **ComboBox**, entrada; etiqueta/prompt: “Provenance:”; `onAction`: `—`.
- `icsfBatchFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Reading:”; `onAction`: `—`.
- `icsfBatchInputArea` — **TextArea**, entrada; etiqueta/prompt: “MY.KEY.01|020000000100C000... — one per line, or two host rows, or one stacked block”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Analyze batch”; `onAction`: `#handleAnalyze`.
- `—` — **Button**, acción; etiqueta/prompt: “Load File…”; `onAction`: `#handleLoadFile`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Example Batch”; `onAction`: `#handleLoadSample`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Report (.txt)…”; `onAction`: `#handleSaveText`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Inventory (.csv)…”; `onAction`: `#handleSaveCsv`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
- `icsfBatchDetailCheck` — **CheckBox**, entrada; etiqueta/prompt: “Include the full per-token detail in the saved .txt (turn off for large batches: a whole CKDS runs to tens of megabytes)”; `onAction`: `—`.
**🖥 ICSF / CCA Batch Analysis › Statistics**
- `icsfBatchStatisticsTable` — **TableView**, entrada; etiqueta/prompt: “icsfBatchStatisticsTable”; `onAction`: `—`.
**🖥 ICSF / CCA Batch Analysis › Findings**
- `icsfBatchFindingsTable` — **TableView**, entrada; etiqueta/prompt: “Double-click a finding to filter the inventory by it.”; `onAction`: `—`.
- `icsfBatchFindingDetailArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Select a finding to read what it is and what to do about it.”; `onAction`: `—`; editable=false.
**🖥 ICSF / CCA Batch Analysis › Inventory**
- `icsfBatchFilterField` — **TextField**, entrada; etiqueta/prompt: “label, key type, finding…”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear filter”; `onAction`: `#handleClearFilter`.
- `icsfBatchInventoryTable` — **TableView**, entrada; etiqueta/prompt: “icsfBatchInventoryTable”; `onAction`: `—`.
**🖥 ICSF / CCA Batch Analysis › Report**
- `icsfBatchReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The report, exactly as it is saved, so it can be read without leaving the application.”; `onAction`: `—`; editable=false.

### [`icsf_keywrap.fxml`](../src/main/resources/fxml/icsf_keywrap.fxml)
Controller declarado: `com.cryptocarver.ui.IcsfKeyWrapController`.

**🖥 ICSF / CCA Key Export / Import › Export (CSNBKEX)**
- `icsfExportKeyField` — **TextField**, entrada; etiqueta/prompt: “8, 16 or 24 bytes in the clear”; `onAction`: `—`.
- `icsfExportKekField` — **TextField**, entrada; etiqueta/prompt: “the transport key, in the clear”; `onAction`: `—`.
- `icsfExportTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key type:”; `onAction`: `—`.
- `icsfExportCvField` — **TextField**, entrada; etiqueta/prompt: “blank uses the Table 676 default for the key type”; `onAction`: `—`.
- `icsfExportVariantCombo` — **ComboBox**, entrada; etiqueta/prompt: “KEK variant:”; `onAction`: `—`.
- `icsfExportModeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Cipher mode:”; `onAction`: `—`.
- `icsfExportRnField` — **TextField**, entrada; etiqueta/prompt: “8 bytes for the CSNBKYT verification pattern; blank means zeroes”; `onAction`: `—`.
- `icsfExportHostVersionCheck` — **CheckBox**, entrada; etiqueta/prompt: “Byte 4 as real hosts write it (X'00' even for double and triple keys)”; `onAction`: `—`.
- `icsfExportNocvCheck` — **CheckBox**, entrada; etiqueta/prompt: “Mark the token NOCV”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Export”; `onAction`: `#handleExport`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearExport`.
**🖥 ICSF / CCA Key Export / Import › Import (CSNBKIM)**
- `icsfImportInputField` — **TextField**, entrada; etiqueta/prompt: “a 64-byte token, or a bare 8/16/24-byte cryptogram”; `onAction`: `—`.
- `icsfImportKekField` — **TextField**, entrada; etiqueta/prompt: “the transport key, in the clear”; `onAction`: `—`.
- `icsfImportTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key type:”; `onAction`: `—`.
- `icsfImportCvField` — **TextField**, entrada; etiqueta/prompt: “blank reads it from the token”; `onAction`: `—`.
- `icsfImportVariantCombo` — **ComboBox**, entrada; etiqueta/prompt: “KEK variant:”; `onAction`: `—`.
- `icsfImportModeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Cipher mode:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Import”; `onAction`: `#handleImport`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearImport`.
**🖥 ICSF / CCA Key Export / Import › Inspect (no KEK)**
- `icsfInspectInputArea` — **TextArea**, entrada; etiqueta/prompt: “paste the 64-byte token”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleInspect`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearInspect`.
**🖥 ICSF / CCA Key Export / Import › Resolve scheme**
- `icsfResolveInputField` — **TextField**, entrada; etiqueta/prompt: “Token or cryptogram (hex):”; `onAction`: `—`.
- `icsfResolveKekField` — **TextField**, entrada; etiqueta/prompt: “KEK (hex):”; `onAction`: `—`.
- `icsfResolveExpectedKeyField` — **TextField**, entrada; etiqueta/prompt: “with this the answer is conclusive”; `onAction`: `—`.
- `icsfResolveExpectedKcvField` — **TextField**, entrada; etiqueta/prompt: “3 or 4 bytes, if the key itself is not available”; `onAction`: `—`.
- `icsfResolveTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key type (optional):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Resolve”; `onAction`: `#handleResolve`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearResolve`.
**🖥 ICSF / CCA Key Export / Import**
- `icsfKeyWrapReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The report will appear here.”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Save Report…”; `onAction`: `#handleSaveReport`.
- `—` — **Button**, acción; etiqueta/prompt: “Copy Token”; `onAction`: `#handleCopyToken`.
- `—` — **Button**, acción; etiqueta/prompt: “Copy Key”; `onAction`: `#handleCopyKey`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.

### [`icsf_token.fxml`](../src/main/resources/fxml/icsf_token.fxml)
Controller declarado: `com.cryptocarver.ui.IcsfTokenController`.

**🖥 ICSF / CCA Key Token Analyzer**
- `icsfTokenOriginCombo` — **ComboBox**, entrada; etiqueta/prompt: “Provenance:”; `onAction`: `—`.
- `icsfTokenFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Input format:”; `onAction`: `—`.
- `icsfTokenInputArea` — **TextArea**, entrada; etiqueta/prompt: “01 AF 02 ... — or paste the two host rows, high digits above and low digits below”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Analyze token”; `onAction`: `#handleAnalyze`.
- `—` — **Button**, acción; etiqueta/prompt: “Load .bin File…”; `onAction`: `#handleLoadBinary`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Report…”; `onAction`: `#handleSaveReport`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
- `—` — **Button**, acción; etiqueta/prompt: “AES fixed”; `onAction`: `#handleSampleAesFixed`.
- `—` — **Button**, acción; etiqueta/prompt: “AES CIPHER var-length”; `onAction`: `#handleSampleVariableCipher`.
- `—` — **Button**, acción; etiqueta/prompt: “3DES K1|K2|K1”; `onAction`: `#handleSampleDesTriple`.
- `—` — **Button**, acción; etiqueta/prompt: “PKA RSA 2048”; `onAction`: `#handleSamplePkaRsa`.
- `icsfTokenSummaryTable` — **TableView**, entrada; etiqueta/prompt: “Summary card:”; `onAction`: `—`.
- `icsfTokenDetailArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The full analysis will appear here.”; `onAction`: `—`; editable=false.

### [`jose.fxml`](../src/main/resources/fxml/jose.fxml)
Controller declarado: `com.cryptocarver.ui.JOSEController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**Generate JWT**
- `jwtAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “jwtAlgoCombo”; `onAction`: `—`.
- `jwtTemplateCombo` — **ComboBox**, entrada; etiqueta/prompt: “Select Template...”; `onAction`: `—`.
- `jwsSerializationCombo` — **ComboBox**, entrada; etiqueta/prompt: “Compact”; `onAction`: `—`.
- `jwsUnencodedPayloadCheck` — **CheckBox**, entrada; etiqueta/prompt: “Unencoded Payload (b64=false)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Import from File...”; `onAction`: `#handleLoadJWTKey`.
- `jwtKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `jwtSecretFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Secret Format:”; `onAction`: `—`.
- `jwtKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Enter secret (for HMAC) or paste Private Key PEM (for RSA/EC)...”; `onAction`: `—`.
**Generate JWT › Signer 2 (Only for General JSON)**
- `jwtAlgo2Combo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm 2:”; `onAction`: `—`.
- `jwtKeyArea2` — **TextArea**, entrada; etiqueta/prompt: “Optional. Required if using General JSON with two signers...”; `onAction`: `—`.
**Generate JWT › Claims Builder (Optional)**
- `jwtIssField` — **TextField**, entrada; etiqueta/prompt: “e.g. auth.server”; `onAction`: `—`.
- `jwtSubField` — **TextField**, entrada; etiqueta/prompt: “e.g. user_123”; `onAction`: `—`.
- `jwtAudField` — **TextField**, entrada; etiqueta/prompt: “e.g. my-api”; `onAction`: `—`.
- `jwtExpField` — **TextField**, entrada; etiqueta/prompt: “1”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Apply to Payload”; `onAction`: `#handleApplyJWTClaims`.
- `jwtAllowedAlgorithmsField` — **TextField**, entrada; etiqueta/prompt: “RS256, ES256”; `onAction`: `—`.
- `jwtExpectedTypeField` — **TextField**, entrada; etiqueta/prompt: “at+jwt”; `onAction`: `—`.
- `jwtExpectedContentTypeField` — **TextField**, entrada; etiqueta/prompt: “jwtExpectedContentTypeField”; `onAction`: `—`.
- `jwtExpectedNonceField` — **TextField**, entrada; etiqueta/prompt: “jwtExpectedNonceField”; `onAction`: `—`.
- `jwtAccessTokenField` — **TextField**, entrada; etiqueta/prompt: “jwtAccessTokenField”; `onAction`: `—`.
- `jwtAuthorizationCodeField` — **TextField**, entrada; etiqueta/prompt: “jwtAuthorizationCodeField”; `onAction`: `—`.
- `jwtExpectedJktField` — **TextField**, entrada; etiqueta/prompt: “jwtExpectedJktField”; `onAction`: `—`.
- `jwtExpectedX5tField` — **TextField**, entrada; etiqueta/prompt: “jwtExpectedX5tField”; `onAction`: `—`.
- `jwtUnderstoodCritField` — **TextField**, entrada; etiqueta/prompt: “b64”; `onAction`: `—`.
- `jwtIgnoreCritCheck` — **CheckBox**, entrada; etiqueta/prompt: “jwtIgnoreCritCheck”; `onAction`: `—`.
- `jwtRfc9068Check` — **CheckBox**, entrada; etiqueta/prompt: “jwtRfc9068Check”; `onAction`: `—`.
**Generate JWT**
- `jwtPayloadArea` — **TextArea**, entrada; etiqueta/prompt: “{"sub":"1234567890", "name":"John Doe", "iat":1516239022}”; `onAction`: `—`.
- `jwtProtectedHeaderArea` — **TextArea**, entrada; etiqueta/prompt: “{"kid":"key-1", "jku":"https://example.invalid/jwks.json"}”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Signed JWT”; `onAction`: `#handleGenerateSignedJWT`.
- `jwtOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Generated JWT will appear here...”; `onAction`: `—`; editable=false.
**Validate & Decode JWT**
- `jwtValidateTokenArea` — **TextArea**, entrada; etiqueta/prompt: “Paste JWT here...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Import from File...”; `onAction`: `#handleLoadJWTValidateKey`.
- `jwtValidateKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `jwtValidateSecretFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Secret Format:”; `onAction`: `—`.
- `jwtValidateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Enter secret (for HMAC) or paste Public Key PEM (for RSA/EC)...”; `onAction`: `—`.
**Validate & Decode JWT › Advanced Validation Settings**
- `jwtExpectedIssField` — **TextField**, entrada; etiqueta/prompt: “e.g. auth.server”; `onAction`: `—`.
- `jwtExpectedAudField` — **TextField**, entrada; etiqueta/prompt: “e.g. my-api”; `onAction`: `—`.
- `jwtClockSkewField` — **TextField**, entrada; etiqueta/prompt: “60”; `onAction`: `—`.
- `jwtCheckExpiryCheck` — **CheckBox**, entrada; etiqueta/prompt: “Check Expiration (exp/nbf/iat)”; `onAction`: `—`.
- `jwtOidcStrictCheck` — **CheckBox**, entrada; etiqueta/prompt: “Strict OIDC Profile”; `onAction`: `—`.
- `jwtAcceptNoneCheck` — **CheckBox**, entrada; etiqueta/prompt: “jwtAcceptNoneCheck”; `onAction`: `—`.
- `jwtTrustHeaderKeyCheck` — **CheckBox**, entrada; etiqueta/prompt: “jwtTrustHeaderKeyCheck”; `onAction`: `—`.
**Validate & Decode JWT**
- `jwtTrustAnchorsArea` — **TextArea**, entrada; etiqueta/prompt: “x5c trust anchors (PEM):”; `onAction`: `—`.
- `jwtCertificateDateField` — **TextField**, entrada; etiqueta/prompt: “Certificate validation date (ISO-8601, empty = now):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify & Decode”; `onAction`: `#handleValidateJWT`.
- `jwtDecodedHeaderArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Header JSON...”; `onAction`: `—`; editable=false.
- `jwtDecodedPayloadArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Payload JSON...”; `onAction`: `—`; editable=false.
- `jwtFindingsArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Issuer, audience, time and critical-header findings...”; `onAction`: `—`; editable=false.
**JWS Detached (External Payload)**
- `detachedAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “HS256 / RS256 / ES256”; `onAction`: `—`.
- `detachedSerializationCombo` — **ComboBox**, entrada; etiqueta/prompt: “Compact”; `onAction`: `—`.
- `detachedUnencodedCheck` — **CheckBox**, entrada; etiqueta/prompt: “b64=false”; `onAction`: `—`.
- `detachedPayloadArea` — **TextArea**, entrada; etiqueta/prompt: “External Payload:”; `onAction`: `—`.
- `detachedSigningKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Secret or Private PEM”; `onAction`: `—`.
- `detachedVerificationKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Same Secret or Public PEM”; `onAction`: `—`.
- `detachedSecretFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Secret Format:”; `onAction`: `—`.
- `detachedProtectedHeaderSecretArea` — **TextArea**, entrada; etiqueta/prompt: “kid, typ, cty, x5c, x5t, x5t#S256, public jwk”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Detached JWS”; `onAction`: `#handleGenerateDetachedJWS`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify Detached JWS”; `onAction`: `#handleVerifyDetachedJWS`.
- `detachedTokenArea` — **TextArea**, entrada; etiqueta/prompt: “header..signature”; `onAction`: `—`.
**JWK Tools**
- `jwkKeyTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “jwkKeyTypeCombo”; `onAction`: `—`.
- `jwkCurveCombo` — **ComboBox**, entrada; etiqueta/prompt: “OKP curve:”; `onAction`: `—`.
- `jwkKeyIdField` — **TextField**, entrada; etiqueta/prompt: “Optional / Auto”; `onAction`: `—`.
- `jwkUseCombo` — **ComboBox**, entrada; etiqueta/prompt: “jwkUseCombo”; `onAction`: `—`.
- `jwkKeyOpsField` — **TextField**, entrada; etiqueta/prompt: “sign, verify”; `onAction`: `—`.
- `jwkSecretFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Secret Format:”; `onAction`: `—`.
- `jwkInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste PEM Key or JWK JSON...”; `onAction`: `—`.
- `pemToJwkBtn` — **Button**, acción; etiqueta/prompt: “PEM -> JWK”; `onAction`: `#handlePemToJwk`.
- `jwkToPemBtn` — **Button**, acción; etiqueta/prompt: “JWK -> PEM”; `onAction`: `#handleJwkToPem`.
- `—` — **Button**, acción; etiqueta/prompt: “Calc Thumbprint”; `onAction`: `#handleCalculateThumbprint`.
- `jwkInspectMetadataBtn` — **Button**, acción; etiqueta/prompt: “Inspect Metadata”; `onAction`: `#handleInspectJwkMetadata`.
- `jwkOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Result...”; `onAction`: `—`; editable=false.
**JWK Set (JWKS)**
- `—` — **Button**, acción; etiqueta/prompt: “New JWKS”; `onAction`: `#handleNewJWKS`.
- `—` — **Button**, acción; etiqueta/prompt: “Load JWKS...”; `onAction`: `#handleLoadJWKS`.
- `jwksSecretArea` — **TextArea**, entrada; etiqueta/prompt: “{ "keys": [] }”; `onAction`: `—`.
**JWK Set (JWKS) › Key Rotation & Operations**
- `jwksRotateAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “jwksRotateAlgoCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Add New Key to Set”; `onAction`: `#handleRotateKey`.
- `—` — **Button**, acción; etiqueta/prompt: “Show Public JWKS Only”; `onAction`: `#handleExportPublicJWKS`.
**Encrypt**
- `jweKeyAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “jweKeyAlgoCombo”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_key_algorithm`.
- `jweContentAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Content:”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_content_encryption`.
- `jweCompressCheck` — **CheckBox**, entrada; etiqueta/prompt: “Compress (DEF)”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_compression`.
- `jweKeyFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “jweKeyFormatCombo”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_secret_format`.
- `jwePbes2IterField` — **TextField**, entrada; etiqueta/prompt: “PBES2 Iterations:”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_pbes2_iterations`.
- `jwePublicKeyImportBtn` — **Button**, acción; etiqueta/prompt: “Import from File...”; `onAction`: `#handleLoadJWEPublicKey`; botón `?` runtime `jweHelp_import_public_key_public_key`.
- `jwePubKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`; botón `?` runtime `jweHelp_use_public_key_shelf_public_key`.
- `jwePublicKeyArea` — **TextArea**, entrada; etiqueta/prompt: “RSA/EC public key (PEM, certificate or JWK) · JWKS with alg per key for several recipients (General JSON) · shared secret (AES-KW, dir) · password (PBES2)”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_public_key`.
- `jwePayloadArea` — **TextArea**, entrada; etiqueta/prompt: “Secret Data...”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_payload`.
- `jweSerializationCombo` — **ComboBox**, entrada; etiqueta/prompt: “jweSerializationCombo”; `onAction`: `—`; botón `?` runtime `jweHelp_choose_serialization_serialization`.
- `jweAadField` — **TextField**, entrada; etiqueta/prompt: “JSON serializations only · UTF-8 text”; `onAction`: `—`; botón `?` runtime `jweHelp_choose_serialization_aad`.
**Encrypt › Header Parameters (Optional)**
- `jweKidField` — **TextField**, entrada; etiqueta/prompt: “Recipient key identifier”; `onAction`: `—`; botón `?` runtime `jweHelp_select_recipients_kid`.
- `jweTypField` — **TextField**, entrada; etiqueta/prompt: “e.g. JWT”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_typ`.
- `jweCtyField` — **TextField**, entrada; etiqueta/prompt: “JWT when the payload is a signed token”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_cty`.
- `jweApuField` — **TextField**, entrada; etiqueta/prompt: “ECDH-ES only · text, sent Base64URL-encoded”; `onAction`: `—`; botón `?` runtime `jweHelp_select_recipients_apu`.
- `jweApvField` — **TextField**, entrada; etiqueta/prompt: “ECDH-ES only · text, sent Base64URL-encoded”; `onAction`: `—`; botón `?` runtime `jweHelp_select_recipients_apv`.
- `jweCustomHeaderArea` — **TextArea**, entrada; etiqueta/prompt: “{"x5t#S256": "...", "iss": "..."}”; `onAction`: `—`; botón `?` runtime `jweHelp_encrypt_custom_header`.
**Encrypt**
- `jweEncryptActionBtn` — **Button**, acción; etiqueta/prompt: “Encrypt & Generate JWE”; `onAction`: `#handleGenerateJWE`; botón `?` runtime `jweHelp_encrypt_action`.
- `jweOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Generated JWE...”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_copy_result_output`.
**Decrypt**
- `jweInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste compact or JSON-serialized JWE here...”; `onAction`: `—`; botón `?` runtime `jweHelp_decrypt_input`.
- `jwePrivateKeyImportBtn` — **Button**, acción; etiqueta/prompt: “Import from File...”; `onAction`: `#handleLoadJWEPrivateKey`; botón `?` runtime `jweHelp_import_private_key_private_key`.
- `jwePrivKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`; botón `?` runtime `jweHelp_use_private_key_shelf_private_key`.
- `jwePrivateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “RSA/EC private key (PEM or JWK) · shared secret (AES-KW, dir) · password (PBES2)”; `onAction`: `—`; botón `?` runtime `jweHelp_decrypt_private_key`.
- `jweDecryptKeyFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Secret Format:”; `onAction`: `—`; botón `?` runtime `jweHelp_decrypt_decrypt_key_format`.
- `jweDecryptActionBtn` — **Button**, acción; etiqueta/prompt: “Decrypt JWE”; `onAction`: `#handleDecryptJWE`; botón `?` runtime `jweHelp_decrypt_action`.
- `jweDecodedHeaderArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Header JSON...”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_inspect_breakdown_decoded_header`.
- `jweDecodedPayloadArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Payload...”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_inspect_breakdown_decoded_payload`.
**Decrypt › Visual Breakdown (Layer 6)**
- `jweHeaderArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Protected Header:”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_inspect_breakdown_breakdown_header`.
- `jweEncryptedKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Encrypted Key:”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_inspect_breakdown_breakdown_encrypted_key`.
- `jweDecryptedKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “CEK status:”; `onAction`: `—`; editable=false; CEK no se recupera ni se muestra automáticamente; botón `?` runtime `jweHelp_inspect_breakdown_breakdown_decrypted_cek`.
- `jweIVArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Initialization Vector (IV):”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_inspect_breakdown_breakdown_content_iv`.
- `jweCiphertextArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Ciphertext (Truncated):”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_inspect_breakdown_breakdown_ciphertext`.
- `jweAuthTagArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Authentication Tag:”; `onAction`: `—`; editable=false; botón `?` runtime `jweHelp_inspect_breakdown_breakdown_content_tag`.

**Acciones de resultado compartidas en `main-view-modern.fxml`**
- `resultCopyButton` / `resultCopyHelpButton` — copia global del resultado; el `?` contextual solo se muestra en JWE y abre `copy_result`.
- `resultExpandButton` / `resultExpandHelpButton` — ampliación global del resultado; el `?` contextual solo se muestra en JWE y abre `expand_result`.
- No hay botón de exportación JWE propio en esta pantalla; el flujo global de resultado/portapapeles se clasifica en su ficha transversal. Los botones auxiliares de importación de claves sí tienen ayuda específica.
- Seguimiento del acordeón: expandir `Encrypt` o `Decrypt` actualiza la acción del contexto solo cuando el modo de ayuda contextual está activo y JWE es la ruta actual. Expandir o pulsar el panel no abre la ayuda ni selecciona la pestaña de ayuda; los botones `?` y los puntos de entrada explícitos conservan esa función.
- El contexto de `Decrypt` no muestra `alg`, `enc`, serialización ni AAD actuales del generador, no analiza el token recibido al abrir la ayuda y no publica entradas ni resultados. El selector `jweDecryptKeyFormatCombo` es local al descifrado e independiente de `jweKeyFormatCombo`.
**Nested (Sign + Encrypt)**
- `nestedSignAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “nestedSignAlgoCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Import...”; `onAction`: `#handleLoadNestedSigningKey`.
- `nestedSigningKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Private Key or Secret...”; `onAction`: `—`.
- `nestedPayloadArea` — **TextArea**, entrada; etiqueta/prompt: “{ 'sub': 'user' }...”; `onAction`: `—`.
- `nestedSecretFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Secret Format:”; `onAction`: `—`.
- `nestedKeyAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “nestedKeyAlgoCombo”; `onAction`: `—`.
- `nestedContentAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Content Algo:”; `onAction`: `—`.
- `nestedCompressCheck` — **CheckBox**, entrada; etiqueta/prompt: “Compress (DEF)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Import...”; `onAction`: `#handleLoadNestedEncryptionKey`.
- `nestedEncryptionKeyArea` — **TextArea**, entrada; etiqueta/prompt: “RSA Public Key (Encryption) / RSA Private Key (Decryption)...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Nested JWT”; `onAction`: `#handleGenerateNestedJWT`.
- `—` — **Button**, acción; etiqueta/prompt: “Decrypt & Verify Nested JWT”; `onAction`: `#handleVerifyNestedJWT`.
- `nestedOutputArea` — **TextArea**, entrada; etiqueta/prompt: “Nested Token...”; `onAction`: `—`.
- `nestedPayloadOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Decrypted & Verified Payload...”; `onAction`: `—`; editable=false.
**General**
- `jwaTable` — **TableView**, entrada; etiqueta/prompt: “Supported Algorithms Registry”; `onAction`: `—`.
- `—` — **TableView**, entrada; etiqueta/prompt: “sin etiqueta/prompt en FXML”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Analyze Token”; `onAction`: `#handleInspectToken`.
- `—` — **Button**, acción; etiqueta/prompt: “Expand Report”; `onAction`: `#handleOpenExpandedInspectorReport`.
- `—` — **Button**, acción; etiqueta/prompt: “Copy Report”; `onAction`: `#handleCopyInspectorOutput`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearInspector`.
- `inspectorInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste JWS (eyJ...) or JWE (ey...) here...”; `onAction`: `—`.

### [`key_certificate_workbench.fxml`](../src/main/resources/fxml/key_certificate_workbench.fxml)
Controller declarado: `com.cryptocarver.ui.KeyCertificateWorkbenchController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleLoadFile`.
- `—` — **Button**, acción; etiqueta/prompt: “Load from Clipboard Shelf”; `onAction`: `#handleLoadFromShelf`.
- `storeTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “storeTypeCombo”; `onAction`: `—`.
- `workbenchPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Password (optional)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Detect & Parse”; `onAction`: `#handleParse`.
- `workbenchInputArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----... or {...} or Base64/Hex DER”; `onAction`: `—`.
- `keystoreTable` — **TableView**, entrada; etiqueta/prompt: “Detection & Validation Results”; `onAction`: `—`.
- `convertToCombo` — **ComboBox**, entrada; etiqueta/prompt: “Convert to:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Convert”; `onAction`: `#handleConvert`.
- `validationSecondaryInput` — **TextField**, entrada; etiqueta/prompt: “Secondary input for validation...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate Pair/Chain”; `onAction`: `#handleValidate`.
- `workbenchOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Send to Clipboard Shelf”; `onAction`: `#handleSendToShelf`.
- `—` — **Button**, acción; etiqueta/prompt: “Copy to Clipboard”; `onAction`: `#handleCopyOutput`.

### [`keys.fxml`](../src/main/resources/fxml/keys.fxml)
Controller declarado: `com.cryptocarver.ui.KeysController`.

**General**
- `useFourByteKcvCheck` — **CheckBox**, entrada; etiqueta/prompt: “Use 4-byte KCVs (otherwise 3 bytes)”; `onAction`: `#handleKcvLengthToggle`.
**🎛 Key Lab**
- `keyLabSearchField` — **TextField**, entrada; etiqueta/prompt: “Search keys by name or algorithm...”; `onAction`: `—`.
- `keyLabStatusFilterCombo` — **ComboBox**, entrada; etiqueta/prompt: “Search:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Import Metadata”; `onAction`: `#handleImportKeyLabMetadata`.
- `—` — **Button**, acción; etiqueta/prompt: “Export Metadata”; `onAction`: `#handleExportKeyLabMetadata`.
- `keyLabTable` — **TableView**, entrada; etiqueta/prompt: “keyLabTable”; `onAction`: `—`.
**🎛 Key Lab › Add New Key**
- `keyLabNewNameField` — **TextField**, entrada; etiqueta/prompt: “Enter key alias/name...”; `onAction`: `—`.
- `keyLabNewAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `keyLabNewSizeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key Size:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Key”; `onAction`: `#handleKeyLabGenerate`.
- `keyLabImportBytesField` — **PasswordField**, entrada; etiqueta/prompt: “Or paste raw key bytes in hex...”; `onAction`: `—`.
- `keyLabImportBtn` — **Button**, acción; etiqueta/prompt: “Import Key”; `onAction`: `#handleKeyLabImport`.
**🎛 Key Lab**
- `keyLabDetailIdField` — **TextField**, salida/read-only; etiqueta/prompt: “ID/Alias:”; `onAction`: `—`; editable=false.
- `keyLabDetailNameField` — **TextField**, entrada; etiqueta/prompt: “Name:”; `onAction`: `—`.
- `keyLabUsageEncryptCheck` — **CheckBox**, entrada; etiqueta/prompt: “ENCRYPT”; `onAction`: `—`.
- `keyLabUsageDecryptCheck` — **CheckBox**, entrada; etiqueta/prompt: “DECRYPT”; `onAction`: `—`.
- `keyLabUsageMacCheck` — **CheckBox**, entrada; etiqueta/prompt: “MAC”; `onAction`: `—`.
- `keyLabUsageWrapCheck` — **CheckBox**, entrada; etiqueta/prompt: “WRAP”; `onAction`: `—`.
- `keyLabUsageUnwrapCheck` — **CheckBox**, entrada; etiqueta/prompt: “UNWRAP”; `onAction`: `—`.
- `keyLabDetailValueField` — **TextField**, salida/read-only; etiqueta/prompt: “keyLabDetailValueField”; `onAction`: `—`; editable=false.
- `keyLabRevealBtn` — **Button**, acción; etiqueta/prompt: “Reveal Value”; `onAction`: `#handleKeyLabReveal`.
- `—` — **Button**, acción; etiqueta/prompt: “Copy ID”; `onAction`: `#handleKeyLabCopyId`.
- `keyLabUseCipherBtn` — **Button**, acción; etiqueta/prompt: “Use in Symmetric Cipher”; `onAction`: `#handleUseKeyLabInCipher`; disable=true.
- `keyLabUseMacBtn` — **Button**, acción; etiqueta/prompt: “Use in MAC”; `onAction`: `#handleUseKeyLabInMac`; disable=true.
- `—` — **Button**, acción; etiqueta/prompt: “Save Metadata”; `onAction`: `#handleKeyLabSaveMetadata`.
- `keyLabArchiveBtn` — **Button**, acción; etiqueta/prompt: “Archive”; `onAction`: `#handleKeyLabArchive`.
- `—` — **Button**, acción; etiqueta/prompt: “Delete Key”; `onAction`: `#handleKeyLabDelete`.
**🔑 Key Generation**
- `keyTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Type:”; `onAction`: `—`.
- `forceOddParityCheck` — **CheckBox**, entrada; etiqueta/prompt: “Force Odd Parity (DES/3DES)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Key”; `onAction`: `#handleGenerateKey`.
- `saveGeneratedKeyButton` — **Button**, acción; etiqueta/prompt: “Save to Simulated HSM / Key Lab…”; `onAction`: `#handleSaveGeneratedKeyToLab`; disable=true.
- `copyGeneratedKeyButton` — **Button**, acción; etiqueta/prompt: “Copy Key”; `onAction`: `#handleCopyGeneratedKey`.
- `copyGeneratedKcvButton` — **Button**, acción; etiqueta/prompt: “Copy KCV”; `onAction`: `#handleCopyGeneratedKcv`.
- `copyGeneratedSummaryButton` — **Button**, acción; etiqueta/prompt: “Copy Summary”; `onAction`: `#handleCopyGeneratedSummary`.
- `saveGeneratedSummaryButton` — **Button**, acción; etiqueta/prompt: “Save to Key Lab…”; `onAction`: `#handleSaveGeneratedKeyToLab`.
- `openValidationButton` — **Button**, acción; etiqueta/prompt: “Validate / Open Validation & KCV”; `onAction`: `#handleOpenValidationAndKcv`.
- `generatedKeyField` — **TextArea**, salida/read-only; etiqueta/prompt: “Generated key will appear here...”; `onAction`: `—`; editable=false.
**✓ Validation & KCV**
- `keyInputField` — **TextField**, entrada; etiqueta/prompt: “Enter key in hexadecimal...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate & Calculate KCVs”; `onAction`: `#handleValidateKey`.
- `validationResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Validation results will appear here...”; `onAction`: `—`; editable=false, visible=false inicial.
**🔎 Key Material Inspector**
- `keyMaterialInputArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY----- / -----BEGIN PRIVATE KEY----- / -----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect Material”; `onAction`: `#handleInspectKeyMaterial`.
- `keyMaterialReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Paste a PEM public/private key or an X.509 certificate to inspect its fingerprint, size and security hints.”; `onAction`: `—`; editable=false.
**🗄 KeyStore Inspector**
- `keyStoreProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Saved profile”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load”; `onAction`: `#handleLoadKeyStoreProfile`.
- `keyStoreTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “keyStoreTypeCombo”; `onAction`: `—`.
- `keyStorePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Store password”; `onAction`: `—`.
- `keyStoreUnsafeExtractCheck` — **CheckBox**, entrada; etiqueta/prompt: “Unsafe extraction: show exportable private/secret keys in clear (laboratory only)”; `onAction`: `—`.
- `keyStorePathField` — **TextField**, entrada; etiqueta/prompt: “PKCS#12, JKS or JCEKS file path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Browse…”; `onAction`: `#handleChooseKeyStore`.
- `keyStoreProfileNameField` — **TextField**, entrada; etiqueta/prompt: “Profile name (path/type only; no password)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Profile”; `onAction`: `#handleSaveKeyStoreProfile`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect KeyStore”; `onAction`: `#handleInspectKeyStore`.
- `keyStoreReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Lists metadata only; it never exports private or secret keys.”; `onAction`: `—`; editable=false.
**🔐 PKCS#11 Token**
- `pkcs11NameField` — **TextField**, entrada; etiqueta/prompt: “Token profile name”; `onAction`: `—`.
- `pkcs11SlotField` — **TextField**, entrada; etiqueta/prompt: “Slot index”; `onAction`: `—`.
- `pkcs11PinField` — **PasswordField**, entrada; etiqueta/prompt: “User PIN (not stored)”; `onAction`: `—`.
- `pkcs11LibraryField` — **TextField**, entrada; etiqueta/prompt: “Native PKCS#11 library (.dylib, .so or .dll)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Browse…”; `onAction`: `#handleChoosePkcs11Library`.
- `pkcs11ProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Saved Profiles”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Profile”; `onAction`: `#handleSavePkcs11Profile`.
- `—` — **Button**, acción; etiqueta/prompt: “Delete”; `onAction`: `#handleDeletePkcs11Profile`.
- `—` — **Button**, acción; etiqueta/prompt: “Connect & Inspect”; `onAction`: `#handleConnectPkcs11`.
- `—` — **Button**, acción; etiqueta/prompt: “Disconnect”; `onAction`: `#handleDisconnectPkcs11`.
- `pkcs11ReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Token provider and public object metadata will appear here; keys are never exported.”; `onAction`: `—`; editable=false.
- `pkcs11SigningKeyCombo` — **ComboBox**, entrada; etiqueta/prompt: “Private key alias”; `onAction`: `—`.
- `pkcs11SignatureAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “pkcs11SignatureAlgorithmCombo”; `onAction`: `—`.
- `pkcs11DataArea` — **TextArea**, entrada; etiqueta/prompt: “Data to sign (hexadecimal)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Sign Data”; `onAction`: `#handlePkcs11Sign`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify Signature”; `onAction`: `#handlePkcs11Verify`.
- `pkcs11SignatureArea` — **TextArea**, entrada; etiqueta/prompt: “Signature (hexadecimal)”; `onAction`: `—`.
- `pkcs11CertificateAliasCombo` — **ComboBox**, entrada; etiqueta/prompt: “Certificate alias”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Show PEM Chain”; `onAction`: `#handleShowPkcs11Certificate`.
- `—` — **Button**, acción; etiqueta/prompt: “Update Chain”; `onAction`: `#handleUpdatePkcs11CertificateChain`.
- `pkcs11CertificateArea` — **TextArea**, entrada; etiqueta/prompt: “Paste PEM chain here to update, or use Show PEM Chain to view.”; `onAction`: `—`.
- `pkcs11JwtAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Creates a compact JWS with the same selected token private-key alias. Claims must be a JSON object.”; `onAction`: `—`.
- `pkcs11JwtPayloadArea` — **TextArea**, entrada; etiqueta/prompt: “JWT claims JSON, e.g. {"sub":"lab"}”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Create Signed JWT”; `onAction`: `#handleGeneratePkcs11Jwt`.
- `pkcs11JwtOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Compact JWS will appear here”; `onAction`: `—`; editable=false.
- `pkcs11CmsDataArea` — **TextArea**, entrada; etiqueta/prompt: “Content to sign (hexadecimal)”; `onAction`: `—`.
- `pkcs11CmsDetachedCheck` — **CheckBox**, entrada; etiqueta/prompt: “Detached CMS (do not embed content)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Create CMS SignedData”; `onAction`: `#handleGeneratePkcs11Cms`.
- `pkcs11CmsOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Base64 CMS/PKCS#7 will appear here”; `onAction`: `—`; editable=false.
- `pkcs11WrappingKeyCombo` — **ComboBox**, entrada; etiqueta/prompt: “Wrapping key alias (public part)”; `onAction`: `—`.
- `pkcs11WrapKeyCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key to wrap alias”; `onAction`: `—`.
- `pkcs11WrapTransformationCombo` — **ComboBox**, entrada; etiqueta/prompt: “pkcs11WrapTransformationCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Wrap Key”; `onAction`: `#handlePkcs11Wrap`.
- `pkcs11WrapResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Wrapped key (hexadecimal) will appear here”; `onAction`: `—`; editable=false.
- `pkcs11UnwrappingKeyCombo` — **ComboBox**, entrada; etiqueta/prompt: “Unwrapping key alias (private part)”; `onAction`: `—`.
- `pkcs11UnwrapTransformationCombo` — **ComboBox**, entrada; etiqueta/prompt: “pkcs11UnwrapTransformationCombo”; `onAction`: `—`.
- `pkcs11UnwrapDataArea` — **TextArea**, entrada; etiqueta/prompt: “Wrapped key (hexadecimal)”; `onAction`: `—`.
- `pkcs11UnwrapAlgorithmField` — **TextField**, entrada; etiqueta/prompt: “Recovered key algorithm, e.g. AES”; `onAction`: `—`.
- `pkcs11UnwrapTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “pkcs11UnwrapTypeCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Unwrap Key”; `onAction`: `#handlePkcs11Unwrap`.
- `pkcs11UnwrapResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Unwrap result summary will appear here (key material itself is never displayed)”; `onAction`: `—`; editable=false.
**🔗 Compare Public / Private Key**
- `keyComparePublicArea` — **TextArea**, entrada; etiqueta/prompt: “Public key PEM or X.509 certificate PEM”; `onAction`: `—`.
- `keyComparePrivateArea` — **TextArea**, entrada; etiqueta/prompt: “Private key PEM”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Compare Pair”; `onAction`: `#handleCompareKeyPair`.
- `keyCompareResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Checks the pair cryptographically with a temporary signing challenge; it does not compare PEM text.”; `onAction`: `—`; editable=false.
**🔀 Key Sharing (XOR Split/Combine) › Split Key**
- `keyToSplitField` — **TextArea**, entrada; etiqueta/prompt: “Enter key to split...”; `onAction`: `—`.
- `numComponentsCombo` — **ComboBox**, entrada; etiqueta/prompt: “Parts:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Split Key”; `onAction`: `#handleSplitKey`.
**🔀 Key Sharing (XOR Split/Combine) › Combine Components**
- `component1Field` — **TextField**, entrada; etiqueta/prompt: “Required”; `onAction`: `—`.
- `component2Field` — **TextField**, entrada; etiqueta/prompt: “Required”; `onAction`: `—`.
- `component3Field` — **TextField**, entrada; etiqueta/prompt: “Optional”; `onAction`: `—`.
- `component4Field` — **TextField**, entrada; etiqueta/prompt: “Optional”; `onAction`: `—`.
- `component5Field` — **TextField**, entrada; etiqueta/prompt: “Optional”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Combine Components”; `onAction`: `#handleCombineComponents`.
**🔀 Key Sharing (XOR Split/Combine)**
- `componentResultsArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Results will appear here...”; `onAction`: `—`; editable=false, visible=false inicial.
**🔐 Key Derivation (KDF)**
- `kdfAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `kdfInputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Input key material format:”; `onAction`: `—`.
- `kdfIterationsField` — **TextField**, entrada; etiqueta/prompt: “10000”; `onAction`: `—`.
- `kdfOutputLengthField` — **TextField**, entrada; etiqueta/prompt: “32”; `onAction`: `—`.
- `kdfInputField` — **TextField**, entrada; etiqueta/prompt: “Input key material (UTF-8)...”; `onAction`: `—`.
- `kdfSaltFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Salt:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate”; `onAction`: `#handleGenerateKdfSalt`.
- `kdfSaltField` — **TextField**, entrada; etiqueta/prompt: “Optional”; `onAction`: `—`.
- `kdfInfoFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Info:”; `onAction`: `—`.
- `kdfInfoField` — **TextField**, entrada; etiqueta/prompt: “HKDF only”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Derive Key”; `onAction`: `#handleDeriveKey`.
- `kdfResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Derived key will appear here...”; `onAction`: `—`; editable=false, visible=false inicial.
**🔗 AES Key Wrap (RFC 3394 / RFC 5649)**
- `keyWrapModeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Mode:”; `onAction`: `—`.
- `keyWrapUnwrapCheck` — **CheckBox**, entrada; etiqueta/prompt: “Unwrap”; `onAction`: `—`.
- `keyWrapKekField` — **TextField**, entrada; etiqueta/prompt: “KEK AES in hexadecimal (16, 24 or 32 bytes)”; `onAction`: `—`.
- `keyWrapDataField` — **TextField**, entrada; etiqueta/prompt: “Key data / wrapped data in hexadecimal”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Execute Key Wrap”; `onAction`: `#handleAesKeyWrap`.
- `keyWrapResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “keyWrapResultArea”; `onAction`: `—`; editable=false, visible=false inicial.
**🔒 TR-31 Key Blocks › Export (Wrap)**
- `tr31KbpkExportField` — **TextField**, entrada; etiqueta/prompt: “Key Block Protection Key (hex)”; `onAction`: `—`.
- `tr31KeyToWrapField` — **TextField**, entrada; etiqueta/prompt: “Key in hex”; `onAction`: `—`.
- `tr31VersionCombo` — **ComboBox**, entrada; etiqueta/prompt: “Version:”; `onAction`: `—`.
- `tr31UsageCombo` — **ComboBox**, entrada; etiqueta/prompt: “Usage:”; `onAction`: `—`.
- `tr31AlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `tr31ModeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Mode:”; `onAction`: `—`.
- `tr31ExportabilityCombo` — **ComboBox**, entrada; etiqueta/prompt: “Exportability:”; `onAction`: `—`.
- `tr31OptionalBlockCombo` — **ComboBox**, entrada; etiqueta/prompt: “Optional blocks (compact, optional):”; `onAction`: `—`.
- `tr31OptionalBlocksField` — **TextField**, entrada; etiqueta/prompt: “Optional blocks (compact, optional):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Wrap Key (Export)”; `onAction`: `#handleTR31Export`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleTR31Clear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleTR31Reset`.
- `tr31ExportResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Export results will appear here...”; `onAction`: `—`; editable=false.
**🔒 TR-31 Key Blocks › Import (Unwrap)**
- `tr31KbpkImportField` — **TextField**, entrada; etiqueta/prompt: “Key Block Protection Key (hex)”; `onAction`: `—`.
- `tr31KeyBlockField` — **TextArea**, entrada; etiqueta/prompt: “Paste TR-31 key block here...”; `onAction`: `—`.
- `tr31KeyLengthField` — **TextField**, entrada; etiqueta/prompt: “bytes (optional)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Unwrap Key”; `onAction`: `#handleTR31Import`.
- `—` — **Button**, acción; etiqueta/prompt: “Parse Header”; `onAction`: `#handleTR31ParseHeader`.
- `tr31ImportResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Import results will appear here...”; `onAction`: `—`; editable=false, visible=false inicial.
**🏛 Thales Variant LMK**
- `thalesLmkField` — **TextField**, entrada; etiqueta/prompt: “Double length (32 hex) or triple length (48 hex)”; `onAction`: `—`.
- `thalesKeyTypeField` — **TextField**, entrada; etiqueta/prompt: “209 is MK-SMI: variant 2 of pair code 09, LMK 28-29”; `onAction`: `—`.
- `thalesSchemeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key scheme:”; `onAction`: `—`.
- `thalesClearKeyField` — **TextField**, entrada; etiqueta/prompt: “The key to protect”; `onAction`: `—`.
- `thalesCryptogramField` — **TextField**, entrada; etiqueta/prompt: “With or without its scheme tag”; `onAction`: `—`.
- `thalesCheckValueField` — **TextField**, entrada; etiqueta/prompt: “For the key type search”; `onAction`: `—`.
- `thalesComponentCheck` — **CheckBox**, entrada; etiqueta/prompt: “This is a key component, not a whole key”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encrypt Under LMK”; `onAction`: `#handleThalesEncrypt`.
- `—` — **Button**, acción; etiqueta/prompt: “Decrypt Under LMK”; `onAction`: `#handleThalesDecrypt`.
- `—` — **Button**, acción; etiqueta/prompt: “Explain Wrapping”; `onAction`: `#handleThalesDescribe`.
- `—` — **Button**, acción; etiqueta/prompt: “Find Key Type”; `onAction`: `#handleThalesLookup`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Manual Example”; `onAction`: `#handleThalesLoadExample`.
- `thalesResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Two different things are both called a variant here. The key type variant enforces what kind of key this is and lands on the first byte of the LMK; the key scheme variant enforces the order of a multi-part key and lands on the first byte of the LMK's right half. Both are applied to the encrypting key, and each 8-byte part is encrypted separately in ECB.”; `onAction`: `—`; editable=false.
**🧱 Thales Key Block**
- `keyBlockInputArea` — **TextArea**, entrada; etiqueta/prompt: “S00072V2TG22N0003...”; `onAction`: `—`.
- `keyBlockLmkField` — **TextField**, entrada; etiqueta/prompt: “Triple-length TDES, 48 hex characters”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect Key Block”; `onAction`: `#handleKeyBlockInspect`.
- `—` — **Button**, acción; etiqueta/prompt: “Unwrap Key Block”; `onAction`: `#handleKeyBlockUnwrap`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Manual Example”; `onAction`: `#handleKeyBlockExample`.
- `keyBlockResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Key Block LMK (optional, to unwrap the key):”; `onAction`: `—`; editable=false.
**🏷 Atalla Key Block (AKB)**
- `atallaTemplateCombo` — **ComboBox**, entrada; etiqueta/prompt: “Working-key template (table 2-9):”; `onAction`: `—`.
- `atalla0Combo` — **ComboBox**, entrada; etiqueta/prompt: “B0 · Version”; `onAction`: `—`.
- `atalla1Combo` — **ComboBox**, entrada; etiqueta/prompt: “B1 · Key usage”; `onAction`: `—`.
- `atalla2Combo` — **ComboBox**, entrada; etiqueta/prompt: “B2 · Algorithm”; `onAction`: `—`.
- `atalla3Combo` — **ComboBox**, entrada; etiqueta/prompt: “B3 · Mode of use”; `onAction`: `—`.
- `atalla4Combo` — **ComboBox**, entrada; etiqueta/prompt: “B4 · Exportability”; `onAction`: `—`.
- `atalla5Combo` — **ComboBox**, entrada; etiqueta/prompt: “B5 · Reserved”; `onAction`: `—`.
- `atalla6Combo` — **ComboBox**, entrada; etiqueta/prompt: “B6 · Special handling”; `onAction`: `—`.
- `atalla7Combo` — **ComboBox**, entrada; etiqueta/prompt: “B7 · Other information”; `onAction`: `—`.
- `atallaHeaderField` — **TextField**, entrada; etiqueta/prompt: “1PUNE000”; `onAction`: `—`.
- `atallaMeaningArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Header (8 characters):”; `onAction`: `—`; editable=false.
- `atallaMfkField` — **TextField**, entrada; etiqueta/prompt: “0123456789ABCDEF8080808080808080FEDCBA9876543210”; `onAction`: `—`.
- `atallaKeyField` — **TextField**, entrada; etiqueta/prompt: “00112233445566778899AABBCCDDEEFF0123456789ABCDEF”; `onAction`: `—`.
- `atallaBlockArea` — **TextArea**, entrada; etiqueta/prompt: “1PUNE000,B17A04DC...,4FFC0BC8BCC1980E”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate AKB”; `onAction`: `#handleAtallaGenerate`.
- `—` — **Button**, acción; etiqueta/prompt: “Unwrap AKB”; `onAction`: `#handleAtallaUnwrap`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Example”; `onAction`: `#handleAtallaExample`.
- `atallaResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “AKB (header,key,MAC) to unwrap:”; `onAction`: `—`; editable=false.
**🔁 RSA Key Exchange › Export (Wrap)**
- `rsaKexRecipientPemArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY----- or -----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `rsaKexKeyToWrapField` — **TextField**, entrada; etiqueta/prompt: “Symmetric key in hex”; `onAction`: `—`.
- `rsaKexExportProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Profile:”; `onAction`: `—`.
- `rsaKexIncludeEnvelopeCheck` — **CheckBox**, entrada; etiqueta/prompt: “Wrap result as Crypto Envelope (adds algorithm/kid/kcv/timestamp header)”; `onAction`: `#handleRsaKexEnvelopeToggle`.
- `rsaKexKidField` — **TextField**, entrada; etiqueta/prompt: “Key ID (optional)”; `onAction`: `—`.
- `rsaKexKeyVersionField` — **TextField**, entrada; etiqueta/prompt: “Key Version (optional)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Wrap Key (Export)”; `onAction`: `#handleRsaKexExport`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleRsaKexClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleRsaKexReset`.
- `rsaKexExportResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Export results will appear here...”; `onAction`: `—`; editable=false.
**🔁 RSA Key Exchange › Import (Unwrap)**
- `rsaKexPrivateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `rsaKexWrappedDataArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the wrapped key or a CCE1./JSON envelope...”; `onAction`: `—`.
- `rsaKexImportProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Profile:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Unwrap Key”; `onAction`: `#handleRsaKexImport`.
- `rsaKexImportResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Import results will appear here...”; `onAction`: `—`; editable=false.
**🔀 TR-34 Key Distribution › Distribute (Sender)**
- `tr34SenderPrivateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `tr34SenderCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `tr34ReceiverCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `tr34KeyToDistributeField` — **TextField**, entrada; etiqueta/prompt: “Symmetric key in hex”; `onAction`: `—`.
- `tr34KeyIdField` — **TextField**, entrada; etiqueta/prompt: “e.g. kek-2026-01”; `onAction`: `—`.
- `tr34BindingNonceField` — **TextField**, entrada; etiqueta/prompt: “Hex nonce from the Receive tab's Generate Challenge — leave blank for one-pass”; `onAction`: `—`.
- `tr34IncludeEnvelopeCheck` — **CheckBox**, entrada; etiqueta/prompt: “Wrap key as Crypto Envelope before distributing”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Distribute Key”; `onAction`: `#handleTr34Distribute`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleTr34Clear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleTr34Reset`.
- `tr34DistributeResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Distribution results will appear here...”; `onAction`: `—`; editable=false.
**🔀 TR-34 Key Distribution › Receive (Receiver)**
- `tr34ReceiverPrivateKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `tr34ExpectedSenderCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `tr34DistributedDataArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the distributed data (or a Crypto Envelope) here...”; `onAction`: `—`.
- `tr34ChallengeNonceField` — **TextField**, entrada; etiqueta/prompt: “Hex nonce — generate here, copy into the Distribute tab's Binding Nonce, leave blank for one-pass”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Challenge”; `onAction`: `#handleTr34GenerateChallenge`.
- `—` — **Button**, acción; etiqueta/prompt: “Receive Key”; `onAction`: `#handleTr34Receive`.
- `tr34ReceiveResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Receive results will appear here...”; `onAction`: `—`; editable=false.
**🔐 RSA Key Generation**
- `rsaKeySizeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key Size:”; `onAction`: `—`.
- `rsaGenerateBtn` — **Button**, acción; etiqueta/prompt: “Generate RSA Key Pair”; `onAction`: `#handleGenerateRSA`.
**🔐 RSA Key Generation › Public Key (PEM)**
- `rsaPublicKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Public key will appear here...”; `onAction`: `—`; editable=false.
**🔐 RSA Key Generation › Private Key (PEM)**
- `rsaPrivateKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Private key will appear here...”; `onAction`: `—`; editable=false.
**🔐 RSA Key Generation**
- `rsaCopyPublicBtn` — **Button**, acción; etiqueta/prompt: “Copy Public Key”; `onAction`: `#handleCopyRsaPublicKey`.
- `rsaCopyPrivateBtn` — **Button**, acción; etiqueta/prompt: “Copy Private Key”; `onAction`: `#handleCopyRsaPrivateKey`.
- `rsaCopySummaryBtn` — **Button**, acción; etiqueta/prompt: “Copy Summary”; `onAction`: `#handleCopyRsaSummary`.
- `rsaExportPublicBtn` — **Button**, acción; etiqueta/prompt: “Export Public PEM…”; `onAction`: `#handleExportRsaPublicPem`.
- `rsaExportPrivateBtn` — **Button**, acción; etiqueta/prompt: “Export Private PEM…”; `onAction`: `#handleExportRsaPrivatePem`.
- `rsaSendShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Public to Shelf”; `onAction`: `#handleSendRsaPublicToShelf`.
- `rsaSendPrivateShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Private to Shelf (session only)”; `onAction`: `#handleSendRsaPrivateToShelf`.
- `rsaUseCipherBtn` — **Button**, acción; etiqueta/prompt: “Use in RSA Cipher”; `onAction`: `#handleUseRsaInCipher`.
- `rsaUseSignaturesBtn` — **Button**, acción; etiqueta/prompt: “Use in Digital Signatures”; `onAction`: `#handleUseRsaInSignatures`.
- `rsaUseCertificatesBtn` — **Button**, acción; etiqueta/prompt: “Use for Certificate / CSR”; `onAction`: `#handleUseRsaInCertificates`.
- `rsaClearBtn` — **Button**, acción; etiqueta/prompt: “Clear Pair”; `onAction`: `#handleClearRsa`.
**📈 ECDSA Key Generation**
- `ecdsaCurveCombo` — **ComboBox**, entrada; etiqueta/prompt: “Curve:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate ECDSA Key Pair”; `onAction`: `#handleGenerateECDSA`.
**📈 ECDSA Key Generation › Public Key (PEM)**
- `ecdsaPublicKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Public key will appear here...”; `onAction`: `—`; editable=false.
**📈 ECDSA Key Generation › Private Key (PEM)**
- `ecdsaPrivateKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Private key will appear here...”; `onAction`: `—`; editable=false.
**📈 ECDSA Key Generation**
- `ecdsaCopyPublicBtn` — **Button**, acción; etiqueta/prompt: “Copy Public Key”; `onAction`: `#handleCopyEcdsaPublicKey`.
- `ecdsaCopyPrivateBtn` — **Button**, acción; etiqueta/prompt: “Copy Private Key”; `onAction`: `#handleCopyEcdsaPrivateKey`.
- `ecdsaCopySummaryBtn` — **Button**, acción; etiqueta/prompt: “Copy Summary”; `onAction`: `#handleCopyEcdsaSummary`.
- `ecdsaExportPublicBtn` — **Button**, acción; etiqueta/prompt: “Export Public PEM…”; `onAction`: `#handleExportEcdsaPublicPem`.
- `ecdsaExportPrivateBtn` — **Button**, acción; etiqueta/prompt: “Export Private PEM…”; `onAction`: `#handleExportEcdsaPrivatePem`.
- `ecdsaSendShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Public to Shelf”; `onAction`: `#handleSendEcdsaPublicToShelf`.
- `ecdsaSendPrivateShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Private to Shelf (session only)”; `onAction`: `#handleSendEcdsaPrivateToShelf`.
- `ecdsaUseCipherBtn` — **Button**, acción; etiqueta/prompt: “Use in RSA Cipher”; `onAction`: `—`; disable=true.
- `ecdsaUseSignaturesBtn` — **Button**, acción; etiqueta/prompt: “Use in Digital Signatures”; `onAction`: `#handleUseEcdsaInSignatures`.
- `ecdsaUseCertificatesBtn` — **Button**, acción; etiqueta/prompt: “Use for Certificate / CSR”; `onAction`: `#handleUseEcdsaInCertificates`.
- `ecdsaClearBtn` — **Button**, acción; etiqueta/prompt: “Clear Pair”; `onAction`: `#handleClearEcdsa`.
**🔑 DSA Key Generation**
- `dsaKeySizeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key Size:”; `onAction`: `—`.
- `dsaGenerateBtn` — **Button**, acción; etiqueta/prompt: “Generate DSA Key Pair”; `onAction`: `#handleGenerateDSA`.
**🔑 DSA Key Generation › Public Key (PEM)**
- `dsaPublicKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Public key will appear here...”; `onAction`: `—`; editable=false.
**🔑 DSA Key Generation › Private Key (PEM)**
- `dsaPrivateKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Private key will appear here...”; `onAction`: `—`; editable=false.
**🔑 DSA Key Generation**
- `dsaCopyPublicBtn` — **Button**, acción; etiqueta/prompt: “Copy Public Key”; `onAction`: `#handleCopyDsaPublicKey`.
- `dsaCopyPrivateBtn` — **Button**, acción; etiqueta/prompt: “Copy Private Key”; `onAction`: `#handleCopyDsaPrivateKey`.
- `dsaCopySummaryBtn` — **Button**, acción; etiqueta/prompt: “Copy Summary”; `onAction`: `#handleCopyDsaSummary`.
- `dsaExportPublicBtn` — **Button**, acción; etiqueta/prompt: “Export Public PEM…”; `onAction`: `#handleExportDsaPublicPem`.
- `dsaExportPrivateBtn` — **Button**, acción; etiqueta/prompt: “Export Private PEM…”; `onAction`: `#handleExportDsaPrivatePem`.
- `dsaSendShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Public to Shelf”; `onAction`: `#handleSendDsaPublicToShelf`.
- `dsaSendPrivateShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Private to Shelf (session only)”; `onAction`: `#handleSendDsaPrivateToShelf`.
- `dsaUseCipherBtn` — **Button**, acción; etiqueta/prompt: “Use in RSA Cipher”; `onAction`: `—`; disable=true.
- `dsaUseSignaturesBtn` — **Button**, acción; etiqueta/prompt: “Use in Digital Signatures”; `onAction`: `#handleUseDsaInSignatures`.
- `dsaUseCertificatesBtn` — **Button**, acción; etiqueta/prompt: “Use for Certificate / CSR”; `onAction`: `#handleUseDsaInCertificates`.
- `dsaClearBtn` — **Button**, acción; etiqueta/prompt: “Clear Pair”; `onAction`: `#handleClearDsa`.
**⚡ EdDSA Key Generation**
- `—` — **Button**, acción; etiqueta/prompt: “Generate EdDSA Key Pair”; `onAction`: `#handleGenerateEdDSA`.
**⚡ EdDSA Key Generation › Public Key (PEM)**
- `eddsaPublicKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Public key will appear here...”; `onAction`: `—`; editable=false.
**⚡ EdDSA Key Generation › Private Key (PEM)**
- `eddsaPrivateKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Private key will appear here...”; `onAction`: `—`; editable=false.
**⚡ EdDSA Key Generation**
- `eddsaCopyPublicBtn` — **Button**, acción; etiqueta/prompt: “Copy Public Key”; `onAction`: `#handleCopyEddsaPublicKey`.
- `eddsaCopyPrivateBtn` — **Button**, acción; etiqueta/prompt: “Copy Private Key”; `onAction`: `#handleCopyEddsaPrivateKey`.
- `eddsaCopySummaryBtn` — **Button**, acción; etiqueta/prompt: “Copy Summary”; `onAction`: `#handleCopyEddsaSummary`.
- `eddsaExportPublicBtn` — **Button**, acción; etiqueta/prompt: “Export Public PEM…”; `onAction`: `#handleExportEddsaPublicPem`.
- `eddsaExportPrivateBtn` — **Button**, acción; etiqueta/prompt: “Export Private PEM…”; `onAction`: `#handleExportEddsaPrivatePem`.
- `eddsaSendShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Public to Shelf”; `onAction`: `#handleSendEddsaPublicToShelf`.
- `eddsaSendPrivateShelfBtn` — **Button**, acción; etiqueta/prompt: “Send Private to Shelf (session only)”; `onAction`: `#handleSendEddsaPrivateToShelf`.
- `eddsaUseCipherBtn` — **Button**, acción; etiqueta/prompt: “Use in RSA Cipher”; `onAction`: `—`; disable=true.
- `eddsaUseSignaturesBtn` — **Button**, acción; etiqueta/prompt: “Use in Digital Signatures”; `onAction`: `#handleUseEddsaInSignatures`.
- `eddsaUseCertificatesBtn` — **Button**, acción; etiqueta/prompt: “Use for Certificate / CSR”; `onAction`: `#handleUseEddsaInCertificates`.
- `eddsaClearBtn` — **Button**, acción; etiqueta/prompt: “Clear Pair”; `onAction`: `#handleClearEd25519`.

### [`main-view-modern.fxml`](../src/main/resources/fxml/main-view-modern.fxml)
Controller declarado: `com.cryptocarver.ui.ModernMainController`.

**General**
- `importKeyMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Import Key...”; `onAction`: `#handleImportKey`.
- `exportScreenMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Export Screen Configuration...”; `onAction`: `#handleExportScreenConfiguration`.
- `importScreenMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Import Screen Configuration...”; `onAction`: `#handleImportScreenConfiguration`.
- `saveSessionMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Save Session”; `onAction`: `#handleSaveSession`.
- `exportSessionTrailMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Export Session Trail...”; `onAction`: `#handleExportSessionTrail`.
- `exportHistoryMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Export History...”; `onAction`: `#handleExportHistory`.
- `exitMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Exit”; `onAction`: `#handleExit`.
- `clearInputMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Clear Input”; `onAction`: `#handleClearInput`.
- `clearOutputMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Clear Output”; `onAction`: `#handleClearOutput`.
- `copyOutputMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Copy Output”; `onAction`: `#handleCopyOutput`.
- `addToShelfMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Add Output to Clipboard Shelf”; `onAction`: `#handleAddCurrentOutputToShelf`.
- `clipboardShelfMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Open Clipboard Shelf”; `onAction`: `#handleOpenClipboardShelf`.
- `quickStartMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Quick Start”; `onAction`: `#handleQuickStart`.
- `commandPaletteMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Command Palette...”; `onAction`: `#handleOpenCommandPalette`.
- `toggleSidePanelMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Toggle Side Panel”; `onAction`: `#handleToggleSidePanel`.
- `toggleInspectorMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Toggle Inspector”; `onAction`: `#handleToggleInspector`.
- `expandResultMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Expand Result”; `onAction`: `#handleOpenExpandedResultViewer`.
- `expandTableMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Expand Table”; `onAction`: `#handleOpenExpandedTableViewer`.
- `zoomInMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Zoom In (Font)”; `onAction`: `#handleIncreaseFontSize`.
- `zoomOutMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Zoom Out (Font)”; `onAction`: `#handleDecreaseFontSize`.
- `resetViewMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Reset to Defaults”; `onAction`: `#handleResetView`.
- `languageSystemMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “System”; `onAction`: `#handleLanguageSystem`.
- `languageEsMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “Español”; `onAction`: `#handleLanguageEs`.
- `languageEnMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “English”; `onAction`: `#handleLanguageEn`.
- `themeSystemMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “System”; `onAction`: `#handleThemeSystem`.
- `themeLightMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “Light”; `onAction`: `#handleThemeLight`.
- `themeDarkMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “Dark”; `onAction`: `#handleThemeDark`.
- `laboratoryQuickStartMenuItem` — **MenuItem**, acción; etiqueta/prompt: “%menu.quickStart”; `onAction`: `#handleQuickStart`.
- `epochMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Epoch Converter”; `onAction`: `#handleEpochConverter`.
- `jsonMenuItem` — **MenuItem**, acción; etiqueta/prompt: “JSON Formatter”; `onAction`: `#handleJsonFormatter`.
- `byteInspectorMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Byte Inspector”; `onAction`: `#handleByteInspector`.
- `clearKeyCacheMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Clear Lab Key Cache”; `onAction`: `#handleClearLabKeyCache`.
- `visibilityFullLabMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “%menu.visibility.full”; `onAction`: `#handleVisibilityFullLab`.
- `visibilityMaskedMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “%menu.visibility.masked”; `onAction`: `#handleVisibilityMasked`.
- `visibilityRedactedMenuItem` — **RadioMenuItem**, acción; etiqueta/prompt: “%menu.visibility.redacted”; `onAction`: `#handleVisibilityRedacted`.
- `shortcutsMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Keyboard Shortcuts...”; `onAction`: `#handleShowKeyboardShortcuts`.
- `diagnosticsMenuItem` — **MenuItem**, acción; etiqueta/prompt: “Diagnostics”; `onAction`: `#handleDiagnostics`.
- `aboutMenuItem` — **MenuItem**, acción; etiqueta/prompt: “About”; `onAction`: `#handleAbout`.
- `—` — **Button**, acción; etiqueta/prompt: “☰”; `onAction`: `#handleToggleSidePanel`.
- `toolbarSearchButton` — **Button**, acción; etiqueta/prompt: “Search operations”; `onAction`: `#handleOpenCommandPalette`.
- `toolbarSaveSessionButton` — **Button**, acción; etiqueta/prompt: “Save Session”; `onAction`: `#handleSaveSession`.
- `inputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Payload format selector”; `onAction`: `—`.
- `outputFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Output format selector”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspector”; `onAction`: `#handleToggleInspector`.
- `breadcrumbSectionBtn` — **Button**, acción; etiqueta/prompt: “Keys”; `onAction`: `#handleBreadcrumbSectionClick`.
- `breadcrumbModuleBtn` — **Button**, acción; etiqueta/prompt: “Symmetric”; `onAction`: `#handleBreadcrumbModuleClick`.
- `favoriteToggleBtn` — **Button**, acción; etiqueta/prompt: “☆”; `onAction`: `#handleToggleFavorite`.
- `resultSaveStepButton` — **Button**, acción; etiqueta/prompt: “Save Step”; `onAction`: `#handleSaveCurrentResultAsSessionStep`.
- `resultExpandButton` — **Button**, acción; etiqueta/prompt: “Expand Result”; `onAction`: `#handleOpenExpandedResultViewer`.
- `resultShelfButton` — **Button**, acción; etiqueta/prompt: “Add to Shelf”; `onAction`: `#handleAddCurrentOutputToShelf`.
- `resultCopyButton` — **Button**, acción; etiqueta/prompt: “Copy Output”; `onAction`: `#handleCopyOutput`.
- `errorBannerGoToFieldBtn` — **Button**, acción; etiqueta/prompt: “Go to field”; `onAction`: `#handleErrorBannerGoToField`; visible=false inicial.
- `errorBannerCopyDetailsBtn` — **Button**, acción; etiqueta/prompt: “Copy technical details”; `onAction`: `#handleErrorBannerCopyDetails`.
- `errorBannerCloseBtn` — **Button**, acción; etiqueta/prompt: “✕”; `onAction`: `#handleErrorBannerClose`.
- `guideBackBtn` — **Button**, acción; etiqueta/prompt: “Back”; `onAction`: `#handleGuideBack`.
- `guideNextBtn` — **Button**, acción; etiqueta/prompt: “Next”; `onAction`: `#handleGuideNext`.
- `guideSkipBtn` — **Button**, acción; etiqueta/prompt: “Skip Guidance”; `onAction`: `#handleGuideSkip`.
- `guideExitBtn` — **Button**, acción; etiqueta/prompt: “Exit”; `onAction`: `#handleGuideExit`.
- `readinessToggleDetailsBtn` — **Button**, acción; etiqueta/prompt: “Show Details”; `onAction`: `#handleToggleReadinessDetails`.
- `—` — **Button**, acción; etiqueta/prompt: “Start guided flow”; `onAction`: `#handleStartGuidedEncrypt`.
- `—` — **Button**, acción; etiqueta/prompt: “Start guided flow”; `onAction`: `#handleStartGuidedHash`.
- `—` — **Button**, acción; etiqueta/prompt: “Start guided flow”; `onAction`: `#handleStartGuidedSign`.
- `—` — **Button**, acción; etiqueta/prompt: “Start guided flow”; `onAction`: `#handleStartGuidedCert`.
- `—` — **Button**, acción; etiqueta/prompt: “Start guided flow”; `onAction`: `#handleStartGuidedConvert`.
- `inspectorToggleButton` — **Button**, acción; etiqueta/prompt: “✕”; `onAction`: `#handleToggleInspector`.
- `inspectorAddSessionStepButton` — **Button**, acción; etiqueta/prompt: “Add current”; `onAction`: `#handleSaveCurrentResultAsSessionStep`; disable=true.
- `inspectorExportSessionTrailButton` — **Button**, acción; etiqueta/prompt: “Export all”; `onAction`: `#handleExportSessionTrail`; disable=true.
- `inspectorClearSessionTrailButton` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearSessionTrail`; disable=true.
- `inspectorPreviousSessionStepButton` — **Button**, acción; etiqueta/prompt: “‹”; `onAction`: `#handlePreviousSessionStep`.
- `inspectorNextSessionStepButton` — **Button**, acción; etiqueta/prompt: “›”; `onAction`: `#handleNextSessionStep`.
- `inspectorOpenSessionStepButton` — **Button**, acción; etiqueta/prompt: “View data”; `onAction`: `#handleOpenSelectedSessionStep`.
- `asyncCancelBtn` — **Button**, acción; etiqueta/prompt: “Cancel”; `onAction`: `#handleCancelAsyncOperation`.
- `statusVisibilityButton` — **Button**, acción; etiqueta/prompt: “Full visibility”; `onAction`: `#handleOpenSecurityMenu`.
- `commandSearchField` — **TextField**, entrada; etiqueta/prompt: “Type a command or search (e.g. aes, hash, jwt, shelf, cert)...”; `onAction`: `—`.
- `commandResultsListView` — **ListView**, entrada; etiqueta/prompt: “commandResultsListView”; `onAction`: `—`.

### [`openpgp.fxml`](../src/main/resources/fxml/openpgp.fxml)
Controller declarado: `com.cryptocarver.ui.OpenPgpController`.

**OpenPGP / GPG Laboratory**
- `handleProbeGnuPgButton` — **Button**, acción; etiqueta/prompt: “Check GnuPG…”; `onAction`: `#handleProbeGnuPg`.
- `handleRunGnuPgInteropButton` — **Button**, acción; etiqueta/prompt: “Run GnuPG Interop…”; `onAction`: `#handleRunGnuPgInterop`.
- `openPgpUserIdField` — **TextField**, entrada; etiqueta/prompt: “User ID, e.g. Alice <alice@example.test>”; `onAction`: `—`.
- `openPgpPassphraseField` — **PasswordField**, entrada; etiqueta/prompt: “Key passphrase”; `onAction`: `—`.
- `handleGenerateKeyPairButton` — **Button**, acción; etiqueta/prompt: “Generate RSA Key Pair”; `onAction`: `#handleGenerateKeyPair`.
- `handlePastePublicKeyButton` — **Button**, acción; etiqueta/prompt: “Paste”; `onAction`: `#handlePastePublicKey`.
- `handleLoadPublicKeyButton` — **Button**, acción; etiqueta/prompt: “Load…”; `onAction`: `#handleLoadPublicKey`.
- `handleSavePublicKeyButton` — **Button**, acción; etiqueta/prompt: “Save…”; `onAction`: `#handleSavePublicKey`.
- `handleInspectPublicKeyButton` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleInspectPublicKey`.
- `openPgpPubKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `openPgpPublicKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Public key (ASCII armor)”; `onAction`: `—`.
- `handlePasteSecretKeyButton` — **Button**, acción; etiqueta/prompt: “Paste”; `onAction`: `#handlePasteSecretKey`.
- `handleLoadSecretKeyButton` — **Button**, acción; etiqueta/prompt: “Load…”; `onAction`: `#handleLoadSecretKey`.
- `handleSaveSecretKeyButton` — **Button**, acción; etiqueta/prompt: “Save…”; `onAction`: `#handleSaveSecretKey`.
- `handleInspectSecretKeyButton` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleInspectSecretKey`.
- `openPgpSecretKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `handleClearSecretMaterialButton` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClearSecretMaterial`.
- `openPgpSecretKeyArea` — **TextArea**, entrada; etiqueta/prompt: “Secret key (laboratory only)”; `onAction`: `—`.
- `handleLoadInputButton` — **Button**, acción; etiqueta/prompt: “Load input…”; `onAction`: `#handleLoadInput`.
- `openPgpInputArea` — **TextArea**, entrada; etiqueta/prompt: “Operations (Text / Armor)”; `onAction`: `—`.
- `handleEncryptButton` — **Button**, acción; etiqueta/prompt: “Encrypt”; `onAction`: `#handleEncrypt`.
- `handleDecryptButton` — **Button**, acción; etiqueta/prompt: “Decrypt”; `onAction`: `#handleDecrypt`.
- `handleSignDetachedButton` — **Button**, acción; etiqueta/prompt: “Sign Detached”; `onAction`: `#handleSignDetached`.
- `handleSignAttachedButton` — **Button**, acción; etiqueta/prompt: “Sign Attached”; `onAction`: `#handleSignAttached`.
- `handleClearSignButton` — **Button**, acción; etiqueta/prompt: “Clear-sign”; `onAction`: `#handleClearSign`.
- `handleVerifyDetachedButton` — **Button**, acción; etiqueta/prompt: “Verify Detached”; `onAction`: `#handleVerifyDetached`.
- `handleVerifyAttachedButton` — **Button**, acción; etiqueta/prompt: “Verify Attached”; `onAction`: `#handleVerifyAttached`.
- `handleVerifyClearSignedButton` — **Button**, acción; etiqueta/prompt: “Verify Clear-sign”; `onAction`: `#handleVerifyClearSigned`.
- `handleVerifyWithGnuPgButton` — **Button**, acción; etiqueta/prompt: “Verify with GnuPG”; `onAction`: `#handleVerifyWithGnuPg`.
- `handleClearButton` — **Button**, acción; etiqueta/prompt: “Clear data”; `onAction`: `#handleClear`.
- `handleEncryptFileButton` — **Button**, acción; etiqueta/prompt: “Encrypt File…”; `onAction`: `#handleEncryptFile`.
- `handleDecryptFileButton` — **Button**, acción; etiqueta/prompt: “Decrypt File…”; `onAction`: `#handleDecryptFile`.
- `handleSignFileDetachedButton` — **Button**, acción; etiqueta/prompt: “Sign File…”; `onAction`: `#handleSignFileDetached`.
- `handleVerifyFileDetachedButton` — **Button**, acción; etiqueta/prompt: “Verify File…”; `onAction`: `#handleVerifyFileDetached`.
- `handleLoadSignatureButton` — **Button**, acción; etiqueta/prompt: “Load signature…”; `onAction`: `#handleLoadSignature`.
- `handleSaveSignatureButton` — **Button**, acción; etiqueta/prompt: “Save signature…”; `onAction`: `#handleSaveSignature`.

Los botones `?` de OpenPGP se enlazan a los controles FXML inyectados cuando se activa el modo contextual; no dependen de que `lookup()` ya encuentre los nodos en CSS. Cada acción conserva su ID real (incluidas las acciones diferenciadas para clave pública y secreta), y las consultas de User ID y frase de paso se asocian a sus campos respectivos. El guardado de la clave secreta y el borrado de material secreto tienen fichas separadas.
- `handleInspectDetachedSignatureButton` — **Button**, acción; etiqueta/prompt: “Inspect signature”; `onAction`: `#handleInspectDetachedSignature`.
- `openPgpSignatureArea` — **TextArea**, entrada; etiqueta/prompt: “Detached signature (ASCII armor)”; `onAction`: `—`.
- `handleSaveOutputButton` — **Button**, acción; etiqueta/prompt: “Save output…”; `onAction`: `#handleSaveOutput`.
- `openPgpOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Output / Result”; `onAction`: `—`; editable=false.

### [`pades.fxml`](../src/main/resources/fxml/pades.fxml)
Controller declarado: `com.cryptocarver.ui.PadesController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**PDF Advanced Electronic Signatures (PAdES)**
- `padesInputPathField` — **TextField**, entrada; etiqueta/prompt: “PDF input path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose PDF…”; `onAction`: `#handleChooseInput`.
- `padesOutputPathField` — **TextField**, entrada; etiqueta/prompt: “New signed PDF output path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose output…”; `onAction`: `#handleChooseOutput`.
- `padesSourceLocalRadio` — **RadioButton**, entrada; etiqueta/prompt: “Local PKCS#12”; `onAction`: `#handleSourceChanged`.
- `padesSourcePkcs11Radio` — **RadioButton**, entrada; etiqueta/prompt: “PKCS#11 Token”; `onAction`: `#handleSourceChanged`.
- `padesPkcs12PathField` — **TextField**, entrada; etiqueta/prompt: “PKCS#12 (.p12 / .pfx) path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose key…”; `onAction`: `#handleChoosePkcs12`.
- `padesPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “PKCS#12 password”; `onAction`: `—`.
- `padesPkcs11AliasCombo` — **ComboBox**, entrada; etiqueta/prompt: “Token signing alias”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Token Keys”; `onAction`: `#handleLoadTokenKeys`.
- `padesTimestampCheck` — **CheckBox**, entrada; etiqueta/prompt: “Add RFC 3161 signature timestamp (PAdES-T)”; `onAction`: `#handleTimestampOptionChanged`.
- `padesTsaUrlField` — **TextField**, entrada; etiqueta/prompt: “https://tsa.example.test”; `onAction`: `—`.
- `padesVisibleSignatureCheck` — **CheckBox**, entrada; etiqueta/prompt: “Add visible signature text”; `onAction`: `#handleVisibleSignatureOptionChanged`.
- `padesVisiblePageField` — **TextField**, entrada; etiqueta/prompt: “1”; `onAction`: `—`.
- `padesVisibleXField` — **TextField**, entrada; etiqueta/prompt: “40”; `onAction`: `—`.
- `padesVisibleYField` — **TextField**, entrada; etiqueta/prompt: “40”; `onAction`: `—`.
- `padesVisibleWidthField` — **TextField**, entrada; etiqueta/prompt: “220”; `onAction`: `—`.
- `padesVisibleHeightField` — **TextField**, entrada; etiqueta/prompt: “60”; `onAction`: `—`.
- `padesVisibleTextField` — **TextField**, entrada; etiqueta/prompt: “Visible signature text”; `onAction`: `—`; text=Signed with CryptoCarver laboratory.
- `padesTrustStorePathField` — **TextField**, entrada; etiqueta/prompt: “Truststore (.p12 / .pfx / .jks), optional”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Choose...”; `onAction`: `#handleChooseTrustStore`.
- `padesTrustStorePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Password”; `onAction`: `—`.
- `padesCrlEvidenceField` — **TextField**, salida/read-only; etiqueta/prompt: “No local CRL evidence selected”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Choose...”; `onAction`: `#handleChooseCrlEvidence`.
- `—` — **Button**, acción; etiqueta/prompt: “Sign PDF”; `onAction`: `#handleSign`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect Signatures”; `onAction`: `#handleInspect`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate PDF”; `onAction`: `#handleValidate`.
- `—` — **Button**, acción; etiqueta/prompt: “Save DSS Reports…”; `onAction`: `#handleSaveValidationReports`.
- `padesResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “PAdES result will appear here...”; `onAction`: `—`; editable=false.

### [`payments.fxml`](../src/main/resources/fxml/payments.fxml)
Controller declarado: `com.cryptocarver.ui.PaymentsController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**💳 CVV Operations (CVV/CVV2/iCVV)**
- `cvvTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Type:”; `onAction`: `—`.
- `cvkAField` — **TextField**, entrada; etiqueta/prompt: “16 hex chars (8 bytes)”; `onAction`: `—`.
- `cvkBField` — **TextField**, entrada; etiqueta/prompt: “16 hex chars (8 bytes)”; `onAction`: `—`.
- `panFieldCvv` — **TextField**, entrada; etiqueta/prompt: “Primary Account Number”; `onAction`: `—`.
- `expiryDateField` — **TextField**, entrada; etiqueta/prompt: “e.g. 2512”; `onAction`: `—`.
- `serviceCodeField` — **TextField**, entrada; etiqueta/prompt: “e.g. 000”; `onAction`: `—`.
- `atcField` — **TextField**, entrada; etiqueta/prompt: “e.g. 0001 (for dCVV)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate CVV”; `onAction`: `#handleGenerateCvv`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify CVV”; `onAction`: `#handleVerifyCvv`.
- `cvvResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Result will appear here...”; `onAction`: `—`; editable=false.
**🔢 Clear PIN Blocks**
- `clearPinBlockPane` — **TitledPane**, navegación/contexto; sigue la acción por defecto de codificación al expandirse solo cuando el modo de ayuda contextual está activo.
- `encodePinBlockButton` — **Button**, acción; `fx:id` añadido para la acción de codificación y su botón contextual `?` accesible.
- `decodePinBlockButton` — **Button**, acción; `fx:id` añadido para la acción de decodificación y su botón contextual `?` accesible.
- `pinField` — **TextField**, entrada; etiqueta/prompt: “Enter PIN”; `onAction`: `—`.
- `panFieldEncode` — **TextField**, entrada; etiqueta/prompt: “13-19 digits”; `onAction`: `—`.
- `pinBlockFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Format:”; `onAction`: `—`.
- `pinBlockPaddingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Padding:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encode PIN Block”; `onAction`: `#handleEncodePinBlock`.
- `pinBlockField` — **TextField**, entrada; etiqueta/prompt: “16 or 32 hex chars”; `onAction`: `—`.
- `panFieldDecode` — **TextField**, entrada; etiqueta/prompt: “13-19 digits”; `onAction`: `—`.
- `pinBlockFormatDecodeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Format:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Decode PIN Block”; `onAction`: `#handleDecodePinBlock`.
- `pinBlockResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “pinBlockResultArea”; `onAction`: `—`; editable=false, visible=false inicial.
**🔐 Encrypted PIN Blocks (ISO)**
- `encryptedPinBlockPane` — **TitledPane**, navegación/contexto; sigue la acción por defecto de protección al expandirse solo cuando el modo está activo.
- `encodeEncryptedPinBlockButton` — **Button**, acción; `fx:id` añadido para proteger/codificar.
- `decodeEncryptedPinBlockButton` — **Button**, acción; `fx:id` añadido para decodificar/descifrar.
- `encPinBlockFormatCombo` — **ComboBox**, entrada; etiqueta/prompt: “Format:”; `onAction`: `—`.
- `encPinField` — **TextField**, entrada; etiqueta/prompt: “4-12 digits”; `onAction`: `—`.
- `encPanFieldEncode` — **TextField**, entrada; etiqueta/prompt: “13-19 digits”; `onAction`: `—`.
- `encPinBlockKeyField` — **TextField**, entrada; etiqueta/prompt: “16/32/48 hex (TDES)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encode”; `onAction`: `#handleEncodeEncryptedPinBlock`.
- `encPinBlockFieldDecode` — **TextField**, entrada; etiqueta/prompt: “16 hex chars”; `onAction`: `—`.
- `encPanFieldDecode` — **TextField**, entrada; etiqueta/prompt: “13-19 digits”; `onAction`: `—`.
- `encPinBlockKeyFieldDecode` — **TextField**, entrada; etiqueta/prompt: “16/32/48 hex (TDES)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Decode”; `onAction`: `#handleDecodeEncryptedPinBlock`.
- `encResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “encResultArea”; `onAction`: `—`; editable=false, visible=false inicial.
Los campos y las cuatro acciones PIN tienen botones `?` creados por `PaymentsController`; se muestran solo con ayuda activa en una de las dos rutas PIN. `PinBlockHelpContextAdapter` publica exclusivamente los códigos de formato/relleno permitidos y presencia de las dos claves. Al abandonar las rutas elimina listeners; la expansión de acordeón en modo desactivado no abre el inspector ni muta el contexto.
**🔑 PIN Generation (IBM 3624)**
- `ibm3624PvkField` — **TextField**, entrada; etiqueta/prompt: “16/32/48 hex (TDES)”; `onAction`: `—`.
- `ibm3624ConvTableField` — **TextField**, entrada; etiqueta/prompt: “e.g. 0123456789012345”; `onAction`: `—`.
- `ibm3624OffsetField` — **TextField**, entrada; etiqueta/prompt: “e.g. 1234”; `onAction`: `—`.
- `ibm3624PanField` — **TextField**, entrada; etiqueta/prompt: “Up to 19 digits”; `onAction`: `—`.
- `ibm3624StartField` — **TextField**, entrada; etiqueta/prompt: “0”; `onAction`: `—`.
- `ibm3624LengthField` — **TextField**, entrada; etiqueta/prompt: “16”; `onAction`: `—`.
- `ibm3624PadField` — **TextField**, entrada; etiqueta/prompt: “F”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate PIN”; `onAction`: `#handleGenerateIbm3624Pin`.
- `ibm3624PinVerifyField` — **TextField**, entrada; etiqueta/prompt: “4-12 digits”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify PIN”; `onAction`: `#handleVerifyIbm3624Pin`.
- `ibm3624ResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “ibm3624ResultArea”; `onAction`: `—`; editable=false, visible=false inicial.
**🔑 PIN Utilities (Offset & PVV)**
- `genOffsetPvkField` — **TextField**, entrada; etiqueta/prompt: “16/32 hex chars”; `onAction`: `—`.
- `genOffsetDecTableField` — **TextField**, entrada; etiqueta/prompt: “0123456789012345”; `onAction`: `—`.
- `genOffsetPanField` — **TextField**, entrada; etiqueta/prompt: “13-19 digits”; `onAction`: `—`.
- `genOffsetPinField` — **TextField**, entrada; etiqueta/prompt: “4-12 digits”; `onAction`: `—`.
- `genOffsetStartField` — **TextField**, entrada; etiqueta/prompt: “0”; `onAction`: `—`.
- `genOffsetLengthField` — **TextField**, entrada; etiqueta/prompt: “16”; `onAction`: `—`.
- `genOffsetPadField` — **TextField**, entrada; etiqueta/prompt: “F”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate Offset”; `onAction`: `#handleGenerateOffsetUtility`.
- `genOffsetResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Desired PIN:”; `onAction`: `—`; editable=false, visible=false inicial.
- `genPvvPvkField` — **TextField**, entrada; etiqueta/prompt: “16/32 hex chars”; `onAction`: `—`.
- `genPvvPanField` — **TextField**, entrada; etiqueta/prompt: “13-19 digits”; `onAction`: `—`.
- `genPvvPinField` — **TextField**, entrada; etiqueta/prompt: “4 digits”; `onAction`: `—`.
- `genPvvKeyIndexField` — **TextField**, entrada; etiqueta/prompt: “0-9 (Optional)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Generate PVV”; `onAction`: `#handleGeneratePVVUtility`.
- `genPvvResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “PVK Index:”; `onAction`: `—`; editable=false, visible=false inicial.
- `derivePvvPvkField` — **TextField**, entrada; etiqueta/prompt: “16/32 hex chars”; `onAction`: `—`.
- `derivePvvPanField` — **TextField**, entrada; etiqueta/prompt: “13-19 digits”; `onAction`: `—`.
- `derivePvvTargetPvvField` — **TextField**, entrada; etiqueta/prompt: “4 digits”; `onAction`: `—`.
- `derivePvvKeyIndexField` — **TextField**, entrada; etiqueta/prompt: “0-9 (Optional)”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Find PIN”; `onAction`: `#handleDerivePinFromPvvUtility`.
- `derivePvvResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “PVK Index:”; `onAction`: `—`; editable=false, visible=false inicial.
**🔐 DUKPT KSN, TDES & AES**
- `dukptSchemeCombo` — **ComboBox**, entrada; etiqueta/prompt: “dukptSchemeCombo”; `onAction`: `—`.
- `dukptBdkField` — **TextField**, entrada; etiqueta/prompt: “Optional for KSN inspection”; `onAction`: `—`.
- `dukptKsnField` — **TextField**, entrada; etiqueta/prompt: “TDES FFFF9876543210E00008 · AES 123456789012345600000005”; `onAction`: `—`.
- `dukptTdesUsageCombo` — **ComboBox**, entrada; etiqueta/prompt: “TDES key purpose:”; `onAction`: `—`.
- `dukptAesUsageCombo` — **ComboBox**, entrada; etiqueta/prompt: “AES key usage:”; `onAction`: `—`.
- `dukptAesKeyTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “AES key usage:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect KSN / Derive Key”; `onAction`: `#handleInspectDukpt`.
- `dukptAesPinBlockField` — **TextField**, entrada; etiqueta/prompt: “04124389999AAAABAAAAAAAAAAAAAAAA”; `onAction`: `—`.
- `dukptAesPinOperationCombo` — **ComboBox**, entrada; etiqueta/prompt: “dukptAesPinOperationCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Process AES PIN block”; `onAction`: `#handleAesDukptPinBlock`.
- `dukptResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “dukptResultArea”; `onAction`: `—`; editable=false.
**🖥 payShield Host Command Bank**
- `hsmHostHeaderField` — **TextField**, entrada; etiqueta/prompt: “0000”; `onAction`: `—`.
- `hsmHostCommandCodeField` — **TextField**, entrada; etiqueta/prompt: “NC”; `onAction`: `—`.
- `hsmHostBodyField` — **TextField**, entrada; etiqueta/prompt: “Command body:”; `onAction`: `—`.
- `hsmHostTrailerField` — **TextField**, entrada; etiqueta/prompt: “Message trailer (optional):”; `onAction`: `—`.
- `hsmHostHeaderLengthField` — **TextField**, entrada; etiqueta/prompt: “4”; `onAction`: `—`.
- `hsmHostTcpPrefixCheck` — **CheckBox**, entrada; etiqueta/prompt: “Two-byte TCP length prefix”; `onAction`: `—`.
- `hsmHostCapturedFrameArea` — **TextArea**, entrada; etiqueta/prompt: “0000ND007B44AC1DDEE2A94B0007-E000”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Compose Command”; `onAction`: `#handleHsmHostCompose`.
- `—` — **Button**, acción; etiqueta/prompt: “Analyze Command”; `onAction`: `#handleHsmHostAnalyzeCommand`.
- `—` — **Button**, acción; etiqueta/prompt: “Analyze Response”; `onAction`: `#handleHsmHostAnalyzeResponse`.
- `—` — **Button**, acción; etiqueta/prompt: “Load NC Example”; `onAction`: `#handleHsmHostLoadExample`.
- `hsmHostResultArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Captured frame:”; `onAction`: `—`; editable=false.
**ISO 8583 Message Inspector**
- `iso8583ProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Profile:”; `onAction`: `—`.
- `iso8583BitmapEncodingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Bitmap encoding:”; `onAction`: `—`.
- `iso8583LengthEncodingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Length encoding:”; `onAction`: `—`.
- `iso8583MessageArea` — **TextArea**, entrada; etiqueta/prompt: “0200
3=000000
11=123456”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Parse message”; `onAction`: `#handleParseIso8583`.
- `—` — **Button**, acción; etiqueta/prompt: “Build message”; `onAction`: `#handleBuildIso8583`.
- `iso8583ReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The field-by-field report will appear here...”; `onAction`: `—`; editable=false.

### [`pkcs11_profiles.fxml`](../src/main/resources/fxml/pkcs11_profiles.fxml)
Controller declarado: `com.cryptocarver.ui.Pkcs11ProfilesController`.

**🔐 PKCS#11 Profiles & Public Inventory**
- `pkcs11ProfilesTable` — **TableView**, entrada; etiqueta/prompt: “Manage non-secret profiles and inspect public slots, tokens and mechanisms. No credentials or token objects are requested.”; `onAction`: `—`.
- `pkcs11ProfileNameField` — **TextField**, entrada; etiqueta/prompt: “Public profile name”; `onAction`: `—`.
- `pkcs11LibraryPathField` — **TextField**, entrada; etiqueta/prompt: “Absolute native library path”; `onAction`: `—`.
- `pkcs11SlotListIndexField` — **TextField**, entrada; etiqueta/prompt: “Non-negative integer”; `onAction`: `—`.
- `pkcs11NewProfileButton` — **Button**, acción; etiqueta/prompt: “New profile”; `onAction`: `#handleNewProfile`.
- `pkcs11SaveProfileButton` — **Button**, acción; etiqueta/prompt: “Save profile”; `onAction`: `#handleSaveProfile`.
- `pkcs11DeleteProfileButton` — **Button**, acción; etiqueta/prompt: “Delete profile”; `onAction`: `#handleDeleteProfile`.
- `pkcs11DiagnoseButton` — **Button**, acción; etiqueta/prompt: “Diagnose”; `onAction`: `#handleDiagnose`.
- `pkcs11InventoryButton` — **Button**, acción; etiqueta/prompt: “Inventory”; `onAction`: `#handleInventory`.
- `pkcs11CancelButton` — **Button**, acción; etiqueta/prompt: “Cancel”; `onAction`: `#handleCancel`.
- `pkcs11InventoryArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Public inventory results will appear here.”; `onAction`: `—`; editable=false.

### [`pqc.fxml`](../src/main/resources/fxml/pqc.fxml)
Controller declarado: `com.cryptocarver.ui.PostQuantumController`.

**🔑 PQC Key Generation**
- `pqcAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm (NIST Standards):”; `onAction`: `—`.
- `pqcGenerateKeyBtn` — **Button**, acción; etiqueta/prompt: “Generate Key Pair”; `onAction`: `#handleGeneratePQCKeyPair`.
- `—` — **Button**, acción; etiqueta/prompt: “Import PEM Keys...”; `onAction`: `#handleImportPQCKeys`.
- `—` — **Button**, acción; etiqueta/prompt: “Export Public...”; `onAction`: `#handleExportPQCPublicKey`.
- `—` — **Button**, acción; etiqueta/prompt: “Export Private...”; `onAction`: `#handleExportPQCPrivateKey`.
- `pqcKeyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
**🔑 PQC Key Generation › Public Key**
- `pqcPublicKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “pqcPublicKeyArea”; `onAction`: `—`; editable=false.
**🔑 PQC Key Generation › Private Key**
- `pqcPrivateKeyArea` — **TextArea**, salida/read-only; etiqueta/prompt: “pqcPrivateKeyArea”; `onAction`: `—`; editable=false.
**🔑 PQC Key Generation › Key Details**
- `pqcKeyDetailsArea` — **TextArea**, salida/read-only; etiqueta/prompt: “pqcKeyDetailsArea”; `onAction`: `—`; editable=false.
**🔏 PQC Sign / Verify**
- `pqcSignAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `pqcSignInputArea` — **TextArea**, entrada; etiqueta/prompt: “Data to Sign (UTF-8):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Sign Data”; `onAction`: `#handlePQCSign`.
- `pqcSignOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Signature (Hex):”; `onAction`: `—`; editable=false.
- `pqcVerifySignatureField` — **TextField**, entrada; etiqueta/prompt: “Verify Signature (Paste Hex):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Paste Signature”; `onAction`: `#handlePastePqcVerifySignature`.
- `pqcVerifyShelfMenu` — **MenuButton**, acción; etiqueta/prompt: “Use from Shelf”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify Signature”; `onAction`: `#handlePQCVerify`.
**🎓 KEM Educational Demo (Alice & Bob)**
- `pqcKemAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “KEM algorithm:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Bob: Encapsulate Shared Secret”; `onAction`: `#handlePQCEncapsulate`.
- `pqcKemCiphertextArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Encapsulation Ciphertext sent to Alice (Hex):”; `onAction`: `—`; editable=false.
- `pqcKemSharedSecretField` — **TextField**, salida/read-only; etiqueta/prompt: “Bob's Shared Secret (Hex):”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Alice: Decapsulate Ciphertext”; `onAction`: `#handlePQCDecapsulate`.
- `pqcAliceSecretField` — **TextField**, salida/read-only; etiqueta/prompt: “Alice's Shared Secret (Hex):”; `onAction`: `—`; editable=false.
**⏱️ Benchmark & Performance**
- `pqcBenchmarkAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `pqcBenchmarkBtn` — **Button**, acción; etiqueta/prompt: “Run Benchmark”; `onAction`: `#handlePQCBenchmark`.
- `pqcBenchmarkArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Algorithm:”; `onAction`: `—`; editable=false.

### [`process_designer.fxml`](../src/main/resources/fxml/process_designer.fxml)
Controller declarado: `com.cryptocarver.ui.ProcessDesignerController`.

**🧩 Process Designer**
- `processNameField` — **TextField**, entrada; etiqueta/prompt: “Untitled process”; `onAction`: `—`.
- `runProcessButton` — **Button**, acción; etiqueta/prompt: “Run”; `onAction`: `#handleRunProcess`.
- `—` — **Button**, acción; etiqueta/prompt: “Dry Run”; `onAction`: `#handleDryRunProcess`.
- `cancelProcessButton` — **Button**, acción; etiqueta/prompt: “Cancel”; `onAction`: `#handleCancelProcess`; disable=true.
- `—` — **Button**, acción; etiqueta/prompt: “Open…”; `onAction`: `#handleLoadProcess`.
- `—` — **Button**, acción; etiqueta/prompt: “Save…”; `onAction`: `#handleSaveProcess`.
- `—` — **MenuButton**, acción; etiqueta/prompt: “Edición”; `onAction`: `—`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Undo”; `onAction`: `#handleUndo`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Redo”; `onAction`: `#handleRedo`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Duplicate”; `onAction`: `#handleDuplicateSelected`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Tidy layout”; `onAction`: `#handleTidyLayout`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Clear canvas”; `onAction`: `#handleClearCanvas`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleResetDefaults`.
- `—` — **MenuButton**, acción; etiqueta/prompt: “Presets”; `onAction`: `—`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “SHA-256 text digest”; `onAction`: `#handleLoadSha256Preset`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “Base64 text encode”; `onAction`: `#handleLoadBase64Preset`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “AES-GCM encrypt and decrypt”; `onAction`: `#handleLoadAesGcmRoundTripPreset`.
- `—` — **MenuItem**, acción; etiqueta/prompt: “AES-CMAC”; `onAction`: `#handleLoadAesCmacPreset`.
- `reverseConnectionToolbarButton` — **Button**, acción; etiqueta/prompt: “Reverse connection”; `onAction`: `#handleReverseSelectedConnection`; disable=true.
- `—` — **Button**, acción; etiqueta/prompt: “Clear canvas”; `onAction`: `#handleClearCanvas`.
- `inspectorToggleButton` — **Button**, acción; etiqueta/prompt: “Hide inspector”; `onAction`: `#handleToggleInspector`.
- `focusModeButton` — **Button**, acción; etiqueta/prompt: “Focus mode”; `onAction`: `#handleToggleFocusMode`.
- `—` — **Button**, acción; etiqueta/prompt: “Expand results”; `onAction`: `#handleOpenExpandedExecutionResult`.
- `—` — **Button**, acción; etiqueta/prompt: “Open in window”; `onAction`: `#handleOpenWindow`.
- `—` — **Button**, acción; etiqueta/prompt: “Zoom in”; `onAction`: `#handleZoomIn`.
- `—` — **Button**, acción; etiqueta/prompt: “Zoom out”; `onAction`: `#handleZoomOut`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset zoom”; `onAction`: `#handleResetZoom`.
- `—` — **Button**, acción; etiqueta/prompt: “Fit to content”; `onAction`: `#handleZoomFit`.
- `snapToGridCheck` — **CheckBox**, entrada; etiqueta/prompt: “Snap to grid”; `onAction`: `#handleSnapToGridToggled`.
- `paletteSearchField` — **TextField**, entrada; etiqueta/prompt: “Search palette…”; `onAction`: `—`.
- `nodeNameField` — **TextField**, entrada; etiqueta/prompt: “Describe this step”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Save block settings”; `onAction`: `#handleSaveNodeSettings`.
- `connectSelectedButton` — **Button**, acción; etiqueta/prompt: “Select 2 blocks to connect”; `onAction`: `#handleConnectSelected`.
- `connectMenuButton` — **MenuButton**, acción; etiqueta/prompt: “Connect to...”; `onAction`: `—`; visible=false inicial.
- `reverseConnectionButton` — **Button**, acción; etiqueta/prompt: “Reverse selected connection”; `onAction`: `#handleReverseSelectedConnection`.
- `deleteSelectedButton` — **Button**, acción; etiqueta/prompt: “Delete selected”; `onAction`: `#handleDeleteSelected`.
- `—` — **Button**, acción; etiqueta/prompt: “Clear canvas”; `onAction`: `#handleClearCanvas`.
- `executionStatusTable` — **TableView**, entrada; etiqueta/prompt: “Execution status”; `onAction`: `—`.
- `executionOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Execution results and validation messages appear here.”; `onAction`: `—`; editable=false.

### [`wallet.fxml`](../src/main/resources/fxml/wallet.fxml)
Controller declarado: `com.cryptocarver.ui.WalletController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Example”; `onAction`: `#handleLoadExample`.
**Issue**
- `sdJwtAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `sdJwtIssuerKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `sdJwtClaimsArea` — **TextArea**, entrada; etiqueta/prompt: “{"given_name": "John"}”; `onAction`: `—`.
- `sdJwtDisclosableArea` — **TextArea**, entrada; etiqueta/prompt: “given_name, address.locality, nationalities[]”; `onAction`: `—`.
- `sdJwtVctField` — **TextField**, entrada; etiqueta/prompt: “urn:eudi:pid:1”; `onAction`: `—`.
- `sdJwtDecoyField` — **TextField**, entrada; etiqueta/prompt: “0”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Issue”; `onAction`: `#handleSdJwtIssue`.
- `sdJwtIssueOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The SD-JWT with every Disclosure attached will appear here”; `onAction`: `—`; editable=false.
**Present**
- `sdJwtPresentInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the issued SD-JWT”; `onAction`: `—`.
- `sdJwtRevealArea` — **TextArea**, entrada; etiqueta/prompt: “given_name”; `onAction`: `—`.
- `sdJwtAudienceField` — **TextField**, entrada; etiqueta/prompt: “https://verifier.example”; `onAction`: `—`.
- `sdJwtNonceField` — **TextField**, entrada; etiqueta/prompt: “nonce from the verifier”; `onAction`: `—`.
- `sdJwtHolderKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Present”; `onAction`: `#handleSdJwtPresent`.
- `sdJwtPresentOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The narrowed presentation will appear here”; `onAction`: `—`; editable=false.
**Verify**
- `sdJwtVerifyInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the presentation”; `onAction`: `—`.
- `sdJwtVerifyIssuerKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `sdJwtVerifyHolderKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `sdJwtVerifyAudienceField` — **TextField**, entrada; etiqueta/prompt: “https://verifier.example”; `onAction`: `—`.
- `sdJwtVerifyNonceField` — **TextField**, entrada; etiqueta/prompt: “nonce the verifier issued”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify”; `onAction`: `#handleSdJwtVerify`.
- `sdJwtVerifyOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The rebuilt payload will appear here”; `onAction`: `—`; editable=false.
**Inspect**
- `sdJwtInspectInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste any SD-JWT”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleSdJwtInspect`.
- `sdJwtInspectOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Header, payload, decoded Disclosures and key binding”; `onAction`: `—`; editable=false.
**Issue**
- `mdocDocTypeField` — **TextField**, entrada; etiqueta/prompt: “Document type:”; `onAction`: `—`.
- `mdocDigestCombo` — **ComboBox**, entrada; etiqueta/prompt: “Digest algorithm:”; `onAction`: `—`.
- `mdocIssuerKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `mdocSignerCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `mdocDeviceKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `mdocClaimsArea` — **TextArea**, entrada; etiqueta/prompt: “{"org.iso.18013.5.1": {"family_name": "Doe"}}”; `onAction`: `—`.
- `mdocValidityField` — **TextField**, entrada; etiqueta/prompt: “365”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Issue”; `onAction`: `#handleMdocIssue`.
- `mdocIssueOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The IssuerSigned structure, hex-encoded”; `onAction`: `—`; editable=false.
**Verify and inspect**
- `mdocVerifyInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the hex-encoded IssuerSigned structure”; `onAction`: `—`.
- `mdocVerifyIssuerKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify”; `onAction`: `#handleMdocVerify`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect only”; `onAction`: `#handleMdocInspect`.
- `mdocVerifyOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The MSO, the disclosed elements and the findings”; `onAction`: `—`; editable=false.
**Issue**
- `statusListBitsCombo` — **ComboBox**, entrada; etiqueta/prompt: “Bits per entry:”; `onAction`: `—`.
- `statusListStatusesArea` — **TextArea**, entrada; etiqueta/prompt: “0,0,1,0,2,0,0,0”; `onAction`: `—`.
- `statusListUriField` — **TextField**, entrada; etiqueta/prompt: “https://issuer.example/statuslists/1”; `onAction`: `—`.
- `statusListAlgoCombo` — **ComboBox**, entrada; etiqueta/prompt: “Algorithm:”; `onAction`: `—`.
- `statusListKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PRIVATE KEY-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Issue”; `onAction`: `#handleStatusListIssue`.
- `statusListOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The Status List Token will appear here”; `onAction`: `—`; editable=false.
**Resolve**
- `statusListTokenArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the Status List Token”; `onAction`: `—`.
- `statusListIndexField` — **TextField**, entrada; etiqueta/prompt: “7”; `onAction`: `—`.
- `statusListVerifyKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Resolve index”; `onAction`: `#handleStatusListResolve`.
- `—` — **Button**, acción; etiqueta/prompt: “Describe list”; `onAction`: `#handleStatusListDescribe`.
- `statusListResolveOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “The status, or a summary of the whole list”; `onAction`: `—`; editable=false.
**General**
- `eidasCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleEidasCertInspect`.
- `eidasCertOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Declared profiles, qcStatements, policies and findings”; `onAction`: `—`; editable=false.
- `trustedListXmlArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the TS 119 612 XML”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleTrustedListInspect`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify signature”; `onAction`: `#handleTrustedListVerify`.
- `trustedEntityListJsonArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the TS 119 602 JSON or compact JAdES”; `onAction`: `—`.
- `trustedEntityListSignerCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `trustedEntityListSearchCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect JSON”; `onAction`: `#handleTrustedEntityListJsonInspect`.
- `trustedListCertArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN CERTIFICATE-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Find certificate”; `onAction`: `#handleTrustedListFind`.
- `trustedListOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Scheme, providers, service statuses and qualifiers”; `onAction`: `—`; editable=false.
**Inspect**
- `cborInputArea` — **TextArea**, entrada; etiqueta/prompt: “a26161016162820203”; `onAction`: `—`.
- `cborViewCombo` — **ComboBox**, entrada; etiqueta/prompt: “View:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleCborInspect`.
- `—` — **Button**, acción; etiqueta/prompt: “To JSON”; `onAction`: `#handleCborToJson`.
- `cborOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Tag 24 payloads are decoded and followed into”; `onAction`: `—`; editable=false.
**From JSON**
- `cborJsonArea` — **TextArea**, entrada; etiqueta/prompt: “{"a": 1}”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encode”; `onAction`: `#handleCborFromJson`.
- `cborFromJsonOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Hex-encoded CBOR”; `onAction`: `—`; editable=false.
**Build transaction data**
- `scaTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Transaction type:”; `onAction`: `—`.
- `scaCredentialIdsField` — **TextField**, entrada; etiqueta/prompt: “sca”; `onAction`: `—`.
- `scaPayloadArea` — **TextArea**, entrada; etiqueta/prompt: “{"amount": "123.45", "currency": "EUR", "payee": "Shop"}”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Build”; `onAction`: `#handleScaBuild`.
- `scaEntryArea` — **TextArea**, entrada; etiqueta/prompt: “The base64url transaction data entry appears here, and is also the input below”; `onAction`: `—`.
**Verify dynamic linking**
- `scaPresentationArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the presentation”; `onAction`: `—`.
- `scaTransactionDataArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the entries the verifier sent”; `onAction`: `—`.
- `scaIssuerKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `scaHolderKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `scaAudienceField` — **TextField**, entrada; etiqueta/prompt: “https://psp.example”; `onAction`: `—`.
- `scaNonceField` — **TextField**, entrada; etiqueta/prompt: “nonce from the request”; `onAction`: `—`.
- `scaResponseModeField` — **TextField**, entrada; etiqueta/prompt: “direct_post.jwt”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify”; `onAction`: `#handleScaVerify`.
- `scaOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Factors, transaction binding and findings”; `onAction`: `—`; editable=false.
**Inspect an OpenID4VP request**
- `oid4vpRequestArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the request”; `onAction`: `—`.
- `oid4vpKeyArea` — **TextArea**, entrada; etiqueta/prompt: “-----BEGIN PUBLIC KEY-----”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect”; `onAction`: `#handleOid4vpInspect`.
- `oid4vpOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Client identifier scheme, nonce, response mode, transaction data and findings”; `onAction`: `—`; editable=false.
**General**
- `adesFileNameField` — **TextField**, entrada; etiqueta/prompt: “document.p7m”; `onAction`: `—`.
- `adesDocumentArea` — **TextArea**, entrada; etiqueta/prompt: “Paste the signed document”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate”; `onAction`: `#handleAdesValidate`.
- `—` — **Button**, acción; etiqueta/prompt: “ETSI report”; `onAction`: `#handleAdesEtsiReport`.
- `adesOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Level, indication and findings; or the full TS 119 102-2 report”; `onAction`: `—`; editable=false.

### [`wss_security.fxml`](../src/main/resources/fxml/wss_security.fxml)
Controller declarado: `com.cryptocarver.ui.WssSecurityController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**🔐 Sign SOAP (WSS)**
- `wssSignInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste SOAP XML here...”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleBrowseWssSignInput`.
- `wssSignAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Signature Algorithm:”; `onAction`: `—`.
- `wssIncludeTimestampCheck` — **CheckBox**, entrada; etiqueta/prompt: “Add wsu:Timestamp”; `onAction`: `—`.
- `wssTimestampValiditySpinner` — **Spinner**, entrada; etiqueta/prompt: “Validity (min):”; `onAction`: `—`.
- `wssSignTimestampCheck` — **CheckBox**, entrada; etiqueta/prompt: “Protect Timestamp”; `onAction`: `—`.
- `wssSignKeyPathField` — **TextField**, entrada; etiqueta/prompt: “wssSignKeyPathField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseWssKey`.
- `wssSignKeyPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “KeyStore Password:”; `onAction`: `—`.
- `wssSignKeyAliasCombo` — **ComboBox**, entrada; etiqueta/prompt: “wssSignKeyAliasCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Keys”; `onAction`: `#handleLoadWssKeys`.
- `wssSignPrivateKeyPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Leave blank if same as KeyStore”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Sign SOAP Body”; `onAction`: `#handleSignWSS`.
- `wssSignOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Signed SOAP Output:”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Save Signed XML...”; `onAction`: `#handleSaveSignedWss`.
**✅ Verify SOAP (WSS)**
- `wssVerifyInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste Signed SOAP XML Content:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleBrowseWssVerifyInput`.
- `wssVerifyTrustStorePathField` — **TextField**, entrada; etiqueta/prompt: “wssVerifyTrustStorePathField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseWssTrustStore`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify SOAP Signature”; `onAction`: `#handleVerifyWSS`.
- `wssVerifyReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Verification Report:”; `onAction`: `—`; editable=false.
**👤 Add UsernameToken (WSS)**
- `wssUsernameCreateInputArea` — **TextArea**, entrada; etiqueta/prompt: “SOAP XML:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleBrowseUsernameCreateInput`.
- `wssUsernameCreateNameField` — **TextField**, entrada; etiqueta/prompt: “Username:”; `onAction`: `—`.
- `wssUsernameCreatePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Password:”; `onAction`: `—`.
- `wssUsernamePasswordTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Password format:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Add UsernameToken”; `onAction`: `#handleAddUsernameToken`.
- `wssUsernameCreateOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “SOAP Output:”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Save SOAP XML...”; `onAction`: `#handleSaveUsernameTokenXml`.
**🔎 Verify UsernameToken (WSS)**
- `wssUsernameVerifyInputArea` — **TextArea**, entrada; etiqueta/prompt: “SOAP XML with UsernameToken:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleBrowseUsernameVerifyInput`.
- `wssUsernameExpectedNameField` — **TextField**, entrada; etiqueta/prompt: “Expected username:”; `onAction`: `—`.
- `wssUsernameExpectedPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Expected password:”; `onAction`: `—`.
- `wssUsernameMaxAgeSpinner` — **Spinner**, entrada; etiqueta/prompt: “Max token age (sec):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify UsernameToken”; `onAction`: `#handleVerifyUsernameToken`.
- `wssUsernameVerifyReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Verification Report:”; `onAction`: `—`; editable=false.
**🔒 Encrypt SOAP Body (WSS)**
- `wssEncryptInputArea` — **TextArea**, entrada; etiqueta/prompt: “SOAP XML:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleBrowseWssEncryptInput`.
- `wssEncryptCertificatePathField` — **TextField**, entrada; etiqueta/prompt: “wssEncryptCertificatePathField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseWssEncryptCertificate`.
- `wssEncryptDataAlgorithmCombo` — **ComboBox**, entrada; etiqueta/prompt: “Data encryption:”; `onAction`: `—`.
- `wssEncryptKeyTransportCombo` — **ComboBox**, entrada; etiqueta/prompt: “Key transport:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Encrypt SOAP Body”; `onAction`: `#handleEncryptSoapBody`.
- `wssEncryptOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Encrypted SOAP Output:”; `onAction`: `—`; editable=false.
- `wssEncryptReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Encryption Report:”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Save Encrypted XML...”; `onAction`: `#handleSaveEncryptedSoap`.
**🔓 Decrypt SOAP Body (WSS)**
- `wssDecryptInputArea` — **TextArea**, entrada; etiqueta/prompt: “Encrypted SOAP XML:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load from File...”; `onAction`: `#handleBrowseWssDecryptInput`.
- `wssDecryptKeyStorePathField` — **TextField**, entrada; etiqueta/prompt: “wssDecryptKeyStorePathField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseWssDecryptKeyStore`.
- `wssDecryptKeyStorePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “KeyStore password:”; `onAction`: `—`.
- `wssDecryptPrivateKeyPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “If different”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Decrypt SOAP Body”; `onAction`: `#handleDecryptSoapBody`.
- `wssDecryptOutputArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Decrypted SOAP Output:”; `onAction`: `—`; editable=false.
- `wssDecryptReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Decryption Report:”; `onAction`: `—`; editable=false.
- `—` — **Button**, acción; etiqueta/prompt: “Save Decrypted XML...”; `onAction`: `#handleSaveDecryptedSoap`.

### [`xml_security.fxml`](../src/main/resources/fxml/xml_security.fxml)
Controller declarado: `com.cryptocarver.ui.XMLSignatureController`.

**General**
- `—` — **Button**, acción; etiqueta/prompt: “Clear”; `onAction`: `#handleClear`.
- `—` — **Button**, acción; etiqueta/prompt: “Reset Defaults”; `onAction`: `#handleReset`.
**📝 Sign XML (XAdES)**
- `xmlSignInputPathField` — **TextField**, entrada; etiqueta/prompt: “xmlSignInputPathField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseXMLSignInput`.
- `xmlSignSourceLocalRadio` — **RadioButton**, entrada; etiqueta/prompt: “Local KeyStore / PEM”; `onAction`: `#handleXMLSignSourceChanged`.
- `xmlSignSourcePkcs11Radio` — **RadioButton**, entrada; etiqueta/prompt: “Connected PKCS#11 Token”; `onAction`: `#handleXMLSignSourceChanged`.
- `xmlSignKeyPathField` — **TextField**, entrada; etiqueta/prompt: “xmlSignKeyPathField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseXMLKey`.
- `xmlSignKeyPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “KeyStore Password:”; `onAction`: `—`.
- `xmlSignKeyAliasCombo` — **ComboBox**, entrada; etiqueta/prompt: “xmlSignKeyAliasCombo”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load Keys”; `onAction`: `#handleLoadXMLKeys`.
- `xmlSignLevelCombo` — **ComboBox**, entrada; etiqueta/prompt: “Signature Level:”; `onAction`: `—`.
- `xmlSignPackagingCombo` — **ComboBox**, entrada; etiqueta/prompt: “Signature Packaging:”; `onAction`: `—`.
- `xmlSignTsaUrlText` — **ComboBox**, entrada; etiqueta/prompt: “Select a preset or enter your corporate TSA URL”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Save TSA”; `onAction`: `#handleSaveTSA`.
- `—` — **Button**, acción; etiqueta/prompt: “Test TSA”; `onAction`: `#handleTestTSA`.
- `xmlSignTsaAuthTypeCombo` — **ComboBox**, entrada; etiqueta/prompt: “Auth Type”; `onAction`: `—`.
- `xmlSignTsaUserField` — **TextField**, entrada; etiqueta/prompt: “User”; `onAction`: `—`.
- `xmlSignTsaPasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Password/Token (Ephemeral)”; `onAction`: `—`.
- `xmlSignTsaProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Saved TSA profile”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load”; `onAction`: `#handleLoadTSASavedProfile`.
- `—` — **Button**, acción; etiqueta/prompt: “Delete”; `onAction`: `#handleDeleteTSASavedProfile`.
- `xmlSignTsaProfileNameField` — **TextField**, entrada; etiqueta/prompt: “Profile name”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Profile”; `onAction`: `#handleSaveTSASavedProfile`.
- `—` — **Button**, acción; etiqueta/prompt: “Sign XML”; `onAction`: `#handleSignXML`.
- `xmlSignOutputArea` — **TextArea**, entrada; etiqueta/prompt: “Signed XML Output:”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Signed XML...”; `onAction`: `#handleSaveSignedXML`.
**🔬 Inspect Signed XML**
- `xmlInspectInputArea` — **TextArea**, entrada; etiqueta/prompt: “Signed XML to inspect (local structural analysis, no validation):”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Open XML...”; `onAction`: `#handleBrowseXMLInspectorInput`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect Signed XML”; `onAction`: `#handleInspectSignedXML`.
- `xmlInspectReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Signature structure report:”; `onAction`: `—`; editable=false.
**⏱ RFC 3161 Timestamp**
- `xmlTimestampFileField` — **TextField**, entrada; etiqueta/prompt: “xmlTimestampFileField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Open...”; `onAction`: `#handleBrowseTimestampFile`.
- `xmlTimestampHashCombo` — **ComboBox**, entrada; etiqueta/prompt: “Imprint hash:”; `onAction`: `—`.
- `xmlTimestampUrlField` — **TextField**, entrada; etiqueta/prompt: “https://your-tsa.example/”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Request Timestamp”; `onAction`: `#handleRequestTimestamp`.
- `—` — **Button**, acción; etiqueta/prompt: “Save Token...”; `onAction`: `#handleSaveTimestampToken`.
- `xmlTimestampTokenField` — **TextField**, entrada; etiqueta/prompt: “Path to .tsq/.tst”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Open...”; `onAction`: `#handleBrowseTimestampToken`.
- `xmlTimestampTrustStoreField` — **TextField**, entrada; etiqueta/prompt: “Truststore path”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseTimestampTrustStore`.
- `xmlTimestampTrustStorePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “Password”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Inspect Token”; `onAction`: `#handleInspectTimestampToken`.
- `—` — **Button**, acción; etiqueta/prompt: “Validate Token”; `onAction`: `#handleValidateTimestampToken`.
- `xmlTimestampReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “RFC 3161 token report:”; `onAction`: `—`; editable=false.
**🔍 Verify XML (XAdES)**
- `xmlVerifyInputArea` — **TextArea**, entrada; etiqueta/prompt: “Paste Signed XML Content:”; `onAction`: `—`.
- `xmlVerifyTrustStoreProfileCombo` — **ComboBox**, entrada; etiqueta/prompt: “Saved KeyStore profile”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Load”; `onAction`: `#handleLoadXMLTrustStoreProfile`.
- `xmlVerifyTrustStorePathField` — **TextField**, entrada; etiqueta/prompt: “xmlVerifyTrustStorePathField”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “...”; `onAction`: `#handleBrowseXMLTrustStore`.
- `xmlVerifyTrustStorePasswordField` — **PasswordField**, entrada; etiqueta/prompt: “TrustStore password”; `onAction`: `—`.
- `—` — **Button**, acción; etiqueta/prompt: “Verify XML”; `onAction`: `#handleVerifyXML`.
- `xmlVerifyReportArea` — **TextArea**, salida/read-only; etiqueta/prompt: “Verification Report:”; `onAction`: `—`; editable=false.

## Toolbar global y herramientas auxiliares

La barra global vive en `main-view-modern.fxml` y se incluye como un grupo propio en el inventario: formatos del payload/resultado, entrada/salida, acciones de sesión, inspector, historial, búsqueda/command palette, panel lateral y menús globales. Los items de menú identificados son acciones, aunque no todos tengan `onAction` en FXML (algunos usan `onAction` heredado/handlers Java o items nativos).

Herramientas auxiliares principales: `clipboard_shelf.fxml`, `history.fxml`, `compare_results.fxml`, `compressed_hex.fxml`, `crypto_envelope_inspector.fxml`, `key_certificate_workbench.fxml`, `pkcs11_profiles.fxml` y `process_designer.fxml`; cada una aparece con la misma ruta de sección y controles.

## Process Designer

La sección dedicada está en `process_designer.fxml`. El inventario incluye controles de canvas/paleta, presets, búsqueda, nombre/ajustes de bloque, conexión y acciones, columnas de ejecución y área de resultados. Los campos de configuración de nodos pueden generarse en tiempo de ejecución desde `NodeParameter`; por tanto no todos aparecen como controles individuales en FXML. Para help contextual de parámetros de nodo, consultar `NodeParameter.helpKey`, opciones y visibilidad condicional en el modelo/controlador del Process Designer. Las columnas Step/Operation/Input/Output/Status/Duration son resultados tabulares; su editabilidad puede depender de la instancia/configuración y no se declara de modo uniforme en FXML.

## Notas para vincular FieldHelp

- Priorizar `fx:id` donde exista. En varias pantallas antiguas el FXML carece de IDs en la mayoría de entradas; este inventario preserva `—` para visibilizar deuda de identificadores, no propone IDs inventados.
- Para campos generados (Process Designer) enlazar por ID semántico de parámetro/nodo, usando `helpKey`; no depender del texto localizado.
- Para salida, ofrecer ayuda de interpretación asociada a la sección/acción. Para entradas con varias acciones (por ejemplo Verify/Sign), la misma área puede cambiar de rol según la acción; confirmar el controller antes de marcarla como salida fija.
- Selector vacío en XML no significa ausencia de opciones: requiere inspección del controlador/coordinador. Los handlers aportan un punto de partida para localizar su población. Este inventario no valida proveedor, combinaciones, tamaños criptográficos ni resultados.
- FXML no declara por sí solo todos los listeners, estados condicionales, diálogos, controles creados en runtime ni lógica de teclado. La etiqueta/prompt puede ser provisional, localizada después o estar ausente; corroborar con el controller en el trabajo editorial.

## Alcance y limitaciones

Cobertura XML: 30/30 archivos `.fxml` enumerados en el directorio (incluye `main-view-modern.fxml`; excluye archivos históricos bajo `docs/legacy-fxml/`). El inventario se genera desde XML y no ejecuta la UI. No se invocaron pruebas ni Maven. Los IDs exactos son los declarados por FXML; un control con `—` requiere resolverlo en controlador o etiquetarlo en una implementación futura.

## Addendum B05 — enlaces de ayuda contextual

La implementación B05 añade IDs de enlace a las acciones de `generic.fxml` y coloca botones `?` junto a controles de Hashing, Manual Conversion, Batch Runner, File Conversion, Random, Check Digits y Modular Arithmetic. Los diálogos Java de Epoch, JSON y Byte Inspector enlazan sus campos visibles al mismo estado contextual. UUID distingue generar y copiar. `CompressedHexController`, `KeyCertificateWorkbenchController`, `CryptoEnvelopeInspectorController` y `ClipboardShelfController` declaran enlaces propios. Process Designer crea los enlaces de parámetros al renderizar el inspector: usa `NodeParameter.key/helpKey/options/visibleWhen/sensitive` y la selección de nodo activa.

Los IDs semánticos y el estado implementado están descritos en [la matriz B05](AYUDA_CONTEXTUAL_MATRIZ_IMPLEMENTACION.md#b05--subentregas-de-catálogo-e-integración-contextual-en-revisión). En B05.1 se confirma que Compressed Hex usa una entrada y salida compartidas por Expandir/Comprimir; UUID es un panel anidado que activa `aux_uuid` al expandirse. Hashing usa el selector global de formatos; Manual Conversion conserva parsers propios por acción; las opciones de archivo, word size, algoritmo/operación y salida aleatoria proceden de sus listas de consumidores. Al ejecutar Manual, File Conversion o Check Digits, el controlador registra la acción intentada para que la ayuda directa de campos compartidos la siga; la selección explícita de acción permanece hasta esa ejecución. Random usa el selector en la siguiente generación y no vuelve a formatear una salida ya visible. La ficha y referencia Check Digits diferencian Luhn/AMEX SE/Verhoeff/Damm, entrada base/valor completo, mínimo de cifras y ceros iniciales. La matriz B05.1 enumera acciones, campos, opciones, referencias y límites de cobertura. B05.1 está aprobada; B05.2 ya incorpora ayuda por operación de lote y por descriptor live de nodo, opción, parámetro, condición y contrato de puertos. B05.3 y los auxiliares A004–A006 permanecen pendientes.
