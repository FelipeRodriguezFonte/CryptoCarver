package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessDefinitionCodec;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FileWritePolicy;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.Representation;
import javafx.application.Platform;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Tooltip;
import javafx.scene.control.MenuButton;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollPane;

/** Interactive MVP canvas: drag blocks, select them, connect selected blocks, save and run safe flows. */
public class ProcessDesignerController {

    @FXML private Pane workflowCanvas;
    @FXML private TextField processNameField;
    @FXML private Label selectedNodeLabel;
    @FXML VBox nodeNameFieldGroup;
    @FXML TextField nodeNameField;
    @FXML TextArea nodeValueArea;
    @FXML private TextField nodePathField;
    @FXML VBox consoleValueFieldGroup;
    @FXML VBox filePathFieldGroup;
    @FXML VBox charsetFieldGroup;
    @FXML VBox hashAlgorithmFieldGroup;
    @FXML private ComboBox<String> nodeCharsetCombo;
    @FXML private ComboBox<String> hashAlgorithmCombo;
    @FXML TextArea executionOutputArea;
    @FXML private Button connectSelectedButton;
    @FXML private Button deleteSelectedButton;
    @FXML private Button reverseConnectionButton;
    @FXML private Button reverseConnectionToolbarButton;
    @FXML private Button runProcessButton;
    @FXML private Button inspectorToggleButton;
    @FXML private SplitPane designerSplitPane;
    @FXML private ScrollPane designerWorkspace;
    @FXML private javafx.scene.layout.VBox nodeInspector;
    @FXML private VBox executionStatusContainer;
    @FXML private VBox fileModeFieldGroup;
    @FXML private Label fileModeLabel;
    @FXML ComboBox<String> fileModeCombo;
    @FXML private Label inputContractLabel;
    @FXML private Label outputContractLabel;
    @FXML TableView<ProcessExecutionRow> executionStatusTable;
    @FXML private TableColumn<ProcessExecutionRow, String> stepCol;
    @FXML private TableColumn<ProcessExecutionRow, String> stepNameCol;
    @FXML private TableColumn<ProcessExecutionRow, String> operationCol;
    @FXML private TableColumn<ProcessExecutionRow, String> inputCol;
    @FXML private TableColumn<ProcessExecutionRow, String> outputCol;
    @FXML private TableColumn<ProcessExecutionRow, String> statusCol;
    @FXML private TableColumn<ProcessExecutionRow, String> durationCol;


    @FXML VBox cryptoAlgorithmFieldGroup;
    @FXML ComboBox<String> cryptoAlgorithmCombo;
    @FXML VBox wssKeyTransportFieldGroup;
    @FXML ComboBox<String> wssKeyTransportCombo;
    @FXML VBox wssTimestampFieldGroup;
    @FXML CheckBox wssTimestampEnabledCheck;
    @FXML TextField wssTimestampMinutesField;
    @FXML CheckBox wssTimestampSignedCheck;
    @FXML VBox wssUsernameFieldGroup;
    @FXML Label wssUsernameLabel;
    @FXML TextField wssUsernameField;
    @FXML Label wssPasswordLabel;
    @FXML PasswordField wssPasswordField;
    @FXML VBox wssPasswordTypeFieldGroup;
    @FXML ComboBox<String> wssPasswordTypeCombo;
    @FXML VBox wssTokenAgeFieldGroup;
    @FXML TextField wssTokenAgeField;
    @FXML VBox keyFormatFieldGroup;
    @FXML ComboBox<String> keyFormatCombo;
    @FXML VBox manualKeyFieldGroup;
    @FXML PasswordField manualKeyField;
    @FXML VBox nonceFieldGroup;
    @FXML Label nonceLabel;
    @FXML TextField nonceField;
    @FXML CheckBox generateNonceCheck;
    @FXML VBox aadPortHintGroup;
    @FXML VBox portBindingsFieldGroup;
    @FXML Label portBindingsLabel;
    @FXML VBox cipherOutputFormatFieldGroup;
    @FXML ComboBox<String> cipherOutputFormatCombo;
    @FXML VBox keystorePathFieldGroup;
    @FXML TextField keystorePathField;
    @FXML VBox keystoreTypeFieldGroup;
    @FXML ComboBox<String> keystoreTypeCombo;
    @FXML VBox aliasFieldGroup;
    @FXML TextField aliasField;
    @FXML VBox keystorePasswordFieldGroup;
    @FXML PasswordField keystorePasswordField;
    @FXML VBox keyPasswordFieldGroup;
    @FXML PasswordField keyPasswordField;
    @FXML VBox materialPathFieldGroup;
    @FXML TextField materialPathField;
    @FXML VBox materialTypeFieldGroup;
    @FXML ComboBox<String> materialTypeCombo;
    @FXML Label secretsWarningLabel;
    @FXML VBox randomBytesFieldGroup;
    @FXML TextField randomBytesLengthField;
    @FXML MenuButton connectMenuButton;
    @FXML VBox keyMaterialFieldGroup;
    @FXML ComboBox<String> keySizeCombo;
    @FXML VBox symmetricKeyAlgorithmFieldGroup;
    @FXML ComboBox<String> symmetricKeyAlgorithmCombo;
    @FXML TextField kdfIterationsField;
    @FXML TextField kdfSaltField;

    private final List<ProcessDefinition.Node> nodes = new ArrayList<>();
    private final List<ProcessDefinition.Connection> connections = new ArrayList<>();
    private final Map<String, StackPane> views = new LinkedHashMap<>();
    /** Ordered pair: first selected block is the connection source, second is the destination. */
    final LinkedHashSet<String> selectedNodeIds = new LinkedHashSet<>();
    private ProcessDefinition.Node selected;
    private ProcessDefinition.Connection selectedConnection;
    private boolean inspectorVisible = true;
    private final ExpandedTextViewer expandedExecutionViewer = new ExpandedTextViewer();

    Label cryptoHelpLabel = new Label();
    Label cryptoWarningLabel = new Label();

