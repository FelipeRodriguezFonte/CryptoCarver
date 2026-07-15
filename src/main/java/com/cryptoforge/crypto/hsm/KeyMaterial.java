package com.cryptoforge.crypto.hsm;

import java.security.Key;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class KeyMaterial {
    private final String id;
    private final String fingerprint;
    private final KeyType type;
    private final String algorithm;
    private final int size;
    private final KeyFormat format;
    private final Set<KeyUsage> usages;
    private final KeyExportability exportability;
    
    // Internal references (can be null depending on what is stored)
    private final Key key;
    private final Certificate certificate;

    public KeyMaterial(String id, String fingerprint, KeyType type, String algorithm, int size, KeyFormat format, Set<KeyUsage> usages, KeyExportability exportability, Key key, Certificate certificate) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.fingerprint = fingerprint != null ? fingerprint : "unknown";
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.algorithm = Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        this.size = size;
        this.format = Objects.requireNonNull(format, "Format cannot be null");
        this.usages = Collections.unmodifiableSet(new HashSet<>(usages));
        this.exportability = Objects.requireNonNull(exportability, "Exportability cannot be null");
        this.key = key;
        this.certificate = certificate;
    }

    public String getId() { return id; }
    public String getFingerprint() { return fingerprint; }
    public KeyType getType() { return type; }
    public String getAlgorithm() { return algorithm; }
    public int getSize() { return size; }
    public KeyFormat getFormat() { return format; }
    public Set<KeyUsage> getUsages() { return usages; }
    public KeyExportability getExportability() { return exportability; }

    public Key getKey() {
        return key;
    }

    public Certificate getCertificate() {
        return certificate;
    }
    
    /** Returns a safe copy of this KeyMaterial without the raw Key references. */
    public KeyMaterial withoutRawKey() {
        return new KeyMaterial(id, fingerprint, type, algorithm, size, format, usages, exportability, null, null);
    }
}
