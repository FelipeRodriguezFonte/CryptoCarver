package com.cryptocarver.ui;

import com.cryptocarver.crypto.FormatPreservingEncryption;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/** The format-preserving encryption panel (FF1 and FF3-1) with its alphabet presets. */
final class FpeCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(FpeCoordinator.class);

    /** The panel's controls, injected into CipherController from cipher.fxml. */
    record View(ComboBox<String> operation,
            ComboBox<String> algorithm,
            ComboBox<String> alphabetPreset,
            TextField key,
            TextField tweak,
            TextField alphabet,
            TextArea input,
            TextArea output) {
    }

    private final ComboBox<String> fpeOperationCombo;
    private final ComboBox<String> fpeAlgorithmCombo;
    private final ComboBox<String> fpeAlphabetPresetCombo;
    private final TextField fpeKeyField;
    private final TextField fpeTweakField;
    private final TextField fpeAlphabetField;
    private final TextArea fpeInputArea;
    private final TextArea fpeOutputArea;
    private final Supplier<StatusReporter> reporter;

    FpeCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.fpeOperationCombo = view.operation();
        this.fpeAlgorithmCombo = view.algorithm();
        this.fpeAlphabetPresetCombo = view.alphabetPreset();
        this.fpeKeyField = view.key();
        this.fpeTweakField = view.tweak();
        this.fpeAlphabetField = view.alphabet();
        this.fpeInputArea = view.input();
        this.fpeOutputArea = view.output();
        this.reporter = reporter;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    void configure() {
        if (fpeOperationCombo != null) fpeOperationCombo.getItems().setAll("ENCRYPT", "DECRYPT");
        if (fpeAlgorithmCombo != null) fpeAlgorithmCombo.getItems().setAll("FF1", "FF3_1");
        if (fpeOperationCombo != null) fpeOperationCombo.setValue("ENCRYPT");
        if (fpeAlgorithmCombo != null) fpeAlgorithmCombo.setValue("FF1");
        if (fpeAlphabetPresetCombo != null) {
            fpeAlphabetPresetCombo.getItems().setAll("Decimal (0-9)", "Alphanumeric", "ASCII printable", "Custom");
            fpeAlphabetPresetCombo.setValue("Decimal (0-9)");
            fpeAlphabetField.setText("0123456789");
            fpeAlphabetPresetCombo.valueProperty().addListener((obs, oldValue, value) -> {
                if ("Decimal (0-9)".equals(value)) fpeAlphabetField.setText("0123456789");
                else if ("Alphanumeric".equals(value)) fpeAlphabetField.setText("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
                else if ("ASCII printable".equals(value)) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0x20; i <= 0x7e; i++) b.append((char) i);
                    fpeAlphabetField.setText(b.toString());
                } else fpeAlphabetField.clear();
            });
        }
    }

    void handleFpe() {
        try {
            String input = fpeInputArea == null ? "" : fpeInputArea.getText();
            String alphabet = fpeAlphabetField == null ? "" : fpeAlphabetField.getText();
            byte[] key = DataConverter.hexToBytes(fpeKeyField.getText().trim());
            byte[] tweak = fpeTweakField.getText().trim().isEmpty()
                    ? new byte[0] : DataConverter.hexToBytes(fpeTweakField.getText().trim());
            FormatPreservingEncryption.Algorithm algorithm = FormatPreservingEncryption.Algorithm.valueOf(fpeAlgorithmCombo.getValue());
            boolean encrypt = "ENCRYPT".equals(fpeOperationCombo.getValue());
            String result = encrypt
                    ? FormatPreservingEncryption.encrypt(algorithm, input, key, alphabet, tweak)
                    : FormatPreservingEncryption.decrypt(algorithm, input, key, alphabet, tweak);
            fpeOutputArea.setText(result);
            if (reporter() != null) reporter().updateStatus("FPE " + (encrypt ? "encryption" : "decryption") + " completed");
        } catch (Exception e) {
            if (reporter() != null) reporter().showError("FPE Error", e.getMessage());
            else if (fpeOutputArea != null) fpeOutputArea.setText("Error: " + e.getMessage());
            LOG.warn("FPE operation failed", e);
        }
    }
}
