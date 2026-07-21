package com.cryptocarver.crypto;

import java.util.Objects;

public class SignerConfig {
    private final String algorithm;
    private final String secretOrKey;

    public SignerConfig(String algorithm, String secretOrKey) {
        this.algorithm = Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        this.secretOrKey = Objects.requireNonNull(secretOrKey, "Secret or key cannot be null");
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getSecretOrKey() {
        return secretOrKey;
    }
}
