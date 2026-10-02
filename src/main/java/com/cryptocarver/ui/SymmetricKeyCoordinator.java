package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.GeneratedKeySummary;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.nio.charset.StandardCharsets;

/** Owns the SymmetricKeyCoordinator workbench; views and shell are resolved on demand. */
final class SymmetricKeyCoordinator extends KeysCoordinatorSupport {
    record View(
            ComboBox<String> keyTypeCombo,
            javafx.scene.control.CheckBox forceOddParityCheck,
            TextArea generatedKeyField,
            Button saveGeneratedKeyButton,
            TextField keyInputField,
            TextArea validationResultArea,
            ComboBox<String> numComponentsCombo,
            TextArea keyToSplitField,
            TextArea componentResultsArea,
            TextField component1Field,
            TextField component2Field,
            TextField component3Field,
            TextField component4Field,
            TextField component5Field) { }

    private final java.util.function.Supplier<View> view;
    private final KeysWorkspaceState workspace;
    private final java.util.function.Consumer<GeneratedKeySummary> summaryUpdated;
    private final java.util.function.IntSupplier kcvLength;

    SymmetricKeyCoordinator(java.util.function.Supplier<View> view, java.util.function.Supplier<StatusReporter> reporter, KeysWorkspaceState workspace, java.util.function.Consumer<GeneratedKeySummary> summaryUpdated, java.util.function.IntSupplier kcvLength) {
        super(reporter);
        this.view = view;
        this.workspace = workspace;
        this.summaryUpdated = summaryUpdated;
        this.kcvLength = kcvLength;
    }

    private View view() {
        return view.get();
    }

    void initialize() {


        // Populate combo boxes
        view().keyTypeCombo().getItems().addAll("DES", "3DES-2KEY", "3DES-3KEY", "AES-128", "AES-192", "AES-256");
        view().keyTypeCombo().setValue("3DES-2KEY");

        view().numComponentsCombo().getItems().addAll("2", "3", "4", "5");
        view().numComponentsCombo().setValue("2");
    }

