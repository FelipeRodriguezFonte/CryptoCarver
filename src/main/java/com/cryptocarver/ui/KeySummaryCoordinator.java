package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.GeneratedAsymmetricKeySummary;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Generation summaries, export and Shelf actions. */
final class KeySummaryCoordinator extends KeysCoordinatorSupport {
    record View(
            ComboBox<String> keyTypeCombo,
            Button saveGeneratedKeyButton,
            ComboBox<Integer> rsaKeySizeCombo,
            ComboBox<String> ecdsaCurveCombo,
            ComboBox<String> dsaKeySizeCombo,
            TextArea ecdsaPublicKeyArea,
            TextArea ecdsaPrivateKeyArea,
            TextArea eddsaPublicKeyArea,
            TextArea eddsaPrivateKeyArea,
            TabPane rsaKeyMaterialTabs,
            TabPane ecdsaKeyMaterialTabs,
            TabPane dsaKeyMaterialTabs,
            TabPane eddsaKeyMaterialTabs,
            TextArea generatedKeyField,
            VBox generatedKeySummaryCard,
            Label summaryAlgoLabel,
            Label summaryLengthLabel,
            Label summaryKcvLabel,
            Label summaryFingerprintLabel,
            Label summaryParityLabel,
            Label summaryOriginLabel,
            Label summarySavedStatusLabel,
            TitledPane validationPane,
            CheckBox useFourByteKcvCheck,
            VBox rsaSummaryCard,
            VBox ecdsaSummaryCard,
            VBox dsaSummaryCard,
            VBox eddsaSummaryCard,
            TextField keyInputField,
            TextArea validationResultArea,
            TextArea componentResultsArea,
            TextField component1Field,
            TextField component2Field,
            TextField component3Field,
            TextArea rsaPublicKeyArea,
            TextArea rsaPrivateKeyArea,
            TextArea dsaPublicKeyArea,
            TextArea dsaPrivateKeyArea,
            TextArea ecdsaFpPublicKeyArea,
            TextArea ecdsaFpPrivateKeyArea,
            TextArea ed25519PublicKeyArea,
            TextArea ed25519PrivateKeyArea) { }

    private final java.util.function.Supplier<View> view;
    private final KeysWorkspaceState workspace;
    private final Runnable validateKey;

    KeySummaryCoordinator(java.util.function.Supplier<View> view, java.util.function.Supplier<StatusReporter> reporter, KeysWorkspaceState workspace, Runnable validateKey) {
        super(reporter);
        this.view = view;
        this.workspace = workspace;
        this.validateKey = validateKey;
    }

    private View view() {
        return view.get();
    }

    private enum AsymmetricShelfMaterial {
        PUBLIC,
        PRIVATE
    }

    void acceptAsymmetricGeneration(AsymmetricKeyGenerationCoordinator.StateUpdate update) {
        workspace.lastGeneratedKeyPair = update.keyPair();
        workspace.lastKeyType = update.algorithm();
        switch (update.algorithm()) {
            case "RSA" -> workspace.currentRsaSummary = update.summary();
            case "DSA" -> workspace.currentDsaSummary = update.summary();
            case "ECDSA" -> workspace.currentEcdsaSummary = update.summary();
            case "Ed25519" -> workspace.currentEddsaSummary = update.summary();
            default -> throw new IllegalArgumentException("Unsupported generation algorithm");
        }
    }

    void hideGeneratedKeySummary() {
        workspace.currentGeneratedKeySummary = null;
        if (view().generatedKeySummaryCard() != null) {
            view().generatedKeySummaryCard().setVisible(false);
            view().generatedKeySummaryCard().setManaged(false);
        }
    }

