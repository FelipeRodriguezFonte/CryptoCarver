package com.cryptocarver.ui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The template bar of the cipher screen: the built-in AES-256-GCM and AES-256-CBC presets,
 * personal templates (apply, save, export, import, delete) and Reset Defaults. Personal
 * templates store only allow-listed settings, never keys, IVs or data.
 */
final class CipherTemplateCoordinator {

    /** The controls a template reads or sets, injected into CipherController from cipher.fxml. */
    record View(ComboBox<String> template,
            ComboBox<String> algorithm,
            ComboBox<String> mode,
            ComboBox<String> padding,
            ComboBox<String> keySource,
            TextField key,
            TextField iv,
            TextField tag,
            TextField aad,
            ComboBox<String> rsaPadding,
            ComboBox<String> rsaInputFormat,
            ComboBox<String> rsaOutputFormat) {
    }

    private final ComboBox<String> cipherTemplateCombo;
    private final ComboBox<String> symmetricAlgorithmCombo;
    private final ComboBox<String> cipherModeCombo;
    private final ComboBox<String> paddingCombo;
    private final ComboBox<String> symKeySourceCombo;
    private final TextField symmetricKeyField;
    private final TextField ivField;
    private final TextField gcmTagField;
    private final TextField aadField;
    private final ComboBox<String> rsaPaddingCombo;
    private final ComboBox<String> asymmetricInputFormatCombo;
    private final ComboBox<String> asymmetricOutputFormatCombo;
    private final Supplier<StatusReporter> reporter;
    /** The toolbar's format selectors, handed to the controller after the panel is built. */
    private final Supplier<ComboBox<String>> inputFormat;
    private final Supplier<ComboBox<String>> outputFormat;

