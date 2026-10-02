package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/** Key material, keystore inspection and token operations. */
final class KeyStoreCoordinator extends KeysCoordinatorSupport {
    record View(
            VBox keysRoot,
            Pkcs11ProfilesController pkcs11ProfilesController,
            IcsfTokenController icsfTokenPaneController,
            IcsfBatchController icsfBatchPaneController,
            IcsfKeyWrapController icsfKeyWrapPaneController,
            TextArea keyMaterialInputArea,
            TextArea keyMaterialReportArea,
            TextArea keyComparePublicArea,
            TextArea keyComparePrivateArea,
            TextArea keyCompareResultArea,
            ComboBox<String> keyStoreTypeCombo,
            PasswordField keyStorePasswordField,
            CheckBox keyStoreUnsafeExtractCheck,
            TextField keyStorePathField,
            TextArea keyStoreReportArea,
            ComboBox<String> keyStoreProfileCombo,
            TextField keyStoreProfileNameField,
            TextField pkcs11NameField,
            TextField pkcs11LibraryField,
            TextField pkcs11SlotField,
            PasswordField pkcs11PinField,
            ComboBox<String> pkcs11ProfileCombo,
            TextArea pkcs11ReportArea,
            ComboBox<String> pkcs11SigningKeyCombo,
            ComboBox<String> pkcs11SignatureAlgorithmCombo,
            TextArea pkcs11DataArea,
            TextArea pkcs11SignatureArea,
            ComboBox<String> pkcs11CertificateAliasCombo,
            TextArea pkcs11CertificateArea,
            ComboBox<String> pkcs11JwtAlgorithmCombo,
            TextArea pkcs11JwtPayloadArea,
            TextArea pkcs11JwtOutputArea,
            TextArea pkcs11CmsDataArea,
            CheckBox pkcs11CmsDetachedCheck,
            TextArea pkcs11CmsOutputArea,
            ComboBox<String> pkcs11WrappingKeyCombo,
            ComboBox<String> pkcs11WrapKeyCombo,
            ComboBox<String> pkcs11WrapTransformationCombo,
            TextArea pkcs11WrapResultArea,
            ComboBox<String> pkcs11UnwrappingKeyCombo,
            TextArea pkcs11UnwrapDataArea,
            ComboBox<String> pkcs11UnwrapTransformationCombo,
            TextField pkcs11UnwrapAlgorithmField,
            ComboBox<String> pkcs11UnwrapTypeCombo,
            TextArea pkcs11UnwrapResultArea) { }

    private final java.util.function.Supplier<View> view;
    private final Runnable refreshHsm;

    KeyStoreCoordinator(java.util.function.Supplier<View> view, java.util.function.Supplier<StatusReporter> reporter, Runnable refreshHsm) {
        super(reporter);
        this.view = view;
        this.refreshHsm = refreshHsm;
    }

    private View view() {
        return view.get();
    }

    void init() {
        if (view().pkcs11ProfilesController() != null && reporter() != null) {
            view().pkcs11ProfilesController().setStatusReporter(reporter());
            view().pkcs11ProfilesController().setOperationExecutor(reporter().getOperationExecutor());
        }
        if (view().icsfTokenPaneController() != null && reporter() != null) {
            view().icsfTokenPaneController().setStatusReporter(reporter());
        }
        if (view().icsfKeyWrapPaneController() != null && reporter() != null) {
            view().icsfKeyWrapPaneController().setStatusReporter(reporter());
        }
        if (view().icsfBatchPaneController() != null && reporter() != null) {
            view().icsfBatchPaneController().setStatusReporter(reporter());
        }
    }

    void handleChooseKeyStore() { chooseKeyStore(); }

    void handleSaveKeyStoreProfile() { saveKeyStoreProfile(); }

    void handleChoosePkcs11Library() { choosePkcs11Library(); }

    void handleConnectPkcs11() {
        connectPkcs11();
        refreshHsm.run();
    }

    void handleDisconnectPkcs11() {
        disconnectPkcs11();
        refreshHsm.run();
    }

    void handlePkcs11Sign() { signWithPkcs11(); }

    void handlePkcs11Verify() { verifyWithPkcs11(); }

    void handleShowPkcs11Certificate() { showPkcs11CertificateChain(); }

    void handleGeneratePkcs11Jwt() { generatePkcs11Jwt(); }

    void handleGeneratePkcs11Cms() { generatePkcs11Cms(); }

    void handlePkcs11Wrap() { wrapWithPkcs11(); }

    void handlePkcs11Unwrap() { unwrapWithPkcs11(); }

