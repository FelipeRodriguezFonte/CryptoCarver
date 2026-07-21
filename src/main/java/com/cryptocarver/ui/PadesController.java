package com.cryptocarver.ui;

import com.cryptocarver.crypto.PadesOperations;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Small, explicit PAdES Baseline-B laboratory panel. */
public final class PadesController {
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
    @FXML private TextArea padesResultArea;

    private StatusReporter statusReporter;
    private PadesOperations.PadesValidationResult lastValidation;
    private List<File> padesCrlEvidence = List.of();

    public void setStatusReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    @FXML
    private void initialize() {
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
            if (aliases.isEmpty()) throw new IllegalArgumentException("No private PKCS#11 keys with X.509 certificates are available");
            padesPkcs11AliasCombo.getItems().setAll(aliases);
            padesPkcs11AliasCombo.getSelectionModel().selectFirst();
        } catch (Exception error) {
            showError("PAdES PKCS#11", error.getMessage());
        }
    }

    /** Makes PAdES-T opt-in so a baseline signature never contacts a TSA unexpectedly. */
    @FXML
    private void handleTimestampOptionChanged() {
        boolean enabled = padesTimestampCheck != null && padesTimestampCheck.isSelected();
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
    private void handleSign() {
        char[] password = password();
        try {
            File source = requireFile(padesInputPathField, "PDF input");
            File destination = requireNewFile(padesOutputPathField, "PDF output");
            byte[] input = readBoundedPdf(source);
            boolean timestamped = padesTimestampCheck != null && padesTimestampCheck.isSelected();
            String tsaUrl = padesTsaUrlField == null ? "" : padesTsaUrlField.getText();
            boolean tokenSource = padesSourcePkcs11Radio != null && padesSourcePkcs11Radio.isSelected();
            PadesOperations.VisibleSignatureOptions visibleSignature = visibleSignatureOptions();
            byte[] signed;
            if (tokenSource) {
                String alias = selectedTokenAlias();
                signed = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                        .signPades(alias, input, timestamped ? tsaUrl : null, visibleSignature);
            } else {
                File pkcs12 = requireFile(padesPkcs12PathField, "PKCS#12 signing key");
                if (timestamped && visibleSignature != null) {
                    signed = PadesOperations.signBaselineT(input, pkcs12, password, tsaUrl, visibleSignature);
                } else if (timestamped) {
                    signed = PadesOperations.signBaselineT(input, pkcs12, password, tsaUrl);
                } else {
                    signed = PadesOperations.signBaselineB(input, pkcs12, password, visibleSignature);
                }
            }
            Files.write(destination.toPath(), signed, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
            PadesOperations.PdfSignatureInspection inspection = PadesOperations.inspectSignatures(signed);
            String profile = (timestamped ? "PAdES Baseline-T" : "PAdES Baseline-B")
                    + (tokenSource ? " (PKCS#11)" : "") + (visibleSignature == null ? "" : " (visible)");
            padesResultArea.setText(profile + " signature written to: " + destination.getName()
                    + "\nPDF signature dictionaries: " + inspection.signatureCount()
                    + "\n\nThis confirms PDF signature structure only. Certificate trust and revocation are not evaluated."
                    + (timestamped ? " TSA trust is not evaluated." : " Timestamping was not requested."));
            publish(profile + " Sign", source, destination, inspection.signatureCount(), profile);
        } catch (Exception error) {
            showError("PAdES signing", error.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @FXML
    private void handleInspect() {
        try {
            File source = requireFile(padesInputPathField, "PDF input");
            PadesOperations.PdfSignatureInspection inspection = PadesOperations.inspectSignatures(readBoundedPdf(source));
            String details = inspection.signatureCount() == 0 ? "No PDF signature dictionaries found." :
                    "PDF signature dictionaries: " + inspection.signatureCount() + "\n\n"
                            + String.join("\n", inspection.signatures());
            padesResultArea.setText(details + "\n\nStructural inspection only: no cryptographic, trust or revocation validation is claimed.");
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PAdES Inspect")
                        .detail("PDF signatures", String.valueOf(inspection.signatureCount()))
                        .status("PAdES structural inspection completed").build());
            }
        } catch (Exception error) {
            showError("PAdES inspection", error.getMessage());
        }
    }

    @FXML
    private void handleValidate() {
        char[] trustPassword = padesTrustStorePasswordField == null || padesTrustStorePasswordField.getText() == null
                ? new char[0] : padesTrustStorePasswordField.getText().toCharArray();
        try {
            File source = requireFile(padesInputPathField, "PDF input");
            File trustStore = optionalFile(padesTrustStorePathField, "Validation truststore");
            PadesOperations.PadesValidationResult validation = PadesOperations.validate(
                    readBoundedPdf(source), trustStore, trustPassword, padesCrlEvidence);
            lastValidation = validation;
            padesResultArea.setText(validation.summary()
                    + "\nReport XML is available internally only; it can contain certificate PII.");
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PAdES Validate")
                        .detail("Input PDF", source.getName())
                        .detail("Truststore", trustStore == null ? "Not configured" : trustStore.getName())
                        .detail("Local CRL evidence", String.valueOf(validation.localCrlCount()))
                        .detail("Revocation", validation.localCrlCount() == 0 ? "NOT EVALUATED (offline)" : "Local CRL evidence supplied")
                        .status("PAdES DSS validation completed").build());
            }
        } catch (Exception error) {
            showError("PAdES validation", error.getMessage());
        } finally {
            Arrays.fill(trustPassword, '\0');
        }
    }

    /** Explicit user action because DSS reports can contain certificate PII. */
    @FXML
    private void handleSaveValidationReports() {
        try {
            if (lastValidation == null) throw new IllegalArgumentException("Validate a PDF before exporting DSS reports");
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
                    .status("PAdES DSS reports exported by explicit user action").build());
            padesResultArea.appendText("\n\nDSS reports saved to: " + directory.getAbsolutePath()
                    + "\nWarning: the XML files may contain certificate PII.");
        } catch (Exception error) {
            showError("PAdES reports export", error.getMessage());
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
        if (field == null || field.getText().isBlank()) throw new IllegalArgumentException(label + " is required");
        File file = new File(field.getText().trim());
        if (!file.isFile()) throw new IllegalArgumentException(label + " does not exist or is not a file");
        return file;
    }

    private String selectedTokenAlias() {
        if (padesPkcs11AliasCombo == null || padesPkcs11AliasCombo.getValue() == null
                || padesPkcs11AliasCombo.getValue().isBlank()) {
            throw new IllegalArgumentException("Load and select a PKCS#11 token signing key first");
        }
        return padesPkcs11AliasCombo.getValue();
    }

    private PadesOperations.VisibleSignatureOptions visibleSignatureOptions() {
        if (padesVisibleSignatureCheck == null || !padesVisibleSignatureCheck.isSelected()) return null;
        try {
            return new PadesOperations.VisibleSignatureOptions(
                    Integer.parseInt(text(padesVisiblePageField, "Visible signature page")),
                    Float.parseFloat(text(padesVisibleXField, "Visible signature X")),
                    Float.parseFloat(text(padesVisibleYField, "Visible signature Y")),
                    Float.parseFloat(text(padesVisibleWidthField, "Visible signature width")),
                    Float.parseFloat(text(padesVisibleHeightField, "Visible signature height")),
                    text(padesVisibleTextField, "Visible signature text"));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Visible signature coordinates must be numeric", invalid);
        }
    }

    private static String text(TextField field, String label) {
        if (field == null || field.getText() == null || field.getText().isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return field.getText().trim();
    }

    private static void setVisibleManaged(javafx.scene.Node node, boolean value) {
        if (node == null) return;
        node.setVisible(value);
        node.setManaged(value);
    }

    private static File requireNewFile(TextField field, String label) {
        if (field == null || field.getText().isBlank()) throw new IllegalArgumentException(label + " is required");
        File file = new File(field.getText().trim());
        if (Files.exists(file.toPath())) throw new IllegalArgumentException(label + " already exists; choose a new destination");
        return file;
    }

    private static File optionalFile(TextField field, String label) {
        if (field == null || field.getText().isBlank()) return null;
        File file = new File(field.getText().trim());
        if (!file.isFile()) throw new IllegalArgumentException(label + " does not exist or is not a file");
        return file;
    }

    private static void writeNewReport(File directory, String name, String content) throws Exception {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("DSS did not produce " + name);
        java.nio.file.Path target = directory.toPath().resolve(name);
        Files.writeString(target, content, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
    }

    private static byte[] readBoundedPdf(File file) throws Exception {
        long size = Files.size(file.toPath());
        if (size > MAX_PDF_BYTES) throw new IllegalArgumentException("PDF exceeds the 64 MiB laboratory limit");
        return Files.readAllBytes(file.toPath());
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
                    .status(profile + " PDF signed").build());
        }
    }

    private void showError(String title, String message) {
        if (statusReporter != null) statusReporter.showError(title, message);
    }
}
