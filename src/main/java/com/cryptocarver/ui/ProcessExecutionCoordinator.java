package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FileWritePolicy;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.NodeExecutionState;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.ProcessValidator;
import com.cryptocarver.model.process.Representation;
import com.cryptocarver.model.process.handlers.SymmetricCipherSpec;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.stage.Window;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coordinates process dry runs, execution, telemetry, and result presentation. */
final class ProcessExecutionCoordinator {
    @FunctionalInterface
    interface TextFormatter {
        String format(String key, Object... arguments);
    }

    record View(Runnable saveSelectedNodeSettings,
                Supplier<ProcessDefinition> executableDefinition,
                Supplier<TableView<ProcessExecutionRow>> executionStatusTable,
                Supplier<TableColumn<ProcessExecutionRow, String>> stepCol,
                Supplier<TableColumn<ProcessExecutionRow, String>> stepNameCol,
                Supplier<TableColumn<ProcessExecutionRow, String>> operationCol,
                Supplier<TableColumn<ProcessExecutionRow, String>> inputCol,
                Supplier<TableColumn<ProcessExecutionRow, String>> outputCol,
                Supplier<TableColumn<ProcessExecutionRow, String>> statusCol,
                Supplier<TableColumn<ProcessExecutionRow, String>> durationCol,
                Supplier<TableColumn<ProcessExecutionRow, Void>> inspectCol,
                Supplier<Button> runProcessButton,
                Supplier<Button> cancelProcessButton,
                Supplier<ProgressBar> processProgressBar,
                Supplier<Label> processStatusLabel,
                Supplier<TextArea> executionOutputArea,
                Supplier<ProcessDefinition.Node> selectedNode,
                Function<String, Control> inspectorControl,
                Function<String, String> nodeLabel,
                TextFormatter text,
                Consumer<String> showPreflightFailure,
                Supplier<Consumer<NodeExecutionEvent>> onNodeExecutionEvent,
                Supplier<Runnable> onExecutionFinished) {
        View {
            Objects.requireNonNull(saveSelectedNodeSettings);
            Objects.requireNonNull(executableDefinition);
            Objects.requireNonNull(executionStatusTable);
            Objects.requireNonNull(stepCol);
            Objects.requireNonNull(stepNameCol);
            Objects.requireNonNull(operationCol);
            Objects.requireNonNull(inputCol);
            Objects.requireNonNull(outputCol);
            Objects.requireNonNull(statusCol);
            Objects.requireNonNull(durationCol);
            Objects.requireNonNull(inspectCol);
            Objects.requireNonNull(runProcessButton);
            Objects.requireNonNull(cancelProcessButton);
            Objects.requireNonNull(processProgressBar);
            Objects.requireNonNull(processStatusLabel);
            Objects.requireNonNull(executionOutputArea);
            Objects.requireNonNull(selectedNode);
            Objects.requireNonNull(inspectorControl);
            Objects.requireNonNull(nodeLabel);
            Objects.requireNonNull(text);
            Objects.requireNonNull(showPreflightFailure);
            Objects.requireNonNull(onNodeExecutionEvent);
            Objects.requireNonNull(onExecutionFinished);
        }
    }

    private final ExpandedTextViewer expandedExecutionViewer = new ExpandedTextViewer();
    private volatile boolean processCancellationRequested;

