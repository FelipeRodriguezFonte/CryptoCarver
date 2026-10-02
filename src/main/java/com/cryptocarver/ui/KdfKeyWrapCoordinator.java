package com.cryptocarver.ui;

import com.cryptocarver.util.DataConverter;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

final class KdfKeyWrapCoordinator extends KeysCoordinatorSupport {
    record View(
            ComboBox<String> kdfAlgorithmCombo,
            ComboBox<String> kdfInputFormatCombo,
            ComboBox<String> kdfSaltFormatCombo,
            ComboBox<String> kdfInfoFormatCombo,
            TextField kdfInputField,
            TextField kdfSaltField,
            TextField kdfInfoField,
            TextField kdfIterationsField,
            TextField kdfOutputLengthField,
            TextArea kdfResultArea,
            Label kdfInputHelpLabel,
            Label kdfValidationLabel,
            Label kdfIterationsLabel,
            VBox kdfSaltBox,
            VBox kdfInfoBox,
            Label kdfInputBadgeLabel,
            Label kdfSaltBadgeLabel,
            Label kdfInfoBadgeLabel,
            ComboBox<String> keyWrapModeCombo,
            CheckBox keyWrapUnwrapCheck,
            TextField keyWrapKekField,
            TextField keyWrapDataField,
            TextArea keyWrapResultArea) { }
    private final Supplier<View> views;
    private com.cryptocarver.ui.component.MaterialFieldBadge kdfInputBadge;
    private com.cryptocarver.ui.component.MaterialFieldBadge kdfSaltBadge;
    private com.cryptocarver.ui.component.MaterialFieldBadge kdfInfoBadge;

    KdfKeyWrapCoordinator(Supplier<View> views, Supplier<StatusReporter> reporter,
            BiConsumer<String, String> errors, Consumer<String> status, BiFunction<String, Object[], String> text) {
        super(reporter, errors, status, text);
        this.views = views;
    }
    private View view() { return views.get(); }

    private javafx.scene.Node validationField(String field) {
        return switch (field) {
            case "kdfInputField" -> view().kdfInputField();
            case "kdfSaltField" -> view().kdfSaltField();
            case "kdfInfoField" -> view().kdfInfoField();
            case "kdfIterationsField" -> view().kdfIterationsField();
            case "kdfOutputLengthField" -> view().kdfOutputLengthField();
            case "kdfAlgorithmCombo" -> view().kdfAlgorithmCombo();
            default -> null;
        };
    }

