package com.cryptocarver.ui;

import com.cryptocarver.crypto.BitShifter;
import com.cryptocarver.crypto.CompressionCodec;
import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.crypto.TraceHexExtractor;
import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecRegistry;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Manual Conversion pane: converts between text, hex, Base64, Base64URL, Base94, binary and
 * decimal, with EBCDIC code pages, Base32, URL encoding, endian swaps, compression, packed
 * decimal, bit shifts, trace-hex extraction and the manual conversion templates.
 */
final class ManualConversionCoordinator {

    /** The pane's controls, injected into GenericController from generic.fxml. */
    record View(ComboBox<String> template,
            TextArea input,
            TextArea output,
            ComboBox<String> inputFormat,
            ComboBox<String> outputFormat,
            CheckBox ebcdic,
            ComboBox<String> ebcdicDirection,
            ComboBox<String> ebcdicCodePage,
            ComboBox<String> endianWordSize,
            ComboBox<String> compressionFormat,
            TextField bitShiftBits) {
    }

    private final ComboBox<String> manualTemplateCombo;
    private final TextArea manualInputArea;
    private final TextArea manualOutputArea;
    private final ComboBox<String> manualInputFormatCombo;
    private final ComboBox<String> manualOutputFormatCombo;
    private final CheckBox ebcdicConversionCheck;
    private final ComboBox<String> ebcdicDirectionCombo;
    private final ComboBox<String> ebcdicCodePageCombo;
    private final ComboBox<String> endianWordSizeCombo;
    private final ComboBox<String> compressionFormatCombo;
    private final TextField bitShiftBitsField;
    private final Supplier<StatusReporter> reporter;
    /** The shell toolbar's format selectors, handed to the controller after the panes load. */
    private final Supplier<ComboBox<String>> toolbarInput;
    private final Supplier<ComboBox<String>> toolbarOutput;
    /** Sets the toolbar formats through the shell, as the controller does for every pane. */
    private final Consumer<String> sharedInputFormat;
    private final Consumer<String> sharedOutputFormat;

    ManualConversionCoordinator(View view, Supplier<StatusReporter> reporter, Supplier<ComboBox<String>> toolbarInput,
            Supplier<ComboBox<String>> toolbarOutput, Consumer<String> sharedInputFormat,
            Consumer<String> sharedOutputFormat) {
        this.manualTemplateCombo = view.template();
        this.manualInputArea = view.input();
        this.manualOutputArea = view.output();
        this.manualInputFormatCombo = view.inputFormat();
        this.manualOutputFormatCombo = view.outputFormat();
        this.ebcdicConversionCheck = view.ebcdic();
        this.ebcdicDirectionCombo = view.ebcdicDirection();
        this.ebcdicCodePageCombo = view.ebcdicCodePage();
        this.endianWordSizeCombo = view.endianWordSize();
        this.compressionFormatCombo = view.compressionFormat();
        this.bitShiftBitsField = view.bitShiftBits();
        this.reporter = reporter;
        this.toolbarInput = toolbarInput;
        this.toolbarOutput = toolbarOutput;
        this.sharedInputFormat = sharedInputFormat;
        this.sharedOutputFormat = sharedOutputFormat;
    }

