package com.cryptocarver.ui;

import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.service.KeyCertificateFormatService;
import com.cryptocarver.service.KeyCertificateFormatService.DetectionResult;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.scene.layout.GridPane;

public class KeyCertificateWorkbenchController {

    @FXML
    public TextArea workbenchInputArea;
    @FXML
    public PasswordField workbenchPasswordField;
    @FXML
    public Label lblFormat;
    @FXML
    public Label lblAlgorithm;
    @FXML
    public Label lblHasPrivate;
    @FXML
    public Label lblSubject;
    @FXML
    public Label lblKeySize;
    @FXML
    public Label lblFingerprint;
    @FXML
    public Label lblValidity;
    @FXML
    public ComboBox<String> convertToCombo;
    @FXML
    public TextArea workbenchOutputArea;
    @FXML
    public TextField validationSecondaryInput;
    @FXML
    public ComboBox<String> storeTypeCombo;
    @FXML
    public TableView<KeyCertificateFormatService.KeystoreEntrySummary> keystoreTable;
    @FXML
    public TableColumn<KeyCertificateFormatService.KeystoreEntrySummary, String> colAlias;
    @FXML
    public TableColumn<KeyCertificateFormatService.KeystoreEntrySummary, String> colType;
    @FXML
    public TableColumn<KeyCertificateFormatService.KeystoreEntrySummary, String> colAlgorithm;
    @FXML
    public TableColumn<KeyCertificateFormatService.KeystoreEntrySummary, String> colSubject;
    @FXML
    public TableColumn<KeyCertificateFormatService.KeystoreEntrySummary, String> colExpiration;
    @FXML
    public GridPane singleItemGrid;

    private StatusReporter statusReporter;
    private KeyCertificateFormatService formatService;

    // Cache the last parsed result so conversion uses it
    private DetectionResult lastParsedResult;
    private OperationDetail.Classification currentOutputClassification = OperationDetail.Classification.PUBLIC;

    public void setStatusReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    @FXML
    public void initialize() {
        formatService = new KeyCertificateFormatService();
        convertToCombo.getItems().addAll(
                "PEM",
                "DER (Hex)",
                "DER (Base64)",
                "JWK",
                "OpenSSH Public Key",
                "PKCS12 Summary",
                "PEM Cert",
                "PEM Chain",
                "PEM Public",
                "PEM Private"
        );
        convertToCombo.setValue("PEM");

        if (storeTypeCombo != null) {
            storeTypeCombo.getItems().addAll("Auto", "PKCS12", "JKS", "BKS");
            storeTypeCombo.setValue("Auto");
        } else {
            System.err.println("CRITICAL: storeTypeCombo is null in initialize!");
        }

        if (colAlias != null) {
            colAlias.setCellValueFactory(new PropertyValueFactory<>("alias"));
            colType.setCellValueFactory(new PropertyValueFactory<>("entryType"));
            colAlgorithm.setCellValueFactory(new PropertyValueFactory<>("algorithm"));
            colSubject.setCellValueFactory(new PropertyValueFactory<>("subjectInfo"));
            colExpiration.setCellValueFactory(new PropertyValueFactory<>("expiration"));
        }

        if (keystoreTable != null) {
            keystoreTable.setVisible(false);
            keystoreTable.setManaged(false);
        }
    }