    void handleLoadKeyStoreProfile() { loadKeyStoreProfile(); }

    void initializeKeyMaterialInspector() {
    }

    void initializeKeyPairComparator() {
    }

    void initializeKeyStoreInspector() {
        view().keyStoreTypeCombo().getItems().setAll("Auto", "PKCS12", "JKS", "JCEKS");
        view().keyStoreTypeCombo().setValue("Auto");
        refreshKeyStoreProfiles();
    }

    void initializePkcs11Inspector() {
        if (view().pkcs11NameField() != null && view().pkcs11NameField().getText().isBlank()) view().pkcs11NameField().setText("CryptoCarverToken");
        if (view().pkcs11SlotField() != null && view().pkcs11SlotField().getText().isBlank()) view().pkcs11SlotField().setText("0");
        refreshPkcs11Profiles();
        if (view().pkcs11ProfileCombo() != null) {
            view().pkcs11ProfileCombo().setOnAction(e -> handlePkcs11ProfileSelection());
        }
    }

    void initializePkcs11Signing() {
        if (view().pkcs11SignatureAlgorithmCombo() != null) {
            view().pkcs11SignatureAlgorithmCombo().getItems().setAll(
                    "SHA256withRSA", "SHA384withRSA", "SHA512withRSA",
                    "SHA256withECDSA", "SHA384withECDSA", "Ed25519");
            view().pkcs11SignatureAlgorithmCombo().setValue("SHA256withRSA");
        }
        refreshPkcs11SigningKeys();
    }

    void initializePkcs11Certificates() {
        refreshPkcs11CertificateAliases();
    }