    /** Fills the pane's choices and its template list. */
    void configure() {
        if (ebcdicCodePageCombo != null) {
            ebcdicCodePageCombo.getItems().setAll(EBCDICConverter.supportedCodePages().keySet());
            AppSettings settings = AppSettings.getInstance();
            String savedCodePage = settings.getEBCDICCodePage();
            ebcdicCodePageCombo.setValue(EBCDICConverter.supportedCodePages().containsKey(savedCodePage)
                    ? savedCodePage : "IBM037 — US/Canada");
        }
        if (ebcdicDirectionCombo != null) {
            ebcdicDirectionCombo.getItems().setAll("Decode EBCDIC → UTF-8", "Encode UTF-8 → EBCDIC");
            AppSettings settings = AppSettings.getInstance();
            String savedDirection = settings.getEBCDICDirection();
            ebcdicDirectionCombo.setValue(ebcdicDirectionCombo.getItems().contains(savedDirection)
                    ? savedDirection : "Decode EBCDIC → UTF-8");
        }
        if (endianWordSizeCombo != null) {
            endianWordSizeCombo.getItems().setAll("16 bits (2 bytes)", "32 bits (4 bytes)", "64 bits (8 bytes)", "128 bits (16 bytes)");
            endianWordSizeCombo.setValue("32 bits (4 bytes)");
        }
        if (compressionFormatCombo != null) {
            compressionFormatCombo.getItems().setAll("gzip", "zlib", "deflate");
            compressionFormatCombo.setValue("gzip");
        }
        refreshManualTemplateCombo();
        initializeEBCDICConverter();
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private ComboBox<String> toolbarInput() {
        return toolbarInput.get();
    }

    private ComboBox<String> toolbarOutput() {
        return toolbarOutput.get();
    }

    private String getManualInputFormat() {
        return manualInputFormatCombo != null && manualInputFormatCombo.getValue() != null ? manualInputFormatCombo.getValue() : "Text";
    }

    private String getManualOutputFormat() {
        return manualOutputFormatCombo != null && manualOutputFormatCombo.getValue() != null ? manualOutputFormatCombo.getValue() : "Text";
    }

    void handleManualConvert() {
        if (ebcdicConversionCheck != null && ebcdicConversionCheck.isSelected()) {
            convertEBCDIC(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(),
                    ebcdicDirectionCombo.getValue(), ebcdicCodePageCombo.getValue(), manualOutputArea);
        } else {
            convert(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), manualOutputArea);
        }
    }

