package com.cryptoforge.crypto.hsm;

import java.security.Key;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;

public class SimulatedHsmProvider {
    private static final SimulatedHsmProvider INSTANCE = new SimulatedHsmProvider();

    private final Map<String, KeyMaterial> keyStore = new ConcurrentHashMap<>();

    private SimulatedHsmProvider() { }

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
    }

    public KeyMaterial getKeyMetadata(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) return null;
        if (km.getExportability() == KeyExportability.NON_EXPORTABLE) {
            return km.withoutRawKey();
        }
        return km;
    }

    public Set<String> listKeyIds(KeyUsage... requiredUsages) {
        if (requiredUsages == null || requiredUsages.length == 0) {
            return Collections.unmodifiableSet(keyStore.keySet());
        }
        return keyStore.entrySet().stream()
                .filter(e -> {
                    for (KeyUsage u : requiredUsages) {
                        if (e.getValue().getUsages().contains(u)) return true;
                    }
                    return false;
                })
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    public Key exportKey(String id) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) throw new IllegalArgumentException("Key not found in HSM: " + id);
        if (km.getExportability() == KeyExportability.NON_EXPORTABLE) {
            throw new UnsupportedOperationException("Cannot export NON_EXPORTABLE key: " + id);
        }
        return km.getKey();
    }

    // internal usage
    private Key getUsableKey(String id, KeyUsage requiredUsage) {
        KeyMaterial km = keyStore.get(id);
        if (km == null) throw new IllegalArgumentException("Key not found in HSM: " + id);
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
            } else if (k instanceof SecretKey) {
                // Best effort zeroization of known raw bytes if we could intercept,
                // but standard SecretKeySpec doesn't expose byte modification safely.
            }
        }
        keyStore.clear();
    }

    // ============================================================
    // CRYPTO OPERATIONS
    // ============================================================

    private byte[] getBytes(Key key) {
        return key.getEncoded();
    }

    public byte[] encryptSymmetric(String keyId, byte[] plaintext, String algorithm, String mode, String padding, byte[] iv, byte[] aad) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.encrypt(plaintext, getBytes(key), algorithm, mode, padding, iv, aad);
    }

    public byte[] encryptSymmetric(String keyId, byte[] plaintext, String algorithm, String mode, String padding, byte[] iv) throws Exception {
        return encryptSymmetric(keyId, plaintext, algorithm, mode, padding, iv, null);
    }

    public byte[] decryptSymmetric(String keyId, byte[] ciphertext, String algorithm, String mode, String padding, byte[] iv, byte[] aad) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.decrypt(ciphertext, getBytes(key), algorithm, mode, padding, iv, aad);
    }

    public byte[] decryptSymmetric(String keyId, byte[] ciphertext, String algorithm, String mode, String padding, byte[] iv) throws Exception {
        return decryptSymmetric(keyId, ciphertext, algorithm, mode, padding, iv, null);
    }

    public byte[] encryptChaCha20(String keyId, byte[] plaintext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.encryptChaCha20(plaintext, getBytes(key), iv);
    }

    public byte[] decryptChaCha20(String keyId, byte[] ciphertext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.decryptChaCha20(ciphertext, getBytes(key), iv);
    }

    public byte[] encryptChaCha20Poly1305(String keyId, byte[] plaintext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.encryptChaCha20Poly1305(plaintext, getBytes(key), iv);
    }

    public byte[] decryptChaCha20Poly1305(String keyId, byte[] ciphertext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.decryptChaCha20Poly1305(ciphertext, getBytes(key), iv);
    }

    public byte[] encryptXChaCha20Poly1305(String keyId, byte[] plaintext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.ENCRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.encryptXChaCha20Poly1305(plaintext, getBytes(key), iv);
    }

    public byte[] decryptXChaCha20Poly1305(String keyId, byte[] ciphertext, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.DECRYPT);
        return com.cryptoforge.crypto.SymmetricCipher.decryptXChaCha20Poly1305(ciphertext, getBytes(key), iv);
    }

    public byte[] generateMac(String keyId, byte[] data, String algorithm) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.MAC);
        return com.cryptoforge.crypto.MACOperations.generate(data, getBytes(key), algorithm);
    }

    public byte[] generateGmac(String keyId, byte[] data, byte[] iv) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.MAC);
        return com.cryptoforge.crypto.MACOperations.generateGmac(data, getBytes(key), iv);
    }

    public byte[] generatePoly1305(String keyId, byte[] data) throws Exception {
        Key key = getUsableKey(keyId, KeyUsage.MAC);
        return com.cryptoforge.crypto.MACOperations.generatePoly1305(data, getBytes(key));
    }
}
