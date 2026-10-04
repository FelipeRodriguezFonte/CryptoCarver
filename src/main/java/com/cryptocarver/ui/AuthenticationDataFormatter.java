package com.cryptocarver.ui;

import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;

import java.util.Base64;

/** Shared conversion at Authentication's UI boundary. */
final class AuthenticationDataFormatter {
    private AuthenticationDataFormatter() { }

    static byte[] read(TextArea inputArea, ComboBox<String> inputFormat, StatusReporter reporter) {
        if (inputArea == null) {
            reporter.showError("Configuration Error",
                    "Input area not available. Authentication operations require proper UI setup.");
            return null;
        }

        String input = inputArea.getText().trim();
        if (input.isEmpty()) return null;

        String format = inputFormat.getValue();
        com.cryptocarver.util.InputValidator.validateInput(input, format);
        return parse(input, format);
    }

    static void write(TextArea outputArea, ComboBox<String> outputFormat, byte[] data,
                      StatusReporter reporter) {
        if (outputArea == null) {
            String format = outputFormat != null ? outputFormat.getValue() : "Hexadecimal";
            reporter.showInfo("Output", format(data, format));
            return;
        }

        String format = outputFormat.getValue();
        outputArea.setText(format(data, format));
    }

    private static byte[] parse(String data, String format) {
        try {
            if (format == null) format = "Hexadecimal";
            return switch (format) {
                case "Hexadecimal" -> DataConverter.hexToBytes(data);
                case "Base64" -> Base64.getDecoder().decode(data.replaceAll("\\s", ""));
                case "Text (UTF-8)" -> data.getBytes("UTF-8");
                default -> data.getBytes();
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static String format(byte[] data, String format) {
        try {
            if (format == null) format = "Hexadecimal";
            return switch (format) {
                case "Hexadecimal" -> DataConverter.bytesToHex(data);
                case "Base64" -> Base64.getEncoder().encodeToString(data);
                case "Text (UTF-8)" -> new String(data, "UTF-8");
                default -> DataConverter.bytesToHex(data);
            };
        } catch (Exception e) {
            return "";
        }
    }
}