    void initializeKDF() {

        // Populate algorithms (with SHA variants)
        view().kdfAlgorithmCombo().getItems().addAll(
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
        view().kdfAlgorithmCombo().setValue("HKDF-SHA256");

        // Populate format combos
        String[] formats = { "UTF-8", "Hex", "Base64" };
        view().kdfInputFormatCombo().getItems().addAll(formats);
        view().kdfSaltFormatCombo().getItems().addAll(formats);
        view().kdfInfoFormatCombo().getItems().addAll(formats);

        view().kdfInputFormatCombo().setValue("UTF-8");
        view().kdfSaltFormatCombo().setValue("Hex");
        view().kdfInfoFormatCombo().setValue("UTF-8");

        if (view().kdfInputBadgeLabel() != null && kdfInputBadge == null) {
            kdfInputBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Input Key Material");
            kdfInputBadge.attach(view().kdfInputField(), view().kdfInputFormatCombo());
            kdfInputBadge.textProperty().addListener((obs, oldVal, newVal) -> view().kdfInputBadgeLabel().setText(newVal));
            kdfInputBadge.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                view().kdfInputBadgeLabel().getStyleClass().setAll(kdfInputBadge.getStyleClass());
            });
        }
        if (view().kdfSaltBadgeLabel() != null && kdfSaltBadge == null) {
            kdfSaltBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Salt");
            kdfSaltBadge.attach(view().kdfSaltField(), view().kdfSaltFormatCombo());
            kdfSaltBadge.textProperty().addListener((obs, oldVal, newVal) -> view().kdfSaltBadgeLabel().setText(newVal));
            kdfSaltBadge.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                view().kdfSaltBadgeLabel().getStyleClass().setAll(kdfSaltBadge.getStyleClass());
            });
        }
        if (view().kdfInfoBadgeLabel() != null && kdfInfoBadge == null) {
            kdfInfoBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Info");
            kdfInfoBadge.attach(view().kdfInfoField(), view().kdfInfoFormatCombo());
            kdfInfoBadge.textProperty().addListener((obs, oldVal, newVal) -> view().kdfInfoBadgeLabel().setText(newVal));
            kdfInfoBadge.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                view().kdfInfoBadgeLabel().getStyleClass().setAll(kdfInfoBadge.getStyleClass());
            });
        }

        // Add listener to update parameters based on algorithm
        view().kdfAlgorithmCombo().valueProperty().addListener((obs, oldVal, newVal) -> {
            updateKDFParameters(newVal);
        });
        view().kdfInputFormatCombo().valueProperty().addListener((obs, oldVal, newVal) -> updateKdfFormatHints());
        view().kdfSaltFormatCombo().valueProperty().addListener((obs, oldVal, newVal) -> updateKdfFormatHints());
        view().kdfInfoFormatCombo().valueProperty().addListener((obs, oldVal, newVal) -> updateKdfFormatHints());
        view().kdfInputField().textProperty().addListener((obs, oldVal, newVal) -> validateKdfEncodedField(view().kdfInputField(), view().kdfInputFormatCombo()));
        view().kdfSaltField().textProperty().addListener((obs, oldVal, newVal) -> validateKdfEncodedField(view().kdfSaltField(), view().kdfSaltFormatCombo()));
        view().kdfInfoField().textProperty().addListener((obs, oldVal, newVal) -> validateKdfEncodedField(view().kdfInfoField(), view().kdfInfoFormatCombo()));

        updateKDFParameters("HKDF-SHA256");
        updateKdfFormatHints();
    }

    private void updateKdfFormatHints() {
        updateKdfEncodedFieldHint(view().kdfInputField(), view().kdfInputFormatCombo(), "Input key material");
        updateKdfEncodedFieldHint(view().kdfSaltField(), view().kdfSaltFormatCombo(), "Salt");
        updateKdfEncodedFieldHint(view().kdfInfoField(), view().kdfInfoFormatCombo(), "Info / application context");
        if (view().kdfInputHelpLabel() != null) {
            String format = view().kdfInputFormatCombo() == null || view().kdfInputFormatCombo().getValue() == null
                    ? "UTF-8" : view().kdfInputFormatCombo().getValue();
            view().kdfInputHelpLabel().setText("Input key material — interpreted as " + format + " using Input key material format above.");
        }
    }

    private void updateKdfEncodedFieldHint(TextField field, ComboBox<String> formatCombo, String label) {
        if (field == null) return;
        String format = formatCombo == null || formatCombo.getValue() == null ? "UTF-8" : formatCombo.getValue();
        field.setPromptText(label + " (" + format + ")...");
        field.setAccessibleText(label + "; encoding: " + format);
        validateKdfEncodedField(field, formatCombo);
    }

    private void validateKdfEncodedField(TextField field, ComboBox<String> formatCombo) {
        if (field == null) return;
        String value = field.getText() == null ? "" : field.getText().trim();
        String format = formatCombo == null ? null : formatCombo.getValue();
        boolean invalid = !value.isEmpty() && (format == null || KdfKeyWrapLogic.parseData(value, format) == null);
        if (invalid) {
            if (!field.getStyleClass().contains("field-error")) field.getStyleClass().add("field-error");
        } else {
            field.getStyleClass().remove("field-error");
        }
        if (kdfInputBadge != null) kdfInputBadge.updateState();
        if (kdfSaltBadge != null) kdfSaltBadge.updateState();
        if (kdfInfoBadge != null) kdfInfoBadge.updateState();
    }

    public void handleGenerateKdfSalt() {
        if (view().kdfSaltField() == null || view().kdfSaltFormatCombo() == null) return;
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        view().kdfSaltFormatCombo().setValue("Hex");
        view().kdfSaltField().setText(DataConverter.bytesToHex(salt));
        clearKdfValidation();
        if (kdfSaltBadge != null) kdfSaltBadge.updateState();
        updateStatus("Generated a fresh 16-byte salt for key derivation");
    }

    private void updateKDFParameters(String algorithm) {
        if (algorithm == null) return;

        boolean requiresSalt = algorithm.startsWith("PBKDF2") || algorithm.equals("SCrypt") || algorithm.equals("Argon2id");

        if (algorithm.startsWith("HKDF")) {
            if (view().kdfIterationsLabel() != null) {
                view().kdfIterationsLabel().setVisible(false);
                view().kdfIterationsLabel().setManaged(false);
            }
            if (view().kdfIterationsField() != null) {
                view().kdfIterationsField().setText("1");
                view().kdfIterationsField().setVisible(false);
                view().kdfIterationsField().setManaged(false);
            }
            if (view().kdfSaltBox() != null) {
                view().kdfSaltBox().setVisible(true);
                view().kdfSaltBox().setManaged(true);
            }
            if (view().kdfSaltField() != null) {
                view().kdfSaltField().setDisable(false);
                view().kdfSaltField().setPromptText("Optional salt (zeros if omitted)");
            }
            if (view().kdfInfoBox() != null) {
                view().kdfInfoBox().setVisible(true);
                view().kdfInfoBox().setManaged(true);
            }
            if (view().kdfInfoField() != null) {
                view().kdfInfoField().setDisable(false);
                view().kdfInfoField().setPromptText("Optional application context");
            }
        } else if (algorithm.startsWith("NIST-800-108")) {
            if (view().kdfIterationsLabel() != null) {
                view().kdfIterationsLabel().setVisible(false);
                view().kdfIterationsLabel().setManaged(false);
            }
            if (view().kdfIterationsField() != null) {
                view().kdfIterationsField().setText("1");
                view().kdfIterationsField().setVisible(false);
                view().kdfIterationsField().setManaged(false);
            }
            if (view().kdfSaltBox() != null) {
                view().kdfSaltBox().setVisible(true);
                view().kdfSaltBox().setManaged(true);
            }
            if (view().kdfSaltField() != null) {
                view().kdfSaltField().setDisable(false);
                view().kdfSaltField().setPromptText("Label (optional)");
            }
            if (view().kdfInfoBox() != null) {
                view().kdfInfoBox().setVisible(true);
                view().kdfInfoBox().setManaged(true);
            }
            if (view().kdfInfoField() != null) {
                view().kdfInfoField().setDisable(false);
                view().kdfInfoField().setPromptText("Context (optional)");
            }
        } else if (algorithm.startsWith("X9.63")) {
            if (view().kdfIterationsLabel() != null) {
                view().kdfIterationsLabel().setVisible(false);
                view().kdfIterationsLabel().setManaged(false);
            }
            if (view().kdfIterationsField() != null) {
                view().kdfIterationsField().setText("1");
                view().kdfIterationsField().setVisible(false);
                view().kdfIterationsField().setManaged(false);
            }
            if (view().kdfSaltBox() != null) {
                view().kdfSaltBox().setVisible(false);
                view().kdfSaltBox().setManaged(false);
            }
            if (view().kdfInfoBox() != null) {
                view().kdfInfoBox().setVisible(true);
                view().kdfInfoBox().setManaged(true);
            }
            if (view().kdfInfoField() != null) {
                view().kdfInfoField().setDisable(false);
                view().kdfInfoField().setPromptText("Shared info (optional)");
            }
        } else if (algorithm.startsWith("PBKDF2")) {
            if (view().kdfIterationsLabel() != null) {
                view().kdfIterationsLabel().setVisible(true);
                view().kdfIterationsLabel().setManaged(true);
            }
            if (view().kdfIterationsField() != null) {
                view().kdfIterationsField().setVisible(true);
                view().kdfIterationsField().setManaged(true);
                view().kdfIterationsField().setDisable(false);
                if (view().kdfIterationsField().getText().equals("1")) view().kdfIterationsField().setText("600000");
            }
            if (view().kdfInfoBox() != null) {
                view().kdfInfoBox().setVisible(false);
                view().kdfInfoBox().setManaged(false);
            }
            if (view().kdfSaltBox() != null) {
                view().kdfSaltBox().setVisible(true);
                view().kdfSaltBox().setManaged(true);
            }
            if (view().kdfSaltField() != null) {
                view().kdfSaltField().setDisable(false);
                view().kdfSaltField().setPromptText("Required salt");
            }
        } else if (algorithm.equals("SCrypt")) {
            if (view().kdfIterationsLabel() != null) {
                view().kdfIterationsLabel().setVisible(true);
                view().kdfIterationsLabel().setManaged(true);
            }
            if (view().kdfIterationsField() != null) {
                view().kdfIterationsField().setVisible(true);
                view().kdfIterationsField().setManaged(true);
                view().kdfIterationsField().setDisable(false);
                if (view().kdfIterationsField().getText().equals("1")) view().kdfIterationsField().setText("32768");
            }
            if (view().kdfInfoBox() != null) {
                view().kdfInfoBox().setVisible(false);
                view().kdfInfoBox().setManaged(false);
            }
            if (view().kdfSaltBox() != null) {
                view().kdfSaltBox().setVisible(true);
                view().kdfSaltBox().setManaged(true);
            }
            if (view().kdfSaltField() != null) {
                view().kdfSaltField().setDisable(false);
                view().kdfSaltField().setPromptText("Required salt");
            }
        } else if (algorithm.equals("Argon2id")) {
            if (view().kdfIterationsLabel() != null) {
                view().kdfIterationsLabel().setVisible(true);
                view().kdfIterationsLabel().setManaged(true);
            }
            if (view().kdfIterationsField() != null) {
                view().kdfIterationsField().setVisible(true);
                view().kdfIterationsField().setManaged(true);
                view().kdfIterationsField().setDisable(false);
                if (view().kdfIterationsField().getText().equals("1")) view().kdfIterationsField().setText("3");
            }
            if (view().kdfInfoBox() != null) {
                view().kdfInfoBox().setVisible(false);
                view().kdfInfoBox().setManaged(false);
            }
            if (view().kdfSaltBox() != null) {
                view().kdfSaltBox().setVisible(true);
                view().kdfSaltBox().setManaged(true);
            }
            if (view().kdfSaltField() != null) {
                view().kdfSaltField().setDisable(false);
                view().kdfSaltField().setPromptText("Required salt");
            }
        }

        if (kdfSaltBadge != null) {
            if (requiresSalt && (view().kdfSaltField() == null || view().kdfSaltField().getText().trim().isEmpty())) {
                kdfSaltBadge.updateStateIncomplete("Salt required");
            } else {
                kdfSaltBadge.updateState();
            }
        }
    }

    void initializeKeyWrap() {
        view().keyWrapModeCombo().getItems().setAll("RFC 3394 - AES Key Wrap", "RFC 5649 - AES Key Wrap with Padding");
        view().keyWrapModeCombo().setValue("RFC 3394 - AES Key Wrap");
    }

    public void handleKeyWrap() {
        try {
            var wrapped = KdfKeyWrapLogic.wrap(view().keyWrapKekField().getText(), view().keyWrapDataField().getText(),
                    view().keyWrapUnwrapCheck().isSelected(), view().keyWrapModeCombo().getValue());
            byte[] kek = wrapped.kek();
            byte[] data = wrapped.data();
            byte[] result = wrapped.result();
            boolean padded = wrapped.padded();
            String operation = wrapped.operation();
            view().keyWrapResultArea().setText(wrapped.report());
            view().keyWrapResultArea().setManaged(true);
            view().keyWrapResultArea().setVisible(true);
            updateStatus("AES Key Wrap " + operation.toLowerCase() + " completed");
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("AES Key " + operation)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Mode: " + (padded ? "RFC 5649" : "RFC 3394") + ", KEK: " + kek.length * 8 + " bits", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Input: " + data.length + " bytes, output: " + result.length + " bytes", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
        } catch (Exception e) {
            showError("AES Key Wrap", "Cannot execute operation: " + e.getMessage());
        }
    }

    public void handleDeriveKey() {
        try {
            clearKdfValidation();
            var result = KdfKeyWrapLogic.derive(new KdfKeyWrapLogic.Request(
                    view().kdfAlgorithmCombo().getValue(),
                    view().kdfInputFormatCombo().getValue(),
                    view().kdfSaltFormatCombo().getValue(),
                    view().kdfInfoFormatCombo().getValue(),
                    view().kdfInputField().getText(),
                    view().kdfSaltField().getText(),
                    view().kdfInfoField().getText(),
                    view().kdfIterationsField().getText(),
                    view().kdfOutputLengthField().getText()));
            byte[] input = result.input();
            byte[] derivedKey = result.derivedKey();
            String algorithm = result.algorithm();
            String inputText = result.inputText();
            String resultInfo = result.resultInfo();

            // Display result
            view().kdfResultArea().setText(resultInfo);
            view().kdfResultArea().setVisible(true);
            view().kdfResultArea().setManaged(true);
            updateStatus("Key derived successfully using " + algorithm);

            // Add to history
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Derive - " + algorithm)
                    .input(input)
                    .output(derivedKey, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                    .enrichedOutput(resultInfo, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Input: " + inputText.substring(0, Math.min(30, inputText.length())), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Derived: " + DataConverter.bytesToHex(derivedKey).substring(0,
                            Math.min(50, DataConverter.bytesToHex(derivedKey).length())), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .status("Key derived successfully using " + algorithm)
                    .build());
            }

        } catch (KeysInputValidation e) {
            showKdfValidation(e.getMessage(), validationField(e.field()));
        } catch (Exception e) {
            showKdfValidation("Cannot derive the key: " + e.getMessage(), null);
        }
    }

    private void clearKdfValidation() {
        if (view().kdfValidationLabel() != null) {
            view().kdfValidationLabel().setText("");
            view().kdfValidationLabel().setVisible(false);
            view().kdfValidationLabel().setManaged(false);
        }
    }

    private void showKdfValidation(String message, javafx.scene.Node field) {
        if (view().kdfValidationLabel() != null) {
            view().kdfValidationLabel().setText("⚠ " + message);
            view().kdfValidationLabel().setVisible(true);
            view().kdfValidationLabel().setManaged(true);
        }
        if (field != null) {
            if (!field.getStyleClass().contains("field-error")) field.getStyleClass().add("field-error");
            field.requestFocus();
        }
        updateStatus(message);
    }

}
