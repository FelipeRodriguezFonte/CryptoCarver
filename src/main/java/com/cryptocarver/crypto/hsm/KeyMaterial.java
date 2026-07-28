package com.cryptocarver.crypto.hsm;

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
    private String name;
    private final String origin;
    private final long created;
    private long modified;
    private final String kcv;
    private String status;
    private final boolean hasKeyMaterial;

    // Internal references (can be null depending on what is stored)
    private final Key key;
    private final Certificate certificate;

    public KeyMaterial(String id, String fingerprint, KeyType type, String algorithm, int size, KeyFormat format, Set<KeyUsage> usages, KeyExportability exportability, Key key, Certificate certificate) {
        this(id, fingerprint, type, algorithm, size, format, usages, exportability, key, certificate, id, "simulated", System.currentTimeMillis(), System.currentTimeMillis(), "N/A", "ACTIVE");
    }

    public KeyMaterial(String id, String fingerprint, KeyType type, String algorithm, int size, KeyFormat format, Set<KeyUsage> usages, KeyExportability exportability, Key key, Certificate certificate,
                       String name, String origin, long created, long modified, String kcv, String status) {
        this(id, fingerprint, type, algorithm, size, format, usages, exportability, key, certificate, name, origin, created, modified, kcv, status, key != null);
    }

    public KeyMaterial(String id, String fingerprint, KeyType type, String algorithm, int size, KeyFormat format, Set<KeyUsage> usages, KeyExportability exportability, Key key, Certificate certificate,
                       String name, String origin, long created, long modified, String kcv, String status, boolean hasKeyMaterial) {
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
        this.name = name != null ? name : id;
        this.origin = origin != null ? origin : "simulated";
        this.created = created > 0 ? created : System.currentTimeMillis();
        this.modified = modified > 0 ? modified : this.created;
        this.kcv = kcv != null ? kcv : "N/A";
        this.status = status != null ? status : "ACTIVE";
        this.hasKeyMaterial = hasKeyMaterial;
    }

    public String getId() { return id; }
    public String getFingerprint() { return fingerprint; }
    public KeyType getType() { return type; }
    public String getAlgorithm() { return algorithm; }
    public int getSize() { return size; }
    public KeyFormat getFormat() { return format; }
    public Set<KeyUsage> getUsages() { return usages; }
    public KeyExportability getExportability() { return exportability; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.modified = System.currentTimeMillis(); }

    public String getOrigin() { return origin; }
    public long getCreated() { return created; }
    public long getModified() { return modified; }
    public void setModified(long modified) { this.modified = modified; }

    public String getKcv() { return kcv; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.modified = System.currentTimeMillis(); }

    public boolean hasKeyMaterial() {
        return hasKeyMaterial;
    }

    public Key getKey() {
        return key;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    /** Returns a safe copy of this KeyMaterial without the raw Key references. */
    public KeyMaterial withoutRawKey() {
        return new KeyMaterial(id, fingerprint, type, algorithm, size, format, usages, exportability, null, null, name, origin, created, modified, kcv, status, hasKeyMaterial);
    }
}
