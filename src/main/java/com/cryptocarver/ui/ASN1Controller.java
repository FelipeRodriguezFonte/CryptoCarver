package com.cryptocarver.ui;

import com.cryptocarver.asn1.ASN1Parser;
import com.cryptocarver.asn1.ASN1TreeNode;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.model.OperationDetail;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ASN1Controller {

    private static final Logger LOG = LoggerFactory.getLogger(ASN1Controller.class);

    private StatusReporter reporter;

    @FXML private TitledPane asn1Pane;
    @FXML private TabPane asn1TabPane;
    @FXML private ComboBox<String> asn1InputFormatCombo;
    @FXML private ComboBox<String> asn1TypeCombo;
    @FXML private TextArea asn1InputArea;
    @FXML private TreeView<ASN1TreeNode> asn1TreeView;
    @FXML private TextArea asn1DetailsArea;
    @FXML private Label asn1StatusLabel;
    @FXML private CheckBox asn1StrictDerCheck;

    @FXML private ComboBox<String> asn1EncodeTypeCombo;
    @FXML private ComboBox<String> asn1EncodeInputFormatCombo;
    @FXML private TextArea asn1EncodeInputArea;
    @FXML private TextArea asn1EncodeOutputArea;

    private byte[] asn1LastParsedData;

    public void init(StatusReporter reporter) {
        this.reporter = reporter;
        initializeASN1();
    }

    @FXML
    public void initialize() {
        setupASN1TreeView();
    }

    private void initializeASN1() {
        if (asn1InputFormatCombo != null) {
            asn1InputFormatCombo.getItems().clear();
            asn1InputFormatCombo.getItems().addAll("Hexadecimal", "Base64", "Base64 (PEM)");
            asn1InputFormatCombo.setValue("Hexadecimal");
        }

        if (asn1TypeCombo != null) {
            asn1TypeCombo.getItems().clear();
            asn1TypeCombo.getItems().addAll(
                    "Auto-detect",
                    "X.509 Certificate",
                    "RSA Private Key",
                    "RSA Public Key",
                    "EC Private Key",
                    "Certificate Signing Request",
                    "Simple SEQUENCE");
            asn1TypeCombo.setValue("Auto-detect");
        }

        if (asn1EncodeTypeCombo != null) {
            asn1EncodeTypeCombo.getItems().addAll(
                    "INTEGER",
                    "OCTET STRING",
                    "BIT STRING",
                    "OBJECT IDENTIFIER (OID)",
                    "UTF8String",
                    "PrintableString",
                    "IA5String",
                    "BOOLEAN",
                    "NULL",
                    "SEQUENCE (from Hex content)",
                    "SET (from Hex content)");
            asn1EncodeTypeCombo.setValue("UTF8String");

            asn1EncodeInputFormatCombo.getItems().addAll("Text", "Hex", "Base64");
            asn1EncodeInputFormatCombo.setValue("Text");

            asn1EncodeTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    if (newVal.equals("NULL")) {
                        asn1EncodeInputArea.setDisable(true);
                        asn1EncodeInputArea.setText("");
                        asn1EncodeInputFormatCombo.setDisable(true);
                    } else if (newVal.contains("SEQUENCE") || newVal.contains("SET")) {
                        asn1EncodeInputArea.setDisable(false);
                        asn1EncodeInputFormatCombo.setValue("Hex");
                        asn1EncodeInputFormatCombo.setDisable(true);
                    } else {
                        asn1EncodeInputArea.setDisable(false);
                        asn1EncodeInputFormatCombo.setDisable(false);
                    }
                }
            });
        }
    }

    @FXML
    private void handleParseASN1() {
        try {
            String inputText = asn1InputArea.getText().trim();
            if (inputText.isEmpty()) {
                if (reporter != null) reporter.showError("Input Error", "Please enter ASN.1 data in the input area");
                return;
            }

            byte[] data = parseASN1InputData(inputText);

            if (data == null || data.length == 0) {
                if (reporter != null) reporter.showError("Parse Error", "Invalid input format");
                return;
            }

            asn1LastParsedData = data;

            com.cryptocarver.asn1.DerValidator.Report derReport = com.cryptocarver.asn1.DerValidator.validate(data);

            if (asn1StrictDerCheck != null && asn1StrictDerCheck.isSelected() && !derReport.validDer()) {
                if (reporter != null) reporter.showError("Strict DER Validation Failed", derReport.message());
                asn1StatusLabel.setText("✗ Invalid DER");
                asn1StatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                return;
            }

            ASN1TreeNode tree = ASN1Parser.parse(data);

            if (asn1TreeView != null) {
                asn1TreeView.setRoot(buildTreeItem(tree));
            }

            String detectedType = ASN1Parser.detectType(data);

            StringBuilder details = new StringBuilder();
            details.append("Detected Type: ").append(detectedType).append("\n");
            details.append("Total Size: ").append(data.length).append(" bytes\n");
            details.append("Root Element: ").append(tree.getLabel()).append("\n");
            details.append("DER Validation: ").append(derReport.validDer() ? "PASS" : "NOT CANONICAL / INVALID")
                    .append(" — ").append(derReport.message()).append("\n");
            details.append("Structure parsed successfully.");

            asn1DetailsArea.setText(details.toString());

            asn1StatusLabel.setText("✓ Parsed successfully - " + detectedType);
            asn1StatusLabel.setStyle("-fx-text-fill: #27ae60;");

            List<OperationDetail> histDetails = new ArrayList<>();
            histDetails.add(OperationDetail.publicDetail("Detected Type", detectedType));
            histDetails.add(OperationDetail.publicDetail("Size", data.length + " bytes"));
            histDetails.add(OperationDetail.publicDetail("Root", tree.getLabel()));

            if (reporter != null) {
                com.cryptocarver.model.OperationResult result = com.cryptocarver.model.OperationResult.forOperation("ASN.1 Parse")
                        .input(data)
                        .details(histDetails)
                        .status("✓ Parsed successfully - " + detectedType)
                        .build();
                reporter.publish(result);
            }

        } catch (Exception e) {
            if (reporter != null) reporter.showError("Parse Error", "Failed to parse ASN.1 data: " + e.getMessage());
            asn1StatusLabel.setText("✗ Parse failed: " + e.getMessage());
            asn1StatusLabel.setStyle("-fx-text-fill: #e74c3c;");
            LOG.error("ASN.1 parse failed", e);
        }
    }

    @FXML
    private void handleLoadASN1Example() {
        String type = asn1TypeCombo.getValue();
        if (type == null || type.equals("Auto-detect")) return;

        String example = "";
        switch (type) {
            case "X.509 Certificate":
                example = "308201A830820111A00302010202090085B0BCA76BC08DA5300D06092A864886F70D01010B05003011310F300D060355040313064D7943657274301E170D32343031303130303030305A170D32353031303130303030305A3011310F300D060355040313064D7943657274305C300D06092A864886F70D0101010500034B003048024100C55E4A13D0F4A0B0C0D0E0F00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF0203010001A3533051301D0603551D0E0416041412345678901234567890123456789012301F0603551D230418301680141234567890123456789012345678901230300F0603551D130101FF040530030101FF300D06092A864886F70D01010B0500034100";
                break;
            case "RSA Public Key":
                example = "30819F300D06092A864886F70D010101050003818D0030818902818100C55E4A13D0F4A0B0C0D0E0F00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF0203010001";
                break;
            case "Simple SEQUENCE":
                example = "300C02012A0C0548656C6C6F";
                break;
            default:
                example = "02012A";
                break;
        }

        asn1InputFormatCombo.setValue("Hexadecimal");
        asn1InputArea.setText(example);
        if (reporter != null) reporter.updateStatus("Loaded example: " + type);
        handleParseASN1();
    }

    @FXML
    private void handleEncodeASN1() {
        try {
            String type = asn1EncodeTypeCombo.getValue();
            String inputFormat = asn1EncodeInputFormatCombo.getValue();
            String inputText = asn1EncodeInputArea.getText();

            if (type == null) return;

            byte[] encoded = null;

            if (type.equals("NULL")) {
                encoded = com.cryptocarver.asn1.ASN1Encoder.encodeNull();
            } else {
                if (inputText == null || inputText.isEmpty()) {
                    if (reporter != null) reporter.showError("Error", "Input cannot be empty for " + type);
                    return;
                }

                if (type.equals("INTEGER")) {
                    int radix = 10;
                    if (inputFormat.equals("Hex")) radix = 16;
                    String numberStr = inputText.trim();
                    encoded = com.cryptocarver.asn1.ASN1Encoder.encodeInteger(numberStr, radix);

                } else if (type.equals("UTF8String")) {
                    String text = inputText;
                    if (inputFormat.equals("Hex"))
                        text = new String(DataConverter.hexToBytes(inputText), java.nio.charset.StandardCharsets.UTF_8);
                    else if (inputFormat.equals("Base64"))
                        text = new String(Base64.getDecoder().decode(inputText), java.nio.charset.StandardCharsets.UTF_8);
                    encoded = com.cryptocarver.asn1.ASN1Encoder.encodeUTF8String(text);

                } else if (type.equals("PrintableString")) {
                    String text = inputText;
                    if (inputFormat.equals("Hex"))
                        text = new String(DataConverter.hexToBytes(inputText), java.nio.charset.StandardCharsets.US_ASCII);
                    encoded = com.cryptocarver.asn1.ASN1Encoder.encodePrintableString(text);

                } else if (type.equals("IA5String")) {
                    String text = inputText;
                    if (inputFormat.equals("Hex"))
                        text = new String(DataConverter.hexToBytes(inputText), java.nio.charset.StandardCharsets.US_ASCII);
                    encoded = com.cryptocarver.asn1.ASN1Encoder.encodeIA5String(text);

                } else if (type.equals("OBJECT IDENTIFIER (OID)")) {
                    encoded = com.cryptocarver.asn1.ASN1Encoder.encodeOID(inputText.trim());

                } else if (type.equals("BOOLEAN")) {
                    boolean val = Boolean.parseBoolean(inputText) || "1".equals(inputText)
                            || "yes".equalsIgnoreCase(inputText) || "true".equalsIgnoreCase(inputText);
                    encoded = com.cryptocarver.asn1.ASN1Encoder.encodeBoolean(val);

                } else if (type.equals("OCTET STRING") || type.equals("BIT STRING") || type.contains("SEQUENCE") || type.contains("SET")) {
                    byte[] data = null;
                    if (inputFormat.equals("Hex"))
                        data = DataConverter.hexToBytes(inputText);
                    else if (inputFormat.equals("Base64"))
                        data = Base64.getDecoder().decode(inputText);
                    else
                        data = inputText.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    if (type.equals("OCTET STRING")) {
                        encoded = com.cryptocarver.asn1.ASN1Encoder.encodeOctetString(data);
                    } else if (type.equals("BIT STRING")) {
                        encoded = com.cryptocarver.asn1.ASN1Encoder.encodeBitString(data);
                    } else if (type.contains("SEQUENCE")) {
                        encoded = com.cryptocarver.asn1.ASN1Encoder.encodeSequence(data);
                    } else if (type.contains("SET")) {
                        encoded = com.cryptocarver.asn1.ASN1Encoder.encodeSet(data);
                    }
                }
            }

            if (encoded != null) {
                asn1EncodeOutputArea.setText(DataConverter.bytesToHex(encoded));

                List<OperationDetail> histDetails = new ArrayList<>();
                histDetails.add(OperationDetail.publicDetail("Type", type));
                histDetails.add(OperationDetail.publicDetail("Input Format", inputFormat));
                histDetails.add(OperationDetail.publicDetail("Input", inputText));
                histDetails.add(OperationDetail.publicDetail("Output", DataConverter.bytesToHex(encoded)));

                if (reporter != null) {
                    com.cryptocarver.model.OperationResult result = com.cryptocarver.model.OperationResult.forOperation("ASN.1 Encode")
                            .output(encoded)
                            .details(histDetails)
                            .status("Encoded successfully")
                            .build();
                    reporter.publish(result);
                }
            }

        } catch (Exception e) {
            if (reporter != null) reporter.showError("ASN.1 Encode Error", e.getMessage());
            LOG.error("ASN.1 encode failed", e);
        }
    }

    @FXML
    private void handleCopyASN1Output() {
        String content = asn1EncodeOutputArea.getText();
        if (content != null && !content.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(content);
            clipboard.setContent(cc);
            if (reporter != null) reporter.updateStatus("Copied ASN.1 output to clipboard");
        }
    }

    @FXML
    private void handleClearASN1() {
        asn1InputArea.clear();
        if (asn1TreeView != null) asn1TreeView.setRoot(null);
        asn1DetailsArea.clear();
        asn1StatusLabel.setText("Ready");
        asn1StatusLabel.setStyle("");
        asn1LastParsedData = null;
    }

    @FXML
    private void handleExportASN1Tree() {
        if (asn1LastParsedData == null || asn1LastParsedData.length == 0) {
            if (reporter != null) reporter.showError("Export Error", "No parsed ASN.1 data available to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save ASN.1 Tree");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialFileName("asn1_tree.txt");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                ASN1TreeNode fullTree = ASN1Parser.parse(asn1LastParsedData, -1);
                String fullTreeString = fullTree.toIndentedString(true);

                writer.println("ASN.1 Structure Tree Export");
                writer.println("===========================");
                writer.println("Date: " + java.time.LocalDateTime.now());
                writer.println("\nInput Size: " + asn1LastParsedData.length + " bytes");
                writer.println("Details:\n" + asn1DetailsArea.getText());
                writer.println("\nStructure (Full View):");
                writer.println("---------------------------");
                writer.println(fullTreeString);

                if (reporter != null) reporter.updateStatus("Full tree exported to " + file.getName());
            } catch (Exception e) {
                if (reporter != null) reporter.showError("Export Failed", "Could not save file: " + e.getMessage());
                LOG.error("Export failed", e);
            }
        }
    }

    @FXML
    private void handleExportASN1Json() { exportASN1Structured("ASN.1 JSON", "asn1_tree.json", "JSON Files", "*.json", true); }

    @FXML
    private void handleExportASN1Markdown() { exportASN1Structured("ASN.1 Markdown", "asn1_tree.md", "Markdown Files", "*.md", false); }

    @FXML
    private void handleLoadASN1OidRegistry() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load ASN.1 OID Registry");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = chooser.showOpenDialog(null);
        if (file == null) return;
        try {
            int loaded = ASN1Parser.loadCustomOidNames(file.toPath());
            if (reporter != null) reporter.updateStatus("Loaded " + loaded + " custom ASN.1 OID name(s)");
            if (asn1DetailsArea != null) asn1DetailsArea.appendText("\nCustom OID registry loaded: " + loaded + " definitions. Re-parse to apply names.\n");
        } catch (Exception e) {
            if (reporter != null) reporter.showError("OID Registry", "Could not load OID registry: " + e.getMessage());
        }
    }

    @FXML
    private void handleCanonicalizeASN1Der() {
        try {
            byte[] input = parseASN1InputData(asn1InputArea.getText().trim());
            byte[] der = com.cryptocarver.asn1.DerValidator.canonicalize(input);
            asn1DetailsArea.appendText("\nCanonical DER: " + der.length + " bytes (input: " + input.length + " bytes)\nDER hex: "
                    + DataConverter.bytesToHex(der) + "\n");
            if (reporter != null) reporter.updateStatus("ASN.1 canonical DER generated (input unchanged)");
        } catch (Exception e) {
            if (reporter != null) reporter.showError("Canonical DER", "Unable to canonicalize ASN.1: " + e.getMessage());
        }
    }

    private void exportASN1Structured(String title, String name, String filterName, String extension, boolean json) {
        if (asn1LastParsedData == null || asn1LastParsedData.length == 0) {
            if (reporter != null) reporter.showError("Export Error", "Parse ASN.1 data before exporting.");
            return;
        }
        FileChooser chooser = new FileChooser(); chooser.setTitle(title); chooser.setInitialFileName(name);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, extension));
        File file = chooser.showSaveDialog(null); if (file == null) return;
        try {
            ASN1TreeNode tree = ASN1Parser.parse(asn1LastParsedData, -1);
            String content = json ? com.cryptocarver.asn1.ASN1TreeExporter.toJson(tree) : com.cryptocarver.asn1.ASN1TreeExporter.toMarkdown(tree);
            Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
            if (reporter != null) reporter.updateStatus("ASN.1 export saved: " + file.getName());
        } catch (Exception e) {
            if (reporter != null) reporter.showError("Export Failed", "Could not export ASN.1 tree: " + e.getMessage());
        }
    }

    private byte[] parseASN1InputData(String input) throws Exception {
        String format = asn1InputFormatCombo.getValue();
        if (format.equals("Hexadecimal")) {
            String hex = input.replaceAll("\\s+", "");
            try {
                return DataConverter.hexToBytes(hex);
            } catch (Exception e) {
                int len = hex.length();
                byte[] data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                            + Character.digit(hex.charAt(i + 1), 16));
                }
                return data;
            }
        } else if (format.equals("Base64")) {
            String base64 = input.replaceAll("\\s+", "");
            return Base64.getDecoder().decode(base64);
        } else if (format.equals("Base64 (PEM)")) {
            String[] lines = input.split("\n");
            StringBuilder base64 = new StringBuilder();
            boolean inData = false;
            boolean foundHeader = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("-----BEGIN")) {
                    inData = true;
                    foundHeader = true;
                    continue;
                }
                if (trimmed.startsWith("-----END")) {
                    break;
                }
                if (inData) {
                    base64.append(trimmed);
                }
            }
            if (!foundHeader) {
                return Base64.getDecoder().decode(input.replaceAll("\\s+", ""));
            }
            return Base64.getDecoder().decode(base64.toString());
        }
        return null;
    }

    private void handleEditASN1Node(ASN1TreeNode node) {
        if (asn1TreeView == null || asn1TreeView.getRoot() == null) return;
        TextInputDialog dialog = new TextInputDialog(DataConverter.bytesToHex(node.getRawValue()));
        dialog.setTitle("Edit ASN.1 Node");
        dialog.setHeaderText("Edit the raw hex of this node (including tag and length).");
        dialog.setContentText("New Hex:");

        dialog.showAndWait().ifPresent(hex -> {
            try {
                byte[] newBytes = DataConverter.hexToBytes(hex);
                ASN1TreeNode rootNode = asn1TreeView.getRoot().getValue();
                byte[] reencoded = com.cryptocarver.asn1.ASN1Editor.editNodeAndReencode(rootNode, node, newBytes);

                asn1LastParsedData = reencoded;
                ASN1TreeNode newTree = ASN1Parser.parse(reencoded);
                asn1TreeView.setRoot(buildTreeItem(newTree));

                asn1InputArea.setText(DataConverter.bytesToHex(reencoded));
                if (reporter != null) reporter.showInfo("Success", "Node edited and tree re-encoded successfully.");
            } catch (Exception ex) {
                if (reporter != null) reporter.showError("Edit Error", "Failed to apply edits: " + ex.getMessage());
            }
        });
    }

    private void handleCompareASN1Hex(ASN1TreeNode node) {
        if (node == null) return;
        String originalHex = DataConverter.bytesToHex(node.getRawValue());
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Compare Hex");
        dialog.setHeaderText("Original Hex: " + originalHex + "\nLength: " + node.getRawValue().length + " bytes");
        dialog.setContentText("Enter Hex to compare:");

        dialog.showAndWait().ifPresent(hex -> {
            try {
                byte[] compareBytes = DataConverter.hexToBytes(hex);
                if (java.util.Arrays.equals(node.getRawValue(), compareBytes)) {
                    if (reporter != null) reporter.showInfo("Compare Result", "The hex matches perfectly.");
                } else {
                    if (reporter != null) reporter.showError("Compare Result", "The hex does NOT match.");
                }
            } catch (Exception ex) {
                if (reporter != null) reporter.showError("Error", "Invalid hex input: " + ex.getMessage());
            }
        });
    }

    private void handleExportASN1Node(ASN1TreeNode node) {
        if (node == null) return;
        List<String> choices = java.util.Arrays.asList("JSON", "Markdown");
        ChoiceDialog<String> dialog = new ChoiceDialog<>("JSON", choices);
        dialog.setTitle("Export ASN.1 Node");
        dialog.setHeaderText("Choose export format");
        dialog.setContentText("Format:");

        dialog.showAndWait().ifPresent(format -> {
            try {
                String result = format.equals("JSON")
                    ? com.cryptocarver.asn1.ASN1TreeExporter.toJson(node)
                    : com.cryptocarver.asn1.ASN1TreeExporter.toMarkdown(node);

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save ASN.1 " + format);
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format + " Files", format.equals("JSON") ? "*.json" : "*.md"));
                fileChooser.setInitialFileName("asn1_node." + (format.equals("JSON") ? "json" : "md"));

                File file = fileChooser.showSaveDialog(null);
                if (file != null) {
                    try (PrintWriter writer = new PrintWriter(file)) {
                        writer.print(result);
                    }
                    if (reporter != null) reporter.showInfo("Success", "Node exported successfully to " + file.getName());
                }
            } catch (Exception ex) {
                if (reporter != null) reporter.showError("Export Error", "Failed to export node: " + ex.getMessage());
            }
        });
    }

    private void setupASN1TreeView() {
        if (asn1TreeView != null) {
            asn1TreeView.setCellFactory(tv -> new TreeCell<ASN1TreeNode>() {
                @Override
                protected void updateItem(ASN1TreeNode item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setContextMenu(null);
                    } else {
                        String offsetHex = String.format("%04X", item.getOffset());
                        String text = "[" + offsetHex + "] " + item.getLabel() + " (L: " + item.getLength() + ")";
                        if (item.getDecodedValue() != null && !item.getDecodedValue().isEmpty()) {
                            text += " : " + item.getDecodedValue();
                        }
                        setText(text);

                        ContextMenu menu = new ContextMenu();
                        MenuItem editItem = new MenuItem("Edit Value");
                        editItem.setOnAction(e -> handleEditASN1Node(item));
                        MenuItem compareItem = new MenuItem("Compare Hex");
                        compareItem.setOnAction(e -> handleCompareASN1Hex(item));
                        MenuItem exportItem = new MenuItem("Export Node");
                        exportItem.setOnAction(e -> handleExportASN1Node(item));

                        menu.getItems().addAll(editItem, compareItem, exportItem);
                        setContextMenu(menu);
                    }
                }
            });
        }
    }

    private TreeItem<ASN1TreeNode> buildTreeItem(ASN1TreeNode node) {
        TreeItem<ASN1TreeNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        for (ASN1TreeNode child : node.getChildren()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }

    public void selectDecodeTab() {
        if (asn1TabPane != null) {
            asn1TabPane.getSelectionModel().select(0);
        }
    }

    public void selectEncodeTab() {
        if (asn1TabPane != null) {
            asn1TabPane.getSelectionModel().select(1);
        }
    }
}
