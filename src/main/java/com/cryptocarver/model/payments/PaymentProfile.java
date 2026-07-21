package com.cryptocarver.model.payments;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates parameters and expected laboratory vectors for a repeatable payment cryptography scenario.
 */
public class PaymentProfile {
    public enum ProfileType {
        EMV, DUKPT_TDES, DUKPT_AES, TR31, PIN, SECURE_MESSAGING
    }

    public enum ExpectedResult {
        SUCCESS, FAILURE
    }

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final ProfileType type;
    private final ExpectedResult expectedResult;
    private final String expectedErrorFragment;

    // Explicit parameters: schema, padding, format, currency, CVN, lengths, etc.
    private final Map<String, String> parameters;

    // Expected vectors for reproducibility
    private final Map<String, String> inputs;
    private final Map<String, String> outputs;

    private PaymentProfile(Builder builder) {
        if (builder.id == null || builder.id.trim().isEmpty()) throw new IllegalArgumentException("Profile ID is required");
        if (builder.name == null || builder.name.trim().isEmpty()) throw new IllegalArgumentException("Profile Name is required");
        if (builder.version == null || builder.version.trim().isEmpty()) throw new IllegalArgumentException("Profile Version is required");
        if (builder.type == null) throw new IllegalArgumentException("Profile Type is required");
        if (builder.expectedResult == ExpectedResult.FAILURE && builder.expectedErrorFragment == null) {
            throw new IllegalArgumentException("FAILURE profile must specify an expectedErrorFragment");
        }

        switch (builder.type) {
            case DUKPT_TDES:
            case DUKPT_AES:
                if (!builder.inputs.containsKey("bdk") || !builder.inputs.containsKey("ksn")) {
                    throw new IllegalArgumentException("DUKPT requires 'bdk' and 'ksn' inputs");
                }
                if (builder.type == ProfileType.DUKPT_AES && !builder.parameters.containsKey("scheme")) {
                    throw new IllegalArgumentException("DUKPT AES requires 'scheme' parameter");
                }
                break;
            case TR31:
                if (!builder.inputs.containsKey("kbpk") || !builder.inputs.containsKey("keyToWrap")) {
                    throw new IllegalArgumentException("TR31 requires 'kbpk' and 'keyToWrap' inputs");
                }
                if (!builder.parameters.containsKey("version") || !builder.parameters.containsKey("algorithm") ||
                    !builder.parameters.containsKey("usage") || !builder.parameters.containsKey("mode") ||
                    !builder.parameters.containsKey("exportability")) {
                    throw new IllegalArgumentException("TR31 requires version, algorithm, usage, mode, exportability");
                }
                break;
            case EMV:
                if (!builder.inputs.containsKey("imk") || !builder.inputs.containsKey("pan") ||
                    !builder.inputs.containsKey("panSeq") || !builder.inputs.containsKey("atc")) {
                    throw new IllegalArgumentException("EMV requires 'imk', 'pan', 'panSeq', 'atc' inputs");
                }
                if (!builder.inputs.containsKey("transactionData") && (!builder.inputs.containsKey("arqc") || !builder.inputs.containsKey("arc"))) {
                    throw new IllegalArgumentException("EMV requires 'transactionData' OR ('arqc' and 'arc')");
                }
                break;
            case PIN:
                if (!builder.parameters.containsKey("format")) {
                    throw new IllegalArgumentException("PIN profile missing mandatory 'format' parameter");
                }
                if (!builder.parameters.containsKey("method") || !"Translate".equals(builder.parameters.get("method"))) {
                    if (!builder.inputs.containsKey("pin") && !builder.outputs.containsKey("pin")) {
                        throw new IllegalArgumentException("PIN requires 'pin' input or output");
                    }
                }
                break;
            case SECURE_MESSAGING:
                if (!builder.inputs.containsKey("sessionKey") || !builder.inputs.containsKey("apdu")) {
                    throw new IllegalArgumentException("SECURE_MESSAGING requires 'sessionKey' and 'apdu' inputs");
                }
                if (!builder.parameters.containsKey("algorithm")) {
                    throw new IllegalArgumentException("SECURE_MESSAGING requires 'algorithm' parameter");
                }
                break;
            default:
                break;
        }

        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.type = builder.type;
        this.expectedResult = builder.expectedResult;
        this.expectedErrorFragment = builder.expectedErrorFragment;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(builder.parameters));
        this.inputs = Collections.unmodifiableMap(new HashMap<>(builder.inputs));
        this.outputs = Collections.unmodifiableMap(new HashMap<>(builder.outputs));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public ProfileType getType() { return type; }
    public ExpectedResult getExpectedResult() { return expectedResult; }
    public String getExpectedErrorFragment() { return expectedErrorFragment; }

    public String getParameter(String key) { return parameters.get(key); }
    public String getInput(String key) { return inputs.get(key); }
    public String getOutput(String key) { return outputs.get(key); }

    public Map<String, String> getParameters() { return parameters; }
    public Map<String, String> getInputs() { return inputs; }
    public Map<String, String> getOutputs() { return outputs; }

    @Override
    public String toString() {
        return name + " (v" + version + ")";
    }

    public static class Builder {
        private final String id;
        private final String name;
        private final String version;
        private final ProfileType type;

        private String description = "";
        private ExpectedResult expectedResult = ExpectedResult.SUCCESS;
        private String expectedErrorFragment = null;

        private final Map<String, String> parameters = new HashMap<>();
        private final Map<String, String> inputs = new HashMap<>();
        private final Map<String, String> outputs = new HashMap<>();

        public Builder(String id, String name, String version, ProfileType type) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.type = type;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder expectedResult(ExpectedResult expectedResult) {
            this.expectedResult = expectedResult;
            return this;
        }

        public Builder expectedErrorFragment(String fragment) {
            this.expectedErrorFragment = fragment;
            return this;
        }

        public Builder addParameter(String key, String value) {
            this.parameters.put(key, value);
            return this;
        }

        public Builder addInput(String key, String hexValue) {
            this.inputs.put(key, hexValue);
            return this;
        }

        public Builder addOutput(String key, String hexValue) {
            this.outputs.put(key, hexValue);
            return this;
        }

        public PaymentProfile build() {
            return new PaymentProfile(this);
        }
    }
}
