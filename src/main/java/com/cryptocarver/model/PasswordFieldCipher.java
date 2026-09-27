package com.cryptocarver.model;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/** Shared password based AES-GCM primitive for protected application data. */
final class PasswordFieldCipher {
    static final String KDF = "PBKDF2-HMAC-SHA256";
    static final int ITERATIONS = 600_000;
    static final int SALT_BYTES = 16;
    static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordFieldCipher() { }

    static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static byte[] encrypt(char[] password, byte[] salt, byte[] nonce, byte[] plaintext, int iterations)
            throws GeneralSecurityException {
        return encrypt(password, salt, nonce, plaintext, iterations, null);
    }
    static byte[] encrypt(char[] password, byte[] salt, byte[] nonce, byte[] plaintext, int iterations, byte[] aad)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt, iterations), new GCMParameterSpec(TAG_BITS, nonce));
        if (aad != null) cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    static byte[] decrypt(char[] password, byte[] salt, byte[] nonce, byte[] ciphertext, int iterations)
            throws GeneralSecurityException {
        return decrypt(password, salt, nonce, ciphertext, iterations, null);
    }
    static byte[] decrypt(char[] password, byte[] salt, byte[] nonce, byte[] ciphertext, int iterations, byte[] aad)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt, iterations), new GCMParameterSpec(TAG_BITS, nonce));
        if (aad != null) cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private static SecretKeySpec derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        if (password == null || password.length == 0) throw new GeneralSecurityException("Password is required");
        if (iterations < 100_000) throw new GeneralSecurityException("PBKDF2 iteration count is too low");
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try { return new SecretKeySpec(encoded, "AES"); }
            finally { Arrays.fill(encoded, (byte) 0); }
        } finally { spec.clearPassword(); }
    }
}
