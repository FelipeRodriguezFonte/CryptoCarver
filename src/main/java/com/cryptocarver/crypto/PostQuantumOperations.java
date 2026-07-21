package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;

import javax.crypto.KeyGenerator;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

/**
 * Post-Quantum Cryptography Operations
 * Uses Bouncy Castle for NIST PQC algorithms (ML-KEM, ML-DSA, SLH-DSA)
 */
public class PostQuantumOperations {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    // ============================================================================
    // ALGORITHMS
    // ============================================================================

    // Key Encapsulation Mechanism (KEM)
    public static final List<String> ML_KEM_ALGORITHMS = Arrays.asList(
        "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024"
    );

    // Digital Signatures
    public static final List<String> ML_DSA_ALGORITHMS = Arrays.asList(
        "ML-DSA-44", "ML-DSA-65", "ML-DSA-87"
    );

    public static final List<String> SLH_DSA_ALGORITHMS = Arrays.asList(
        "SLH-DSA-SHA2-128s", "SLH-DSA-SHA2-128f",
        "SLH-DSA-SHA2-192s", "SLH-DSA-SHA2-192f",
        "SLH-DSA-SHA2-256s", "SLH-DSA-SHA2-256f"
    );

    // ============================================================================
    // KEY GENERATION
    // ============================================================================

    /**
     * Generate PQC Key Pair
     * @param algorithm The algorithm name (e.g., "Kyber512", "Dilithium2")
     * @return KeyPair
     */
    public static KeyPair generateKeyPair(String algorithm) throws Exception {
        String bcName = toBouncyCastleAlias(algorithm);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(bcName, "BCPQC");
        // Use default parameters for the named algorithm
        return kpg.generateKeyPair();
    }

    // ============================================================================
    // SIGNATURE OPERATIONS (ML-DSA, SLH-DSA)
    // ============================================================================

    /**
     * Sign data using a private key
     */
    public static byte[] sign(PrivateKey privateKey, byte[] data, String algorithm) throws Exception {
        PqcAlgorithmDetectionResult det = detectAlgorithmFromEncoded(privateKey.getEncoded(), false);
        if (det == null || !det.isSupported()) {
            throw new IllegalArgumentException("Unknown or unsupported PQC algorithm in key.");
        }
        if (!areAlgorithmsCompatible(algorithm, det.nistName())) {
            throw new IllegalArgumentException("Key mismatch: Key is " + det.nistName() + " but algorithm requested is " + algorithm);
        }
        String bcName = getSignatureAlgorithmName(algorithm);
        Signature sig = Signature.getInstance(bcName, "BCPQC");
        sig.initSign(privateKey, new SecureRandom());
        sig.update(data);
        return sig.sign();
    }

