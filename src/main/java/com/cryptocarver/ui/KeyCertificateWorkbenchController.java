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
    private String lastParsedInput;
    private ShelfOutputState shelfOutputState = ShelfOutputState.NONE;

    private enum ShelfOutputState {
        NONE,
        CONVERTED_MATERIAL,
        SUMMARY,
        VALIDATION,
        ERROR
    }

    private record ShelfMaterial(String text, boolean privateMaterial, String algorithm) { }

    private record ShelfResolution(ShelfMaterial material, String blockedMessage) {
        static ShelfResolution accepted(ShelfMaterial material) {
            return new ShelfResolution(material, null);
        }

        static ShelfResolution blocked(String message) {
            return new ShelfResolution(null, message);
        }
    }

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
        if (selected == null) return false;
        if (selected.getClassification() == com.cryptocarver.model.OperationDetail.Classification.SECRET
            && com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            return false;
        }
        return true;
    }

    /** Loads a validated session-only private key without publishing its value. */
    void loadSessionOnlyPrivateKey(String value) {
        if (com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile()
                != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            return;
        }
        if (value == null || value.isBlank()) return;
        workbenchInputArea.setText(value);
        workbenchOutputArea.clear();
        handleParse(null);
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
        lastParsedInput = null;
        shelfOutputState = ShelfOutputState.NONE;
        currentOutputClassification = OperationDetail.Classification.PUBLIC;
    }

    @FXML
    private void handleParse(ActionEvent event) {
        String inputStr = workbenchInputArea.getText().trim();
        if (inputStr.isEmpty()) {
            clearUI();
            workbenchOutputArea.clear();
            return;
        }

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
            lastParsedInput = inputStr;

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
            lastParsedResult = null;
            lastParsedInput = null;
            shelfOutputState = ShelfOutputState.ERROR;
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

        shelfOutputState = ShelfOutputState.NONE;

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
                        AppSettings.getInstance().getSecretVisibilityProfile());

                workbenchOutputArea.setText(converted);
                shelfOutputState = isShelfMaterialOutput(converted)
                        ? ShelfOutputState.CONVERTED_MATERIAL : ShelfOutputState.SUMMARY;

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
                    shelfOutputState = ShelfOutputState.SUMMARY;
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

            String converted = formatService.convert(lastParsedResult, targetFormat, AppSettings.getInstance().getSecretVisibilityProfile());
            workbenchOutputArea.setText(converted);
            shelfOutputState = isShelfMaterialOutput(converted)
                    ? ShelfOutputState.CONVERTED_MATERIAL : ShelfOutputState.SUMMARY;
            currentOutputClassification = (lastParsedResult != null && lastParsedResult.hasPrivateKey) ? OperationDetail.Classification.SECRET : OperationDetail.Classification.PUBLIC;
            publishResult("Convert to " + targetFormat, converted);

        } catch (Exception e) {
            shelfOutputState = ShelfOutputState.ERROR;
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
        shelfOutputState = ShelfOutputState.VALIDATION;
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
            com.cryptocarver.model.SecretVisibilityProfile vis = AppSettings.getInstance().getSecretVisibilityProfile();
            if (vis != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
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
        sendCurrentMaterialToShelf();
    }

    /** Shared by the Workbench button and the shell's global Add to Shelf action. */
    void sendCurrentMaterialToShelf() {
        ShelfResolution resolution = resolveShelfMaterial();
        if (resolution.material() == null) {
            if (statusReporter != null) statusReporter.updateStatus(resolution.blockedMessage());
            return;
        }

        ShelfMaterial material = resolution.material();
        if (material.privateMaterial()) {
            if (AppSettings.getInstance().getSecretVisibilityProfile()
                    != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
                if (statusReporter != null) {
                    statusReporter.updateStatus("Action blocked: private key material requires FULL_LAB.");
                }
                return;
            }
            try {
                ClipboardEntry sessionEntry = ClipboardShelfManager.getInstance().addSessionOnlyPrivateKey(
                        material.text(), "Key & Certificate Format Workbench", material.algorithm());
                if (sessionEntry == null) {
                    if (statusReporter != null) {
                        statusReporter.updateStatus("Action blocked: private key material requires FULL_LAB.");
                    }
                    return;
                }
                revealShelfEntry(sessionEntry);
                if (statusReporter != null) {
                    statusReporter.updateStatus("Added private key to Clipboard Shelf (session only).");
                }
            } catch (Exception e) {
                if (statusReporter != null) {
                    statusReporter.updateStatus("Action blocked: unable to add private key to Clipboard Shelf.");
                }
            }
            return;
        }

        try {
            ClipboardEntry entry = new ClipboardEntry(
                    "Workbench Public Key Material",
                    material.text(),
                    ClipboardEntry.Format.inferFormat(material.text()),
                    OperationDetail.Classification.PUBLIC,
                    "Key & Certificate Format Workbench",
                    material.algorithm());
            ClipboardShelfManager.getInstance().addEntry(entry);
            revealShelfEntry(entry);
            if (statusReporter != null) {
                statusReporter.updateStatus("Added public key material to Clipboard Shelf.");
            }
        } catch (Exception e) {
            if (statusReporter != null) {
                statusReporter.updateStatus("Action blocked: unable to add public key material to Clipboard Shelf.");
            }
        }
    }

    /** True only when the Workbench pane containing the detected material is visible. */
    boolean isShelfMaterialViewVisible() {
        if (workbenchInputArea == null) return false;
        for (javafx.scene.Node node = workbenchInputArea; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
            if (node instanceof javafx.scene.control.TitledPane pane && !pane.isExpanded()) return false;
        }
        return true;
    }

    private ShelfResolution resolveShelfMaterial() {
        String currentInput = workbenchInputArea == null ? "" : workbenchInputArea.getText().trim();
        String currentOutput = workbenchOutputArea == null ? "" : workbenchOutputArea.getText().trim();

        if (shelfOutputState == ShelfOutputState.VALIDATION) {
            return ShelfResolution.blocked("Action blocked: validation results cannot be sent to Clipboard Shelf.");
        }
        if (shelfOutputState == ShelfOutputState.ERROR) {
            return ShelfResolution.blocked("Action blocked: conversion errors cannot be sent to Clipboard Shelf.");
        }
        if (shelfOutputState == ShelfOutputState.SUMMARY) {
            return ShelfResolution.blocked("Action blocked: summaries and keystore containers must be explicitly converted before sending to Clipboard Shelf.");
        }

        if (shelfOutputState == ShelfOutputState.CONVERTED_MATERIAL && !currentOutput.isEmpty()) {
            if (!matchesLastParsedInput(currentInput)) {
                return ShelfResolution.blocked("Action blocked: re-run Detect & Parse before sending the current material to Clipboard Shelf.");
            }
            ShelfMaterial converted = detectShelfMaterial(currentOutput);
            if (converted != null) return ShelfResolution.accepted(converted);
            return ShelfResolution.blocked("Action blocked: conversion did not produce complete key material.");
        }

        if (lastParsedResult == null || !matchesLastParsedInput(currentInput)) {
            return ShelfResolution.blocked("Action blocked: detect and parse valid key material before sending to Clipboard Shelf.");
        }
        if (isKeystoreResult(lastParsedResult)) {
            return ShelfResolution.blocked("Action blocked: keystore containers must be explicitly converted before sending to Clipboard Shelf.");
        }
        if (lastParsedResult.validationError != null || !isSupportedDirectResult(lastParsedResult)) {
            return ShelfResolution.blocked("Action blocked: no valid complete key material was detected.");
        }

        ShelfMaterial inputMaterial = detectShelfMaterial(currentInput, lastParsedResult);
        if (inputMaterial == null) {
            return ShelfResolution.blocked("Action blocked: material is incomplete or was not detected as a standalone key or certificate.");
        }
        return ShelfResolution.accepted(inputMaterial);
    }

    private boolean matchesLastParsedInput(String currentInput) {
        return currentInput != null && !currentInput.isEmpty()
                && lastParsedInput != null && lastParsedInput.equals(currentInput);
    }

    private boolean isKeystoreResult(DetectionResult result) {
        return result != null
                && (result.keystoreEntries != null
                || result.type == KeyCertificateFormatService.FormatType.JKS
                || result.type == KeyCertificateFormatService.FormatType.PKCS12
                || result.type == KeyCertificateFormatService.FormatType.BKS);
    }

    private boolean isSupportedDirectResult(DetectionResult result) {
        if (result == null || result.parsedObject == null || result.isEncrypted || isKeystoreResult(result)) {
            return false;
        }
        return isPrivateType(result.type) || isPublicType(result.type);
    }

    private ShelfMaterial detectShelfMaterial(String text) {
        try {
            DetectionResult detected = formatService.detect(getRawBytes(text.trim()), null);
            return detectShelfMaterial(text, detected);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ShelfMaterial detectShelfMaterial(String text, DetectionResult detected) {
        if (text == null || text.isBlank() || isPrivateMaterialPlaceholder(text)
                || detected == null || detected.validationError != null
                || detected.parsedObject == null || isKeystoreResult(detected)) {
            return null;
        }
        if (isPrivateType(detected.type)) {
            if (!detected.hasPrivateKey || detected.isEncrypted) return null;
            if (detected.type == KeyCertificateFormatService.FormatType.PEM_PRIVATE_KEY
                    && !isCompletePrivateKeyMaterial(text)) return null;
            return new ShelfMaterial(text.trim(), true, detected.algorithm);
        }
        if (isPublicType(detected.type)) {
            return new ShelfMaterial(text.trim(), false, detected.algorithm);
        }
        return null;
    }

    private boolean isShelfMaterialOutput(String text) {
        return detectShelfMaterial(text) != null;
    }

    private boolean isPrivateType(KeyCertificateFormatService.FormatType type) {
        return type == KeyCertificateFormatService.FormatType.PEM_PRIVATE_KEY
                || type == KeyCertificateFormatService.FormatType.DER_PRIVATE_KEY
                || type == KeyCertificateFormatService.FormatType.JWK_PRIVATE;
    }

    private boolean isPublicType(KeyCertificateFormatService.FormatType type) {
        return type == KeyCertificateFormatService.FormatType.PEM_CERTIFICATE
                || type == KeyCertificateFormatService.FormatType.DER_CERTIFICATE
                || type == KeyCertificateFormatService.FormatType.PEM_PUBLIC_KEY
                || type == KeyCertificateFormatService.FormatType.DER_PUBLIC_KEY
                || type == KeyCertificateFormatService.FormatType.JWK_PUBLIC
                || type == KeyCertificateFormatService.FormatType.OPENSSH_PUBLIC_KEY;
    }

    private void revealShelfEntry(ClipboardEntry entry) {
        if (entry != null && statusReporter instanceof ModernMainController modern) {
            modern.revealShelfEntry(entry);
        }
    }

    private boolean isPrivateMaterialPlaceholder(String text) {
        if (text == null) return false;
        String normalized = text.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("PRIVATE KEY MATERIAL") && normalized.contains("NOT RECORDED");
    }

    private boolean isCompletePrivateKeyMaterial(String text) {
        if (text == null || isPrivateMaterialPlaceholder(text)) return false;
        String normalized = text.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("-----BEGIN ")
                && normalized.contains("PRIVATE KEY-----")
                && normalized.contains("-----END ")
                && normalized.contains("PRIVATE KEY-----");
    }

    private void publishResult(String operationSuffix, String output) {
        if (statusReporter != null) {
            String inputStr = workbenchInputArea.getText().trim();
            // Private-key material is never sent to history, inspector or
            // reports. FULL_LAB authorizes on-screen lab use, not recording.
            if (lastParsedResult != null && lastParsedResult.hasPrivateKey) {
                inputStr = "*** PRIVATE KEY INPUT — NOT RECORDED ***";
                if (currentOutputClassification == OperationDetail.Classification.SECRET
                        || (!operationSuffix.contains("Summary") && !operationSuffix.contains("Validate"))) {
                    output = "*** PRIVATE KEY MATERIAL — NOT RECORDED ***";
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
