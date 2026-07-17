package com.cryptoforge.crypto.hsm;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;

public class KeyMaterialFactory {

    public static KeyMaterial fromSecretKey(String id, SecretKey key, KeyExportability exportability, Set<KeyUsage> usages) {
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        int size = key.getEncoded() != null ? key.getEncoded().length * 8 : 0;
        return new KeyMaterial(
                id,
                generateFingerprint(key.getEncoded()),
                KeyType.SYMMETRIC,
                key.getAlgorithm(),
                size,
                KeyFormat.RAW,
                usages,
                exportability,
                key,
                null
        );
    }

    public static KeyMaterial fromPrivateKey(String id, PrivateKey key, KeyExportability exportability, Set<KeyUsage> usages, KeyFormat format) {
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        return new KeyMaterial(
                id,
                generateFingerprint(key.getEncoded()),
                KeyType.ASYMMETRIC_PRIVATE,
                key.getAlgorithm(),
                -1, // size calculation varies by algorithm (RSA modulus, EC curve, etc.)
                format,
                usages,
                exportability,
                key,
                null
        );
    }

    public static KeyMaterial fromPublicKey(String id, PublicKey key, KeyExportability exportability, Set<KeyUsage> usages, KeyFormat format) {
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        return new KeyMaterial(
                id,
                generateFingerprint(key.getEncoded()),
                KeyType.ASYMMETRIC_PUBLIC,
                key.getAlgorithm(),
                -1,
                format,
                usages,
                exportability,
                key,
                null
        );
    }

    public static KeyMaterial fromCertificate(String id, Certificate cert, KeyExportability exportability, Set<KeyUsage> usages) {
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        byte[] encoded = null;
        try {
            encoded = cert.getEncoded();
        } catch (Exception ignored) {}

        return new KeyMaterial(
                id,
                generateFingerprint(encoded),
                KeyType.CERTIFICATE,
                cert.getType(),
                -1,
                KeyFormat.DER,
                usages,
                exportability,
                cert.getPublicKey(),
                cert
        );
    }

    private static String generateFingerprint(byte[] data) {
        if (data == null || data.length == 0) return "unknown";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 16); i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }
}
