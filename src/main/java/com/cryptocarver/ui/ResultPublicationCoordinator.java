package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Publishes a completed result to the summary bar, inspector, and history. */
final class ResultPublicationCoordinator {
    private final Consumer<OperationResult> accept, inspector;
    private final Runnable refreshTrail;
    private final BiConsumer<OperationResult, List<OperationDetail>> history;
    private final Consumer<String> status;
    private final Button addSessionStep;
    private final HBox summary;
    private final Label operation, algorithm, size, format, badge;
    private final ComboBox<String> outputFormat;
    private final I18nService i18n;

    ResultPublicationCoordinator(Consumer<OperationResult> accept, Consumer<OperationResult> inspector,
            Runnable refreshTrail, BiConsumer<OperationResult, List<OperationDetail>> history,
            Consumer<String> status, Button addSessionStep, HBox summary, Label operation, Label algorithm,
            Label size, Label format, Label badge, ComboBox<String> outputFormat, I18nService i18n) {
        this.accept=accept; this.inspector=inspector; this.refreshTrail=refreshTrail; this.history=history; this.status=status;
        this.addSessionStep=addSessionStep; this.summary=summary; this.operation=operation; this.algorithm=algorithm;
        this.size=size; this.format=format; this.badge=badge; this.outputFormat=outputFormat; this.i18n=i18n;
    }

    void publish(OperationResult result) {
        if (result == null) return;
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> publish(result)); return; }
        accept.accept(result); inspector.accept(result); refreshTrail.run(); history.accept(result, com.cryptocarver.model.ResultPresentationPolicy.detailsForHistory(result));
        if (result.getStatusMessage() != null && !result.getStatusMessage().isBlank()) status.accept(result.getStatusMessage());
        boolean failed = result.getStatusMessage() != null && result.getStatusMessage().toLowerCase(java.util.Locale.ROOT).contains("failed");
        boolean payload = (result.getOutput() != null && result.getOutput().length > 0)
                || (result.getEnrichedOutput() != null && !result.getEnrichedOutput().isBlank());
        boolean inspectable = payload || !result.getDetails().isEmpty();
        if (addSessionStep != null) addSessionStep.setDisable(failed || !inspectable);
        if (summary == null) return;
        if (failed || !inspectable) { summary.setManaged(false); summary.setVisible(false); return; }
        summary.setManaged(true); summary.setVisible(true);
        if (operation != null) operation.setText(result.getOperation());
        String algo = "N/A";
        for (OperationDetail detail : result.getDetails()) if ("Algorithm".equalsIgnoreCase(detail.name()) || "Type".equalsIgnoreCase(detail.name())) { algo = detail.value(); break; }
        if (algorithm != null) algorithm.setText(algo);
        int inLen = result.getInput() != null ? result.getInput().length : 0;
        int outLen = result.getOutput() != null ? result.getOutput().length : 0;
        if (size != null) size.setText(i18n.text("result.size", inLen, outLen));
        String output = outputFormat != null ? outputFormat.getValue() : "HEX";
        if (format != null) format.setText(output);
        if (badge != null) { badge.setText(i18n.text("result.success")); badge.getStyleClass().setAll("result-status-success"); badge.setStyle(""); badge.setAccessibleText(i18n.text("a11y.resultStatus", badge.getText())); }
    }
}
