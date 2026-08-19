package com.cryptocarver.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HistoryCommand implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Reproducibility {
        REPRODUCIBLE_WITHOUT_SECRETS,
        REPRODUCIBLE_WITH_SECRETS,
        NOT_REPRODUCIBLE
    }

    private String id;
    private String timestamp;
    private String operation;
    /** Navigation target captured when the operation ran. Kept separate from the descriptive result label. */
    private String navigationOperation;
    private String details;
    private List<OperationDetail> structuredDetails;

    // Use parameters as the clean recipe. Alternate allows loading legacy uiState.
    @SerializedName(value = "parameters", alternate = {"uiState"})
    private Map<String, Object> parameters;

    private Reproducibility reproducibility;
    private String reproducibilityReason;
    private String inputFormat;
    private String outputFormat;
    private String version;

    // Legacy constructor wrapper
    public HistoryCommand(String operation, String details, Map<String, Object> parameters) {
        this(operation, details, parameters, Reproducibility.REPRODUCIBLE_WITH_SECRETS, "Legacy execution", null, null);
    }

    public HistoryCommand(String operation, String details, Map<String, Object> parameters,
                          Reproducibility reproducibility, String reproducibilityReason,
                          String inputFormat, String outputFormat) {
        this(operation, details, parameters, reproducibility, reproducibilityReason,
                inputFormat, outputFormat, null);
    }

    public HistoryCommand(String operation, String details, Map<String, Object> parameters,
                          Reproducibility reproducibility, String reproducibilityReason,
                          String inputFormat, String outputFormat, String navigationOperation) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.operation = operation;
        this.navigationOperation = navigationOperation;
        this.details = details;
        this.parameters = parameters != null ? parameters : java.util.Collections.emptyMap();
        this.reproducibility = reproducibility;
        this.reproducibilityReason = reproducibilityReason;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.version = "2.0";
    }

    public void setStructuredDetails(List<OperationDetail> structuredDetails) {
        this.structuredDetails = structuredDetails;
    }

    public List<OperationDetail> getStructuredDetails() {
        return structuredDetails;
    }

    public String getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getOperation() { return operation; }
    /** Returns the originating workspace, or the result label for legacy history records. */
    public String getNavigationOperation() {
        return navigationOperation == null || navigationOperation.isBlank() ? operation : navigationOperation;
    }
    public String getDetails() { return details; }

    // Kept for backward compatibility mapping in other classes for now
    public Map<String, Object> getUiState() {
        return getParameters();
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Reproducibility getReproducibility() {
        // Fallback for legacy items without a saved reproducibility state
        return reproducibility == null ? Reproducibility.REPRODUCIBLE_WITH_SECRETS : reproducibility;
    }

    public String getReproducibilityReason() {
        return reproducibilityReason == null ? "Legacy history item" : reproducibilityReason;
    }

    public String getInputFormat() { return inputFormat; }
    public String getOutputFormat() { return outputFormat; }
    public String getVersion() { return version == null ? "1.0" : version; }

    @Override
    public String toString() {
        return timestamp + " - " + operation;
    }
}
