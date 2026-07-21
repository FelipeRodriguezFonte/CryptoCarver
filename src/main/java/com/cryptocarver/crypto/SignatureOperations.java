package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.security.spec.*;

/**
 * Digital signature operations (Ed25519, RSA with various hashes, ECDSA)
 *
 * @author Felipe
 */
public class SignatureOperations {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Supported signature algorithms
     */
    public static final String[] SUPPORTED_ALGORITHMS = {
        "Ed25519",
        // RSA PKCS#1 v1.5 (classic, widely compatible)
        "RSA-SHA1-PKCS1",
        "RSA-SHA256-PKCS1",
        "RSA-SHA512-PKCS1",
        // RSA-PSS (modern, more secure - probabilistic)
        "RSA-SHA256-PSS",
        "RSA-SHA384-PSS",
        "RSA-SHA512-PSS",
        // ECDSA
        "ECDSA-SHA256",
        "ECDSA-SHA384",
        "ECDSA-SHA512"
    };

    /**
     * Sign data with private key
     *
     * @param data Data to sign
     * @param privateKey Private key for signing
     * @param algorithm Signature algorithm (e.g., "Ed25519", "RSA-SHA256")
     * @return Digital signature
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey, String algorithm) throws Exception {
        String javaAlgorithm = getJavaAlgorithmName(algorithm);

        Signature signature = Signature.getInstance(javaAlgorithm, BouncyCastleProvider.PROVIDER_NAME);
        signature.initSign(privateKey);
        signature.update(data);

        return signature.sign();
    }

    /**
     * Verify signature with public key
     *
     * @param data Original data
     * @param signatureBytes Signature to verify
     * @param publicKey Public key for verification
     * @param algorithm Signature algorithm
     * @return true if signature is valid
     */
    public static boolean verify(byte[] data, byte[] signatureBytes, PublicKey publicKey, String algorithm)
            throws Exception {
        String javaAlgorithm = getJavaAlgorithmName(algorithm);

        Signature signature = Signature.getInstance(javaAlgorithm, BouncyCastleProvider.PROVIDER_NAME);
        signature.initVerify(publicKey);
        signature.update(data);

        return signature.verify(signatureBytes);
    }

    /**
     * Convert user-friendly algorithm name to Java algorithm name
     */
    private static String getJavaAlgorithmName(String algorithm) {
        switch (algorithm) {
            case "Ed25519":
                return "Ed25519";
            // RSA PKCS#1 v1.5 (classic)
            case "RSA-SHA1-PKCS1":
                return "SHA1withRSA";
            case "RSA-SHA256-PKCS1":
                return "SHA256withRSA";
            case "RSA-SHA512-PKCS1":
                return "SHA512withRSA";
            // RSA-PSS (modern, probabilistic)
            case "RSA-SHA256-PSS":
                return "SHA256withRSAandMGF1";
            case "RSA-SHA384-PSS":
                return "SHA384withRSAandMGF1";
            case "RSA-SHA512-PSS":
                return "SHA512withRSAandMGF1";
            // ECDSA
            case "ECDSA-SHA256":
                return "SHA256withECDSA";
            case "ECDSA-SHA384":
                return "SHA384withECDSA";
            case "ECDSA-SHA512":
                return "SHA512withECDSA";
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }

    /**
     * Get algorithm information
     */
    public static String getAlgorithmInfo(String algorithm) {
        switch (algorithm) {
            case "Ed25519":
                return "Ed25519 - Modern, fast elliptic curve signature (255-bit)";
            // RSA PKCS#1 v1.5
            case "RSA-SHA1-PKCS1":
                return "RSA-PKCS#1 v1.5 with SHA-1 (deprecated, legacy only)";
            case "RSA-SHA256-PKCS1":
                return "RSA-PKCS#1 v1.5 with SHA-256 (classic, widely compatible)";
            case "RSA-SHA512-PKCS1":
                return "RSA-PKCS#1 v1.5 with SHA-512 (classic, high security)";
            // RSA-PSS
            case "RSA-SHA256-PSS":
                return "RSA-PSS with SHA-256 (modern, probabilistic, more secure than PKCS#1)";
            case "RSA-SHA384-PSS":
                return "RSA-PSS with SHA-384 (modern, probabilistic, high security)";
            case "RSA-SHA512-PSS":
                return "RSA-PSS with SHA-512 (modern, probabilistic, maximum security)";
            // ECDSA
            case "ECDSA-SHA256":
                return "ECDSA with SHA-256 - Elliptic curve, smaller signatures";
            case "ECDSA-SHA384":
                return "ECDSA with SHA-384 - Higher security";
            case "ECDSA-SHA512":
                return "ECDSA with SHA-512 - Maximum security";
            default:
                return "Unknown algorithm";
        }
    }

    /**
     * Get expected key type for algorithm
     */
    public static String getExpectedKeyType(String algorithm) {
        if (algorithm.startsWith("RSA")) {
            return "RSA";
        } else if (algorithm.equals("Ed25519")) {
            return "Ed25519";
        } else if (algorithm.startsWith("ECDSA")) {
            return "EC";
        }
        return "Unknown";
    }
}
