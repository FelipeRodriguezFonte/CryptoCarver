package com.cryptoforge.crypto;

import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.macs.HMac;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Key Derivation Functions (KDF) - HKDF, PBKDF2, SCrypt, Argon2
 *
 * @author Felipe
 */
public class KeyDerivation {

    /**
     * HKDF (HMAC-based Key Derivation Function) - RFC 5869
     * Used in TLS 1.3, Signal Protocol, etc.
     *
     * @param ikm Input Key Material
     * @param salt Salt value (can be null for no salt)
     * @param info Context and application specific information (can be null)
     * @param outputLength Desired output length in bytes
     * @param digest Digest to use (SHA1, SHA256, or SHA512)
     * @return Derived key
     */
    public static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int outputLength, Digest digest) {
        if (ikm == null) throw new IllegalArgumentException("IKM cannot be null");
        if (outputLength < 0 || outputLength > 255 * digest.getDigestSize()) {
            throw new IllegalArgumentException("Invalid HKDF output length");
        }
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(digest);

        // If no salt provided, HKDF spec says to use zeros
        if (salt == null || salt.length == 0) {
            salt = new byte[digest.getDigestSize()];
        }

        // If no info provided, use empty
        if (info == null) {
            info = new byte[0];
        }

        hkdf.init(new HKDFParameters(ikm, salt, info));

        byte[] output = new byte[outputLength];
        hkdf.generateBytes(output, 0, outputLength);

        return output;
    }

    /**
     * NIST SP 800-108 Counter Mode KDF using HMAC as the PRF.
     * The fixed input is {@code label || 0x00 || context || [L]32}.
     */
    public static byte[] sp800108Counter(byte[] key, byte[] label, byte[] context,
                                          int outputLength, Digest digest) {
        if (key == null || key.length == 0) throw new IllegalArgumentException("KDF key cannot be empty");
        if (outputLength < 0 || (long) outputLength * 8 > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Invalid SP 800-108 output length");
        }
        byte[] actualLabel = label == null ? new byte[0] : label;
        byte[] actualContext = context == null ? new byte[0] : context;
        byte[] lengthBits = intToBytes(outputLength * 8);
        HMac mac = new HMac(digest);
        mac.init(new KeyParameter(key));
        byte[] output = new byte[outputLength];
        int written = 0;
        for (int counter = 1; written < outputLength; counter++) {
            mac.reset();
            mac.update(intToBytes(counter), 0, 4);
            mac.update(actualLabel, 0, actualLabel.length);
            mac.update((byte) 0);
            mac.update(actualContext, 0, actualContext.length);
            mac.update(lengthBits, 0, lengthBits.length);
            byte[] block = new byte[mac.getMacSize()];
            mac.doFinal(block, 0);
            int copy = Math.min(block.length, outputLength - written);
            System.arraycopy(block, 0, output, written, copy);
            written += copy;
        }
        return output;
    }

    /** ANSI X9.63 / concatenation KDF: Hash(shared secret || counter || shared info). */
    public static byte[] x963(byte[] sharedSecret, byte[] sharedInfo, int outputLength, Digest digest) {
        if (sharedSecret == null || sharedSecret.length == 0) throw new IllegalArgumentException("Shared secret cannot be empty");
        if (outputLength < 0 || (long) outputLength > 0xFFFFFFFFL * digest.getDigestSize()) {
            throw new IllegalArgumentException("Invalid X9.63 output length");
        }
        byte[] info = sharedInfo == null ? new byte[0] : sharedInfo;
        byte[] output = new byte[outputLength];
        int written = 0;
        for (int counter = 1; written < outputLength; counter++) {
            digest.reset();
            digest.update(sharedSecret, 0, sharedSecret.length);
            byte[] counterBytes = intToBytes(counter);
            digest.update(counterBytes, 0, counterBytes.length);
            digest.update(info, 0, info.length);
            byte[] block = new byte[digest.getDigestSize()];
            digest.doFinal(block, 0);
            int copy = Math.min(block.length, outputLength - written);
            System.arraycopy(block, 0, output, written, copy);
            written += copy;
        }
        return output;
    }

    /** RFC 5869 Extract step, returning a pseudorandom key (PRK). */
    public static byte[] hkdfExtract(byte[] ikm, byte[] salt, Digest digest) {
        if (ikm == null) throw new IllegalArgumentException("IKM cannot be null");
        byte[] actualSalt = (salt == null || salt.length == 0) ? new byte[digest.getDigestSize()] : salt;
        HMac mac = new HMac(digest);
        mac.init(new KeyParameter(actualSalt));
        mac.update(ikm, 0, ikm.length);
        byte[] prk = new byte[mac.getMacSize()];
        mac.doFinal(prk, 0);
        return prk;
    }

    /** RFC 5869 Expand step. The PRK must be at least digest-length bytes. */
    public static byte[] hkdfExpand(byte[] prk, byte[] info, int outputLength, Digest digest) {
        if (prk == null || prk.length < digest.getDigestSize()) throw new IllegalArgumentException("PRK is shorter than the digest output");
        if (outputLength < 0 || outputLength > 255 * digest.getDigestSize()) throw new IllegalArgumentException("Invalid HKDF output length");
        byte[] context = info == null ? new byte[0] : info;
        HMac mac = new HMac(digest);
        mac.init(new KeyParameter(prk));
        byte[] output = new byte[outputLength], previous = new byte[0];
        int written = 0;
        for (int counter = 1; written < outputLength; counter++) {
            mac.reset();
            mac.update(previous, 0, previous.length);
            mac.update(context, 0, context.length);
            mac.update((byte) counter);
            previous = new byte[mac.getMacSize()];
            mac.doFinal(previous, 0);
            int copy = Math.min(previous.length, outputLength - written);
            System.arraycopy(previous, 0, output, written, copy);
            written += copy;
        }
        return output;
    }

    private static byte[] intToBytes(int value) {
        return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value };
    }

    /**
     * PBKDF2 (Password-Based Key Derivation Function 2) - PKCS #5
     * Standard password hashing function
     *
     * @param password Password or input key material
     * @param salt Salt value (required)
     * @param iterations Number of iterations (recommended: 10,000+)
     * @param outputLength Desired output length in bytes
     * @param hashAlgorithm Hash algorithm ("SHA1", "SHA256", or "SHA512")
     * @return Derived key
     */
    public static byte[] pbkdf2(byte[] password, byte[] salt, int iterations, int outputLength, String hashAlgorithm)
            throws Exception {
        String algorithm = "PBKDF2WithHmac" + hashAlgorithm.toUpperCase().replace("-", "");

        // Using standard Java implementation for compatibility
        PBEKeySpec spec = new PBEKeySpec(
            new String(password, StandardCharsets.UTF_8).toCharArray(),
            salt,
            iterations,
            outputLength * 8 // bits
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
        return factory.generateSecret(spec).getEncoded();
    }

    /**
     * PBKDF2 using BouncyCastle (alternative implementation)
     */
    public static byte[] pbkdf2BC(byte[] password, byte[] salt, int iterations, int outputLength, Digest digest) {
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(digest);
        gen.init(password, salt, iterations);
        return ((KeyParameter) gen.generateDerivedParameters(outputLength * 8)).getKey();
    }

    /**
     * SCrypt - Memory-hard key derivation function
     * More resistant to hardware brute-force attacks than PBKDF2
     *
     * @param password Password or input key material
     * @param salt Salt value (required)
     * @param N CPU/memory cost parameter (must be power of 2, e.g., 16384)
     * @param r Block size parameter (typically 8)
     * @param p Parallelization parameter (typically 1)
     * @param outputLength Desired output length in bytes
     * @return Derived key
     */
    public static byte[] scrypt(byte[] password, byte[] salt, int N, int r, int p, int outputLength) {
        return SCrypt.generate(password, salt, N, r, p, outputLength);
    }

    /**
     * Argon2 - Winner of Password Hashing Competition (2015)
     * Most modern and secure password hashing algorithm
     *
     * @param password Password or input key material
     * @param salt Salt value (minimum 8 bytes, required)
     * @param iterations Number of iterations (time cost)
     * @param memory Memory cost in KB
     * @param parallelism Parallelism factor
     * @param outputLength Desired output length in bytes
     * @return Derived key
     */
    public static byte[] argon2(byte[] password, byte[] salt, int iterations, int memory,
                                int parallelism, int outputLength) {
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(iterations)
            .withMemoryAsKB(memory)
            .withParallelism(parallelism);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] output = new byte[outputLength];
        generator.generateBytes(password, output);

        return output;
    }

    /**
     * Generate random salt
     *
     * @param length Salt length in bytes (recommended: 16-32)
     * @return Random salt
     */
    public static byte[] generateSalt(int length) {
        byte[] salt = new byte[length];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Get Digest instance for hash algorithm name
     */
    public static Digest getDigest(String algorithm) {
        switch (algorithm.toUpperCase()) {
            case "SHA1":
            case "SHA-1":
                return new SHA1Digest();
            case "SHA256":
            case "SHA-256":
                return new SHA256Digest();
            case "SHA512":
            case "SHA-512":
                return new SHA512Digest();
            default:
                return new SHA256Digest();
        }
    }

    /**
     * Get recommended parameters for each algorithm
     */
    public static class RecommendedParameters {
        // PBKDF2
        public static final int PBKDF2_ITERATIONS = 600000; // OWASP 2023 recommendation
        public static final int PBKDF2_SALT_LENGTH = 16;

        // SCrypt
        public static final int SCRYPT_N = 32768; // 2^15
        public static final int SCRYPT_R = 8;
        public static final int SCRYPT_P = 1;
        public static final int SCRYPT_SALT_LENGTH = 16;

        // Argon2
        public static final int ARGON2_ITERATIONS = 3;
        public static final int ARGON2_MEMORY = 65536; // 64 MB
        public static final int ARGON2_PARALLELISM = 4;
        public static final int ARGON2_SALT_LENGTH = 16;

        // HKDF
        public static final int HKDF_SALT_LENGTH = 32;
    }
}
