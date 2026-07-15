package com.cryptoforge.ui;

import com.cryptoforge.crypto.*;
import com.cryptoforge.model.OperationResult;
import com.cryptoforge.model.AppSettings;
import com.cryptoforge.util.DataConverter;
import com.cryptoforge.utils.OperationHistory;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Controller for Keys tab - Enhanced with asymmetric cryptography
 *
 * @author Felipe
 */
public class KeysController {

    private StatusReporter mainController;

    // Symmetric Key Generation components
    private ComboBox<String> keyTypeCombo;
    private javafx.scene.control.CheckBox forceOddParityCheck;
    private TextArea generatedKeyField;

    // Key Validation components
    private TextField keyInputField;
    private TextArea validationResultArea;

    // Key material inspection
    private TextArea keyMaterialInputArea;
    private TextArea keyMaterialReportArea;
    private TextArea keyComparePublicArea;
    private TextArea keyComparePrivateArea;
    private TextArea keyCompareResultArea;
    private ComboBox<String> keyStoreTypeCombo;
    private PasswordField keyStorePasswordField;
    private CheckBox keyStoreUnsafeExtractCheck;
    private TextField keyStorePathField;
    private TextArea keyStoreReportArea;
    private ComboBox<String> keyStoreProfileCombo;
    private TextField keyStoreProfileNameField;

    // Key Sharing components
    private ComboBox<String> numComponentsCombo;
    private TextArea keyToSplitField;
    private TextArea componentResultsArea;
    private TextField component1Field;
    private TextField component2Field;
    private TextField component3Field;
    private TextField component4Field;
    private TextField component5Field;

    // Key Derivation components
    private ComboBox<String> kdfAlgorithmCombo;
    private ComboBox<String> kdfInputFormatCombo;
    private ComboBox<String> kdfSaltFormatCombo;
    private ComboBox<String> kdfInfoFormatCombo;
    private TextField kdfInputField;
    private TextField kdfSaltField;
    private TextField kdfInfoField;
    private TextField kdfIterationsField;
    private TextField kdfOutputLengthField;
    private TextArea kdfResultArea;

    // AES Key Wrap components
    private ComboBox<String> keyWrapModeCombo;
    private CheckBox keyWrapUnwrapCheck;
    private TextField keyWrapKekField;
    private TextField keyWrapDataField;
    private TextArea keyWrapResultArea;

    // RSA Generation components
    private ComboBox<Integer> rsaKeySizeCombo;
    private TextArea rsaPublicKeyArea;
    private TextArea rsaPrivateKeyArea;

    // DSA Generation components
    private ComboBox<String> dsaKeySizeCombo;
    private TextArea dsaPublicKeyArea;
    private TextArea dsaPrivateKeyArea;

    // ECDSA F(p) components
    private ComboBox<String> ecdsaFpCurveCombo;
    private TextArea ecdsaFpPublicKeyArea;
    private TextArea ecdsaFpPrivateKeyArea;

    // Ed25519 components
    private TextArea ed25519PublicKeyArea;
    private TextArea ed25519PrivateKeyArea;

    // Certificate Generation components
    private TextField certCNField;
    private TextField certOrgField;
    private TextField certOUField;
    private TextField certLocalityField;
    private TextField certStateField;
    private TextField certCountryField;
    private TextField certEmailField;
    private TextField certValidityField;
    private ComboBox<String> certKeyTypeCombo;
    private ComboBox<String> certSignAlgoCombo;
    private TextArea certOutputArea;
    private TextField certSanDnsField;
    private TextField certSanIpField;
    private CheckBox certRootCaCheck;

    // Certificate Parsing components
    private TextArea certInputArea;
    private TextArea certParseResultArea;
    private TextArea certCompareLeftArea;
    private TextArea certCompareRightArea;
    private TextArea certCompareResultArea;
    private TextArea certIssueCsrArea;
    private TextArea certIssueCaCertArea;
    private TextArea certIssueCaKeyArea;
    private TextField certIssueValidityField;
    private TextField certIssueSignatureField;
    private TextArea certIssueResultArea;
    private CheckBox certIssueIntermediateCaCheck;
    private TextField certIssuePathLengthField;

    // Validate Certificate components
    private TextArea valCertInput;
    private TextArea valIssuerInput;
    private TextArea valResultArea;

    // Store last generated key pair for certificate generation
    private KeyPair lastGeneratedKeyPair;
    private String lastKeyType;

    public KeyPair getLastGeneratedKeyPair() {
        return lastGeneratedKeyPair;
    }

    private void showError(String title, String message) {
        if (mainController != null) mainController.showError(title, message);
    }

    private void updateStatus(String message) {
        if (mainController != null) mainController.updateStatus(message);
    }

    /**
     * Initialize the controller - Symmetric keys
     */
    public void initialize(StatusReporter mainController,
            ComboBox<String> keyTypeCombo,
            javafx.scene.control.CheckBox forceOddParityCheck,
            TextArea generatedKeyField,
            TextField keyInputField,
            TextArea validationResultArea,
            ComboBox<String> numComponentsCombo,
            TextArea keyToSplitField,
            TextArea componentResultsArea,
            TextField component1Field,
            TextField component2Field,
            TextField component3Field,
            TextField component4Field,
            TextField component5Field) {

        this.mainController = mainController;
        this.keyTypeCombo = keyTypeCombo;
        this.forceOddParityCheck = forceOddParityCheck;
        this.generatedKeyField = generatedKeyField;
        this.keyInputField = keyInputField;
        this.validationResultArea = validationResultArea;
        this.numComponentsCombo = numComponentsCombo;
        this.keyToSplitField = keyToSplitField;
        this.componentResultsArea = componentResultsArea;
        this.component1Field = component1Field;
        this.component2Field = component2Field;
        this.component3Field = component3Field;
        this.component4Field = component4Field;
        this.component5Field = component5Field;

        // Populate combo boxes
        keyTypeCombo.getItems().addAll("DES", "3DES-2KEY", "3DES-3KEY", "AES-128", "AES-192", "AES-256");
        keyTypeCombo.setValue("3DES-2KEY");

        numComponentsCombo.getItems().addAll("2", "3", "4", "5");
        numComponentsCombo.setValue("2");
    }

    public void initializeKeyMaterialInspector(TextArea inputArea, TextArea reportArea) {
        this.keyMaterialInputArea = inputArea;
        this.keyMaterialReportArea = reportArea;
    }

    public void initializeKeyPairComparator(TextArea publicArea, TextArea privateArea, TextArea resultArea) {
        this.keyComparePublicArea = publicArea;
        this.keyComparePrivateArea = privateArea;
        this.keyCompareResultArea = resultArea;
    }

    public void initializeKeyStoreInspector(ComboBox<String> typeCombo, PasswordField passwordField, CheckBox unsafeExtractCheck,
            TextField pathField, TextArea reportArea, ComboBox<String> profileCombo, TextField profileNameField) {
        this.keyStoreTypeCombo = typeCombo;
        this.keyStorePasswordField = passwordField;
        this.keyStoreUnsafeExtractCheck = unsafeExtractCheck;
        this.keyStorePathField = pathField;
        this.keyStoreReportArea = reportArea;
        this.keyStoreProfileCombo = profileCombo;
        this.keyStoreProfileNameField = profileNameField;
        typeCombo.getItems().setAll("Auto", "PKCS12", "JKS", "JCEKS");
        typeCombo.setValue("Auto");
        refreshKeyStoreProfiles();
    }

