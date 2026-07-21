package com.cryptoforge.ui;

import com.cryptoforge.crypto.CmsInspectionReport;
import com.cryptoforge.crypto.CmsInspector;
import com.cryptoforge.model.OperationDetail;
import com.cryptoforge.model.OperationRegistry;
import com.cryptoforge.model.OperationResult;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

public class CmsInspectorController {

    @FXML private Label cmsFilePathLabel;
    @FXML private TextArea cmsInputArea;
    @FXML private CheckBox cmsDetachedCheck;
    @FXML private HBox cmsDetachedFileBox;
    @FXML private Label cmsContentPathLabel;
    @FXML private TextArea cmsContentArea;
    @FXML private Label truststorePathLabel;
    @FXML private PasswordField truststorePasswordField;
    @FXML private TextArea cmsReportArea;

    private byte[] cmsFileBytes;
    private byte[] contentFileBytes;
    private File truststoreFile;
    private final CmsInspector inspector = new CmsInspector();
    private StatusReporter statusReporter;

    @FXML
    public void initialize() {
    }

    public void init(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    private com.cryptoforge.crypto.CmsInspectionReport currentReport;

    @FXML
    void handleLoadCmsFile(ActionEvent event) {
        Window window = cmsInputArea.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select CMS File (DER/PEM)");
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            if (file.length() > 16 * 1024 * 1024) {
                if (statusReporter != null) statusReporter.showError("File Error", "CMS file exceeds 16 MiB limit");
                return;
            }
            try {
                cmsFileBytes = Files.readAllBytes(file.toPath());
                cmsFilePathLabel.setText(file.getName());
                cmsInputArea.setText(file.getName() + " loaded (" + cmsFileBytes.length + " bytes)");
                cmsInputArea.setEditable(false);
            } catch (Exception e) {
                if (statusReporter != null) statusReporter.showError("File Error", "Failed to load CMS file: " + e.getMessage());
            }
        }
    }

    @FXML
    void handleDetachedToggle(ActionEvent event) {
        boolean selected = cmsDetachedCheck.isSelected();
        cmsDetachedFileBox.setVisible(selected);
        cmsDetachedFileBox.setManaged(selected);
        cmsContentArea.setVisible(selected);
        cmsContentArea.setManaged(selected);
    }