    @FXML public void initialize() {
        nodeCharsetCombo.getItems().setAll("UTF-8", "ISO-8859-1", "IBM037");
        nodeCharsetCombo.setValue("UTF-8");
        hashAlgorithmCombo.getItems().setAll("SHA-256", "SHA-384", "SHA-512", "SHA-1", "MD5");
        hashAlgorithmCombo.setValue("SHA-256");
        configureExecutionStatusTable();

        if (cryptoAlgorithmCombo != null) {
            cryptoHelpLabel.setWrapText(true);
            cryptoHelpLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666; -fx-padding: 4 0 4 0;");
            cryptoWarningLabel.setWrapText(true);
            cryptoWarningLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #d32f2f; -fx-font-weight: bold; -fx-padding: 0 0 4 0;");
            cryptoWarningLabel.setVisible(false);
            cryptoWarningLabel.setManaged(false);

            if (cryptoAlgorithmFieldGroup != null) {
                cryptoAlgorithmFieldGroup.getChildren().addAll(cryptoWarningLabel, cryptoHelpLabel);
            }

            cryptoAlgorithmCombo.setCellFactory(lv -> new ProcessDesignerAlgorithmListCell());

            cryptoAlgorithmCombo.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && newV.startsWith("---")) {
                    javafx.application.Platform.runLater(() -> cryptoAlgorithmCombo.setValue(oldV));
                    return;
                }

                if (newV != null) {
                    if (selected == null || "ENCRYPT".equals(selected.type) || "DECRYPT".equals(selected.type)) {
                        try {
                            com.cryptocarver.model.process.handlers.SymmetricCipherSpec spec = com.cryptocarver.model.process.handlers.SymmetricCipherSpec.fromAlgorithm(newV);
                            cryptoHelpLabel.setText(spec.helpText);
                            cryptoWarningLabel.setText("WARNING: ECB mode is insecure for general use.");
                            boolean isEcb = newV.contains("ECB");
                            cryptoWarningLabel.setVisible(isEcb);
                            cryptoWarningLabel.setManaged(isEcb);
                        } catch (Exception e) {
                            cryptoHelpLabel.setText("");
                            cryptoWarningLabel.setVisible(false);
                            cryptoWarningLabel.setManaged(false);
                        }
                    } else if ("WSS_ENCRYPT_BODY".equals(selected.type)) {
                        boolean authenticated = newV.endsWith("GCM");
                        cryptoHelpLabel.setText(authenticated
                                ? "SOAP Body content encryption. Authenticated encryption: yes."
                                : "SOAP Body content encryption. Authenticated encryption: no.");
                        cryptoWarningLabel.setText("WARNING: CBC encryption is not authenticated.");
                        cryptoWarningLabel.setVisible(!authenticated);
                        cryptoWarningLabel.setManaged(!authenticated);
                    } else if ("WSS_SIGN_BODY".equals(selected.type)) {
                        cryptoHelpLabel.setText("Signs the SOAP Body with exclusive canonicalization and " + newV + ".");
                        cryptoWarningLabel.setVisible(false);
                        cryptoWarningLabel.setManaged(false);
                    } else {
                        cryptoHelpLabel.setText("");
                        cryptoWarningLabel.setVisible(false);
                        cryptoWarningLabel.setManaged(false);
                    }
                }

                if (newV != null && selected != null) {
                    String algorithmKey = "WSS_ENCRYPT_BODY".equals(selected.type)
                            ? "dataAlgorithm"
                            : "WSS_SIGN_BODY".equals(selected.type) ? "signatureAlgorithm" : "algorithm";
                    if (!newV.equals(selected.configuration.get(algorithmKey))) {
                        selected.configuration.put(algorithmKey, newV);
                        updateNonceLabel();
                        updateAadPortHint();

                        if ("ENCRYPT".equals(selected.type) || "DECRYPT".equals(selected.type)) {
                            try {
                                com.cryptocarver.model.process.handlers.SymmetricCipherSpec spec = com.cryptocarver.model.process.handlers.SymmetricCipherSpec.fromAlgorithm(newV);
                                if (cipherOutputFormatCombo != null) {
                                    if (!spec.supportsEnvelope) {
                                        cipherOutputFormatCombo.setValue("RAW (standard ciphertext)");
                                        cipherOutputFormatCombo.setDisable(true);
                                    } else {
                                        cipherOutputFormatCombo.setDisable(false);
                                    }
                                }
                            } catch (Exception e) {}
                        }

                        redraw();
                    }
                }
            });
        }
        if (keyFormatCombo != null) {
            keyFormatCombo.getItems().setAll("HEX", "BASE64");
            keyFormatCombo.setValue("HEX");
        }
        if (keystoreTypeCombo != null) {
            keystoreTypeCombo.getItems().setAll("PKCS12", "JKS");
            keystoreTypeCombo.setValue("PKCS12");
        }
        if (materialTypeCombo != null) {
            materialTypeCombo.getItems().setAll("CERTIFICATE", "PEM");
            materialTypeCombo.setValue("CERTIFICATE");
        }
        if (wssKeyTransportCombo != null) {
            wssKeyTransportCombo.getItems().setAll("RSA-OAEP SHA-256", "RSA-OAEP SHA-1 (legacy profile)");
            wssKeyTransportCombo.setValue("RSA-OAEP SHA-256");
        }
        if (wssPasswordTypeCombo != null) {
            wssPasswordTypeCombo.getItems().setAll("PasswordDigest", "PasswordText");
            wssPasswordTypeCombo.setValue("PasswordDigest");
        }
        if (wssTimestampEnabledCheck != null) {
            wssTimestampEnabledCheck.selectedProperty().addListener((observable, oldValue, enabled) -> {
                if (wssTimestampMinutesField != null) wssTimestampMinutesField.setDisable(!enabled);
                if (wssTimestampSignedCheck != null) wssTimestampSignedCheck.setDisable(!enabled);
            });
        }
        if (keySizeCombo != null) keySizeCombo.setValue("256");
        if (symmetricKeyAlgorithmCombo != null) {
            symmetricKeyAlgorithmCombo.getItems().setAll("AES", "3DES");
            symmetricKeyAlgorithmCombo.setValue("AES");
            symmetricKeyAlgorithmCombo.setOnAction(event -> {
                if (selected != null && "AES_KEY_GENERATE".equals(selected.type)) {
                    String keyAlgorithm = symmetricKeyAlgorithmCombo.getValue();
                    selected.configuration.put("keyAlgorithm", keyAlgorithm);
                    selected.label = "3DES".equals(keyAlgorithm) ? "Generate 3DES key" : "Generate AES key";
                    updateSymmetricKeyGeneratorSize(keyAlgorithm, selected.configuration.getOrDefault("keySize", "256"));
                    redraw();
                }
            });
        }
        if (cipherOutputFormatCombo != null) {
            cipherOutputFormatCombo.getItems().setAll("RAW (standard ciphertext)", "ENVELOPE (self-describing workflow value)");
        }

        if (fileModeCombo != null) {
            fileModeCombo.getItems().setAll("Binary (raw bytes)", "Text");
            fileModeCombo.setOnAction(e -> {
                if (selected != null && ("FILE_INPUT".equals(selected.type) || "FILE_OUTPUT".equals(selected.type))) {
                    String modeStr = "Text".equals(fileModeCombo.getValue()) ? "TEXT" : "BINARY";
                    if ("FILE_INPUT".equals(selected.type)) {
                        selected.configuration.put("readMode", modeStr);
                    } else {
                        selected.configuration.put("writeMode", modeStr);
                    }
                    boolean needsCharset = "TEXT".equals(modeStr);
                    charsetFieldGroup.setVisible(needsCharset); charsetFieldGroup.setManaged(needsCharset);
                    redraw();
                }
            });
        }
        selectedNodeLabel.setText("Select a block to configure it");
        if (nodeNameFieldGroup != null) {
            nodeNameFieldGroup.setVisible(false);
            nodeNameFieldGroup.setManaged(false);
        }
        workflowCanvas.setFocusTraversable(true);
        workflowCanvas.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE || event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
                handleDeleteSelected();
                event.consume();
            }
        });
        updateSelectionUi();
        ProcessDefinition.Node input = addNode("CONSOLE_INPUT", "Console input", 40, 60);
        ProcessDefinition.Node hash = addNode("HASH", "SHA-256", 270, 60);
        ProcessDefinition.Node output = addNode("CONSOLE_OUTPUT", "Console output", 500, 60);
        connections.add(new ProcessDefinition.Connection(input.id, hash.id));
        connections.add(new ProcessDefinition.Connection(hash.id, output.id));
        // The configuration editor must always belong to a concrete node.  The example starts on its input.
        select(input);
    }

    /**
     * The FXML declares the columns but deliberately keeps presentation mapping
     * here, next to the row model.  Without these factories TableView accepts
     * rows yet renders them as empty cells.
     */
    private void configureExecutionStatusTable() {
        if (executionStatusTable == null) return;
        stepCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getStep()));
        stepNameCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getStepName()));
        operationCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getOperation()));
        inputCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getInput()));
        outputCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getOutput()));
        statusCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getStatus()));
        durationCol.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().getDuration()));
        executionStatusTable.setPlaceholder(new Label("Run a process to see its execution steps."));
    }

    @FXML public void handleAddConsoleInput() { addNode("CONSOLE_INPUT", "Console input", 60, 180); }
    @FXML public void handleAddFileInput() { addNode("FILE_INPUT", "File input", 60, 180); }
    @FXML public void handleAddHash() { addNode("HASH", "SHA-256", 280, 180); }
    @FXML public void handleAddEncrypt() { addNode("ENCRYPT", "Encrypt", 280, 180); }
    @FXML public void handleAddDecrypt() { addNode("DECRYPT", "Decrypt", 280, 180); }
    @FXML public void handleAddSign() { addNode("SIGN", "Sign", 280, 180); }
    @FXML public void handleAddVerify() { addNode("VERIFY", "Verify", 280, 180); }
    @FXML public void handleAddMac() { addNode("MAC", "MAC", 280, 180); }
    @FXML public void handleAddWssEncryptBody() {
        addNode("WSS_ENCRYPT_BODY", "WSS Encrypt SOAP Body", 280, 180);
    }
    @FXML public void handleAddWssDecryptBody() {
        addNode("WSS_DECRYPT_BODY", "WSS Decrypt SOAP Body", 280, 180);
    }
    @FXML public void handleAddWssSignBody() {
        addNode("WSS_SIGN_BODY", "WSS Sign SOAP Body", 280, 180);
    }
    @FXML public void handleAddWssVerifySignature() {
        addNode("WSS_VERIFY_SIGNATURE", "WSS Verify Signature", 280, 180);
    }
    @FXML public void handleAddWssUsernameToken() {
        addNode("WSS_USERNAME_TOKEN_ADD", "Add WSS UsernameToken", 280, 180);
    }
    @FXML public void handleAddWssVerifyUsernameToken() {
        addNode("WSS_USERNAME_TOKEN_VERIFY", "Verify WSS UsernameToken", 280, 180);
    }
    @FXML public void handleAddAesKeyGenerate() { addNode("AES_KEY_GENERATE", "Generate AES key", 180, 180); }
    @FXML public void handleAddPbkdf2() { addNode("KDF_PBKDF2", "PBKDF2", 240, 180); }
    @FXML public void handleAddRsaKeypairGenerate() { addNode("RSA_KEYPAIR_GENERATE", "Generate RSA key pair", 180, 180); }
    @FXML public void handleAddRandomBytes() { addNode("RANDOM_BYTES", "Random bytes", 60, 180); }
        @FXML public void handleAddBase64Encode() { addNode("BASE64_ENCODE", "Base64 Encode", 250, 150); }
    @FXML public void handleAddBase64Decode() { addNode("BASE64_DECODE", "Base64 Decode", 250, 150); }
    @FXML public void handleAddBase64UrlEncode() { addNode("BASE64URL_ENCODE", "Base64URL Encode", 250, 150); }
    @FXML public void handleAddBase64UrlDecode() { addNode("BASE64URL_DECODE", "Base64URL Decode", 250, 150); }
    @FXML public void handleAddHexEncode() { addNode("HEX_ENCODE", "Hex encode", 280, 180); }
    @FXML public void handleAddHexDecode() { addNode("HEX_DECODE", "Hex decode", 280, 180); }
    @FXML public void handleAddUtf8Encode() { addNode("UTF8_ENCODE", "UTF-8 encode", 280, 180); }
    @FXML public void handleAddUtf8Decode() { addNode("UTF8_DECODE", "UTF-8 decode", 280, 180); }
    @FXML public void handleAddFileOutput() { addNode("FILE_OUTPUT", "File output", 500, 180); }
    @FXML public void handleAddConsoleOutput() { addNode("CONSOLE_OUTPUT", "Console output", 500, 180); }

    @FXML public void handleToggleInspector() {
        inspectorVisible = !inspectorVisible;
        nodeInspector.setManaged(inspectorVisible);
        nodeInspector.setVisible(inspectorVisible);
        if (inspectorVisible) {
            nodeInspector.setMinWidth(250); nodeInspector.setPrefWidth(280); nodeInspector.setMaxWidth(Double.MAX_VALUE);
            designerSplitPane.setDividerPositions(0.72);
            inspectorToggleButton.setText("Hide inspector");
        } else {
            nodeInspector.setMinWidth(0); nodeInspector.setPrefWidth(0); nodeInspector.setMaxWidth(0);
            designerSplitPane.setDividerPositions(1.0);
            inspectorToggleButton.setText("Show inspector");
        }
    }

    /** Opens this workflow's own trace, rather than the unrelated global-operation result. */
    @FXML public void handleOpenExpandedExecutionResult() {
        String trace = executionOutputArea == null ? "" : executionOutputArea.getText();
        if (trace == null || trace.isBlank()) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Run this process before opening its expanded execution result.").showAndWait();
            return;
        }
        javafx.stage.Window owner = workflowCanvas == null || workflowCanvas.getScene() == null
                ? null : workflowCanvas.getScene().getWindow();
        expandedExecutionViewer.show(owner, "Expanded Result — Process Designer", trace);
    }

    private void connectToPort(String targetPort) {
        if (selectedNodeIds.size() != 2) return;
        List<String> pair = orderedConnectionPair();
        String source = pair.get(0);
        String destination = pair.get(1);

        if (targetPort != null) {
            boolean occupied = connections.stream().anyMatch(c -> c.to.equals(destination) && targetPort.equals(c.targetPort));
            if (occupied) {
                executionOutputArea.setText("Error: The port '" + targetPort + "' on " + nodeLabel(destination) + " is already occupied. Delete existing connection first.");
                return;
            }
        } else {
            connections.removeIf(c -> c.to.equals(destination) && c.targetPort == null);
        }

        ProcessDefinition.Connection newConn = new ProcessDefinition.Connection(source, destination);
        newConn.targetPort = targetPort;
        connections.add(newConn);
        if ("key".equals(targetPort)) {
            nodes.stream().filter(n -> n.id.equals(destination)).findFirst()
                    .ifPresent(n -> n.configuration.put("keyFromFlow", "true"));
        }

        ProcessDefinition.Node sourceNode = nodes.stream().filter(n -> n.id.equals(source)).findFirst().orElse(null);
        ProcessDefinition.Node destinationNode = nodes.stream().filter(n -> n.id.equals(destination)).findFirst().orElse(null);
        // Key material is deliberately reusable: keep it selected after a key
        // binding so the next crypto node can receive the same generated key.
        // This makes Encrypt -> Decrypt round trips natural without weakening the
        // one-source-per-target-port invariant.
        boolean keepReusableKeySourceSelected = "key".equals(targetPort) && isReusableKeySource(sourceNode);
        selected = keepReusableKeySourceSelected ? sourceNode : destinationNode;
        selectedNodeIds.clear();
        if (selected != null) selectedNodeIds.add(selected.id);
        String portStr = targetPort != null ? " [" + targetPort + "]" : "";
        executionOutputArea.setText("Connected " + nodeLabel(source) + " → " + nodeLabel(destination) + portStr
                + (keepReusableKeySourceSelected ? ". Select another crypto node to reuse this key." : ""));
        updateSelectionUi();
        redraw();
    }

    @FXML public void handleConnectSelected() { connectToPort(null); }

    @FXML public void handleSaveNodeSettings() {
        saveSelectedNodeSettings();
        redraw();
    }

    @FXML public void handleCryptoAlgorithmChanged() {
        updateNonceLabel();
        updateAadPortHint();
    }
    @FXML public void handleBrowseFile() {
        if (selected == null) return;
        FileChooser chooser = new FileChooser();
        if ("FILE_OUTPUT".equals(selected.type)) {
            chooser.setTitle("Select output file");
            File file = chooser.showSaveDialog(workflowCanvas.getScene().getWindow());
            if (file != null) {
                nodePathField.setText(file.getAbsolutePath());
                saveSelectedNodeSettings();
            }
        } else {
            chooser.setTitle("Select input file");
            File file = chooser.showOpenDialog(workflowCanvas.getScene().getWindow());
            if (file != null) {
                nodePathField.setText(file.getAbsolutePath());
                saveSelectedNodeSettings();
            }
        }
    }
    @FXML public void handleBrowseKeystore() {
        if (selected == null) return;
        FileChooser chooser = new FileChooser(); chooser.setTitle("Select Keystore");
        File file = chooser.showOpenDialog(workflowCanvas.getScene().getWindow());
        if (file != null) { keystorePathField.setText(file.getAbsolutePath()); saveSelectedNodeSettings(); }
    }
    @FXML public void handleBrowseMaterial() {
        if (selected == null) return;
        FileChooser chooser = new FileChooser(); chooser.setTitle("Select Certificate / Public Key");
        File file = chooser.showOpenDialog(workflowCanvas.getScene().getWindow());
        if (file != null) { materialPathField.setText(file.getAbsolutePath()); saveSelectedNodeSettings(); }
    }
    private void saveSelectedNodeSettings() {
        if (selected == null) return;
        if (nodeNameField != null && !nodeNameField.getText().isBlank()) {
            selected.label = nodeNameField.getText().trim();
            selectedNodeLabel.setText(selected.type + " · " + selected.label);
        }
        if ("CONSOLE_INPUT".equals(selected.type)) selected.configuration.put("value", nodeValueArea.getText());
        if ("FILE_INPUT".equals(selected.type) || "FILE_OUTPUT".equals(selected.type)) {
            selected.configuration.put("path", nodePathField.getText().trim());
        }
        if ("RANDOM_BYTES".equals(selected.type)) {
            if (randomBytesLengthField != null) selected.configuration.put("length", randomBytesLengthField.getText().trim());
        }
        if ("CONSOLE_INPUT".equals(selected.type) || "FILE_INPUT".equals(selected.type) || "FILE_OUTPUT".equals(selected.type)) {
            selected.configuration.put("charset", nodeCharsetCombo.getValue());
        }
        if ("HASH".equals(selected.type)) selected.configuration.put("algorithm", hashAlgorithmCombo.getValue());

        boolean encrypt = "ENCRYPT".equals(selected.type);
        boolean decrypt = "DECRYPT".equals(selected.type);
        boolean mac = "MAC".equals(selected.type);
        boolean sign = "SIGN".equals(selected.type);
        boolean verify = "VERIFY".equals(selected.type);
        boolean wssEncrypt = "WSS_ENCRYPT_BODY".equals(selected.type);
        boolean wssDecrypt = "WSS_DECRYPT_BODY".equals(selected.type);
        boolean wssSign = "WSS_SIGN_BODY".equals(selected.type);
        boolean wssVerify = "WSS_VERIFY_SIGNATURE".equals(selected.type);
        boolean wssUsernameAdd = "WSS_USERNAME_TOKEN_ADD".equals(selected.type);
        boolean wssUsernameVerify = "WSS_USERNAME_TOKEN_VERIFY".equals(selected.type);


        updateRepresentationContract(selected);
if (encrypt || decrypt || mac || sign || verify) {
            if (cryptoAlgorithmCombo != null) selected.configuration.put("algorithm", cryptoAlgorithmCombo.getValue());
        }
        if (wssEncrypt) {
            if (cryptoAlgorithmCombo != null) {
                selected.configuration.put("dataAlgorithm", cryptoAlgorithmCombo.getValue());
            }
            if (wssKeyTransportCombo != null) {
                selected.configuration.put("keyTransportAlgorithm", wssKeyTransportCombo.getValue());
            }
        }
        if (wssSign && cryptoAlgorithmCombo != null) {
            selected.configuration.put("signatureAlgorithm", cryptoAlgorithmCombo.getValue());
            selected.configuration.put("timestampEnabled", String.valueOf(wssTimestampEnabledCheck.isSelected()));
            selected.configuration.put("timestampMinutes", wssTimestampMinutesField.getText().trim());
            selected.configuration.put("timestampSigned", String.valueOf(wssTimestampSignedCheck.isSelected()));
        }
        if (wssUsernameAdd || wssUsernameVerify) {
            selected.configuration.put("username", wssUsernameField.getText().trim());
            if (!wssPasswordField.getText().isEmpty()) {
                selected.configuration.put("wssPassword", wssPasswordField.getText());
            }
            if (wssUsernameAdd) {
                selected.configuration.put("passwordType", wssPasswordTypeCombo.getValue());
            } else {
                selected.configuration.put("maxAgeSeconds", wssTokenAgeField.getText().trim());
            }
        }
        if (encrypt || decrypt || mac) {
            if (keyFormatCombo != null) selected.configuration.put("keyFormat", keyFormatCombo.getValue());
            if (manualKeyField != null && !manualKeyField.getText().isEmpty()) selected.configuration.put("key", manualKeyField.getText());
        }
        if (encrypt || decrypt) {
            if (nonceField != null) selected.configuration.put("nonce", nonceField.getText());
            if (generateNonceCheck != null) selected.configuration.put("generateNonce", String.valueOf(generateNonceCheck.isSelected()));
        }
        if (encrypt && cipherOutputFormatCombo != null && cipherOutputFormatCombo.getValue() != null) {
            selected.configuration.put("outputFormat", cipherOutputFormatCombo.getValue().startsWith("ENVELOPE") ? "ENVELOPE" : "RAW");
        }
        if ("AES_KEY_GENERATE".equals(selected.type) || "KDF_PBKDF2".equals(selected.type) || "RSA_KEYPAIR_GENERATE".equals(selected.type)) {
            if ("AES_KEY_GENERATE".equals(selected.type) && symmetricKeyAlgorithmCombo != null) {
                String keyAlgorithm = symmetricKeyAlgorithmCombo.getValue();
                selected.configuration.put("keyAlgorithm", keyAlgorithm);
            }
            if (keySizeCombo != null && keySizeCombo.getValue() != null) selected.configuration.put("keySize", keySizeCombo.getValue());
            if ("KDF_PBKDF2".equals(selected.type)) {
                if (kdfIterationsField != null && !kdfIterationsField.getText().isBlank()) selected.configuration.put("iterations", kdfIterationsField.getText().trim());
                if (kdfSaltField != null && !kdfSaltField.getText().isBlank()) selected.configuration.put("salt", kdfSaltField.getText().trim());
            }
        }
        if (sign || wssDecrypt || wssSign) {
            if (keystorePathField != null) selected.configuration.put("keystorePath", keystorePathField.getText());
            if (keystoreTypeCombo != null) selected.configuration.put("keystoreType", keystoreTypeCombo.getValue());
            if ((sign || wssSign) && aliasField != null) selected.configuration.put("alias", aliasField.getText());
            if (keystorePasswordField != null && !keystorePasswordField.getText().isEmpty()) selected.configuration.put("keystorePassword", keystorePasswordField.getText());
            if (keyPasswordField != null && !keyPasswordField.getText().isEmpty()) selected.configuration.put("keyPassword", keyPasswordField.getText());
        }
        if (verify || wssEncrypt || wssVerify) {
            if (materialPathField != null) selected.configuration.put("materialPath", materialPathField.getText());
            if (verify && materialTypeCombo != null) selected.configuration.put("materialType", materialTypeCombo.getValue());
        }
    }
    @FXML public void handleDeleteSelected() {
        ProcessDefinition.Connection connection = selectedConnection != null ? selectedConnection : connectionBetweenSelectedNodes();
        if (connection != null) {
            connections.remove(connection);
            executionOutputArea.setText("Deleted connection to port '" + connection.targetPort + "'.");
            selectedConnection = null;
            updateSelectionUi();
            redraw();
            return;
        }
        if (selected == null) return;
        connections.removeIf(c -> c.from.equals(selected.id) || c.to.equals(selected.id));
        nodes.remove(selected); views.remove(selected.id); selectedNodeIds.remove(selected.id); selected = null; updateSelectionUi(); redraw();
    }
    @FXML public void handleReverseSelectedConnection() {
        ProcessDefinition.Connection connection = selectedConnection != null ? selectedConnection : connectionBetweenSelectedNodes();
        if (connection == null) return;
        String previousSource = connection.from;
        connection.from = connection.to;
        connection.to = previousSource;
        selectedConnection = connection;
        selectedNodeIds.clear();
        selected = null;
        selectedNodeLabel.setText("Connection reversed: " + nodeLabel(connection.from) + " → " + nodeLabel(connection.to));
        executionOutputArea.setText("Connection direction reversed. Check the flow before running it.");
        updateSelectionUi();
        redraw();
    }
    @FXML public void handleClearCanvas() { nodes.clear(); connections.clear(); views.clear(); selectedNodeIds.clear(); selected = null; selectedConnection = null; updateSelectionUi(); redraw(); }

    public Runnable onExecutionFinished;

    @FXML public void handleRunProcess() {
        saveSelectedNodeSettings();
        ProcessDefinition definition = toDefinition();
        executionOutputArea.clear();
        if (executionStatusTable != null) executionStatusTable.getItems().clear();

        for (ProcessDefinition.Node n : definition.nodes) {
            if ("ENCRYPT".equals(n.type) || "DECRYPT".equals(n.type)) {
                String alg = n.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding");
                com.cryptocarver.model.process.handlers.SymmetricCipherSpec spec;
                try {
                    spec = com.cryptocarver.model.process.handlers.SymmetricCipherSpec.fromAlgorithm(alg);
                } catch (Exception e) {
                    showPreflightFailure("Error on " + n.label + ": " + e.getMessage());
                    return;
                }
                boolean hasAadConn = definition.connections.stream().anyMatch(c -> c.to.equals(n.id) && "aad".equals(c.targetPort));
                if (!spec.aead && hasAadConn) {
                    showPreflightFailure("Validation error: " + n.label + " is connected to an 'aad' port, but " + alg + " does not support AAD. Please remove the connection or change the algorithm.");
                    return;
                }
                boolean hasIvConn = definition.connections.stream().anyMatch(c -> c.to.equals(n.id) && "iv".equals(c.targetPort));
                if (spec.ivLength == 0 && hasIvConn) {
                    showPreflightFailure("Validation error: " + n.label + " is connected to an 'iv' port, but " + alg + " does not use an IV. Please remove the connection.");
                    return;
                }
            }
        }

        java.util.Queue<NodeExecutionEvent> events = new java.util.concurrent.ConcurrentLinkedQueue<>();
        ExecutionContext context = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, events::add);
        new Thread(() -> {
            try {
                Map<String, com.cryptocarver.model.process.FlowValue> result = ProcessEngine.execute(definition, context);
                Platform.runLater(() -> {
                    renderExecutionResult(definition, result, events, null);
                    if (onExecutionFinished != null) onExecutionFinished.run();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    renderExecutionResult(definition, Map.of(), events, e);
                    if (onExecutionFinished != null) onExecutionFinished.run();
                });
            }
        }, "process-designer-execution").start();
    }

    private void showPreflightFailure(String message) {
        if (executionStatusTable != null) {
            executionStatusTable.getItems().setAll(new ProcessExecutionRow("validation", "-", "Validation",
                    "PRE-FLIGHT", "-", "-", "ERROR", "0 ms"));
        }
        executionOutputArea.setText("Process failed: " + message);
    }

    private void renderExecutionResult(ProcessDefinition definition, Map<String, com.cryptocarver.model.process.FlowValue> result,
            java.util.Collection<NodeExecutionEvent> events, Exception failure) {
        Map<String, NodeExecutionEvent> finalEvents = new LinkedHashMap<>();
        for (NodeExecutionEvent event : events) {
            if (event.state() != com.cryptocarver.model.process.NodeExecutionState.RUNNING) {
                finalEvents.put(event.nodeId(), event);
            }
        }
        if (executionStatusTable != null) {
            executionStatusTable.getItems().clear();
            if (finalEvents.isEmpty() && failure != null) {
                executionStatusTable.getItems().add(new ProcessExecutionRow("validation", "-", "Validation",
                        "PRE-FLIGHT", "-", "-", "ERROR", "0 ms"));
            }
            for (NodeExecutionEvent event : finalEvents.values()) {
                executionStatusTable.getItems().add(new ProcessExecutionRow(event.nodeId(), String.valueOf(event.step()),
                        event.nodeLabel(), event.nodeType(), formatFlow(event.inputRepresentation(), event.inputSize()),
                        formatFlow(event.outputRepresentation(), event.outputSize()), event.state().name(),
                        event.duration().toMillis() + " ms"));
            }
        }

        StringBuilder trace = new StringBuilder(failure == null ? "Process completed successfully.\n" : "Process failed: " + failure.getMessage() + "\n");
        for (NodeExecutionEvent event : finalEvents.values()) {
            trace.append('\n').append('[').append(event.step()).append("] ")
                    .append(event.nodeLabel().replace("\n", " ")).append(" · ").append(event.nodeType())
                    .append(" — ").append(event.state().name()).append(" (").append(event.duration().toMillis()).append(" ms)\n");
            if (event.inputRepresentation() != null) trace.append("  input:  ").append(formatFlow(event.inputRepresentation(), event.inputSize())).append('\n');
            if (event.outputRepresentation() != null) trace.append("  output: ").append(formatFlow(event.outputRepresentation(), event.outputSize())).append('\n');
            if (result.containsKey(event.nodeId())) {
                com.cryptocarver.model.process.FlowValue value = result.get(event.nodeId());
                trace.append("  value: ").append(value.render()).append('\n');
            }
            ProcessDefinition.Node node = definition.nodes.stream().filter(n -> n.id.equals(event.nodeId())).findFirst().orElse(null);
            if (node != null && ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type))) {
                if (Boolean.parseBoolean(node.configuration.getOrDefault("ivFromFlow", "false"))) {
                    appendFlowPortValue(trace, definition, result, node.id, "iv", "IV/nonce");
                } else if (node.configuration.get("nonce") != null) {
                    trace.append("  IV/nonce (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ")
                            .append(node.configuration.get("nonce")).append('\n');
                }
                if (Boolean.parseBoolean(node.configuration.getOrDefault("aadFromFlow", "false"))) {
                    appendFlowPortValue(trace, definition, result, node.id, "aad", "AAD");
                }
            }
            if (node != null && ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type) || "MAC".equals(node.type))
                    && node.configuration.get("key") != null) {
                trace.append("  key (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ")
                        .append(node.configuration.get("key")).append('\n');
            }
            if (node != null && "KDF_PBKDF2".equals(node.type)) {
                trace.append("  PBKDF2: ").append(node.configuration.getOrDefault("iterations", "210000"))
                        .append(" iterations; salt (Base64): ").append(node.configuration.getOrDefault("salt", "")).append('\n');
            }
            if (node != null && ("AES_KEY_GENERATE".equals(node.type) || "KDF_PBKDF2".equals(node.type)
                    || "RSA_KEYPAIR_GENERATE".equals(node.type) || "RANDOM_BYTES".equals(node.type)) && result.containsKey(node.id)) {
                trace.append("  generated material (HEX): ").append(result.get(node.id).render()).append('\n');
            }
        }
        for (ProcessDefinition.Node node : definition.nodes) {
            if ("CONSOLE_OUTPUT".equals(node.type) && result.containsKey(node.id)) {
                com.cryptocarver.model.process.FlowValue value = result.get(node.id);
                trace.append("\nConsole output · ").append(node.label.replace("\n", " ")).append('\n')
                        .append("  ").append(formatFlow(value.representation(), value.bytes().length)).append('\n')
                        .append("  value: ").append(value.render()).append('\n');
            }
        }
        if (selected != null && "ENCRYPT".equals(selected.type) && nonceField != null) {
            nonceField.setText(selected.configuration.getOrDefault("nonce", ""));
        }
        executionOutputArea.setText(trace.toString());
    }

    /**
     * The designer is a laboratory tool: when a port is connected, show both the
     * binding and its complete value at the consuming operation, not merely a
     * vague "provided by flow" marker.  Process files still exclude secrets.
     */
    private static void appendFlowPortValue(StringBuilder trace, ProcessDefinition definition,
            Map<String, com.cryptocarver.model.process.FlowValue> result, String destinationId,
            String targetPort, String displayName) {
        for (ProcessDefinition.Connection connection : definition.connections) {
            if (destinationId.equals(connection.to) && targetPort.equals(connection.targetPort)) {
                com.cryptocarver.model.process.FlowValue value = result.get(connection.from);
                if (value != null) {
                    trace.append("  ").append(displayName).append(" (flow from ")
                            .append(connection.from).append(", ").append(value.representation()).append("): ")
                            .append(value.render()).append('\n');
                    return;
                }
            }
        }
        trace.append("  ").append(displayName).append(": [provided by flow; value unavailable]\n");
    }

    private static String formatFlow(Representation representation, int size) {
        if (representation == null) return "—";
        return representation + " · " + size + (representation == Representation.BINARY ? " bytes" : " chars");
    }
    @FXML public void handleSaveProcess() {
        saveSelectedNodeSettings();
        String processName = normalizedProcessName();
        processNameField.setText(processName);
        FileChooser chooser = new FileChooser(); chooser.setTitle("Save process"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CryptoForge process", "*.cfprocess.json"));
        chooser.setInitialFileName(safeProcessFileName(processName) + ".cfprocess.json");
        File file = chooser.showSaveDialog(workflowCanvas.getScene().getWindow());
        try {
            if (file != null) {
                Files.writeString(file.toPath(), ProcessDefinitionCodec.serialize(toDefinition()));
                executionOutputArea.setText("Saved process '" + processName + "' to " + file.getAbsolutePath());
            }
        }
        catch (Exception e) { executionOutputArea.setText("Cannot save process: " + e.getMessage()); }
    }
    @FXML public void handleLoadProcess() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Open process"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CryptoForge process", "*.cfprocess.json"));
        File file = chooser.showOpenDialog(workflowCanvas.getScene().getWindow());
        try { if (file != null) load(ProcessDefinitionCodec.deserialize(Files.readString(file.toPath()))); }
        catch (Exception e) { executionOutputArea.setText("Cannot open process: " + e.getMessage()); }
    }

    /** Loads an editable starter workflow; no secret material is embedded in presets. */
    @FXML public void handleLoadSha256Preset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "SHA-256 text digest";
        ProcessDefinition.Node input = presetNode("input", "CONSOLE_INPUT", "Text input", 60, 110);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node hash = presetNode("hash", "HASH", "SHA-256", 280, 110);
        hash.configuration.put("algorithm", "SHA-256");
        ProcessDefinition.Node output = presetNode("output", "CONSOLE_OUTPUT", "Digest output", 500, 110);
        preset.nodes.addAll(List.of(input, hash, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "hash", "input"));
        preset.connections.add(new ProcessDefinition.Connection("hash", "output", "input"));
        loadPreset(preset);
    }

    @FXML public void handleLoadBase64Preset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "Base64 text encode";
        ProcessDefinition.Node input = presetNode("input", "CONSOLE_INPUT", "Text input", 60, 110);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node encode = presetNode("encode", "BASE64_ENCODE", "Base64 encode", 280, 110);
        ProcessDefinition.Node output = presetNode("output", "CONSOLE_OUTPUT", "Base64 output", 500, 110);
        preset.nodes.addAll(List.of(input, encode, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "encode", "input"));
        preset.connections.add(new ProcessDefinition.Connection("encode", "output", "input"));
        loadPreset(preset);
    }

    @FXML public void handleLoadAesGcmRoundTripPreset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "AES-GCM encrypt and decrypt";
        ProcessDefinition.Node input = presetNode("input", "CONSOLE_INPUT", "Plaintext", 40, 140);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node key = presetNode("key", "AES_KEY_GENERATE", "Generate AES key", 220, 300);
        key.configuration.put("keySize", "256");
        key.configuration.put("keyAlgorithm", "AES");
        ProcessDefinition.Node iv = presetNode("iv", "RANDOM_BYTES", "Random 12-byte IV", 220, 40);
        iv.configuration.put("length", "12");
        ProcessDefinition.Node encrypt = presetNode("encrypt", "ENCRYPT", "Encrypt AES-GCM", 410, 140);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        encrypt.configuration.put("keyFormat", "HEX");
        encrypt.configuration.put("generateNonce", "false");
        encrypt.configuration.put("outputFormat", "RAW");
        ProcessDefinition.Node decrypt = presetNode("decrypt", "DECRYPT", "Decrypt AES-GCM", 620, 140);
        decrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        decrypt.configuration.put("keyFormat", "HEX");
        decrypt.configuration.put("generateNonce", "false");
        decrypt.configuration.put("outputFormat", "RAW");
        ProcessDefinition.Node decode = presetNode("decode", "UTF8_DECODE", "Decode UTF-8", 820, 140);
        ProcessDefinition.Node output = presetNode("output", "CONSOLE_OUTPUT", "Recovered text", 1000, 140);
        preset.nodes.addAll(List.of(input, key, iv, encrypt, decrypt, decode, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        preset.connections.add(new ProcessDefinition.Connection("key", "encrypt", "key"));
        preset.connections.add(new ProcessDefinition.Connection("key", "decrypt", "key"));
        preset.connections.add(new ProcessDefinition.Connection("iv", "encrypt", "iv"));
        preset.connections.add(new ProcessDefinition.Connection("iv", "decrypt", "iv"));
        preset.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt", "payload"));
        preset.connections.add(new ProcessDefinition.Connection("decrypt", "decode", "input"));
        preset.connections.add(new ProcessDefinition.Connection("decode", "output", "input"));
        loadPreset(preset);
    }

    @FXML public void handleLoadAesCmacPreset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "AES-CMAC";
        ProcessDefinition.Node input = presetNode("input", "CONSOLE_INPUT", "Message", 60, 140);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node key = presetNode("key", "AES_KEY_GENERATE", "Generate AES key", 260, 280);
        key.configuration.put("keySize", "256");
        key.configuration.put("keyAlgorithm", "AES");
        ProcessDefinition.Node mac = presetNode("mac", "MAC", "CMAC-AES", 420, 140);
        mac.configuration.put("algorithm", "CMAC-AES");
        mac.configuration.put("keyFormat", "HEX");
        ProcessDefinition.Node output = presetNode("output", "CONSOLE_OUTPUT", "CMAC output", 640, 140);
        preset.nodes.addAll(List.of(input, key, mac, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "mac", "payload"));
        preset.connections.add(new ProcessDefinition.Connection("key", "mac", "key"));
        preset.connections.add(new ProcessDefinition.Connection("mac", "output", "input"));
        loadPreset(preset);
    }

    private ProcessDefinition.Node presetNode(String id, String type, String label, double x, double y) {
        ProcessDefinition.Node node = new ProcessDefinition.Node(id, type, label, x, y);
        node.configuration.put("charset", "UTF-8");
        return node;
    }

    private void loadPreset(ProcessDefinition preset) {
        load(preset);
        if (executionOutputArea != null) {
            executionOutputArea.setText("Loaded preset '" + preset.name + "'. You can edit every node before running it.");
        }
    }

    private ProcessDefinition.Node addNode(String type, String label, double x, double y) {
        ProcessDefinition.Node node = new ProcessDefinition.Node(UUID.randomUUID().toString(), type, label, x, y);
        node.configuration.put("charset", "UTF-8");
        if ("HASH".equals(type)) node.configuration.put("algorithm", "SHA-256");
        if ("ENCRYPT".equals(type) || "DECRYPT".equals(type)) {
            node.configuration.put("algorithm", "AES/GCM/NoPadding");
            node.configuration.put("keyFormat", "HEX");
            node.configuration.put("generateNonce", "true");
        }
        if ("MAC".equals(type)) node.configuration.put("algorithm", "HmacSHA256");
        if ("SIGN".equals(type) || "VERIFY".equals(type)) node.configuration.put("algorithm", "SHA256withRSA");
        if ("WSS_ENCRYPT_BODY".equals(type)) {
            node.configuration.put("dataAlgorithm", "AES-256-GCM");
            node.configuration.put("keyTransportAlgorithm", "RSA-OAEP SHA-256");
        }
        if ("WSS_DECRYPT_BODY".equals(type)) node.configuration.put("keystoreType", "PKCS12");
        if ("WSS_SIGN_BODY".equals(type)) {
            node.configuration.put("keystoreType", "PKCS12");
            node.configuration.put("signatureAlgorithm", "RSA_SHA256");
            node.configuration.put("timestampEnabled", "false");
            node.configuration.put("timestampMinutes", "5");
            node.configuration.put("timestampSigned", "true");
        }
        if ("WSS_USERNAME_TOKEN_ADD".equals(type)) {
            node.configuration.put("passwordType", "PasswordDigest");
        }
        if ("WSS_USERNAME_TOKEN_VERIFY".equals(type)) {
            node.configuration.put("maxAgeSeconds", "300");
        }
        if ("AES_KEY_GENERATE".equals(type) || "KDF_PBKDF2".equals(type)) node.configuration.put("keySize", "256");
        if ("KDF_PBKDF2".equals(type)) node.configuration.put("iterations", "210000");
        if ("RSA_KEYPAIR_GENERATE".equals(type)) node.configuration.put("keySize", "2048");
        nodes.add(node); redraw();
        return node;
    }
    ProcessDefinition toDefinition() {
        ProcessDefinition definition = new ProcessDefinition(); definition.name = processNameField.getText().trim(); definition.nodes = new ArrayList<>(nodes); definition.connections = new ArrayList<>(connections); return definition;
    }
    private void load(ProcessDefinition definition) {
        processNameField.setText(normalizedProcessName(definition.name));
        nodes.clear(); nodes.addAll(definition.nodes);
        connections.clear(); connections.addAll(definition.connections);
        selected = null; selectedConnection = null; selectedNodeIds.clear();
        updateSelectionUi(); redraw();
    }

    private String normalizedProcessName() {
        return normalizedProcessName(processNameField == null ? null : processNameField.getText());
    }

    private static String normalizedProcessName(String name) {
        return name == null || name.isBlank() ? "Untitled process" : name.trim();
    }

    private static String safeProcessFileName(String processName) {
        String safe = processName.replaceAll("[^a-zA-Z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "process" : safe;
    }
    void redraw() {
        workflowCanvas.getChildren().clear(); views.clear();
        Map<String, Representation> reps = new java.util.HashMap<>();
        try { reps = ProcessEngine.validate(toDefinition()); } catch(Exception ignored) {}

        for (ProcessDefinition.Connection connection : connections) {
            ProcessDefinition.Node from = nodes.stream().filter(n -> n.id.equals(connection.from)).findFirst().orElse(null);
            ProcessDefinition.Node to = nodes.stream().filter(n -> n.id.equals(connection.to)).findFirst().orElse(null);
            if (from != null && to != null) addConnectionView(connection, from, to);
        }
        for (ProcessDefinition.Node node : nodes) workflowCanvas.getChildren().add(createNodeView(node, reps.get(node.id)));
    }
    private StackPane createNodeView(ProcessDefinition.Node node, Representation rep) {
        String badge = rep != null ? " [" + rep.name() + "]" : "";
        Label label = new Label(node.label + badge); label.setWrapText(true); label.setMaxWidth(135); label.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
        StackPane view = new StackPane(label); view.setLayoutX(node.x); view.setLayoutY(node.y); view.setPrefSize(150, 70);

        List<com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition> ports = ProcessEngine.getHandlerFor(node.type).inputPorts(node);
        if (ports.size() > 1) {
            for (int index = 0; index < ports.size(); index++) {
                Label portLabel = new Label("• " + ports.get(index).name());
                portLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 9px;");
                portLabel.setTranslateX(-48);
                portLabel.setTranslateY((index - (ports.size() - 1) / 2.0) * 13);
                view.getChildren().add(portLabel);
            }
        }

        boolean active = selected != null && selected.id.equals(node.id);
        boolean pending = !active && selectedNodeIds.contains(node.id);
        if (active) {
            view.setStyle("-fx-background-color: #287bb5; -fx-border-color: white; -fx-border-width: 3; -fx-background-radius: 5;");
        } else if (pending) {
            view.setStyle("-fx-background-color: #5a4a20; -fx-border-color: #f6c344; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;");
            Label sourceMarker = new Label("SOURCE");
            sourceMarker.setStyle("-fx-text-fill: #f6c344; -fx-font-size: 8px; -fx-font-weight: bold; -fx-background-color: #202a33;");
            sourceMarker.setTranslateX(46); sourceMarker.setTranslateY(-25);
            view.getChildren().add(sourceMarker);
        } else {
            view.setStyle("-fx-background-color: #33495e; -fx-border-color: #6f97bb; -fx-border-width: 1; -fx-background-radius: 5;");
        }
        final double[] offset = new double[2];
        view.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> { workflowCanvas.requestFocus(); offset[0] = e.getSceneX() - view.getLayoutX(); offset[1] = e.getSceneY() - view.getLayoutY(); select(node); e.consume(); });
        view.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> { node.x = Math.max(0, e.getSceneX() - offset[0]); node.y = Math.max(0, e.getSceneY() - offset[1]); redraw(); e.consume(); });
        return view;
    }
    void select(ProcessDefinition.Node node) {
        saveSelectedNodeSettings();
        selectedConnection = null;
        if (!selectedNodeIds.contains(node.id) && selectedNodeIds.size() == 2) selectedNodeIds.clear();
        selectedNodeIds.add(node.id);
        selected = node; selectedNodeLabel.setText(node.type + " · " + node.label);
        if (nodeNameFieldGroup != null) {
            nodeNameFieldGroup.setVisible(true);
            nodeNameFieldGroup.setManaged(true);
        }
        if (nodeNameField != null) nodeNameField.setText(node.label == null ? "" : node.label);
        boolean consoleInput = "CONSOLE_INPUT".equals(node.type);
        boolean fileNode = "FILE_INPUT".equals(node.type) || "FILE_OUTPUT".equals(node.type);
        boolean hashNode = "HASH".equals(node.type);
        boolean encrypt = "ENCRYPT".equals(node.type);
        boolean decrypt = "DECRYPT".equals(node.type);
        boolean mac = "MAC".equals(node.type);
        boolean sign = "SIGN".equals(node.type);
        boolean verify = "VERIFY".equals(node.type);
        boolean wssEncrypt = "WSS_ENCRYPT_BODY".equals(node.type);
        boolean wssDecrypt = "WSS_DECRYPT_BODY".equals(node.type);
        boolean wssSign = "WSS_SIGN_BODY".equals(node.type);
        boolean wssVerify = "WSS_VERIFY_SIGNATURE".equals(node.type);
        boolean wssUsernameAdd = "WSS_USERNAME_TOKEN_ADD".equals(node.type);
        boolean wssUsernameVerify = "WSS_USERNAME_TOKEN_VERIFY".equals(node.type);
        boolean keyMaterial = "AES_KEY_GENERATE".equals(node.type) || "KDF_PBKDF2".equals(node.type)
                || "RSA_KEYPAIR_GENERATE".equals(node.type);

        boolean hasCryptoAlg = encrypt || decrypt || mac || sign || verify || wssEncrypt || wssSign;
        boolean hasKeyFormat = encrypt || decrypt || mac;
        boolean hasManualKey = encrypt || decrypt || mac;
        boolean hasNonce = encrypt || decrypt;
        boolean hasKeystore = sign || wssDecrypt || wssSign;
        boolean hasMaterial = verify || wssEncrypt || wssVerify;
        boolean hasSecrets = encrypt || decrypt || mac || sign || wssDecrypt || wssSign
                || wssUsernameAdd || wssUsernameVerify;

        nodeValueArea.setText(consoleInput ? node.configuration.getOrDefault("value", "") : "");
        consoleValueFieldGroup.setVisible(consoleInput); consoleValueFieldGroup.setManaged(consoleInput);
        nodePathField.setText(fileNode ? node.configuration.getOrDefault("path", "") : "");
        filePathFieldGroup.setVisible(fileNode); filePathFieldGroup.setManaged(fileNode);
        boolean needsCharset = consoleInput || (fileNode && ("TEXT".equals(node.configuration.get("readMode")) || "TEXT".equals(node.configuration.get("writeMode")))) || "BASE64_DECODE".equals(node.type) || "BASE64URL_DECODE".equals(node.type) || "HEX_DECODE".equals(node.type) || "UTF8_ENCODE".equals(node.type) || "UTF8_DECODE".equals(node.type);
        charsetFieldGroup.setVisible(needsCharset); charsetFieldGroup.setManaged(needsCharset);
        hashAlgorithmFieldGroup.setVisible(hashNode); hashAlgorithmFieldGroup.setManaged(hashNode);
        nodeCharsetCombo.setValue(node.configuration.getOrDefault("charset", "UTF-8")); hashAlgorithmCombo.setValue(node.configuration.getOrDefault("algorithm", "SHA-256"));

        boolean randomBytes = "RANDOM_BYTES".equals(node.type);
        if (randomBytesFieldGroup != null) {
            randomBytesFieldGroup.setVisible(randomBytes); randomBytesFieldGroup.setManaged(randomBytes);
            if (randomBytes) randomBytesLengthField.setText(node.configuration.getOrDefault("length", "16"));
        }

        if (fileModeFieldGroup != null) { fileModeFieldGroup.setVisible(false); fileModeFieldGroup.setManaged(false); }
        if (fileNode) {
            if (fileModeFieldGroup != null) {
                fileModeFieldGroup.setVisible(true); fileModeFieldGroup.setManaged(true);
                if (fileModeLabel != null) {
                    fileModeLabel.setText("FILE_INPUT".equals(node.type) ? "Read mode" : "Write mode");
                }
                String rm = "FILE_INPUT".equals(node.type) ?
                    selected.configuration.getOrDefault("readMode", "BINARY") :
                    selected.configuration.getOrDefault("writeMode", selected.configuration.getOrDefault("readMode", "BINARY"));
                fileModeCombo.setValue("TEXT".equals(rm) ? "Text" : "Binary (raw bytes)");
            }
        }
        updateRepresentationContract(node);
if (cryptoAlgorithmFieldGroup != null) {
            cryptoAlgorithmFieldGroup.setVisible(hasCryptoAlg); cryptoAlgorithmFieldGroup.setManaged(hasCryptoAlg);
            if (hasCryptoAlg) {
                if (encrypt || decrypt) {
                    java.util.List<String> items = new java.util.ArrayList<>();
                    String lastCategory = null;
                    for (com.cryptocarver.model.process.handlers.SymmetricCipherSpec spec : com.cryptocarver.model.process.handlers.SymmetricCipherSpec.values()) {
                        if (!spec.category.equals(lastCategory)) {
                            items.add("--- " + spec.category + " ---");
                            lastCategory = spec.category;
                        }
                        items.add(spec.algorithm);
                    }
                    cryptoAlgorithmCombo.getItems().setAll(items);
                } else if (mac) {
                    cryptoAlgorithmCombo.getItems().setAll(
                            "HmacSHA256", "HmacSHA384", "HmacSHA512",
                            "CMAC-AES", "CMAC-3DES");
                } else if (sign || verify) {
                    cryptoAlgorithmCombo.getItems().setAll("SHA256withRSA", "SHA256withECDSA");
                } else if (wssEncrypt) {
                    cryptoAlgorithmCombo.getItems().setAll(
                            "AES-128-GCM", "AES-256-GCM", "AES-128-CBC", "AES-256-CBC");
                } else if (wssSign) {
                    cryptoAlgorithmCombo.getItems().setAll(
                            "RSA_SHA256", "RSA_SHA384", "RSA_SHA512",
                            "ECDSA_SHA256", "ECDSA_SHA384", "ECDSA_SHA512");
                }

                String fallbackAlg = "AES/GCM/NoPadding";
                if (mac) fallbackAlg = "HmacSHA256";
                if (sign || verify) fallbackAlg = "SHA256withRSA";
                if (wssEncrypt) fallbackAlg = "AES-256-GCM";
                if (wssSign) fallbackAlg = "RSA_SHA256";

                String configuredAlg = wssEncrypt
                        ? node.configuration.get("dataAlgorithm")
                        : wssSign ? node.configuration.get("signatureAlgorithm")
                        : node.configuration.get("algorithm");
                if ("AES/CBC/PKCS5Padding".equals(configuredAlg)) {
                    configuredAlg = "AES/CBC/PKCS7Padding";
                    node.configuration.put("algorithm", configuredAlg);
                }
                if (configuredAlg != null && cryptoAlgorithmCombo.getItems().contains(configuredAlg)) {
                    cryptoAlgorithmCombo.setValue(configuredAlg);
                } else {
                    cryptoAlgorithmCombo.setValue(fallbackAlg);
                }
                if (wssEncrypt) {
                    boolean authenticated = cryptoAlgorithmCombo.getValue().endsWith("GCM");
                    cryptoHelpLabel.setText(authenticated
                            ? "SOAP Body content encryption. Authenticated encryption: yes."
                            : "SOAP Body content encryption. Authenticated encryption: no.");
                    cryptoWarningLabel.setText("WARNING: CBC encryption is not authenticated.");
                    cryptoWarningLabel.setVisible(!authenticated);
                    cryptoWarningLabel.setManaged(!authenticated);
                }
            }
        }
        if (wssKeyTransportFieldGroup != null) {
            wssKeyTransportFieldGroup.setVisible(wssEncrypt);
            wssKeyTransportFieldGroup.setManaged(wssEncrypt);
            if (wssEncrypt) {
                wssKeyTransportCombo.setValue(node.configuration.getOrDefault(
                        "keyTransportAlgorithm", "RSA-OAEP SHA-256"));
            }
        }
        if (wssTimestampFieldGroup != null) {
            wssTimestampFieldGroup.setVisible(wssSign);
            wssTimestampFieldGroup.setManaged(wssSign);
            if (wssSign) {
                boolean enabled = Boolean.parseBoolean(node.configuration.getOrDefault("timestampEnabled", "false"));
                wssTimestampEnabledCheck.setSelected(enabled);
                wssTimestampMinutesField.setText(node.configuration.getOrDefault("timestampMinutes", "5"));
                wssTimestampMinutesField.setDisable(!enabled);
                wssTimestampSignedCheck.setSelected(Boolean.parseBoolean(
                        node.configuration.getOrDefault("timestampSigned", "true")));
                wssTimestampSignedCheck.setDisable(!enabled);
            }
        }
        boolean usernameNode = wssUsernameAdd || wssUsernameVerify;
        if (wssUsernameFieldGroup != null) {
            wssUsernameFieldGroup.setVisible(usernameNode);
            wssUsernameFieldGroup.setManaged(usernameNode);
            if (usernameNode) {
                wssUsernameLabel.setText(wssUsernameVerify ? "Expected username" : "Username");
                wssPasswordLabel.setText(wssUsernameVerify ? "Expected password" : "Password");
                wssUsernameField.setText(node.configuration.getOrDefault("username", ""));
                wssPasswordField.setText("");
            }
        }
        if (wssPasswordTypeFieldGroup != null) {
            wssPasswordTypeFieldGroup.setVisible(wssUsernameAdd);
            wssPasswordTypeFieldGroup.setManaged(wssUsernameAdd);
            if (wssUsernameAdd) {
                wssPasswordTypeCombo.setValue(node.configuration.getOrDefault("passwordType", "PasswordDigest"));
            }
        }
        if (wssTokenAgeFieldGroup != null) {
            wssTokenAgeFieldGroup.setVisible(wssUsernameVerify);
            wssTokenAgeFieldGroup.setManaged(wssUsernameVerify);
            if (wssUsernameVerify) {
                wssTokenAgeField.setText(node.configuration.getOrDefault("maxAgeSeconds", "300"));
            }
        }
        if (keyFormatFieldGroup != null) {
            keyFormatFieldGroup.setVisible(hasKeyFormat); keyFormatFieldGroup.setManaged(hasKeyFormat);
            if (hasKeyFormat) keyFormatCombo.setValue(node.configuration.getOrDefault("keyFormat", "HEX"));
        }
        if (manualKeyFieldGroup != null) {
            manualKeyFieldGroup.setVisible(hasManualKey); manualKeyFieldGroup.setManaged(hasManualKey);
            if (hasManualKey) manualKeyField.setText("");
        }
        if (nonceFieldGroup != null) {
            boolean showGroup = hasNonce;
            if (showGroup && (encrypt || decrypt) && cryptoAlgorithmCombo != null && cryptoAlgorithmCombo.getValue() != null) {
                try {
                    showGroup = com.cryptocarver.model.process.handlers.SymmetricCipherSpec.fromAlgorithm(cryptoAlgorithmCombo.getValue()).ivLength > 0;
                } catch (Exception e) {}

                nonceFieldGroup.setVisible(showGroup); nonceFieldGroup.setManaged(showGroup);
                if (showGroup) nonceField.setText(node.configuration.getOrDefault("nonce", ""));
                generateNonceCheck.setSelected(Boolean.parseBoolean(node.configuration.getOrDefault("generateNonce", "true")));
                generateNonceCheck.setVisible(encrypt);
                updateNonceLabel();
            }
        }
        if (cipherOutputFormatFieldGroup != null) {
            boolean showFormat = encrypt || decrypt;
            cipherOutputFormatFieldGroup.setVisible(showFormat);
            cipherOutputFormatFieldGroup.setManaged(showFormat);
            if (showFormat) {
                String fmt = node.configuration.getOrDefault("outputFormat", "RAW");
                boolean disableEnvelope = false;
                if (cryptoAlgorithmCombo != null && cryptoAlgorithmCombo.getValue() != null && !cryptoAlgorithmCombo.getValue().startsWith("---")) {
                    try {
                        com.cryptocarver.model.process.handlers.SymmetricCipherSpec spec = com.cryptocarver.model.process.handlers.SymmetricCipherSpec.fromAlgorithm(cryptoAlgorithmCombo.getValue());
                        disableEnvelope = !spec.supportsEnvelope;
                        if (disableEnvelope) {
                            fmt = "RAW";
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                cipherOutputFormatCombo.setValue(fmt);
                cipherOutputFormatCombo.setDisable(disableEnvelope);
            }
        }
        updateAadPortHint();
        updatePortBindings(node);

        if (keystorePathFieldGroup != null) {
            keystorePathFieldGroup.setVisible(hasKeystore); keystorePathFieldGroup.setManaged(hasKeystore);
            if (hasKeystore) keystorePathField.setText(node.configuration.getOrDefault("keystorePath", ""));
        }
        if (keystoreTypeFieldGroup != null) {
            keystoreTypeFieldGroup.setVisible(hasKeystore); keystoreTypeFieldGroup.setManaged(hasKeystore);
            if (hasKeystore) keystoreTypeCombo.setValue(node.configuration.getOrDefault("keystoreType", "PKCS12"));
        }
        if (aliasFieldGroup != null) {
            boolean showAlias = sign || wssSign;
            aliasFieldGroup.setVisible(showAlias); aliasFieldGroup.setManaged(showAlias);
            if (showAlias) aliasField.setText(node.configuration.getOrDefault("alias", ""));
        }
        if (keystorePasswordFieldGroup != null) {
            keystorePasswordFieldGroup.setVisible(hasKeystore); keystorePasswordFieldGroup.setManaged(hasKeystore);
            if (hasKeystore) keystorePasswordField.setText("");
        }
        if (keyPasswordFieldGroup != null) {
            keyPasswordFieldGroup.setVisible(hasKeystore); keyPasswordFieldGroup.setManaged(hasKeystore);
            if (hasKeystore) keyPasswordField.setText("");
        }

        if (materialPathFieldGroup != null) {
            materialPathFieldGroup.setVisible(hasMaterial); materialPathFieldGroup.setManaged(hasMaterial);
            if (hasMaterial) materialPathField.setText(node.configuration.getOrDefault("materialPath", ""));
        }
        if (materialTypeFieldGroup != null) {
            materialTypeFieldGroup.setVisible(verify); materialTypeFieldGroup.setManaged(verify);
            if (verify) materialTypeCombo.setValue(node.configuration.getOrDefault("materialType", "CERTIFICATE"));
        }
        if (secretsWarningLabel != null) {
            secretsWarningLabel.setVisible(hasSecrets); secretsWarningLabel.setManaged(hasSecrets);
        }
        if (keyMaterialFieldGroup != null) {
            keyMaterialFieldGroup.setVisible(keyMaterial); keyMaterialFieldGroup.setManaged(keyMaterial);
            if (keyMaterial) {
                if ("RSA_KEYPAIR_GENERATE".equals(node.type)) keySizeCombo.getItems().setAll("2048", "3072", "4096");
                else keySizeCombo.getItems().setAll("128", "192", "256");
                keySizeCombo.setValue(node.configuration.getOrDefault("keySize", "256"));
                boolean kdf = "KDF_PBKDF2".equals(node.type);
                kdfIterationsField.setVisible(kdf); kdfIterationsField.setManaged(kdf);
                kdfSaltField.setVisible(kdf); kdfSaltField.setManaged(kdf);
                if (kdf) {
                    kdfIterationsField.setText(node.configuration.getOrDefault("iterations", "210000"));
                    kdfSaltField.setText(node.configuration.getOrDefault("salt", ""));
                }
            }
        }
        if (symmetricKeyAlgorithmFieldGroup != null) {
            boolean symmetricKeyGenerator = "AES_KEY_GENERATE".equals(node.type);
            symmetricKeyAlgorithmFieldGroup.setVisible(symmetricKeyGenerator);
            symmetricKeyAlgorithmFieldGroup.setManaged(symmetricKeyGenerator);
            if (symmetricKeyGenerator && symmetricKeyAlgorithmCombo != null) {
                String keyAlgorithm = node.configuration.getOrDefault("keyAlgorithm", "AES");
                symmetricKeyAlgorithmCombo.setValue(keyAlgorithm);
                updateSymmetricKeyGeneratorSize(keyAlgorithm, node.configuration.getOrDefault("keySize", "256"));
            }
        }

        redraw();
        updateSelectionUi();
    }

    private void updateSymmetricKeyGeneratorSize(String keyAlgorithm, String configuredSize) {
        if (keySizeCombo == null) return;
        if ("3DES".equals(keyAlgorithm)) {
            keySizeCombo.getItems().setAll("168 (24 bytes)");
            keySizeCombo.setValue("168 (24 bytes)");
            keySizeCombo.setDisable(true);
        } else {
            keySizeCombo.getItems().setAll("128", "192", "256");
            keySizeCombo.setValue(configuredSize);
            keySizeCombo.setDisable(false);
        }
    }

    private void updateNonceLabel() {
        if (nonceLabel == null || cryptoAlgorithmCombo == null) return;
        String algorithm = cryptoAlgorithmCombo.getValue();
        if ("ENCRYPT".equals(selected.type) || "DECRYPT".equals(selected.type)) {
            try {
                com.cryptocarver.model.process.handlers.SymmetricCipherSpec spec = com.cryptocarver.model.process.handlers.SymmetricCipherSpec.fromAlgorithm(algorithm);
                nonceLabel.setText(spec.helpText);
                if ("AES/ECB/PKCS7Padding".equals(algorithm)) {
                    nonceLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                } else {
                    nonceLabel.setStyle("");
                }
            } catch(Exception e) {}
            return;
        }
        if ("AES/GCM/NoPadding".equals(algorithm)) {
            nonceLabel.setText("Nonce (12 bytes / 96 bits for AES-GCM)");
        } else if ("AES/CBC/PKCS7Padding".equals(algorithm) || "AES/CTR/NoPadding".equals(algorithm)) {
            nonceLabel.setText("IV (16 bytes / AES block size)");
        } else {
            nonceLabel.setText("Nonce / IV");
        }
    }

    private void updateAadPortHint() {
        if (aadPortHintGroup == null) return;
        boolean aad = false;
        if (selected != null && cryptoAlgorithmCombo != null) {
            String algorithm = cryptoAlgorithmCombo.getValue();
            if ("ENCRYPT".equals(selected.type) || "DECRYPT".equals(selected.type)) {
                try {
                    aad = com.cryptocarver.model.process.handlers.SymmetricCipherSpec.fromAlgorithm(algorithm).aead;
                } catch (Exception e) {}
            }
        }
        aadPortHintGroup.setVisible(aad);
        aadPortHintGroup.setManaged(aad);
    }

    private void updatePortBindings(ProcessDefinition.Node node) {
        if (portBindingsFieldGroup == null || portBindingsLabel == null) return;
        List<ProcessDefinition.Connection> incoming = connections.stream().filter(c -> node.id.equals(c.to)).toList();
        if (incoming.isEmpty()) {
            portBindingsFieldGroup.setVisible(false);
            portBindingsFieldGroup.setManaged(false);
            return;
        }
        List<com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition> ports = ProcessEngine.getHandlerFor(node.type).inputPorts(node);
        StringBuilder bindings = new StringBuilder();
        for (com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition port : ports) {
            for (ProcessDefinition.Connection connection : incoming) {
                if (port.name().equals(connection.targetPort)) {
                    if (!bindings.isEmpty()) bindings.append('\n');
                    bindings.append(port.name()).append(" ← ").append(sourceDescription(connection.from));
                }
            }
        }
        if (bindings.isEmpty()) {
            portBindingsFieldGroup.setVisible(false);
            portBindingsFieldGroup.setManaged(false);
            return;
        }
        portBindingsLabel.setText(bindings.toString());
        portBindingsFieldGroup.setVisible(true);
        portBindingsFieldGroup.setManaged(true);
    }

    private String sourceDescription(String sourceId) {
        ProcessDefinition.Node source = nodes.stream().filter(node -> sourceId.equals(node.id)).findFirst().orElse(null);
        if (source == null) return sourceId;
        if ("CONSOLE_INPUT".equals(source.type)) {
            String value = source.configuration.getOrDefault("value", "").replace('\n', ' ');
            return source.label + " (\"" + (value.length() > 40 ? value.substring(0, 40) + "…" : value) + "\")";
        }
        return source.label;
    }
    private void updateSelectionUi() {
        if (connectSelectedButton == null && connectMenuButton == null) return;
        int count = selectedNodeIds.size();

        if (count == 2) {
            List<String> pair = orderedConnectionPair();
            ProcessDefinition.Node dest = nodes.stream().filter(n -> n.id.equals(pair.get(1))).findFirst().orElse(null);
            if (dest != null) {
                com.cryptocarver.model.process.ProcessNodeHandler handler = ProcessEngine.getHandlerFor(dest.type);
                List<com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition> ports = handler != null ? handler.inputPorts(dest) : List.of();
                ProcessDefinition.Node sourceNode = nodes.stream().filter(n -> n.id.equals(pair.get(0))).findFirst().orElse(null);
                Representation sourceRepresentation = sourceNode == null ? null : outputRepresentationOf(sourceNode);
                List<com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition> availablePorts = ports.stream()
                        .filter(port -> connections.stream().noneMatch(c -> c.to.equals(dest.id) && port.name().equals(c.targetPort)))
                        .filter(port -> sourceRepresentation == null || port.acceptedRepresentations().contains(sourceRepresentation))
                        .toList();

                if (availablePorts.size() > 1) {
                    if (connectSelectedButton != null) { connectSelectedButton.setVisible(false); connectSelectedButton.setManaged(false); }
                    if (connectMenuButton != null) {
                        connectMenuButton.getItems().clear();
                        connectMenuButton.setText("Connect " + nodeLabel(pair.get(0)) + " to...");
                        for (com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition port : availablePorts) {
                            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem("Connect to " + port.name());
                            item.setOnAction(e -> connectToPort(port.name()));
                            connectMenuButton.getItems().add(item);
                        }
                        connectMenuButton.setVisible(true); connectMenuButton.setManaged(true);
                    }
                } else {
                    if (connectMenuButton != null) {
                        connectMenuButton.getItems().clear();
                        connectMenuButton.setVisible(false);
                        connectMenuButton.setManaged(false);
                    }
                    if (connectSelectedButton != null) {
                        connectSelectedButton.setVisible(true); connectSelectedButton.setManaged(true);
                        connectSelectedButton.setDisable(availablePorts.isEmpty());
                        connectSelectedButton.setText(availablePorts.isEmpty()
                                ? "No compatible free input ports"
                                : "Connect " + nodeLabel(pair.get(0)) + " → " + nodeLabel(pair.get(1)));
                        connectSelectedButton.setOnAction(e -> connectToPort(availablePorts.isEmpty() ? null : availablePorts.get(0).name()));
                    }
                }
            }
        } else {
            if (connectMenuButton != null) { connectMenuButton.setVisible(false); connectMenuButton.setManaged(false); }
            if (connectSelectedButton != null) {
                connectSelectedButton.setVisible(true); connectSelectedButton.setManaged(true);
                connectSelectedButton.setDisable(true);
                connectSelectedButton.setText("Select 2 blocks to connect");
            }
        }

        boolean hasSelectedConnection = selectedConnection != null || connectionBetweenSelectedNodes() != null;
        if (reverseConnectionButton != null) reverseConnectionButton.setDisable(!hasSelectedConnection);
        if (reverseConnectionToolbarButton != null) reverseConnectionToolbarButton.setDisable(!hasSelectedConnection);
        if (deleteSelectedButton != null) deleteSelectedButton.setText(hasSelectedConnection ? "Delete selected connection (Del)" : "Delete selected (Del)");
    }

    private Representation outputRepresentationOf(ProcessDefinition.Node node) {
        try {
            return ProcessEngine.getHandlerFor(node.type).outputRepresentation(node, Map.of());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void updateRepresentationContract(ProcessDefinition.Node node) {
        if (inputContractLabel == null || outputContractLabel == null || node == null) return;
        String input = "Input: BINARY";
        String output = "Output: BINARY";
        if ("CONSOLE_INPUT".equals(node.type)) {
            input = "Input: N/A";
            output = "Output: TEXT_UTF8";
        } else if ("FILE_INPUT".equals(node.type)) {
            input = "Input: N/A";
            output = "Output: BINARY or TEXT_UTF8";
        } else if ("UTF8_ENCODE".equals(node.type)) {
            input = "Input: TEXT_UTF8";
            output = "Output: BINARY (UTF-8 bytes)";
        } else if ("UTF8_DECODE".equals(node.type)) {
            input = "Input: BINARY (UTF-8 bytes)";
            output = "Output: TEXT_UTF8";
        } else if ("VERIFY".equals(node.type)) {
            output = "Output: TEXT_UTF8 (VALID/INVALID)";
        } else if (node.type.endsWith("_ENCODE")) {
            output = "Output: " + node.type.replace("_ENCODE", "");
        } else if (node.type.endsWith("_DECODE")) {
            input = "Input: " + node.type.replace("_DECODE", "") + " or TEXT_UTF8";
        }
        inputContractLabel.setText(input);
        outputContractLabel.setText(output);
        inputContractLabel.setVisible(true); inputContractLabel.setManaged(true);
        outputContractLabel.setVisible(true); outputContractLabel.setManaged(true);
    }

    /**
     * Inputs produce values and outputs consume them.  Honour selection order for
     * transformations, but reverse an accidental output-to-operation selection so
     * Encrypt + Console output always creates Encrypt → Console output.
     */
    private List<String> orderedConnectionPair() {
        List<String> pair = new ArrayList<>(selectedNodeIds);
        if (pair.size() != 2) return pair;
        ProcessDefinition.Node first = nodes.stream().filter(n -> n.id.equals(pair.get(0))).findFirst().orElse(null);
        ProcessDefinition.Node second = nodes.stream().filter(n -> n.id.equals(pair.get(1))).findFirst().orElse(null);
        if (first == null || second == null) return pair;
        if ((isOutputNode(first) && !isOutputNode(second)) || (isInputNode(second) && !isInputNode(first))) {
            return List.of(second.id, first.id);
        }
        return pair;
    }

    private static boolean isInputNode(ProcessDefinition.Node node) {
        return "CONSOLE_INPUT".equals(node.type) || "FILE_INPUT".equals(node.type)
                || "RANDOM_BYTES".equals(node.type) || "AES_KEY_GENERATE".equals(node.type)
                || "KDF_PBKDF2".equals(node.type) || "RSA_KEYPAIR_GENERATE".equals(node.type);
    }

    private static boolean isReusableKeySource(ProcessDefinition.Node node) {
        return node != null && ("AES_KEY_GENERATE".equals(node.type)
                || "KDF_PBKDF2".equals(node.type) || "RSA_KEYPAIR_GENERATE".equals(node.type));
    }

    private static boolean isOutputNode(ProcessDefinition.Node node) {
        return "CONSOLE_OUTPUT".equals(node.type) || "FILE_OUTPUT".equals(node.type);
    }
    private String nodeLabel(String id) {
        return nodes.stream().filter(n -> n.id.equals(id)).map(n -> n.label).findFirst().orElse(id);
    }
    private ProcessDefinition.Connection connectionBetweenSelectedNodes() {
        if (selectedNodeIds.size() != 2) return null;
        List<String> pair = new ArrayList<>(selectedNodeIds);
        return connections.stream().filter(c -> (c.from.equals(pair.get(0)) && c.to.equals(pair.get(1)))
                || (c.from.equals(pair.get(1)) && c.to.equals(pair.get(0)))).findFirst().orElse(null);
    }
    private void addConnectionView(ProcessDefinition.Connection connection, ProcessDefinition.Node from, ProcessDefinition.Node to) {
        double startX = from.x + 150;
        double startY = from.y + 35;
        double endX = to.x - 7;
        double endY = to.y + 35;

        if (connection.targetPort != null) {
            List<com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition> targetPorts = ProcessEngine.getHandlerFor(to.type).inputPorts(to);
            int portIndex = -1;
            for (int index = 0; index < targetPorts.size(); index++) {
                if (connection.targetPort.equals(targetPorts.get(index).name())) {
                    portIndex = index;
                    break;
                }
            }
            if (portIndex >= 0) {
                endY += (portIndex - (targetPorts.size() - 1) / 2.0) * 13;
            }

            Label portLabel = new Label(connection.targetPort);
            portLabel.setStyle("-fx-text-fill: #f6c344; -fx-font-size: 9px; -fx-background-color: #202a33;");
            portLabel.setLayoutX(endX - 35);
            portLabel.setLayoutY(endY - 15);
            workflowCanvas.getChildren().add(portLabel);
        }

        Line line = new Line(startX, startY, endX, endY);
        boolean isSelected = connection == selectedConnection;
        line.setStyle(isSelected ? "-fx-stroke: #f6c344; -fx-stroke-width: 4;" : "-fx-stroke: #5d9bd3; -fx-stroke-width: 3;");
        line.setOnMouseClicked(event -> selectConnection(connection));
        Polygon arrow = arrowHead(startX, startY, endX, endY, isSelected ? "#f6c344" : "#5d9bd3");
        arrow.setOnMouseClicked(event -> selectConnection(connection));
        workflowCanvas.getChildren().addAll(line, arrow);
    }
    private Polygon arrowHead(double startX, double startY, double endX, double endY, String color) {
        double angle = Math.atan2(endY - startY, endX - startX);
        double length = 12;
        double spread = Math.PI / 7;
        Polygon arrow = new Polygon(
                endX, endY,
                endX - length * Math.cos(angle - spread), endY - length * Math.sin(angle - spread),
                endX - length * Math.cos(angle + spread), endY - length * Math.sin(angle + spread));
        arrow.setStyle("-fx-fill: " + color + ";");
        return arrow;
    }
    private void selectConnection(ProcessDefinition.Connection connection) {
        saveSelectedNodeSettings();
        selected = null;
        selectedNodeIds.clear();
        selectedConnection = connection;
        selectedNodeLabel.setText("Connection: " + nodeLabel(connection.from) + " → " + nodeLabel(connection.to));
        if (nodeNameFieldGroup != null) {
            nodeNameFieldGroup.setVisible(false);
            nodeNameFieldGroup.setManaged(false);
        }
        nodeValueArea.clear(); nodePathField.clear();
        consoleValueFieldGroup.setVisible(false); consoleValueFieldGroup.setManaged(false);
        filePathFieldGroup.setVisible(false); filePathFieldGroup.setManaged(false);
        charsetFieldGroup.setVisible(false); charsetFieldGroup.setManaged(false);
        hashAlgorithmFieldGroup.setVisible(false); hashAlgorithmFieldGroup.setManaged(false);

        if (fileModeFieldGroup != null) { fileModeFieldGroup.setVisible(false); fileModeFieldGroup.setManaged(false); }
        if (inputContractLabel != null) { inputContractLabel.setVisible(false); inputContractLabel.setManaged(false); }
        if (outputContractLabel != null) { outputContractLabel.setVisible(false); outputContractLabel.setManaged(false); }
if (cryptoAlgorithmFieldGroup != null) { cryptoAlgorithmFieldGroup.setVisible(false); cryptoAlgorithmFieldGroup.setManaged(false); }
        if (wssKeyTransportFieldGroup != null) { wssKeyTransportFieldGroup.setVisible(false); wssKeyTransportFieldGroup.setManaged(false); }
        if (wssTimestampFieldGroup != null) { wssTimestampFieldGroup.setVisible(false); wssTimestampFieldGroup.setManaged(false); }
        if (wssUsernameFieldGroup != null) { wssUsernameFieldGroup.setVisible(false); wssUsernameFieldGroup.setManaged(false); }
        if (wssPasswordTypeFieldGroup != null) { wssPasswordTypeFieldGroup.setVisible(false); wssPasswordTypeFieldGroup.setManaged(false); }
        if (wssTokenAgeFieldGroup != null) { wssTokenAgeFieldGroup.setVisible(false); wssTokenAgeFieldGroup.setManaged(false); }
        if (keyFormatFieldGroup != null) { keyFormatFieldGroup.setVisible(false); keyFormatFieldGroup.setManaged(false); }
        if (manualKeyFieldGroup != null) { manualKeyFieldGroup.setVisible(false); manualKeyFieldGroup.setManaged(false); }
        if (nonceFieldGroup != null) { nonceFieldGroup.setVisible(false); nonceFieldGroup.setManaged(false); }
        if (aadPortHintGroup != null) { aadPortHintGroup.setVisible(false); aadPortHintGroup.setManaged(false); }
        if (portBindingsFieldGroup != null) { portBindingsFieldGroup.setVisible(false); portBindingsFieldGroup.setManaged(false); }
        if (cipherOutputFormatFieldGroup != null) { cipherOutputFormatFieldGroup.setVisible(false); cipherOutputFormatFieldGroup.setManaged(false); }
        if (keystorePathFieldGroup != null) { keystorePathFieldGroup.setVisible(false); keystorePathFieldGroup.setManaged(false); }
        if (keystoreTypeFieldGroup != null) { keystoreTypeFieldGroup.setVisible(false); keystoreTypeFieldGroup.setManaged(false); }
        if (aliasFieldGroup != null) { aliasFieldGroup.setVisible(false); aliasFieldGroup.setManaged(false); }
        if (keystorePasswordFieldGroup != null) { keystorePasswordFieldGroup.setVisible(false); keystorePasswordFieldGroup.setManaged(false); }
        if (keyPasswordFieldGroup != null) { keyPasswordFieldGroup.setVisible(false); keyPasswordFieldGroup.setManaged(false); }
        if (materialPathFieldGroup != null) { materialPathFieldGroup.setVisible(false); materialPathFieldGroup.setManaged(false); }
        if (materialTypeFieldGroup != null) { materialTypeFieldGroup.setVisible(false); materialTypeFieldGroup.setManaged(false); }
        if (secretsWarningLabel != null) { secretsWarningLabel.setVisible(false); secretsWarningLabel.setManaged(false); }
        if (keyMaterialFieldGroup != null) { keyMaterialFieldGroup.setVisible(false); keyMaterialFieldGroup.setManaged(false); }
        updateSelectionUi();
        redraw();
    }
}
