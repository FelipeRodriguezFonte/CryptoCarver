package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.crypto.hsm.KeyMaterial;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.util.UUID;

/** Key Lab inventory, metadata and visibility actions. */
final class KeyLabCoordinator extends KeysCoordinatorSupport {
    record View(
            TitledPane keyLabPane,
            TextField keyLabSearchField,
            ComboBox<String> keyLabStatusFilterCombo,
            TableView<KeyMaterial> keyLabTable,
            TextField keyLabNewNameField,
            ComboBox<String> keyLabNewAlgoCombo,
            ComboBox<String> keyLabNewSizeCombo,
            PasswordField keyLabImportBytesField,
            Button keyLabImportBtn,
            TextField keyLabDetailIdField,
            TextField keyLabDetailNameField,
            Label keyLabDetailAlgoLabel,
            Label keyLabDetailBitsLabel,
            CheckBox keyLabUsageEncryptCheck,
            CheckBox keyLabUsageDecryptCheck,
            CheckBox keyLabUsageMacCheck,
            CheckBox keyLabUsageWrapCheck,
            CheckBox keyLabUsageUnwrapCheck,
            Label keyLabDetailExportabilityLabel,
            Label keyLabDetailKcvLabel,
            Label keyLabDetailFingerprintLabel,
            Label keyLabDetailOriginLabel,
            Label keyLabDetailCreatedLabel,
            Label keyLabDetailModifiedLabel,
            Label keyLabDetailStatusLabel,
            TextField keyLabDetailValueField,
            Button keyLabRevealBtn,
            Button keyLabArchiveBtn,
            Button keyLabUseCipherBtn,
            Button keyLabUseMacBtn,
            Label summarySavedStatusLabel,
            Button rsaSendPrivateShelfBtn,
            Button ecdsaSendPrivateShelfBtn,
            Button dsaSendPrivateShelfBtn,
            Button eddsaSendPrivateShelfBtn) { }

    private final java.util.function.Supplier<View> view;
    private final KeysWorkspaceState workspace;
    private final Runnable refreshHsm;
    private final java.util.function.IntSupplier kcvLength;

    KeyLabCoordinator(java.util.function.Supplier<View> view, java.util.function.Supplier<StatusReporter> reporter, KeysWorkspaceState workspace, Runnable refreshHsm, java.util.function.IntSupplier kcvLength) {
        super(reporter);
        this.view = view;
        this.workspace = workspace;
        this.refreshHsm = refreshHsm;
        this.kcvLength = kcvLength;
    }

    private View view() {
        return view.get();
    }

