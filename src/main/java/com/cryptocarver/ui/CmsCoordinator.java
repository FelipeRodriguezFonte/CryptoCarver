package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.AppSettings;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

final class CmsCoordinator extends KeysCoordinatorSupport {
    record View(
            TextArea cmsInputArea,
            TextArea cmsOutputArea,
            CheckBox cmsDetachedCheck,
            CheckBox cmsCadesBesCheck,
            CheckBox cmsCadesTCheck,
            TextField cmsCadesTsaUrlField,
            javafx.scene.layout.HBox cmsCadesTsaBox,
            TextArea cmsSignCertArea,
            TextArea cmsSignKeyArea,
            TextArea cmsEncryptCertArea,
            TextArea cmsDecryptKeyArea,
            javafx.scene.control.RadioButton cmsSignSourcePkcs11Radio,
            javafx.scene.layout.GridPane cmsSignLocalGrid,
            javafx.scene.layout.HBox cmsSignPkcs11Box,
            javafx.scene.control.ComboBox<String> cmsSignKeyAliasCombo,
            javafx.scene.control.TextArea cmsVerifyDataArea,
            javafx.scene.control.RadioButton cmsEncryptSourcePkcs11Radio,
            javafx.scene.layout.GridPane cmsEncryptLocalGrid,
            javafx.scene.layout.HBox cmsEncryptPkcs11Box,
            javafx.scene.control.ComboBox<String> cmsEncryptKeyAliasCombo,
            javafx.scene.control.Button cmsSignButton,
            CheckBox cmsOnlineRevocationCheck) { }
    private static final Logger LOG = LoggerFactory.getLogger(CmsCoordinator.class);
    private View controls;

    CmsCoordinator(Supplier<StatusReporter> reporter, BiConsumer<String, String> errors,
            Consumer<String> status, BiFunction<String, Object[], String> text) {
        super(reporter, errors, status, text);
    }
    private View view() { return controls; }
    void initialize(View view) {
        controls = view;
        handleCadesTimestampOptionChanged();
    }

    public void handleCadesTimestampOptionChanged() {
        boolean cadesT = view().cmsCadesTCheck() != null && view().cmsCadesTCheck().isSelected();
        if (cadesT && view().cmsCadesBesCheck() != null) {
            view().cmsCadesBesCheck().setSelected(true);
        }
        if (view().cmsCadesTsaBox() != null) {
            view().cmsCadesTsaBox().setVisible(cadesT);
            view().cmsCadesTsaBox().setManaged(cadesT);
        }
        if (cadesT && view().cmsCadesTsaUrlField() != null && view().cmsCadesTsaUrlField().getText().isBlank()) {
            view().cmsCadesTsaUrlField().setText(AppSettings.getInstance().getCustomTsaUrl());
        }
    }

    public void handleCMSourceChanged() {
        boolean usePkcs11 = view().cmsSignSourcePkcs11Radio() != null && view().cmsSignSourcePkcs11Radio().isSelected();
        if (view().cmsSignLocalGrid() != null) view().cmsSignLocalGrid().setVisible(!usePkcs11);
        if (view().cmsSignLocalGrid() != null) view().cmsSignLocalGrid().setManaged(!usePkcs11);
        if (view().cmsSignPkcs11Box() != null) view().cmsSignPkcs11Box().setVisible(usePkcs11);
        if (view().cmsSignPkcs11Box() != null) view().cmsSignPkcs11Box().setManaged(usePkcs11);
    }

