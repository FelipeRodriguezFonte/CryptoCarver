package com.cryptocarver.ui;

import javafx.beans.property.SimpleStringProperty;

/** One immutable row in the Process Designer execution-status table. */
public final class ProcessExecutionRow {
    private final SimpleStringProperty step = new SimpleStringProperty();
    private final SimpleStringProperty stepName = new SimpleStringProperty();
    private final SimpleStringProperty operation = new SimpleStringProperty();
    private final SimpleStringProperty input = new SimpleStringProperty();
    private final SimpleStringProperty output = new SimpleStringProperty();
    private final SimpleStringProperty status = new SimpleStringProperty();
    private final SimpleStringProperty duration = new SimpleStringProperty();
    private final String nodeId;

    public ProcessExecutionRow(String nodeId, String step, String stepName, String operation, String input,
            String output, String status, String duration) {
        this.nodeId = nodeId;
        this.step.set(step);
        this.stepName.set(stepName);
        this.operation.set(operation);
        this.input.set(input);
        this.output.set(output);
        this.status.set(status);
        this.duration.set(duration);
    }

    public String getStep() { return step.get(); }
    public String getStepName() { return stepName.get(); }
    public String getOperation() { return operation.get(); }
    public String getInput() { return input.get(); }
    public String getOutput() { return output.get(); }
    public String getStatus() { return status.get(); }
    public String getDuration() { return duration.get(); }
    public String getNodeId() { return nodeId; }
}