    void handleSaveGeneratedKeyToLab() {
        byte[] keyBytes = workspace.lastGeneratedSymmetricKeyBytes;
        String algoName = workspace.lastGeneratedSymmetricKeyType;

        if (keyBytes == null || keyBytes.length == 0) {
            showError("No Key Available", "Generate a key first before saving to Key Lab.");
            return;
        }

        if (algoName == null || algoName.isEmpty()) {
            algoName = "AES-256";
        }

        String fingerprint = com.cryptocarver.crypto.hsm.KeyMaterialFactory.generateFingerprint(keyBytes);
        String kcvHex = "N/A";
        try {
            byte[] kcvBytes = (algoName.contains("DES") || algoName.contains("3DES"))
                    ? KeyOperations.calculateKCV_VISA(keyBytes, kcvLength.getAsInt())
                    : KeyOperations.calculateKCV_AES(keyBytes, kcvLength.getAsInt());
            kcvHex = DataConverter.bytesToHex(kcvBytes);
        } catch (Exception ignored) {}

        // Fingerprint Duplicate Check
        com.cryptocarver.crypto.hsm.KeyMaterial existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(fingerprint);
        if (existing != null) {
            showInfo("Duplicate Key Detected",
                    "A key with an identical fingerprint already exists in Key Lab:\n\n"
                    + "Name: " + existing.getName() + "\n"
                    + "ID: " + existing.getId() + "\n"
                    + "Algorithm: " + existing.getAlgorithm() + "\n"
                    + "KCV: " + existing.getKcv() + "\n\n"
                    + "No duplicate entry was created.");
            refreshKeyLabTable();
            if (view().keyLabTable() != null) {
                view().keyLabTable().getItems().stream()
                        .filter(km -> km.getId().equals(existing.getId()))
                        .findFirst()
                        .ifPresent(km -> {
                            view().keyLabTable().getSelectionModel().select(km);
                            showKeyLabDetails(km);
                        });
            }
            if (reporter() != null) {
                reporter().refreshHsmKeyCombos();
            }
            updateStatus("Key already exists in Key Lab: " + existing.getName() + " (ID: " + existing.getId() + ")");
            return;
        }

        // Show metadata dialog
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Save Generated Key to Key Lab");
        dialog.setHeaderText("Specify key metadata for Simulated HSM / Key Lab");

        javafx.scene.control.ButtonType saveButtonType = new javafx.scene.control.ButtonType("Save to Key Lab", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, javafx.scene.control.ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(15));

        // Metadata summary
        javafx.scene.control.Label summaryLabel = new javafx.scene.control.Label(
                "Algorithm: " + algoName + "  |  Length: " + (keyBytes.length * 8) + " bits  |  KCV: " + kcvHex + "  |  Origin: Generated"
        );
        summaryLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3b82f6;");
        grid.add(summaryLabel, 0, 0, 2, 1);

        javafx.scene.control.Label nameLabel = new javafx.scene.control.Label("Name (1-100 chars):");
        javafx.scene.control.TextField nameField = new javafx.scene.control.TextField("Generated " + algoName + " Key");
        nameField.setPromptText("Enter key name...");
        grid.add(nameLabel, 0, 1);
        grid.add(nameField, 1, 1);

        javafx.scene.control.Label usageLabel = new javafx.scene.control.Label("Key Usages:");
        javafx.scene.layout.VBox usageBox = new javafx.scene.layout.VBox(5);
        javafx.scene.control.CheckBox chkEncrypt = new javafx.scene.control.CheckBox("ENCRYPT");
        chkEncrypt.setSelected(true);
        javafx.scene.control.CheckBox chkDecrypt = new CheckBox("DECRYPT");
        chkDecrypt.setSelected(true);
        javafx.scene.control.CheckBox chkMac = new javafx.scene.control.CheckBox("MAC");
        chkMac.setSelected(true);
        javafx.scene.control.CheckBox chkWrap = new javafx.scene.control.CheckBox("WRAP / UNWRAP (KEY_WRAP)");
        chkWrap.setSelected(true);
        usageBox.getChildren().addAll(chkEncrypt, chkDecrypt, chkMac, chkWrap);
        grid.add(usageLabel, 0, 2);
        grid.add(usageBox, 1, 2);

        javafx.scene.control.Label exportLabel = new javafx.scene.control.Label("Exportability:");
        javafx.scene.control.ComboBox<com.cryptocarver.crypto.hsm.KeyExportability> exportCombo = new javafx.scene.control.ComboBox<>();
        exportCombo.getItems().addAll(com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE, com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE);
        exportCombo.setValue(com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE);
        grid.add(exportLabel, 0, 3);
        grid.add(exportCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Validation
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean valid = newVal != null && !newVal.trim().isEmpty() && newVal.trim().length() <= 100;
            if (saveButton != null) {
                saveButton.setDisable(!valid);
            }
        });

