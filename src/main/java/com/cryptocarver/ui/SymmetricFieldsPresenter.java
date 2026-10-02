package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.ui.component.MaterialFieldBadge;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Which symmetric fields the selected algorithm and mode use: IV/nonce, tag and AAD rows, the
 * ECB warning and AEAD note, the mode and padding locks, and the material badges that check
 * key, IV, tag and AAD lengths as the user types. Also fills the IV field with random bytes.
 */
final class SymmetricFieldsPresenter {

    /** The controls it shows, hides and checks, injected into CipherController from cipher.fxml. */
    record View(ComboBox<String> algorithm,
            ComboBox<String> mode,
            ComboBox<String> padding,
            ComboBox<String> keySource,
            ComboBox<String> hsmKey,
            TextField key,
            TextField iv,
            TextField tag,
            TextField aad,
            Label ivLabel,
            HBox ivContainer,
            Label tagLabel,
            Label aadLabel,
            VBox ecbWarning,
            Label aeadNote,
            Label keyBadge,
            Label ivBadge,
            Label tagBadge,
            Label aadBadge) {
    }

    private final ComboBox<String> symmetricAlgorithmCombo;
    private final ComboBox<String> cipherModeCombo;
    private final ComboBox<String> paddingCombo;
    private final ComboBox<String> symKeySourceCombo;
    private final ComboBox<String> symHsmKeyCombo;
    private final TextField symmetricKeyField;
    private final TextField ivField;
    private final TextField gcmTagField;
    private final TextField aadField;
    private final Label ivLabel;
    private final HBox ivContainer;
    private final Label gcmTagLabel;
    private final Label aadLabel;
    private final VBox ecbWarningBox;
    private final Label aeadNoteLabel;
    private final Label symKeyBadgeLabel;
    private final Label ivBadgeLabel;
    private final Label gcmTagBadgeLabel;
    private final Label aadBadgeLabel;
    /** Padding chosen before a mode without padding forced NoPadding. */
    private String paddingBeforeLock;

    SymmetricFieldsPresenter(View view) {
        this.symmetricAlgorithmCombo = view.algorithm();
        this.cipherModeCombo = view.mode();
        this.paddingCombo = view.padding();
        this.symKeySourceCombo = view.keySource();
        this.symHsmKeyCombo = view.hsmKey();
        this.symmetricKeyField = view.key();
        this.ivField = view.iv();
        this.gcmTagField = view.tag();
        this.aadField = view.aad();
        this.ivLabel = view.ivLabel();
        this.ivContainer = view.ivContainer();
        this.gcmTagLabel = view.tagLabel();
        this.aadLabel = view.aadLabel();
        this.ecbWarningBox = view.ecbWarning();
        this.aeadNoteLabel = view.aeadNote();
        this.symKeyBadgeLabel = view.keyBadge();
        this.ivBadgeLabel = view.ivBadge();
        this.gcmTagBadgeLabel = view.tagBadge();
        this.aadBadgeLabel = view.aadBadge();
    }

    private MaterialFieldBadge symKeyBadge;
    private MaterialFieldBadge ivBadge;
    private MaterialFieldBadge tagBadge;
    private MaterialFieldBadge aadBadge;

    /**
     * Generate IV based on current algorithm
     */
    void generateIV() {
        if (ivField == null || symmetricAlgorithmCombo == null || cipherModeCombo == null)
            return;

        String algorithm = symmetricAlgorithmCombo.getValue();
        String mode = cipherModeCombo.getValue();
        int ivLength = recommendedIvLength(algorithm, mode);
        if (ivLength == 0) return;

        byte[] iv = new byte[ivLength];
        new java.security.SecureRandom().nextBytes(iv);
        ivField.setText(DataConverter.bytesToHex(iv));
    }