    /**
     * Verify signature using a public key
     */
    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature, String algorithm) throws Exception {
        PqcAlgorithmDetectionResult det = detectAlgorithmFromEncoded(publicKey.getEncoded(), true);
        if (det == null || !det.isSupported()) {
            throw new IllegalArgumentException("Unknown or unsupported PQC algorithm in key.");
        }
        if (!areAlgorithmsCompatible(algorithm, det.nistName())) {
            throw new IllegalArgumentException("Key mismatch: Key is " + det.nistName() + " but algorithm requested is " + algorithm);
        }
        String bcName = getSignatureAlgorithmName(algorithm);
        Signature sig = Signature.getInstance(bcName, "BCPQC");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Encapsulates a fresh 256-bit shared secret for an ML-KEM/Kyber public key.
     */
    public static KEMResult encapsulate(PublicKey publicKey, String algorithm) throws Exception {
        PqcAlgorithmDetectionResult det = detectAlgorithmFromEncoded(publicKey.getEncoded(), true);
        if (det == null || !det.isSupported()) {
            throw new IllegalArgumentException("Unknown or unsupported PQC algorithm in key.");
        }
        if (!areAlgorithmsCompatible(algorithm, det.nistName())) {
            throw new IllegalArgumentException("Key mismatch: Key is " + det.nistName() + " but algorithm requested is " + algorithm);
        }
        String bcName = toBouncyCastleAlias(algorithm);
        KeyGenerator keyGenerator = KeyGenerator.getInstance(bcName, "BCPQC");
        keyGenerator.init(new KEMGenerateSpec(publicKey, "AES", 256), new SecureRandom());
        SecretKeyWithEncapsulation secret = (SecretKeyWithEncapsulation) keyGenerator.generateKey();
        return new KEMResult(secret.getEncoded(), secret.getEncapsulation());
    }

    /**
     * Recovers the shared secret from an ML-KEM/Kyber encapsulation.
     */
    public static byte[] decapsulate(PrivateKey privateKey, byte[] encapsulation, String algorithm) throws Exception {
        PqcAlgorithmDetectionResult det = detectAlgorithmFromEncoded(privateKey.getEncoded(), false);
        if (det == null || !det.isSupported()) {
            throw new IllegalArgumentException("Unknown or unsupported PQC algorithm in key.");
        }
        if (!areAlgorithmsCompatible(algorithm, det.nistName())) {
            throw new IllegalArgumentException("Key mismatch: Key is " + det.nistName() + " but algorithm requested is " + algorithm);
        }
        String bcName = toBouncyCastleAlias(algorithm);
        KeyGenerator keyGenerator = KeyGenerator.getInstance(bcName, "BCPQC");
        keyGenerator.init(new KEMExtractSpec(privateKey, encapsulation, "AES", 256));
        return keyGenerator.generateKey().getEncoded();
    }

    public static PublicKey importPublicKey(String algorithm, byte[] encoded) throws Exception {
        return KeyFactory.getInstance(normalizeKeyFactoryAlgorithm(algorithm), "BCPQC")
                .generatePublic(new X509EncodedKeySpec(encoded));
    }

    public static PrivateKey importPrivateKey(String algorithm, byte[] encoded) throws Exception {
        return KeyFactory.getInstance(normalizeKeyFactoryAlgorithm(algorithm), "BCPQC")
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static boolean areAlgorithmsCompatible(String requested, String keyAlgorithm) {
        if (requested == null || keyAlgorithm == null) return false;
        String expected = canonicalParameterSet(requested);
        String actual = canonicalParameterSet(keyAlgorithm);
        if (expected.equals(actual)) return true;
        return isGenericFamilyName(actual) && family(expected).equals(actual);
    }

    public static String canonicalParameterSet(String algorithm) {
        String normalized = algorithm == null ? "" : algorithm.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return switch (normalized) {
            case "KYBER512", "MLKEM512" -> "MLKEM512";
            case "KYBER768", "MLKEM768" -> "MLKEM768";
            case "KYBER1024", "MLKEM1024" -> "MLKEM1024";
            case "DILITHIUM2", "MLDSA44" -> "MLDSA44";
            case "DILITHIUM3", "MLDSA65" -> "MLDSA65";
            case "DILITHIUM5", "MLDSA87" -> "MLDSA87";
            default -> normalized;
        };
    }

    private static boolean isGenericFamilyName(String value) { return "KYBER".equals(value) || "DILITHIUM".equals(value) || "SPHINCSPLUS".equals(value) || "SLHDSA".equals(value) || "MLKEM".equals(value) || "MLDSA".equals(value); }
    private static String family(String canonical) {
        if (canonical.startsWith("MLKEM")) return "KYBER";
        if (canonical.startsWith("MLDSA")) return "DILITHIUM";
        if (canonical.startsWith("SPHINCSPLUS") || canonical.startsWith("SLHDSA")) return "SPHINCSPLUS";
        return canonical;
    }

    private static String normalizeKeyFactoryAlgorithm(String algorithm) {
        if (algorithm == null) return null;
        if (algorithm.startsWith("ML-KEM")) return "Kyber";
        if (algorithm.startsWith("Kyber")) return "Kyber";
        if (algorithm.startsWith("ML-DSA")) return "Dilithium";
        if (algorithm.startsWith("Dilithium")) return "Dilithium";
        if (algorithm.startsWith("SLH-DSA")) return "SPHINCSPlus";
        if (algorithm.startsWith("SPHINCSPlus")) return "SPHINCSPlus";
        return algorithm;
    }

    private static String toBouncyCastleAlias(String algorithm) {
        if (algorithm == null) return null;
        return switch (algorithm) {
            case "ML-KEM-512" -> "Kyber512";
            case "ML-KEM-768" -> "Kyber768";
            case "ML-KEM-1024" -> "Kyber1024";
            case "ML-DSA-44" -> "Dilithium2";
            case "ML-DSA-65" -> "Dilithium3";
            case "ML-DSA-87" -> "Dilithium5";
            case "SLH-DSA-SHA2-128s" -> "SPHINCS+-SHA2-128S";
            case "SLH-DSA-SHA2-128f" -> "SPHINCS+-SHA2-128F";
            case "SLH-DSA-SHA2-192s" -> "SPHINCS+-SHA2-192S";
            case "SLH-DSA-SHA2-192f" -> "SPHINCS+-SHA2-192F";
            case "SLH-DSA-SHA2-256s" -> "SPHINCS+-SHA2-256S";
            case "SLH-DSA-SHA2-256f" -> "SPHINCS+-SHA2-256F";
            default -> algorithm;
        };
    }

    private static String getSignatureAlgorithmName(String algorithm) {
        String bcAlias = toBouncyCastleAlias(algorithm);
        if (bcAlias != null && bcAlias.startsWith("SPHINCS+-")) {
            return "SPHINCSPlus";
        }
        return bcAlias;
    }

    public record KEMResult(byte[] sharedSecret, byte[] encapsulation) { }

    public record PqcAlgorithmDetectionResult(String originalOid, String nistName, boolean isSupported) {}

    // ============================================================================
    // UTILS
    // ============================================================================

    public static PqcAlgorithmDetectionResult detectAlgorithmFromEncoded(byte[] encoded, boolean isPublic) {
        try {
            ASN1ObjectIdentifier oid;
            if (isPublic) {
                SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(encoded);
                oid = spki.getAlgorithm().getAlgorithm();
            } else {
                PrivateKeyInfo pki = PrivateKeyInfo.getInstance(encoded);
                oid = pki.getPrivateKeyAlgorithm().getAlgorithm();
            }
            String oidStr = oid.getId();

            // ML-KEM OIDs (FIPS 203)
            if ("2.16.840.1.101.3.4.4.1".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-KEM-512", true);
            if ("2.16.840.1.101.3.4.4.2".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-KEM-768", true);
            if ("2.16.840.1.101.3.4.4.3".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-KEM-1024", true);

            // Kyber OIDs (Pre-standard) -> mapped to ML-KEM
            if ("1.3.6.1.4.1.2.267.8.3.3".equals(oidStr) || "1.3.6.1.4.1.22554.5.6.1".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-KEM-512", true);
            if ("1.3.6.1.4.1.2.267.8.4.4".equals(oidStr) || "1.3.6.1.4.1.22554.5.6.2".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-KEM-768", true);
            if ("1.3.6.1.4.1.2.267.8.8.5".equals(oidStr) || "1.3.6.1.4.1.22554.5.6.3".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-KEM-1024", true);

            // ML-DSA OIDs (FIPS 204)
            if ("2.16.840.1.101.3.4.3.17".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-DSA-44", true);
            if ("2.16.840.1.101.3.4.3.18".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-DSA-65", true);
            if ("2.16.840.1.101.3.4.3.19".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-DSA-87", true);

            // Dilithium OIDs (Pre-standard) -> mapped to ML-DSA
            if ("1.3.6.1.4.1.2.267.7.4.4".equals(oidStr) || "1.3.6.1.4.1.2.267.1.6.5".equals(oidStr) || "1.3.6.1.4.1.2.267.12.4.4".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-DSA-44", true);
            if ("1.3.6.1.4.1.2.267.7.6.5".equals(oidStr) || "1.3.6.1.4.1.2.267.1.8.7".equals(oidStr) || "1.3.6.1.4.1.2.267.12.6.5".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-DSA-65", true);
            if ("1.3.6.1.4.1.2.267.7.8.7".equals(oidStr) || "1.3.6.1.4.1.2.267.1.11.8".equals(oidStr) || "1.3.6.1.4.1.2.267.12.8.7".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "ML-DSA-87", true);

            // SLH-DSA OIDs
            if ("2.16.840.1.101.3.4.3.20".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-128s", true);
            if ("2.16.840.1.101.3.4.3.21".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-128f", true);
            if ("2.16.840.1.101.3.4.3.22".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-192s", true);
            if ("2.16.840.1.101.3.4.3.23".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-192f", true);
            if ("2.16.840.1.101.3.4.3.24".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-256s", true);
            if ("2.16.840.1.101.3.4.3.25".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-256f", true);

            // SPHINCS+ OIDs (Pre-standard) -> mapped to SLH-DSA
            if ("1.3.9999.6.4.16".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-128s", true);
            if ("1.3.9999.6.4.13".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-128f", true);
            if ("1.3.9999.6.5.12".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-192s", true);
            if ("1.3.9999.6.5.10".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-192f", true);
            if ("1.3.9999.6.6.12".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-256s", true);
            if ("1.3.9999.6.6.10".equals(oidStr)) return new PqcAlgorithmDetectionResult(oidStr, "SLH-DSA-SHA2-256f", true);

            return new PqcAlgorithmDetectionResult(oidStr, oidStr, false);
        } catch (Exception e) {
            return new PqcAlgorithmDetectionResult("unknown", "unknown", false);
        }
    }

    public static String getKeyInfo(Key key) {
        StringBuilder sb = new StringBuilder();
        sb.append("Algorithm: ").append(key.getAlgorithm()).append("\n");
        sb.append("Format: ").append(key.getFormat()).append("\n");
        // PQC keys often just return "RAW" or "PKCS#8"/"X.509" encodings.
        // Specific parameter details might be hard to parse without specific BC classes.
        try {
            sb.append("Encoded Length: ").append(key.getEncoded().length).append(" bytes\n");
        } catch (Exception e) {
            sb.append("Encoded Length: Unknown\n");
        }
        return sb.toString();
    }
}