        if (!Boolean.getBoolean("test.mode") && !Boolean.getBoolean("runUiTests") && !System.getProperty("java.awt.headless", "false").equals("true")) {
            java.util.Optional<javafx.scene.control.ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() != saveButtonType) {
                return;
            }
        }

        String keyName = nameField.getText().trim();
        if (keyName.isEmpty() || keyName.length() > 100) {
            keyName = "Generated " + algoName + " Key";
        }

        java.util.Set<com.cryptocarver.crypto.hsm.KeyUsage> usages = new java.util.HashSet<>();
        if (chkEncrypt.isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT);
        if (chkDecrypt.isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT);
        if (chkMac.isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.MAC);
        if (chkWrap.isSelected()) {
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.WRAP);
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.UNWRAP);
        }
        if (usages.isEmpty()) {
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT);
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT);
        }

        com.cryptocarver.crypto.hsm.KeyExportability exportability = exportCombo.getValue();
        if (exportability == null) exportability = com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE;

        javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, algoName);
        String keyId = java.util.UUID.randomUUID().toString();

        com.cryptocarver.crypto.hsm.KeyMaterial km = new com.cryptocarver.crypto.hsm.KeyMaterial(
                keyId,
                fingerprint,
                com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC,
                algoName,
                keyBytes.length * 8,
                com.cryptocarver.crypto.hsm.KeyFormat.RAW,
                usages,
                exportability,
                secretKey,
                null,
                keyName,
                "Generated",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                kcvHex,
                "ACTIVE",
                true
        );

        com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);

        refreshKeyLabTable();
        if (view().keyLabTable() != null) {
            view().keyLabTable().getItems().stream()
                    .filter(item -> item.getId().equals(keyId))
                    .findFirst()
                    .ifPresent(item -> {
                        view().keyLabTable().getSelectionModel().select(item);
                        showKeyLabDetails(item);
                    });
        }

        if (reporter() != null) {
            reporter().refreshHsmKeyCombos();
        }

        if (workspace.currentGeneratedKeySummary != null) {
            String status = "Saved to Key Lab (" + keyName + ")";
            workspace.currentGeneratedKeySummary.setSavedStatus(status);
            if (view().summarySavedStatusLabel() != null) {
                view().summarySavedStatusLabel().setText("✓ " + status);
            }
        }

        updateStatus("Saved generated key to Key Lab: " + keyName + " — " + algoName + " — KCV " + kcvHex);
    }

    void initializeKeyLab() {
        if (view().keyLabStatusFilterCombo() != null) {
            view().keyLabStatusFilterCombo().getItems().setAll("Active Only", "Archived Only", "All Keys");
            view().keyLabStatusFilterCombo().setValue("Active Only");
            view().keyLabStatusFilterCombo().setOnAction(e -> refreshKeyLabTable());
        }

        if (view().keyLabNewAlgoCombo() != null) {
            view().keyLabNewAlgoCombo().getItems().setAll("AES", "3DES", "DES", "ChaCha20");
            view().keyLabNewAlgoCombo().setValue("AES");
            view().keyLabNewAlgoCombo().setOnAction(e -> updateNewKeySizes());
        }

        if (view().keyLabNewSizeCombo() != null) {
            updateNewKeySizes();
        }

        if (view().keyLabSearchField() != null) {
            view().keyLabSearchField().textProperty().addListener((obs, old, val) -> refreshKeyLabTable());
        }

        if (view().keyLabTable() != null) {
            TableColumn<KeyMaterial, String> nameCol = new TableColumn<>("Name");
            nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
            nameCol.setPrefWidth(120);

            TableColumn<KeyMaterial, String> algoCol = new TableColumn<>("Algorithm");
            algoCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getAlgorithm()));
            algoCol.setPrefWidth(80);

            TableColumn<KeyMaterial, String> bitsCol = new TableColumn<>("Bits");
            bitsCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().getSize())));
            bitsCol.setPrefWidth(50);

            TableColumn<KeyMaterial, String> kcvCol = new TableColumn<>("KCV");
            kcvCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getKcv()));
            kcvCol.setPrefWidth(60);

            TableColumn<KeyMaterial, String> originCol = new TableColumn<>("Origin");
            originCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getOrigin()));
            originCol.setPrefWidth(80);

            TableColumn<KeyMaterial, String> statusCol = new TableColumn<>("Status");
            statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()));
            statusCol.setPrefWidth(70);

            view().keyLabTable().getColumns().setAll(nameCol, algoCol, bitsCol, kcvCol, originCol, statusCol);

            view().keyLabTable().setRowFactory(tv -> new TableRow<KeyMaterial>() {
                @Override
                protected void updateItem(KeyMaterial item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setStyle("");
                        getStyleClass().removeAll("key-row-archived", "key-row-metadata-only", "key-row-non-exportable");
                    } else {
                        getStyleClass().removeAll("key-row-archived", "key-row-metadata-only", "key-row-non-exportable");
                        if ("ARCHIVED".equalsIgnoreCase(item.getStatus())) {
                            getStyleClass().add("key-row-archived");
                        } else if (!item.hasKeyMaterial()) {
                            getStyleClass().add("key-row-metadata-only");
                        }
                        if (item.getExportability() == com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE) {
                            getStyleClass().add("key-row-non-exportable");
                        }
                    }
                }
            });

            view().keyLabTable().getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> showKeyLabDetails(val));
        }

        updateVisibilityControls();
        refreshKeyLabTable();
    }

    void updateVisibilityControls() {
        boolean isFullLab = AppSettings.isFullLab();
        if (view().rsaSendPrivateShelfBtn() != null) view().rsaSendPrivateShelfBtn().setDisable(!isFullLab);
        if (view().ecdsaSendPrivateShelfBtn() != null) view().ecdsaSendPrivateShelfBtn().setDisable(!isFullLab);
        if (view().dsaSendPrivateShelfBtn() != null) view().dsaSendPrivateShelfBtn().setDisable(!isFullLab);
        if (view().eddsaSendPrivateShelfBtn() != null) view().eddsaSendPrivateShelfBtn().setDisable(!isFullLab);
        if (view().keyLabImportBytesField() != null) {
            view().keyLabImportBytesField().setDisable(!isFullLab);
            if (!isFullLab) {
                view().keyLabImportBytesField().setText("");
                view().keyLabImportBytesField().setPromptText("Importing secret key material requires FULL_LAB");
            } else {
                view().keyLabImportBytesField().setPromptText("Or paste raw key bytes in hex...");
            }
        }
        if (view().keyLabImportBtn() != null) {
            view().keyLabImportBtn().setDisable(!isFullLab);
        }
    }

    void updateNewKeySizes() {
        if (view().keyLabNewSizeCombo() == null || view().keyLabNewAlgoCombo() == null) return;
        String algo = view().keyLabNewAlgoCombo().getValue();
        if ("AES".equals(algo)) {
            view().keyLabNewSizeCombo().getItems().setAll("128", "192", "256");
            view().keyLabNewSizeCombo().setValue("256");
        } else if ("3DES".equals(algo)) {
            view().keyLabNewSizeCombo().getItems().setAll("128 (2key)", "192 (3key)");
            view().keyLabNewSizeCombo().setValue("192 (3key)");
        } else if ("DES".equals(algo)) {
            view().keyLabNewSizeCombo().getItems().setAll("64");
            view().keyLabNewSizeCombo().setValue("64");
        } else if ("ChaCha20".equals(algo)) {
            view().keyLabNewSizeCombo().getItems().setAll("256");
            view().keyLabNewSizeCombo().setValue("256");
        }
    }

    void refreshKeyLabTable() {
        if (view().keyLabTable() == null) return;

        boolean includeArchived = !"Active Only".equals(view().keyLabStatusFilterCombo().getValue());
        boolean onlyArchived = "Archived Only".equals(view().keyLabStatusFilterCombo().getValue());
        String query = view().keyLabSearchField() != null ? view().keyLabSearchField().getText().toLowerCase(java.util.Locale.ROOT) : "";

        java.util.List<KeyMaterial> filtered = new java.util.ArrayList<>();
        for (String id : com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().listKeyIds(true)) {
            KeyMaterial km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(id);
            if (km == null) continue;

            if (onlyArchived && !"ARCHIVED".equalsIgnoreCase(km.getStatus())) continue;
            if (!includeArchived && "ARCHIVED".equalsIgnoreCase(km.getStatus())) continue;

            if (!query.isEmpty()) {
                boolean matches = km.getName().toLowerCase().contains(query) ||
                                  km.getAlgorithm().toLowerCase().contains(query) ||
                                  km.getId().toLowerCase().contains(query);
                if (!matches) continue;
            }

            filtered.add(km);
        }

        view().keyLabTable().getItems().setAll(filtered);
    }

    void showKeyLabDetails(KeyMaterial km) {
        if (km == null) {
            clearKeyLabDetails();
            return;
        }

        view().keyLabDetailIdField().setText(km.getId());
        view().keyLabDetailNameField().setText(km.getName());
        view().keyLabDetailAlgoLabel().setText(km.getAlgorithm());
        view().keyLabDetailBitsLabel().setText(km.getSize() + " bits");
        setKeyLabUsageControls(km);
        view().keyLabDetailExportabilityLabel().setText(km.getExportability().name());
        view().keyLabDetailKcvLabel().setText(km.getKcv());
        view().keyLabDetailFingerprintLabel().setText(km.getFingerprint());
        view().keyLabDetailOriginLabel().setText(km.getOrigin());

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        view().keyLabDetailCreatedLabel().setText(sdf.format(new java.util.Date(km.getCreated())));
        view().keyLabDetailModifiedLabel().setText(sdf.format(new java.util.Date(km.getModified())));
        view().keyLabDetailStatusLabel().setText(km.getStatus() + (km.hasKeyMaterial() ? "" : " (Metadata-only)"));

        boolean isFullLab = AppSettings.isFullLab();
        boolean isExportable = km.getExportability() == com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE;
        view().keyLabRevealBtn().setDisable(!isFullLab || !isExportable);

        view().keyLabDetailValueField().setText("************************");

        if ("ARCHIVED".equalsIgnoreCase(km.getStatus())) {
            view().keyLabArchiveBtn().setText("Restore");
        } else {
            view().keyLabArchiveBtn().setText("Archive");
        }
        updateKeyLabUseActions(km);
    }

    void updateKeyLabUseActions(KeyMaterial km) {
        boolean usable = km != null
                && km.getType() == com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC
                && km.hasKeyMaterial()
                && !"ARCHIVED".equalsIgnoreCase(km.getStatus());
        boolean canCipher = usable && (km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT)
                || km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT));
        boolean canMac = usable && km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.MAC);

        if (view().keyLabUseCipherBtn() != null) {
            view().keyLabUseCipherBtn().setDisable(!canCipher);
            view().keyLabUseCipherBtn().setTooltip(new Tooltip(keyLabActionReason(km, canCipher, "ENCRYPT or DECRYPT")));
        }
        if (view().keyLabUseMacBtn() != null) {
            view().keyLabUseMacBtn().setDisable(!canMac);
            view().keyLabUseMacBtn().setTooltip(new Tooltip(keyLabActionReason(km, canMac, "MAC")));
        }
    }

    void setKeyLabUsageControls(KeyMaterial km) {
        boolean symmetric = km != null && km.getType() == com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC;
        setUsageControl(view().keyLabUsageEncryptCheck(), symmetric, km, com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT);
        setUsageControl(view().keyLabUsageDecryptCheck(), symmetric, km, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT);
        setUsageControl(view().keyLabUsageMacCheck(), symmetric, km, com.cryptocarver.crypto.hsm.KeyUsage.MAC);
        setUsageControl(view().keyLabUsageWrapCheck(), symmetric, km, com.cryptocarver.crypto.hsm.KeyUsage.WRAP);
        setUsageControl(view().keyLabUsageUnwrapCheck(), symmetric, km, com.cryptocarver.crypto.hsm.KeyUsage.UNWRAP);
    }

    void setUsageControl(CheckBox control, boolean enabled, KeyMaterial km,
            com.cryptocarver.crypto.hsm.KeyUsage usage) {
        if (control == null) return;
        control.setSelected(km != null && km.getUsages().contains(usage));
        control.setDisable(!enabled);
    }

    String keyLabActionReason(KeyMaterial km, boolean allowed, String requiredUsage) {
        if (allowed) return "Load this key by reference without revealing its value";
        if (km == null) return "Select a Key Lab entry first";
        if (km.getType() != com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC) return "This operation requires a symmetric key";
        if ("ARCHIVED".equalsIgnoreCase(km.getStatus())) return "Restore the archived key before using it";
        if (!km.hasKeyMaterial()) return "This entry contains metadata only; re-import or regenerate the key material";
        return "The key is not authorized for " + requiredUsage + " usage";
    }

    void clearKeyLabDetails() {
        view().keyLabDetailIdField().clear();
        view().keyLabDetailNameField().clear();
        view().keyLabDetailAlgoLabel().setText("N/A");
        view().keyLabDetailBitsLabel().setText("N/A");
        setKeyLabUsageControls(null);
        view().keyLabDetailExportabilityLabel().setText("N/A");
        view().keyLabDetailKcvLabel().setText("N/A");
        view().keyLabDetailFingerprintLabel().setText("N/A");
        view().keyLabDetailOriginLabel().setText("N/A");
        view().keyLabDetailCreatedLabel().setText("N/A");
        view().keyLabDetailModifiedLabel().setText("N/A");
        view().keyLabDetailStatusLabel().setText("N/A");
        view().keyLabDetailValueField().clear();
        view().keyLabRevealBtn().setDisable(true);
        view().keyLabArchiveBtn().setText("Archive");
        updateKeyLabUseActions(null);
    }

    void handleUseKeyLabInCipher() {
        useSelectedKeyLabEntry(false);
    }

    void handleUseKeyLabInMac() {
        useSelectedKeyLabEntry(true);
    }

    void useSelectedKeyLabEntry(boolean forMac) {
        KeyMaterial km = view().keyLabTable() == null ? null : view().keyLabTable().getSelectionModel().getSelectedItem();
        if (km == null) {
            showError("Key Lab", "Select a key first");
            return;
        }
        boolean allowed = forMac
                ? km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.MAC)
                : km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT)
                    || km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT);
        if (km.getType() != com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC
                || "ARCHIVED".equalsIgnoreCase(km.getStatus()) || !km.hasKeyMaterial() || !allowed) {
            showError("Key Lab", keyLabActionReason(km, false, forMac ? "MAC" : "ENCRYPT or DECRYPT"));
            return;
        }
        if (!(reporter() instanceof ModernMainController modern)) {
            showError("Key Lab", "Direct operational loading is available in the modern workspace");
            return;
        }
        try {
            if (forMac) {
                modern.useLabKeyInMac(km.getId());
                updateStatus("Loaded Key Lab entry \"" + km.getName() + "\" in MAC by reference");
            } else {
                modern.useLabKeyInSymmetricCipher(km.getId());
                updateStatus("Loaded Key Lab entry \"" + km.getName() + "\" in Symmetric Cipher by reference");
            }
        } catch (RuntimeException e) {
            showError("Key Lab", e.getMessage());
        }
    }

    void handleKeyLabGenerate() {
        try {
            String name = view().keyLabNewNameField().getText().trim();
            if (name.isEmpty()) {
                showError("Validation Error", "Please specify a name for the new key");
                return;
            }
            String algo = view().keyLabNewAlgoCombo().getValue();
            String sizeStr = view().keyLabNewSizeCombo().getValue();
            int size = 256;
            if (sizeStr != null) {
                if (sizeStr.contains("128")) size = 128;
                else if (sizeStr.contains("192")) size = 192;
                else if (sizeStr.contains("64")) size = 64;
            }

            String keyTypeMap = "AES";
            if ("3DES".equals(algo)) keyTypeMap = "3DES";
            else if ("DES".equals(algo)) keyTypeMap = "DES";
            else if ("ChaCha20".equals(algo)) keyTypeMap = "AES-256";

            byte[] keyBytes = com.cryptocarver.crypto.KeyOperations.generateKey(keyTypeMap, true);
            if ("AES".equals(algo) && keyBytes.length != (size / 8)) {
                byte[] temp = new byte[size / 8];
                System.arraycopy(keyBytes, 0, temp, 0, temp.length);
                keyBytes = temp;
            }

            String realAlgo = algo;
            if ("ChaCha20".equals(algo)) realAlgo = "ChaCha20";

            javax.crypto.SecretKey spec = new javax.crypto.spec.SecretKeySpec(keyBytes, realAlgo);
            String id = UUID.randomUUID().toString();
            KeyMaterial km = com.cryptocarver.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                    id, spec, com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE,
                    java.util.Set.of(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT, com.cryptocarver.crypto.hsm.KeyUsage.MAC),
                    kcvLength.getAsInt()
            );
            km.setName(name);
            km.setModified(System.currentTimeMillis());

            var existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(km.getFingerprint());
            if (existing != null) {
                showError("Duplicate Key", "A key with this fingerprint already exists in the Lab: " + existing.getName() + " (" + existing.getId() + ")");
                return;
            }

            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            refreshKeyLabTable();
            view().keyLabTable().getSelectionModel().select(km);
            view().keyLabTable().scrollTo(km);
            view().keyLabTable().requestFocus();
            view().keyLabNewNameField().clear();
            refreshHsm.run();
            if (reporter() != null) {
                reporter().updateStatus("Generated and registered key: " + name);
            }
        } catch (Exception e) {
            showError("Generation Error", "Failed to generate key: " + e.getMessage());
        }
    }

    void handleKeyLabImport() {
        if (!AppSettings.isFullLab()) {
            showError("Security Error", "Importing secret key material requires FULL_LAB visibility profile.");
            return;
        }
        try {
            String name = view().keyLabNewNameField().getText().trim();
            if (name.isEmpty()) {
                showError("Validation Error", "Please specify a name for the imported key");
                return;
            }
            String hex = view().keyLabImportBytesField().getText().trim();
            if (hex.isEmpty()) {
                showError("Validation Error", "Please enter key bytes in hexadecimal format");
                return;
            }
            byte[] bytes = com.cryptocarver.util.DataConverter.hexToBytes(hex);
            String algo = view().keyLabNewAlgoCombo().getValue();

            javax.crypto.SecretKey spec = new javax.crypto.spec.SecretKeySpec(bytes, algo);
            String id = UUID.randomUUID().toString();
            KeyMaterial km = com.cryptocarver.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                    id, spec, com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE,
                    java.util.Set.of(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT, com.cryptocarver.crypto.hsm.KeyUsage.MAC),
                    kcvLength.getAsInt()
            );
            km.setName(name);
            km.setModified(System.currentTimeMillis());

            var existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(km.getFingerprint());
            if (existing != null) {
                showError("Duplicate Key", "A key with this fingerprint already exists in the Lab: " + existing.getName() + " (" + existing.getId() + ")");
                return;
            }

            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            refreshKeyLabTable();
            view().keyLabTable().getSelectionModel().select(km);
            view().keyLabTable().scrollTo(km);
            view().keyLabTable().requestFocus();
            view().keyLabNewNameField().clear();
            view().keyLabImportBytesField().clear();
            refreshHsm.run();
            if (reporter() != null) {
                reporter().updateStatus("Imported key: " + name);
            }
        } catch (Exception e) {
            showError("Import Error", "Failed to import key: " + e.getMessage());
        }
    }

    void handleKeyLabReveal() {
        KeyMaterial km = view().keyLabTable().getSelectionModel().getSelectedItem();
        if (km == null) return;

        if (!AppSettings.isFullLab()) {
            showError("Security Restriction", "Revealing key material is only allowed in FULL_LAB security profile.");
            return;
        }

        try {
            byte[] keyBytes = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab(km.getId());
            if (keyBytes != null) {
                view().keyLabDetailValueField().setText(com.cryptocarver.util.DataConverter.bytesToHex(keyBytes).toUpperCase());
            } else {
                view().keyLabDetailValueField().setText(t("module.keys.noRawKeyMaterial"));
            }
        } catch (Exception e) {
            showError("Security Restriction", e.getMessage());
        }
    }

    void handleKeyLabCopyId() {
        KeyMaterial km = view().keyLabTable().getSelectionModel().getSelectedItem();
        if (km == null) return;
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(km.getId());
        clipboard.setContent(content);
        if (reporter() != null) {
            reporter().updateStatus("Copied key ID to clipboard: " + km.getId());
        }
    }

    void handleKeyLabSaveMetadata() {
        KeyMaterial km = view().keyLabTable().getSelectionModel().getSelectedItem();
        if (km == null) return;

        String newName = view().keyLabDetailNameField().getText().trim();
        if (newName.isEmpty()) {
            showError("Validation Error", "Name cannot be empty");
            return;
        }

        java.util.Set<com.cryptocarver.crypto.hsm.KeyUsage> usages =
                java.util.EnumSet.noneOf(com.cryptocarver.crypto.hsm.KeyUsage.class);
        usages.addAll(km.getUsages());
        if (km.getType() == com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC) {
            usages.removeAll(java.util.EnumSet.of(
                    com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT,
                    com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT,
                    com.cryptocarver.crypto.hsm.KeyUsage.MAC,
                    com.cryptocarver.crypto.hsm.KeyUsage.WRAP,
                    com.cryptocarver.crypto.hsm.KeyUsage.UNWRAP));
            if (view().keyLabUsageEncryptCheck().isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT);
            if (view().keyLabUsageDecryptCheck().isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT);
            if (view().keyLabUsageMacCheck().isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.MAC);
            if (view().keyLabUsageWrapCheck().isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.WRAP);
            if (view().keyLabUsageUnwrapCheck().isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.UNWRAP);
        }
        if (usages.isEmpty()) {
            showError("Validation Error", "Select at least one allowed key usage");
            return;
        }

        com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance()
                .updateKeyMetadata(km.getId(), newName, km.getStatus(), usages);
        refreshKeyLabTable();
        for (KeyMaterial item : view().keyLabTable().getItems()) {
            if (item.getId().equals(km.getId())) {
                view().keyLabTable().getSelectionModel().select(item);
                break;
            }
        }
        refreshHsm.run();
        if (reporter() != null) {
            reporter().showInfo("Success", "Key metadata updated");
        }
    }

    void handleKeyLabArchive() {
        KeyMaterial km = view().keyLabTable().getSelectionModel().getSelectedItem();
        if (km == null) return;

        boolean willArchive = !"ARCHIVED".equalsIgnoreCase(km.getStatus());
        String actionText = willArchive ? "archive" : "restore";
        java.util.Optional<ButtonType> result = LabPrompt.KEY_ARCHIVE.shouldShow()
                ? dialogService.show(Alert.AlertType.CONFIRMATION,
                view().keyLabTable().getScene().getWindow(), "Confirm " + (willArchive ? "Archive" : "Restore"),
                (willArchive ? "Archive" : "Restore") + " Key: " + km.getName(),
                new Label("Are you sure you want to " + actionText + " this key? "
                        + (willArchive ? "Archived keys are hidden from standard operations but kept in history." : "This key will be active again.")),
                ButtonType.CANCEL, ButtonType.OK)
                : java.util.Optional.of(ButtonType.OK);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().archiveKey(km.getId());
            refreshKeyLabTable();
            var reloaded = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(km.getId());
            if (reloaded != null) {
                view().keyLabTable().getSelectionModel().select(reloaded);
            }
            refreshHsm.run();
        }
    }

    void handleKeyLabDelete() {
        KeyMaterial km = view().keyLabTable().getSelectionModel().getSelectedItem();
        if (km == null) return;

        java.util.Optional<ButtonType> result = dialogService.show(Alert.AlertType.CONFIRMATION,
                view().keyLabTable().getScene().getWindow(), "Confirm Deletion", "Delete Key: " + km.getName(),
                new Label("Are you sure you want to permanently delete this key from the Lab? This action cannot be undone."),
                ButtonType.CANCEL, ButtonType.OK);
        if (result.isPresent() && result.get() == ButtonType.OK) {
            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().deleteKey(km.getId());
            refreshKeyLabTable();
            view().keyLabTable().getSelectionModel().clearSelection();
            refreshHsm.run();
            if (reporter() != null) {
                reporter().updateStatus("Deleted key: " + km.getName());
            }
        }
    }

    void handleImportKeyLabMetadata() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Key Lab Metadata Manifest");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = chooser.showOpenDialog(view().keyLabTable().getScene().getWindow());
        if (file != null) {
            try {
                com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importMetadata(file);
                refreshKeyLabTable();
                refreshHsm.run();
                if (reporter() != null) {
                    reporter().showInfo("Success", "Imported key metadata manifest successfully.");
                }
            } catch (Exception e) {
                showError("Import Error", "Failed to import metadata: " + e.getMessage());
            }
        }
    }

    void handleExportKeyLabMetadata() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Key Lab Metadata Manifest (No Secrets)");
        chooser.setInitialFileName("key-lab-metadata.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = chooser.showSaveDialog(view().keyLabTable().getScene().getWindow());
        if (file != null) {
            try {
                com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().exportMetadata(file);
                if (reporter() != null) {
                    reporter().showInfo("Success", "Exported metadata successfully to " + file.getName());
                }
            } catch (Exception e) {
                showError("Export Error", "Failed to export metadata: " + e.getMessage());
            }
        }
    }

    void selectKeyInKeyLab(String keyId) {
        if (view().keyLabPane() != null) {
            view().keyLabPane().setExpanded(true);
        }
        if (view().keyLabStatusFilterCombo() != null) {
            view().keyLabStatusFilterCombo().setValue("All Keys");
        }
        refreshKeyLabTable();
        if (view().keyLabTable() != null) {
            for (KeyMaterial km : view().keyLabTable().getItems()) {
                if (km.getId().equals(keyId)) {
                    view().keyLabTable().getSelectionModel().select(km);
                    view().keyLabTable().scrollTo(km);
                    view().keyLabTable().requestFocus();
                    break;
                }
            }
        }
    }
}
