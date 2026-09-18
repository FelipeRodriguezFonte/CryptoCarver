package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reusable result surface for operation pages.
 *
 * <p>The component deliberately owns presentation only. Operation execution,
 * history and Shelf persistence remain controller responsibilities. Values are
 * rendered through the same classification policy used by the inspector, so a
 * result cannot accidentally bypass REDACTED/MASKED output rules.</p>
 */
public final class ResultPanel extends VBox {
    public enum Status { SUCCESS, ERROR, WARNING, EMPTY }

    private final Label statusLabel = new Label();
    private final Label operationLabel = new Label();
    private final Label metricsLabel = new Label();
    private final Label copiedLabel = new Label();
    private final VBox outputs = new VBox(8);
    private final ComboBox<String> formatSelector = new ComboBox<>();
    private Status status = Status.EMPTY;
    private Consumer<String> copyHandler;
    private Consumer<String> shelfHandler;
    private Runnable expandHandler;
    private Runnable saveStepHandler;
    private Consumer<String> chainHandler;

    public ResultPanel() {
        getStyleClass().add("result-panel");
        setSpacing(10);
        setPadding(new Insets(12));

        statusLabel.getStyleClass().addAll("result-panel-status", "result-status-empty");
        operationLabel.getStyleClass().add("result-panel-operation");
        metricsLabel.getStyleClass().add("result-panel-metrics");
        copiedLabel.getStyleClass().add("result-panel-feedback");
        copiedLabel.setManaged(false);
        copiedLabel.setVisible(false);

        formatSelector.getItems().addAll("Texto", "Hex", "Base64");
        formatSelector.getSelectionModel().selectFirst();
        formatSelector.getStyleClass().add("result-panel-format");
        formatSelector.setAccessibleText("Formato de salida");

        HBox header = new HBox(10, statusLabel, operationLabel, metricsLabel, formatSelector);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(operationLabel, Priority.ALWAYS);
        HBox.setHgrow(metricsLabel, Priority.NEVER);

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button copy = action("Copiar", "Copiar resultado", () -> copyCurrent());
        Button shelf = action("Shelf", "Añadir resultado al Shelf", () -> addCurrentToShelf());
        Button expand = action("Ampliar", "Ampliar resultado", () -> run(expandHandler));
        Button save = action("Guardar", "Guardar paso", () -> run(saveStepHandler));
        Button chain = action("Usar como entrada", "Usar resultado como entrada", () -> chainCurrent());
        actions.getChildren().addAll(copy, shelf, expand, save, chain);

        getChildren().addAll(header, new Separator(), outputs, actions, copiedLabel);
    }

    private static Button action(String text, String accessibleText, Runnable handler) {
        Button button = new Button(text);
        button.setAccessibleText(accessibleText);
        button.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
        button.setOnAction(event -> handler.run());
        return button;
    }

    public void setResult(OperationResult result, SecretVisibilityProfile visibility,
                          Status resultStatus, long durationMillis) {
        Objects.requireNonNull(result, "result");
        status = resultStatus == null ? Status.SUCCESS : resultStatus;
        statusLabel.setText(statusText(status));
        statusLabel.getStyleClass().removeIf(style -> style.startsWith("result-status-"));
        statusLabel.getStyleClass().add("result-status-" + status.name().toLowerCase());
        operationLabel.setText(result.getOperation());
        int inputSize = result.getInput() == null ? 0 : result.getInput().length;
        int outputSize = result.getOutput() == null ? 0 : result.getOutput().length;
        metricsLabel.setText(inputSize + " B → " + outputSize + " B · " + Math.max(0, durationMillis) + " ms");
        outputs.getChildren().clear();

        if (result.getOutput() != null && result.getOutput().length > 0) {
            addOutput("Salida", OperationResultRenderer.render(result, visibility));
        } else if (result.getEnrichedOutput() != null && !result.getEnrichedOutput().isBlank()) {
            addOutput("Salida", OperationResultRenderer.render(result, visibility));
        } else {
            addOutput("Resumen", OperationResultRenderer.render(result, visibility));
        }
    }

    /** Publishes a plain text result for legacy controls that still own their output area. */
    public void showText(String operation, String value) {
        String text = value == null ? "" : value;
        OperationResult result = OperationResult.forOperation(
                        operation == null || operation.isBlank() ? "Resultado" : operation)
                .output(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
        setResult(result, SecretVisibilityProfile.FULL_LAB,
                text.isBlank() ? Status.EMPTY : Status.SUCCESS, 0);
    }

    public void addOutput(String label, String value) {
        Label name = new Label(label == null || label.isBlank() ? "Salida" : label);
        name.getStyleClass().add("result-panel-output-label");
        TextArea area = new TextArea(value == null ? "" : value);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(3);
        area.setAccessibleText(name.getText() + " resultado");
        area.getStyleClass().add("result-panel-output");
        VBox row = new VBox(4, name, area);
        row.getStyleClass().add("result-panel-output-row");
        outputs.getChildren().add(row);
    }

    public void setCopyHandler(Consumer<String> handler) { copyHandler = handler; }
    public void setShelfHandler(Consumer<String> handler) { shelfHandler = handler; }
    public void setExpandHandler(Runnable handler) { expandHandler = handler; }
    public void setSaveStepHandler(Runnable handler) { saveStepHandler = handler; }
    public void setChainHandler(Consumer<String> handler) { chainHandler = handler; }
    public Status getStatus() { return status; }
    public VBox getOutputs() { return outputs; }

    private String currentValue() {
        return outputs.getChildren().stream()
                .filter(VBox.class::isInstance)
                .map(VBox.class::cast)
                .flatMap(row -> row.getChildren().stream())
                .filter(TextArea.class::isInstance)
                .map(Node::toString)
                .findFirst()
                .orElse("");
    }

    private String currentText() {
        for (Node rowNode : outputs.getChildren()) {
            if (rowNode instanceof VBox row) {
                for (Node child : row.getChildren()) {
                    if (child instanceof TextArea area) return area.getText();
                }
            }
        }
        return "";
    }

    private void copyCurrent() {
        String value = currentText();
        if (copyHandler != null) copyHandler.accept(value);
        else javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                new javafx.scene.input.ClipboardContent() {{ putString(value); }});
        copiedLabel.setText("Copiado");
        copiedLabel.setManaged(true);
        copiedLabel.setVisible(true);
    }

    private void addCurrentToShelf() {
        if (shelfHandler != null) shelfHandler.accept(currentText());
    }

    private void chainCurrent() {
        if (chainHandler != null) chainHandler.accept(currentText());
    }

    private static void run(Runnable handler) { if (handler != null) handler.run(); }

    private static String statusText(Status value) {
        return switch (value) {
            case SUCCESS -> "✓ Éxito";
            case ERROR -> "✕ Error";
            case WARNING -> "⚠ Advertencia";
            case EMPTY -> "Sin resultado";
        };
    }

    /** Groups hexadecimal bytes into stable, readable blocks without changing their value. */
    public static String groupHex(String hex, int blockSize) {
        if (hex == null || hex.isBlank()) return "";
        if (blockSize <= 0) throw new IllegalArgumentException("blockSize must be positive");
        String compact = hex.replaceAll("\\s+", "");
        if (!compact.matches("(?i)[0-9a-f]+")) return hex;
        StringBuilder grouped = new StringBuilder(compact.length() + compact.length() / blockSize);
        for (int i = 0; i < compact.length(); i++) {
            if (i > 0 && i % blockSize == 0) grouped.append(' ');
            grouped.append(compact.charAt(i));
        }
        return grouped.toString();
    }
}
