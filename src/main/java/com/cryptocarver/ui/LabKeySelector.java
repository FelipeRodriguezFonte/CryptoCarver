package com.cryptocarver.ui;

import com.cryptocarver.util.DataConverter;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.util.function.Supplier;

/**
 * Where the symmetric key comes from: the manual hex field or a Key Lab entry. Lists the lab
 * keys allowed to encrypt or decrypt (by name, algorithm and KCV, never by value), selects one
 * on request from Key Lab, opens it for inspection and saves a typed key into the lab.
 */
final class LabKeySelector {

    /** The key source controls, injected into CipherController from cipher.fxml. */
    record View(ComboBox<String> keySource,
            ComboBox<String> hsmKey,
            TextField key,
            ComboBox<String> algorithm,
            Button inspect,
            Button saveToLab) {
    }

    private final ComboBox<String> symKeySourceCombo;
    private final ComboBox<String> symHsmKeyCombo;
    private final TextField symmetricKeyField;
    private final ComboBox<String> symmetricAlgorithmCombo;
    private final Button inspectKeyBtn;
    private final Button saveToLabBtn;
    private final Supplier<StatusReporter> reporter;
    /** Re-evaluates field states and badges after the key source or selected key changes. */
    private final Runnable refreshFields;

    LabKeySelector(View view, Supplier<StatusReporter> reporter, Runnable refreshFields) {
        this.symKeySourceCombo = view.keySource();
        this.symHsmKeyCombo = view.hsmKey();
        this.symmetricKeyField = view.key();
        this.symmetricAlgorithmCombo = view.algorithm();
        this.inspectKeyBtn = view.inspect();
        this.saveToLabBtn = view.saveToLab();
        this.reporter = reporter;
        this.refreshFields = refreshFields;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    /** Shows lab keys by name, algorithm and KCV, flags metadata-only entries and loads the list. */
    void installHsmKeyCombo() {
        ComboBox<String> combo = symHsmKeyCombo;
        configureHsmKeyCombo(combo);
        if (combo != null) {
            combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(newVal);
                    if (km != null && !km.hasKeyMaterial()) {
                        if (!combo.getStyleClass().contains("field-error")) {
                            combo.getStyleClass().add("field-error");
                        }
                    } else {
                        combo.getStyleClass().remove("field-error");
                    }
                } else {
                    combo.getStyleClass().remove("field-error");
                }
                refreshFields.run();
            });
        }
        refreshHsmKeys();
    }

    private void configureHsmKeyCombo(ComboBox<String> combo) {
        if (combo == null) return;
        combo.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(item);
                    if (km != null) {
                        String kcvShort = km.getKcv() != null && km.getKcv().length() >= 6 ? km.getKcv().substring(0, 6) : km.getKcv();
                        String prefix = km.hasKeyMaterial() ? "" : "[Metadata-only] ";
                        setText(prefix + km.getName() + " — " + km.getAlgorithm() + " — KCV " + kcvShort);
                    } else {
                        setText(item);
                    }
                }
            }
        });
        combo.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String item) {
                if (item == null) return "";
                var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(item);
                if (km != null) {
                    String kcvShort = km.getKcv() != null && km.getKcv().length() >= 6 ? km.getKcv().substring(0, 6) : km.getKcv();
                    String prefix = km.hasKeyMaterial() ? "" : "[Metadata-only] ";
                    return prefix + km.getName() + " — " + km.getAlgorithm() + " — KCV " + kcvShort;
                }
                return item;
            }
            @Override
            public String fromString(String string) {
                return string;
            }
        });
    }

    void updateKeySourceVisibility() {
        boolean isHsm = "Simulated HSM".equals(symKeySourceCombo.getValue());
        if (symmetricKeyField != null) {
            symmetricKeyField.setVisible(!isHsm);
            symmetricKeyField.setManaged(!isHsm);
        }
        if (symHsmKeyCombo != null) {
            symHsmKeyCombo.setVisible(isHsm);
            symHsmKeyCombo.setManaged(isHsm);
        }
        if (inspectKeyBtn != null) {
            inspectKeyBtn.setVisible(isHsm);
            inspectKeyBtn.setManaged(isHsm);
        }
        if (saveToLabBtn != null) {
            saveToLabBtn.setVisible(!isHsm);
            saveToLabBtn.setManaged(!isHsm);
        }
    }

    void handleInspectKey() {
        if (symHsmKeyCombo == null) return;
        String keyId = symHsmKeyCombo.getValue();
        if (keyId == null || keyId.isEmpty()) {
            reporter().showError("Inspect Error", "No HSM key is currently selected to inspect.");
            return;
        }
        if (reporter() instanceof ModernMainController) {
            ModernMainController mmc = (ModernMainController) reporter();
            mmc.navigateTo("Key Lab");
            if (mmc.getKeysController() != null) {
                mmc.getKeysController().selectKeyInKeyLab(keyId);
            }
        }
    }

    void refreshHsmKeys() {
        if (symHsmKeyCombo != null) {
            String current = symHsmKeyCombo.getValue();
            symHsmKeyCombo.getItems().clear();
            symHsmKeyCombo.getItems().addAll(com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().listKeyIds(
                    com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT));
            if (current != null && symHsmKeyCombo.getItems().contains(current)) {
                symHsmKeyCombo.setValue(current);
            } else if (!symHsmKeyCombo.getItems().isEmpty()) {
                symHsmKeyCombo.setValue(symHsmKeyCombo.getItems().get(0));
            }
        }
    }

    /** Selects a usable Key Lab key for symmetric operations without revealing its bytes. */
    void selectLabKey(String keyId) {
        var provider = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance();
        var km = provider.getKeyMetadata(keyId);
        if (km == null) throw new IllegalArgumentException("Key Lab entry was not found: " + keyId);
        if (km.getType() != com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC) {
            throw new IllegalArgumentException("Symmetric Cipher requires a symmetric Key Lab entry");
        }
        if ("ARCHIVED".equalsIgnoreCase(km.getStatus())) {
            throw new IllegalArgumentException("Restore the archived Key Lab entry before using it");
        }
        if (!km.hasKeyMaterial()) {
            throw new IllegalArgumentException("The selected Key Lab entry contains metadata only; re-import or regenerate its key material");
        }
        if (!km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT)
                && !km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT)) {
            throw new IllegalArgumentException("The selected Key Lab entry is not authorized for encryption or decryption");
        }
        symKeySourceCombo.setValue("Simulated HSM");
        refreshHsmKeys();
        if (!symHsmKeyCombo.getItems().contains(keyId)) {
            throw new IllegalArgumentException("The selected key is not available to the Symmetric Cipher workspace");
        }
        symHsmKeyCombo.setValue(keyId);
        selectAlgorithmForLabKey(km);
        updateKeySourceVisibility();
        refreshFields.run();
    }

    private void selectAlgorithmForLabKey(com.cryptocarver.crypto.hsm.KeyMaterial km) {
        if (symmetricAlgorithmCombo == null || km.getAlgorithm() == null) return;
        String stored = km.getAlgorithm().toUpperCase(java.util.Locale.ROOT);
        String target = null;
        if (stored.equals("AES") || stored.startsWith("AES-")) {
            target = "AES-" + km.getSize();
        } else if (stored.equals("3DES") || stored.equals("DESEDE") || stored.contains("TRIPLE DES")) {
            target = "3DES (Triple DES)";
        } else if (stored.equals("DES")) {
            target = "DES";
        } else if (stored.contains("XCHACHA20")) {
            target = "XChaCha20-Poly1305";
        } else if (stored.contains("CHACHA20")) {
            target = "ChaCha20";
        }
        if (target != null && symmetricAlgorithmCombo.getItems().contains(target)) {
            symmetricAlgorithmCombo.setValue(target);
        }
    }

    void saveCurrentKeyToHsm() {
        try {
            if (symmetricKeyField == null || symmetricKeyField.getText().trim().isEmpty()) {
                reporter().showError("Save Error", "No key to save");
                return;
            }
            byte[] keyBytes = DataConverter.hexToBytes(symmetricKeyField.getText().trim());
            String algo = symmetricAlgorithmCombo.getValue();
            if (algo == null) algo = "AES";

            // Prompt user for key name
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("Key-" + algo + "-" + (keyBytes.length * 8));
            dialog.setTitle("Save Key to Lab");
            dialog.setHeaderText("Specify a name for this key in Key Lab:");
            dialog.setContentText("Name:");
            java.util.Optional<String> nameResult = dialog.showAndWait();
            if (!nameResult.isPresent()) {
                return; // User cancelled
            }
            String name = nameResult.get().trim();
            if (name.isEmpty()) name = "Unnamed Key";

            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, algo);
            String id = java.util.UUID.randomUUID().toString();
            com.cryptocarver.crypto.hsm.KeyMaterial km = com.cryptocarver.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                id, secretKey,
                com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE,
                java.util.Set.of(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT, com.cryptocarver.crypto.hsm.KeyUsage.MAC)
            );
            km.setName(name);

            // Check duplicate by fingerprint
            var existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(km.getFingerprint());
            if (existing != null) {
                reporter().showError("Save Error", "A key with this fingerprint already exists in the Lab: " + existing.getName() + " (" + existing.getId() + ")");
                return;
            }

            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            refreshHsmKeys();

            if (symHsmKeyCombo != null) {
                symHsmKeyCombo.setValue(km.getId());
            }
            symKeySourceCombo.setValue("Simulated HSM");

            String kcvShort = km.getKcv() != null && km.getKcv().length() >= 6 ? km.getKcv().substring(0, 6) : km.getKcv();
            reporter().showInfo("Success", "Key \"" + name + "\" saved to Lab. KCV: " + kcvShort);
        } catch (Exception e) {
            reporter().showError("Save Error", "Failed to save key: " + e.getMessage());
        }
    }
}