    @FXML
    void handleLoadContentFile(ActionEvent event) {
        Window window = cmsContentArea.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Original Detached Content");
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            if (file.length() > 16 * 1024 * 1024) {
                if (statusReporter != null) statusReporter.showError("File Error", "Content file exceeds 16 MiB limit");
                return;
            }
            try {
                contentFileBytes = Files.readAllBytes(file.toPath());
                cmsContentPathLabel.setText(file.getName());
                cmsContentArea.setText(file.getName() + " loaded (" + contentFileBytes.length + " bytes)");
                cmsContentArea.setEditable(false);
            } catch (Exception e) {
                if (statusReporter != null) statusReporter.showError("File Error", "Failed to load content file: " + e.getMessage());
            }
        }
    }

    @FXML
    void handleSelectTruststore(ActionEvent event) {
        Window window = truststorePasswordField.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Truststore (JKS/PKCS12)");
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            truststoreFile = file;
            truststorePathLabel.setText(file.getName());
        }
    }

    @FXML
    void handleInspectCms(ActionEvent event) {
        doInspection("Inspect CMS");
    }

    @FXML
    void handleValidateSignedData(ActionEvent event) {
        doInspection("Validate SignedData");
    }

    private void doInspection(String actionName) {
        try {
            byte[] inputBytes = cmsFileBytes;
            if (inputBytes == null) {
                String text = cmsInputArea.getText().trim();
                if (!text.isEmpty()) {
                    inputBytes = text.getBytes();
                } else {
                    if (statusReporter != null) statusReporter.showError("Input Error", "Please provide CMS input (file or text)");
                    return;
                }
            }

            byte[] contentBytes = null;
            if (cmsDetachedCheck.isSelected()) {
                contentBytes = contentFileBytes;
                if (contentBytes == null) {
                    String text = cmsContentArea.getText().trim();
                    if (!text.isEmpty()) {
                        contentBytes = text.getBytes();
                    }
                }
            }

            KeyStore ts = null;
            if (truststoreFile != null) {
                ts = KeyStore.getInstance(truststoreFile.getName().endsWith(".p12") ? "PKCS12" : KeyStore.getDefaultType());
                char[] pass = truststorePasswordField.getText().toCharArray();
                try {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(truststoreFile)) {
                        ts.load(fis, pass);
                    }
                } finally {
                    Arrays.fill(pass, '\0');
                }
            }

            CmsInspectionReport report = inspector.inspect(inputBytes, contentBytes, ts);
            this.currentReport = report;

            String reportText = formatReport(report);
            cmsReportArea.setText(reportText);

            if (statusReporter != null) {
                OperationResult result = OperationResult.forOperation("CMS_INSPECTOR")
                    .input(inputBytes)
                    .output(reportText.getBytes())
                    .detail(new OperationDetail("Type", report.getType().name(), OperationDetail.Classification.PUBLIC, false, null))
                    .detail(new OperationDetail("Content State", report.getContentState().name(), OperationDetail.Classification.PUBLIC, false, null))
                    .status("Successfully inspected " + report.getType() + " - " + report.getContentState())
                    .build();
                statusReporter.publish(result);
            }

        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Inspection Error", "CMS Inspection failed: " + e.getMessage());
            cmsReportArea.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    void handleExportReport(ActionEvent event) {
        if (currentReport == null) {
            if (statusReporter != null) statusReporter.showError("Export Error", "No report to export");
            return;
        }

        Window window = cmsReportArea.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Report");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JSON Files", "*.json"),
            new FileChooser.ExtensionFilter("Markdown Files", "*.md")
        );
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            try {
                String content;
                if (file.getName().toLowerCase().endsWith(".json")) {
                    content = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(currentReport);
                } else {
                    content = formatReport(currentReport); // formatReport already outputs a Markdown-like structure
                }
                Files.write(file.toPath(), content.getBytes());
                if (statusReporter != null) statusReporter.updateStatus("Report exported to " + file.getName());
            } catch (Exception e) {
                if (statusReporter != null) statusReporter.showError("Export Error", "Failed to export report: " + e.getMessage());
            }
        }
    }

    private String formatReport(CmsInspectionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("== CMS Inspection Report ==\n");
        sb.append("Type: ").append(report.getType()).append(" (").append(report.getContentOid()).append(")\n");
        sb.append("Content State: ").append(report.getContentState());
        if (report.getContentSize() != null) {
            sb.append(" (").append(report.getContentSize()).append(" bytes)");
        }
        sb.append("\n");
        if (report.getContentEncryptionAlgorithm() != null) {
            sb.append("Encryption Alg: ").append(report.getContentEncryptionAlgorithm()).append("\n");
        }
        sb.append("\n");

        if (!report.getValidationSteps().isEmpty()) {
            sb.append("== Validation Steps ==\n");
            for (CmsInspectionReport.ValidationStep step : report.getValidationSteps()) {
                sb.append(String.format("- [%-13s] %s: %s\n", step.getState(), step.getStepName(), step.getDetails()));
            }
            sb.append("\n");
        }

        if (!report.getSigners().isEmpty()) {
            sb.append("== Signers ==\n");
            for (int i = 0; i < report.getSigners().size(); i++) {
                CmsInspectionReport.SignerInfoSummary s = report.getSigners().get(i);
                sb.append("Signer ").append(i + 1).append(":\n");
                sb.append("  SID: ").append(s.getSid()).append("\n");
                sb.append("  Digest Alg: ").append(s.getDigestAlg()).append("\n");
                sb.append("  Signature Valid: ").append(s.getSignatureValid()).append("\n");
                sb.append("  Cert Valid: ").append(s.getCertificateValid()).append("\n");
                if (!s.getSignedAttributes().isEmpty()) {
                    sb.append("  Signed Attributes:\n");
                    s.getSignedAttributes().forEach((k, v) -> sb.append("    ").append(k).append(": ").append(v).append("\n"));
                }
                if (!s.getUnsignedAttributes().isEmpty()) {
                    sb.append("  Unsigned Attributes:\n");
                    s.getUnsignedAttributes().forEach((k, v) -> sb.append("    ").append(k).append(": ").append(v).append("\n"));
                }
            }
            sb.append("\n");
        }

        if (!report.getRecipients().isEmpty()) {
            sb.append("== Recipients ==\n");
            for (CmsInspectionReport.RecipientSummary r : report.getRecipients()) {
                sb.append("Recipient: ").append(r.getSid()).append(" (").append(r.getType()).append(", Alg: ").append(r.getAlgorithm()).append(")\n");
            }
            sb.append("\n");
        }

        if (!report.getCertificates().isEmpty()) {
            sb.append("== Certificates Included ==\n");
            for (CmsInspectionReport.CertificateSummary c : report.getCertificates()) {
                sb.append("Subject: ").append(c.getSubject()).append("\n");
                sb.append("Issuer:  ").append(c.getIssuer()).append("\n");
                sb.append("Serial:  ").append(c.getSerial()).append("\n");
                sb.append("Valid:   ").append(c.getNotBefore()).append(" to ").append(c.getNotAfter()).append("\n");
                sb.append("Alg:     ").append(c.getKeyAlgorithm()).append("\n");
                sb.append("Fingerpt:").append(c.getSha256Fingerprint()).append("\n");
                if (!c.getKeyUsages().isEmpty()) {
                    sb.append("Usages:  ").append(String.join(", ", c.getKeyUsages())).append("\n");
                }
                sb.append("\n");
            }
        }

        if (!report.getWarnings().isEmpty()) {
            sb.append("== Warnings ==\n");
            for (String w : report.getWarnings()) {
                sb.append("- ").append(w).append("\n");
            }
        }

        return sb.toString();
    }
}
