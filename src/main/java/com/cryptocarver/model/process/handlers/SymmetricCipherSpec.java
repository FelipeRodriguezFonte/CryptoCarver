package com.cryptocarver.model.process.handlers;

import java.util.Set;

public enum SymmetricCipherSpec {
    AES_GCM(
        "AES/GCM/NoPadding", "AES", "GCM", "NoPadding",
        12, true, Set.of(16, 24, 32), "Modern AEAD",
        "Nonce: 12 bytes / 96 bits\nAAD: optional input port\nAuthenticated encryption: yes", true
    ),
    AES_CBC(
        "AES/CBC/PKCS7Padding", "AES", "CBC", "PKCS7Padding",
        16, false, Set.of(16, 24, 32), "AES block modes",
        "IV: 16 bytes / AES block size\nAAD: not supported\nAuthenticated encryption: no", true
    ),
    AES_CTR(
        "AES/CTR/NoPadding", "AES", "CTR", "NoPadding",
        16, false, Set.of(16, 24, 32), "AES block modes",
        "IV: 16 bytes / AES block size\nAAD: not supported\nAuthenticated encryption: no", true
    ),
    AES_CFB(
        "AES/CFB/NoPadding", "AES", "CFB", "NoPadding",
        16, false, Set.of(16, 24, 32), "AES block modes",
        "IV: 16 bytes / AES block size\nAAD: not supported\nAuthenticated encryption: no", false
    ),
    AES_OFB(
        "AES/OFB/NoPadding", "AES", "OFB", "NoPadding",
        16, false, Set.of(16, 24, 32), "AES block modes",
        "IV: 16 bytes / AES block size\nAAD: not supported\nAuthenticated encryption: no", false
    ),
    AES_ECB(
        "AES/ECB/PKCS7Padding", "AES", "ECB", "PKCS7Padding",
        0, false, Set.of(16, 24, 32), "Laboratory / insecure",
        "IV: not used\nAAD: not supported\nAuthenticated encryption: no\nWARNING: ECB mode is insecure for general use.", false
    ),
    CHACHA20_POLY1305(
        "ChaCha20-Poly1305", "ChaCha20-Poly1305", "POLY1305", "NoPadding",
        12, true, Set.of(32), "Modern AEAD",
        "Nonce: 12 bytes / 96 bits\nAAD: optional input port\nAuthenticated encryption: yes\nKey: 32 bytes / 256 bits", true
    ),
    TDES_CBC(
        "3DES/CBC/PKCS7Padding", "3DES", "CBC", "PKCS7Padding",
        8, false, Set.of(24), "Legacy / laboratory",
        "IV: 8 bytes / DES block size\nAAD: not supported\nAuthenticated encryption: no\nWARNING: 3DES is legacy; prefer AES-GCM or ChaCha20-Poly1305.", true
    ),
    TDES_CBC_NO_PADDING(
        "3DES/CBC/NoPadding", "3DES", "CBC", "NoPadding",
        8, false, Set.of(24), "Legacy / laboratory",
        "IV: 8 bytes / DES block size\nPadding: none (payload must be a multiple of 8 bytes)\nAAD: not supported\nAuthenticated encryption: no\nWARNING: 3DES is legacy; prefer AES-GCM or ChaCha20-Poly1305.", true
    );

    public final String algorithm;
    public final String cipherType;
    public final String mode;
    public final String padding;
    public final int ivLength;
    public final boolean aead;
    public final Set<Integer> acceptedKeySizes;
    public final String category;
    public final String helpText;
    public final boolean supportsEnvelope;

    SymmetricCipherSpec(String algorithm, String cipherType, String mode, String padding, int ivLength, boolean aead, Set<Integer> acceptedKeySizes, String category, String helpText, boolean supportsEnvelope) {
        this.algorithm = algorithm;
        this.cipherType = cipherType;
        this.mode = mode;
        this.padding = padding;
        this.ivLength = ivLength;
        this.aead = aead;
        this.acceptedKeySizes = acceptedKeySizes;
        this.category = category;
        this.helpText = helpText;
        this.supportsEnvelope = supportsEnvelope;
    }

    public static SymmetricCipherSpec fromAlgorithm(String algorithm) {
        for (SymmetricCipherSpec spec : values()) {
            if (spec.algorithm.equals(algorithm) ||
               // Map PKCS5 to PKCS7 to support both string forms consistently as they are equivalent in JCE
               (algorithm.endsWith("PKCS5Padding") && spec.algorithm.endsWith("PKCS7Padding") && spec.algorithm.substring(0, spec.algorithm.indexOf("PKCS")).equals(algorithm.substring(0, algorithm.indexOf("PKCS"))))) {
                return spec;
            }
        }
        throw new IllegalArgumentException("Unsupported encryption algorithm: " + algorithm);
    }
}