    @FXML
    private void handleLoadFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Key or Certificate File");
        File file = fileChooser.showOpenDialog(workbenchInputArea.getScene().getWindow());
        if (file != null) {
            if (!canLoadFile(file)) {
                if (statusReporter != null) {
                    statusReporter.showError("Load Error", "File exceeds 10MB limit for the workbench.");
                }
                return;
            }
            try {
                byte[] content = Files.readAllBytes(file.toPath());
                String str = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                if (str.contains("BEGIN") || str.trim().startsWith("{") || str.trim().startsWith("ssh-")) {
                    workbenchInputArea.setText(str);
                } else {
                    workbenchInputArea.setText(com.cryptocarver.util.DataConverter.bytesToHex(content));
                }
                handleParse(null);
            } catch (Exception e) {
                if (statusReporter != null) {
                    statusReporter.showError("Load Error", "Failed to load file: " + e.getMessage());
                }
            }
        }
    }

    @FXML
    private void handleLoadFromShelf(ActionEvent event) {
        try {
            List<ClipboardEntry> entries = ClipboardShelfManager.getInstance().getEntries();
            if (entries.isEmpty()) {
                if (statusReporter != null) {
                    statusReporter.showError("Clipboard Shelf", "The shelf is empty.");
                }
                return;
            }
            java.util.Map<String, ClipboardEntry> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                ClipboardEntry e = entries.get(i);
                map.put(String.format("%d. %s (%s)", i+1, e.getLabel(), e.getFormat()), e);
            }
            java.util.List<String> choices = new java.util.ArrayList<>(map.keySet());

            javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(choices.get(0), choices);
            dialog.setTitle("Load from Shelf");
            dialog.setHeaderText("Select an entry to load into the workbench:");
            dialog.setContentText("Entry:");

            java.util.Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                ClipboardEntry selected = map.get(result.get());

                if (!canLoadFromShelf(selected)) {
                    if (statusReporter != null) {
                        statusReporter.showError("Security Policy", "Cannot load SECRET material in restricted visibility mode.");
                    }
                    return;
                }

                workbenchInputArea.setText(selected.getValue());
                if (statusReporter != null) {
                    statusReporter.updateStatus("Loaded item from Shelf: " + selected.getLabel());
                }
                handleParse(null);
            }
        } catch (Exception e) {
             if (statusReporter != null) {
                statusReporter.showError("Load Error", "Failed to load from shelf.");
             }
        }
    }

    // Visible for testing
    boolean canLoadFile(File file) {
        return file.length() <= 10 * 1024 * 1024;
    }

    // Visible for testing
    boolean canLoadFromShelf(ClipboardEntry selected) {
        if (selected.getClassification() == com.cryptocarver.model.OperationDetail.Classification.SECRET
            && com.cryptocarver.model.AppSettings.getInstance().getSecretVisibility() != com.cryptocarver.model.SecretVisibility.FULL_LAB) {
            return false;
        }
        return true;
    }

    private byte[] getRawBytes(String input) {
        if (input.contains("BEGIN") || input.startsWith("{") || input.startsWith("ssh-")) {
            return input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            if (input.matches("^[0-9a-fA-F\\s]+$")) {
                return com.cryptocarver.util.DataConverter.hexToBytes(input.replaceAll("\\s", ""));
            } else {
                return Base64.getDecoder().decode(input.replaceAll("\\s", ""));
            }
        }
    }

    private void clearUI() {
        lblFormat.setText("Unknown");
        lblAlgorithm.setText("N/A");
        lblHasPrivate.setText("No");
        lblSubject.setText("N/A");
        lblKeySize.setText("N/A");
        lblFingerprint.setText("N/A");
        lblValidity.setText("N/A");
        lastParsedResult = null;
        currentOutputClassification = OperationDetail.Classification.PUBLIC;
    }

    @FXML
    private void handleParse(ActionEvent event) {
        String inputStr = workbenchInputArea.getText().trim();
        if (inputStr.isEmpty()) return;

        clearUI();

        byte[] input;
        try {
            input = getRawBytes(inputStr);
        } catch (Exception e) {
            lblFormat.setText("Invalid Input");
            return;
        }

        char[] password = null;
        try {
            if (!workbenchPasswordField.getText().isEmpty()) {
                password = workbenchPasswordField.getText().toCharArray();
            }

            String explicitStoreType = storeTypeCombo.getValue();
            DetectionResult result;

            if (!"Auto".equals(explicitStoreType)) {
                result = formatService.inspectKeystore(input, explicitStoreType, password);
            } else {
                result = formatService.detect(input, password);
                if (result.type == KeyCertificateFormatService.FormatType.PKCS12) {
                    // Re-parse with inspectKeystore to get table entries
                    result = formatService.inspectKeystore(input, "PKCS12", password);
                }
            }

            lastParsedResult = result;

            if (result.keystoreEntries != null) {
                // It's a keystore
                singleItemGrid.setVisible(false);
                singleItemGrid.setManaged(false);
                keystoreTable.setVisible(true);
                keystoreTable.setManaged(true);
                keystoreTable.setItems(FXCollections.observableArrayList(result.keystoreEntries));
                lblFormat.setText(result.formatString != null ? result.formatString : "Keystore");
            } else {
                singleItemGrid.setVisible(true);
                singleItemGrid.setManaged(true);
                keystoreTable.setVisible(false);
                keystoreTable.setManaged(false);

                lblFormat.setText(result.formatString != null ? result.formatString : "Unknown");
                lblAlgorithm.setText(result.algorithm != null ? result.algorithm : "N/A");
                lblHasPrivate.setText(result.hasPrivateKey ? (result.isEncrypted ? "Yes (Encrypted)" : "Yes") : "No");
                lblSubject.setText(result.subject != null ? result.subject : "N/A");
                lblKeySize.setText(result.keySize > 0 ? String.valueOf(result.keySize) + " bits" : "N/A");
                lblFingerprint.setText(result.sha256Fingerprint != null ? result.sha256Fingerprint : "N/A");
                lblValidity.setText((result.notBefore != null && result.notAfter != null) ? (result.notBefore + " to " + result.notAfter) : "N/A");
            }

            if (statusReporter != null) {
                statusReporter.updateStatus("Parsed as " + lblFormat.getText());
            }
        } catch (Exception e) {
            lblFormat.setText("Error: " + e.getMessage());
        } finally {
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
            workbenchPasswordField.clear();
        }
    }

    private void resetLabels() {
        lblFormat.setText("Unknown / Unrecognized");
        lblAlgorithm.setText("N/A");
        lblHasPrivate.setText("No");
        lblSubject.setText("N/A");
    }

    @FXML
    private void handleConvert(ActionEvent event) {
        if (lastParsedResult == null) {
            handleParse(null);
            if (lastParsedResult == null) return;
        }

        String targetFormat = convertToCombo.getValue();
        char[] password = null;
        try {
            if (keystoreTable.isVisible()) {
                // Keystore extraction
                KeyCertificateFormatService.KeystoreEntrySummary selected = keystoreTable.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    throw new Exception("Please select an alias from the table first.");
                }

                String storeType = lastParsedResult.type == KeyCertificateFormatService.FormatType.PKCS12 ? "PKCS12" :
                                   lastParsedResult.type == KeyCertificateFormatService.FormatType.JKS ? "JKS" :
                                   lastParsedResult.type == KeyCertificateFormatService.FormatType.BKS ? "BKS" : "PKCS12";

                if (!workbenchPasswordField.getText().isEmpty()) {
                    password = workbenchPasswordField.getText().toCharArray();
                }

                String converted = formatService.extractFromKeystore(
                        lastParsedResult.rawBytes,
                        storeType,
                        password,
                        selected.getAlias(),
                        targetFormat,
                        AppSettings.getInstance().getSecretVisibility());

                workbenchOutputArea.setText(converted);

                // Do not leak alias data unnecessarily, but it is useful for history
                String safeOpDetail = "Extracted " + targetFormat + " for alias: " + selected.getAlias();

                List<OperationDetail> details = new ArrayList<>();
                if (targetFormat.contains("Private")) {
                    currentOutputClassification = OperationDetail.Classification.SECRET;
                    details.add(new OperationDetail("Export Private Key", safeOpDetail, OperationDetail.Classification.SECRET, false, null));
                } else {
                    currentOutputClassification = OperationDetail.Classification.PUBLIC;
                    details.add(new OperationDetail("Export Public Material", safeOpDetail, OperationDetail.Classification.PUBLIC, false, null));
                }

                OperationResult opRes = OperationResult.forOperation("Format Workbench: Keystore Export")
                    .input(new byte[0])
                    .output(new byte[0])
                    .details(details)
                    .status(safeOpDetail)
                    .build();

                if (statusReporter != null) {
                    statusReporter.publish(opRes);
                }

                return;
            }

            // Single item conversion
            if (lastParsedResult.type == KeyCertificateFormatService.FormatType.PKCS12) {
                password = workbenchPasswordField.getText().toCharArray();
                if ("PKCS12 Summary".equals(targetFormat)) {
                    String summary = formatService.getChainSummary(lastParsedResult.rawBytes, password);
                    workbenchOutputArea.setText(summary);
                    currentOutputClassification = OperationDetail.Classification.PUBLIC;
                    publishResult("PKCS12 Chain Summary", summary);
                    return;
                } else {
                    throw new Exception("Use 'PKCS12 Summary' or explicitly load as Keystore to extract aliases.");
                }
            }

            if ("PKCS12 Summary".equals(targetFormat) || targetFormat.contains("Chain") || targetFormat.contains("Cert") || targetFormat.contains("Public") || targetFormat.contains("Private")) {
                throw new Exception("Target format " + targetFormat + " is only for Keystore extractions.");
            }

            String converted = formatService.convert(lastParsedResult, targetFormat, AppSettings.getInstance().getSecretVisibility());
            workbenchOutputArea.setText(converted);
            currentOutputClassification = (lastParsedResult != null && lastParsedResult.hasPrivateKey) ? OperationDetail.Classification.SECRET : OperationDetail.Classification.PUBLIC;
            publishResult("Convert to " + targetFormat, converted);

        } catch (Exception e) {
            if (statusReporter != null) {
                statusReporter.showError("Conversion Error", e.getMessage());
            }
        } finally {
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
                workbenchPasswordField.setText("");
            }
        }
    }

    @FXML
    private void handleValidate(ActionEvent event) {
        String secondaryStr = validationSecondaryInput.getText().trim();
        if (secondaryStr.isEmpty() || lastParsedResult == null) {
            if (statusReporter != null) {
                statusReporter.showError("Validation Error", "Need both primary input and secondary input to validate pair.");
            }
            return;
        }

        byte[] secondaryBytes = getRawBytes(secondaryStr);
        boolean isValid = formatService.validatePair(lastParsedResult.rawBytes, secondaryBytes);

        if (isValid) {
            workbenchOutputArea.setText("VALID PAIR: The public key (or certificate) matches the private key.");
            publishResult("Validate Key Pair", "Valid Match");
            if (statusReporter != null) {
                statusReporter.showInfo("Validation", "Keys match successfully.");
            }
        } else {
            workbenchOutputArea.setText("INVALID PAIR: The provided keys do not match, or could not be processed as a pair.");
            publishResult("Validate Key Pair", "Invalid / No Match");
            if (statusReporter != null) {
                statusReporter.showError("Validation", "Keys do NOT match or error parsing inputs.");
            }
        }
    }

    @FXML
    private void handleCopyOutput(ActionEvent event) {
        String text = workbenchOutputArea.getText();
        if (text == null || text.trim().isEmpty()) return;

        boolean isSecret = (currentOutputClassification == OperationDetail.Classification.SECRET);
        if (isSecret) {
            com.cryptocarver.model.SecretVisibility vis = AppSettings.getInstance().getSecretVisibility();
            if (vis != com.cryptocarver.model.SecretVisibility.FULL_LAB) {
                if (statusReporter != null) {
                    statusReporter.showError("Security Policy", "Cannot copy SECRET material while environment is " + vis);
                }
                return;
            }
        }

        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        if (statusReporter != null) {
            statusReporter.updateStatus("Output copied to system clipboard");
        }
    }

    @FXML
    private void handleSendToShelf(ActionEvent event) {
        String text = workbenchOutputArea.getText();
        if (text == null || text.trim().isEmpty()) return;

        boolean isSecret = (currentOutputClassification == OperationDetail.Classification.SECRET);
        OperationDetail.Classification classification = currentOutputClassification;

        if (isSecret) {
            com.cryptocarver.model.SecretVisibility vis = AppSettings.getInstance().getSecretVisibility();
            if (vis != com.cryptocarver.model.SecretVisibility.FULL_LAB) {
                if (statusReporter != null) {
                    statusReporter.showError("Security Policy", "Cannot copy SECRET material to Shelf while environment is " + vis);
                }
                return;
            }
        }

        try {
            ClipboardEntry entry = new ClipboardEntry(
                "Workbench Output",
                text,
                ClipboardEntry.Format.TEXT,
                classification,
                "Key & Certificate Format Workbench"
            );
            ClipboardShelfManager.getInstance().addEntry(entry);
            if (statusReporter != null) {
                statusReporter.updateStatus("Sent to Clipboard Shelf");
            }
        } catch (Exception e) {
            if (statusReporter != null) {
                statusReporter.showError("Clipboard Shelf Error", e.getMessage());
            }
        }
    }

    private void publishResult(String operationSuffix, String output) {
        if (statusReporter != null) {
            String inputStr = workbenchInputArea.getText().trim();
            // Don't leak private inputs if REDACTED or MASKED
            if (lastParsedResult != null && lastParsedResult.hasPrivateKey) {
                if (AppSettings.getInstance().getSecretVisibility() == com.cryptocarver.model.SecretVisibility.REDACTED) {
                    inputStr = "*** REDACTED ***";
                    if (!operationSuffix.contains("Summary") && !operationSuffix.contains("Validate")) {
                        output = "*** REDACTED ***";
                    }
                } else if (AppSettings.getInstance().getSecretVisibility() == com.cryptocarver.model.SecretVisibility.MASKED) {
                    inputStr = "*** MASKED ***";
                    if (!operationSuffix.contains("Summary") && !operationSuffix.contains("Validate")) {
                        output = "*** MASKED ***";
                    }
                }
            }

            List<OperationDetail> details = new ArrayList<>();
            details.add(new OperationDetail("Detected Format", lblFormat.getText(), OperationDetail.Classification.PUBLIC, false, null));
            details.add(new OperationDetail("Algorithm", lblAlgorithm.getText(), OperationDetail.Classification.PUBLIC, false, null));

            OperationResult result = OperationResult.forOperation("Format Workbench: " + operationSuffix)
                .input(inputStr.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .output(output.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .details(details)
                .status("Operation completed: " + operationSuffix)
                .build();
            statusReporter.publish(result);
        }
    }
}
