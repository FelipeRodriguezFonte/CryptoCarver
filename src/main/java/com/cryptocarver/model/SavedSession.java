package com.cryptocarver.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public class SavedSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String timestamp;
    private String operation; // The content header/operation name when saved
    private Map<String, Object> uiState;

    public SavedSession(String name, String operation, Map<String, Object> uiState) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.operation = operation;
        this.uiState = uiState;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getOperation() {
        return operation;
    }

    public Map<String, Object> getUiState() {
        return uiState;
    }

    @Override
    public String toString() {
        return timestamp + " - " + name + " (" + operation + ")";
    }
}
