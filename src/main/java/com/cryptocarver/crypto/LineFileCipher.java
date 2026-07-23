package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Encrypts UTF-8 text files as independently authenticated records, one per
 * input line. Each encrypted record carries its own fresh nonce and tag, so a
 * line can be handled independently without reusing an AEAD nonce.
 */
public final class LineFileCipher {
    private static final String BASE64_PREFIX = "CF-LINE-1-B64";
    private static final String HEX_PREFIX = "CF-LINE-1-HEX";
    private static final String CBC_BASE64_PREFIX = "CF-LINE-1-CBC-B64";
    private static final String CBC_HEX_PREFIX = "CF-LINE-1-CBC-HEX";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private LineFileCipher() { }

    public record Result(long lines, long inputBytes, long outputBytes) { }

    public enum Encoding { BASE64URL, HEXADECIMAL }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad) throws Exception {
        return encrypt(source, destination, key, algorithm, aad, Encoding.BASE64URL);
    }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, Encoding encoding) throws Exception {
        return encrypt(source, destination, key, algorithm, aad, encoding, null);
    }

    /** CBC compatibility mode uses the caller-provided IV for every line; AEAD modes generate a fresh nonce per line. */
    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, Encoding encoding, byte[] iv) throws Exception {
        return encrypt(source, destination, key, algorithm, aad, encoding, iv, StandardCharsets.UTF_8, false);
    }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, Encoding encoding,
                                 byte[] iv, Charset textCharset, boolean compactRecords) throws Exception {
        validateAlgorithmAndKey(algorithm, key);
        validateIvAndAad(algorithm, iv, aad);
        Path temporary = temporarySibling(destination);
        long lines = 0;
        try (BufferedReader input = Files.newBufferedReader(source, textCharset);
             BufferedWriter output = Files.newBufferedWriter(temporary, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = input.readLine()) != null) {
                byte[] nonce = "AES-256-CBC".equals(algorithm) ? iv : new byte[NONCE_BYTES];
                if (!"AES-256-CBC".equals(algorithm)) RANDOM.nextBytes(nonce);
                Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, key, algorithm, nonce, aad);
                byte[] sealed = cipher.doFinal(line.getBytes(textCharset));
                if (lines++ > 0) output.newLine();
                output.write("AES-256-CBC".equals(algorithm)
                        ? (compactRecords ? "" : cbcPrefix(encoding) + ".") + encode(sealed, encoding)
                        : (compactRecords ? encode(nonce, encoding) : prefix(encoding) + "." + encode(nonce, encoding))
                                + "." + encode(sealed, encoding));
            }
        } catch (Exception e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        moveAtomically(temporary, destination);
        return new Result(lines, Files.size(source), Files.size(destination));
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad) throws Exception {
        return decrypt(source, destination, key, algorithm, aad, null);
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, byte[] iv) throws Exception {
        return decrypt(source, destination, key, algorithm, aad, iv, Encoding.BASE64URL);
    }

    /**
     * Decodes the self-describing record formats and, for CBC compatibility,
     * accepts compact legacy records containing only ciphertext per line.
     */
    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, byte[] iv,
                                 Encoding compactCbcEncoding) throws Exception {
        return decrypt(source, destination, key, algorithm, aad, iv, compactCbcEncoding, StandardCharsets.UTF_8);
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, byte[] iv,
                                 Encoding compactCbcEncoding, Charset textCharset) throws Exception {
        validateAlgorithmAndKey(algorithm, key);
        validateIvAndAad(algorithm, iv, aad);
        Path temporary = temporarySibling(destination);
        long lines = 0;
        try (BufferedReader input = Files.newBufferedReader(source, StandardCharsets.US_ASCII);
             BufferedWriter output = Files.newBufferedWriter(temporary, textCharset)) {
            String record;
            while ((record = input.readLine()) != null) {
                String[] parts = record.split("\\.", -1);
                boolean cbc = isCbcPrefix(parts.length == 2 ? parts[0] : null);
                Encoding encoding = cbc ? encodingForCbcPrefix(parts[0]) : encodingForPrefix(parts.length == 3 ? parts[0] : null);
                boolean compactCbc = "AES-256-CBC".equals(algorithm) && parts.length == 1;
                if (compactCbc) {
                    cbc = true;
                    encoding = compactCbcEncoding == null ? Encoding.BASE64URL : compactCbcEncoding;
                }
                boolean compactAead = !"AES-256-CBC".equals(algorithm) && parts.length == 2;
                if (compactAead) encoding = compactCbcEncoding == null ? Encoding.BASE64URL : compactCbcEncoding;
                if (encoding == null || (cbc && !"AES-256-CBC".equals(algorithm)) || (!cbc && "AES-256-CBC".equals(algorithm))) {
                    throw new IllegalArgumentException("Invalid encrypted line " + (lines + 1));
                }
                byte[] nonce = cbc ? iv : decode(parts[compactAead ? 0 : 1], encoding, "nonce", lines + 1);
                byte[] sealed = decode(parts[compactCbc ? 0 : compactAead ? 1 : cbc ? 1 : 2], encoding, "ciphertext", lines + 1);
                if ((!cbc && (nonce.length != NONCE_BYTES || sealed.length < TAG_BYTES)) || (cbc && sealed.length == 0)) {
                    throw new IllegalArgumentException("Invalid encrypted line " + (lines + 1));
                }
                byte[] plain = newCipher(Cipher.DECRYPT_MODE, key, algorithm, nonce, aad).doFinal(sealed);
                if (lines++ > 0) output.newLine();
                output.write(new String(plain, textCharset));
            }
        } catch (Exception e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        moveAtomically(temporary, destination);
        return new Result(lines, Files.size(source), Files.size(destination));
    }

    private static Cipher newCipher(int operation, byte[] key, String algorithm, byte[] nonce, byte[] aad) throws Exception {
        boolean chacha = "ChaCha20-Poly1305".equals(algorithm);
        boolean cbc = "AES-256-CBC".equals(algorithm);
        Cipher cipher = Cipher.getInstance(chacha ? "ChaCha20-Poly1305" : cbc ? "AES/CBC/PKCS5Padding" : "AES/GCM/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, chacha ? "ChaCha20" : "AES");
        if (chacha || cbc) cipher.init(operation, secretKey, new IvParameterSpec(nonce));
        else cipher.init(operation, secretKey, new GCMParameterSpec(128, nonce));
        if (aad != null && aad.length > 0) cipher.updateAAD(aad);
        return cipher;
    }

    private static void validateAlgorithmAndKey(String algorithm, byte[] key) {
        if (!"AES-256-GCM".equals(algorithm) && !"ChaCha20-Poly1305".equals(algorithm) && !"AES-256-CBC".equals(algorithm)) {
            throw new IllegalArgumentException("Line mode supports AES-256-GCM, ChaCha20-Poly1305, and AES-256-CBC");
        }
        if (key == null || key.length != 32) throw new IllegalArgumentException(algorithm + " requires a 32-byte key");
    }

    private static void validateIvAndAad(String algorithm, byte[] iv, byte[] aad) {
        if ("AES-256-CBC".equals(algorithm)) {
            if (iv == null || iv.length != 16) throw new IllegalArgumentException("AES-256-CBC line mode requires a 16-byte IV");
            if (aad != null && aad.length > 0) throw new IllegalArgumentException("AAD is only available with authenticated encryption");
        }
    }

    private static String prefix(Encoding encoding) { return encoding == Encoding.HEXADECIMAL ? HEX_PREFIX : BASE64_PREFIX; }

    private static Encoding encodingForPrefix(String prefix) {
        if (BASE64_PREFIX.equals(prefix)) return Encoding.BASE64URL;
        if (HEX_PREFIX.equals(prefix)) return Encoding.HEXADECIMAL;
        return null;
    }

    private static String cbcPrefix(Encoding encoding) { return encoding == Encoding.HEXADECIMAL ? CBC_HEX_PREFIX : CBC_BASE64_PREFIX; }

    private static boolean isCbcPrefix(String prefix) { return CBC_BASE64_PREFIX.equals(prefix) || CBC_HEX_PREFIX.equals(prefix); }

    private static Encoding encodingForCbcPrefix(String prefix) {
        if (CBC_BASE64_PREFIX.equals(prefix)) return Encoding.BASE64URL;
        if (CBC_HEX_PREFIX.equals(prefix)) return Encoding.HEXADECIMAL;
        return null;
    }

    private static String encode(byte[] bytes, Encoding encoding) {
        return encoding == Encoding.HEXADECIMAL ? HexFormat.of().formatHex(bytes) : Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(String value, Encoding encoding, String field, long line) {
        try { return encoding == Encoding.HEXADECIMAL ? HexFormat.of().parseHex(value) : Base64.getUrlDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid " + field + " in encrypted line " + line); }
    }

    private static Path temporarySibling(Path destination) throws java.io.IOException {
        return Files.createTempFile(destination.toAbsolutePath().getParent(), ".cryptocarver-lines-", ".tmp");
    }

    private static void moveAtomically(Path source, Path target) throws java.io.IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
