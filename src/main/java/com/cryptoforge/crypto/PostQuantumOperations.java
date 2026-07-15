package com.cryptoforge.crypto;

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
        "Kyber512", "Kyber768", "Kyber1024",
        "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024"
    );

    // Digital Signatures
    public static final List<String> ML_DSA_ALGORITHMS = Arrays.asList(
        "Dilithium2", "Dilithium3", "Dilithium5",
        "ML-DSA-44", "ML-DSA-65", "ML-DSA-87"
    );

    public static final List<String> SLH_DSA_ALGORITHMS = Arrays.asList(
        "SPHINCSPlus-SHA2-128s", "SPHINCSPlus-SHA2-128f", 
        "SPHINCSPlus-SHA2-192s", "SPHINCSPlus-SHA2-192f",
        "SPHINCSPlus-SHA2-256s", "SPHINCSPlus-SHA2-256f",
        "SLH-DSA-SHA2-128s", "SLH-DSA-SHA2-128f"
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
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm, "BCPQC");
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
        Signature sig = Signature.getInstance(algorithm, "BCPQC");
        sig.initSign(privateKey, new SecureRandom());
        sig.update(data);
        return sig.sign();
    }

    /**
     * Verify signature using a public key
     */
    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature, String algorithm) throws Exception {
        Signature sig = Signature.getInstance(algorithm, "BCPQC");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Encapsulates a fresh 256-bit shared secret for an ML-KEM/Kyber public key.
     */
    public static KEMResult encapsulate(PublicKey publicKey) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("Kyber", "BCPQC");
        keyGenerator.init(new KEMGenerateSpec(publicKey, "AES", 256), new SecureRandom());
        SecretKeyWithEncapsulation secret = (SecretKeyWithEncapsulation) keyGenerator.generateKey();
        return new KEMResult(secret.getEncoded(), secret.getEncapsulation());
    }

    /**
     * Recovers the shared secret from an ML-KEM/Kyber encapsulation.
     */
    public static byte[] decapsulate(PrivateKey privateKey, byte[] encapsulation) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("Kyber", "BCPQC");
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

    /**
     * Compares parameter sets rather than just PQC families. Bouncy Castle and
     * NIST names coexist (for example Kyber512 and ML-KEM-512), so equivalent
     * aliases are accepted while a 512/768 or 44/65 mismatch is rejected.
     */
    public static boolean areAlgorithmsCompatible(String requested, String keyAlgorithm) {
        if (requested == null || keyAlgorithm == null) return false;
        String expected = canonicalParameterSet(requested);
        String actual = canonicalParameterSet(keyAlgorithm);
        if (expected.equals(actual)) return true;
        // Some providers return only the family name in Key#getAlgorithm().
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

    private static boolean isGenericFamilyName(String value) { return "KYBER".equals(value) || "DILITHIUM".equals(value) || "SPHINCSPLUS".equals(value) || "SLHDSA".equals(value); }
    private static String family(String canonical) {
        if (canonical.startsWith("MLKEM")) return "KYBER";
        if (canonical.startsWith("MLDSA")) return "DILITHIUM";
        if (canonical.startsWith("SPHINCSPLUS")) return "SPHINCSPLUS";
        if (canonical.startsWith("SLHDSA")) return "SLHDSA";
        return canonical;
    }

    private static String normalizeKeyFactoryAlgorithm(String algorithm) {
        if (algorithm.startsWith("ML-KEM")) return "Kyber";
        if (algorithm.startsWith("ML-DSA")) return "Dilithium";
        if (algorithm.startsWith("SLH-DSA")) return "SPHINCSPlus";
        return algorithm;
    }

    public record KEMResult(byte[] sharedSecret, byte[] encapsulation) { }

    // ============================================================================ 
    // UTILS
    // ============================================================================ 
    
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
