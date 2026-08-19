package com.cryptocarver.ui;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class UiStateSnapshotTest {

    @BeforeAll
    static void initJfx() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    public static class DummyController {
        @FXML public TextField keyField;
        @FXML public TextField passwordInput;
        @FXML public TextField ivField;
        @FXML public TextField dataField;
        @FXML public TextField tr31KbpkExportField;
        @FXML public TextField cvkField;
        @FXML public TextField pvkField;
        @FXML public TextField certInputArea;
        @FXML public TextField certIssueCaKeyArea;
        @FXML public TextArea technicalResultArea;
        @FXML public TextField outputLengthField;
        @FXML public TextField outputPathField;
        @FXML public ComboBox<String> algoCombo;
        @FXML public ComboBox<String> tr31FormatCombo;
        @FXML public ComboBox<String> outputFormatCombo;
        @FXML public ComboBox<String> tr31ModeCombo;
        @FXML public ComboBox<String> tr31KeySizeCombo;
        @FXML public ComboBox<String> tr31UsageCombo;
        @FXML public ComboBox<String> certAlgorithmCombo;
        @FXML public ComboBox<String> certFormatCombo;
        @FXML public ComboBox<String> certKeySizeCombo;
        @FXML public ChoiceBox<String> encodingFormatChoice;
        @FXML public CheckBox myCheck;
        @FXML public CheckBox algorithmModeCheck;

        public DummyController() {
            keyField = new TextField("my-super-secret-key");
            passwordInput = new TextField("hunter2");
            ivField = new TextField("1234567890123456");
            dataField = new TextField("hello world");
            tr31KbpkExportField = new TextField("00112233445566778899AABBCCDDEEFF");
            cvkField = new TextField("1111222233334444");
            pvkField = new TextField("5555666677778888");
            certInputArea = new TextField("-----BEGIN CERTIFICATE-----secret-----END CERTIFICATE-----");
            certIssueCaKeyArea = new TextField("ca-private-key");
            technicalResultArea = new TextArea("B0096P0TE00E000000000000000000000000000");
            outputLengthField = new TextField("32");
            outputPathField = new TextField("/tmp/cryptocarver-result.bin");
            algoCombo = new ComboBox<>();
            algoCombo.getItems().add("AES");
            algoCombo.setValue("AES");
            tr31FormatCombo = selector("HEX");
            outputFormatCombo = selector("Base64");
            tr31ModeCombo = selector("Derive");
            tr31KeySizeCombo = selector("256");
            tr31UsageCombo = selector("C0 - CVK");
            certAlgorithmCombo = selector("SHA256withRSA");
            certFormatCombo = selector("PEM");
            certKeySizeCombo = selector("2048");
            encodingFormatChoice = new ChoiceBox<>();
            encodingFormatChoice.getItems().add("Base64URL");
            encodingFormatChoice.setValue("Base64URL");
            myCheck = new CheckBox();
            myCheck.setSelected(true);
            algorithmModeCheck = new CheckBox();
            algorithmModeCheck.setSelected(true);
        }

        private static ComboBox<String> selector(String value) {
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().add(value);
            combo.setValue(value);
            return combo;
        }
    }

    @Test
    void captureHistoryRecipeRedactsSecrets() {
        DummyController controller = new DummyController();

        Map<String, Object> state = UiStateSnapshot.captureHistoryRecipe(controller);

        assertEquals("[REDACTED_SECRET]", state.get("DummyController.keyField"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.passwordInput"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.ivField"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.tr31KbpkExportField"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.cvkField"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.pvkField"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.certInputArea"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.certIssueCaKeyArea"));
        assertEquals(null, state.get("DummyController.technicalResultArea"));
        assertEquals("32", state.get("DummyController.outputLengthField"));
        assertEquals("/tmp/cryptocarver-result.bin", state.get("DummyController.outputPathField"));
        assertEquals("hello world", state.get("DummyController.dataField"));
        assertEquals("AES", state.get("DummyController.algoCombo"));
        assertEquals("HEX", state.get("DummyController.tr31FormatCombo"));
        assertEquals("Base64", state.get("DummyController.outputFormatCombo"));
        assertEquals("Derive", state.get("DummyController.tr31ModeCombo"));
        assertEquals("256", state.get("DummyController.tr31KeySizeCombo"));
        assertEquals("C0 - CVK", state.get("DummyController.tr31UsageCombo"));
        assertEquals("SHA256withRSA", state.get("DummyController.certAlgorithmCombo"));
        assertEquals("PEM", state.get("DummyController.certFormatCombo"));
        assertEquals("2048", state.get("DummyController.certKeySizeCombo"));
        assertEquals("Base64URL", state.get("DummyController.encodingFormatChoice"));
        assertEquals(true, state.get("DummyController.myCheck"));
        assertEquals(true, state.get("DummyController.algorithmModeCheck"));
    }

    @Test
    void historyRestoreClearsSensitiveFieldsButRestoresSafeSelectors() {
        DummyController controller = new DummyController();
        controller.tr31KbpkExportField.setText("old-kbpk");
        controller.cvkField.setText("old-cvk");
        controller.pvkField.setText("old-pvk");
        controller.certInputArea.setText("old-certificate");
        controller.certIssueCaKeyArea.setText("old-ca-key");
        controller.technicalResultArea.setText("old-result");
        controller.outputLengthField.clear();
        controller.outputPathField.clear();

        UiStateSnapshot.restoreHistoryRecipe(controller, Map.ofEntries(
                Map.entry("DummyController.tr31KbpkExportField", "[REDACTED_SECRET]"),
                Map.entry("DummyController.cvkField", "[REDACTED_SECRET]"),
                Map.entry("DummyController.pvkField", "[REDACTED_SECRET]"),
                Map.entry("DummyController.certInputArea", "[REDACTED_SECRET]"),
                Map.entry("DummyController.certIssueCaKeyArea", "[REDACTED_SECRET]"),
                Map.entry("DummyController.technicalResultArea", "old-result"),
                Map.entry("DummyController.outputLengthField", "64"),
                Map.entry("DummyController.outputPathField", "/tmp/derived.bin"),
                Map.entry("DummyController.outputFormatCombo", "Hex"),
                Map.entry("DummyController.tr31ModeCombo", "Wrap"),
                Map.entry("DummyController.tr31KeySizeCombo", "128"),
                Map.entry("DummyController.encodingFormatChoice", "HEX"),
                Map.entry("DummyController.algorithmModeCheck", false)));

        assertEquals("", controller.tr31KbpkExportField.getText());
        assertEquals("", controller.cvkField.getText());
        assertEquals("", controller.pvkField.getText());
        assertEquals("", controller.certInputArea.getText());
        assertEquals("", controller.certIssueCaKeyArea.getText());
        assertEquals("", controller.technicalResultArea.getText());
        assertEquals("64", controller.outputLengthField.getText());
        assertEquals("/tmp/derived.bin", controller.outputPathField.getText());
        assertEquals("Hex", controller.outputFormatCombo.getValue());
        assertEquals("Wrap", controller.tr31ModeCombo.getValue());
        assertEquals("128", controller.tr31KeySizeCombo.getValue());
        assertEquals("HEX", controller.encodingFormatChoice.getValue());
        assertFalse(controller.algorithmModeCheck.isSelected());
    }

    @Test
    void capturePortableConfigurationPreservesSecrets() {
        DummyController controller = new DummyController();

        Map<String, Object> state = UiStateSnapshot.capturePortableConfiguration(controller);

        assertEquals("my-super-secret-key", state.get("DummyController.keyField"));
    }
}
