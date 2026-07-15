package com.cryptoforge.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class HistoryItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String timestamp;
    private String operation;
    private String details;
    private java.util.List<OperationDetail> structuredDetails;
    private Map<String, Object> uiState;

    public HistoryItem(String operation, String details, Map<String, Object> uiState) {
        this.id = java.util.UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.operation = operation;
        this.details = details;
        this.uiState = uiState;
    }

    public void setStructuredDetails(java.util.List<OperationDetail> structuredDetails) {
        this.structuredDetails = structuredDetails;
    }

    public java.util.List<OperationDetail> getStructuredDetails() {
        return structuredDetails;
    }

    public String getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getOperation() {
        return operation;
    }

    public String getDetails() {
        return details;
    }

    public Map<String, Object> getUiState() {
        return uiState;
    }

    @Override
    public String toString() {
        return timestamp + " - " + operation;
    }
}
