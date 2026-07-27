package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** Encodes portable screen configurations as plain JSON or password-protected AES-GCM envelopes. */
public final class ScreenConfigurationCodec {

    public static final String ENVELOPE_FORMAT = "cryptocarver-screen-configuration-encrypted";
    private static final int VERSION = 1;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ScreenConfigurationCodec() {
    }

    public static String encodePlain(ScreenConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("Configuration is required");
        return configuration.toJson();
    }

    public static String encodeEncrypted(ScreenConfiguration configuration, char[] password) {
        requirePassword(password);
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] nonce = randomBytes(NONCE_BYTES);
        byte[] plaintext = configuration.toJson().getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad());
            ciphertext = cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt screen configuration", e);
        } finally {
            java.util.Arrays.fill(plaintext, (byte) 0);
            java.util.Arrays.fill(password, '\0');
        }
        Envelope envelope = new Envelope(ENVELOPE_FORMAT, VERSION, "PBKDF2-HMAC-SHA256/AES-256-GCM",
                ITERATIONS, b64(salt), b64(nonce), b64(ciphertext));
        return new GsonBuilder().setPrettyPrinting().create().toJson(envelope);
    }

    public static ScreenConfiguration decode(String document, char[] password) {
        if (document == null || document.isBlank()) throw new IllegalArgumentException("Configuration document is empty");
        if (!isEncrypted(document)) return ScreenConfiguration.fromJson(document);
        requirePassword(password);
        Envelope envelope;
        try {
            envelope = new Gson().fromJson(document, Envelope.class);
            validate(envelope);
        } catch (RuntimeException e) {
            java.util.Arrays.fill(password, '\0');
            throw new IllegalArgumentException("Invalid encrypted configuration envelope", e);
        }
        byte[] plaintext;
        try {
            byte[] salt = Base64.getDecoder().decode(envelope.salt);
            byte[] nonce = Base64.getDecoder().decode(envelope.nonce);
            byte[] ciphertext = Base64.getDecoder().decode(envelope.ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, envelope.iterations),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad());
            plaintext = cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new IllegalArgumentException("Incorrect password or modified configuration file");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Unable to decrypt configuration file", e);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        try {
            return ScreenConfiguration.fromJson(new String(plaintext, StandardCharsets.UTF_8));
        } finally {
            java.util.Arrays.fill(plaintext, (byte) 0);
        }
    }

    public static boolean isEncrypted(String document) {
        if (document == null) return false;
        try {
            Header header = new Gson().fromJson(document, Header.class);
            return header != null && ENVELOPE_FORMAT.equals(header.format);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(encoded, "AES");
            } finally {
                java.util.Arrays.fill(encoded, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static void validate(Envelope envelope) {
        if (envelope == null || !ENVELOPE_FORMAT.equals(envelope.format) || envelope.version != VERSION) {
            throw new IllegalArgumentException("Unsupported encrypted configuration format");
        }
        if (envelope.iterations < 100_000 || envelope.iterations > 2_000_000) {
            throw new IllegalArgumentException("Invalid PBKDF2 iteration count");
        }
        if (Base64.getDecoder().decode(envelope.salt).length != SALT_BYTES
                || Base64.getDecoder().decode(envelope.nonce).length != NONCE_BYTES) {
            throw new IllegalArgumentException("Invalid encrypted configuration parameters");
        }
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Configuration password must contain at least 8 characters");
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return value;
    }

    private static byte[] aad() {
        return (ENVELOPE_FORMAT + ":" + VERSION).getBytes(StandardCharsets.UTF_8);
    }

    private static String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private record Envelope(String format, int version, String encryption, int iterations,
                            String salt, String nonce, String ciphertext) { }

    private static final class Header { String format; }
}