    void updateGeneratedKeySummaryCard(com.cryptocarver.model.GeneratedKeySummary summary) {
        if (view().generatedKeySummaryCard() == null || summary == null) return;
        if (view().summaryAlgoLabel() != null) view().summaryAlgoLabel().setText(summary.getAlgorithm());
        if (view().summaryLengthLabel() != null) view().summaryLengthLabel().setText(summary.getFormattedLength());
        if (view().summaryKcvLabel() != null) view().summaryKcvLabel().setText(summary.getFormattedKcv(selectedKcvLength()));
        if (view().summaryFingerprintLabel() != null) view().summaryFingerprintLabel().setText(summary.getFingerprintTruncated());
        if (view().summaryParityLabel() != null) view().summaryParityLabel().setText(summary.getParityStatus());
        if (view().summaryOriginLabel() != null) view().summaryOriginLabel().setText(summary.getOrigin());
        if (view().summarySavedStatusLabel() != null) {
            view().summarySavedStatusLabel().setText(summary.getSavedStatus() != null ? "✓ " + summary.getSavedStatus() : "");
        }
        view().generatedKeySummaryCard().setVisible(true);
        view().generatedKeySummaryCard().setManaged(true);
    }

    void handleCopyGeneratedKey() {
        if (workspace.currentGeneratedKeySummary == null || workspace.currentGeneratedKeySummary.getRawKeyBytes().length == 0) {
            updateStatus("No generated key summary available to copy.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (!AppSettings.isFullLab()) {
            updateStatus("Action blocked: Secret key cannot be copied in current visibility mode.");
            showInfo("Security Policy", "Copying key material is blocked under " + profile + " mode. Switch to FULL_LAB to copy secret keys.");
            return;
        }
        copyToClipboard(workspace.currentGeneratedKeySummary.getRawKeyHex());
        updateStatus("Copied generated key to clipboard");
    }

    void handleCopyGeneratedKcv() {
        if (workspace.currentGeneratedKeySummary == null) {
            updateStatus("No generated key summary available to copy.");
            return;
        }
        String kcv = workspace.currentGeneratedKeySummary.getFormattedKcv(selectedKcvLength());
        copyToClipboard(kcv);
        updateStatus("Copied KCV to clipboard: " + kcv);
    }

    void handleCopyGeneratedSummary() {
        if (workspace.currentGeneratedKeySummary == null) {
            updateStatus("No generated key summary available to copy.");
            return;
        }
        String keyDisplay = (AppSettings.isFullLab())
                ? workspace.currentGeneratedKeySummary.getRawKeyHex()
                : "***MASKED***";

        StringBuilder sb = new StringBuilder();
        sb.append("--- Generated Key Summary ---\n");
        sb.append("Algorithm: ").append(workspace.currentGeneratedKeySummary.getAlgorithm()).append("\n");
        sb.append("Length: ").append(workspace.currentGeneratedKeySummary.getFormattedLength()).append("\n");
        sb.append("KCV: ").append(workspace.currentGeneratedKeySummary.getFormattedKcv(selectedKcvLength())).append("\n");
        sb.append("Fingerprint: ").append(workspace.currentGeneratedKeySummary.getFingerprintTruncated()).append("\n");
        sb.append("Odd Parity: ").append(workspace.currentGeneratedKeySummary.getParityStatus()).append("\n");
        sb.append("Origin: ").append(workspace.currentGeneratedKeySummary.getOrigin()).append("\n");
        sb.append("Key: ").append(keyDisplay);
        if (workspace.currentGeneratedKeySummary.getSavedStatus() != null) {
            sb.append("\nStatus: ").append(workspace.currentGeneratedKeySummary.getSavedStatus());
        }

        copyToClipboard(sb.toString());
        updateStatus("Copied Key Summary to clipboard");
    }

    void handleOpenValidationAndKcv() {
        if (workspace.currentGeneratedKeySummary == null) {
            updateStatus("No generated key available for validation.");
            return;
        }
        if (AppSettings.isFullLab() && view().keyInputField() != null) {
            view().keyInputField().setText(workspace.currentGeneratedKeySummary.getRawKeyHex());
        }
        if (view().validationPane() != null) {
            view().validationPane().setExpanded(true);
        }
        validateKey.run();
    }

    void handleKcvLengthToggle() {
        if (workspace.currentGeneratedKeySummary != null) {
            updateGeneratedKeySummaryCard(workspace.currentGeneratedKeySummary);
        }
        if (view().validationResultArea() != null && view().validationResultArea().isVisible()
                && view().keyInputField() != null && !view().keyInputField().getText().isBlank()) {
            validateKey.run();
        }
    }

    int selectedKcvLength() {
        return view().useFourByteKcvCheck() == null || view().useFourByteKcvCheck().isSelected() ? 4 : 3;
    }

    void copyToClipboard(String text) {
        if (text == null) return;
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    void handleClear() {
        // Symmetric
        if (view().generatedKeyField() != null)
            view().generatedKeyField().clear();
        if (view().keyInputField() != null)
            view().keyInputField().clear();
        if (view().validationResultArea() != null)
            view().validationResultArea().clear();
        if (view().component1Field() != null)
            view().component1Field().clear();
        if (view().component2Field() != null)
            view().component2Field().clear();
        if (view().component3Field() != null)
            view().component3Field().clear();
        if (view().componentResultsArea() != null)
            view().componentResultsArea().clear();
    }

    void handleClearAsymmetric() {
        // Asymmetric
        workspace.currentRsaSummary = null;
        workspace.currentEcdsaSummary = null;
        workspace.currentDsaSummary = null;
        workspace.currentEddsaSummary = null;

        if (view().rsaSummaryCard() != null) {
            view().rsaSummaryCard().setVisible(false);
            view().rsaSummaryCard().setManaged(false);
        }
        if (view().ecdsaSummaryCard() != null) {
            view().ecdsaSummaryCard().setVisible(false);
            view().ecdsaSummaryCard().setManaged(false);
        }
        if (view().dsaSummaryCard() != null) {
            view().dsaSummaryCard().setVisible(false);
            view().dsaSummaryCard().setManaged(false);
        }
        if (view().eddsaSummaryCard() != null) {
            view().eddsaSummaryCard().setVisible(false);
            view().eddsaSummaryCard().setManaged(false);
        }

        if (view().rsaPublicKeyArea() != null) view().rsaPublicKeyArea().clear();
        if (view().rsaPrivateKeyArea() != null) view().rsaPrivateKeyArea().clear();
        if (view().dsaPublicKeyArea() != null) view().dsaPublicKeyArea().clear();
        if (view().dsaPrivateKeyArea() != null) view().dsaPrivateKeyArea().clear();
        if (view().ecdsaPublicKeyArea() != null) view().ecdsaPublicKeyArea().clear();
        if (view().ecdsaPrivateKeyArea() != null) view().ecdsaPrivateKeyArea().clear();
        if (view().ecdsaFpPublicKeyArea() != null) view().ecdsaFpPublicKeyArea().clear();
        if (view().ecdsaFpPrivateKeyArea() != null) view().ecdsaFpPrivateKeyArea().clear();
        if (view().eddsaPublicKeyArea() != null) view().eddsaPublicKeyArea().clear();
        if (view().eddsaPrivateKeyArea() != null) view().eddsaPrivateKeyArea().clear();
        if (view().ed25519PublicKeyArea() != null) view().ed25519PublicKeyArea().clear();
        if (view().ed25519PrivateKeyArea() != null) view().ed25519PrivateKeyArea().clear();
    }

    void copyPublicKey(GeneratedAsymmetricKeySummary summary) {
        if (summary == null || summary.getPublicKeyPem() == null) {
            updateStatus("No public key available to copy.");
            return;
        }
        copyToClipboard(summary.getPublicKeyPem());
        updateStatus("Copied " + summary.getAlgorithm() + " public key to clipboard");
    }

    void copyPrivateKey(GeneratedAsymmetricKeySummary summary) {
        if (summary == null || summary.getPrivateKeyPem() == null) {
            updateStatus("No private key available to copy.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (!AppSettings.isFullLab()) {
            updateStatus("Action blocked: Private key cannot be copied under " + profile + " profile.");
            showInfo("Security Policy", "Copying private key material is blocked under " + profile + " profile. Switch to FULL_LAB to copy private keys.");
            return;
        }
        copyToClipboard(summary.getPrivateKeyPem());
        updateStatus("Copied " + summary.getAlgorithm() + " private key to clipboard");
    }

    void copyAsymmetricSummary(GeneratedAsymmetricKeySummary summary) {
        if (summary == null) {
            updateStatus("No asymmetric summary available to copy.");
            return;
        }
        String privDisplay = (AppSettings.isFullLab())
                ? summary.getPrivateKeyPem()
                : "***MASKED***";

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(summary.getAlgorithm()).append(" Key Pair Summary ---\n");
        sb.append("Algorithm/Size: ").append(summary.getAlgorithm()).append(" (").append(summary.getCurveOrKeySize()).append(")\n");
        sb.append("Public Fingerprint (SHA-256): ").append(summary.getPublicFingerprintTruncated()).append("\n");
        sb.append("Public Key Length: ").append(summary.getPublicKeyLength()).append("\n");
        sb.append("Private Key Length: ").append(summary.getPrivateKeyLength()).append("\n");
        sb.append("Creation Time: ").append(summary.getCreatedAt()).append("\n");
        sb.append("Compatible Uses: ").append(summary.getCompatibleUses()).append("\n");
        sb.append("Origin: ").append(summary.getOrigin()).append("\n\n");
        sb.append("=== PUBLIC KEY (PEM) ===\n").append(summary.getPublicKeyPem()).append("\n\n");
        sb.append("=== PRIVATE KEY (PEM) ===\n").append(privDisplay);

        copyToClipboard(sb.toString());
        updateStatus("Copied " + summary.getAlgorithm() + " PEM Pair Summary to clipboard");
    }

    void exportPublicPem(GeneratedAsymmetricKeySummary summary, String defaultFilename) {
        if (summary == null || summary.getPublicKeyPem() == null) {
            updateStatus("No public key available to export.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Public Key PEM");
        fc.setInitialFileName(defaultFilename);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files (*.pem, *.pub)", "*.pem", "*.pub"));
        java.io.File file = fc.showSaveDialog(null);
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), summary.getPublicKeyPem(), StandardCharsets.UTF_8);
                updateStatus("Exported public key to " + file.getName());
                summary.setSavedStatus("Exported to " + file.getName());
            } catch (Exception e) {
                showError("Export Error", "Error exporting public key: " + e.getMessage());
            }
        }
    }