    void handleEncodeBase64Url() { convertBase64Url(manualInputArea.getText(), true, manualOutputArea); }
    void handleDecodeBase64Url() { convertBase64Url(manualInputArea.getText(), false, manualOutputArea); }
    void handleEncodeBase32() { convertBase32(manualInputArea.getText(), true, manualOutputArea); }
    void handleDecodeBase32() { convertBase32(manualInputArea.getText(), false, manualOutputArea); }
    void handleConvertEndian() {
        int wordSize = 4;
        if (endianWordSizeCombo != null && endianWordSizeCombo.getValue() != null) {
            try {
                wordSize = Integer.parseInt(endianWordSizeCombo.getValue().split(" ")[0]) / 8;
            } catch (Exception e) {
                wordSize = 4;
            }
        }
        convertEndian(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), wordSize, manualOutputArea);
    }
    void handleEncodeUrl() { convertUrlEncoding(manualInputArea.getText(), true, manualOutputArea); }
    void handleDecodeUrl() { convertUrlEncoding(manualInputArea.getText(), false, manualOutputArea); }
    void handleCompressData() { convertCompression(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), compressionFormatCombo.getValue(), true, manualOutputArea); }
    void handleDecompressData() { convertCompression(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), compressionFormatCombo.getValue(), false, manualOutputArea); }
    void handleEncodeBcd() { convertPackedDecimal(manualInputArea.getText(), false, true, manualOutputArea); }
    void handleDecodeBcd() { convertPackedDecimal(manualInputArea.getText(), false, false, manualOutputArea); }
    void handleEncodeComp3() { convertPackedDecimal(manualInputArea.getText(), true, true, manualOutputArea); }
    void handleDecodeComp3() { convertPackedDecimal(manualInputArea.getText(), true, false, manualOutputArea); }
    void handleShiftLeft() { shiftManualBits(true); }
    void handleShiftRight() { shiftManualBits(false); }
    void handleExtractTraceHex() {
        try {
            TraceHexExtractor.Extraction result = TraceHexExtractor.extract(manualInputArea.getText());
            manualOutputArea.setText(result.hex());
            if (reporter() != null) reporter().updateStatus("Extracted " + result.byteCount() + " trace bytes");
        } catch (IllegalArgumentException e) {
            manualOutputArea.setText("Error: " + e.getMessage());
        }
    }
    private void shiftManualBits(boolean left) {
        try {
            int bits = Integer.parseInt(bitShiftBitsField == null ? "1" : bitShiftBitsField.getText().trim());
            byte[] input = DataConverter.hexToBytes(manualInputArea.getText().replaceAll("\\s+", ""));
            manualOutputArea.setText(DataConverter.bytesToHex(left ? BitShifter.left(input, bits) : BitShifter.right(input, bits)));
        } catch (IllegalArgumentException e) {
            manualOutputArea.setText("Error: " + e.getMessage());
        }
    }

    void refreshManualTemplateCombo() {
        SafeTemplateUIHelper.populateTemplateCombo(
                manualTemplateCombo,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION,
                List.of("Convert Text UTF-8 → Base64", "Convert Hex → Text UTF-8")
        );
    }

    void handleApplyManualTemplate() {
        String template = manualTemplateCombo != null ? manualTemplateCombo.getValue() : null;
        if (template == null) return;

        Map<String, java.util.function.Consumer<String>> setters = Map.of(
                "manualInputFormatCombo", v -> GenericController.selectIfSupported(manualInputFormatCombo, GenericController.normalizeFormatName(v)),
                "manualOutputFormatCombo", v -> GenericController.selectIfSupported(manualOutputFormatCombo, GenericController.normalizeFormatName(v)),
                "inputFormatCombo", sharedInputFormat,
                "outputFormatCombo", sharedOutputFormat,
                "ebcdicDirectionCombo", v -> { if (ebcdicDirectionCombo != null) ebcdicDirectionCombo.setValue(v); }
        );

        SafeTemplateUIHelper.applySelectedTemplate(
                template,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION,
                () -> {
                    if (template.contains("Base64")) {
                        manualInputFormatCombo.setValue("Text (UTF-8)");
                        manualOutputFormatCombo.setValue("Base64");
                        if (reporter() != null) {
                            reporter().setInputFormat("Text (UTF-8)");
                            reporter().setOutputFormat("Base64");
                            reporter().updateStatus("Template Applied: Convert Text UTF-8 → Base64");
                        }
                    } else if (template.contains("Hex")) {
                        manualInputFormatCombo.setValue("Hexadecimal");
                        manualOutputFormatCombo.setValue("Text (UTF-8)");
                        if (reporter() != null) {
                            reporter().setInputFormat("Hexadecimal");
                            reporter().setOutputFormat("Text (UTF-8)");
                            reporter().updateStatus("Template Applied: Convert Hex → Text UTF-8");
                        }
                    }
                },
                setters,
                reporter()
        );
    }

    void handleSaveManualTemplate() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (manualInputFormatCombo != null && manualInputFormatCombo.getValue() != null) params.put("manualInputFormatCombo", manualInputFormatCombo.getValue());
        if (manualOutputFormatCombo != null && manualOutputFormatCombo.getValue() != null) params.put("manualOutputFormatCombo", manualOutputFormatCombo.getValue());
        if (toolbarInput() != null && toolbarInput().getValue() != null) params.put("inputFormatCombo", toolbarInput().getValue());
        if (toolbarOutput() != null && toolbarOutput().getValue() != null) params.put("outputFormatCombo", toolbarOutput().getValue());
        if (ebcdicDirectionCombo != null && ebcdicDirectionCombo.getValue() != null) params.put("ebcdicDirectionCombo", ebcdicDirectionCombo.getValue());
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.saveCurrentAsTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, params, this::refreshManualTemplateCombo, reporter());
    }

    void handleExportManualTemplate() {
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.exportSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, manualTemplateCombo, reporter());
    }

    void handleImportManualTemplate() {
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.importTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, this::refreshManualTemplateCombo, reporter());
    }

    void handleDeleteManualTemplate() {
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.deleteSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, manualTemplateCombo, this::refreshManualTemplateCombo, reporter());
    }

    void handleResetManualDefaults() {
        manualInputFormatCombo.setValue("Text (UTF-8)");
        manualOutputFormatCombo.setValue("Text (UTF-8)");
        manualInputArea.setText("");
        manualOutputArea.setText("");
        if (reporter() != null) {
            reporter().setInputFormat("Text (UTF-8)");
            reporter().setOutputFormat("Text (UTF-8)");
            reporter().updateStatus("Manual conversion form reset to default");
        }
    }



    void initializeEBCDICConverter() {
        if (ebcdicCodePageCombo != null && ebcdicDirectionCombo != null && ebcdicConversionCheck != null) {
            ebcdicCodePageCombo.getItems().setAll(EBCDICConverter.supportedCodePages().keySet());
            AppSettings settings = AppSettings.getInstance();
            String savedCodePage = settings.getEBCDICCodePage();
            ebcdicCodePageCombo.setValue(EBCDICConverter.supportedCodePages().containsKey(savedCodePage)
                    ? savedCodePage : "IBM037 — US/Canada");
            ebcdicDirectionCombo.getItems().setAll("Decode EBCDIC → UTF-8", "Encode UTF-8 → EBCDIC");
            String savedDirection = settings.getEBCDICDirection();
            ebcdicDirectionCombo.setValue(ebcdicDirectionCombo.getItems().contains(savedDirection)
                    ? savedDirection : "Decode EBCDIC → UTF-8");

            ebcdicCodePageCombo.valueProperty().addListener((observable, previous, selected) -> settings.setEBCDICCodePage(selected));
            ebcdicDirectionCombo.valueProperty().addListener((observable, previous, selected) -> settings.setEBCDICDirection(selected));
            ebcdicDirectionCombo.disableProperty().bind(ebcdicConversionCheck.selectedProperty().not());
            ebcdicCodePageCombo.disableProperty().bind(ebcdicConversionCheck.selectedProperty().not());
        }
    }

    void convertEBCDIC(String input, String inputFormat, String outputFormat, String direction, String codePage,
                              TextInputControl targetOutputArea) {
        try {
            byte[] sourceBytes = GenericController.parseInput(input, inputFormat);
            if (sourceBytes == null) throw new IllegalArgumentException("Input cannot be empty");
            boolean encoding = "Encode UTF-8 → EBCDIC".equals(direction);
            String operation = encoding ? "UTF-8 → EBCDIC Conversion" : "EBCDIC → UTF-8 Conversion";
            byte[] resultBytes;
            String result;

            if (encoding) {
                if ("Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)) {
                    throw new IllegalArgumentException("Choose Hexadecimal, Base64, Binary or Decimal to represent EBCDIC bytes");
                }
                resultBytes = EBCDICConverter.encode(DataConverter.utf8BytesToString(sourceBytes), codePage);
                result = GenericController.formatBytes(resultBytes, outputFormat);
            } else {
                String decodedText = EBCDICConverter.decode(sourceBytes, codePage);
                resultBytes = decodedText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                result = "Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)
                        ? decodedText : GenericController.formatBytes(resultBytes, outputFormat);
            }
            targetOutputArea.setText(result);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Input Format", inputFormat);
            details.put("Output Format", outputFormat);
            details.put("EBCDIC Code Page", codePage);
            details.put("Direction", direction);
            reporter().publish(OperationResult.forOperation(operation)
                    .input(sourceBytes).output(resultBytes).details(details)
                    .status(operation + " using " + codePage).build());
        } catch (Exception e) {
            reporter().showError("EBCDIC Conversion Error", e.getMessage());
        }
    }

    /** Explicit Base64URL text conversion for JOSE-style payloads. */
    void convertBase64Url(String input, boolean encode, TextInputControl targetOutputArea) {
        try {
            byte[] inputBytes;
            byte[] outputBytes;
            String output;
            if (encode) {
                inputBytes = CodecRegistry.getInstance().decode(input, ByteFormat.TEXT_UTF8);
                output = CodecRegistry.getInstance().encode(inputBytes, ByteFormat.BASE64_URL);
                outputBytes = CodecRegistry.getInstance().decode(output, ByteFormat.TEXT_ASCII);
            } else {
                inputBytes = CodecRegistry.getInstance().decode(input, ByteFormat.BASE64_URL);
                output = CodecRegistry.getInstance().encode(inputBytes, ByteFormat.TEXT_UTF8);
                outputBytes = inputBytes;
            }
            targetOutputArea.setText(output);
            String operation = encode ? "UTF-8 → Base64URL" : "Base64URL → UTF-8";
            reporter().publish(OperationResult.forOperation(operation)
                    .input(encode ? inputBytes : input.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .output(outputBytes).detail("Padding", "None (RFC 4648 / JOSE)")
                    .status(operation + " conversion completed").build());
        } catch (Exception e) {
            reporter().showError("Base64URL Conversion Error", e.getMessage());
        }
    }

    /** Explicit Base32 conversion for RFC 4648 interoperability. */
    void convertBase32(String input, boolean encode, TextInputControl targetOutputArea) {
        try {
            byte[] decoded;
            String output;
            if (encode) {
                decoded = CodecRegistry.getInstance().decode(input, ByteFormat.TEXT_UTF8);
                output = CodecRegistry.getInstance().encode(decoded, ByteFormat.BASE32);
            } else {
                decoded = CodecRegistry.getInstance().decode(input, ByteFormat.BASE32);
                output = CodecRegistry.getInstance().encode(decoded, ByteFormat.TEXT_UTF8);
            }
            targetOutputArea.setText(output);
            String operation = encode ? "UTF-8 → Base32" : "Base32 → UTF-8";
            reporter().publish(OperationResult.forOperation(operation)
                    .input(encode ? input.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                            : input.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .output(encode ? output.getBytes(java.nio.charset.StandardCharsets.US_ASCII) : decoded)
                    .detail("Standard", "RFC 4648 Base32")
                    .status(operation + " conversion completed").build());
        } catch (Exception e) {
            reporter().showError("Base32 Conversion Error", e.getMessage());
        }
    }

    /** Reverses byte order inside each fixed-width integer (16/32/64/128 bits). */
    void convertEndian(String input, String inputFormat, String outputFormat, int wordBytes,
                              TextInputControl targetOutputArea) {
        try {
            byte[] source = GenericController.parseInput(input, inputFormat);
            if (source == null || source.length == 0) throw new IllegalArgumentException("Input cannot be empty");
            if (wordBytes != 2 && wordBytes != 4 && wordBytes != 8 && wordBytes != 16) {
                throw new IllegalArgumentException("Word size must be 2, 4, 8 or 16 bytes");
            }
            if (source.length % wordBytes != 0) {
                throw new IllegalArgumentException("Input length must be a multiple of " + wordBytes + " bytes");
            }
            byte[] converted = source.clone();
            for (int offset = 0; offset < converted.length; offset += wordBytes) {
                for (int left = offset, right = offset + wordBytes - 1; left < right; left++, right--) {
                    byte temporary = converted[left];
                    converted[left] = converted[right];
                    converted[right] = temporary;
                }
            }
            String output = "Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)
                    ? DataConverter.utf8BytesToString(converted) : GenericController.formatBytes(converted, outputFormat);
            targetOutputArea.setText(output);
            reporter().publish(OperationResult.forOperation("Endian Conversion")
                    .input(source).output(converted)
                    .detail("Word Size", (wordBytes * 8) + " bits")
                    .detail("Input Format", inputFormat).detail("Output Format", outputFormat)
                    .status("Byte order converted for " + (wordBytes * 8) + "-bit words").build());
        } catch (Exception e) {
            reporter().showError("Endian Conversion Error", e.getMessage());
        }
    }

    void convertUrlEncoding(String input, boolean encode, TextInputControl targetOutputArea) {
        try {
            String result = encode
                    ? java.net.URLEncoder.encode(input, java.nio.charset.StandardCharsets.UTF_8)
                    : java.net.URLDecoder.decode(input, java.nio.charset.StandardCharsets.UTF_8);
            targetOutputArea.setText(result);
            String operation = encode ? "UTF-8 → URL Encoding" : "URL Encoding → UTF-8";
            reporter().publish(OperationResult.forOperation(operation)
                    .input(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .status(operation + " completed").build());
        } catch (Exception e) {
            reporter().showError("URL Encoding Error", e.getMessage());
        }
    }

    void convertCompression(String input, String inputFormat, String outputFormat, String format, boolean compress,
                                  TextInputControl targetOutputArea) {
        try {
            byte[] source = GenericController.parseInput(input, inputFormat);
            byte[] converted = compress ? CompressionCodec.compress(source, format) : CompressionCodec.decompress(source, format);
            String output = "Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)
                    ? DataConverter.utf8BytesToString(converted) : GenericController.formatBytes(converted, outputFormat);
            targetOutputArea.setText(output);
            String operation = (compress ? "Compress " : "Decompress ") + format;
            reporter().publish(OperationResult.forOperation(operation)
                    .input(source).output(converted).detail("Format", format)
                    .detail("Input Bytes", String.valueOf(source.length)).detail("Output Bytes", String.valueOf(converted.length))
                    .status(operation + " completed").build());
        } catch (Exception e) {
            reporter().showError("Compression Error", e.getMessage());
        }
    }

    void convertPackedDecimal(String input, boolean comp3, boolean encode, TextInputControl targetOutputArea) {
        try {
            byte[] bytes;
            String output;
            if (encode) {
                bytes = comp3 ? DataConverter.decimalToComp3(input) : DataConverter.decimalToPackedBcd(input);
                output = DataConverter.bytesToHex(bytes);
            } else {
                bytes = DataConverter.hexToBytes(input);
                output = comp3 ? DataConverter.comp3ToDecimal(bytes) : DataConverter.packedBcdToDecimal(bytes);
            }
            targetOutputArea.setText(output);
            String name = comp3 ? "COMP-3" : "Packed BCD";
            String operation = encode ? "Decimal → " + name : name + " → Decimal";
            reporter().publish(OperationResult.forOperation(operation)
                    .input(encode ? input.getBytes(java.nio.charset.StandardCharsets.US_ASCII) : bytes)
                    .output(encode ? bytes : output.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .status(operation + " conversion completed").build());
        } catch (Exception e) { reporter().showError("Packed Decimal Error", e.getMessage()); }
    }

    /**
     * Universal conversion
     */
    void fillManualConversionInput(String value, com.cryptocarver.model.ClipboardEntry.Format format) {
        String targetFormat = "Text (UTF-8)";
        switch (format) {
            case HEX: targetFormat = "Hexadecimal"; break;
            case BASE64: targetFormat = "Base64"; break;
            case BASE64URL: targetFormat = "Base64URL"; break;
            default: break;
        }

        if (manualInputFormatCombo != null && !manualInputFormatCombo.getItems().contains(targetFormat)) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Format Not Supported");
            alert.setHeaderText("Incompatible Format");
            alert.setContentText("The format " + format + " is not supported by Manual Conversion.");
            alert.showAndWait();
            return;
        }

        if (manualInputArea != null) {
            manualInputArea.setText(value);
        }
        if (manualInputFormatCombo != null) {
            manualInputFormatCombo.setValue(targetFormat);
        }
    }

    void convert(String input, String inputFormat, String outputFormat, TextInputControl targetOutputArea) {
        try {
            inputFormat = GenericController.normalizeFormatName(inputFormat);
            outputFormat = GenericController.normalizeFormatName(outputFormat);
            if (input == null || input.isEmpty()) {
                reporter().showError("Input Error", "Please enter data to convert");
                return;
            }
            if (inputFormat == null || outputFormat == null) {
                reporter().showError("Format Error", "Please select both input and output formats");
                return;
            }

            try {
                com.cryptocarver.util.InputValidator.validateInput(input, inputFormat);
            } catch (IllegalArgumentException e) {
                reporter().showError("Format Error", e.getMessage());
                return;
            }

            // Parse Input
            byte[] inputData;
            switch (inputFormat) {
                case "Hexadecimal":
                    String cleanHex = input.replaceAll("\\s+", "");
                    if (!DataConverter.isValidHex(cleanHex)) {
                        reporter().showError("Input Error", "Invalid hexadecimal input. Use 0-9, A-F.");
                        return;
                    }
                    inputData = DataConverter.hexToBytes(cleanHex);
                    break;
                case "Base64":
                    inputData = DataConverter.decodeBase64Flexible(input);
                    break;
                case "Base64URL":
                    inputData = DataConverter.decodeBase64Url(input);
                    break;
                case "Text (UTF-8)":
                case "Text":
                    inputData = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Binary":
                    try {
                        inputData = DataConverter.binaryToBytes(input.replaceAll("\\s+", ""));
                    } catch (IllegalArgumentException e) {
                        reporter().showError("Input Error", "Invalid binary input: " + e.getMessage());
                        return;
                    }
                    break;
                case "Decimal":
                    inputData = DataConverter.decimalToBytes(input);
                    break;
                default:
                    reporter().showError("Format Error", "Unsupported input format: " + inputFormat);
                    return;
            }

            // Format Output
            String outputResult;
            switch (outputFormat) {
                case "Hexadecimal":
                    outputResult = bytesToHex(inputData);
                    break;
                case "Base64":
                    outputResult = java.util.Base64.getEncoder().encodeToString(inputData);
                    break;
                case "Base64URL":
                    outputResult = DataConverter.bytesToBase64Url(inputData);
                    break;
                case "Text (UTF-8)":
                case "Text":
                    outputResult = new String(inputData, java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Binary":
                    outputResult = DataConverter.bytesToBinary(inputData);
                    break;
                case "Decimal":
                    outputResult = DataConverter.bytesToDecimal(inputData);
                    break;
                default:
                    reporter().showError("Format Error", "Unsupported output format: " + outputFormat);
                    return;
            }

            targetOutputArea.setText(outputResult);
            reporter().publish(OperationResult.forOperation("Manual Conversion")
                    .input(inputData).output(inputData)
                    .detail("Input Format", inputFormat).detail("Output Format", outputFormat)
                    .status(String.format("Converted from %s to %s", inputFormat, outputFormat)).build());

        } catch (Exception e) {
            reporter().showError("Conversion Error", "Error converting data: " + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
