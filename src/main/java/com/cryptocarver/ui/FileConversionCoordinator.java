package com.cryptocarver.ui;

import com.cryptocarver.crypto.StreamingFileTools;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.FileConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * The File Conversion pane: converts a file between binary, hex, Base64 and text (with a
 * charset), and compares, hashes or previews files by streaming them.
 */
final class FileConversionCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(FileConversionCoordinator.class);

    /** The pane's controls, injected into GenericController from generic.fxml. */
    record View(TextField inputPath,
            TextField outputPath,
            TextField comparePath,
            ComboBox<String> inputFormat,
            ComboBox<String> outputFormat,
            ComboBox<String> encoding,
            TextArea result) {
    }

    private final TextField fileInputPathField;
    private final TextField fileOutputPathField;
    private final TextField fileComparePathField;
    private final ComboBox<String> fileInputFormatCombo;
    private final ComboBox<String> fileOutputFormatCombo;
    private final ComboBox<String> fileEncodingCombo;
    private final TextArea fileResultArea;
    private final Supplier<StatusReporter> reporter;

    FileConversionCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.fileInputPathField = view.inputPath();
        this.fileOutputPathField = view.outputPath();
        this.fileComparePathField = view.comparePath();
        this.fileInputFormatCombo = view.inputFormat();
        this.fileOutputFormatCombo = view.outputFormat();
        this.fileEncodingCombo = view.encoding();
        this.fileResultArea = view.result();
        this.reporter = reporter;
    }

    /** Fills the pane's choices. */
    void configure() {
        if (fileInputFormatCombo != null) {
            fileInputFormatCombo.getItems().setAll("Binary", "Text", "Hex", "Base64");
            fileInputFormatCombo.getSelectionModel().select("Binary");
        }
        if (fileOutputFormatCombo != null) {
            fileOutputFormatCombo.getItems().setAll("Binary", "Text", "Hex", "Base64");
            fileOutputFormatCombo.getSelectionModel().select("Binary");
        }
        if (fileEncodingCombo != null) {
            fileEncodingCombo.getItems().setAll("UTF-8", "ASCII", "ISO-8859-1");
            fileEncodingCombo.getSelectionModel().select("UTF-8");
        }

    }

    private StatusReporter reporter() {
        return reporter.get();
    }


    void handleBrowseInputFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Input File");
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && fileInputPathField != null) fileInputPathField.setText(file.getAbsolutePath());
    }

    void handleBrowseOutputFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Output File");
        java.io.File file = fileChooser.showSaveDialog(null);
        if (file != null && fileOutputPathField != null) fileOutputPathField.setText(file.getAbsolutePath());
    }

    void handleBrowseCompareFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select File to Compare");
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && fileComparePathField != null) fileComparePathField.setText(file.getAbsolutePath());
    }

    void handleConvertFile() {
        if (fileInputPathField != null && fileOutputPathField != null && fileInputFormatCombo != null && fileOutputFormatCombo != null) {
            String inputPath = fileInputPathField.getText().trim();
            String outputPath = fileOutputPathField.getText().trim();
            if (inputPath.isEmpty() || outputPath.isEmpty()) return;

            boolean isTestMode = "true".equals(System.getProperty("test.mode"));
            if (!isTestMode && java.nio.file.Files.exists(java.nio.file.Paths.get(outputPath))) {
                javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Overwrite existing file?");
                confirm.setHeaderText("The selected output file already exists.");
                confirm.setContentText(outputPath);
                if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) {
                    if (reporter() != null) reporter().updateStatus("File conversion cancelled");
                    return;
                }
            }

            handleFileConvert();

            if (reporter() != null) {
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                details.put("Input File", inputPath);
                details.put("Output File", outputPath);
                details.put("Input Format", fileInputFormatCombo.getValue());
                details.put("Output Format", fileOutputFormatCombo.getValue());
                reporter().publish(OperationResult.forOperation("File Conversion").details(details).build());
            }
        }
    }

    void compareFiles() {
        try {
            String left = fileInputPathField.getText().trim();
            String right = fileComparePathField.getText().trim();
            if (left.isEmpty() || right.isEmpty()) throw new IllegalArgumentException("Select both files to compare");
            long difference = StreamingFileTools.firstDifference(java.nio.file.Paths.get(left), java.nio.file.Paths.get(right), com.cryptocarver.util.ProgressMonitor.NO_OP);
            String result = difference < 0 ? "Files are identical."
                    : "Files differ at byte offset " + difference + " (0x" + Long.toHexString(difference).toUpperCase() + ").";
            fileResultArea.setText(result);
            reporter().publish(OperationResult.forOperation("Compare Files")
                    .detail("Left File", java.nio.file.Paths.get(left).getFileName().toString())
                    .detail("Right File", java.nio.file.Paths.get(right).getFileName().toString())
                    .detail("Result", difference < 0 ? "IDENTICAL" : "DIFFERENT")
                    .status(result).build());
        } catch (Exception e) {
            reporter().showError("File Comparison Error", e.getMessage());
        }
    }

    void hashFileStreaming() {
        try {
            String source = fileInputPathField.getText().trim();
            if (source.isEmpty()) throw new IllegalArgumentException("Select a source file first");
            java.nio.file.Path path = java.nio.file.Paths.get(source);
            String hash = StreamingFileTools.hash(path, "SHA-256", com.cryptocarver.util.ProgressMonitor.NO_OP);
            String result = "SHA-256\n" + hash + "\n\nBytes: " + java.nio.file.Files.size(path);
            fileResultArea.setText(result);
            reporter().publish(OperationResult.forOperation("Hash File (streaming)")
                    .output(DataConverter.hexToBytes(hash)).detail("Algorithm", "SHA-256")
                    .detail("File", path.getFileName().toString()).detail("Bytes", String.valueOf(java.nio.file.Files.size(path)))
                    .status("File hash calculated").build());
        } catch (Exception e) {
            reporter().showError("File Hash Error", e.getMessage());
        }
    }

    void previewFileStreaming() {
        try {
            String source = fileInputPathField.getText().trim();
            if (source.isEmpty()) throw new IllegalArgumentException("Select a source file first");
            java.nio.file.Path path = java.nio.file.Paths.get(source);
            byte[] preview = StreamingFileTools.preview(path, 4096, com.cryptocarver.util.ProgressMonitor.NO_OP);
            String result = com.cryptocarver.crypto.HexInspector.render(preview, 0, preview.length);
            fileResultArea.setText(result);
            reporter().publish(OperationResult.forOperation("Preview File (streaming)")
                    .output(preview).detail("File", path.getFileName().toString()).detail("Preview", preview.length + " bytes")
                    .status("File preview loaded").build());
        } catch (Exception e) { reporter().showError("File Preview Error", e.getMessage()); }
    }

    /**
     * Handle file conversion operation
     */
    void handleFileConvert() {
        try {
            String inputPath = fileInputPathField.getText().trim();
            String outputPath = fileOutputPathField.getText().trim();
            String inputFormat = fileInputFormatCombo.getValue();
            String outputFormat = fileOutputFormatCombo.getValue();
            String encodingFull = fileEncodingCombo.getValue();

            // Extract charset name (e.g., "UTF-8" or "Cp037" from "Cp037 (EBCDIC
            // US/Canada)")
            String encoding = encodingFull != null ? encodingFull.split(" ")[0] : "UTF-8";

            if (inputPath.isEmpty()) {
                reporter().showError("Input Error", "Input file path is required");
                return;
            }

            if (inputFormat == null || outputFormat == null) {
                reporter().showError("Input Error", "Please select input and output formats");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("File Conversion\n");
            result.append("===============\n\n");
            result.append("Input File: ").append(inputPath).append("\n");
            result.append("From: ").append(inputFormat).append("\n");
            result.append("To: ").append(outputFormat).append("\n");

            // Special operations (no output format)
            if ("Analyze".equals(inputFormat)) {
                result.append("\n").append(FileConverter.analyzeFile(inputPath));
                result.append("\n\n").append(FileConverter.getFileSizeInfo(inputPath));
                fileResultArea.setText(result.toString());
                return;
            }

            if ("Hex Dump".equals(inputFormat)) {
                result.append("\n\n").append(FileConverter.hexDump(inputPath, 512));
                fileResultArea.setText(result.toString());
                return;
            }

            // Step 1: Read input file as bytes based on input format
            byte[] data;
            switch (inputFormat) {
                case "Binary":
                    data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(inputPath));
                    break;

                case "Hex":
                    String hexContent = java.nio.file.Files.readString(java.nio.file.Paths.get(inputPath)).trim();
                    hexContent = hexContent.replaceAll("\\s+", ""); // Remove whitespace
                    data = hexToBytes(hexContent);
                    break;

                case "Base64":
                    String base64Content = java.nio.file.Files.readString(java.nio.file.Paths.get(inputPath)).trim();
                    data = java.util.Base64.getDecoder().decode(base64Content);
                    break;

                case "Text":
                    String textContent = java.nio.file.Files.readString(java.nio.file.Paths.get(inputPath),
                            java.nio.charset.Charset.forName(encoding));
                    data = textContent.getBytes(encoding);
                    result.append("Input Encoding: ").append(encodingFull).append("\n");
                    break;

                default:
                    reporter().showError("Error", "Unknown input format: " + inputFormat);
                    return;
            }

            result.append("Data Size: ").append(data.length).append(" bytes\n");

            // Step 2: Convert to output format
            switch (outputFormat) {
                case "Binary":
                    if (outputPath.isEmpty()) {
                        reporter().showError("Input Error", "Output path required for binary files");
                        return;
                    }
                    java.nio.file.Files.write(java.nio.file.Paths.get(outputPath), data);
                    result.append("Output: ").append(outputPath).append("\n");
                    result.append("Status: Binary file written successfully");
                    break;

                case "Hex":
                    String hexOutput = bytesToHex(data);
                    if (outputPath.isEmpty()) {
                        result.append("\nHex Output (first 1000 chars):\n");
                        result.append(hexOutput.substring(0, Math.min(1000, hexOutput.length())));
                        if (hexOutput.length() > 1000) {
                            result.append("\n\n... ").append(hexOutput.length() - 1000).append(" more chars");
                        }
                    } else {
                        java.nio.file.Files.writeString(java.nio.file.Paths.get(outputPath), hexOutput);
                        result.append("Output: ").append(outputPath).append("\n");
                        result.append("Status: Hex file written (").append(hexOutput.length()).append(" chars)");
                    }
                    break;

                case "Base64":
                    String base64Output = java.util.Base64.getEncoder().encodeToString(data);
                    if (outputPath.isEmpty()) {
                        result.append("\nBase64 Output (first 1000 chars):\n");
                        result.append(base64Output.substring(0, Math.min(1000, base64Output.length())));
                        if (base64Output.length() > 1000) {
                            result.append("\n\n... ").append(base64Output.length() - 1000).append(" more chars");
                        }
                    } else {
                        java.nio.file.Files.writeString(java.nio.file.Paths.get(outputPath), base64Output);
                        result.append("Output: ").append(outputPath).append("\n");
                        result.append("Status: Base64 file written (").append(base64Output.length()).append(" chars)");
                    }
                    break;

                case "Text":
                    String textOutput = new String(data, encoding);
                    result.append("Output Encoding: ").append(encodingFull).append("\n");
                    if (outputPath.isEmpty()) {
                        result.append("\nText Output (first 1000 chars):\n");
                        result.append(textOutput.substring(0, Math.min(1000, textOutput.length())));
                        if (textOutput.length() > 1000) {
                            result.append("\n\n... ").append(textOutput.length() - 1000).append(" more chars");
                        }
                    } else {
                        java.nio.file.Files.writeString(java.nio.file.Paths.get(outputPath), textOutput,
                                java.nio.charset.Charset.forName(encoding));
                        result.append("Output: ").append(outputPath).append("\n");
                        result.append("Status: Text file written (").append(textOutput.length()).append(" chars)");
                    }
                    break;

                default:
                    reporter().showError("Error", "Unknown output format: " + outputFormat);
                    return;
            }

            fileResultArea.setText(result.toString());
            reporter().updateStatus("Conversion completed: " + inputFormat + " → " + outputFormat);

            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("File Convert: " + inputFormat + " → " + outputFormat)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", inputPath, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Success", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (java.io.FileNotFoundException e) {
            reporter().showError("File Error", "File not found: " + e.getMessage());
        } catch (java.io.IOException e) {
            reporter().showError("File Error", "I/O error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            reporter().showError("Format Error", "Invalid input format: " + e.getMessage());
        } catch (Exception e) {
            reporter().showError("Conversion Error", "Error: " + e.getMessage());
            LOG.error("Conversion failed", e);
        }
    }

    /** Hex text to bytes, as the conversion has always read it. */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
