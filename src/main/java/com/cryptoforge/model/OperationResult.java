package com.cryptoforge.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado normalizado de una operación del laboratorio.
 *
 * <p>Transporta la información que debe aparecer de forma coherente en el
 * inspector, la barra de estado y el histórico. No persiste secretos por sí
 * mismo: el llamador decide expresamente qué detalles incluir.</p>
 */
public final class OperationResult {
    private final String operation;
    private final byte[] input;
    private final byte[] output;
    private final List<OperationDetail> details;
    private final String statusMessage;

    private OperationResult(Builder builder) {
        this.operation = builder.operation;
        this.input = copy(builder.input);
        this.output = copy(builder.output);
        this.details = Collections.unmodifiableList(new ArrayList<>(builder.details));
        this.statusMessage = builder.statusMessage;
    }

    public String getOperation() { return operation; }
    public byte[] getInput() { return copy(input); }
    public byte[] getOutput() { return copy(output); }
    public List<OperationDetail> getDetails() { return details; }
    public String getStatusMessage() { return statusMessage; }

    public static Builder forOperation(String operation) {
        return new Builder(operation);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : value.clone();
    }

    public static final class Builder {
        private final String operation;
        private byte[] input;
        private byte[] output;
        private final List<OperationDetail> details = new ArrayList<>();
        private String statusMessage;

        private Builder(String operation) {
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("Operation name is required");
            }
            this.operation = operation;
        }

        public Builder input(byte[] value) { this.input = copy(value); return this; }
        public Builder output(byte[] value) { this.output = copy(value); return this; }
        public Builder detail(OperationDetail detail) {
            if (detail != null) this.details.add(detail);
            return this;
        }
        public Builder detail(String key, String value) {
            if (key != null && value != null) this.details.add(OperationDetail.publicDetail(key, value));
            return this;
        }
        public Builder details(java.util.Map<String, String> map) {
            if (map != null) {
                map.forEach((k, v) -> this.details.add(OperationDetail.publicDetail(k, v)));
            }
            return this;
        }
        public Builder details(java.util.List<OperationDetail> list) {
            if (list != null) {
                this.details.addAll(list);
            }
            return this;
        }
        public Builder status(String value) { this.statusMessage = value; return this; }
        public OperationResult build() { return new OperationResult(this); }
    }
}