    CipherTemplateCoordinator(View view, Supplier<StatusReporter> reporter,
            Supplier<ComboBox<String>> inputFormat, Supplier<ComboBox<String>> outputFormat) {
        this.cipherTemplateCombo = view.template();
        this.symmetricAlgorithmCombo = view.algorithm();
        this.cipherModeCombo = view.mode();
        this.paddingCombo = view.padding();
        this.symKeySourceCombo = view.keySource();
        this.symmetricKeyField = view.key();
        this.ivField = view.iv();
        this.gcmTagField = view.tag();
        this.aadField = view.aad();
        this.rsaPaddingCombo = view.rsaPadding();
        this.asymmetricInputFormatCombo = view.rsaInputFormat();
        this.asymmetricOutputFormatCombo = view.rsaOutputFormat();
        this.reporter = reporter;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private ComboBox<String> inputFormat() {
        return inputFormat.get();
    }

    private ComboBox<String> outputFormat() {
        return outputFormat.get();
    }

    void refreshCipherTemplateCombo() {
        SafeTemplateUIHelper.populateTemplateCombo(
                cipherTemplateCombo,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER,
                List.of("AES-256-GCM — Text UTF-8 → Base64", "AES-256-CBC — Hex → Hex")
        );
    }

    void handleApplyCipherTemplate() {
        String template = cipherTemplateCombo != null ? cipherTemplateCombo.getValue() : null;
        if (template == null) return;

        Map<String, java.util.function.Consumer<String>> setters = Map.of(
                "symmetricAlgorithmCombo", v -> { if (symmetricAlgorithmCombo != null) symmetricAlgorithmCombo.setValue(v); },
                "cipherModeCombo", v -> { if (cipherModeCombo != null) cipherModeCombo.setValue(v); },
                "paddingCombo", v -> { if (paddingCombo != null) paddingCombo.setValue(v); },
                "asymmetricInputFormatCombo", v -> { if (asymmetricInputFormatCombo != null) asymmetricInputFormatCombo.setValue(v); },
                "asymmetricOutputFormatCombo", v -> { if (asymmetricOutputFormatCombo != null) asymmetricOutputFormatCombo.setValue(v); },
                "rsaPaddingCombo", v -> { if (rsaPaddingCombo != null) rsaPaddingCombo.setValue(v); },
                "inputFormatCombo", v -> { if (reporter() != null) reporter().setInputFormat(v); },
                "outputFormat()", v -> { if (reporter() != null) reporter().setOutputFormat(v); }
        );

        SafeTemplateUIHelper.applySelectedTemplate(
                template,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER,
                () -> {
                    if (template.contains("AES-256-GCM")) {
                        symmetricAlgorithmCombo.setValue("AES-256");
                        cipherModeCombo.setValue("GCM");
                        paddingCombo.setValue("NoPadding");
                        symKeySourceCombo.setValue("Manual Input");
                        symmetricKeyField.setText("");
                        symmetricKeyField.setPromptText("Enter key in hex (Select a Key)");
                        ivField.setText("");
                        ivField.setPromptText("Click Generate for fresh GCM nonce...");
                        gcmTagField.setText("");
                        aadField.setText("");
                        if (reporter() != null) {
                            reporter().setInputFormat("Text (UTF-8)");
                            reporter().setOutputFormat("Base64");
                            reporter().updateStatus("Template Applied: AES-256-GCM — Text UTF-8 → Base64. GCM authenticates ciphertext; use a fresh nonce for every encryption.");
                        }
                    } else if (template.contains("AES-256-CBC")) {
                        symmetricAlgorithmCombo.setValue("AES-256");
                        cipherModeCombo.setValue("CBC");
                        paddingCombo.setValue("PKCS5Padding");
                        symKeySourceCombo.setValue("Manual Input");
                        symmetricKeyField.setText("");
                        ivField.setText("");
                        gcmTagField.setText("");
                        aadField.setText("");
                        if (reporter() != null) {
                            reporter().setInputFormat("Hexadecimal");
                            reporter().setOutputFormat("Hexadecimal");
                            reporter().updateStatus("Template Applied: AES-256-CBC — Hex → Hex");
                        }
                    }
                },
                setters,
                reporter()
        );
    }

    void handleSaveCipherTemplate() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (symmetricAlgorithmCombo != null && symmetricAlgorithmCombo.getValue() != null) params.put("symmetricAlgorithmCombo", symmetricAlgorithmCombo.getValue());
        if (cipherModeCombo != null && cipherModeCombo.getValue() != null) params.put("cipherModeCombo", cipherModeCombo.getValue());
        if (paddingCombo != null && paddingCombo.getValue() != null) params.put("paddingCombo", paddingCombo.getValue());
        if (rsaPaddingCombo != null && rsaPaddingCombo.getValue() != null) params.put("rsaPaddingCombo", rsaPaddingCombo.getValue());
        if (asymmetricInputFormatCombo != null && asymmetricInputFormatCombo.getValue() != null) params.put("asymmetricInputFormatCombo", asymmetricInputFormatCombo.getValue());
        if (asymmetricOutputFormatCombo != null && asymmetricOutputFormatCombo.getValue() != null) params.put("asymmetricOutputFormatCombo", asymmetricOutputFormatCombo.getValue());
        if (inputFormat() != null && inputFormat().getValue() != null) params.put("inputFormatCombo", inputFormat().getValue());
        if (outputFormat() != null && outputFormat().getValue() != null) params.put("outputFormat()", outputFormat().getValue());

        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.saveCurrentAsTemplate(
                owner,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER,
                params,
                this::refreshCipherTemplateCombo,
                reporter()
        );
    }

    void handleExportCipherTemplate() {
        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.exportSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER, cipherTemplateCombo, reporter());
    }

    void handleImportCipherTemplate() {
        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.importTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER, this::refreshCipherTemplateCombo, reporter());
    }

    void handleDeleteCipherTemplate() {
        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.deleteSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER, cipherTemplateCombo, this::refreshCipherTemplateCombo, reporter());
    }

    void handleResetCipherDefaults() {
        symmetricAlgorithmCombo.setValue("AES-256");
        cipherModeCombo.setValue("CBC");
        paddingCombo.setValue("PKCS5Padding");
        symKeySourceCombo.setValue("Manual Input");
        symmetricKeyField.setText("");
        ivField.setText("");
        gcmTagField.setText("");
        aadField.setText("");
        if (reporter() != null) {
            reporter().setInputFormat("Text (UTF-8)");
            reporter().setOutputFormat("Hexadecimal");
            reporter().updateStatus(com.cryptocarver.service.I18nService.getInstance().text("module.cipher.reset"));
        }
    }
}
