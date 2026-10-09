package com.cryptocarver.ui;

import com.cryptocarver.crypto.XMLSignatureOperations;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.TsaAuthCredentials;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Coordinates XAdES signing, verification, structural inspection and signed XML export. */
final class XmlSignatureSigningCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(XmlSignatureSigningCoordinator.class);

    @FunctionalInterface
    interface ValidationErrors {
        void show(String title, String message, String fieldKey);
    }

    record View(Supplier<TextField> xmlSignInputPathField,
            Supplier<ComboBox<String>> xmlSignLevelCombo,
            Supplier<ComboBox<String>> xmlSignPackagingCombo,
            Supplier<RadioButton> xmlSignSourcePkcs11Radio,
            Supplier<ComboBox<String>> xmlSignKeyAliasCombo,
            Supplier<TextField> xmlSignKeyPathField,
            Supplier<PasswordField> xmlSignKeyPasswordField,
            Supplier<TextArea> xmlSignOutputArea,
            Supplier<TextArea> xmlVerifyInputArea,
            Supplier<TextArea> xmlVerifyReportArea,
            Supplier<TextField> xmlVerifyTrustStorePathField,
            Supplier<PasswordField> xmlVerifyTrustStorePasswordField,
            Supplier<TextArea> xmlInspectInputArea,
            Supplier<TextArea> xmlInspectReportArea,
            Supplier<String> tsaUrl,
            Predicate<String> isHttpUrl,
            Consumer<String> saveCustomTsa,
            UnaryOperator<String> publishedTsaUrl,
            Supplier<TsaAuthCredentials> tsaCredentials,
            Runnable loadKeys,
            ValidationErrors validationErrors) { }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    XmlSignatureSigningCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handleSignXML() {
        try {
            String inputPath = view.xmlSignInputPathField().get().getText();
            if (inputPath.isEmpty()) {
                view.validationErrors().show(t("module.xml.error.inputTitle"), t("module.xml.inputRequired"), "xmlSignInputPathField");
                return;
            }

            String xmlContent = Files.readString(new File(inputPath).toPath());
            String level = view.xmlSignLevelCombo().get().getValue();
            String packaging = view.xmlSignPackagingCombo().get().getValue();
            String tsaUrl = view.tsaUrl().get();

            if (!"XAdES-BASELINE-B".equals(level) && tsaUrl == null) {
                view.validationErrors().show(t("module.xml.error.inputTitle"), t("module.xml.tsaRequired", level), "xmlSignTsaUrlText");
                return;
            }
            if (tsaUrl != null && !view.isHttpUrl().test(tsaUrl)) {
                view.validationErrors().show(t("module.xml.error.inputTitle"), t("module.xml.tsaUrlInvalid"), "xmlSignTsaUrlText");
                return;
            }
            view.saveCustomTsa().accept(tsaUrl);

            String signedXml;
            Map<String, String> details = new HashMap<>();
            details.put("Action", "XAdES Sign");
            details.put("Level", level);
            details.put("Packaging", packaging);
            details.put("Input", inputPath);
            if (tsaUrl != null && !tsaUrl.isEmpty()) {
                details.put("TSA", view.publishedTsaUrl().apply(tsaUrl));
            }

            ComboBox<String> aliasCombo = view.xmlSignKeyAliasCombo().get();
            RadioButton pkcs11Radio = view.xmlSignSourcePkcs11Radio().get();
            if (pkcs11Radio != null && pkcs11Radio.isSelected()) {
                String alias = aliasCombo.getValue();
                if (alias == null || alias.isEmpty()) {
                    view.validationErrors().show(t("module.xml.error.inputTitle"), t("module.xml.feedback.aliasRequired"), "xmlSignKeyAliasCombo");
                    return;
                }
                signedXml = XMLSignatureOperations.signXAdESWithPkcs11(
                        xmlContent, alias, level, tsaUrl, packaging, view.tsaCredentials().get());
                details.put("Source", "PKCS#11");
                details.put("Alias", alias);
            } else {
                String keyPath = view.xmlSignKeyPathField().get().getText();
                String password = view.xmlSignKeyPasswordField().get().getText();
                int keyIndex = aliasCombo.getSelectionModel().getSelectedIndex();

                if (keyPath.isEmpty() || password.isEmpty()) {
                    view.validationErrors().show(t("module.xml.error.inputTitle"), t("module.xml.feedback.keyStoreRequired"), "xmlSignKeyPathField");
                    return;
                }
                if (keyIndex < 0) {
                    view.loadKeys().run();
                    keyIndex = aliasCombo.getSelectionModel().getSelectedIndex();
                    if (keyIndex < 0) {
                        view.validationErrors().show(t("module.xml.error.inputTitle"), t("module.xml.feedback.aliasRequired"), "xmlSignKeyAliasCombo");
                        return;
                    }
                }
                signedXml = XMLSignatureOperations.signXAdES(
                        xmlContent, keyPath, password, keyIndex, level, tsaUrl, packaging, view.tsaCredentials().get());
                details.put("Source", "Local KeyStore");
                details.put("KeyStore", keyPath);
            }

            view.xmlSignOutputArea().get().setText(signedXml);
            details.put("Output Size", signedXml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes");
            reporter.get().publish(OperationResult.forOperation("XAdES Sign")
                    .input(xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(signedXml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details)
                    .status(t("module.xml.status.success"))
                    .build());

        } catch (Exception e) {
            reporter.get().showError("Signing Error", t("module.xml.operationFailed", "XML signing", e.getMessage()));
            LOG.error("XAdES signing failed", e);
        }
    }

    void handleVerifyXML() {
        try {
            String xmlContent = view.xmlVerifyInputArea().get().getText();
            if (xmlContent.isEmpty()) {
                view.validationErrors().show(t("module.xml.error.inputTitle"), t("module.xml.error.pasteXml"), "xmlVerifyInputArea");
                return;
            }
            String trustStorePath = view.xmlVerifyTrustStorePathField().get().getText().trim();
            String trustStorePassword = view.xmlVerifyTrustStorePasswordField().get().getText();
            XMLSignatureOperations.VerificationResult result = XMLSignatureOperations.verifyXAdES(xmlContent, trustStorePath, trustStorePassword);

            String report = result.summary();
            view.xmlVerifyReportArea().get().setText(report);

            Map<String, String> details = new HashMap<>();
            details.put("Action", "XAdES Verify");
            details.put("Trust Policy", trustStorePath.isBlank() ? "Integrity only (no truststore)" : "Truststore configured");
            String indication = extractReportValue(report, "Indication:");
            if (indication != null) details.put("Indication", indication);
            String subIndication = extractReportValue(report, "SubIndication:");
            if (subIndication != null) details.put("SubIndication", subIndication);
            String status = "TOTAL_PASSED".equals(indication)
                    ? "XML verification: valid"
                    : "XML verification: " + (indication == null ? "completed" : indication);
            reporter.get().publish(OperationResult.forOperation("XAdES Verify")
                    .input(xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details).status(status).build());

            // Prompt to save detailed reports
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle(t("module.xml.reportSaveTitle"));
            alert.setHeaderText(t("module.xml.reportSaveHeader"));
            alert.setContentText(AppSettings.isFullLab()
                    ? t("module.xml.reportSavePrompt")
                    : t("module.xml.reportSavePromptSensitive"));
            java.util.Optional<javafx.scene.control.ButtonType> opt = LabPrompt.XML_REPORT_EXPORT.shouldShow()
                    ? alert.showAndWait()
                    : java.util.Optional.of(javafx.scene.control.ButtonType.OK);
            if (opt.isPresent() && opt.get() == javafx.scene.control.ButtonType.OK) {
                javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
                dc.setTitle("Select folder to save reports");
                java.io.File dir = dc.showDialog(view.xmlVerifyReportArea().get().getScene().getWindow());
                if (dir != null) {
                    if (result.xmlSimpleReport() != null) Files.writeString(new File(dir, "SimpleReport.xml").toPath(), result.xmlSimpleReport());
                    if (result.xmlDetailedReport() != null) Files.writeString(new File(dir, "DetailedReport.xml").toPath(), result.xmlDetailedReport());
                    if (result.xmlEtsiReport() != null) Files.writeString(new File(dir, "ETSIReport.xml").toPath(), result.xmlEtsiReport());
                    reporter.get().updateStatus("Saved 3 reports to " + dir.getAbsolutePath());
                }
            }

        } catch (Exception e) {
            reporter.get().showError("Verification Error", t("module.xml.operationFailed", "XML verification", e.getMessage()));
            LOG.error("XAdES verification failed", e);
        }
    }

    void handleInspectSignedXML() {
        try {
            String xml = view.xmlInspectInputArea().get().getText();
            String report = XMLSignatureOperations.inspectSignedXml(xml);
            view.xmlInspectReportArea().get().setText(report);
            Map<String, String> details = new HashMap<>();
            details.put("Action", "Inspect Signed XML");
            details.put("Input bytes", String.valueOf(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
            String signatures = extractReportValue(report, "XMLDSig signatures:");
            if (signatures != null) details.put("Signatures", signatures);
            reporter.get().publish(OperationResult.forOperation("Inspect Signed XML")
                    .input(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details).status(t("module.xml.feedback.statusInspected")).build());
        } catch (Exception e) {
            reporter.get().showError("XML Inspector", t("module.xml.operationFailed", "XML inspection", e.getMessage()));
            LOG.error("Signed XML inspection failed", e);
        }
    }

    void handleSaveSignedXML() {
        TextArea output = view.xmlSignOutputArea().get();
        if (output.getText().isBlank()) {
            reporter.get().showError("Save Error", t("module.xml.feedback.saveRequired"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Signed XML");
        chooser.setInitialFileName("signed.xml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File target = chooser.showSaveDialog(null);
        if (target == null) return;
        try {
            Files.writeString(target.toPath(), output.getText());
            Map<String, String> details = new HashMap<>();
            details.put("Action", "Save signed XML");
            details.put("Output", target.getAbsolutePath());
            reporter.get().publish(OperationResult.forOperation("Save XAdES XML")
                    .output(output.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details).status(t("module.xml.feedback.statusSaved", target.getName())).build());
        } catch (Exception e) {
            reporter.get().showError("Save Error", t("module.xml.operationFailed", "Signed XML save", e.getMessage()));
        }
    }

    private String extractReportValue(String report, String label) {
        for (String line : report.split("\\R")) {
            if (line.startsWith(label)) return line.substring(label.length()).trim();
        }
        return null;
    }
}
