package com.cryptocarver.ui;

import com.cryptocarver.crypto.ByteStatistics;
import com.cryptocarver.crypto.CharsetInspector;
import com.cryptocarver.crypto.HexInspector;
import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.util.DataConverter;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Dedicated byte-analysis tool, intentionally separate from transformations. */
public final class ByteInspectorWindow {
    private ByteInspectorWindow() { }

    public static void show(Stage owner) {
        TextArea input = new TextArea();
        input.setPromptText("Bytes to inspect");
        input.setPrefRowCount(5);
        TextArea secondInput = new TextArea();
        secondInput.setPromptText("Second buffer (only for XOR / Compare)");
        secondInput.setPrefRowCount(3);
        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(false);
        output.setPrefRowCount(16);
        output.setStyle("-fx-font-family: monospace;");
        ComboBox<String> format = new ComboBox<>();
        format.getItems().addAll("Hexadecimal", "Base64", "Base64URL", "Text (UTF-8)", "Binary", "Decimal");
        format.setValue("Hexadecimal");
        ComboBox<String> codePage = new ComboBox<>();
        codePage.getItems().addAll(EBCDICConverter.supportedCodePages().keySet());
        codePage.setValue("IBM037 — US/Canada");

        TextField viewOffset = new TextField("0");
        viewOffset.setPrefWidth(70);
        TextField viewLength = new TextField();
        viewLength.setPromptText("View length");
        viewLength.setPrefWidth(90);
        TextField selectionOffset = new TextField();
        selectionOffset.setPromptText("Select offset");
        selectionOffset.setPrefWidth(90);
        TextField selectionLength = new TextField();
        selectionLength.setPromptText("Select length");
        selectionLength.setPrefWidth(90);

        Button hex = new Button("Hex view");
        hex.setOnAction(event -> {
            try {
                byte[] bytes = GenericController.parseInput(input.getText(), format.getValue());
                int offset = parse(viewOffset, 0), length = parse(viewLength, bytes.length);
                int selectedOffset = selectionOffset.getText().isBlank() ? -1 : parse(selectionOffset, -1);
                int selectedLength = parse(selectionLength, 0);
                output.setText(HexInspector.render(bytes, offset, length, selectedOffset, selectedLength));
            } catch (Exception e) { output.setText("Error: " + e.getMessage()); }
        });
        Button charsets = action("Charsets", input, format, output,
                bytes -> CharsetInspector.compare(bytes, codePage.getValue()));
        Button stats = action("Statistics", input, format, output, ByteStatistics::analyze);
        Button controls = action("Controls", input, format, output, DataConverter::visualizeBytes);
        Button xor = new Button("XOR");
        xor.setOnAction(event -> {
            try {
                byte[] left = GenericController.parseInput(input.getText(), format.getValue());
                byte[] right = GenericController.parseInput(secondInput.getText(), format.getValue());
                output.setText(HexInspector.render(DataConverter.xor(left, right), 0, left.length));
            } catch (Exception e) { output.setText("Error: " + e.getMessage()); }
        });
        Button compare = new Button("Compare");
        compare.setOnAction(event -> {
            try {
                byte[] left = GenericController.parseInput(input.getText(), format.getValue());
                byte[] right = GenericController.parseInput(secondInput.getText(), format.getValue());
                int limit = Math.min(left.length, right.length), first = -1;
                for (int i = 0; i < limit; i++) if (left[i] != right[i]) { first = i; break; }
                output.setText(first >= 0 ? "First difference: offset " + first + " (0x" + Integer.toHexString(first).toUpperCase() + ")"
                        : left.length == right.length ? "Buffers are identical." : "Buffers match for " + limit + " bytes; lengths differ.");
            } catch (Exception e) { output.setText("Error: " + e.getMessage()); }
        });
        Button copy = new Button("Copy result");
        copy.setOnAction(event -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(output.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        HBox actions = new HBox(8, hex, charsets, stats, controls, xor, compare, copy);
        HBox range = new HBox(6, new Label("View:"), viewOffset, viewLength, new Label("Selection:"), selectionOffset, selectionLength);
        HBox settings = new HBox(8, new Label("Input format:"), format, new Label("EBCDIC:"), codePage);
        VBox root = new VBox(8, settings, new Label("Input:"), input, range,
                new Label("Second buffer (optional):"), secondInput, actions, new Label("Inspection result:"), output);
        root.setPadding(new Insets(12));
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("CryptoCarver — Byte Inspector");
        stage.setScene(new Scene(root, 780, 650));
        stage.show();
    }

    private static int parse(TextField field, int fallback) {
        return field.getText().isBlank() ? fallback : Integer.parseInt(field.getText().trim());
    }

    private static Button action(String label, TextArea input, ComboBox<String> format, TextArea output,
                                 java.util.function.Function<byte[], String> operation) {
        Button button = new Button(label);
        button.setOnAction(event -> {
            try { output.setText(operation.apply(GenericController.parseInput(input.getText(), format.getValue()))); }
            catch (Exception e) { output.setText("Error: " + e.getMessage()); }
        });
        return button;
    }
}