    void updateModeAndAlgorithmVisibility() {
        if (symmetricAlgorithmCombo == null) return;
        String algo = symmetricAlgorithmCombo.getValue() != null ? symmetricAlgorithmCombo.getValue() : "AES-256";
        String mode = cipherModeCombo != null && cipherModeCombo.getValue() != null ? cipherModeCombo.getValue() : "CBC";

        boolean isStreamCipher = SymmetricCipher.isStreamCipher(algo);
        boolean isGCM = !isStreamCipher && mode.equalsIgnoreCase("GCM");
        boolean isECB = !isStreamCipher && mode.equalsIgnoreCase("ECB");
        boolean isChaChaPoly = algo.equalsIgnoreCase("ChaCha20-Poly1305");
        boolean isXChaChaPoly = algo.equalsIgnoreCase("XChaCha20-Poly1305");
        boolean isAEAD = isGCM || isChaChaPoly || isXChaChaPoly;

        // Mode & Padding combo disabled state for Stream Ciphers
        if (isStreamCipher) {
            if (cipherModeCombo != null) { cipherModeCombo.setDisable(true); cipherModeCombo.setStyle("-fx-opacity: 0.5;"); }
            if (paddingCombo != null) { paddingCombo.setDisable(true); paddingCombo.setStyle("-fx-opacity: 0.5;"); }
        } else {
            if (cipherModeCombo != null) { cipherModeCombo.setDisable(false); cipherModeCombo.setStyle("-fx-opacity: 1.0;"); }
            if (paddingCombo != null) {
                boolean supportsPadding = SymmetricCipher.supportsPadding(mode);
                paddingCombo.setDisable(!supportsPadding);
                paddingCombo.setStyle(supportsPadding ? "-fx-opacity: 1.0;" : "-fx-opacity: 0.5;");
            }
        }

        if (isECB) {
            if (ivLabel != null) { ivLabel.setVisible(false); ivLabel.setManaged(false); }
            if (ivContainer != null) { ivContainer.setVisible(false); ivContainer.setManaged(false); }
            if (ivField != null) { ivField.setVisible(false); ivField.setManaged(false); }
            if (gcmTagLabel != null) { gcmTagLabel.setVisible(false); gcmTagLabel.setManaged(false); }
            if (gcmTagField != null) { gcmTagField.setVisible(false); gcmTagField.setManaged(false); }
            if (aadLabel != null) { aadLabel.setVisible(false); aadLabel.setManaged(false); }
            if (aadField != null) { aadField.setVisible(false); aadField.setManaged(false); }
            if (ecbWarningBox != null) { ecbWarningBox.setVisible(true); ecbWarningBox.setManaged(true); }
            if (aeadNoteLabel != null) { aeadNoteLabel.setVisible(false); aeadNoteLabel.setManaged(false); }
        } else if (isAEAD) {
            if (ivLabel != null) { ivLabel.setVisible(true); ivLabel.setManaged(true); }
            if (ivContainer != null) { ivContainer.setVisible(true); ivContainer.setManaged(true); }
            if (ivField != null) {
                ivField.setVisible(true);
                ivField.setManaged(true);
                ivField.setDisable(false);
                if (isXChaChaPoly) {
                    ivField.setPromptText("Hex Nonce (24 bytes recommended for XChaCha20-Poly1305)");
                } else if (isChaChaPoly) {
                    ivField.setPromptText("Hex Nonce (12 bytes recommended for ChaCha20-Poly1305)");
                } else {
                    ivField.setPromptText("Hex Nonce (12 bytes recommended for GCM)");
                }
            }
            if (gcmTagLabel != null) { gcmTagLabel.setVisible(true); gcmTagLabel.setManaged(true); }
            if (gcmTagField != null) {
                gcmTagField.setVisible(true);
                gcmTagField.setManaged(true);
                gcmTagField.setDisable(false);
                gcmTagField.setPromptText("Hex Tag (16 bytes; required for AEAD Decryption)");
            }
            if (aadLabel != null) { aadLabel.setVisible(true); aadLabel.setManaged(true); }
            if (aadField != null) {
                aadField.setVisible(true);
                aadField.setManaged(true);
                aadField.setDisable(false);
            }
            if (ecbWarningBox != null) { ecbWarningBox.setVisible(false); ecbWarningBox.setManaged(false); }
            if (aeadNoteLabel != null) { aeadNoteLabel.setVisible(true); aeadNoteLabel.setManaged(true); }
        } else {
            // CBC, CTR, CFB, OFB, Salsa20, ChaCha20
            if (ivLabel != null) { ivLabel.setVisible(true); ivLabel.setManaged(true); }
            if (ivContainer != null) { ivContainer.setVisible(true); ivContainer.setManaged(true); }
            if (ivField != null) {
                ivField.setVisible(true);
                ivField.setManaged(true);
                ivField.setDisable(false);
                if (isStreamCipher) {
                    ivField.setPromptText("Hex Nonce (" + recommendedIvLength(algo, mode)
                            + " bytes recommended for " + algo + ")");
                } else {
                    ivField.setPromptText("Hex IV (required for " + mode + " mode)...");
                }
            }
            if (gcmTagLabel != null) { gcmTagLabel.setVisible(false); gcmTagLabel.setManaged(false); }
            if (gcmTagField != null) { gcmTagField.setVisible(false); gcmTagField.setManaged(false); }
            if (aadLabel != null) { aadLabel.setVisible(false); aadLabel.setManaged(false); }
            if (aadField != null) { aadField.setVisible(false); aadField.setManaged(false); }
            if (ecbWarningBox != null) { ecbWarningBox.setVisible(false); ecbWarningBox.setManaged(false); }
            if (aeadNoteLabel != null) { aeadNoteLabel.setVisible(false); aeadNoteLabel.setManaged(false); }
        }

        updateMaterialBadges(algo, mode, isStreamCipher, isAEAD, isXChaChaPoly, isChaChaPoly, isGCM);
    }

