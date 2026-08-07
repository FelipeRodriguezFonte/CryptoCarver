package com.cryptocarver.ui;

import com.cryptocarver.crypto.PadesOperations;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Accordion;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Small, explicit PAdES Baseline-B laboratory panel. */
public final class PadesController {
    @FXML private javafx.scene.layout.VBox padesRoot;
    @FXML private Accordion padesAccordion;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }
    private static final long MAX_PDF_BYTES = 64L * 1024L * 1024L;

    @FXML private TextField padesInputPathField;
    @FXML private TextField padesOutputPathField;
    @FXML private TextField padesPkcs12PathField;
    @FXML private PasswordField padesPasswordField;
    @FXML private RadioButton padesSourceLocalRadio;
    @FXML private RadioButton padesSourcePkcs11Radio;
    @FXML private ToggleGroup padesSourceToggleGroup;
    @FXML private javafx.scene.layout.HBox padesLocalKeyBox;
    @FXML private javafx.scene.layout.HBox padesPkcs11Box;
    @FXML private ComboBox<String> padesPkcs11AliasCombo;
    @FXML private CheckBox padesTimestampCheck;
    @FXML private ComboBox<String> padesProfileCombo;
    @FXML private javafx.scene.layout.HBox padesTsaBox;
    @FXML private TextField padesTsaUrlField;
    @FXML private CheckBox padesVisibleSignatureCheck;
    @FXML private javafx.scene.layout.VBox padesVisibleSignatureBox;
    @FXML private TextField padesVisiblePageField;
    @FXML private TextField padesVisibleXField;
    @FXML private TextField padesVisibleYField;
    @FXML private TextField padesVisibleWidthField;
    @FXML private TextField padesVisibleHeightField;
    @FXML private TextField padesVisibleTextField;
    @FXML private TextField padesTrustStorePathField;
    @FXML private PasswordField padesTrustStorePasswordField;
    @FXML private TextField padesCrlEvidenceField;
    @FXML private CheckBox padesOnlineRevocationCheck;
    @FXML private TextArea padesResultArea;
    @FXML private Button padesSignButton;
    @FXML private Button padesValidateButton;

    private StatusReporter statusReporter;
    private PadesOperations.PadesValidationResult lastValidation;
    private List<File> padesCrlEvidence = List.of();

    public void setStatusReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    @FXML
    private void initialize() {
        moduleI18n = ModuleI18n.bind(padesRoot, ModuleTextCatalog.pades());
        if (padesProfileCombo != null) {
            padesProfileCombo.getItems().setAll("Baseline-B", "Baseline-T", "Baseline-LT", "Baseline-LTA");
            padesProfileCombo.getSelectionModel().selectFirst();
        }
        handleTimestampOptionChanged();
        handleSourceChanged();
        handleVisibleSignatureOptionChanged();
    }

    @FXML
    private void handleChooseInput() {
        chooseInto(padesInputPathField, "Select PDF to sign", "PDF files", "*.pdf");
    }

    @FXML
    private void handleChooseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save signed PDF (new file required)");
        chooser.setInitialFileName("signed-document.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = chooser.showSaveDialog(owner());
        if (file != null) padesOutputPathField.setText(file.getAbsolutePath());
    }

    @FXML
    private void handleChoosePkcs12() {
        chooseInto(padesPkcs12PathField, "Select PKCS#12 signing key", "PKCS#12 files", "*.p12", "*.pfx");
    }

    @FXML
    private void handleChooseTrustStore() {
        chooseInto(padesTrustStorePathField, "Select validation truststore", "Truststores", "*.p12", "*.pfx", "*.jks");
    }

    @FXML
    private void handleChooseCrlEvidence() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select local CRL evidence (optional, 4 MiB each)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CRL evidence", "*.crl", "*.pem", "*.der"));
        List<File> selected = chooser.showOpenMultipleDialog(owner());
        if (selected == null || selected.isEmpty()) return;
        padesCrlEvidence = List.copyOf(selected);
        if (padesCrlEvidenceField != null) {
            padesCrlEvidenceField.setText(padesCrlEvidence.size() + " local CRL file(s) selected");
        }
    }

    @FXML
    private void handleSourceChanged() {
        boolean tokenSource = padesSourcePkcs11Radio != null && padesSourcePkcs11Radio.isSelected();
        setVisibleManaged(padesLocalKeyBox, !tokenSource);
        setVisibleManaged(padesPkcs11Box, tokenSource);
    }

    @FXML
    private void handleLoadTokenKeys() {
        try {
            java.util.List<String> aliases = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance()
                    .requireSession().listPrivateKeysWithCertificate();
            if (aliases.isEmpty()) throw new FieldValidationException(
                    t("module.pades.feedback.noTokenKeys"), "padesPkcs11AliasCombo");
            padesPkcs11AliasCombo.getItems().setAll(aliases);
            padesPkcs11AliasCombo.getSelectionModel().selectFirst();
        } catch (FieldValidationException validation) {
            showValidation(validation);
        } catch (Exception error) {
            showError("PAdES PKCS#11", error.getMessage());
        }
    }

    /** Makes PAdES-T opt-in so a baseline signature never contacts a TSA unexpectedly. */
    @FXML
    private void handleTimestampOptionChanged() {
        boolean lt = "Baseline-LT".equals(profile()) || "Baseline-LTA".equals(profile());
        boolean enabled = lt || (padesTimestampCheck != null && padesTimestampCheck.isSelected());
        if (lt && padesTimestampCheck != null) padesTimestampCheck.setSelected(true);
        if (padesTsaBox != null) {
            padesTsaBox.setVisible(enabled);
            padesTsaBox.setManaged(enabled);
        }
        if (enabled && padesTsaUrlField != null && padesTsaUrlField.getText().isBlank()) {
            padesTsaUrlField.setText(AppSettings.getInstance().getCustomTsaUrl());
        }
    }

    @FXML
    private void handleVisibleSignatureOptionChanged() {
        boolean enabled = padesVisibleSignatureCheck != null && padesVisibleSignatureCheck.isSelected();
        setVisibleManaged(padesVisibleSignatureBox, enabled);
    }

    @FXML
    private void handleReset() {
        ModuleResetPolicy.apply(padesRoot, ModuleResetPolicy.Action.RESET_DEFAULTS,
                this::clearModuleData, this::restoreSafeDefaults);
        if (statusReporter != null) statusReporter.updateStatus(t("module.common.resetStatus"));
    }

    @FXML
    private void handleClear() {
        ModuleResetPolicy.apply(padesRoot, ModuleResetPolicy.Action.CLEAR, this::clearModuleData, null);
        if (statusReporter != null) statusReporter.updateStatus(t("module.common.clearStatus"));
    }

    private void clearModuleData() {
        ModuleResetPolicy.clearTextInputs(padesRoot);
        lastValidation = null;
        padesCrlEvidence = List.of();
    }

    private void restoreSafeDefaults() {
        if (padesSourceLocalRadio != null) padesSourceLocalRadio.setSelected(true);
        if (padesTimestampCheck != null) padesTimestampCheck.setSelected(false);
        if (padesProfileCombo != null) padesProfileCombo.getSelectionModel().select("Baseline-B");
        if (padesVisibleSignatureCheck != null) padesVisibleSignatureCheck.setSelected(false);
        if (padesOnlineRevocationCheck != null) padesOnlineRevocationCheck.setSelected(false);
        handleTimestampOptionChanged();
        handleVisibleSignatureOptionChanged();
        handleSourceChanged();
    }

    @FXML
    private void handleSign() {
        char[] password = password();
        try {
            File source = requireFile(padesInputPathField, "PDF input");
            File destination = requireNewFile(padesOutputPathField, "PDF output");
            byte[] input = readBoundedPdf(source, "padesInputPathField");
            String selectedProfile = profile();
            boolean lt = "Baseline-LT".equals(selectedProfile) || "Baseline-LTA".equals(selectedProfile);
            boolean lta = "Baseline-LTA".equals(selectedProfile);
            boolean timestamped = lt || (padesTimestampCheck != null && padesTimestampCheck.isSelected());
            String tsaUrl = padesTsaUrlField == null ? "" : padesTsaUrlField.getText();
            boolean tokenSource = padesSourcePkcs11Radio != null && padesSourcePkcs11Radio.isSelected();
            PadesOperations.VisibleSignatureOptions visibleSignature = visibleSignatureOptions();
            String tokenAlias = tokenSource ? selectedTokenAlias() : null;
            File pkcs12 = tokenSource ? null : requireFile(padesPkcs12PathField, "PKCS#12 signing key");
            OperationExecutor executor = statusReporter == null ? null : statusReporter.getOperationExecutor();
            if (executor == null) {
                throw new IllegalStateException("PAdES operation executor is not available");
            }
            executor.execute("PAdES signing", padesSignButton, () -> {
                byte[] signed;
                if (tokenSource) {
                    if (lta) {
                        signed = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                                .signPadesBaselineLTA(tokenAlias, input, tsaUrl, padesCrlEvidence,
                                        padesOnlineRevocationCheck.isSelected(), visibleSignature);
                    } else if (lt) {
                        signed = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                                .signPadesBaselineLT(tokenAlias, input, tsaUrl, padesCrlEvidence,
                                        padesOnlineRevocationCheck.isSelected(), visibleSignature);
                    } else {
                        signed = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                                .signPades(tokenAlias, input, timestamped ? tsaUrl : null, visibleSignature);
                    }
                } else if (lta) {
                    signed = PadesOperations.signBaselineLTA(input, pkcs12, password, tsaUrl,
                            padesCrlEvidence, padesOnlineRevocationCheck.isSelected(), visibleSignature);
                } else if (lt) {
                    signed = PadesOperations.signBaselineLT(input, pkcs12, password, tsaUrl,
                            padesCrlEvidence, padesOnlineRevocationCheck.isSelected(), visibleSignature);
                } else if (timestamped && visibleSignature != null) {
                    signed = PadesOperations.signBaselineT(input, pkcs12, password, tsaUrl, visibleSignature);
                } else if (timestamped) {
                    signed = PadesOperations.signBaselineT(input, pkcs12, password, tsaUrl);
                } else {
                    signed = PadesOperations.signBaselineB(input, pkcs12, password, visibleSignature);
                }
                Files.write(destination.toPath(), signed, java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE);
                return new PadesSignResult(signed, PadesOperations.inspectSignatures(signed),
                        PadesOperations.inspectEmbeddedEvidence(signed));
            }, result -> {
                String profile = (lta ? "PAdES Baseline-LTA" : (lt ? "PAdES Baseline-LT" : (timestamped ? "PAdES Baseline-T" : "PAdES Baseline-B")))
                        + (tokenSource ? " (PKCS#11)" : "") + (visibleSignature == null ? "" : " (visible)");
                PadesOperations.EmbeddedEvidence evidence = result.evidence();
                padesResultArea.setText(profile + " signature written to: " + destination.getName()
                        + "\nPDF signature dictionaries: " + result.inspection().signatureCount()
                        + "\nEmbedded evidence: certificates=" + evidence.certificateCount()
                        + ", CRLs=" + evidence.crlCount() + ", OCSP=" + evidence.ocspCount()
                        + (lta ? "\nDSS profile produced: PAdES Baseline-LTA (LT evidence present; RFC 3161 archive timestamp integrity checked; TSA trust requires validation truststore)"
                                : (lt ? "\nEffective profile: PAdES Baseline-LT (DSS LT + LT evidence verified)" : ""))
                        + "\n\nThis separates cryptographic signature from chain trust and revocation."
                        + (timestamped ? " TSA trust is not evaluated." : " Timestamping was not requested."));
                publish(profile + " Sign", source, destination, result.inspection().signatureCount(), profile);
                Arrays.fill(password, '\0');
            }, error -> {
                Arrays.fill(password, '\0');
                showError("PAdES signing", t("module.pades.feedback.operation", "PAdES signing", error.getMessage()));
            }, () -> Arrays.fill(password, '\0'));
        } catch (FieldValidationException validation) {
            Arrays.fill(password, '\0');
            showValidation(validation);
        } catch (Exception error) {
            Arrays.fill(password, '\0');
            showError("PAdES signing", t("module.pades.feedback.operation", "PAdES signing", error.getMessage()));
        }
    }

    private record PadesSignResult(byte[] signed, PadesOperations.PdfSignatureInspection inspection,
                                   PadesOperations.EmbeddedEvidence evidence) { }

    private String profile() {
        return padesProfileCombo == null || padesProfileCombo.getValue() == null
                ? "Baseline-B" : padesProfileCombo.getValue();
    }

    @FXML
    private void handleInspect() {
        try {
            File source = requireFile(padesInputPathField, "PDF input");
            PadesOperations.PdfSignatureInspection inspection = PadesOperations.inspectSignatures(
                    readBoundedPdf(source, "padesInputPathField"));
            String details = inspection.signatureCount() == 0 ? "No PDF signature dictionaries found." :
                    "PDF signature dictionaries: " + inspection.signatureCount() + "\n\n"
                            + String.join("\n", inspection.signatures());
            PadesOperations.EmbeddedEvidence evidence = PadesOperations.inspectEmbeddedEvidence(
                    readBoundedPdf(source, "padesInputPathField"));
            String effectiveProfile = "not established (DSS validation required)";
            padesResultArea.setText(details + "\n\nDeclared profile: not available from PDF metadata"
                    + "\nEffective profile: " + effectiveProfile
                    + "\nEmbedded evidence: certificates=" + evidence.certificateCount()
                    + ", CRLs=" + evidence.crlCount() + ", OCSP=" + evidence.ocspCount()
                    + "\nArchive timestamp: " + (inspection.archiveTimestampCount() > 0 ? "present" : "absent")
                    + "\nStructural inspection only: signature, chain trust and revocation are reported separately.");
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PAdES Inspect")
                        .detail("PDF signatures", String.valueOf(inspection.signatureCount()))
                        .status(t("module.pades.feedback.statusInspected")).build());
            }
        } catch (FieldValidationException validation) {
            showValidation(validation);
        } catch (Exception error) {
            showError("PAdES inspection", t("module.pades.feedback.operation", "PAdES inspection", error.getMessage()));
        }
    }

    @FXML
    private void handleValidate() {
        char[] trustPassword = padesTrustStorePasswordField == null || padesTrustStorePasswordField.getText() == null
                ? new char[0] : padesTrustStorePasswordField.getText().toCharArray();
        try {
            File source = requireFile(padesInputPathField, "PDF input");
            File trustStore = optionalFile(padesTrustStorePathField, "Validation truststore");
            byte[] pdf = readBoundedPdf(source, "padesInputPathField");
            boolean online = padesOnlineRevocationCheck != null && padesOnlineRevocationCheck.isSelected();
            OperationExecutor executor = statusReporter == null ? null : statusReporter.getOperationExecutor();
            if (executor == null) throw new IllegalStateException("PAdES operation executor is not available");
            executor.execute("PAdES validation", padesValidateButton, () -> PadesOperations.validate(
                    pdf, trustStore, trustPassword, padesCrlEvidence, online), validation -> {
                lastValidation = validation;
                padesResultArea.setText(validation.summary()
                        + "\nReport XML is available internally only; it can contain certificate PII.");
                if (statusReporter != null) statusReporter.publish(OperationResult.forOperation("PAdES Validate")
                        .detail("Input PDF", source.getName())
                        .detail("Truststore", trustStore == null ? "Not configured" : trustStore.getName())
                        .detail("Local CRL evidence", String.valueOf(validation.localCrlCount()))
                        .detail("Revocation", validation.revocation().status().name())
                        .detail("Evidence", validation.revocation().evidence().name())
                        .status(t("module.pades.feedback.statusValidated")).build());
                Arrays.fill(trustPassword, '\0');
            }, error -> {
                Arrays.fill(trustPassword, '\0');
                showError("PAdES validation", t("module.pades.feedback.operation", "PAdES validation", error.getMessage()));
            }, () -> Arrays.fill(trustPassword, '\0'));
        } catch (FieldValidationException validation) {
            showValidation(validation);
        } catch (Exception error) {
            showError("PAdES validation", t("module.pades.feedback.operation", "PAdES validation", error.getMessage()));
        } finally {
            Arrays.fill(trustPassword, '\0');
        }
    }

    /** Explicit user action because DSS reports can contain certificate PII. */
    @FXML
    private void handleSaveValidationReports() {
        try {
            if (lastValidation == null) {
                showValidation(t("module.pades.feedback.reportRequired"), "padesInputPathField");
                return;
            }
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose empty output location for PAdES DSS reports (contains certificate PII)");
            File directory = chooser.showDialog(owner());
            if (directory == null) return;
            writeNewReport(directory, "pades-simple-report.xml", lastValidation.xmlSimpleReport());
            writeNewReport(directory, "pades-detailed-report.xml", lastValidation.xmlDetailedReport());
            writeNewReport(directory, "pades-etsi-validation-report.xml", lastValidation.xmlEtsiReport());
            if (statusReporter != null) statusReporter.publish(OperationResult.forOperation("PAdES Validation Reports Export")
                    .detail("Directory", directory.getName())
                    .detail("Files", "3 XML reports")
                    .detail("Privacy", "Certificate PII may be included")
                    .status(t("module.pades.feedback.statusReports")).build());
            padesResultArea.appendText("\n\nDSS reports saved to: " + directory.getAbsolutePath()
                    + "\nWarning: the XML files may contain certificate PII.");
        } catch (Exception error) {
            showError("PAdES reports export", t("module.pades.feedback.operation", "PAdES reports export", error.getMessage()));
        }
    }

    private void chooseInto(TextField destination, String title, String type, String... patterns) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(type, patterns));
        File file = chooser.showOpenDialog(owner());
        if (file != null) destination.setText(file.getAbsolutePath());
    }

    private javafx.stage.Window owner() {
        return padesResultArea == null || padesResultArea.getScene() == null ? null : padesResultArea.getScene().getWindow();
    }

    private static File requireFile(TextField field, String label) {
        String fieldKey = fieldKeyForLabel(label);
        if (field == null || field.getText().isBlank()) throw new FieldValidationException(
                com.cryptocarver.service.I18nService.getInstance()
                        .text("module.pades.feedback.required", label), fieldKey);
        File file = new File(field.getText().trim());
        if (!file.isFile()) throw new FieldValidationException(
                com.cryptocarver.service.I18nService.getInstance()
                        .text("module.pades.feedback.fileMissing", label), fieldKey);
        return file;
    }

    private String selectedTokenAlias() {
        if (padesPkcs11AliasCombo == null || padesPkcs11AliasCombo.getValue() == null
                || padesPkcs11AliasCombo.getValue().isBlank()) {
            throw new FieldValidationException(t("module.pades.feedback.tokenKey"), "padesPkcs11AliasCombo");
        }
        return padesPkcs11AliasCombo.getValue();
    }

    private PadesOperations.VisibleSignatureOptions visibleSignatureOptions() {
        if (padesVisibleSignatureCheck == null || !padesVisibleSignatureCheck.isSelected()) return null;
        int page = parseInteger(text(padesVisiblePageField, "Visible signature page", "padesVisiblePageField"),
                "padesVisiblePageField");
        float x = parseFloat(text(padesVisibleXField, "Visible signature X", "padesVisibleXField"),
                "padesVisibleXField");
        float y = parseFloat(text(padesVisibleYField, "Visible signature Y", "padesVisibleYField"),
                "padesVisibleYField");
        float width = parseFloat(text(padesVisibleWidthField, "Visible signature width", "padesVisibleWidthField"),
                "padesVisibleWidthField");
        float height = parseFloat(text(padesVisibleHeightField, "Visible signature height", "padesVisibleHeightField"),
                "padesVisibleHeightField");
        return new PadesOperations.VisibleSignatureOptions(page, x, y, width, height,
                text(padesVisibleTextField, "Visible signature text", "padesVisibleTextField"));
    }

    private static int parseInteger(String value, String fieldKey) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw coordinateValidation(invalid, fieldKey);
        }
    }

    private static float parseFloat(String value, String fieldKey) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException invalid) {
            throw coordinateValidation(invalid, fieldKey);
        }
    }

    private static FieldValidationException coordinateValidation(NumberFormatException cause, String fieldKey) {
        return new FieldValidationException(com.cryptocarver.service.I18nService.getInstance()
                .text("module.pades.feedback.coordinates"), cause, fieldKey);
    }

    private static String text(TextField field, String label) {
        return text(field, label, null);
    }

    private static String text(TextField field, String label, String fieldKey) {
        if (field == null || field.getText() == null || field.getText().isBlank()) {
            throw new FieldValidationException(com.cryptocarver.service.I18nService.getInstance()
                    .text("module.pades.feedback.required", label), fieldKey);
        }
        return field.getText().trim();
    }

    private static void setVisibleManaged(javafx.scene.Node node, boolean value) {
        if (node == null) return;
        node.setVisible(value);
        node.setManaged(value);
    }

    private static File requireNewFile(TextField field, String label) {
        String fieldKey = fieldKeyForLabel(label);
        if (field == null || field.getText().isBlank()) throw new FieldValidationException(
                com.cryptocarver.service.I18nService.getInstance().text("module.pades.feedback.required", label), fieldKey);
        File file = new File(field.getText().trim());
        if (Files.exists(file.toPath())) throw new FieldValidationException(
                com.cryptocarver.service.I18nService.getInstance().text("module.pades.feedback.outputExists", label), fieldKey);
        return file;
    }

    private static File optionalFile(TextField field, String label) {
        if (field == null || field.getText().isBlank()) return null;
        File file = new File(field.getText().trim());
        if (!file.isFile()) throw new FieldValidationException(
                com.cryptocarver.service.I18nService.getInstance().text("module.pades.feedback.fileMissing", label),
                fieldKeyForLabel(label));
        return file;
    }

    private static void writeNewReport(File directory, String name, String content) throws Exception {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("DSS did not produce " + name);
        java.nio.file.Path target = directory.toPath().resolve(name);
        Files.writeString(target, content, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
    }

    private static byte[] readBoundedPdf(File file, String fieldKey) throws Exception {
        long size = Files.size(file.toPath());
        if (size > MAX_PDF_BYTES) throw new FieldValidationException(com.cryptocarver.service.I18nService
                .getInstance().text("module.pades.feedback.fileTooLarge"), fieldKey);
        return Files.readAllBytes(file.toPath());
    }

    private static String fieldKeyForLabel(String label) {
        if (label == null) return null;
        String normalized = label.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("output")) return "padesOutputPathField";
        if (normalized.contains("pkcs#12")) return "padesPkcs12PathField";
        if (normalized.contains("truststore")) return "padesTrustStorePathField";
        return "padesInputPathField";
    }

    private void showValidation(FieldValidationException validation) {
        InlineValidationSupport.showValidation(statusReporter, t("preflight.title"), validation.getMessage(),
                t("preflight.remedy.input"), validation);
    }

    private void showValidation(String detail, String fieldKey) {
        InlineValidationSupport.show(statusReporter, t("preflight.title"), detail,
                t("preflight.remedy.input"), fieldKey, null);
    }

    private char[] password() {
        return padesPasswordField == null || padesPasswordField.getText() == null
                ? new char[0] : padesPasswordField.getText().toCharArray();
    }

    private void publish(String operation, File input, File output, int signatures, String profile) {
        if (statusReporter != null) {
            statusReporter.publish(OperationResult.forOperation(operation)
                    .detail("Input PDF", input.getName())
                    .detail("Output PDF", output.getName())
                    .detail("PDF signatures", String.valueOf(signatures))
                    .detail("Profile", profile)
                    .status(t("module.pades.feedback.statusSigned", profile)).build());
        }
    }

    private void showError(String title, String message) {
        if (statusReporter != null) statusReporter.showError(title, message);
    }
}
