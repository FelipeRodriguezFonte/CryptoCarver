package com.cryptoforge.ui;

import com.cryptoforge.asn1.ASN1Parser;
import com.cryptoforge.asn1.ASN1TreeNode;
import com.cryptoforge.util.DataConverter;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;

/**
 * Controller for ASN.1 Parser functionality
 */
public class LegacyASN1Controller {

    private final MainController mainController;

    private ComboBox<String> asn1InputFormatCombo;
    private ComboBox<String> asn1TypeCombo;
    private TextArea asn1InputArea;
    private TextArea asn1TreeArea;
    private TextArea asn1DetailsArea;
    private Label asn1StatusLabel;

    // Store parsed data for export
    private byte[] lastParsedData;

    public LegacyASN1Controller(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Initialize ASN.1 components
     */
    public void initialize(ComboBox<String> inputFormatCombo,
            ComboBox<String> typeCombo,
            TextArea inputArea,
            TextArea treeArea,
            TextArea detailsArea,
            Label statusLabel) {
        this.asn1InputFormatCombo = inputFormatCombo;
        this.asn1TypeCombo = typeCombo;
        this.asn1InputArea = inputArea;
        this.asn1TreeArea = treeArea;
        this.asn1DetailsArea = detailsArea;
        this.asn1StatusLabel = statusLabel;

        // Populate input format combo
        asn1InputFormatCombo.getItems().addAll(
                "Hexadecimal",
                "Base64",
                "Base64 (PEM)");
        asn1InputFormatCombo.setValue("Hexadecimal");

        // Populate type combo (for examples)
        asn1TypeCombo.getItems().addAll(
                "Auto-detect",
                "X.509 Certificate",
                "RSA Private Key",
                "RSA Public Key",
                "EC Private Key",
                "Certificate Signing Request",
                "Custom");
        asn1TypeCombo.setValue("Auto-detect");
    }

    /**
     * Handle parse ASN.1 button
     */
    public void handleParseASN1() {
        try {
            // Get input data
            String inputText = asn1InputArea.getText().trim();
            if (inputText.isEmpty()) {
                mainController.showError("Input Error", "Please enter ASN.1 data in the input area");
                return;
            }

            // Parse based on format
            byte[] data = parseInputData(inputText);

            if (data == null || data.length == 0) {
                mainController.showError("Parse Error", "Invalid input format");
                return;
            }

            // Store data for export
            lastParsedData = data;

            // Parse ASN.1 (with truncation for display)
            ASN1TreeNode tree = ASN1Parser.parse(data); // Default: 32 bytes truncation

            // Display tree
            String treeString = tree.toIndentedString();
            asn1TreeArea.setText(treeString);

            // Detect type
            String detectedType = ASN1Parser.detectType(data);

            // Display details
            StringBuilder details = new StringBuilder();
            details.append("Detected Type: ").append(detectedType).append("\n");
            details.append("Total Size: ").append(data.length).append(" bytes\n");
            details.append("Root Element: ").append(tree.getLabel()).append("\n");
            details.append("\n");
            details.append("Structure parsed successfully.\n");
            details.append("Select elements in the tree for more details.");

            asn1DetailsArea.setText(details.toString());

            // Update status
            asn1StatusLabel.setText("✓ Parsed successfully - " + detectedType);
            asn1StatusLabel.setStyle("-fx-text-fill: #27ae60;");

        } catch (Exception e) {
            mainController.showError("Parse Error", "Failed to parse ASN.1 data: " + e.getMessage());
            asn1StatusLabel.setText("✗ Parse failed: " + e.getMessage());
            asn1StatusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    /**
     * Parse input data based on format
     */
    private byte[] parseInputData(String input) throws Exception {
        String format = asn1InputFormatCombo.getValue();

        if (format.equals("Hexadecimal")) {
            // Remove spaces, newlines, etc.
            String hex = input.replaceAll("\\s+", "");
            return DataConverter.hexToBytes(hex);

        } else if (format.equals("Base64")) {
            // Remove whitespace
            String base64 = input.replaceAll("\\s+", "");
            return DataConverter.decodeBase64Flexible(base64);

        } else if (format.equals("Base64 (PEM)")) {
            // Extract base64 from PEM format (between -----BEGIN ... and -----END ...)
            String[] lines = input.split("\n");
            StringBuilder base64 = new StringBuilder();
            boolean inData = false;

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("-----BEGIN")) {
                    inData = true;
                    continue;
                }
                if (line.startsWith("-----END")) {
                    break;
                }
                if (inData) {
                    base64.append(line);
                }
            }

            return DataConverter.decodeBase64Flexible(base64.toString());
        }

        return null;
    }

    /**
     * Handle load file button
     */
    public void handleLoadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open ASN.1 File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificate Files", "*.cer", "*.crt", "*.der", "*.pem"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                byte[] data = Files.readAllBytes(file.toPath());

                // Detect if PEM format
                String content = new String(data);
                if (content.contains("-----BEGIN")) {
                    asn1InputFormatCombo.setValue("Base64 (PEM)");
                    asn1InputArea.setText(content);
                } else {
                    // Binary DER - convert to hex
                    asn1InputFormatCombo.setValue("Hexadecimal");
                    String hex = DataConverter.bytesToHex(data);
                    asn1InputArea.setText(hex);
                }

                mainController.updateStatus("Loaded file: " + file.getName());

                // Auto-parse
                handleParseASN1();

            } catch (Exception e) {
                mainController.showError("File Error", "Failed to load file: " + e.getMessage());
            }
        }
    }

    /**
     * Handle load example button
     */
    public void handleLoadExample() {
        String type = asn1TypeCombo.getValue();
        String example = getExample(type);

        if (example != null) {
            asn1InputFormatCombo.setValue("Hexadecimal");
            asn1InputArea.setText(example);
            mainController.updateStatus("Loaded example: " + type);

            // Auto-parse
            handleParseASN1();
        } else {
            mainController.showError("Example Error", "No example available for: " + type);
        }
    }

    /**
     * Get example data for testing
     */
    private String getExample(String type) {
        switch (type) {
            case "X.509 Certificate":
                // Simple self-signed certificate (DER hex)
                return "308201A830820111A00302010202090085B0BCA76BC08DA5300D06092A864886F70D01010B05003011310F300D060355040313064D7943657274301E170D3234303130313030303030305A170D3235303130313030303030305A3011310F300D060355040313064D7943657274305C300D06092A864886F70D0101010500034B003048024100C55E4A13D0F4A0B0C0D0E0F00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF0203010001A3533051301D0603551D0E0416041412345678901234567890123456789012301F0603551D230418301680141234567890123456789012345678901230300F0603551D130101FF040530030101FF300D06092A864886F70D01010B0500034100";

            case "RSA Public Key":
                // RSA public key (modulus + exponent)
                return "30819F300D06092A864886F70D010101050003818D0030818902818100C55E4A13D0F4A0B0C0D0E0F00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF0203010001";

            case "Simple SEQUENCE":
                // SEQUENCE { INTEGER 42, UTF8String "Hello" }
                return "300C02012A0C0548656C6C6F";

            case "OID Example":
                // SEQUENCE { OID sha256WithRSAEncryption, NULL }
                return "300D06092A864886F70D01010B0500";

            default:
                // Simple INTEGER
                return "02012A";
        }
    }

    /**
     * Handle export tree button
     */
    public void handleExportTree() {
        if (lastParsedData == null) {
            mainController.showError("Export Error", "No tree to export. Parse ASN.1 data first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Tree");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialFileName("asn1-tree.txt");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                // Re-parse without truncation for full export
                ASN1TreeNode fullTree = ASN1Parser.parse(lastParsedData, -1); // -1 = no truncation
                String fullTreeText = fullTree.toIndentedString(true); // true = full export

                Files.write(file.toPath(), fullTreeText.getBytes());
                mainController.updateStatus("Tree exported to: " + file.getName() + " (complete, no truncation)");
            } catch (Exception e) {
                mainController.showError("Export Error", "Failed to export tree: " + e.getMessage());
            }
        }
    }

    /**
     * Handle clear button
     */
    public void handleClear() {
        asn1InputArea.clear();
        asn1TreeArea.clear();
        asn1DetailsArea.clear();
        asn1StatusLabel.setText("Ready");
        asn1StatusLabel.setStyle("");
        lastParsedData = null; // Clear stored data
    }
}