    void exportPrivatePem(GeneratedAsymmetricKeySummary summary, String defaultFilename) {
        if (summary == null || summary.getPrivateKeyPem() == null) {
            updateStatus("No private key available to export.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (!AppSettings.isFullLab()) {
            updateStatus("Action blocked: Exporting private key is blocked under " + profile + " profile.");
            showInfo("Security Policy", "Exporting private key files is blocked under " + profile + " profile. Switch to FULL_LAB to export private keys.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Private Key PEM");
        fc.setInitialFileName(defaultFilename);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files (*.pem, *.key)", "*.pem", "*.key"));
        java.io.File file = fc.showSaveDialog(null);
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), summary.getPrivateKeyPem(), StandardCharsets.UTF_8);
                updateStatus("Exported private key to " + file.getName());
                summary.setSavedStatus("Exported to " + file.getName());
            } catch (Exception e) {
                showError("Export Error", "Error exporting private key: " + e.getMessage());
            }
        }
    }

    void sendPublicKeyToShelf(GeneratedAsymmetricKeySummary summary) {
        sendAsymmetricKeyToShelf(summary, AsymmetricShelfMaterial.PUBLIC);
    }

    void sendAsymmetricKeyToShelf(GeneratedAsymmetricKeySummary summary,
                                          AsymmetricShelfMaterial material) {
        if (summary == null) {
            updateStatus("No generated " + (material == AsymmetricShelfMaterial.PRIVATE ? "private" : "public")
                    + " key pair available for Clipboard Shelf.");
            return;
        }

        if (material == AsymmetricShelfMaterial.PRIVATE) {
            if (!AppSettings.isFullLab()) {
                updateStatus("Action blocked: private key material requires FULL_LAB.");
                return;
            }
            String privatePem = summary.getPrivateKeyPem();
            if (privatePem == null || privatePem.isBlank()) {
                updateStatus("No generated private key available for Clipboard Shelf.");
                return;
            }
            com.cryptocarver.model.ClipboardEntry entry =
                    com.cryptocarver.model.ClipboardShelfManager.getInstance()
                            .addSessionOnlyPrivateKey(privatePem, "Key Generation", summary.getAlgorithm());
            if (entry == null) {
                updateStatus("Action blocked: private key material requires FULL_LAB.");
                return;
            }
            revealShelfEntry(entry);
            updateStatus("Added " + summary.getAlgorithm() + " private key to Clipboard Shelf (session only).");
            return;
        }

        String publicPem = summary.getPublicKeyPem();
        if (publicPem == null || publicPem.isBlank()) {
            updateStatus("No generated public key available for Clipboard Shelf.");
            return;
        }
        com.cryptocarver.model.ClipboardEntry entry = new com.cryptocarver.model.ClipboardEntry(
                summary.getAlgorithm() + " Public Key",
                publicPem,
                com.cryptocarver.model.ClipboardEntry.Format.PEM,
                com.cryptocarver.model.OperationDetail.Classification.PUBLIC,
                "Key Generation",
                summary.getAlgorithm()
        );
        com.cryptocarver.model.ClipboardShelfManager.getInstance().addEntry(entry);
        revealShelfEntry(entry);
        updateStatus("Added " + summary.getAlgorithm() + " public key to Clipboard Shelf.");
    }

    void revealShelfEntry(com.cryptocarver.model.ClipboardEntry entry) {
        if (entry != null && reporter() instanceof ModernMainController modern) {
            modern.revealShelfEntry(entry);
        }
    }

    GeneratedAsymmetricKeySummary summaryForGeneration(String operation) {
        if (operation == null) return null;
        return switch (operation) {
            case "RSA Key Generation" -> workspace.currentRsaSummary;
            case "ECDSA Key Generation" -> workspace.currentEcdsaSummary;
            case "DSA Key Generation" -> workspace.currentDsaSummary;
            case "EdDSA Key Generation" -> workspace.currentEddsaSummary;
            default -> null;
        };
    }

    TabPane tabsForGeneration(String operation) {
        if (operation == null) return null;
        return switch (operation) {
            case "RSA Key Generation" -> view().rsaKeyMaterialTabs();
            case "ECDSA Key Generation" -> view().ecdsaKeyMaterialTabs();
            case "DSA Key Generation" -> view().dsaKeyMaterialTabs();
            case "EdDSA Key Generation" -> view().eddsaKeyMaterialTabs();
            default -> null;
        };
    }

    void handleGlobalSymmetricShelfAction() {
        byte[] key = workspace.lastGeneratedSymmetricKeyBytes;
        if (key == null || key.length == 0) {
            updateStatus("No generated symmetric key available for Clipboard Shelf.");
            return;
        }
        if (com.cryptocarver.model.ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(
                com.cryptocarver.model.OperationDetail.Classification.SECRET,
                AppSettings.getInstance().getSecretVisibilityProfile())) {
            updateStatus("Action blocked: output hidden by visibility policy.");
            return;
        }
        String keyHex = DataConverter.bytesToHex(key);
        com.cryptocarver.model.ClipboardShelfManager shelf = com.cryptocarver.model.ClipboardShelfManager.getInstance();
        java.util.Optional<com.cryptocarver.model.ClipboardEntry> duplicate =
                shelf.findDuplicate(keyHex, "Generate Symmetric Key");
        if (duplicate.isPresent()) {
            updateStatus("Item already in Clipboard Shelf: " + duplicate.get().getLabel());
            return;
        }
        com.cryptocarver.model.ClipboardEntry entry = new com.cryptocarver.model.ClipboardEntry(
                "Copied from Generate Symmetric Key", keyHex,
                com.cryptocarver.model.ClipboardEntry.Format.HEX,
                com.cryptocarver.model.OperationDetail.Classification.SECRET,
                "Generate Symmetric Key", workspace.lastGeneratedSymmetricKeyType);
        shelf.addEntry(entry);
        revealShelfEntry(entry);
        updateStatus("Added " + workspace.lastGeneratedSymmetricKeyType + " key to Clipboard Shelf.");
    }

    void handleGlobalAsymmetricShelfAction(String operation) {
        GeneratedAsymmetricKeySummary summary = summaryForGeneration(operation);
        TabPane tabs = tabsForGeneration(operation);
        if (summary == null || tabs == null) {
            String algorithm = operation == null ? "asymmetric" : operation.replace(" Key Generation", "");
            updateStatus("No generated " + algorithm + " key pair available for Clipboard Shelf.");
            return;
        }
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        Object selectedMaterial = selectedTab == null ? null : selectedTab.getUserData();
        if ("PRIVATE".equals(selectedMaterial)) {
            sendAsymmetricKeyToShelf(summary, AsymmetricShelfMaterial.PRIVATE);
        } else if ("PUBLIC".equals(selectedMaterial)) {
            sendAsymmetricKeyToShelf(summary, AsymmetricShelfMaterial.PUBLIC);
        } else {
            updateStatus("Action blocked: select Public Key (PEM) or Private Key (PEM) before adding to Shelf.");
        }
    }

    void useInSignatures(GeneratedAsymmetricKeySummary summary) {
        if (summary == null || summary.getKeyPair() == null) {
            updateStatus("No key pair available for signatures.");
            return;
        }
        updateStatus("Selected " + summary.getAlgorithm() + " key pair for Digital Signatures");
        if (reporter() instanceof ModernMainController modern) {
            modern.useGeneratedKeyPairInSignatures(
                    summary.getKeyPair(), summary.getPublicKeyPem(), summary.getPrivateKeyPem());
        } else if (reporter() != null) {
            reporter().navigateTo("Digital Signatures");
        }
    }

    void useInCertificates(GeneratedAsymmetricKeySummary summary) {
        if (summary == null) {
            updateStatus("No key pair available for certificates.");
            return;
        }
        updateStatus("Selected " + summary.getAlgorithm() + " key pair for Certificates");
        if (reporter() != null) {
            reporter().navigateTo("Generate Certificate");
        }
    }

    void handleCopyRsaPublicKey() { copyPublicKey(workspace.currentRsaSummary); }

    void handleCopyRsaPrivateKey() { copyPrivateKey(workspace.currentRsaSummary); }

    void handleCopyRsaSummary() { copyAsymmetricSummary(workspace.currentRsaSummary); }

    void handleExportRsaPublicPem() { exportPublicPem(workspace.currentRsaSummary, "rsa_public.pem"); }

    void handleExportRsaPrivatePem() { exportPrivatePem(workspace.currentRsaSummary, "rsa_private.pem"); }

    void handleSendRsaPublicToShelf() { sendPublicKeyToShelf(workspace.currentRsaSummary); }

    void handleSendRsaPrivateToShelf() { sendAsymmetricKeyToShelf(workspace.currentRsaSummary, AsymmetricShelfMaterial.PRIVATE); }

    void handleUseRsaInCipher() {
        if (workspace.currentRsaSummary == null) {
            updateStatus("No RSA key pair available for encryption.");
            return;
        }
        updateStatus("Selected RSA key pair for RSA Cipher");
        if (reporter() != null) {
            reporter().navigateTo("Asymmetric Ciphers");
        }
    }

    void handleUseRsaInSignatures() { useInSignatures(workspace.currentRsaSummary); }

    void handleUseRsaInCertificates() { useInCertificates(workspace.currentRsaSummary); }

    void handleClearRsa() {
        workspace.currentRsaSummary = null;
        if (view().rsaSummaryCard() != null) {
            view().rsaSummaryCard().setVisible(false);
            view().rsaSummaryCard().setManaged(false);
        }
        if (view().rsaPublicKeyArea() != null) view().rsaPublicKeyArea().clear();
        if (view().rsaPrivateKeyArea() != null) view().rsaPrivateKeyArea().clear();
        updateStatus("Cleared RSA key pair");
    }

    void handleCopyEcdsaPublicKey() { copyPublicKey(workspace.currentEcdsaSummary); }

    void handleCopyEcdsaPrivateKey() { copyPrivateKey(workspace.currentEcdsaSummary); }

    void handleCopyEcdsaSummary() { copyAsymmetricSummary(workspace.currentEcdsaSummary); }

    void handleExportEcdsaPublicPem() { exportPublicPem(workspace.currentEcdsaSummary, "ecdsa_public.pem"); }

    void handleExportEcdsaPrivatePem() { exportPrivatePem(workspace.currentEcdsaSummary, "ecdsa_private.pem"); }

    void handleSendEcdsaPublicToShelf() { sendPublicKeyToShelf(workspace.currentEcdsaSummary); }

    void handleSendEcdsaPrivateToShelf() { sendAsymmetricKeyToShelf(workspace.currentEcdsaSummary, AsymmetricShelfMaterial.PRIVATE); }

    void handleUseEcdsaInSignatures() { useInSignatures(workspace.currentEcdsaSummary); }

    void handleUseEcdsaInCertificates() { useInCertificates(workspace.currentEcdsaSummary); }

    void handleClearEcdsa() {
        workspace.currentEcdsaSummary = null;
        if (view().ecdsaSummaryCard() != null) {
            view().ecdsaSummaryCard().setVisible(false);
            view().ecdsaSummaryCard().setManaged(false);
        }
        if (view().ecdsaPublicKeyArea() != null) view().ecdsaPublicKeyArea().clear();
        if (view().ecdsaPrivateKeyArea() != null) view().ecdsaPrivateKeyArea().clear();
        if (view().ecdsaFpPublicKeyArea() != null) view().ecdsaFpPublicKeyArea().clear();
        if (view().ecdsaFpPrivateKeyArea() != null) view().ecdsaFpPrivateKeyArea().clear();
        updateStatus("Cleared ECDSA key pair");
    }

    void handleCopyDsaPublicKey() { copyPublicKey(workspace.currentDsaSummary); }

    void handleCopyDsaPrivateKey() { copyPrivateKey(workspace.currentDsaSummary); }

    void handleCopyDsaSummary() { copyAsymmetricSummary(workspace.currentDsaSummary); }

    void handleExportDsaPublicPem() { exportPublicPem(workspace.currentDsaSummary, "dsa_public.pem"); }

    void handleExportDsaPrivatePem() { exportPrivatePem(workspace.currentDsaSummary, "dsa_private.pem"); }

    void handleSendDsaPublicToShelf() { sendPublicKeyToShelf(workspace.currentDsaSummary); }

    void handleSendDsaPrivateToShelf() { sendAsymmetricKeyToShelf(workspace.currentDsaSummary, AsymmetricShelfMaterial.PRIVATE); }

    void handleUseDsaInSignatures() { useInSignatures(workspace.currentDsaSummary); }

    void handleUseDsaInCertificates() { useInCertificates(workspace.currentDsaSummary); }

    void handleClearDsa() {
        workspace.currentDsaSummary = null;
        if (view().dsaSummaryCard() != null) {
            view().dsaSummaryCard().setVisible(false);
            view().dsaSummaryCard().setManaged(false);
        }
        if (view().dsaPublicKeyArea() != null) view().dsaPublicKeyArea().clear();
        if (view().dsaPrivateKeyArea() != null) view().dsaPrivateKeyArea().clear();
        updateStatus("Cleared DSA key pair");
    }

    void handleCopyEddsaPublicKey() { copyPublicKey(workspace.currentEddsaSummary); }

    void handleCopyEddsaPrivateKey() { copyPrivateKey(workspace.currentEddsaSummary); }

    void handleCopyEddsaSummary() { copyAsymmetricSummary(workspace.currentEddsaSummary); }

    void handleExportEddsaPublicPem() { exportPublicPem(workspace.currentEddsaSummary, "ed25519_public.pem"); }

    void handleExportEddsaPrivatePem() { exportPrivatePem(workspace.currentEddsaSummary, "ed25519_private.pem"); }

    void handleSendEddsaPublicToShelf() { sendPublicKeyToShelf(workspace.currentEddsaSummary); }

    void handleSendEddsaPrivateToShelf() { sendAsymmetricKeyToShelf(workspace.currentEddsaSummary, AsymmetricShelfMaterial.PRIVATE); }

    void handleUseEddsaInSignatures() { useInSignatures(workspace.currentEddsaSummary); }

    void handleUseEddsaInCertificates() { useInCertificates(workspace.currentEddsaSummary); }

    void handleClearEd25519() {
        workspace.currentEddsaSummary = null;
        if (view().eddsaSummaryCard() != null) {
            view().eddsaSummaryCard().setVisible(false);
            view().eddsaSummaryCard().setManaged(false);
        }
        if (view().eddsaPublicKeyArea() != null) view().eddsaPublicKeyArea().clear();
        if (view().eddsaPrivateKeyArea() != null) view().eddsaPrivateKeyArea().clear();
        if (view().ed25519PublicKeyArea() != null) view().ed25519PublicKeyArea().clear();
        if (view().ed25519PrivateKeyArea() != null) view().ed25519PrivateKeyArea().clear();
        updateStatus("Cleared Ed25519 key pair");
    }

    String getOutputText() {
        // Check Symmetric Results
        if (view().componentResultsArea() != null && !view().componentResultsArea().getText().isEmpty()) {
            return view().componentResultsArea().getText();
        }
        if (view().validationResultArea() != null && !view().validationResultArea().getText().isEmpty()) {
            return view().validationResultArea().getText();
        }
        if (view().generatedKeyField() != null && !view().generatedKeyField().getText().isEmpty()) {
            return view().generatedKeyField().getText();
        }

        // Check Asymmetric (Public/Private)
        StringBuilder sb = new StringBuilder();
        // RSA
        if (view().rsaPublicKeyArea() != null && !view().rsaPublicKeyArea().getText().isEmpty()) {
            sb.append("RSA Public Key:\n").append(view().rsaPublicKeyArea().getText()).append("\n\n");
        }
        if (view().rsaPrivateKeyArea() != null && !view().rsaPrivateKeyArea().getText().isEmpty()) {
            sb.append("RSA Private Key:\n").append(view().rsaPrivateKeyArea().getText()).append("\n\n");
        }
        // DSA
        if (view().dsaPublicKeyArea() != null && !view().dsaPublicKeyArea().getText().isEmpty()) {
            sb.append("DSA Public Key:\n").append(view().dsaPublicKeyArea().getText()).append("\n\n");
        }

        return sb.toString();
    }

    void initializeSummaryListeners() {
        if (view().keyTypeCombo() != null) {
            view().keyTypeCombo().valueProperty().addListener((obs, oldVal, newVal) -> {
                if (workspace.currentGeneratedKeySummary != null && (newVal == null || !newVal.equalsIgnoreCase(workspace.currentGeneratedKeySummary.getAlgorithm()))) {
                    hideGeneratedKeySummary();
                    if (workspace.lastGeneratedSymmetricKeyBytes != null) {
                        Arrays.fill(workspace.lastGeneratedSymmetricKeyBytes, (byte) 0);
                    }
                    workspace.lastGeneratedSymmetricKeyBytes = null;
                    workspace.lastGeneratedSymmetricKeyType = null;
                    if (view().generatedKeyField() != null) view().generatedKeyField().clear();
                    if (view().saveGeneratedKeyButton() != null) view().saveGeneratedKeyButton().setDisable(true);
                }
            });
        }

        if (view().rsaKeySizeCombo() != null) {
            view().rsaKeySizeCombo().valueProperty().addListener((obs, oldVal, newVal) -> {
                workspace.currentRsaSummary = null;
                if (view().rsaSummaryCard() != null) {
                    view().rsaSummaryCard().setVisible(false);
                    view().rsaSummaryCard().setManaged(false);
                }
            });
        }
        if (view().ecdsaCurveCombo() != null) {
            view().ecdsaCurveCombo().valueProperty().addListener((obs, oldVal, newVal) -> {
                workspace.currentEcdsaSummary = null;
                if (view().ecdsaSummaryCard() != null) {
                    view().ecdsaSummaryCard().setVisible(false);
                    view().ecdsaSummaryCard().setManaged(false);
                }
            });
        }
        if (view().dsaKeySizeCombo() != null) {
            view().dsaKeySizeCombo().valueProperty().addListener((obs, oldVal, newVal) -> {
                workspace.currentDsaSummary = null;
                if (view().dsaSummaryCard() != null) {
                    view().dsaSummaryCard().setVisible(false);
                    view().dsaSummaryCard().setManaged(false);
                }
            });
        }
    }
}