    void handleGenerateKey() {
        try {
            String keyType = view().keyTypeCombo().getValue();
            if (keyType == null) {
                showError("Input Error", "Please select a key type");
                return;
            }

            boolean forceParity = view().forceOddParityCheck().isSelected();
            byte[] key = KeyOperations.generateKey(keyType, forceParity);
            String keyHex = DataConverter.bytesToHex(key);
            view().generatedKeyField().setText(keyHex);

            workspace.lastGeneratedSymmetricKeyBytes = key;
            workspace.lastGeneratedSymmetricKeyType = keyType;
            if (view().saveGeneratedKeyButton() != null) {
                view().saveGeneratedKeyButton().setDisable(false);
            }

            // Create and update GeneratedKeySummary card
            GeneratedKeySummary summary = new GeneratedKeySummary(key, keyType, forceParity);
            workspace.currentGeneratedKeySummary = summary;
            summaryUpdated.accept(summary);

            String parityStatus = forceParity ? " with odd parity" : " without parity adjustment";
            updateStatus("Generated " + keyType + " key" + parityStatus);

            // Delegate to ModernMainController history if available
            if (reporter() != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Type", keyType));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Generated Key", keyHex));
                    try {
                        if (keyType.contains("DES") || keyType.contains("3DES")) {
                            byte[] kcv = KeyOperations.calculateKCV_VISA(key, this.kcvLength.getAsInt());
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("KCV (VISA)", DataConverter.bytesToHex(kcv)));
                        } else {
                            byte[] kcv = KeyOperations.calculateKCV_AES(key, this.kcvLength.getAsInt());
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("KCV (AES)", DataConverter.bytesToHex(kcv)));
                        }
                    } catch (Exception e) {
                        details.add(com.cryptocarver.model.OperationDetail.publicDetail("KCV", "Error calculating"));
                    }

                    reporter().publish(OperationResult.forOperation("Generate Symmetric Key")
                            .output(key)
                            .details(details)
                            .status("Generated " + keyType + " key" + parityStatus)
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Generate Symmetric Key")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", keyType, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Key: " + keyHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating key: " + e.getMessage());
        }
    }

    void handleValidateKey() {
        try {
            String keyHex = view().keyInputField().getText().trim();
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
            int kcvLength = this.kcvLength.getAsInt();
            result.append("KEY CHECK VALUES (KCV)\n");
            result.append("----------------------------------------\n\n");
            result.append("Output Length: ").append(kcvLength).append(" bytes (")
                    .append(kcvLength * 2).append(" hex characters)\n\n");

            try {
                byte[] kcvVisa = KeyOperations.calculateKCV_VISA(key, kcvLength);
                result.append("KCV (VISA):     ").append(DataConverter.bytesToHex(kcvVisa)).append("\n");
            } catch (Exception e) {
                result.append("KCV (VISA):     Error - ").append(e.getMessage()).append("\n");
            }

            try {
                byte[] kcvAtalla = KeyOperations.calculateKCV_ATALLA(key, kcvLength);
                result.append("KCV (ATALLA):   ").append(DataConverter.bytesToHex(kcvAtalla)).append("\n\n");
            } catch (Exception e) {
                result.append("KCV (ATALLA):   Error - ").append(e.getMessage()).append("\n\n");
            }

            result.append("--- Modern Methods ---\n\n");

            try {
                byte[] kcvSha256 = KeyOperations.calculateKCV_SHA256(key, kcvLength);
                result.append("KCV (SHA256):   ").append(DataConverter.bytesToHex(kcvSha256)).append("\n");
            } catch (Exception e) {
                result.append("KCV (SHA256):   Error - ").append(e.getMessage()).append("\n");
            }

            try {
                byte[] kcvCMAC = KeyOperations.calculateKCV_CMAC(key, kcvLength);
                result.append("KCV (CMAC):     ").append(DataConverter.bytesToHex(kcvCMAC)).append("\n");
            } catch (Exception e) {
                result.append("KCV (CMAC):     Error - ").append(e.getMessage()).append("\n");
            }

            if (key.length == 16 || key.length == 24) {
                try {
                    result.append("CKCV (TDEA):    ").append(DataConverter.bytesToHex(KeyOperations.calculateCKCV_TDEA(key))).append("\n");
                } catch (Exception e) {
                    result.append("CKCV (TDEA):    Error - ").append(e.getMessage()).append("\n");
                }
            }
            if (key.length == 16 || key.length == 24 || key.length == 32) {
                try {
                    result.append("CKCV (AES):     ").append(DataConverter.bytesToHex(KeyOperations.calculateCKCV_AES(key))).append("\n");
                } catch (Exception e) {
                    result.append("CKCV (AES):     Error - ").append(e.getMessage()).append("\n");
                }
            }

            // Only calculate AES KCV for AES keys
            if (key.length == 16 || key.length == 24 || key.length == 32) {
                try {
                    byte[] kcvAES = KeyOperations.calculateKCV_AES(key, kcvLength);
                    result.append("KCV (AES):      ").append(DataConverter.bytesToHex(kcvAES)).append("\n");
                } catch (Exception e) {
                    result.append("KCV (AES):      Error - ").append(e.getMessage()).append("\n");
                }
            }

            result.append("\n========================================\n");

            view().validationResultArea().setText(result.toString());
            view().validationResultArea().setVisible(true);
            view().validationResultArea().setManaged(true);
            updateStatus(com.cryptocarver.service.I18nService.getInstance().text("module.keys.validated"));

            // Publish a coherent result so the inspector, history and expanded
            // viewer contain the actual validation report and the input key is
            // consistently classified as sensitive.
            if (reporter() != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key", keyHex));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Validation Report", result.toString()));
                    reporter().publish(OperationResult.forOperation("Validate Symmetric Key")
                            .input(key)
                            .output(result.toString().getBytes(StandardCharsets.UTF_8))
                            .details(details)
                            .status("Key validated successfully")
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                // Add to history (Legacy)
                String keyType = KeyOperations.getKeyType(key);
                if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Validate - " + keyType)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Key: " + keyHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", result.toString(), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (IllegalArgumentException e) {
            showError("Input Error", e.getMessage());
        } catch (Exception e) {
            showError("Validation Error", "Error validating key: " + e.getMessage());
        }
    }

    void handleSplitKey() {
        try {
            String keyHex = view().keyToSplitField().getText().trim();
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

            int numComponents = Integer.parseInt(view().numComponentsCombo().getValue());

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
                        view().component1Field().setText(componentHex);
                        break;
                    case 1:
                        view().component2Field().setText(componentHex);
                        break;
                    case 2:
                        view().component3Field().setText(componentHex);
                        break;
                    case 3:
                        view().component4Field().setText(componentHex);
                        break;
                    case 4:
                        view().component5Field().setText(componentHex);
                        break;
                }
            }

            // Clear unused component fields
            if (numComponents < 3)
                view().component3Field().setText("");
            if (numComponents < 4)
                view().component4Field().setText("");
            if (numComponents < 5)
                view().component5Field().setText("");

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

            view().componentResultsArea().setText(result.toString());
            view().componentResultsArea().setVisible(true);
            view().componentResultsArea().setManaged(true);
            updateStatus("Key split into " + numComponents + " components");

            // Add to history
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Split - " + numComponents + " components")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Input Key: " + keyHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", result.toString(), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (NumberFormatException e) {
            showError("Input Error", "Invalid number of components");
        } catch (Exception e) {
            showError("Splitting Error", "Error splitting key: " + e.getMessage());
        }
    }

    void handleCombineComponents() {
        try {
            String comp1 = view().component1Field().getText().trim();
            String comp2 = view().component2Field().getText().trim();

            if (comp1.isEmpty() || comp2.isEmpty()) {
                showError("Input Error", "Please enter at least 2 components");
                return;
            }

            // Collect all non-empty components
            java.util.List<byte[]> componentList = new java.util.ArrayList<>();
            componentList.add(DataConverter.hexToBytes(comp1));
            componentList.add(DataConverter.hexToBytes(comp2));

            if (!view().component3Field().getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(view().component3Field().getText().trim()));
            }
            if (!view().component4Field().getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(view().component4Field().getText().trim()));
            }
            if (!view().component5Field().getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(view().component5Field().getText().trim()));
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

            view().componentResultsArea().setText(result.toString());
            view().componentResultsArea().setVisible(true);
            view().componentResultsArea().setManaged(true);

            // Add to history
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Combine - " + components.length + " components")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Components: " + components.length, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", combinedKeyHex.substring(0, Math.min(32, combinedKeyHex.length())), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (IllegalArgumentException e) {
            showError("Input Error", e.getMessage());
        } catch (Exception e) {
            showError("Combining Error", "Error combining components: " + e.getMessage());
        }
    }

    void setupHexValidation(TextField field) {
        if (field == null) return;
        field.textProperty().addListener((obs, old, val) -> {
            if (val != null && !val.trim().isEmpty() && !KeysHexValidation.isValidHex(val.trim())) {
                if (!field.getStyleClass().contains("field-error")) {
                    field.getStyleClass().add("field-error");
                }
            } else {
                field.getStyleClass().remove("field-error");
            }
        });
    }

    void setupHexValidation(TextArea field) {
        if (field == null) return;
        field.textProperty().addListener((obs, old, val) -> {
            if (val != null && !val.trim().isEmpty() && !KeysHexValidation.isValidHex(val.trim())) {
                if (!field.getStyleClass().contains("field-error")) {
                    field.getStyleClass().add("field-error");
                }
            } else {
                field.getStyleClass().remove("field-error");
            }
        });
    }


}
