package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;

import java.util.Objects;

public class SignerConfig {
    private final String algorithm;
    private final String secretOrKey;
    private final SecretEncoding secretEncoding;

    public SignerConfig(String algorithm, String secretOrKey) {
        this(algorithm, secretOrKey, SecretEncoding.UTF8);
    }

    /** {@code secretEncoding} applies to HMAC secrets only; RSA/EC keys ignore it. */
    public SignerConfig(String algorithm, String secretOrKey, SecretEncoding secretEncoding) {
        this.algorithm = Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        this.secretOrKey = Objects.requireNonNull(secretOrKey, "Secret or key cannot be null");
        this.secretEncoding = secretEncoding == null ? SecretEncoding.UTF8 : secretEncoding;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getSecretOrKey() {
        return secretOrKey;
    }

    public SecretEncoding getSecretEncoding() {
        return secretEncoding;
    }
}
