package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;

/**
 * Asymmetric encryption/decryption operations using RSA
 */
public class AsymmetricCipher {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Supported RSA key sizes
     */
    public static final List<String> SUPPORTED_KEY_SIZES = Arrays.asList(
            "RSA-1024",
            "RSA-2048",
            "RSA-4096"
    );

    /**
     * Encrypt data using RSA public key
     *
     * @param plaintext Data to encrypt
     * @param publicKey RSA public key
     * @return Encrypted data
     */
    public static byte[] encrypt(byte[] plaintext, PublicKey publicKey) throws Exception {
        return encrypt(plaintext, publicKey, "RSA/ECB/PKCS1Padding");
    }

    public static byte[] encrypt(byte[] plaintext, PublicKey publicKey, String transformation) throws Exception {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("Plaintext cannot be null or empty");
        }

        if (publicKey == null) {
            throw new IllegalArgumentException("Public key cannot be null");
        }

        Cipher cipher = Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypt data using RSA private key
     *
     * @param ciphertext Data to decrypt
     * @param privateKey RSA private key
     * @return Decrypted data
     */
    public static byte[] decrypt(byte[] ciphertext, PrivateKey privateKey) throws Exception {
        return decrypt(ciphertext, privateKey, "RSA/ECB/PKCS1Padding");
    }

    public static byte[] decrypt(byte[] ciphertext, PrivateKey privateKey, String transformation) throws Exception {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("Ciphertext cannot be null or empty");
        }

        if (privateKey == null) {
            throw new IllegalArgumentException("Private key cannot be null");
        }

        Cipher cipher = Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        return cipher.doFinal(ciphertext);
    }

    /**
     * Generate RSA key pair
     *
     * @param keySize Key size (1024, 2048, 4096)
     * @return KeyPair containing public and private keys
     */
    public static KeyPair generateKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(keySize, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Load public key from bytes (X.509 format)
     *
     * @param keyBytes Public key bytes
     * @return PublicKey
     */
    public static PublicKey loadPublicKey(byte[] keyBytes) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /**
     * Load private key from bytes (PKCS#8 format)
     *
     * @param keyBytes Private key bytes
     * @return PrivateKey
     */
    public static PrivateKey loadPrivateKey(byte[] keyBytes) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    /**
     * Get maximum data size for RSA encryption
     *
     * @param keySize RSA key size in bits
     * @return Maximum plaintext size in bytes
     */
    public static int getMaxPlaintextSize(int keySize) {
        // For PKCS1 padding: (keySize / 8) - 11
        return (keySize / 8) - 11;
    }

    /**
     * Parse key size from algorithm string
     *
     * @param algorithm "RSA-2048" etc.
     * @return Key size in bits
     */
    public static int parseKeySize(String algorithm) {
        if (algorithm == null) {
            return 2048; // Default
        }

        if (algorithm.contains("1024")) return 1024;
        if (algorithm.contains("2048")) return 2048;
        if (algorithm.contains("4096")) return 4096;

        return 2048; // Default
    }
}