    void initializePkcs11Jwt() {
        if (view().pkcs11JwtAlgorithmCombo() != null) {
            view().pkcs11JwtAlgorithmCombo().getItems().setAll("RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "EdDSA");
            view().pkcs11JwtAlgorithmCombo().setValue("RS256");
        }
    }

    void initializePkcs11Cms() {
    }

    void initializePkcs11Wrap() {
        // RSA/ECB/PKCS1Padding is what real tokens actually advertise in practice (confirmed
        // empirically against SoftHSM — see Pkcs11Session#wrapKey); OAEP is offered too in case a
        // specific token/HSM does expose it, but is not the safe default here.
        java.util.List<String> transformations = java.util.List.of("RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        if (view().pkcs11WrapTransformationCombo() != null) {
            view().pkcs11WrapTransformationCombo().getItems().setAll(transformations);
            view().pkcs11WrapTransformationCombo().setValue(transformations.get(0));
        }
        if (view().pkcs11UnwrapTransformationCombo() != null) {
            view().pkcs11UnwrapTransformationCombo().getItems().setAll(transformations);
            view().pkcs11UnwrapTransformationCombo().setValue(transformations.get(0));
        }
        if (view().pkcs11UnwrapTypeCombo() != null) {
            view().pkcs11UnwrapTypeCombo().getItems().setAll("Secret Key", "Private Key", "Public Key");
            view().pkcs11UnwrapTypeCombo().setValue("Secret Key");
        }
        if (view().pkcs11UnwrapAlgorithmField() != null && view().pkcs11UnwrapAlgorithmField().getText().isBlank()) {
            view().pkcs11UnwrapAlgorithmField().setText("AES");
        }
        refreshPkcs11WrapKeyAliases();
    }

    void connectPkcs11() {
        char[] pin = view().pkcs11PinField() == null ? new char[0] : view().pkcs11PinField().getText().toCharArray();
        try {
            int slot = Integer.parseInt(view().pkcs11SlotField().getText().trim());
            var configuration = new com.cryptocarver.crypto.hsm.Pkcs11Configuration(
                    view().pkcs11NameField().getText(), java.nio.file.Path.of(view().pkcs11LibraryField().getText().trim()), slot);
            disconnectPkcs11Internal();
            com.cryptocarver.crypto.hsm.Pkcs11Session pkcs11Session =
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().connect(configuration, pin);
            var objects = pkcs11Session.listObjects();
            StringBuilder report = new StringBuilder("========================================\nPKCS#11 TOKEN SESSION\n========================================\n\n")
                    .append("Provider: ").append(pkcs11Session.providerName()).append("\n")
                    .append("Library: ").append(configuration.library()).append("\n")
                    .append("Slot list index: ").append(configuration.slotListIndex()).append("\n")
                    .append("Objects: ").append(objects.size()).append("\n\n");
            for (var object : objects) {
                report.append("Alias: ").append(object.alias())
                        .append("\nType: ").append(object.objectType())
                        .append("\nAlgorithm: ").append(object.algorithm())
                        .append("\nFormat: ").append(object.format())
                        .append("\nFingerprint: ").append(object.fingerprint())
                        .append("\n----------------------------------------\n");
            }

            report.append("\n========================================\nJCA PROVIDER SERVICES (COMPATIBILITY)\n========================================\n")
                    .append("Advertised services are not a direct PKCS#11 mechanism list; a selected key may still reject an operation.\n\n");
            var sigs = pkcs11Session.getSupportedMechanisms("Signature");
            report.append("Signatures (").append(sigs.size()).append("): ").append(String.join(", ", sigs)).append("\n\n");
            var ciphers = pkcs11Session.getSupportedMechanisms("Cipher");
            report.append("Ciphers (").append(ciphers.size()).append("): ").append(String.join(", ", ciphers)).append("\n\n");
            var macs = pkcs11Session.getSupportedMechanisms("Mac");
            report.append("MACs (").append(macs.size()).append("): ").append(String.join(", ", macs)).append("\n\n");

            report.append("UI Compatible Signatures:\n");
            if (view().pkcs11SignatureAlgorithmCombo() != null) {
                for (String algo : view().pkcs11SignatureAlgorithmCombo().getItems()) {
                    if (sigs.contains(algo)) {
                        report.append(" [YES] ").append(algo).append("\n");
                    } else {
                        report.append(" [NO]  ").append(algo).append("\n");
                    }
                }
            }

            view().pkcs11ReportArea().setText(report.toString());
            refreshPkcs11SigningKeys();
            refreshPkcs11CertificateAliases();
            refreshPkcs11WrapKeyAliases();
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("PKCS#11 Token Connect")
                        .output(report.toString().getBytes(StandardCharsets.UTF_8))
                        .detail("Provider", pkcs11Session.providerName())
                        .detail("Slot list index", String.valueOf(slot))
                        .detail("Objects", String.valueOf(objects.size()))
                        .status("PKCS#11 token connected; " + objects.size() + " object(s) discovered")
                        .build());
            }
        } catch (Exception error) {
            showError("PKCS#11 connection", "Unable to open token: " + safePkcs11Message(error));
        } finally {
            java.util.Arrays.fill(pin, '\0');
            if (view().pkcs11PinField() != null) view().pkcs11PinField().clear();
        }
    }

    void disconnectPkcs11() {
        boolean wasConnected = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected();
        disconnectPkcs11Internal();
        if (view().pkcs11ReportArea() != null) {
            view().pkcs11ReportArea().setText(wasConnected ? "PKCS#11 session closed. Token keys remain on the token." : "No PKCS#11 session is open.");
        }
        updateStatus(com.cryptocarver.service.I18nService.getInstance().text(
                wasConnected ? "module.keys.pkcs11Closed" : "module.keys.pkcs11NotOpen"));
        refreshPkcs11SigningKeys();
        refreshPkcs11CertificateAliases();
        refreshPkcs11WrapKeyAliases();
    }

    void choosePkcs11Library() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PKCS#11 native library");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PKCS#11 libraries", "*.dylib", "*.so", "*.dll"),
                new FileChooser.ExtensionFilter("All files", "*"));
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null && view().pkcs11LibraryField() != null) view().pkcs11LibraryField().setText(selected.getAbsolutePath());
    }

    void handleSavePkcs11Profile() {
        if (view().pkcs11NameField() == null || view().pkcs11LibraryField() == null || view().pkcs11SlotField() == null) return;
        String name = view().pkcs11NameField().getText().trim();
        String library = view().pkcs11LibraryField().getText().trim();
        String slotStr = view().pkcs11SlotField().getText().trim();
        if (name.isEmpty() || library.isEmpty()) {
            showError("Save Profile", "Profile name and library path are required.");
            return;
        }
        int slot = 0;
        try {
            slot = Integer.parseInt(slotStr);
        } catch (NumberFormatException e) {
            showError("Save Profile", "Slot must be a valid integer.");
            return;
        }
        if (slot < 0) {
            showError("Save Profile", "Slot must be zero or greater.");
            return;
        }
        com.cryptocarver.model.AppSettings.getInstance().savePkcs11Profile(name, library, slot);
        refreshPkcs11Profiles();
        if (view().pkcs11ProfileCombo() != null) view().pkcs11ProfileCombo().setValue(name);
        updateStatus("PKCS#11 profile '" + name + "' saved");
    }

    void handleDeletePkcs11Profile() {
        if (view().pkcs11ProfileCombo() == null || view().pkcs11ProfileCombo().getValue() == null) return;
        String name = view().pkcs11ProfileCombo().getValue();
        com.cryptocarver.model.AppSettings.getInstance().removePkcs11Profile(name);
        refreshPkcs11Profiles();
        updateStatus("PKCS#11 profile '" + name + "' deleted");
    }

    void handlePkcs11ProfileSelection() {
        if (view().pkcs11ProfileCombo() == null || view().pkcs11ProfileCombo().getValue() == null) return;
        String name = view().pkcs11ProfileCombo().getValue();
        for (var profile : com.cryptocarver.model.AppSettings.getInstance().getPkcs11Profiles()) {
            if (profile.name().equalsIgnoreCase(name)) {
                view().pkcs11NameField().setText(profile.name());
                view().pkcs11LibraryField().setText(profile.library());
                view().pkcs11SlotField().setText(String.valueOf(profile.slot()));
                if (view().pkcs11PinField() != null) view().pkcs11PinField().clear(); // Ensure PIN is blank
                break;
            }
        }
    }

    void refreshPkcs11Profiles() {
        if (view().pkcs11ProfileCombo() == null) return;
        String current = view().pkcs11ProfileCombo().getValue();
        view().pkcs11ProfileCombo().getItems().clear();
        for (var profile : com.cryptocarver.model.AppSettings.getInstance().getPkcs11Profiles()) {
            view().pkcs11ProfileCombo().getItems().add(profile.name());
        }
        if (current != null && view().pkcs11ProfileCombo().getItems().contains(current)) {
            view().pkcs11ProfileCombo().setValue(current);
        }
    }

    void disconnectPkcs11Internal() {
        com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().disconnect();
    }

    String safePkcs11Message(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    void refreshPkcs11SigningKeys() {
        if (view().pkcs11SigningKeyCombo() == null) return;
        String selected = view().pkcs11SigningKeyCombo().getValue();
        view().pkcs11SigningKeyCombo().getItems().clear();
        try {
            view().pkcs11SigningKeyCombo().getItems().addAll(
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().listPrivateKeyAliases());
            if (selected != null && view().pkcs11SigningKeyCombo().getItems().contains(selected)) {
                view().pkcs11SigningKeyCombo().setValue(selected);
            } else if (!view().pkcs11SigningKeyCombo().getItems().isEmpty()) {
                view().pkcs11SigningKeyCombo().setValue(view().pkcs11SigningKeyCombo().getItems().get(0));
            }
        } catch (Exception ignored) {
            // No token session is expected before the user connects one.
        }
    }

    void refreshPkcs11CertificateAliases() {
        if (view().pkcs11CertificateAliasCombo() == null) return;
        String selected = view().pkcs11CertificateAliasCombo().getValue();
        view().pkcs11CertificateAliasCombo().getItems().clear();
        try {
            view().pkcs11CertificateAliasCombo().getItems().addAll(
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().listCertificateAliases());
            if (selected != null && view().pkcs11CertificateAliasCombo().getItems().contains(selected)) {
                view().pkcs11CertificateAliasCombo().setValue(selected);
            } else if (!view().pkcs11CertificateAliasCombo().getItems().isEmpty()) {
                view().pkcs11CertificateAliasCombo().setValue(view().pkcs11CertificateAliasCombo().getItems().get(0));
            }
        } catch (Exception ignored) {
            // No token session is expected before the user connects one.
        }
    }

    void refreshPkcs11WrapKeyAliases() {
        if (view().pkcs11WrappingKeyCombo() == null && view().pkcs11WrapKeyCombo() == null && view().pkcs11UnwrappingKeyCombo() == null) return;
        String selectedWrapping = view().pkcs11WrappingKeyCombo() == null ? null : view().pkcs11WrappingKeyCombo().getValue();
        String selectedTarget = view().pkcs11WrapKeyCombo() == null ? null : view().pkcs11WrapKeyCombo().getValue();
        String selectedUnwrapping = view().pkcs11UnwrappingKeyCombo() == null ? null : view().pkcs11UnwrappingKeyCombo().getValue();
        if (view().pkcs11WrappingKeyCombo() != null) view().pkcs11WrappingKeyCombo().getItems().clear();
        if (view().pkcs11WrapKeyCombo() != null) view().pkcs11WrapKeyCombo().getItems().clear();
        if (view().pkcs11UnwrappingKeyCombo() != null) view().pkcs11UnwrappingKeyCombo().getItems().clear();
        try {
            var session = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession();
            java.util.List<String> allAliases = session.listObjects().stream()
                    .map(com.cryptocarver.crypto.hsm.Pkcs11ObjectInfo::alias)
                    .distinct().toList();
            java.util.List<String> privateAliases = session.listPrivateKeysWithCertificate();
            if (view().pkcs11WrappingKeyCombo() != null) {
                view().pkcs11WrappingKeyCombo().getItems().addAll(privateAliases);
                selectComboValue(view().pkcs11WrappingKeyCombo(), selectedWrapping);
            }
            if (view().pkcs11UnwrappingKeyCombo() != null) {
                view().pkcs11UnwrappingKeyCombo().getItems().addAll(privateAliases);
                selectComboValue(view().pkcs11UnwrappingKeyCombo(), selectedUnwrapping);
            }
            if (view().pkcs11WrapKeyCombo() != null) {
                view().pkcs11WrapKeyCombo().getItems().addAll(allAliases);
                selectComboValue(view().pkcs11WrapKeyCombo(), selectedTarget);
            }
        } catch (Exception ignored) {
            // No token session is expected before the user connects one.
        }
    }

    static void selectComboValue(ComboBox<String> combo, String previous) {
        if (previous != null && combo.getItems().contains(previous)) {
            combo.setValue(previous);
        } else if (!combo.getItems().isEmpty()) {
            combo.setValue(combo.getItems().get(0));
        }
    }

    void wrapWithPkcs11() {
        try {
            String wrappingAlias = requireComboValue(view().pkcs11WrappingKeyCombo(),
                    "Connect a token, then select a wrapping key alias");
            String targetAlias = requireComboValue(view().pkcs11WrapKeyCombo(),
                    "Select the alias of the key to wrap");
            String transformation = view().pkcs11WrapTransformationCombo() == null || view().pkcs11WrapTransformationCombo().getValue() == null
                    ? "RSA/ECB/PKCS1Padding" : view().pkcs11WrapTransformationCombo().getValue();
            byte[] wrapped = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .wrapKey(wrappingAlias, targetAlias, transformation);
            String hex = DataConverter.bytesToHex(wrapped);
            view().pkcs11WrapResultArea().setText(hex);
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("PKCS#11 Wrap Key")
                        .output(wrapped)
                        .detail("Wrapping key alias", wrappingAlias)
                        .detail("Wrapped key alias", targetAlias)
                        .detail("Transformation", transformation)
                        .status("Wrapped '" + targetAlias + "' under '" + wrappingAlias + "'").build());
            }
        } catch (Exception error) {
            showError("PKCS#11 Wrap", "Unable to wrap key: " + safePkcs11Message(error));
        }
    }

    void unwrapWithPkcs11() {
        try {
            String unwrappingAlias = requireComboValue(view().pkcs11UnwrappingKeyCombo(),
                    "Connect a token, then select an unwrapping key alias");
            byte[] wrapped = DataConverter.hexToBytes(requirePkcs11Text(view().pkcs11UnwrapDataArea(), "Wrapped key"));
            String transformation = view().pkcs11UnwrapTransformationCombo() == null || view().pkcs11UnwrapTransformationCombo().getValue() == null
                    ? "RSA/ECB/PKCS1Padding" : view().pkcs11UnwrapTransformationCombo().getValue();
            String algorithm = view().pkcs11UnwrapAlgorithmField() == null || view().pkcs11UnwrapAlgorithmField().getText().isBlank()
                    ? "AES" : view().pkcs11UnwrapAlgorithmField().getText().trim();
            int keyType = switch (view().pkcs11UnwrapTypeCombo() == null || view().pkcs11UnwrapTypeCombo().getValue() == null
                    ? "Secret Key" : view().pkcs11UnwrapTypeCombo().getValue()) {
                case "Private Key" -> javax.crypto.Cipher.PRIVATE_KEY;
                case "Public Key" -> javax.crypto.Cipher.PUBLIC_KEY;
                default -> javax.crypto.Cipher.SECRET_KEY;
            };
            java.security.Key unwrapped = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .unwrapKey(unwrappingAlias, wrapped, transformation, algorithm, keyType);
            // The recovered key material is never displayed or logged — only a description of the
            // handle, matching how every other PKCS#11 operation in this class treats key material.
            String summary = "Unwrapped " + unwrapped.getClass().getSimpleName()
                    + " (algorithm=" + unwrapped.getAlgorithm() + ", format=" + unwrapped.getFormat() + ")";
            view().pkcs11UnwrapResultArea().setText(summary);
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("PKCS#11 Unwrap Key")
                        .input(wrapped)
                        .detail("Unwrapping key alias", unwrappingAlias)
                        .detail("Transformation", transformation)
                        .detail("Recovered algorithm", unwrapped.getAlgorithm())
                        .status(summary).build());
            }
        } catch (Exception error) {
            showError("PKCS#11 Unwrap", "Unable to unwrap key: " + safePkcs11Message(error));
        }
    }

    static String requireComboValue(ComboBox<String> combo, String message) {
        String value = combo == null ? null : combo.getValue();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    void showPkcs11CertificateChain() {
        try {
            String alias = view().pkcs11CertificateAliasCombo() == null ? null : view().pkcs11CertificateAliasCombo().getValue();
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException("Connect a token and select an alias with a certificate");
            }
            String pem = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .certificateChainPem(alias);
            view().pkcs11CertificateArea().setText(pem);
            reporter().publish(OperationResult.forOperation("PKCS#11 Certificate Export")
                    .output(pem.getBytes(StandardCharsets.US_ASCII))
                    .detail("Key alias", alias).detail("Content", "Public X.509 certificate chain")
                    .status("Exported public certificate chain from PKCS#11 token").build());
        } catch (Exception error) {
            showError("PKCS#11 certificate", "Unable to load certificate chain: " + safePkcs11Message(error));
        }
    }

    void handleUpdatePkcs11CertificateChain() {
        try {
            String alias = view().pkcs11CertificateAliasCombo() == null ? null : view().pkcs11CertificateAliasCombo().getValue();
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException("Connect a token and select an alias to update");
            }

            String pem = view().pkcs11CertificateArea().getText().trim();
            if (pem.isEmpty()) {
                throw new IllegalArgumentException("Paste the PEM certificate chain in the text area");
            }

            List<X509Certificate> chain = new ArrayList<>();
            String[] parts = pem.split("-----BEGIN CERTIFICATE-----");
            for (String part : parts) {
                if (part.trim().isEmpty()) continue;
                String certPem = "-----BEGIN CERTIFICATE-----" + part;
                int endIndex = certPem.indexOf("-----END CERTIFICATE-----");
                if (endIndex != -1) {
                    certPem = certPem.substring(0, endIndex + 25);
                    chain.add(CertificateGenerator.parseCertificate(certPem));
                }
            }

            if (chain.isEmpty()) {
                throw new IllegalArgumentException("No valid PEM certificates found");
            }

            // Determine the leaf from verified issuer relationships so the
            // confirmation describes the certificate that will be installed.
            java.security.cert.X509Certificate leaf = null;
            for (java.security.cert.X509Certificate cert : chain) {
                boolean isIssuer = false;
                for (java.security.cert.X509Certificate other : chain) {
                    if (cert != other && KeysMaterialSupport.isVerifiedIssuer(cert, other)) {
                        isIssuer = true;
                        break;
                    }
                }
                if (!isIssuer) {
                    if (leaf != null) throw new IllegalArgumentException("Chain contains multiple leaves");
                    leaf = cert;
                }
            }
            if (leaf == null) {
                throw new IllegalArgumentException("Could not determine a unique leaf in the chain");
            }

            String subject = leaf.getSubjectX500Principal().getName();
            String issuer = leaf.getIssuerX500Principal().getName();

            javafx.stage.Window owner = view().keysRoot() == null || view().keysRoot().getScene() == null
                    ? null : view().keysRoot().getScene().getWindow();
            boolean confirmed = dialogService.confirmDestructive(owner, "Confirm Token Update",
                    "Updating certificate chain for alias: " + alias + "\n\nLeaf Subject: " + subject
                            + "\nLeaf Issuer: " + issuer + "\nChain length: " + chain.size()
                            + "\n\nProceed with token modification?", "Update");
            if (!confirmed) {
                updateStatus("Update cancelled by user");
                return;
            }

            com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .updateCertificateChain(alias, chain.toArray(new java.security.cert.Certificate[0]));

            updateStatus("Successfully updated certificate chain for token alias: " + alias);

            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("Update PKCS#11 Certificate Chain")
                        .detail("Alias", alias)
                        .detail("Subject", subject)
                        .detail("Issuer", issuer)
                        .detail("Chain Length", String.valueOf(chain.size()))
                        .status("Success").build());
            }
        } catch (Exception error) {
            showError("Update PKCS#11 certificate chain", "Failed to update chain: " + safePkcs11Message(error));
        }
    }

    void generatePkcs11Jwt() {
        try {
            String alias = requirePkcs11SigningAlias();
            String payload = requirePkcs11TextPayload(view().pkcs11JwtPayloadArea(), "JWT claims JSON");
            String algorithm = view().pkcs11JwtAlgorithmCombo() == null ? null : view().pkcs11JwtAlgorithmCombo().getValue();
            String compactJws = com.cryptocarver.crypto.JOSEService.generateSignedJwtWithPkcs11(payload, algorithm,
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession(), alias);
            view().pkcs11JwtOutputArea().setText(compactJws);
            reporter().publish(OperationResult.forOperation("PKCS#11 Signed JWT")
                    .input(payload.getBytes(StandardCharsets.UTF_8)).output(compactJws.getBytes(StandardCharsets.US_ASCII))
                    .detail("Key alias", alias).detail("Algorithm", algorithm).detail("Serialization", "Compact JWS")
                    .status("JWT signed by PKCS#11 token object " + alias).build());
        } catch (Exception error) {
            showError("PKCS#11 JWT", "Unable to create signed JWT: " + safePkcs11Message(error));
        }
    }

    void generatePkcs11Cms() {
        try {
            String alias = requirePkcs11SigningAlias();
            byte[] data = DataConverter.hexToBytes(requirePkcs11Text(view().pkcs11CmsDataArea(), "CMS data"));
            boolean detached = view().pkcs11CmsDetachedCheck() != null && view().pkcs11CmsDetachedCheck().isSelected();
            byte[] cms = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .signCms(alias, data, detached);
            String base64 = java.util.Base64.getEncoder().encodeToString(cms);
            view().pkcs11CmsOutputArea().setText(base64);
            reporter().publish(OperationResult.forOperation("PKCS#11 CMS SignedData")
                    .input(data).output(cms)
                    .detail("Key alias", alias).detail("Detached", String.valueOf(detached))
                    .detail("Encoding", "Base64 CMS/PKCS#7")
                    .status("CMS SignedData created by PKCS#11 token object " + alias).build());
        } catch (Exception error) {
            showError("PKCS#11 CMS", "Unable to create CMS SignedData: " + safePkcs11Message(error));
        }
    }

    void signWithPkcs11() {
        try {
            String alias = requirePkcs11SigningAlias();
            byte[] data = DataConverter.hexToBytes(requirePkcs11Text(view().pkcs11DataArea(), "Data"));
            String algorithm = view().pkcs11SignatureAlgorithmCombo().getValue();
            byte[] signature = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .sign(alias, data, algorithm);
            view().pkcs11SignatureArea().setText(DataConverter.bytesToHex(signature));
            reporter().publish(OperationResult.forOperation("PKCS#11 Sign")
                    .input(data).output(signature)
                    .detail("Key alias", alias).detail("Algorithm", algorithm)
                    .status("Signature created by PKCS#11 token object " + alias).build());
        } catch (Exception error) {
            showError("PKCS#11 signing", "Unable to sign: " + safePkcs11Message(error));
        }
    }

    void verifyWithPkcs11() {
        try {
            String alias = requirePkcs11SigningAlias();
            byte[] data = DataConverter.hexToBytes(requirePkcs11Text(view().pkcs11DataArea(), "Data"));
            byte[] signature = DataConverter.hexToBytes(requirePkcs11Text(view().pkcs11SignatureArea(), "Signature"));
            String algorithm = view().pkcs11SignatureAlgorithmCombo().getValue();
            boolean valid = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .verify(alias, data, signature, algorithm);
            reporter().publish(OperationResult.forOperation("PKCS#11 Signature Verify")
                    .input(data).output(signature)
                    .detail("Key alias", alias).detail("Algorithm", algorithm).detail("Valid", String.valueOf(valid))
                    .status("PKCS#11 signature verification: " + (valid ? "VALID" : "INVALID")).build());
            if (valid) updateStatus("PKCS#11 signature is valid");
            else showError("PKCS#11 verification", "Signature is not valid for the selected token key");
        } catch (Exception error) {
            showError("PKCS#11 verification", "Unable to verify: " + safePkcs11Message(error));
        }
    }

    String requirePkcs11SigningAlias() {
        String alias = view().pkcs11SigningKeyCombo() == null ? null : view().pkcs11SigningKeyCombo().getValue();
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Connect a token that exposes a private-key object and select its alias");
        }
        return alias;
    }

    static String requirePkcs11Text(TextArea area, String name) {
        String value = area == null ? null : area.getText().replaceAll("\\s+", "");
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " hex is required");
        return value;
    }

    static String requirePkcs11TextPayload(TextArea area, String name) {
        String value = area == null ? null : area.getText().trim();
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    void handleInspectKeyMaterial() {
        try {
            String pem = view().keyMaterialInputArea().getText().trim();
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
            view().keyMaterialReportArea().setText(report);
            updateStatus("Key material inspected successfully");
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Key Material Inspection")
                        .enrichedOutput(report, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("Key material inspected successfully")
                        .build());
            }
        } catch (Exception e) {
            showError("Key Material Inspector", "Cannot inspect material: " + e.getMessage());
        }
    }

    void handleCompareKeyPair() {
        try {
            java.security.PublicKey publicKey = KeysMaterialSupport.parsePublicMaterial(view().keyComparePublicArea().getText().trim());
            java.security.PrivateKey privateKey = KeysMaterialSupport.parsePrivateMaterial(view().keyComparePrivateArea().getText().trim());
            boolean matches = KeyMaterialInspector.matches(publicKey, privateKey);
            String reportText = "========================================\nKEY PAIR COMPARISON\n========================================\n\n"
                    + "Public algorithm: " + publicKey.getAlgorithm() + "\nPrivate algorithm: " + privateKey.getAlgorithm() + "\n"
                    + "Public SHA-256: " + KeyMaterialInspector.fingerprint(publicKey.getEncoded()) + "\n\n"
                    + (matches ? "✓ MATCH: the private key successfully signed a challenge verified by the public key."
                            : "✗ NO MATCH: signature verification failed or the algorithms are incompatible.");
            view().keyCompareResultArea().setText(reportText);
            updateStatus(matches ? "Key pair comparison: match" : "Key pair comparison: no match");
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Key Pair Comparison")
                        .enrichedOutput(reportText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status(matches ? "Key pair comparison: match" : "Key pair comparison: no match")
                        .build());
            }
        } catch (Exception e) {
            showError("Compare Key Pair", "Cannot compare material: " + e.getMessage());
        }
    }

    void handleInspectKeyStore() {
        char[] password = view().keyStorePasswordField().getText().toCharArray();
        try {
            boolean unsafe = view().keyStoreUnsafeExtractCheck().isSelected();
            var report = KeyStoreInspector.inspect(java.nio.file.Path.of(view().keyStorePathField().getText().trim()), password,
                    view().keyStoreTypeCombo().getValue(), unsafe);
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
            view().keyStoreReportArea().setText(text.toString());
            updateStatus("KeyStore inspected: " + report.entries().size() + " entries");
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("KeyStore Inspection")
                        .enrichedOutput(text.toString(), unsafe ? com.cryptocarver.model.OperationDetail.Classification.SECRET : com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("KeyStore inspected: " + report.entries().size() + " entries")
                        .build());
            }
        } catch (Exception e) {
            showError("KeyStore Inspector", "Cannot inspect keystore: " + e.getMessage());
        } finally {
            java.util.Arrays.fill(password, '\0');
            view().keyStorePasswordField().clear();
        }
    }

    void chooseKeyStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PKCS#12, JKS or JCEKS KeyStore");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("KeyStores", "*.p12", "*.pfx", "*.jks", "*.jceks"),
                new FileChooser.ExtensionFilter("All files", "*"));
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null) view().keyStorePathField().setText(selected.getAbsolutePath());
    }

    void saveKeyStoreProfile() {
        try {
            AppSettings.getInstance().saveTrustStoreProfile(view().keyStoreProfileNameField().getText(), view().keyStorePathField().getText(), view().keyStoreTypeCombo().getValue());
            refreshKeyStoreProfiles();
            view().keyStoreProfileCombo().setValue(view().keyStoreProfileNameField().getText().trim());
            updateStatus("KeyStore profile saved (password not stored)");
        } catch (Exception e) {
            showError("KeyStore Profile", e.getMessage());
        }
    }

    void loadKeyStoreProfile() {
        String name = view().keyStoreProfileCombo().getValue();
        if (name == null || name.isBlank()) return;
        AppSettings.getInstance().getTrustStoreProfiles().stream().filter(profile -> name.equals(profile.name())).findFirst().ifPresent(profile -> {
            view().keyStorePathField().setText(profile.path());
            view().keyStoreTypeCombo().setValue(profile.type());
            view().keyStorePasswordField().clear();
            updateStatus("KeyStore profile loaded; enter password to inspect");
        });
    }

    void refreshKeyStoreProfiles() {
        if (view().keyStoreProfileCombo() == null) return;
        view().keyStoreProfileCombo().getItems().setAll(AppSettings.getInstance().getTrustStoreProfiles().stream()
                .map(AppSettings.TrustStoreProfile::name).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

}
