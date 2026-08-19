package com.cryptocarver.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A versioned, self-describing container for ciphertext produced anywhere in the app.
 *
 * <p>Generalizes the "metadata alongside the cipher result" idea already used by
 * {@link FileCipherRecipe} into something that travels <em>with</em> the ciphertext instead of
 * sitting in a side-channel recipe file, and adds the fields that actually enable crypto-agility:
 * a key identifier/version so a future key rotation does not leave old ciphertexts orphaned, and
 * a key check value so a wrong key is caught before a confusing authentication-tag failure.</p>
 *
 * <p>This model does not perform cryptography and does not decide how {@code alg} is
 * interpreted &mdash; it is a plain data holder. See {@link CryptoEnvelopeCodec} for serialization
 * and validation.</p>
 */
public final class CryptoEnvelope {

    /** Schema version of the envelope format itself (not of the algorithm it describes). */
    public static final String CURRENT_VERSION = "CCE-1";

    private final String envVersion;
    private final String alg;
    private final String kid;
    private final Integer keyVersion;
    private final String kcv;
    private final String createdAt;
    private final String ivNonceHex;
    private final String aadHex;
    private final String ciphertextB64;
    private final Map<String, String> extensions;

    private CryptoEnvelope(Builder builder) {
        this.envVersion = CURRENT_VERSION;
        this.alg = builder.alg;
        this.kid = builder.kid;
        this.keyVersion = builder.keyVersion;
        this.kcv = builder.kcv;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now().toString();
        this.ivNonceHex = builder.ivNonceHex;
        this.aadHex = builder.aadHex;
        this.ciphertextB64 = builder.ciphertextB64;
        this.extensions = builder.extensions.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.extensions));
    }

    public String getEnvVersion() { return envVersion; }
    public String getAlg() { return alg; }
    public String getKid() { return kid; }
    public Integer getKeyVersion() { return keyVersion; }
    public String getKcv() { return kcv; }
    public String getCreatedAt() { return createdAt; }
    public String getIvNonceHex() { return ivNonceHex; }
    public String getAadHex() { return aadHex; }
    public String getCiphertextB64() { return ciphertextB64; }
    public Map<String, String> getExtensions() { return extensions; }

    public static Builder forAlgorithm(String alg) {
        return new Builder(alg);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CryptoEnvelope that)) return false;
        return Objects.equals(envVersion, that.envVersion)
                && Objects.equals(alg, that.alg)
                && Objects.equals(kid, that.kid)
                && Objects.equals(keyVersion, that.keyVersion)
                && Objects.equals(kcv, that.kcv)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(ivNonceHex, that.ivNonceHex)
                && Objects.equals(aadHex, that.aadHex)
                && Objects.equals(ciphertextB64, that.ciphertextB64)
                && Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(envVersion, alg, kid, keyVersion, kcv, createdAt, ivNonceHex, aadHex, ciphertextB64, extensions);
    }

    public static final class Builder {
        private final String alg;
        private String kid;
        private Integer keyVersion;
        private String kcv;
        private String createdAt;
        private String ivNonceHex;
        private String aadHex;
        private String ciphertextB64;
        private final Map<String, String> extensions = new LinkedHashMap<>();

        private Builder(String alg) {
            if (alg == null || alg.isBlank()) {
                throw new IllegalArgumentException("Algorithm is required");
            }
            this.alg = alg;
        }

        public Builder kid(String value) { this.kid = value; return this; }
        public Builder keyVersion(Integer value) { this.keyVersion = value; return this; }
        public Builder kcv(String value) { this.kcv = value; return this; }
        /** Overrides the default (now) creation timestamp — mainly useful for tests. */
        public Builder createdAt(String isoInstant) { this.createdAt = isoInstant; return this; }
        public Builder ivNonceHex(String value) { this.ivNonceHex = value; return this; }
        public Builder aadHex(String value) { this.aadHex = value; return this; }
        public Builder ciphertextB64(String value) { this.ciphertextB64 = value; return this; }
        public Builder ciphertext(byte[] value) {
            this.ciphertextB64 = value == null ? null : java.util.Base64.getEncoder().encodeToString(value);
            return this;
        }
        public Builder extension(String key, String value) {
            if (key != null && value != null) this.extensions.put(key, value);
            return this;
        }

        public CryptoEnvelope build() {
            if (ciphertextB64 == null || ciphertextB64.isBlank()) {
                throw new IllegalArgumentException("Ciphertext is required");
            }
            return new CryptoEnvelope(this);
        }
    }
}