    private void initMaterialBadges() {
        if (symKeyBadgeLabel != null && symKeyBadge == null) {
            symKeyBadge = new MaterialFieldBadge("Manual Key");
            symKeyBadge.attach(symmetricKeyField, "Hex");
            mirrorMaterialBadge(symKeyBadge, symKeyBadgeLabel);
        }
        if (ivBadgeLabel != null && ivBadge == null) {
            ivBadge = new MaterialFieldBadge("IV / Nonce");
            ivBadge.attach(ivField, "Hex");
            mirrorMaterialBadge(ivBadge, ivBadgeLabel);
        }
        if (gcmTagBadgeLabel != null && tagBadge == null) {
            tagBadge = new MaterialFieldBadge("AEAD Tag");
            tagBadge.attach(gcmTagField, "Hex");
            mirrorMaterialBadge(tagBadge, gcmTagBadgeLabel);
        }
        if (aadBadgeLabel != null && aadBadge == null) {
            aadBadge = new MaterialFieldBadge("AAD");
            aadBadge.attach(aadField, "Hex / ASCII");
            mirrorMaterialBadge(aadBadge, aadBadgeLabel);
        }
    }

    private static void mirrorMaterialBadge(
            MaterialFieldBadge source,
            Label target) {
        Runnable sync = () -> {
            target.setText(source.getText());
            target.getStyleClass().setAll(source.getStyleClass());
            boolean hasUsefulStatus = source.getCurrentStatus()
                    != MaterialFieldBadge.Status.EMPTY;
            boolean show = hasUsefulStatus && source.isVisible() && source.isManaged();
            target.setVisible(show);
            target.setManaged(show);
        };
        source.textProperty().addListener((obs, oldVal, newVal) -> sync.run());
        source.visibleProperty().addListener((obs, oldVal, newVal) -> sync.run());
        source.managedProperty().addListener((obs, oldVal, newVal) -> sync.run());
        source.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> sync.run());
        sync.run();
    }

    private void updateMaterialBadges(String algo, String mode, boolean isStreamCipher, boolean isAEAD, boolean isXChaChaPoly, boolean isChaChaPoly, boolean isGCM) {
        initMaterialBadges();
        if (symmetricAlgorithmCombo == null) return;

        String algoUpper = algo.toUpperCase();
        int expectedKeyBytes = 32;
        if (algoUpper.contains("AES-192")) expectedKeyBytes = 24;
        else if (algoUpper.contains("AES-128")) expectedKeyBytes = 16;
        else if (algoUpper.contains("3DES") || algoUpper.contains("TRIPLEDES")) expectedKeyBytes = 24;
        else if (algoUpper.contains("DES")) expectedKeyBytes = 8;

        boolean isHsm = symKeySourceCombo != null && ("Simulated HSM".equalsIgnoreCase(symKeySourceCombo.getValue()) || "Lab Cache".equalsIgnoreCase(symKeySourceCombo.getValue()));
        if (isHsm) {
            String selectedKey = symHsmKeyCombo != null ? symHsmKeyCombo.getValue() : null;
            if (selectedKey != null && !selectedKey.isEmpty()) {
                var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(selectedKey);
                boolean available = km != null && km.hasKeyMaterial();
                String keyAlgo = km != null && km.getAlgorithm() != null ? km.getAlgorithm() : algo;
                String kcv = km != null ? km.getKcv() : null;
                if (symKeyBadge != null) symKeyBadge.updateStateKeyReference(selectedKey, keyAlgo, kcv, available);
            } else if (symKeyBadge != null) {
                symKeyBadge.updateStateIncomplete("Select HSM Key from Lab");
            }
        } else if (symKeyBadge != null) {
            if (algoUpper.contains("3DES") || algoUpper.contains("TRIPLEDES")) {
                symKeyBadge.setAcceptedByteLengths(16, 24);
            } else {
                symKeyBadge.setExpectedBytes(expectedKeyBytes);
            }
            symKeyBadge.updateState();
        }

        int expectedNonceBytes = recommendedIvLength(algo, mode);
        if (ivBadge != null) {
            if (expectedNonceBytes > 0) ivBadge.setExpectedBytes(expectedNonceBytes);
            ivBadge.updateState();
        }
        if (tagBadge != null) {
            tagBadge.setExpectedBytes(16);
            tagBadge.updateState();
        }
        if (aadBadge != null) {
            aadBadge.updateState();
        }

        setBadgeVisibility(symKeyBadgeLabel, symKeyBadge != null
                && symKeyBadge.getCurrentStatus() != MaterialFieldBadge.Status.EMPTY);
        setBadgeVisibility(ivBadgeLabel, expectedNonceBytes > 0 && ivBadge != null
                && ivBadge.getCurrentStatus() != MaterialFieldBadge.Status.EMPTY);
        setBadgeVisibility(gcmTagBadgeLabel, isAEAD && tagBadge != null
                && tagBadge.getCurrentStatus() != MaterialFieldBadge.Status.EMPTY);
        setBadgeVisibility(aadBadgeLabel, isAEAD && aadBadge != null
                && aadBadge.getCurrentStatus() != MaterialFieldBadge.Status.EMPTY);
    }

    private static void setBadgeVisibility(Label badgeLabel, boolean visible) {
        if (badgeLabel != null) {
            badgeLabel.setVisible(visible);
            badgeLabel.setManaged(visible);
        }
    }

    /**
     * Update padding field state based on selected mode
     */
    void updatePaddingFieldState() {
        if (paddingCombo != null && cipherModeCombo != null) {
            String mode = cipherModeCombo.getValue();
            boolean supportsPadding = SymmetricCipher.supportsPadding(mode);

            if (supportsPadding) {
                paddingCombo.setDisable(false);
                paddingCombo.setStyle("-fx-opacity: 1.0;");
                // Give back the padding a GCM/CTR-style mode replaced, unless the user chose another.
                if (paddingBeforeLock != null && "NoPadding".equals(paddingCombo.getValue())) {
                    paddingCombo.setValue(paddingBeforeLock);
                }
                paddingBeforeLock = null;
            } else {
                paddingCombo.setDisable(true);
                paddingCombo.setStyle("-fx-opacity: 0.5;");
                if (paddingCombo.getValue() != null && !"NoPadding".equals(paddingCombo.getValue())) {
                    paddingBeforeLock = paddingCombo.getValue();
                }
                paddingCombo.setValue("NoPadding");
            }
        }
    }

    /** Stream ciphers ignore the (disabled) mode selector, so a leftover ECB must not zero their nonce. */
    private static int recommendedIvLength(String algorithm, String mode) {
        boolean stream = algorithm != null && SymmetricCipher.isStreamCipher(algorithm);
        return SymmetricCipher.getRecommendedIvLength(algorithm, stream ? null : mode);
    }
}