    public void handleLoadCMSKeys() {
        if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
            showError("PKCS#11 Error", "No token is connected. Please connect from the left panel first.");
            return;
        }
        try {
            java.util.List<String> aliases = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().listPrivateKeysWithCertificate();
            view().cmsSignKeyAliasCombo().getItems().setAll(aliases);
            if (!aliases.isEmpty()) {
                view().cmsSignKeyAliasCombo().getSelectionModel().selectFirst();
            }
        } catch (Exception error) {
            showError("PKCS#11 Error", "Unable to list valid signing aliases: " + error.getMessage());
        }
    }

    public void handleCMSEncryptSourceChanged() {
        boolean usePkcs11 = view().cmsEncryptSourcePkcs11Radio() != null && view().cmsEncryptSourcePkcs11Radio().isSelected();
        if (view().cmsEncryptLocalGrid() != null) view().cmsEncryptLocalGrid().setVisible(!usePkcs11);
        if (view().cmsEncryptLocalGrid() != null) view().cmsEncryptLocalGrid().setManaged(!usePkcs11);
        if (view().cmsEncryptPkcs11Box() != null) view().cmsEncryptPkcs11Box().setVisible(usePkcs11);
        if (view().cmsEncryptPkcs11Box() != null) view().cmsEncryptPkcs11Box().setManaged(usePkcs11);
    }

    public void handleLoadCMSEncryptKeys() {
        if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
            showError("PKCS#11 Error", "No token is connected. Please connect from the left panel first.");
            return;
        }
        try {
            java.util.List<String> aliases = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().listPrivateKeysWithCertificate();
            view().cmsEncryptKeyAliasCombo().getItems().setAll(aliases);
            if (!aliases.isEmpty()) {
                view().cmsEncryptKeyAliasCombo().getSelectionModel().selectFirst();
            }
        } catch (Exception error) {
            showError("PKCS#11 Error", "Unable to list valid encrypt/decrypt aliases: " + error.getMessage());
        }
    }

    public void handleCMSSign() {
        try {
            String dataStr = view().cmsInputArea().getText();
            boolean detached = view().cmsDetachedCheck().isSelected();
            boolean cadesBes = view().cmsCadesBesCheck() != null && view().cmsCadesBesCheck().isSelected();
            boolean cadesT = view().cmsCadesTCheck() != null && view().cmsCadesTCheck().isSelected();
            if (cadesT) cadesBes = true;
            boolean usePkcs11 = view().cmsSignSourcePkcs11Radio() != null && view().cmsSignSourcePkcs11Radio().isSelected();

            if (dataStr.isEmpty()) {
                showError("Input Error", "Data to sign is required");
                return;
            }

            byte[] data = dataStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String alias = usePkcs11 ? view().cmsSignKeyAliasCombo().getSelectionModel().getSelectedItem() : null;
            String certStr = usePkcs11 ? null : view().cmsSignCertArea().getText().trim();
            String keyStr = usePkcs11 ? null : view().cmsSignKeyArea().getText().trim();
            String tsaUrl = cadesT ? view().cmsCadesTsaUrlField() == null ? "" : view().cmsCadesTsaUrlField().getText().trim() : null;
            if (usePkcs11 && (alias == null || alias.isEmpty())) {
                showError("Input Error", "Please select a token alias with a valid certificate.");
                return;
            }
            if (!usePkcs11 && (certStr.isEmpty() || keyStr.isEmpty())) {
                showError("Input Error", "Signer Certificate and Private Key are required for local signing");
                return;
            }
            if (cadesT && !tsaUrl.startsWith("http://") && !tsaUrl.startsWith("https://")) {
                showError("CAdES-T TSA", "Enter a valid http:// or https:// TSA URL for CAdES-T.");
                return;
            }
            final boolean cadesBesOption = cadesBes;
            final boolean cadesTOption = cadesT;
            OperationExecutor executor = reporter() == null ? null : reporter().getOperationExecutor();
            if (executor == null) {
                showError("Signing Error", "CMS operation executor is not available");
                return;
            }
            updateStatus("Signing data...");
            executor.execute("CMS/CAdES signing", view().cmsSignButton(), () -> {
                byte[] signature;
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                if (usePkcs11) {
                    signature = cadesBesOption
                            ? com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                                    .signCadesBes(alias, data, detached)
                            : com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                                    .signCms(alias, data, detached);
                    details.put("Source", "PKCS#11 Token");
                    details.put("Alias", alias);
                } else {
                    signature = CmsLogic.signLocal(data, certStr, keyStr, detached, cadesBesOption);
                    details.put("Source", "Local PEM");
                    details.put("Certificate", "Present");
                    details.put("Private Key", "[not persisted]");
                }
                if (cadesTOption) {
                    AppSettings.getInstance().setCustomTsaUrl(tsaUrl);
                    byte[] signatureValue = CMSOperations.cadesSignatureValue(signature);
                    TsaDiagnostics.TokenResult timestamp = TsaDiagnostics.timestamp(tsaUrl, signatureValue, "SHA-256");
                    signature = CMSOperations.addCadesTSignatureTimestamp(signature, timestamp.token());
                    details.put("TSA", tsaUrl);
                    details.put("Timestamp", timestamp.report().generationTime());
                }
                return new CadesSignResult(signature, details);
            }, result -> {
                String output = CmsLogic.armor(result.signature());
                view().cmsOutputArea().setText(output);
                result.details().put("Type", detached ? "Detached SignedData" : "Encapsulated SignedData");
                result.details().put("Profile", cadesTOption ? "CAdES-T" : (cadesBesOption ? "CAdES-BES" : "CMS / PKCS#7"));
                reporter().publish(OperationResult.forOperation(cadesTOption ? "CAdES-T Sign" : (cadesBesOption ? "CAdES-BES Sign" : "CMS Sign"))
                        .input(data).output(result.signature()).details(result.details())
                        .status((cadesTOption ? "CAdES-T" : (cadesBesOption ? "CAdES-BES" : "CMS")) + " signature generated successfully").build());
            }, error -> {
                showError("Signing Error", "Error signing data: " + error.getMessage());
                LOG.warn("Key operation failed", error);
            }, () -> updateStatus("Signing cancelled"));
        } catch (Exception e) {
            showError("Signing Error", "Error signing data: " + e.getMessage());
            LOG.warn("Key operation failed", e);
        }
    }

    public void handleCMSVerify() {
        try {
            if (view().cmsOnlineRevocationCheck() != null && view().cmsOnlineRevocationCheck().isSelected()) {
                handleCMSVerifyOnline();
                return;
            }
            String pkcs7Str = view().cmsInputArea().getText().trim();

            if (pkcs7Str.isEmpty()) {
                showError("Input Error", "PKCS#7 Signature is required in Input");
                return;
            }

            updateStatus("Verifying signature...");

            var verified = CmsLogic.verify(pkcs7Str, view().cmsVerifyDataArea() == null ? null : view().cmsVerifyDataArea().getText(),
                    new java.util.Date());
            byte[] pkcs7Bytes = verified.bytes();
            var result = verified.result();
            var cadesProfile = verified.profile();
            var timestampStatus = verified.timestamp();
            var longTerm = verified.longTerm();
            var longTermValidation = verified.longTermValidation();
            view().cmsOutputArea().setText(verified.report());
            reporter().publish(OperationResult.forOperation(
                            cadesProfile.profile().startsWith("CAdES") ? cadesProfile.profile() + " Verify" : "CMS Verify")
                    .input(pkcs7Bytes).output(result.content)
                    .detail("Result", result.verified ? "VALID" : "INVALID")
                    .detail("Profile", cadesProfile.profile())
                    .detail("Certificate binding", cadesProfile.certificateBindingPresent()
                            ? (cadesProfile.certificateBindingValid() ? "VALID" : "INVALID") : "NOT PRESENT")
                    .detail("Signature timestamp", timestampStatus.present()
                            ? (timestampStatus.imprintValid() ? "VALID" : "INVALID") : "NOT PRESENT")
                    .detail("Long-term evidence", longTerm.level())
                    .detail("CRLs embedded", String.valueOf(longTermValidation.crlCount()))
                    .detail("CRLs signature-valid", String.valueOf(longTermValidation.signatureValidCrlCount()))
                    .detail("CRLs currently valid", String.valueOf(longTermValidation.currentCrlCount()))
                    .status("CMS verification: " + (result.verified ? "valid" : "invalid")).build());

        } catch (Exception e) {
            view().cmsOutputArea().setText("Verification Failed: " + e.getMessage());
            updateStatus("Verification failed");
            LOG.warn("Key operation failed", e);
        }
    }

    private void handleCMSVerifyOnline() {
        String input = view().cmsInputArea() == null ? "" : view().cmsInputArea().getText().trim();
        if (input.isEmpty()) {
            showError("Input Error", "PKCS#7 Signature is required in Input");
            return;
        }
        final byte[] cmsBytes;
        try {
            cmsBytes = CmsLogic.decodeCmsArmored(input);
        } catch (Exception error) {
            showError("Verification Error", "Invalid CMS encoding");
            return;
        }
        final byte[] detached = view().cmsVerifyDataArea() != null && !view().cmsVerifyDataArea().getText().trim().isEmpty()
                ? view().cmsVerifyDataArea().getText().getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        OperationExecutor executor = reporter() == null ? null : reporter().getOperationExecutor();
        if (executor == null) {
            showError("Verification Error", "CMS operation executor is not available");
            return;
        }
        executor.execute("CMS/CAdES online revocation validation", null,
                () -> new com.cryptocarver.crypto.CmsInspector().inspect(cmsBytes, detached, null, true, java.util.List.of()),
                report -> {
                    String text = CmsLogic.onlineReport(report);
                    view().cmsOutputArea().setText(text);
                    updateStatus("CMS revocation validation completed: " + report.getRevocation().status());
                },
                error -> {
                    view().cmsOutputArea().setText("Verification Failed: " + (error.getMessage() == null ? "CMS validation failed" : error.getMessage()));
                    updateStatus("Verification failed");
                },
                () -> updateStatus("CMS validation cancelled"));
    }

    public void handleUpgradeCadesLt() {
        try {
            String current = view().cmsOutputArea() == null ? "" : view().cmsOutputArea().getText().trim();
            if (current.isEmpty()) {
                showError("CAdES-LT", "Generate or paste a CAdES-T signature into the Output area first.");
                return;
            }
            byte[] cadesT = CmsLogic.decodeCmsArmored(current);
            CMSOperations.CadesLongTermStatus status = CmsLogic.inspectLongTerm(cadesT);
            if (!"CAdES-T".equals(status.level())) {
                showError("CAdES-LT", "The selected CMS must be a valid CAdES-T signature without LT evidence.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select CAdES-LT Evidence (CRL required; certificates optional)");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("CRL or certificate evidence", "*.crl", "*.cer", "*.crt", "*.der", "*.pem"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            java.util.List<java.io.File> files = chooser.showOpenMultipleDialog(view().cmsOutputArea().getScene().getWindow());
            if (files == null || files.isEmpty()) return;

            java.util.List<java.security.cert.X509CRL> crls = new java.util.ArrayList<>();
            java.util.List<java.security.cert.X509Certificate> certificates = new java.util.ArrayList<>();
            for (java.io.File file : files) {
                byte[] evidence = java.nio.file.Files.readAllBytes(file.toPath());
                CmsLogic.parseEvidence(evidence, file.getName(), crls, certificates);
            }
            if (crls.isEmpty()) {
                showError("CAdES-LT", "Select at least one CRL. Certificate files alone are not revocation evidence.");
                return;
            }
            byte[] upgraded = CmsLogic.upgradeLongTerm(cadesT, certificates, crls);
            String armored = CmsLogic.armor(upgraded);
            view().cmsOutputArea().setText(armored);
            reporter().publish(OperationResult.forOperation("CAdES-LT Evidence")
                    .input(cadesT).output(upgraded)
                    .detail("CRL evidence", String.valueOf(crls.size()))
                    .detail("Certificate evidence", String.valueOf(certificates.size()))
                    .detail("Network", "Not used; evidence selected locally")
                    .status("CAdES-LT evidence embedded; validate freshness and trust separately").build());
            updateStatus("CAdES-LT evidence embedded from " + crls.size() + " CRL(s) and "
                    + certificates.size() + " certificate(s).");
        } catch (Exception error) {
            showError("CAdES-LT", "Unable to embed LT evidence: " + error.getMessage());
        }
    }

    public void handleCMSEncrypt() {
        try {
            String dataStr = view().cmsInputArea().getText();
            boolean usePkcs11 = view().cmsEncryptSourcePkcs11Radio() != null && view().cmsEncryptSourcePkcs11Radio().isSelected();

            if (dataStr.isEmpty()) {
                showError("Input Error", "Data to encrypt is required");
                return;
            }

            updateStatus("Encrypting data...");
            byte[] data = dataStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            X509Certificate cert;
            String alias = null;

            if (usePkcs11) {
                if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
                    showError("PKCS#11 Error", "No token connected.");
                    return;
                }
                alias = view().cmsEncryptKeyAliasCombo().getValue();
                if (alias == null || alias.isEmpty()) {
                    showError("PKCS#11 Error", "Select an alias from the token.");
                    return;
                }
                cert = (X509Certificate) com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().getCertificateChain(alias)[0];
                if (cert == null) {
                    showError("PKCS#11 Error", "No certificate found for the selected alias.");
                    return;
                }
            } else {
                String certStr = view().cmsEncryptCertArea().getText().trim();
                if (certStr.isEmpty()) {
                    showError("Input Error", "Recipient Certificate is required in Local mode");
                    return;
                }
                cert = CmsLogic.parseRecipient(certStr);
            }

            byte[] encrypted = CmsLogic.encrypt(data, cert);

            String output = CmsLogic.armor(encrypted);

            view().cmsOutputArea().setText(output);
            updateStatus("CMS Encrypted (EnvelopedData) successfully");
            String sourceStr = usePkcs11 ? ("PKCS#11 Token (alias: " + alias + ")") : "Local PEM";
            reporter().publish(com.cryptocarver.model.OperationResult.forOperation("CMS Encrypt (EnvelopedData)")
                    .input(data).output(encrypted)
                    .detail("Source", sourceStr)
                    .status("CMS data encrypted successfully").build());
        } catch (Exception e) {
            showError("Encryption Error", "Error encrypting data: " + e.getMessage());
            LOG.warn("Key operation failed", e);
        }
    }

    public void handleCMSDecrypt() {
        try {
            String pkcs7Str = view().cmsInputArea().getText().trim();
            boolean usePkcs11 = view().cmsEncryptSourcePkcs11Radio() != null && view().cmsEncryptSourcePkcs11Radio().isSelected();

            if (pkcs7Str.isEmpty()) {
                showError("Input Error", "PKCS#7 Enveloped Data is required");
                return;
            }

            updateStatus("Decrypting data...");

            byte[] pkcs7Bytes = CmsLogic.decodeCmsArmored(pkcs7Str);

            byte[] decrypted;
            String alias = null;

            if (usePkcs11) {
                if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
                    showError("PKCS#11 Error", "No token connected.");
                    return;
                }
                alias = view().cmsEncryptKeyAliasCombo().getValue();
                if (alias == null || alias.isEmpty()) {
                    showError("PKCS#11 Error", "Select an alias from the token.");
                    return;
                }
                decrypted = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().decryptCms(alias, pkcs7Bytes);
            } else {
                String keyStr = view().cmsDecryptKeyArea().getText().trim();
                if (keyStr.isEmpty()) {
                    showError("Input Error", "Private Key is required in Local mode");
                    return;
                }
                decrypted = CmsLogic.decryptLocal(pkcs7Bytes, keyStr);
            }

            view().cmsOutputArea().setText(new String(decrypted, java.nio.charset.StandardCharsets.UTF_8));
            updateStatus("CMS Decrypted successfully");
            String sourceStr = usePkcs11 ? ("PKCS#11 Token (alias: " + alias + ")") : "Local PEM";
            reporter().publish(com.cryptocarver.model.OperationResult.forOperation("CMS Decrypt (EnvelopedData)")
                    .input(pkcs7Bytes).output(decrypted)
                    .detail("Source", sourceStr)
                    .detail("Private Key", "[not persisted]")
                    .status("CMS data decrypted successfully").build());
        } catch (Exception e) {
            view().cmsOutputArea().setText("Decryption Failed: " + e.getMessage());
            updateStatus("Decryption failed");
            LOG.warn("Key operation failed", e);
        }
    }

    private record CadesSignResult(byte[] signature, java.util.Map<String, String> details) { }

}