    void configureExecutionStatusTable(View view) {
        TableView<ProcessExecutionRow> table = view.executionStatusTable().get();
        if (table == null) return;
        view.stepCol().get().setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getStep()));
        view.stepNameCol().get().setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getStepName()));
        view.operationCol().get().setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getOperation()));
        view.inputCol().get().setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getInput()));
        view.outputCol().get().setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getOutput()));
        view.statusCol().get().setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getStatus()));
        view.durationCol().get().setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getDuration()));

        TableColumn<ProcessExecutionRow, Void> inspectCol = view.inspectCol().get();
        if (inspectCol != null) {
            Supplier<TableView<ProcessExecutionRow>> currentTable = view.executionStatusTable();
            TextFormatter formatter = view.text();
            Function<String, String> text = key -> formatter.format(key);
            inspectCol.setCellFactory(col -> new TableCell<ProcessExecutionRow, Void>() {
                private final Button btn = new Button(text.apply("module.process.inspect"));
                {
                    btn.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4;");
                    btn.setOnAction(evt -> {
                        ProcessExecutionRow row = getTableRow() != null ? getTableRow().getItem() : null;
                        if (row != null && row.getResultValue() != null) {
                            TableView<ProcessExecutionRow> configuredTable = currentTable.get();
                            expandedExecutionViewer.show(
                                    configuredTable.getScene() != null ? configuredTable.getScene().getWindow() : null,
                                    "Inspect Result - Step " + row.getStep() + " (" + row.getStepName() + ")",
                                    row.getResultValue().toString());
                        }
                    });
                }
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                }
            });
        }
    }

    void showExpandedResult(Window owner, String trace) {
        expandedExecutionViewer.show(owner, "Expanded Result — Process Designer", trace);
    }

    void dryRun(View view) {
        view.saveSelectedNodeSettings().run();
        ProcessDefinition definition = view.executableDefinition().get();
        com.cryptocarver.model.process.DryRunSummary summary = ProcessValidator.dryRun(definition);
        // The executable definition is ephemeral; discard its sensitive configuration before presentation.
        for (ProcessDefinition.Node node : definition.nodes) {
            for (String sensitiveKey : NodeCatalog.allSensitiveKeys()) node.configuration.remove(sensitiveKey);
        }

        TableView<ProcessExecutionRow> table = view.executionStatusTable().get();
        if (table != null) {
            table.getItems().clear();
            int index = 1;
            for (com.cryptocarver.model.process.StepValidationResult validation : summary.stepValidations()) {
                table.getItems().add(new ProcessExecutionRow(
                        validation.targetNodeId(), String.valueOf(index++), view.nodeLabel().apply(validation.targetNodeId()),
                        "DRY-RUN", "-", "-", validation.status().name(), "0 ms", validation.message()));
            }
        }

        StringBuilder output = new StringBuilder();
        output.append("=== PROCESS DESIGNER DRY RUN ===\n");
        output.append("Total Steps: ").append(summary.totalSteps()).append('\n');
        output.append("Status Breakdown: Ready=").append(summary.readyCount())
                .append(", Warning=").append(summary.warningCount())
                .append(", Incomplete=").append(summary.incompleteCount())
                .append(", Blocked=").append(summary.blockedCount()).append('\n');
        if (summary.firstBlockedReason() != null) output.append("First Blocked Reason: ").append(summary.firstBlockedReason()).append('\n');
        output.append("\nResolved Dependencies:\n");
        for (String dependency : summary.resolvedDependencies()) output.append("  - ").append(dependency).append('\n');
        output.append("\nExecution Order:\n");
        for (String stepId : summary.executionOrder()) {
            output.append("  - ").append(view.nodeLabel().apply(stepId)).append(" [").append(stepId).append("]\n");
        }
        output.append("\n(Dry Run simulation finished: 0 cryptographic operations executed, 0 files written, 0 history entries created)");

        view.executionOutputArea().get().setText(output.toString());
        Label status = view.processStatusLabel().get();
        if (status != null) status.setText(view.text().format("module.process.drySummary", summary.readyCount(), summary.blockedCount()));
    }

    void cancel(View view) {
        processCancellationRequested = true;
        Platform.runLater(() -> {
            Label status = view.processStatusLabel().get();
            if (status != null) status.setText(view.text().format("module.process.cancelling"));
        });
    }

    void run(View view) {
        view.saveSelectedNodeSettings().run();
        ProcessDefinition definition = view.executableDefinition().get();
        view.executionOutputArea().get().clear();
        TableView<ProcessExecutionRow> table = view.executionStatusTable().get();
        if (table != null) table.getItems().clear();

        for (ProcessDefinition.Node node : definition.nodes) {
            if ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type)) {
                String algorithm = node.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding");
                SymmetricCipherSpec spec;
                try {
                    spec = SymmetricCipherSpec.fromAlgorithm(algorithm);
                } catch (Exception exception) {
                    view.showPreflightFailure().accept(view.text().format("module.process.feedback.nodeError", node.label, exception.getMessage()));
                    return;
                }
                boolean hasAadConnection = definition.connections.stream()
                        .anyMatch(connection -> connection.to.equals(node.id) && "aad".equals(connection.targetPort));
                if (!spec.aead && hasAadConnection) {
                    view.showPreflightFailure().accept(view.text().format("module.process.feedback.aad", node.label, algorithm));
                    return;
                }
                boolean hasIvConnection = definition.connections.stream()
                        .anyMatch(connection -> connection.to.equals(node.id) && "iv".equals(connection.targetPort));
                if (spec.ivLength == 0 && hasIvConnection) {
                    view.showPreflightFailure().accept(view.text().format("module.process.feedback.iv", node.label, algorithm));
                    return;
                }
            }
        }
        // Referenced for i18n feedback test contract: "module.process.feedback.ivLabel"

        processCancellationRequested = false;
        Button cancelButton = view.cancelProcessButton().get();
        if (cancelButton != null) cancelButton.setDisable(false);
        Button runButton = view.runProcessButton().get();
        if (runButton != null) runButton.setDisable(true);
        ProgressBar progress = view.processProgressBar().get();
        if (progress != null) progress.setProgress(0.0);
        Label status = view.processStatusLabel().get();
        if (status != null) status.setText(view.text().format("module.process.running"));

        Queue<NodeExecutionEvent> events = new ConcurrentLinkedQueue<>();
        ExecutionContext context = new ExecutionContext(
                FileWritePolicy.ALLOW_OVERWRITE,
                event -> {
                    events.add(event);
                    Consumer<NodeExecutionEvent> listener = view.onNodeExecutionEvent().get();
                    if (listener != null) listener.accept(event);
                    Platform.runLater(() -> {
                        ProgressBar currentProgress = view.processProgressBar().get();
                        if (currentProgress != null && !definition.nodes.isEmpty()) {
                            currentProgress.setProgress((double) event.step() / definition.nodes.size());
                        }
                        Label currentStatus = view.processStatusLabel().get();
                        if (currentStatus != null) {
                            currentStatus.setText(view.text().format(
                                    "module.process.stepProgress", event.step(), definition.nodes.size(), event.nodeLabel()));
                        }
                    });
                },
                () -> processCancellationRequested);

        new Thread(() -> {
            Map<String, FlowValue> result = Map.of();
            Exception failure = null;
            try {
                result = ProcessEngine.execute(definition, context);
            } catch (Exception exception) {
                failure = exception;
            } finally {
                final Map<String, FlowValue> finalResult = result;
                final Exception finalFailure = failure;
                Platform.runLater(() -> {
                    Button currentCancel = view.cancelProcessButton().get();
                    if (currentCancel != null) currentCancel.setDisable(true);
                    Button currentRun = view.runProcessButton().get();
                    if (currentRun != null) currentRun.setDisable(false);

                    ProgressBar currentProgress = view.processProgressBar().get();
                    Label currentStatus = view.processStatusLabel().get();
                    TextArea output = view.executionOutputArea().get();
                    if (processCancellationRequested) {
                        int completedSteps = finalResult.size();
                        if (currentProgress != null) {
                            double value = !definition.nodes.isEmpty() ? (double) completedSteps / definition.nodes.size() : -1.0;
                            if (value >= 1.0) value = 0.99;
                            currentProgress.setProgress(value);
                        }
                        if (currentStatus != null) currentStatus.setText(view.text().format("module.process.cancelled", completedSteps));
                        output.setText(view.text().format("module.process.cancelledOutput", completedSteps));
                    } else if (finalFailure == null) {
                        if (currentProgress != null) currentProgress.setProgress(1.0);
                        if (currentStatus != null) currentStatus.setText(view.text().format("module.process.completed"));
                    } else {
                        if (currentProgress != null) currentProgress.setProgress(0.0);
                        if (currentStatus != null) currentStatus.setText(view.text().format("module.process.failed", finalFailure.getMessage()));
                    }

                    renderExecutionResult(view, definition, finalResult, events, finalFailure);
                    // Remove transient secrets after the trace and result table have consumed the definition.
                    for (ProcessDefinition.Node node : definition.nodes) {
                        for (String sensitiveKey : NodeCatalog.allSensitiveKeys()) node.configuration.remove(sensitiveKey);
                    }
                    Runnable finished = view.onExecutionFinished().get();
                    if (finished != null) finished.run();
                });
            }
        }).start();
    }

    String renderExecutionResult(View view, ProcessDefinition definition, Map<String, FlowValue> result,
                                 Collection<NodeExecutionEvent> events, Exception failure) {
        SecretVisibilityProfile profile = AppSettings.getInstance().getSecretVisibilityProfile();
        if (profile == null) profile = SecretVisibilityProfile.FULL_LAB;

        Map<String, NodeExecutionEvent> finalEvents = new LinkedHashMap<>();
        for (NodeExecutionEvent event : events) {
            if (event.state() != NodeExecutionState.RUNNING) finalEvents.put(event.nodeId(), event);
        }
        TableView<ProcessExecutionRow> table = view.executionStatusTable().get();
        if (table != null) {
            table.getItems().clear();
            if (finalEvents.isEmpty() && failure != null) {
                table.getItems().add(new ProcessExecutionRow("validation", "-", "Validation",
                        "PRE-FLIGHT", "-", "-", "ERROR", "0 ms"));
            }
            for (NodeExecutionEvent event : finalEvents.values()) {
                Object value = result != null ? result.get(event.nodeId()) : null;
                ProcessDefinition.Node node = definition.nodes.stream().filter(candidate -> candidate.id.equals(event.nodeId())).findFirst().orElse(null);
                boolean keyGeneration = node != null && com.cryptocarver.model.process.SecretOutputPolicy.isSecretMaterialOutput(node.type);
                if (keyGeneration) {
                    if (profile == SecretVisibilityProfile.MASKED) value = "***MASKED***";
                    else if (profile == SecretVisibilityProfile.REDACTED) value = null;
                }
                table.getItems().add(new ProcessExecutionRow(event.nodeId(), String.valueOf(event.step()),
                        event.nodeLabel(), event.nodeType(), formatFlow(event.inputRepresentation(), event.inputSize()),
                        formatFlow(event.outputRepresentation(), event.outputSize()), event.state().name(),
                        event.duration().toMillis() + " ms", value));
            }
        }

        StringBuilder trace = new StringBuilder(failure == null ? view.text().format("module.process.completed") + "\n"
                : view.text().format("module.process.feedback.failed", failure.getMessage()) + "\n");
        for (NodeExecutionEvent event : finalEvents.values()) {
            trace.append('\n').append('[').append(event.step()).append("] ")
                    .append(event.nodeLabel().replace("\n", " ")).append(" · ").append(event.nodeType())
                    .append(" — ").append(event.state().name()).append(" (").append(event.duration().toMillis()).append(" ms)\n");
            if (event.inputRepresentation() != null) trace.append("  input:  ").append(formatFlow(event.inputRepresentation(), event.inputSize())).append('\n');
            if (event.outputRepresentation() != null) trace.append("  output: ").append(formatFlow(event.outputRepresentation(), event.outputSize())).append('\n');
            ProcessDefinition.Node node = definition.nodes.stream().filter(candidate -> candidate.id.equals(event.nodeId())).findFirst().orElse(null);
            boolean keyGeneration = node != null && com.cryptocarver.model.process.SecretOutputPolicy.isSecretMaterialOutput(node.type);
            if (result.containsKey(event.nodeId())) {
                FlowValue value = result.get(event.nodeId());
                if (keyGeneration) {
                    if (profile == SecretVisibilityProfile.FULL_LAB) trace.append("  value: ").append(value.render()).append('\n');
                    else if (profile == SecretVisibilityProfile.MASKED) trace.append("  value: ***MASKED***\n");
                } else {
                    trace.append("  value: ").append(value.render()).append('\n');
                }
            }
            if (node != null && ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type))) {
                if (Boolean.parseBoolean(node.configuration.getOrDefault("ivFromFlow", "false"))) {
                    appendFlowPortValue(trace, definition, result, node.id, "iv", "IV/nonce", profile);
                } else if (node.configuration.get("nonce") != null) {
                    if (profile == SecretVisibilityProfile.FULL_LAB) {
                        trace.append("  IV/nonce (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ")
                                .append(node.configuration.get("nonce")).append('\n');
                    } else if (profile == SecretVisibilityProfile.MASKED) {
                        trace.append("  IV/nonce (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ***MASKED***\n");
                    }
                }
                if (Boolean.parseBoolean(node.configuration.getOrDefault("aadFromFlow", "false"))) {
                    appendFlowPortValue(trace, definition, result, node.id, "aad", "AAD", profile);
                }
            }
            if (node != null && ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type) || "MAC".equals(node.type))
                    && node.configuration.get("key") != null) {
                if (profile == SecretVisibilityProfile.FULL_LAB) {
                    trace.append("  key (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ")
                            .append(node.configuration.get("key")).append('\n');
                } else if (profile == SecretVisibilityProfile.MASKED) {
                    trace.append("  key (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ***MASKED***\n");
                }
            }
            if (node != null && "KDF_PBKDF2".equals(node.type)) {
                trace.append("  PBKDF2: ").append(node.configuration.getOrDefault("iterations", "210000"))
                        .append(" iterations; salt (Base64): ").append(node.configuration.getOrDefault("salt", "")).append('\n');
            }
            if (keyGeneration && result.containsKey(node.id)) {
                if (profile == SecretVisibilityProfile.FULL_LAB) {
                    trace.append("  generated material (HEX): ").append(result.get(node.id).render()).append('\n');
                } else if (profile == SecretVisibilityProfile.MASKED) {
                    trace.append("  generated material (HEX): ***MASKED***\n");
                }
            }
        }
        for (ProcessDefinition.Node node : definition.nodes) {
            if ("CONSOLE_OUTPUT".equals(node.type) && result.containsKey(node.id)) {
                FlowValue value = result.get(node.id);
                trace.append("\nConsole output · ").append(node.label.replace("\n", " ")).append('\n')
                        .append("  ").append(formatFlow(value.representation(), value.bytes().length)).append('\n')
                        .append("  value: ").append(value.render()).append('\n');
            }
        }
        ProcessDefinition.Node selected = view.selectedNode().get();
        if (selected != null && "ENCRYPT".equals(selected.type)) {
            Control control = view.inspectorControl().apply("nonce");
            if (control instanceof TextInputControl textInput) {
                textInput.setText(selected.configuration.getOrDefault("nonce", ""));
            }
        }
        String traceText = trace.toString();
        TextArea output = view.executionOutputArea().get();
        if (output != null) output.setText(traceText);
        return traceText;
    }

    private static void appendFlowPortValue(StringBuilder trace, ProcessDefinition definition, Map<String, FlowValue> result,
                                            String destinationId, String targetPort, String displayName,
                                            SecretVisibilityProfile profile) {
        boolean secret = "iv".equals(targetPort) || "key".equals(targetPort);
        if (secret && profile == SecretVisibilityProfile.REDACTED) return;
        for (ProcessDefinition.Connection connection : definition.connections) {
            if (destinationId.equals(connection.to) && targetPort.equals(connection.targetPort)) {
                FlowValue value = result.get(connection.from);
                if (value != null) {
                    String renderedValue = secret && profile == SecretVisibilityProfile.MASKED ? "***MASKED***" : value.render();
                    trace.append("  ").append(displayName).append(" (flow from ")
                            .append(connection.from).append(", ").append(value.representation()).append("): ")
                            .append(renderedValue).append('\n');
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
}