    /** Inspects PEM keys and certificates without modifying them. */
    public void handleInspectKeyMaterial() {
        try {
            String pem = keyMaterialInputArea.getText().trim();
            if (pem.isEmpty()) throw new IllegalArgumentException("Paste PEM key or certificate material first");
            String report;
            if (pem.contains("BEGIN CERTIFICATE")) {
                var factory = java.security.cert.CertificateFactory.getInstance("X.509");
                var certificate = (java.security.cert.X509Certificate) factory.generateCertificate(
                        new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
                report = KeyMaterialInspector.describeCertificate(certificate);
            } else if (pem.contains("PRIVATE KEY")) {
                java.security.PrivateKey key = AsymmetricKeyOperations.importPrivateKeyPEMAuto(pem);
                report = KeyMaterialInspector.describeKey(key);
            } else if (pem.contains("BEGIN PUBLIC KEY")) {
                java.security.PublicKey key = AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
                report = KeyMaterialInspector.describeKey(key);
            } else {
                throw new IllegalArgumentException("Recognized PEM headers are PUBLIC KEY, EC/PRIVATE KEY and CERTIFICATE");
            }
            keyMaterialReportArea.setText(report);
            updateStatus("Key material inspected successfully");
        } catch (Exception e) {
            showError("Key Material Inspector", "Cannot inspect material: " + e.getMessage());
        }
    }

    public void handleCompareKeyPair() {
        try {
            java.security.PublicKey publicKey = parsePublicMaterial(keyComparePublicArea.getText().trim());
            java.security.PrivateKey privateKey = parsePrivateMaterial(keyComparePrivateArea.getText().trim());
            boolean matches = KeyMaterialInspector.matches(publicKey, privateKey);
            keyCompareResultArea.setText("========================================\nKEY PAIR COMPARISON\n========================================\n\n"
                    + "Public algorithm: " + publicKey.getAlgorithm() + "\nPrivate algorithm: " + privateKey.getAlgorithm() + "\n"
                    + "Public SHA-256: " + KeyMaterialInspector.fingerprint(publicKey.getEncoded()) + "\n\n"
                    + (matches ? "✓ MATCH: the private key successfully signed a challenge verified by the public key."
                            : "✗ NO MATCH: signature verification failed or the algorithms are incompatible."));
            updateStatus(matches ? "Key pair comparison: match" : "Key pair comparison: no match");
        } catch (Exception e) {
            showError("Compare Key Pair", "Cannot compare material: " + e.getMessage());
        }
    }

    public void handleInspectKeyStore() {
        char[] password = keyStorePasswordField.getText().toCharArray();
        try {
            boolean unsafe = keyStoreUnsafeExtractCheck.isSelected();
            var report = KeyStoreInspector.inspect(java.nio.file.Path.of(keyStorePathField.getText().trim()), password,
                    keyStoreTypeCombo.getValue(), unsafe);
            StringBuilder text = new StringBuilder("========================================\nKEYSTORE REPORT\n========================================\n\n")
                    .append("Type: ").append(report.type()).append("\nEntries: ").append(report.entries().size()).append("\n")
                    .append(unsafe ? "⚠️ UNSAFE EXTRACTION ENABLED — do not use this mode in production.\n\n" : "\n");
            for (var entry : report.entries()) {
                text.append("Alias: ").append(entry.alias()).append("\nType: ").append(entry.kind())
                        .append("\nAlgorithm: ").append(entry.algorithm());
                if (!entry.subject().isEmpty()) text.append("\nSubject: ").append(entry.subject());
                if (!entry.fingerprint().equals("Not exposed")) text.append("\nSHA-256: ").append(entry.fingerprint());
                if (unsafe && !entry.keyMaterial().equals("Not requested")) text.append("\nEXPORTED KEY (HEX): ").append(entry.keyMaterial());
                text.append("\n----------------------------------------\n");
            }
            keyStoreReportArea.setText(text.toString());
            updateStatus("KeyStore inspected: " + report.entries().size() + " entries");
        } catch (Exception e) {
            showError("KeyStore Inspector", "Cannot inspect keystore: " + e.getMessage());
        } finally {
            java.util.Arrays.fill(password, '\0');
            keyStorePasswordField.clear();
        }
    }

    public void chooseKeyStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PKCS#12, JKS or JCEKS KeyStore");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("KeyStores", "*.p12", "*.pfx", "*.jks", "*.jceks"),
                new FileChooser.ExtensionFilter("All files", "*"));
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null) keyStorePathField.setText(selected.getAbsolutePath());
    }

    public void saveKeyStoreProfile() {
        try {
            AppSettings.getInstance().saveTrustStoreProfile(keyStoreProfileNameField.getText(), keyStorePathField.getText(), keyStoreTypeCombo.getValue());
            refreshKeyStoreProfiles();
            keyStoreProfileCombo.setValue(keyStoreProfileNameField.getText().trim());
            updateStatus("KeyStore profile saved (password not stored)");
        } catch (Exception e) {
            showError("KeyStore Profile", e.getMessage());
        }
    }

    public void loadKeyStoreProfile() {
        String name = keyStoreProfileCombo.getValue();
        if (name == null || name.isBlank()) return;
        AppSettings.getInstance().getTrustStoreProfiles().stream().filter(profile -> name.equals(profile.name())).findFirst().ifPresent(profile -> {
            keyStorePathField.setText(profile.path());
            keyStoreTypeCombo.setValue(profile.type());
            keyStorePasswordField.clear();
            updateStatus("KeyStore profile loaded; enter password to inspect");
        });
    }

    private void refreshKeyStoreProfiles() {
        if (keyStoreProfileCombo == null) return;
        keyStoreProfileCombo.getItems().setAll(AppSettings.getInstance().getTrustStoreProfiles().stream()
                .map(AppSettings.TrustStoreProfile::name).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private java.security.PublicKey parsePublicMaterial(String pem) throws Exception {
        if (pem.isBlank()) throw new IllegalArgumentException("Public key or certificate is required");
        if (pem.contains("BEGIN CERTIFICATE")) {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            return ((java.security.cert.X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))).getPublicKey();
        }
        return AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
    }

    private java.security.PrivateKey parsePrivateMaterial(String pem) throws Exception {
        if (pem.isBlank()) throw new IllegalArgumentException("Private key is required");
        if (pem.contains("ED25519")) return AsymmetricKeyOperations.importEd25519PrivateKeyPEM(pem);
        if (pem.contains("EC PRIVATE")) return AsymmetricKeyOperations.importECPrivateKeyPEM(pem);
        return AsymmetricKeyOperations.importPrivateKeyPEMAuto(pem);
    }

    /**
     * Initialize RSA components
     */
    public void initializeRSA(ComboBox<Integer> keySizeCombo, TextArea publicArea, TextArea privateArea) {
        this.rsaKeySizeCombo = keySizeCombo;
        this.rsaPublicKeyArea = publicArea;
        this.rsaPrivateKeyArea = privateArea;

        rsaKeySizeCombo.getItems().addAll(AsymmetricKeyOperations.RSA_KEY_SIZES);
        rsaKeySizeCombo.setValue(2048);
    }

    /**
     * Initialize DSA components
     */
    public void initializeDSA(ComboBox<String> keySizeCombo, TextArea publicArea, TextArea privateArea) {
        this.dsaKeySizeCombo = keySizeCombo;
        this.dsaPublicKeyArea = publicArea;
        this.dsaPrivateKeyArea = privateArea;

        dsaKeySizeCombo.getItems().addAll(AsymmetricKeyOperations.DSA_KEY_SIZES);
        dsaKeySizeCombo.setValue("2048/256");
    }

    /**
     * Initialize ECDSA F(p) components
     */
    public void initializeECDSAFp(ComboBox<String> curveCombo, TextArea publicArea, TextArea privateArea) {
        this.ecdsaFpCurveCombo = curveCombo;
        this.ecdsaFpPublicKeyArea = publicArea;
        this.ecdsaFpPrivateKeyArea = privateArea;

        ecdsaFpCurveCombo.getItems().addAll(AsymmetricKeyOperations.ECDSA_FP_NAMED_CURVES);
        ecdsaFpCurveCombo.setValue("secp256r1");
    }

    /**
     * Initialize Ed25519 components
     */
    public void initializeEd25519(TextArea publicArea, TextArea privateArea) {
        this.ed25519PublicKeyArea = publicArea;
        this.ed25519PrivateKeyArea = privateArea;
    }

    /**
     * Initialize ECDSA F(2^m) components
     */

    /**
     * Initialize Certificate Generator components
     */
    public void initializeCertificateGen(
            TextField cnField, TextField orgField, TextField ouField,
            TextField localityField, TextField stateField, TextField countryField,
            TextField emailField, TextField validityField, ComboBox<String> keyTypeCombo,
            ComboBox<String> signAlgoCombo, TextArea outputArea, TextField sanDnsField, TextField sanIpField, CheckBox rootCaCheck) {

        this.certCNField = cnField;
        this.certOrgField = orgField;
        this.certOUField = ouField;
        this.certLocalityField = localityField;
        this.certStateField = stateField;
        this.certCountryField = countryField;
        this.certEmailField = emailField;
        this.certValidityField = validityField;
        this.certKeyTypeCombo = keyTypeCombo;
        this.certSignAlgoCombo = signAlgoCombo;
        this.certOutputArea = outputArea;
        this.certSanDnsField = sanDnsField;
        this.certSanIpField = sanIpField;
        this.certRootCaCheck = rootCaCheck;

        certKeyTypeCombo.getItems().addAll("RSA-2048", "RSA-4096", "ECDSA-P256", "ECDSA-P384");
        certKeyTypeCombo.setValue("RSA-2048");

        certSignAlgoCombo.getItems().addAll("SHA256withRSA", "SHA384withRSA", "SHA512withRSA");
        certSignAlgoCombo.setValue("SHA256withRSA");

        certValidityField.setText("365");
    }

    /** Compatibility entry point for the classic UI, which has no SAN controls. */
    public void initializeCertificateGen(
            TextField cnField, TextField orgField, TextField ouField,
            TextField localityField, TextField stateField, TextField countryField,
            TextField emailField, TextField validityField, ComboBox<String> keyTypeCombo,
            ComboBox<String> signAlgoCombo, TextArea outputArea) {
        initializeCertificateGen(cnField, orgField, ouField, localityField, stateField, countryField, emailField,
                validityField, keyTypeCombo, signAlgoCombo, outputArea, null, null, null);
    }

    /**
     * Initialize Certificate Parsing components
     */
    public void initializeCertificateParse(TextArea inputArea, TextArea resultArea) {
        this.certInputArea = inputArea;
        this.certParseResultArea = resultArea;
    }

    public void initializeCertificateComparator(TextArea leftArea, TextArea rightArea, TextArea resultArea) {
        this.certCompareLeftArea = leftArea;
        this.certCompareRightArea = rightArea;
        this.certCompareResultArea = resultArea;
    }

    public void initializeCertificateIssuer(TextArea csrArea, TextArea caCertArea, TextArea caKeyArea,
            TextField validityField, TextField signatureField, TextArea resultArea, CheckBox intermediateCaCheck,
            TextField pathLengthField) {
        this.certIssueCsrArea = csrArea;
        this.certIssueCaCertArea = caCertArea;
        this.certIssueCaKeyArea = caKeyArea;
        this.certIssueValidityField = validityField;
        this.certIssueSignatureField = signatureField;
        this.certIssueResultArea = resultArea;
        this.certIssueIntermediateCaCheck = intermediateCaCheck;
        this.certIssuePathLengthField = pathLengthField;
    }

    public void handleIssueCertificateFromCsr() {
        try {
            String csrPem = certIssueCsrArea.getText().trim();
            String compact = csrPem.replaceAll("-----[^-]+-----|\\s", "");
            var csr = new org.bouncycastle.pkcs.PKCS10CertificationRequest(java.util.Base64.getDecoder().decode(compact));
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var issuerCert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    certIssueCaCertArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var issuerKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(certIssueCaKeyArea.getText().trim());
            int validity = Integer.parseInt(certIssueValidityField.getText().trim());
            String requestedSignature = certIssueSignatureField.getText().trim();
            String signatureAlgorithm = requestedSignature.isEmpty() || "automatic".equalsIgnoreCase(requestedSignature)
                    ? CertificateAuthorityOperations.suggestSignatureAlgorithm(issuerKey) : requestedSignature;
            certIssueSignatureField.setText(signatureAlgorithm);
            boolean intermediate = certIssueIntermediateCaCheck.isSelected();
            var issued = intermediate
                    ? CertificateAuthorityOperations.issueIntermediateCaFromCsr(csr, issuerCert, issuerKey, validity,
                            signatureAlgorithm, Integer.parseInt(certIssuePathLengthField.getText().trim()))
                    : CertificateAuthorityOperations.issueFromCsr(csr, issuerCert, issuerKey, validity,
                            signatureAlgorithm);
            certIssueResultArea.setText(intermediate ? "=== ISSUED INTERMEDIATE CA ===\n\n" : "=== ISSUED END-ENTITY CERTIFICATE ===\n\n"
                    + CertificateGenerator.getCertificateInfo(issued)
                    + "\n\n" + CertificateGenerator.exportCertificatePEM(issued));
            updateStatus("Certificate issued from validated CSR");
        } catch (Exception e) {
            showError("Issue Certificate", "Cannot issue certificate: " + e.getMessage());
        }
    }

    public void handleCompareCertificates() {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var left = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    certCompareLeftArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var right = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    certCompareRightArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            certCompareResultArea.setText(CertificateComparator.compare(left, right));
            updateStatus("Certificates compared");
        } catch (Exception e) {
            showError("Compare Certificates", "Cannot compare certificates: " + e.getMessage());
        }
    }

    /**
     * Initialize Validate Certificate components
     */
    public void initializeValidateCertificate(TextArea valCertInput, TextArea valIssuerInput, TextArea valResultArea) {
        this.valCertInput = valCertInput;
        this.valIssuerInput = valIssuerInput;
        this.valResultArea = valResultArea;
    }

    /**
     * Initialize Validate Chain components
     */
    public void initializeValidateChain() {
        // No components to initialize for now
    }

    /**
     * Generate a random key
     */
    public void handleGenerateKey() {
        try {
            String keyType = keyTypeCombo.getValue();
            if (keyType == null) {
                showError("Input Error", "Please select a key type");
                return;
            }

            boolean forceParity = forceOddParityCheck.isSelected();
            byte[] key = KeyOperations.generateKey(keyType, forceParity);
            String keyHex = DataConverter.bytesToHex(key);
            generatedKeyField.setText(keyHex);

            String parityStatus = forceParity ? " with odd parity" : " without parity adjustment";
            updateStatus("Generated " + keyType + " key" + parityStatus);

            // Calculate KCVs for history
            StringBuilder historyDetails = new StringBuilder();
            historyDetails.append("Key Type: ").append(keyType).append("\n");
            historyDetails.append("Generated Key: ").append(keyHex).append("\n");

            try {
                if (keyType.contains("DES") || keyType.contains("3DES")) {
                    byte[] kcv = KeyOperations.calculateKCV_VISA(key);
                    historyDetails.append("KCV (VISA): ").append(DataConverter.bytesToHex(kcv));
                } else {
                    byte[] kcv = KeyOperations.calculateKCV_AES(key);
                    historyDetails.append("KCV (AES): ").append(DataConverter.bytesToHex(kcv));
                }
            } catch (Exception e) {
                historyDetails.append("KCV: Error calculating");
            }

            // Delegate to ModernMainController history if available
            if (mainController != null) {
                try {
                    java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Key Type", keyType));
                    details.add(com.cryptoforge.model.OperationDetail.secretDetail("Generated Key", keyHex));
                    try {
                        if (keyType.contains("DES") || keyType.contains("3DES")) {
                            byte[] kcv = KeyOperations.calculateKCV_VISA(key);
                            details.add(com.cryptoforge.model.OperationDetail.publicDetail("KCV (VISA)", DataConverter.bytesToHex(kcv)));
                        } else {
                            byte[] kcv = KeyOperations.calculateKCV_AES(key);
                            details.add(com.cryptoforge.model.OperationDetail.publicDetail("KCV (AES)", DataConverter.bytesToHex(kcv)));
                        }
                    } catch (Exception e) {
                        details.add(com.cryptoforge.model.OperationDetail.publicDetail("KCV", "Error calculating"));
                    }

                    mainController.addToHistory("Generate Symmetric Key", details);
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                OperationHistory.getInstance().addOperation(
                        "Keys",
                        "Generate Symmetric Key",
                        keyType,
                        "Key: " + keyHex);
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating key: " + e.getMessage());
        }
    }

    /**
     * Validate a key and calculate all KCVs
     */
    public void handleValidateKey() {
        try {
            String keyHex = keyInputField.getText().trim();
            if (keyHex.isEmpty()) {
                showError("Input Error", "Please enter a key in hexadecimal");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);

            if (!KeyOperations.isValidKeyLength(key)) {
                showError("Validation Error",
                        "Invalid key length. Key must be 8, 16, 24, or 32 bytes (16, 32, 48, or 64 hex characters)");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("KEY VALIDATION RESULTS\n");
            result.append("========================================\n\n");

            result.append("Key: ").append(keyHex).append("\n");
            result.append("Key Length: ").append(key.length).append(" bytes (")
                    .append(key.length * 8).append(" bits)\n");
            result.append("Key Type: ").append(KeyOperations.getKeyType(key)).append("\n\n");

            // Detect parity
            KeyOperations.ParityType parity = KeyOperations.detectParity(key);
            result.append("Parity Detected: ").append(parity).append("\n\n");

            // Calculate all KCVs
            result.append("----------------------------------------\n");
            result.append("KEY CHECK VALUES (KCV)\n");
            result.append("----------------------------------------\n\n");

            try {
                byte[] kcvVisa = KeyOperations.calculateKCV_VISA(key);
                result.append("KCV (VISA):     ").append(DataConverter.bytesToHex(kcvVisa)).append("\n");
            } catch (Exception e) {
                result.append("KCV (VISA):     Error - ").append(e.getMessage()).append("\n");
            }

            try {
                byte[] kcvAtalla = KeyOperations.calculateKCV_ATALLA(key);
                result.append("KCV (ATALLA):   ").append(DataConverter.bytesToHex(kcvAtalla)).append("\n\n");
            } catch (Exception e) {
                result.append("KCV (ATALLA):   Error - ").append(e.getMessage()).append("\n\n");
            }

            result.append("--- Modern Methods ---\n\n");

            try {
                byte[] kcvSha256 = KeyOperations.calculateKCV_SHA256(key);
                result.append("KCV (SHA256):   ").append(DataConverter.bytesToHex(kcvSha256)).append("\n");
            } catch (Exception e) {
                result.append("KCV (SHA256):   Error - ").append(e.getMessage()).append("\n");
            }

            try {
                byte[] kcvCMAC = KeyOperations.calculateKCV_CMAC(key);
                result.append("KCV (CMAC):     ").append(DataConverter.bytesToHex(kcvCMAC)).append("\n");
            } catch (Exception e) {
                result.append("KCV (CMAC):     Error - ").append(e.getMessage()).append("\n");
            }

            // Only calculate AES KCV for AES keys
            if (key.length == 16 || key.length == 24 || key.length == 32) {
                try {
                    byte[] kcvAES = KeyOperations.calculateKCV_AES(key);
                    result.append("KCV (AES):      ").append(DataConverter.bytesToHex(kcvAES)).append("\n");
                } catch (Exception e) {
                    result.append("KCV (AES):      Error - ").append(e.getMessage()).append("\n");
                }
            }

            result.append("\n========================================\n");

            validationResultArea.setText(result.toString());
            validationResultArea.setVisible(true);
            validationResultArea.setManaged(true);
            updateStatus("Key validated successfully");

            // Delegate to ModernMainController history if available
            if (mainController != null && mainController.getClass().getSimpleName().equals("ModernMainController")) {
                try {
                    java.util.Map<String, String> details = new java.util.HashMap<>();
                    details.put("Operation", "Validate Symmetric Key");
                    details.put("Validation Output", result.toString());

                    java.lang.reflect.Method method = mainController.getClass().getMethod("addToHistory",
                            String.class, java.util.Map.class);
                    method.invoke(mainController, "Validate Symmetric Key", details);
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                // Add to history (Legacy)
                String keyType = KeyOperations.getKeyType(key);
                OperationHistory.getInstance().addOperation(
                        "Keys",
                        "Validate - " + keyType,
                        "Key: " + keyHex,
                        result.toString() // Full validation results with all KCVs
                );
            }

        } catch (IllegalArgumentException e) {
            showError("Input Error", e.getMessage());
        } catch (Exception e) {
            showError("Validation Error", "Error validating key: " + e.getMessage());
        }
    }

    /**
     * Split a key into components
     */
    public void handleSplitKey() {
        try {
            String keyHex = keyToSplitField.getText().trim();
            if (keyHex.isEmpty()) {
                showError("Input Error", "Please enter a key to split");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);

            if (!KeyOperations.isValidKeyLength(key)) {
                showError("Validation Error",
                        "Invalid key length. Key must be 8, 16, 24, or 32 bytes");
                return;
            }

            int numComponents = Integer.parseInt(numComponentsCombo.getValue());

            byte[][] components = KeyOperations.splitKey(key, numComponents);

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("KEY SPLITTING RESULTS\n");
            result.append("========================================\n\n");

            result.append("Original Key: ").append(keyHex).append("\n");
            result.append("Number of Components: ").append(numComponents).append("\n\n");

            result.append("Components (XOR these to get original key):\n\n");
            for (int i = 0; i < numComponents; i++) {
                String componentHex = DataConverter.bytesToHex(components[i]);
                result.append("Component ").append(i + 1).append(": ").append(componentHex).append("\n");

                // Also set in individual text fields for easy copying
                switch (i) {
                    case 0:
                        component1Field.setText(componentHex);
                        break;
                    case 1:
                        component2Field.setText(componentHex);
                        break;
                    case 2:
                        component3Field.setText(componentHex);
                        break;
                    case 3:
                        component4Field.setText(componentHex);
                        break;
                    case 4:
                        component5Field.setText(componentHex);
                        break;
                }
            }

            // Clear unused component fields
            if (numComponents < 3)
                component3Field.setText("");
            if (numComponents < 4)
                component4Field.setText("");
            if (numComponents < 5)
                component5Field.setText("");

            result.append("\n");

            // Calculate KCV of original key
            try {
                byte[] kcv = KeyOperations.calculateKCV_VISA(key);
                result.append("Original Key KCV (VISA): ").append(DataConverter.bytesToHex(kcv)).append("\n");
            } catch (Exception e) {
                // Ignore
            }

            result.append("\n========================================\n");
            result.append("ℹ️  XOR all components together to reconstruct the original key\n");
            result.append("ℹ️  Each component should be stored securely in separate locations\n");

            componentResultsArea.setText(result.toString());
            componentResultsArea.setVisible(true);
            componentResultsArea.setManaged(true);
            updateStatus("Key split into " + numComponents + " components");

            // Add to history
            OperationHistory.getInstance().addOperation(
                    "Keys",
                    "Split - " + numComponents + " components",
                    "Input Key: " + keyHex,
                    result.toString() // Full components output
            );

        } catch (NumberFormatException e) {
            showError("Input Error", "Invalid number of components");
        } catch (Exception e) {
            showError("Splitting Error", "Error splitting key: " + e.getMessage());
        }
    }

    /**
     * Combine key components back into original key
     */
    public void handleCombineComponents() {
        try {
            String comp1 = component1Field.getText().trim();
            String comp2 = component2Field.getText().trim();

            if (comp1.isEmpty() || comp2.isEmpty()) {
                showError("Input Error", "Please enter at least 2 components");
                return;
            }

            // Collect all non-empty components
            java.util.List<byte[]> componentList = new java.util.ArrayList<>();
            componentList.add(DataConverter.hexToBytes(comp1));
            componentList.add(DataConverter.hexToBytes(comp2));

            if (!component3Field.getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(component3Field.getText().trim()));
            }
            if (!component4Field.getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(component4Field.getText().trim()));
            }
            if (!component5Field.getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(component5Field.getText().trim()));
            }

            byte[][] components = componentList.toArray(new byte[0][]);

            // Verify all components have the same length
            int length = components[0].length;
            for (byte[] comp : components) {
                if (comp.length != length) {
                    showError("Validation Error",
                            "All components must have the same length");
                    return;
                }
            }

            byte[] combinedKey = KeyOperations.combineKeyComponents(components);
            String combinedKeyHex = DataConverter.bytesToHex(combinedKey);

            // Display combined key in results area
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("COMBINED KEY\n");
            result.append("========================================\n\n");
            result.append("Combined Key: ").append(combinedKeyHex).append("\n");
            result.append("Key Length:   ").append(combinedKey.length).append(" bytes (");
            result.append(combinedKey.length * 8).append(" bits)\n\n");

            // Calculate KCV
            try {
                byte[] kcv = KeyOperations.calculateKCV_VISA(combinedKey);
                result.append("KCV (VISA):   ").append(DataConverter.bytesToHex(kcv)).append("\n");
                result.append("\n========================================\n");
                updateStatus("Components combined. KCV: " + DataConverter.bytesToHex(kcv));
            } catch (Exception e) {
                result.append("\n========================================\n");
                updateStatus("Components combined successfully");
            }

            componentResultsArea.setText(result.toString());
            componentResultsArea.setVisible(true);
            componentResultsArea.setManaged(true);

            // Add to history
            OperationHistory.getInstance().addOperation(
                    "Keys",
                    "Combine - " + components.length + " components",
                    "Components: " + components.length,
                    combinedKeyHex.substring(0, Math.min(32, combinedKeyHex.length())));

        } catch (IllegalArgumentException e) {
            showError("Input Error", e.getMessage());
        } catch (Exception e) {
            showError("Combining Error", "Error combining components: " + e.getMessage());
        }
    }

    // ============================================================================
    // ADVANCED ASYMMETRIC KEY GENERATION
    // ============================================================================

    /**
     * Generate RSA key pair
     */
    public void handleGenerateRSA() {
        try {
            Integer keySize = rsaKeySizeCombo.getValue();
            if (keySize == null) {
                showError("Input Error", "Please select RSA key size");
                return;
            }

            updateStatus("Generating RSA-" + keySize + " key pair... This may take a moment.");

            // Generate key pair
            KeyPair keyPair = AsymmetricKeyOperations.generateRSAKeyPair(keySize);

            // Store for certificate generation
            lastGeneratedKeyPair = keyPair;
            lastKeyType = "RSA";

            // Get key info
            String publicKeyInfo = AsymmetricKeyOperations.getRSAPublicKeyInfo(keyPair.getPublic());
            String privateKeyInfo = AsymmetricKeyOperations.getRSAPrivateKeyInfo(keyPair.getPrivate());

            // Display
            rsaPublicKeyArea.setText("=== RSA PUBLIC KEY ===\n\n" + publicKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            rsaPrivateKeyArea.setText("=== RSA PRIVATE KEY ===\n\n" + privateKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

            updateStatus("RSA-" + keySize + " key pair generated successfully");

            if (mainController != null) {
                try {
                    java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Key Size", keySize + " bits"));
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key", AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())));
                    details.add(com.cryptoforge.model.OperationDetail.secretDetail("Private Key", AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())));

                    mainController.addToHistory("Generate RSA Key", details);
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                OperationHistory.getInstance().addOperation(
                        "Keys",
                        "Generate RSA-" + keySize,
                        "Key Size: " + keySize + " bits",
                        "Public Key:\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()) +
                                "\n\nPrivate Key:\n"
                                + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating RSA key: " + e.getMessage());
        }
    }

    /**
     * Generate DSA key pair
     */
    public void handleGenerateDSA() {
        try {
            String keySize = dsaKeySizeCombo.getValue();
            if (keySize == null) {
                showError("Input Error", "Please select DSA key size");
                return;
            }

            updateStatus("Generating DSA-" + keySize + " key pair...");

            KeyPair keyPair = AsymmetricKeyOperations.generateDSAKeyPair(keySize);

            lastGeneratedKeyPair = keyPair;
            lastKeyType = "DSA";

            String publicKeyInfo = AsymmetricKeyOperations.getDSAKeyInfo(keyPair.getPublic());
            String privateKeyInfo = AsymmetricKeyOperations.getDSAKeyInfo(keyPair.getPrivate());

            dsaPublicKeyArea.setText("=== DSA PUBLIC KEY ===\n\n" + publicKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            dsaPrivateKeyArea.setText("=== DSA PRIVATE KEY ===\n\n" + privateKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

            updateStatus("DSA-" + keySize + " key pair generated successfully");

            if (mainController != null) {
                try {
                    java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Key Size", keySize));
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key", AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())));
                    details.add(com.cryptoforge.model.OperationDetail.secretDetail("Private Key", AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())));

                    mainController.addToHistory("Generate DSA Key", details);
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                OperationHistory.getInstance().addOperation(
                        "Keys",
                        "Generate DSA-" + keySize,
                        "N/A",
                        "Public key generated");
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating DSA key: " + e.getMessage());
        }
    }

    /**
     * Generate ECDSA F(p) key pair
     */
    public void handleGenerateECDSAFp() {
        try {
            String curve = ecdsaFpCurveCombo.getValue();
            if (curve == null) {
                showError("Input Error", "Please select a curve");
                return;
            }

            updateStatus("Generating ECDSA F(p) key pair on curve " + curve + "...");

            KeyPair keyPair = AsymmetricKeyOperations.generateECDSAFpKeyPair(curve);

            lastGeneratedKeyPair = keyPair;
            lastKeyType = "ECDSA";

            String publicKeyInfo = AsymmetricKeyOperations.getECKeyInfo(keyPair.getPublic());
            String privateKeyInfo = AsymmetricKeyOperations.getECKeyInfo(keyPair.getPrivate());

            ecdsaFpPublicKeyArea.setText("=== ECDSA F(p) PUBLIC KEY ===\n" +
                    "Curve: " + curve + "\n\n" + publicKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            ecdsaFpPrivateKeyArea.setText("=== ECDSA F(p) PRIVATE KEY ===\n" +
                    "Curve: " + curve + "\n\n" + privateKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

            updateStatus("ECDSA F(p) key pair generated on curve " + curve);

            if (mainController != null) {
                try {
                    java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Curve", curve));
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key", AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())));
                    details.add(com.cryptoforge.model.OperationDetail.secretDetail("Private Key", AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())));

                    mainController.addToHistory("Generate ECDSA Key", details);
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                OperationHistory.getInstance().addOperation(
                        "Keys",
                        "Generate ECDSA F(p) - " + curve,
                        "N/A",
                        "Curve: " + curve);
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating ECDSA F(p) key: " + e.getMessage());
        }
    }

    /**
     * Generate Ed25519 key pair
     */
    public void handleGenerateEd25519() {
        try {
            updateStatus("Generating Ed25519 key pair...");

            KeyPair keyPair = AsymmetricKeyOperations.generateEd25519KeyPair();

            lastGeneratedKeyPair = keyPair;
            lastKeyType = "Ed25519";

            ed25519PublicKeyArea.setText("=== Ed25519 PUBLIC KEY ===\n" +
                    "Algorithm: Ed25519 (255-bit curve)\n" +
                    "Use: Digital signatures (fast, secure)\n\n" +
                    "=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            ed25519PrivateKeyArea.setText("=== Ed25519 PRIVATE KEY ===\n" +
                    "Algorithm: Ed25519 (255-bit curve)\n" +
                    "Use: Digital signatures (fast, secure)\n\n" +
                    "=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

            updateStatus("Ed25519 key pair generated successfully");

            // Delegate to ModernMainController history if available
            if (mainController != null && mainController.getClass().getSimpleName().equals("ModernMainController")) {
                try {
                    java.util.Map<String, String> details = new java.util.HashMap<>();
                    details.put("Algorithm", "Ed25519");
                    details.put("Public Key", AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));
                    details.put("Private Key", AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

                    java.lang.reflect.Method method = mainController.getClass().getMethod("addToHistory",
                            String.class, java.util.Map.class);
                    method.invoke(mainController, "Generate EdDSA Key", details);
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                OperationHistory.getInstance().addOperation(
                        "Keys",
                        "Generate Ed25519",
                        "N/A",
                        "Algorithm: Ed25519");
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating Ed25519 key: " + e.getMessage());
        }
    }

    /**
     * Alias for handleGenerateEd25519 for Modern UI
     */
    public void handleGenerateEdDSA() {
        handleGenerateEd25519();
    }

    /**
     * Generate ECDSA F(2^m) key pair
     */

    /**
     * Generate self-signed X.509 certificate
     */
    public void handleGenerateCertificate() {
        try {
            // Validate inputs
            String cn = certCNField.getText().trim();
            if (cn.isEmpty()) {
                showError("Input Error", "Common Name (CN) is required");
                return;
            }

            int validity;
            try {
                validity = Integer.parseInt(certValidityField.getText().trim());
                if (validity <= 0)
                    throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showError("Input Error", "Validity must be a positive number of days");
                return;
            }

            updateStatus("Generating certificate and key pair...");

            // Generate or use existing key pair
            KeyPair keyPair;
            String keyTypeDesc;

            String certKeyType = certKeyTypeCombo.getValue();
            if (certKeyType.startsWith("RSA")) {
                int keySize = Integer.parseInt(certKeyType.substring(4));
                keyPair = AsymmetricKeyOperations.generateRSAKeyPair(keySize);
                keyTypeDesc = "RSA-" + keySize;
            } else if (certKeyType.startsWith("ECDSA")) {
                String curve = certKeyType.equals("ECDSA-P256") ? "secp256r1" : "secp384r1";
                keyPair = AsymmetricKeyOperations.generateECDSAFpKeyPair(curve);
                keyTypeDesc = "ECDSA-" + curve;
            } else {
                showError("Input Error", "Invalid key type selected");
                return;
            }

            // Build certificate configuration
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = cn;
            config.organization = certOrgField != null ? certOrgField.getText().trim() : "Crypto Org";
            config.organizationalUnit = certOUField != null ? certOUField.getText().trim() : "IT Security";
            config.locality = certLocalityField != null ? certLocalityField.getText().trim() : "Madrid";
            config.state = certStateField != null ? certStateField.getText().trim() : "Madrid";
            config.country = certCountryField != null ? certCountryField.getText().trim() : "ES";
            config.validityDays = validity;
            config.signatureAlgorithm = certSignAlgoCombo.getValue();
            applySanConfiguration(config);

            // Email is optional - only add if provided
            String email = certEmailField != null ? certEmailField.getText().trim() : "";
            config.email = email.isEmpty() ? null : email;

            // Generate certificate
            boolean rootCa = certRootCaCheck != null && certRootCaCheck.isSelected();
            X509Certificate certificate = rootCa
                    ? CertificateGenerator.generateRootCA(keyPair, config, 1)
                    : CertificateGenerator.generateSelfSignedCertificate(keyPair, config);

            // Build output
            StringBuilder output = new StringBuilder();
            output.append(rootCa ? "=== SELF-SIGNED ROOT CA (LABORATORY) ===\n\n" : "=== SELF-SIGNED X.509 CERTIFICATE ===\n\n");
            output.append(CertificateGenerator.getCertificateInfo(certificate));
            output.append("\n\n=== CERTIFICATE (PEM) ===\n");
            output.append(CertificateGenerator.exportCertificatePEM(certificate));
            output.append("\n=== PRIVATE KEY (PEM) ===\n");
            output.append(AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));
            output.append("\n=== PUBLIC KEY (PEM) ===\n");
            output.append(AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            certOutputArea.setText(output.toString());
            certOutputArea.setVisible(true);
            certOutputArea.setManaged(true);

            updateStatus("Certificate generated successfully with " + keyTypeDesc);

            OperationHistory.getInstance().addOperation(
                    "Keys",
                    "Generate Certificate - " + keyTypeDesc,
                    "CN=" + cn + ", Validity=" + validity + " days",
                    output.toString() // Full certificate + private key + public key
            );

        } catch (Exception e) {
            showError("Generation Error", "Error generating certificate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Generates a PKCS#10 request and a fresh laboratory key pair using the certificate form parameters. */
    public void handleGenerateCSR() {
        try {
            String cn = certCNField.getText().trim();
            if (cn.isEmpty()) throw new IllegalArgumentException("Common Name (CN) is required");
            String selected = certKeyTypeCombo.getValue();
            KeyPair pair;
            if (selected.startsWith("RSA")) pair = AsymmetricKeyOperations.generateRSAKeyPair(Integer.parseInt(selected.substring(4)));
            else if (selected.startsWith("ECDSA")) pair = AsymmetricKeyOperations.generateECDSAFpKeyPair(selected.equals("ECDSA-P256") ? "secp256r1" : "secp384r1");
            else throw new IllegalArgumentException("Unsupported CSR key type");
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = cn;
            config.organization = certOrgField.getText().trim();
            config.organizationalUnit = certOUField.getText().trim();
            config.locality = certLocalityField.getText().trim();
            config.state = certStateField.getText().trim();
            config.country = certCountryField.getText().trim();
            config.email = certEmailField.getText().trim().isEmpty() ? null : certEmailField.getText().trim();
            config.signatureAlgorithm = certSignAlgoCombo.getValue();
            applySanConfiguration(config);
            certOutputArea.setText("=== PKCS#10 CERTIFICATE SIGNING REQUEST ===\n\n" + CertificateGenerator.generateCSR(pair, config)
                    + "\n=== PRIVATE KEY (LABORATORY ONLY) ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate()));
            certOutputArea.setManaged(true);
            certOutputArea.setVisible(true);
            updateStatus("CSR generated with requested SANs");
        } catch (Exception e) {
            showError("CSR Generation", "Cannot generate CSR: " + e.getMessage());
        }
    }

    private void applySanConfiguration(CertificateGenerator.CertificateConfig config) {
        config.sanDnsNames = commaSeparatedValues(certSanDnsField == null ? null : certSanDnsField.getText());
        config.sanIpAddresses = commaSeparatedValues(certSanIpField == null ? null : certSanIpField.getText());
        config.addSubjectAlternativeNames = !config.sanDnsNames.isEmpty() || !config.sanIpAddresses.isEmpty();
    }

    private List<String> commaSeparatedValues(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    /**
     * Parse and display certificate information
     */
    public void handleParseCertificate() {
        try {
            if (certInputArea == null || certParseResultArea == null) {
                updateStatus("Certificate parsing not initialized");
                return;
            }

            String pemCert = certInputArea.getText().trim();
            if (pemCert.isEmpty()) {
                showError("Input Error", "Please paste a certificate in PEM format");
                return;
            }

            updateStatus("Parsing certificate...");

            // Parse certificate using CertificateGenerator
            X509Certificate cert = CertificateGenerator.parseCertificate(pemCert);

            // Get certificate info
            String certInfo = CertificateGenerator.getCertificateInfo(cert);

            StringBuilder output = new StringBuilder();
            output.append("=== CERTIFICATE INFORMATION ===\n\n");
            output.append(certInfo);

            certParseResultArea.setText(output.toString());
            certParseResultArea.setVisible(true);
            certParseResultArea.setManaged(true);

            updateStatus("Certificate parsed successfully");

            OperationHistory.getInstance().addOperation(
                    "Certificates",
                    "Parse Certificate",
                    "Subject: " + cert.getSubjectX500Principal().getName(),
                    "Parsed successfully");

        } catch (Exception e) {
            certParseResultArea.setText("Error parsing certificate: " + e.getMessage());
            certParseResultArea.setVisible(true);
            certParseResultArea.setManaged(true);
            updateStatus("Certificate parse failed");
            e.printStackTrace();
        }
    }

    /**
     * Handle Validate Certificate button click
     */
    public void handleValidateCertificate() {
        try {
            if (valCertInput == null || valResultArea == null) {
                // Not initialized
                return;
            }

            String certPem = valCertInput.getText().trim();
            if (certPem.isEmpty()) {
                showError("Input Error", "Please paste a certificate to validate");
                return;
            }

            String issuerPem = valIssuerInput.getText().trim();

            updateStatus("Validating certificate...");

            // Parse main certificate
            X509Certificate cert = null;
            try {
                cert = CertificateGenerator.parseCertificate(certPem);
            } catch (Exception e) {
                valResultArea.setText("Error parsing certificate: " + e.getMessage());
                updateStatus("Validation failed: Parse error");
                return;
            }

            // Parse issuer if provided
            X509Certificate issuer = null;
            if (!issuerPem.isEmpty()) {
                try {
                    issuer = CertificateGenerator.parseCertificate(issuerPem);
                } catch (Exception e) {
                    valResultArea.setText("Error parsing issuer certificate: " + e.getMessage());
                    updateStatus("Validation failed: Issuer parse error");
                    return;
                }
            }

            // Validate
            CertificateGenerator.CertificateValidationResult result = CertificateGenerator.validateCertificate(cert,
                    issuer);

            // Display results
            StringBuilder sb = new StringBuilder();
            sb.append("=== VALIDATION RESULT ===\n");
            sb.append("Status: ").append(result.isValid ? "VALID ✅" : "INVALID ❌").append("\n");
            sb.append("Reason: ").append(result.status).append("\n");
            sb.append("Message: ").append(result.message).append("\n\n");

            sb.append("=== DETAILS ===\n");
            for (String detail : result.details) {
                sb.append("• ").append(detail).append("\n");
            }

            valResultArea.setText(sb.toString());
            updateStatus(result.isValid ? "Certificate is valid" : "Certificate is invalid");

            OperationHistory.getInstance().addOperation(
                    "Certificates",
                    "Validate Certificate",
                    "Status: " + result.status,
                    result.isValid ? "Success" : "Failed");

        } catch (Exception e) {
            valResultArea.setText("Error during validation: " + e.getMessage());
            updateStatus("Validation error");
            e.printStackTrace();
        }
    }

    // ============================================================================
    // TR-31 KEY BLOCK OPERATIONS
    // ============================================================================

    // TR-31 UI Components (to be added to FXML)
    private TextField tr31KbpkExportField;
    private TextField tr31KeyToWrapField;
    private ComboBox<String> tr31UsageCombo;
    private ComboBox<String> tr31AlgorithmCombo;
    private ComboBox<String> tr31ModeCombo;
    private ComboBox<String> tr31VersionCombo;
    private ComboBox<String> tr31ExportabilityCombo;
    private TextField tr31OptionalBlocksField;
    private TextArea tr31ExportResultArea;

    private TextField tr31KbpkImportField;
    private TextArea tr31KeyBlockField;
    private TextField tr31KeyLengthField;
    private TextArea tr31ImportResultArea;

    /**
     * Initialize TR-31 UI components
     */
    public void initializeTR31(TextField tr31KbpkExportField, TextField tr31KeyToWrapField,
            ComboBox<String> tr31VersionCombo, ComboBox<String> tr31UsageCombo,
            ComboBox<String> tr31AlgorithmCombo, ComboBox<String> tr31ModeCombo,
            ComboBox<String> tr31ExportabilityCombo,
            TextField tr31OptionalBlocksField,
            TextArea tr31ExportResultArea, TextField tr31KbpkImportField,
            TextArea tr31KeyBlockField, TextField tr31KeyLengthField,
            TextArea tr31ImportResultArea) {

        this.tr31KbpkExportField = tr31KbpkExportField;
        this.tr31KeyToWrapField = tr31KeyToWrapField;
        this.tr31VersionCombo = tr31VersionCombo;
        this.tr31UsageCombo = tr31UsageCombo;
        this.tr31AlgorithmCombo = tr31AlgorithmCombo;
        this.tr31ModeCombo = tr31ModeCombo;
        this.tr31ExportabilityCombo = tr31ExportabilityCombo;
        this.tr31OptionalBlocksField = tr31OptionalBlocksField;
        this.tr31ExportResultArea = tr31ExportResultArea;

        this.tr31KbpkImportField = tr31KbpkImportField;
        this.tr31KeyBlockField = tr31KeyBlockField;
        this.tr31KeyLengthField = tr31KeyLengthField;
        this.tr31ImportResultArea = tr31ImportResultArea;

        setupTR31Combos();
    }

    /**
     * Setup TR-31 ComboBoxes
     */
    private void setupTR31Combos() {
        if (tr31VersionCombo != null) {
            tr31VersionCombo.getItems().addAll(
                    "A - DES Key Variant Binding (deprecated)",
                    "B - TDES Key Derivation Binding",
                    "C - TDES Key Variant Binding (deprecated)",
                    "D - AES Key Derivation Binding");
            tr31VersionCombo.getSelectionModel().select(1); // Default to B
        }

        if (tr31UsageCombo != null) {
            tr31UsageCombo.getItems().addAll(
                    "B0 - BDK (Base Derivation Key)",
                    "B1 - Initial DUKPT Key",
                    "C0 - CVK (Card Verification Key)",
                    "D0 - Data Encryption (symmetric)",
                    "D1 - Data Encryption (asymmetric)",
                    "E0 - EMV/Chip Card Keys",
                    "I0 - Initialization Vector",
                    "K0 - Key Encryption / Wrapping",
                    "K1 - TR-31 KBPK",
                    "M0 - ISO 16609 MAC (algorithm 1)",
                    "M1 - ISO 9797-1 MAC (algorithm 1)",
                    "M3 - ISO 9797-1 MAC (algorithm 3 - Retail)",
                    "M6 - ISO 9797-1 CMAC (algorithm 5)",
                    "M7 - HMAC",
                    "P0 - PIN Encryption",
                    "S0 - Asymmetric Digital Signature",
                    "V0 - PIN Verification (other)",
                    "V1 - PIN Verification (IBM 3624)",
                    "V2 - PIN Verification (VISA PVV)");
            tr31UsageCombo.getSelectionModel().selectFirst();
        }

        if (tr31AlgorithmCombo != null) {
            tr31AlgorithmCombo.getItems().addAll(
                    "T - Triple DES",
                    "A - AES",
                    "D - DES (single)",
                    "H - HMAC",
                    "R - RSA",
                    "S - DSA",
                    "E - Elliptic Curve");
            tr31AlgorithmCombo.getSelectionModel().selectFirst();
        }

        if (tr31ModeCombo != null) {
            tr31ModeCombo.getItems().addAll(
                    "B - Both encrypt & decrypt",
                    "C - Both generate & verify",
                    "D - Decrypt only",
                    "E - Encrypt only",
                    "G - Generate only",
                    "N - No special restrictions",
                    "S - Signature only",
                    "T - Both sign & key transport",
                    "V - Verify only",
                    "X - Key derivation",
                    "Y - Create cryptographic checksum");
            tr31ModeCombo.getSelectionModel().selectFirst(); // "B - Both"
        }

        if (tr31ExportabilityCombo != null) {
            tr31ExportabilityCombo.getItems().addAll(
                    "E - Exportable",
                    "N - Non-exportable",
                    "S - Sensitive");
            tr31ExportabilityCombo.getSelectionModel().selectFirst(); // "E - Exportable"
        }
    }

    /**
     * Handle TR-31 Export (Wrap Key)
     */
    public void handleTR31Export() {
        try {
            updateStatus("Starting TR-31 Export...");
            String kbpk = tr31KbpkExportField.getText().trim().replaceAll("\\s+", "");
            String key = tr31KeyToWrapField.getText().trim().replaceAll("\\s+", "");

            // Validate inputs
            if (kbpk.isEmpty() || key.isEmpty()) {
                tr31ExportResultArea.setText("Error: KBPK and Key are required");
                return;
            }

            if (!kbpk.matches("[0-9A-Fa-f]+")) {
                tr31ExportResultArea.setText("Error: KBPK must be hexadecimal");
                return;
            }

            if (!key.matches("[0-9A-Fa-f]+")) {
                tr31ExportResultArea.setText("Error: Key must be hexadecimal");
                return;
            }

            // Extract parameters
            String versionStr = tr31VersionCombo.getValue();
            char version = versionStr.charAt(0); // 'B' or 'D'

            String usageStr = tr31UsageCombo.getValue();
            String usage = usageStr.substring(0, 2); // Extract "P0", "D0", etc.

            String algoStr = tr31AlgorithmCombo.getValue();
            char algorithm = algoStr.charAt(0); // 'T' or 'A'

            String modeStr = tr31ModeCombo.getValue();
            char mode = modeStr.charAt(0); // 'E', 'D', 'B', etc.

            String exportStr = tr31ExportabilityCombo.getValue();
            char exportability = exportStr.charAt(0); // 'E', 'N', or 'S'

            // Wrap key
            String optionalBlocks = tr31OptionalBlocksField == null ? "" : tr31OptionalBlocksField.getText();
            String keyBlock = TR31Operations.wrapKey(kbpk, key, usage, version, algorithm, mode, exportability, optionalBlocks);

            // Parse header for display
            TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

            // Build result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-31 KEY BLOCK EXPORT\n");
            result.append("========================================\n\n");

            result.append("HEADER INFORMATION:\n");
            result.append("------------------\n");
            result.append("Version ID:        ").append(header.versionId).append("\n");
            result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
            result.append("Key Usage:         ").append(header.keyUsage);
            result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
            result.append("Algorithm:         ").append(header.algorithm);
            result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                    .append(")\n");
            result.append("Mode of Use:       ").append(header.modeOfUse);
            result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                    .append(")\n");
            result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
            result.append("Exportability:     ").append(header.exportability);
            result.append(" (").append(TR31Operations.getExportabilityDescription(header.exportability.charAt(0)))
                    .append(")\n");
            result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n\n");
            if (!header.optionalBlockDetails.isEmpty()) {
                result.append("OPTIONAL BLOCKS:\n");
                for (TR31Operations.OptionalBlock block : header.optionalBlockDetails) {
                    result.append("  ").append(block.id()).append(" (" ).append(block.dataLength()).append(" bytes): ").append(block.data()).append("\n");
                }
                result.append("\n");
            }

            result.append("KEY BLOCK:\n");
            result.append("------------------\n");
            result.append(keyBlock).append("\n\n");

            result.append("KEY BLOCK (Formatted):\n");
            result.append("------------------\n");
            result.append("Header:       ")
                    .append(keyBlock.substring(0, Math.min(header.build().length(), keyBlock.length()))).append("\n");
            int headerLen = header.build().length();
            int macLen = (header.versionId.equals("A") || header.versionId.equals("C")) ? 8 : 16;
            if (keyBlock.length() > headerLen + macLen) {
                result.append("Encrypted Key: ").append(keyBlock.substring(headerLen, keyBlock.length() - macLen))
                        .append("\n");
                result.append("MAC:          ").append(keyBlock.substring(keyBlock.length() - macLen)).append("\n");
            }

            result.append("\n========================================\n");

            javafx.application.Platform.runLater(() -> {
                tr31ExportResultArea.setVisible(true);
                tr31ExportResultArea.setManaged(true);
                tr31ExportResultArea.setText(result.toString());

                // Force layout update specifically for VBox parent
                if (tr31ExportResultArea.getParent() != null) {
                    tr31ExportResultArea.getParent().requestLayout();
                    // If parent is VBox/HBox/Grid, this helps trigger resize
                    tr31ExportResultArea.getParent().layout();
                }
            });

            updateStatus("TR-31 key wrapped successfully");

            // Delegate to ModernMainController history if available
            if (mainController != null) {
                try {
                    java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Version", header.versionId));
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Usage", usage));
                    details.add(com.cryptoforge.model.OperationDetail.secretDetail("KBPK", kbpk));
                    details.add(com.cryptoforge.model.OperationDetail.secretDetail("Key to Wrap", key));
                    details.add(com.cryptoforge.model.OperationDetail.publicDetail("Key Block", keyBlock));

                    mainController.addToHistory("TR-31 Export", details);
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                // Fallback to old system
                OperationHistory.getInstance().addOperation(
                        "Keys/TR-31",
                        "Wrap Key - " + TR31Operations.getKeyUsageDescription(usage),
                        "Version: " + header.versionId + " | Usage: " + usage,
                        "KBPK: " + kbpk + "\nKey to Wrap: " + key + "\nKey Block: " + keyBlock);
            }

        } catch (Exception e) {
            tr31ExportResultArea.setText("Error wrapping key: " + e.getMessage());
            tr31ExportResultArea.setVisible(true);
            tr31ExportResultArea.setManaged(true);
            updateStatus("TR-31 wrap failed");
            e.printStackTrace();
        }
    }

    /**
     * Handle TR-31 Import (Unwrap Key)
     */
    public void handleTR31Import() {
        try {
            String kbpk = tr31KbpkImportField.getText().trim().replaceAll("\\s+", "");
            String keyBlock = tr31KeyBlockField.getText().trim().replaceAll("\\s+", "");

            // Validate inputs
            if (kbpk.isEmpty() || keyBlock.isEmpty()) {
                tr31ImportResultArea.setText("Error: KBPK and Key Block are required");
                return;
            }

            // Parse header
            TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

            // Unwrap key
            String unwrappedKey = TR31Operations.unwrapKey(kbpk, keyBlock);

            // Build result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-31 KEY BLOCK IMPORT\n");
            result.append("========================================\n\n");

            result.append("HEADER INFORMATION:\n");
            result.append("------------------\n");
            result.append("Version ID:        ").append(header.versionId).append("\n");
            result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
            result.append("Key Usage:         ").append(header.keyUsage);
            result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
            result.append("Algorithm:         ").append(header.algorithm);
            result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                    .append(")\n");
            result.append("Mode of Use:       ").append(header.modeOfUse);
            result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                    .append(")\n");
            result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
            result.append("Exportability:     ").append(header.exportability).append("\n");
            result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n");
            result.append("\n");

            result.append("UNWRAPPED KEY:\n");
            result.append("------------------\n");
            result.append(unwrappedKey.toUpperCase()).append("\n");
            result.append("\nKey Length: ").append(unwrappedKey.length() / 2).append(" bytes (");
            result.append(unwrappedKey.length()).append(" hex characters)\n");

            result.append("\n========================================\n");

            tr31ImportResultArea.setText(result.toString());
            tr31ImportResultArea.setVisible(true);
            tr31ImportResultArea.setManaged(true);
            updateStatus("TR-31 key unwrapped successfully");

            OperationHistory.getInstance().addOperation(
                    "Keys/TR-31",
                    "Unwrap Key - " + TR31Operations.getKeyUsageDescription(header.keyUsage),
                    "Version " + header.versionId,
                    "Key Length: " + (unwrappedKey.length() / 2) + " bytes");

        } catch (Exception e) {
            tr31ImportResultArea.setText("Error unwrapping key: " + e.getMessage());
            tr31ImportResultArea.setVisible(true);
            tr31ImportResultArea.setManaged(true);
            updateStatus("TR-31 unwrap failed");
            e.printStackTrace();
        }
    }

    /**
     * Handle Parse TR-31 Header (without unwrapping)
     */
    public void handleTR31ParseHeader() {
        try {
            String keyBlock = tr31KeyBlockField.getText().trim().replaceAll("\\s+", "");

            if (keyBlock.isEmpty()) {
                tr31ImportResultArea.setText("Error: Key Block is required");
                return;
            }

            // Parse header
            TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

            // Build result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-31 HEADER PARSE\n");
            result.append("========================================\n\n");

            result.append("HEADER FIELDS:\n");
            result.append("------------------\n");
            result.append("Version ID:        ").append(header.versionId).append("\n");
            result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
            result.append("Key Usage:         ").append(header.keyUsage);
            result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
            result.append("Algorithm:         ").append(header.algorithm);
            result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                    .append(")\n");
            result.append("Mode of Use:       ").append(header.modeOfUse);
            result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                    .append(")\n");
            result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
            result.append("Exportability:     ").append(header.exportability).append(" (")
                    .append(TR31Operations.getExportabilityDescription(header.exportability.charAt(0))).append(")\n");
            result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n");
            result.append("Reserved:          ").append(header.reserved).append("\n\n");

            result.append("INPUT LENGTH:       ").append(keyBlock.length()).append(" characters\n");

            if (!header.optionalBlockDetails.isEmpty()) {
                result.append("OPTIONAL BLOCKS:\n");
                result.append("------------------\n");
                for (TR31Operations.OptionalBlock block : header.optionalBlockDetails) {
                    result.append(block.id()).append(": ").append(block.dataLength()).append(" bytes\n");
                    result.append("  Data: ").append(block.data()).append("\n");
                }
                result.append("\n");
            }

            result.append("DIAGNOSTICS:\n");
            result.append("------------------\n");
            if (header.getDiagnostics().isEmpty()) result.append("No structural warnings detected.\n\n");
            else {
                for (String diagnostic : header.getDiagnostics()) result.append(diagnostic).append("\n");
                result.append("\n");
            }

            result.append("RAW HEADER:\n");
            result.append("------------------\n");
            result.append(header.build()).append("\n");

            result.append("\n========================================\n");

            tr31ImportResultArea.setText(result.toString());
            tr31ImportResultArea.setVisible(true);
            tr31ImportResultArea.setManaged(true);
            updateStatus("TR-31 header parsed successfully");

        } catch (Exception e) {
            tr31ImportResultArea.setText("Error parsing header: " + e.getMessage());
            tr31ImportResultArea.setVisible(true);
            tr31ImportResultArea.setManaged(true);
            updateStatus("TR-31 parse failed");
            e.printStackTrace();
        }
    }

    /**
     * Initialize Key Derivation Functions
     */
    public void initializeKDF(ComboBox<String> algorithmCombo,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> saltFormatCombo,
            ComboBox<String> infoFormatCombo,
            TextField inputField,
            TextField saltField,
            TextField infoField,
            TextField iterationsField,
            TextField outputLengthField,
            TextArea resultArea) {
        this.kdfAlgorithmCombo = algorithmCombo;
        this.kdfInputFormatCombo = inputFormatCombo;
        this.kdfSaltFormatCombo = saltFormatCombo;
        this.kdfInfoFormatCombo = infoFormatCombo;
        this.kdfInputField = inputField;
        this.kdfSaltField = saltField;
        this.kdfInfoField = infoField;
        this.kdfIterationsField = iterationsField;
        this.kdfOutputLengthField = outputLengthField;
        this.kdfResultArea = resultArea;

        // Populate algorithms (with SHA variants)
        kdfAlgorithmCombo.getItems().addAll(
                "HKDF-SHA1",
                "HKDF-SHA256",
                "HKDF-SHA512",
                "NIST-800-108-SHA256",
                "X9.63-SHA256",
                "PBKDF2-SHA1",
                "PBKDF2-SHA256",
                "PBKDF2-SHA512",
                "SCrypt",
                "Argon2id");
        kdfAlgorithmCombo.setValue("HKDF-SHA256");

        // Populate format combos
        String[] formats = { "UTF-8", "Hex", "Base64" };
        kdfInputFormatCombo.getItems().addAll(formats);
        kdfSaltFormatCombo.getItems().addAll(formats);
        kdfInfoFormatCombo.getItems().addAll(formats);

        kdfInputFormatCombo.setValue("UTF-8");
        kdfSaltFormatCombo.setValue("Hex");
        kdfInfoFormatCombo.setValue("UTF-8");

        // Add listener to update parameters based on algorithm
        kdfAlgorithmCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateKDFParameters(newVal);
        });

        updateKDFParameters("HKDF-SHA256");
    }

    /**
     * Update KDF parameters based on selected algorithm
     */
    private void updateKDFParameters(String algorithm) {
        if (algorithm == null)
            return;

        if (algorithm.startsWith("HKDF")) {
            kdfIterationsField.setText("1");
            kdfIterationsField.setDisable(true);
            kdfSaltField.setDisable(false);
            kdfSaltFormatCombo.setDisable(false);
            kdfSaltField.setPromptText("Optional salt (zeros if omitted)");
            kdfInfoField.setDisable(false);
            kdfInfoFormatCombo.setDisable(false);
            kdfInfoField.setPromptText("Optional application context");
        } else if (algorithm.startsWith("NIST-800-108")) {
            kdfIterationsField.setText("1");
            kdfIterationsField.setDisable(true);
            kdfSaltField.setDisable(false);
            kdfSaltFormatCombo.setDisable(false);
            kdfSaltField.setPromptText("Label (optional)");
            kdfInfoField.setDisable(false);
            kdfInfoFormatCombo.setDisable(false);
            kdfInfoField.setPromptText("Context (optional)");
        } else if (algorithm.startsWith("X9.63")) {
            kdfIterationsField.setText("1");
            kdfIterationsField.setDisable(true);
            kdfSaltField.setDisable(true);
            kdfSaltFormatCombo.setDisable(true);
            kdfSaltField.setPromptText("Not used by X9.63");
            kdfInfoField.setDisable(false);
            kdfInfoFormatCombo.setDisable(false);
            kdfInfoField.setPromptText("Shared info (optional)");
        } else if (algorithm.startsWith("PBKDF2")) {
            kdfIterationsField.setText("600000");
            kdfIterationsField.setDisable(false);
            kdfInfoField.setDisable(true);
            kdfInfoFormatCombo.setDisable(true);
            kdfSaltField.setDisable(false);
            kdfSaltFormatCombo.setDisable(false);
            kdfSaltField.setPromptText("Required salt");
        } else if (algorithm.equals("SCrypt")) {
            kdfIterationsField.setText("32768");
            kdfIterationsField.setDisable(false);
            kdfInfoField.setDisable(true);
            kdfInfoFormatCombo.setDisable(true);
            kdfSaltField.setDisable(false);
            kdfSaltFormatCombo.setDisable(false);
        } else if (algorithm.equals("Argon2id")) {
            kdfIterationsField.setText("3");
            kdfIterationsField.setDisable(false);
            kdfInfoField.setDisable(true);
            kdfInfoFormatCombo.setDisable(true);
            kdfSaltField.setDisable(false);
            kdfSaltFormatCombo.setDisable(false);
        }
    }

    /** Initializes the standalone AES Key Wrap laboratory panel. */
    public void initializeKeyWrap(ComboBox<String> modeCombo, CheckBox unwrapCheck, TextField kekField,
            TextField dataField, TextArea resultArea) {
        this.keyWrapModeCombo = modeCombo;
        this.keyWrapUnwrapCheck = unwrapCheck;
        this.keyWrapKekField = kekField;
        this.keyWrapDataField = dataField;
        this.keyWrapResultArea = resultArea;
        modeCombo.getItems().setAll("RFC 3394 - AES Key Wrap", "RFC 5649 - AES Key Wrap with Padding");
        modeCombo.setValue("RFC 3394 - AES Key Wrap");
    }

    /** Executes wrapping or authenticated unwrapping of hexadecimal key material. */
    public void handleKeyWrap() {
        try {
            byte[] kek = DataConverter.hexToBytes(keyWrapKekField.getText().replaceAll("\\s+", ""));
            byte[] data = DataConverter.hexToBytes(keyWrapDataField.getText().replaceAll("\\s+", ""));
            boolean unwrap = keyWrapUnwrapCheck.isSelected();
            boolean padded = keyWrapModeCombo.getValue().startsWith("RFC 5649");
            byte[] result;
            if (unwrap) {
                result = padded ? KeyWrapOperations.unwrapRfc5649(kek, data) : KeyWrapOperations.unwrapRfc3394(kek, data);
            } else {
                result = padded ? KeyWrapOperations.wrapRfc5649(kek, data) : KeyWrapOperations.wrapRfc3394(kek, data);
            }
            String operation = unwrap ? "UNWRAP" : "WRAP";
            StringBuilder text = new StringBuilder("========================================\nAES KEY ")
                    .append(operation).append("\n========================================\n\n")
                    .append("Mode: ").append(keyWrapModeCombo.getValue()).append("\n")
                    .append("KEK: ").append(kek.length * 8).append(" bits\n")
                    .append("Input: ").append(data.length).append(" bytes\n")
                    .append("Output: ").append(result.length).append(" bytes\n\n")
                    .append(unwrap ? "UNWRAPPED:" : "WRAPPED:").append("\n")
                    .append(DataConverter.bytesToHex(result)).append("\n\n")
                    .append("✓ Integrity is verified during unwrapping.");
            keyWrapResultArea.setText(text.toString());
            keyWrapResultArea.setManaged(true);
            keyWrapResultArea.setVisible(true);
            updateStatus("AES Key Wrap " + operation.toLowerCase() + " completed");
            OperationHistory.getInstance().addOperation("Keys", "AES Key " + operation,
                    "Mode: " + (padded ? "RFC 5649" : "RFC 3394") + ", KEK: " + kek.length * 8 + " bits",
                    "Input: " + data.length + " bytes, output: " + result.length + " bytes");
        } catch (Exception e) {
            showError("AES Key Wrap", "Cannot execute operation: " + e.getMessage());
        }
    }

    /**
     * Handle key derivation
     */
    public void handleDeriveKey() {
        try {
            String algorithm = kdfAlgorithmCombo.getValue();
            String inputFormat = kdfInputFormatCombo.getValue();
            String saltFormat = kdfSaltFormatCombo.getValue();
            String infoFormat = kdfInfoFormatCombo.getValue();

            String inputText = kdfInputField.getText().trim();
            String saltText = kdfSaltField.getText().trim();
            String infoText = kdfInfoField.getText().trim();
            String iterationsText = kdfIterationsField.getText().trim();
            String outputLengthText = kdfOutputLengthField.getText().trim();

            if (inputText.isEmpty()) {
                showError("Input Error", "Please enter input key material");
                return;
            }

            // Parse input according to format
            byte[] input = parseData(inputText, inputFormat);
            if (input == null) {
                showError("Input Error", "Invalid " + inputFormat + " format for input");
                return;
            }

            // Parse salt according to format (NULL if empty - no forced generation!)
            byte[] salt = null;
            if (!saltText.isEmpty()) {
                salt = parseData(saltText, saltFormat);
                if (salt == null) {
                    showError("Input Error", "Invalid " + saltFormat + " format for salt");
                    return;
                }
            }

            // Parse info according to format
            byte[] info = null;
            if (!infoText.isEmpty()) {
                info = parseData(infoText, infoFormat);
                if (info == null) {
                    showError("Input Error", "Invalid " + infoFormat + " format for info");
                    return;
                }
            }

            // Parse iterations
            int iterations;
            try {
                iterations = Integer.parseInt(iterationsText);
            } catch (Exception e) {
                showError("Input Error", "Invalid iterations value");
                return;
            }

            // Parse output length
            int outputLength;
            try {
                outputLength = Integer.parseInt(outputLengthText);
                if (outputLength < 1 || outputLength > 256) {
                    showError("Input Error", "Output length must be between 1 and 256 bytes");
                    return;
                }
            } catch (Exception e) {
                showError("Input Error", "Invalid output length");
                return;
            }

            // Extract hash algorithm from name (e.g., "HKDF-SHA256" -> "SHA256")
            String hashAlgo = "SHA256"; // default
            if (algorithm.contains("SHA1")) {
                hashAlgo = "SHA1";
            } else if (algorithm.contains("SHA256")) {
                hashAlgo = "SHA256";
            } else if (algorithm.contains("SHA512")) {
                hashAlgo = "SHA512";
            }

            // Derive key based on algorithm
            byte[] derivedKey;
            String resultInfo;

            if (algorithm.startsWith("HKDF")) {
                // HKDF requires digest
                org.bouncycastle.crypto.Digest digest = com.cryptoforge.crypto.KeyDerivation.getDigest(hashAlgo);
                derivedKey = com.cryptoforge.crypto.KeyDerivation.hkdf(input, salt, info, outputLength, digest);
                resultInfo = buildHKDFResult(input, salt, info, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.startsWith("NIST-800-108")) {
                org.bouncycastle.crypto.Digest digest = com.cryptoforge.crypto.KeyDerivation.getDigest(hashAlgo);
                derivedKey = com.cryptoforge.crypto.KeyDerivation.sp800108Counter(input, salt, info, outputLength, digest);
                resultInfo = buildContextKdfResult("NIST SP 800-108 Counter KDF", "Key", input,
                        "Label", salt, "Context", info, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.startsWith("X9.63")) {
                org.bouncycastle.crypto.Digest digest = com.cryptoforge.crypto.KeyDerivation.getDigest(hashAlgo);
                derivedKey = com.cryptoforge.crypto.KeyDerivation.x963(input, info, outputLength, digest);
                resultInfo = buildContextKdfResult("ANSI X9.63 / Concatenation KDF", "Shared secret", input,
                        null, null, "Shared info", info, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.startsWith("PBKDF2")) {
                // PBKDF2 requires salt
                if (salt == null || salt.length == 0) {
                    showError("Input Error", "PBKDF2 requires a salt (cannot be empty)");
                    return;
                }
                derivedKey = com.cryptoforge.crypto.KeyDerivation.pbkdf2(input, salt, iterations, outputLength,
                        hashAlgo);
                resultInfo = buildPBKDF2Result(input, salt, iterations, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.equals("SCrypt")) {
                // SCrypt requires salt
                if (salt == null || salt.length == 0) {
                    showError("Input Error", "SCrypt requires a salt (cannot be empty)");
                    return;
                }
                // N=iterations, r=8, p=1
                derivedKey = com.cryptoforge.crypto.KeyDerivation.scrypt(input, salt, iterations, 8, 1, outputLength);
                resultInfo = buildSCryptResult(input, salt, iterations, 8, 1, outputLength, derivedKey);
            } else if (algorithm.equals("Argon2id")) {
                // Argon2 requires salt
                if (salt == null || salt.length < 8) {
                    showError("Input Error", "Argon2 requires a salt with minimum 8 bytes");
                    return;
                }
                // iterations=time, memory=64MB, parallelism=4
                derivedKey = com.cryptoforge.crypto.KeyDerivation.argon2(input, salt, iterations, 65536, 4,
                        outputLength);
                resultInfo = buildArgon2Result(input, salt, iterations, 65536, 4, outputLength, derivedKey);
            } else {
                showError("Algorithm Error", "Unknown algorithm: " + algorithm);
                return;
            }

            // Display result
            kdfResultArea.setText(resultInfo);
            kdfResultArea.setVisible(true);
            kdfResultArea.setManaged(true);
            updateStatus("Key derived successfully using " + algorithm);

            // Add to history
            OperationHistory.getInstance().addOperation(
                    "Keys",
                    "Derive - " + algorithm,
                    "Input: " + inputText.substring(0, Math.min(30, inputText.length())),
                    "Derived: " + DataConverter.bytesToHex(derivedKey).substring(0,
                            Math.min(50, DataConverter.bytesToHex(derivedKey).length())));

        } catch (Exception e) {
            showError("Derivation Error", "Error deriving key: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parse data according to format
     */
    private byte[] parseData(String text, String format) {
        try {
            switch (format) {
                case "UTF-8":
                    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                case "Hex":
                    return DataConverter.hexToBytes(text.replaceAll("\\s+", ""));
                case "Base64":
                    return java.util.Base64.getDecoder().decode(text.replaceAll("\\s+", ""));
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String buildHKDFResult(byte[] input, byte[] salt, byte[] info, int outputLength, byte[] derivedKey,
            String hashAlgo) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("HKDF-").append(hashAlgo).append(" KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: HKDF (RFC 5869) with ").append(hashAlgo).append("\n\n");
        result.append("Input Key Material (").append(input.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(input)).append("\n\n");
        if (salt != null && salt.length > 0) {
            result.append("Salt (").append(salt.length).append(" bytes):\n");
            result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        } else {
            result.append("Salt: (none provided - HKDF will use zeros)\n\n");
        }
        if (info != null && info.length > 0) {
            result.append("Info (").append(info.length).append(" bytes):\n");
            result.append(new String(info, java.nio.charset.StandardCharsets.UTF_8)).append("\n");
            result.append("(hex: ").append(DataConverter.bytesToHex(info)).append(")\n\n");
        }
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ HKDF is deterministic: same inputs always produce same output\n");
        result.append("✓ Used in: TLS 1.3, Signal Protocol, WireGuard\n");
        return result.toString();
    }

    private String buildContextKdfResult(String name, String inputLabel, byte[] input, String firstLabel,
            byte[] firstValue, String secondLabel, byte[] secondValue, int outputLength, byte[] derivedKey,
            String hashAlgorithm) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append(name.toUpperCase()).append("\n");
        result.append("========================================\n\n");
        result.append("Hash/PRF: HMAC-").append(hashAlgorithm).append("\n");
        result.append(inputLabel).append(" (").append(input.length).append(" bytes):\n")
                .append(DataConverter.bytesToHex(input)).append("\n\n");
        appendKdfField(result, firstLabel, firstValue);
        appendKdfField(result, secondLabel, secondValue);
        result.append("Output Length: ").append(outputLength).append(" bytes\n\nDERIVED KEY:\n")
                .append(DataConverter.bytesToHex(derivedKey)).append("\n\n")
                .append("✓ Deterministic: preserve every input to reproduce this result\n");
        return result.toString();
    }

    private void appendKdfField(StringBuilder result, String label, byte[] value) {
        if (label == null) return;
        result.append(label).append(": ");
        if (value == null || value.length == 0) {
            result.append("(empty)\n\n");
        } else {
            result.append(value.length).append(" bytes\n").append(DataConverter.bytesToHex(value)).append("\n\n");
        }
    }

    private String buildPBKDF2Result(byte[] password, byte[] salt, int iterations, int outputLength, byte[] derivedKey,
            String hashAlgo) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("PBKDF2-").append(hashAlgo).append(" KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: PBKDF2 (PKCS #5) with HMAC-").append(hashAlgo).append("\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Iterations: ").append(String.format("%,d", iterations));
        if (iterations < 100000) {
            result.append(" ⚠️ LOW - Recommend 600,000+ (OWASP 2023)");
        } else if (iterations < 600000) {
            result.append(" ⚠️ MEDIUM - Recommend 600,000+ (OWASP 2023)");
        } else {
            result.append(" ✓ GOOD (OWASP 2023 compliant)");
        }
        result.append("\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Standard password-based key derivation\n");
        result.append("✓ Widely supported and battle-tested\n");
        return result.toString();
    }

    private String buildSCryptResult(byte[] password, byte[] salt, int N, int r, int p, int outputLength,
            byte[] derivedKey) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("SCRYPT KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: SCrypt (memory-hard KDF)\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Parameters:\n");
        result.append("  N (CPU/Memory cost): ").append(String.format("%,d", N));
        if (N < 16384) {
            result.append(" ⚠️ LOW");
        } else {
            result.append(" ✓ GOOD");
        }
        result.append("\n");
        result.append("  r (Block size): ").append(r).append("\n");
        result.append("  p (Parallelism): ").append(p).append("\n");
        result.append("  Memory required: ~").append((128 * N * r / 1024)).append(" KB\n\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Memory-hard: resistant to hardware attacks\n");
        result.append("✓ Used in: Litecoin, many password managers\n");
        return result.toString();
    }

    private String buildArgon2Result(byte[] password, byte[] salt, int iterations, int memory, int parallelism,
            int outputLength, byte[] derivedKey) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("ARGON2ID KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: Argon2id (Password Hashing Competition winner 2015)\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Parameters:\n");
        result.append("  Time cost (iterations): ").append(iterations);
        if (iterations < 3) {
            result.append(" ⚠️ LOW");
        } else {
            result.append(" ✓ GOOD");
        }
        result.append("\n");
        result.append("  Memory cost: ").append(memory).append(" KB (").append(memory / 1024).append(" MB)\n");
        result.append("  Parallelism: ").append(parallelism).append(" threads\n\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Most modern and secure password hashing algorithm\n");
        result.append("✓ Combines data-dependent (Argon2i) and data-independent (Argon2d) approaches\n");
        result.append("✓ Recommended for new applications\n");
        return result.toString();
    }
    // ============================================================================
    // CMS / PKCS#7 OPERATIONS
    // ============================================================================

    // CMS UI // CMS
    private TextArea cmsInputArea;
    private TextArea cmsOutputArea;
    private CheckBox cmsDetachedCheck;
    // Split fields
    private TextArea cmsSignCertArea;
    private TextArea cmsSignKeyArea;
    private TextArea cmsEncryptCertArea;
    private TextArea cmsDecryptKeyArea;

    /**
     * Initialize CMS components
     */
    public void initializeCMS(TextArea inputArea, TextArea outputArea, CheckBox detachedCheck,
            TextArea signCertArea, TextArea signKeyArea,
            TextArea encryptCertArea, TextArea decryptKeyArea) {
        this.cmsInputArea = inputArea;
        this.cmsOutputArea = outputArea;
        this.cmsDetachedCheck = detachedCheck;
        this.cmsSignCertArea = signCertArea;
        this.cmsSignKeyArea = signKeyArea;
        this.cmsEncryptCertArea = encryptCertArea;
        this.cmsDecryptKeyArea = decryptKeyArea;
    }

    /**
     * Handle CMS Sign
     */
    public void handleCMSSign() {
        try {
            String dataStr = cmsInputArea.getText();
            String certStr = cmsSignCertArea.getText().trim();
            String keyStr = cmsSignKeyArea.getText().trim();
            boolean detached = cmsDetachedCheck.isSelected();

            if (dataStr.isEmpty() || certStr.isEmpty() || keyStr.isEmpty()) {
                showError("Input Error", "Data, Signer Certificate, and Private Key are required");
                return;
            }

            updateStatus("Signing data...");

            // Parse certificate
            X509Certificate cert = CertificateGenerator.parseCertificate(certStr);

            // Parse private key (assuming PEM format handled by AsymmetricKeyOperations or
            // similar helper)
            // Note: AsymmetricKeyOperations doesn't have a public parsePrivateKey method
            // shown in previous views
            // We'll use a local helper or try standard parsing
            PrivateKey privateKey = parsePrivateKeyFromPEM(keyStr);

            byte[] data = dataStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            byte[] signature = CMSOperations.generateSignedData(data, cert, privateKey, null, detached);

            String output = "-----BEGIN PKCS7-----\n" +
                    java.util.Base64.getEncoder().encodeToString(signature) +
                    "\n-----END PKCS7-----";

            cmsOutputArea.setText(output);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Type", detached ? "Detached SignedData" : "Encapsulated SignedData");
            details.put("Certificate", "Present");
            details.put("Private Key", "[not persisted]");
            mainController.publish(OperationResult.forOperation("CMS Sign")
                    .input(data).output(signature).details(details)
                    .status("CMS signature generated successfully").build());

        } catch (Exception e) {
            showError("Signing Error", "Error signing data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle CMS Verify
     */
    public void handleCMSVerify() {
        try {
            String pkcs7Str = cmsInputArea.getText().trim();

            if (pkcs7Str.isEmpty()) {
                showError("Input Error", "PKCS#7 Signature is required in Input");
                return;
            }

            updateStatus("Verifying signature...");

            // Clean PEM
            String base64 = pkcs7Str.replace("-----BEGIN PKCS7-----", "")
                    .replace("-----END PKCS7-----", "")
                    .replaceAll("\\s+", "");
            byte[] pkcs7Bytes = java.util.Base64.getDecoder().decode(base64);

            // Verify
            CMSOperations.VerificationResult result = CMSOperations.verifySignedData(pkcs7Bytes, null);

            StringBuilder output = new StringBuilder();
            output.append("VERIFICATION RESULT: ").append(result.verified ? "✅ VALID" : "❌ INVALID").append("\n\n");

            if (result.content != null) {
                output.append("SIGNED CONTENT:\n");
                output.append(new String(result.content, java.nio.charset.StandardCharsets.UTF_8)).append("\n\n");
            } else {
                output.append("Content is detached (not present in signature).\n\n");
            }

            if (!result.associatedData.isEmpty()) {
                output.append("SIGNED ATTRIBUTES:\n");
                for (java.util.Map.Entry<String, String> entry : result.associatedData.entrySet()) {
                    output.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            cmsOutputArea.setText(output.toString());
            mainController.publish(OperationResult.forOperation("CMS Verify")
                    .input(pkcs7Bytes).output(result.content)
                    .detail("Result", result.verified ? "VALID" : "INVALID")
                    .status("CMS verification: " + (result.verified ? "valid" : "invalid")).build());

        } catch (Exception e) {
            cmsOutputArea.setText("Verification Failed: " + e.getMessage());
            updateStatus("Verification failed");
            e.printStackTrace();
        }
    }

    /**
     * Handle CMS Encrypt (EnvelopedData)
     */
    public void handleCMSEncrypt() {
        try {
            String dataStr = cmsInputArea.getText();
            String certStr = cmsEncryptCertArea.getText().trim();

            if (dataStr.isEmpty() || certStr.isEmpty()) {
                showError("Input Error", "Data and Recipient Certificate are required");
                return;
            }

            updateStatus("Encrypting data...");

            X509Certificate cert = CertificateGenerator.parseCertificate(certStr);
            byte[] data = dataStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            byte[] encrypted = CMSOperations.generateEnvelopedData(data, cert);

            String output = "-----BEGIN PKCS7-----\n" +
                    java.util.Base64.getEncoder().encodeToString(encrypted) +
                    "\n-----END PKCS7-----";

            cmsOutputArea.setText(output);
            mainController.publish(OperationResult.forOperation("CMS Encrypt")
                    .input(data).output(encrypted).detail("Recipient Certificate", "Present")
                    .status("CMS data encrypted successfully").build());

        } catch (Exception e) {
            showError("Encryption Error", "Error encrypting data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle CMS Decrypt (EnvelopedData)
     */
    public void handleCMSDecrypt() {
        try {
            String pkcs7Str = cmsInputArea.getText().trim();
            String keyStr = cmsDecryptKeyArea.getText().trim();

            if (pkcs7Str.isEmpty() || keyStr.isEmpty()) {
                showError("Input Error", "PKCS#7 Enveloped Data and Private Key are required");
                return;
            }

            updateStatus("Decrypting data...");

            // Clean PEM
            String base64 = pkcs7Str.replace("-----BEGIN PKCS7-----", "")
                    .replace("-----END PKCS7-----", "")
                    .replaceAll("\\s+", "");
            byte[] pkcs7Bytes = java.util.Base64.getDecoder().decode(base64);

            PrivateKey privateKey = parsePrivateKeyFromPEM(keyStr);

            byte[] decrypted = CMSOperations.decryptEnvelopedData(pkcs7Bytes, privateKey);

            String output = new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);

            cmsOutputArea.setText(output);
            mainController.publish(OperationResult.forOperation("CMS Decrypt")
                    .input(pkcs7Bytes).output(decrypted).detail("Private Key", "[not persisted]")
                    .status("CMS data decrypted successfully").build());

        } catch (Exception e) {
            cmsOutputArea.setText("Decryption Failed: " + e.getMessage());
            updateStatus("Decryption failed");
            e.printStackTrace();
        }
    }

    // Helper to parse Private Key from PEM (simplistic version for now)
    private PrivateKey parsePrivateKeyFromPEM(String pemKey) throws Exception {
        String base64 = pemKey.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] encoded = java.util.Base64.getDecoder().decode(base64);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA"); // Defaulting to RSA for now

        try {
            return keyFactory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            // Try as standard RSA private key (PKCS#1) if needed, but Java mostly supports
            // PKCS#8
            // If BouncyCastle is registered, we can try to use it more robustly
            throw new Exception("Could not parse Private Key. Ensure it is PKCS#8 format (or standard PEM). sent: "
                    + e.getMessage());
        }
    }

    // ============================================================================
    // CERTIFICATE CHAIN VALIDATION
    // ============================================================================

    private TextArea chainInputArea;
    private TextArea chainResultArea;

    public void initializeCertificateChain(TextArea inputArea, TextArea resultArea) {
        this.chainInputArea = inputArea;
        this.chainResultArea = resultArea;
    }

    public void handleValidateCertificateChain() {
        try {
            String chainStr = chainInputArea.getText().trim();

            if (chainStr.isEmpty()) {
                showError("Input Error", "Certificate Chain PEM is required");
                return;
            }

            updateStatus("Validating chain...");

            // Extract multiple certificates from PEM sequence
            List<String> pemCerts = new ArrayList<>();
            String[] parts = chainStr.split("-----BEGIN CERTIFICATE-----");

            for (String part : parts) {
                if (part.trim().isEmpty())
                    continue;
                String pem = "-----BEGIN CERTIFICATE-----" + part;
                int endIndex = pem.indexOf("-----END CERTIFICATE-----");
                if (endIndex != -1) {
                    pem = pem.substring(0, endIndex + 25);
                    pemCerts.add(pem);
                }
            }

            if (pemCerts.isEmpty()) {
                showError("Input Error", "No valid PEM certificates found");
                return;
            }

            List<X509Certificate> chain = new ArrayList<>();
            for (String pem : pemCerts) {
                chain.add(CertificateGenerator.parseCertificate(pem));
            }

            // Validate
            CertificateGenerator.ChainValidationResult result = CertificateGenerator.validateCertificateChain(chain);

            StringBuilder sb = new StringBuilder();
            sb.append("CHAIN VALIDATION: ").append(result.isValid ? "✅ VALID" : "❌ INVALID").append("\n\n");

            if (result.message != null) {
                sb.append("Message: ").append(result.message).append("\n\n");
            }

            sb.append("DETAILS:\n");
            for (String detail : result.details) {
                sb.append("- ").append(detail).append("\n");
            }

            chainResultArea.setText(sb.toString());
            chainResultArea.setVisible(true);
            chainResultArea.setManaged(true);

            updateStatus("Chain validation complete: " + (result.isValid ? "Valid" : "Invalid"));
            OperationHistory.getInstance().addOperation("Certificates", "Validate Chain", "Length: " + chain.size(),
                    result.isValid ? "Valid" : "Invalid");

        } catch (Exception e) {
            showError("Validation Error", "Error validating chain: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Global Helper Methods ---

    public void handleClear() {
        // Symmetric
        if (generatedKeyField != null)
            generatedKeyField.clear();
        if (keyInputField != null)
            keyInputField.clear();
        if (validationResultArea != null)
            validationResultArea.clear();
        if (component1Field != null)
            component1Field.clear();
        if (component2Field != null)
            component2Field.clear();
        if (component3Field != null)
            component3Field.clear();
        if (componentResultsArea != null)
            componentResultsArea.clear();
    }

    public void handleClearAsymmetric() {
        // Asymmetric
        if (rsaPublicKeyArea != null)
            rsaPublicKeyArea.clear();
        if (rsaPrivateKeyArea != null)
            rsaPrivateKeyArea.clear();
        if (dsaPublicKeyArea != null)
            dsaPublicKeyArea.clear();
        if (dsaPrivateKeyArea != null)
            dsaPrivateKeyArea.clear();
        if (ecdsaFpPublicKeyArea != null)
            ecdsaFpPublicKeyArea.clear();
        if (ecdsaFpPrivateKeyArea != null)
            ecdsaFpPrivateKeyArea.clear();
        if (ed25519PublicKeyArea != null)
            ed25519PublicKeyArea.clear();
        if (ed25519PrivateKeyArea != null)
            ed25519PrivateKeyArea.clear();
    }

    public String getOutputText() {
        // Check Symmetric Results
        if (componentResultsArea != null && !componentResultsArea.getText().isEmpty()) {
            return componentResultsArea.getText();
        }
        if (validationResultArea != null && !validationResultArea.getText().isEmpty()) {
            return validationResultArea.getText();
        }
        if (generatedKeyField != null && !generatedKeyField.getText().isEmpty()) {
            return generatedKeyField.getText();
        }

        // Check Asymmetric (Public/Private)
        StringBuilder sb = new StringBuilder();
        // RSA
        if (rsaPublicKeyArea != null && !rsaPublicKeyArea.getText().isEmpty()) {
            sb.append("RSA Public Key:\n").append(rsaPublicKeyArea.getText()).append("\n\n");
        }
        if (rsaPrivateKeyArea != null && !rsaPrivateKeyArea.getText().isEmpty()) {
            sb.append("RSA Private Key:\n").append(rsaPrivateKeyArea.getText()).append("\n\n");
        }
        // DSA
        if (dsaPublicKeyArea != null && !dsaPublicKeyArea.getText().isEmpty()) {
            sb.append("DSA Public Key:\n").append(dsaPublicKeyArea.getText()).append("\n\n");
        }

        return sb.toString();
    }
}
