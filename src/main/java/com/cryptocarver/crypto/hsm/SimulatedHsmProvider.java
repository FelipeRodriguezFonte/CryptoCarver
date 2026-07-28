package com.cryptocarver.crypto.hsm;

import java.security.Key;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;

public class SimulatedHsmProvider {
    private static final SimulatedHsmProvider INSTANCE = new SimulatedHsmProvider();

    private final Map<String, KeyMaterial> keyStore = new ConcurrentHashMap<>();
    private java.nio.file.Path testPathOverride = null;

    private SimulatedHsmProvider() {
        load();
    }

    public static SimulatedHsmProvider getInstance() {
        return INSTANCE;
    }

    public void importKey(KeyMaterial keyMaterial) {
        importKey(keyMaterial, false);
    }

    public void importKey(KeyMaterial keyMaterial, boolean replace) {
        if (keyMaterial == null) throw new IllegalArgumentException("KeyMaterial cannot be null");
        if (!replace && keyStore.containsKey(keyMaterial.getId())) {
            throw new IllegalArgumentException("Key ID already exists in HSM: " + keyMaterial.getId());
        }
        keyStore.put(keyMaterial.getId(), keyMaterial);
        save();
    }

    public KeyMaterial getKeyMetadata(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) return null;
        return km.withoutRawKey();
    }

    public byte[] revealExportableKeyForFullLab(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) {
            throw new IllegalArgumentException("Key not found in HSM: " + id);
        }
        if (km.getExportability() == KeyExportability.NON_EXPORTABLE) {
            throw new SecurityException("Cannot reveal NON_EXPORTABLE key material");
        }
        if (com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            throw new SecurityException("Revealing key material is only allowed in FULL_LAB visibility profile");
        }
        Key key = km.getKey();
        if (key == null) {
            return null;
        }
        return key.getEncoded();
    }

    public synchronized void updateKeyMetadata(String id, String name, String status) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) {
            throw new IllegalArgumentException("Key not found in HSM: " + id);
        }
        KeyMaterial updated = new KeyMaterial(
            km.getId(),
            km.getFingerprint(),
            km.getType(),
            km.getAlgorithm(),
            km.getSize(),
            km.getFormat(),
            km.getUsages(),
            km.getExportability(),
            km.getKey(),
            km.getCertificate(),
            name,
            km.getOrigin(),
            km.getCreated(),
            System.currentTimeMillis(),
            km.getKcv(),
            status,
            km.hasKeyMaterial()
        );
        keyStore.put(id, updated);
        save();
    }

    public void deleteKey(String id) {
        if (id == null) return;
        keyStore.remove(id);
        save();
    }

    public void archiveKey(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km != null) {
            km.setStatus("ARCHIVED".equalsIgnoreCase(km.getStatus()) ? "ACTIVE" : "ARCHIVED");
            save();
        }
    }

    public KeyMaterial findKeyByFingerprint(String fingerprint) {
        if (fingerprint == null) return null;
        for (KeyMaterial km : keyStore.values()) {
            if (fingerprint.equalsIgnoreCase(km.getFingerprint())) {
                return km;
            }
        }
        return null;
    }

    public Set<String> listKeyIds(KeyUsage... requiredUsages) {
        return listKeyIds(false, requiredUsages);
    }

    public Set<String> listKeyIds(boolean includeArchived, KeyUsage... requiredUsages) {
        java.util.stream.Stream<Map.Entry<String, KeyMaterial>> stream = keyStore.entrySet().stream();
        if (!includeArchived) {
            stream = stream.filter(e -> !"ARCHIVED".equalsIgnoreCase(e.getValue().getStatus()));
        }
        if (requiredUsages == null || requiredUsages.length == 0) {
            return stream.map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        }
        return stream.filter(e -> {
            for (KeyUsage u : requiredUsages) {
                if (e.getValue().getUsages().contains(u)) return true;
            }
            return false;
        }).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
    }

    public Key exportKey(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) throw new IllegalArgumentException("Key not found in HSM: " + id);
        if (km.getKey() == null) {
            throw new IllegalStateException("Key material is not available (only metadata/reference is loaded). Please re-import or regenerate the key bytes.");
        }
        if (km.getExportability() == KeyExportability.NON_EXPORTABLE) {
            throw new UnsupportedOperationException("Cannot export NON_EXPORTABLE key: " + id);
        }
        return km.getKey();
    }

    // internal usage
    private Key getUsableKey(String id, KeyUsage requiredUsage) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) throw new IllegalArgumentException("Key not found in HSM: " + id);
        if (km.getKey() == null) {
            throw new IllegalStateException("Key material is not available (only metadata/reference is loaded). Please re-import or regenerate the key bytes.");
        }
        if (!km.getUsages().contains(requiredUsage)) {
            throw new IllegalArgumentException("Key " + id + " does not support usage: " + requiredUsage);
        }
        return km.getKey();
    }

    public void clear() {
        for (KeyMaterial km : keyStore.values()) {
            Key k = km.getKey();
            if (k != null && k instanceof javax.security.auth.Destroyable) {
                try {
                    ((javax.security.auth.Destroyable) k).destroy();
                } catch (Exception ignored) { }
            }
        }
        keyStore.clear();
        save();
    }

    public synchronized void resetForTest(java.nio.file.Path tempPath) {
        keyStore.clear();
        this.testPathOverride = tempPath;
        load();
    }

    public void exportMetadata(java.io.File dest) throws Exception {
        java.util.List<KeyLabEntry> entries = keyStore.values().stream()
                .map(SimulatedHsmProvider::toEntry)
                .toList();
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(entries);
        java.nio.file.Files.writeString(dest.toPath(), json);
    }

    public void importMetadata(java.io.File src) throws Exception {
        String json = java.nio.file.Files.readString(src.toPath());
        KeyLabEntry[] entries = new com.google.gson.Gson().fromJson(json, KeyLabEntry[].class);
        if (entries != null) {
            boolean changed = false;
            for (KeyLabEntry entry : entries) {
                if (!keyStore.containsKey(entry.id)) {
                    KeyMaterial km = fromEntry(entry);
                    keyStore.put(km.getId(), km);
                    changed = true;
                }
            }
            if (changed) {
                save();
            }
        }
    }

    private static java.nio.file.Path defaultKeyLabFile() {
        if (INSTANCE != null && INSTANCE.testPathOverride != null) {
            return INSTANCE.testPathOverride;
        }
        String customPath = System.getProperty("cryptocarver.keylab.path");
        if (customPath != null && !customPath.isEmpty()) {
            return java.nio.file.Paths.get(customPath);
        }
        String home = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
        return java.nio.file.Paths.get(home, ".cryptocarver", "key_lab.json");
    }

    private synchronized void load() {
        java.nio.file.Path file = defaultKeyLabFile();
        try {
            if (java.nio.file.Files.exists(file)) {
                String json = java.nio.file.Files.readString(file);
                KeyLabEntry[] entries = new com.google.gson.Gson().fromJson(json, KeyLabEntry[].class);
                if (entries != null) {
                    for (KeyLabEntry entry : entries) {
                        try {
                            KeyMaterial km = fromEntry(entry);
                            keyStore.put(km.getId(), km);
                        } catch (Exception e) {
                            System.err.println("Error loading Key Lab entry: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading Simulated HSM key lab database: " + e.getMessage());
        }
    }

    private synchronized void save() {
        java.nio.file.Path file = defaultKeyLabFile();
        java.nio.file.Path tempFile = null;
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            tempFile = file.resolveSibling(file.getFileName().toString() + "." + java.util.UUID.randomUUID().toString() + ".tmp");

            java.util.List<KeyLabEntry> entries = keyStore.values().stream()
                    .map(SimulatedHsmProvider::toEntry)
                    .toList();
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(entries);

            java.nio.file.Files.writeString(tempFile, json);

            // Try setting POSIX file permissions
            try {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
                java.nio.file.Files.setPosixFilePermissions(tempFile, perms);
            } catch (UnsupportedOperationException ignored) {
                java.io.File f = tempFile.toFile();
                f.setReadable(true, true);
                f.setWritable(true, true);
            }

            try {
                java.nio.file.Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException e) {
                java.nio.file.Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("Error saving Simulated HSM key lab database: " + e.getMessage());
            if (tempFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }

    private static KeyLabEntry toEntry(KeyMaterial km) {
        KeyLabEntry entry = new KeyLabEntry();
        entry.id = km.getId();
        entry.fingerprint = km.getFingerprint();
        entry.type = km.getType().name();
        entry.algorithm = km.getAlgorithm();
        entry.size = km.getSize();
        entry.format = km.getFormat().name();
        entry.usages = new java.util.HashSet<>();
        if (km.getUsages() != null) {
            for (KeyUsage u : km.getUsages()) {
                entry.usages.add(u.name());
            }
        }
        entry.exportability = km.getExportability().name();
        entry.name = km.getName();
        entry.origin = km.getOrigin();
        entry.created = km.getCreated();
        entry.modified = km.getModified();
        entry.kcv = km.getKcv();
        entry.status = km.getStatus();

        // NO keyMaterialHex or certificateHex inside metadata database
        return entry;
    }

    private static KeyMaterial fromEntry(KeyLabEntry entry) {
        KeyType type = KeyType.valueOf(entry.type);
        KeyFormat format = KeyFormat.valueOf(entry.format);
        KeyExportability exportability = KeyExportability.valueOf(entry.exportability);

        Set<KeyUsage> usages = new java.util.HashSet<>();
        if (entry.usages != null) {
            for (String u : entry.usages) {
                usages.add(KeyUsage.valueOf(u));
            }
        }

        return new KeyMaterial(
                entry.id,
                entry.fingerprint,
                type,
                entry.algorithm,
                entry.size,
                format,
                usages,
                exportability,
                null,
                null,
                entry.name,
                entry.origin,
                entry.created,
                entry.modified,
                entry.kcv,
                entry.status,
                false
        );
    }

    private static class KeyLabEntry {
        String id;
        String fingerprint;
        String type;
        String algorithm;
        int size;
        String format;
        Set<String> usages;
        String exportability;
        String name;
        String origin;
        long created;
        long modified;
        String kcv;
        String status;
    }

    // ============================================================
    // CRYPTO OPERATIONS
    // ============================================================

    private byte[] getBytes(Key key) {
        return key.getEncoded();
    }

    public byte[] encryptSymmetric(String keyId, byte[] plaintext, String algorithm, String mode, String padding, byte[] iv, byte[] aad) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.encrypt(plaintext, getBytes(key), algorithm, mode, padding, iv, aad);
    }

    public byte[] encryptSymmetric(String keyId, byte[] plaintext, String algorithm, String mode, String padding, byte[] iv) throws Exception {
        return encryptSymmetric(keyId, plaintext, algorithm, mode, padding, iv, null);
    }

    public byte[] decryptSymmetric(String keyId, byte[] ciphertext, String algorithm, String mode, String padding, byte[] iv, byte[] aad) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.decrypt(ciphertext, getBytes(key), algorithm, mode, padding, iv, aad);
    }

    public byte[] decryptSymmetric(String keyId, byte[] ciphertext, String algorithm, String mode, String padding, byte[] iv) throws Exception {
        return decryptSymmetric(keyId, ciphertext, algorithm, mode, padding, iv, null);
    }

    public byte[] encryptChaCha20(String keyId, byte[] plaintext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.encryptChaCha20(plaintext, getBytes(key), iv);
    }

    public byte[] decryptChaCha20(String keyId, byte[] ciphertext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.decryptChaCha20(ciphertext, getBytes(key), iv);
    }

    public byte[] encryptChaCha20Poly1305(String keyId, byte[] plaintext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.encryptChaCha20Poly1305(plaintext, getBytes(key), iv);
    }

    public byte[] decryptChaCha20Poly1305(String keyId, byte[] ciphertext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.decryptChaCha20Poly1305(ciphertext, getBytes(key), iv);
    }

    public byte[] encryptXChaCha20Poly1305(String keyId, byte[] plaintext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.encryptXChaCha20Poly1305(plaintext, getBytes(key), iv);
    }

    public byte[] decryptXChaCha20Poly1305(String keyId, byte[] ciphertext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptocarver.crypto.SymmetricCipher.decryptXChaCha20Poly1305(ciphertext, getBytes(key), iv);
    }

    public byte[] generateMac(String keyId, byte[] data, String algorithm) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.MAC);
        return com.cryptocarver.crypto.MACOperations.generate(data, getBytes(key), algorithm);
    }

    public byte[] generateGmac(String keyId, byte[] data, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.MAC);
        return com.cryptocarver.crypto.MACOperations.generateGmac(data, getBytes(key), iv);
    }

    public byte[] generatePoly1305(String keyId, byte[] data) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.MAC);
        return com.cryptocarver.crypto.MACOperations.generatePoly1305(data, getBytes(key));
    }
}
