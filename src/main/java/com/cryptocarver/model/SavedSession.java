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
    private OperationSessionLog operationLog;
    private int version = 1;
    private ProtectedFields protectedFields;

    public SavedSession(String name, String operation, Map<String, Object> uiState) {
        this(name, operation, uiState, null);
    }

    public SavedSession(String name, String operation, Map<String, Object> uiState,
                        OperationSessionLog operationLog) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.operation = operation;
        this.uiState = uiState;
        this.operationLog = operationLog == null ? null : operationLog.copy();
    }

    public String getId() {
        return id;
    }
    public void setId(String id) { this.id = id; }

    public String getName() {
        return name;
    }

    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getOperation() {
        return operation;
    }

    public Map<String, Object> getUiState() {
        return uiState;
    }
    public void setUiState(Map<String, Object> uiState) { this.uiState = uiState; }

    public OperationSessionLog getOperationLog() {
        return operationLog == null ? null : operationLog.copy();
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public ProtectedFields getProtectedFields() { return protectedFields; }
    public void setProtectedFields(ProtectedFields protectedFields) { this.protectedFields = protectedFields; }

    public static final class ProtectedFields {
        private String kdf;
        private int iterations;
        private String salt;
        private String nonce;
        private String ciphertext;

        public ProtectedFields() { }
        public ProtectedFields(String kdf, int iterations, String salt, String nonce, String ciphertext) {
            this.kdf = kdf; this.iterations = iterations; this.salt = salt; this.nonce = nonce; this.ciphertext = ciphertext;
        }
        public String getKdf() { return kdf; }
        public int getIterations() { return iterations; }
        public String getSalt() { return salt; }
        public String getNonce() { return nonce; }
        public String getCiphertext() { return ciphertext; }
    }

    @Override
    public String toString() {
        return timestamp + " - " + name + " (" + operation + ")";
    }
}
