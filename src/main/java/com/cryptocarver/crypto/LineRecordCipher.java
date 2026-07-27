package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Encrypts or decrypts independent records for line-based or batch formats.
 */
public final class LineRecordCipher {
    static final String BASE64_PREFIX = "CF-LINE-1-B64";
    static final String HEX_PREFIX = "CF-LINE-1-HEX";
    static final String CBC_BASE64_PREFIX = "CF-LINE-1-CBC-B64";
    static final String CBC_HEX_PREFIX = "CF-LINE-1-CBC-HEX";
    static final int NONCE_BYTES = 12;
    static final int TAG_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private LineRecordCipher() { }

    public static void validateAlgorithmAndKey(String algorithm, byte[] key) {
        if (!"AES-256-GCM".equals(algorithm) && !"ChaCha20-Poly1305".equals(algorithm) && !"AES-256-CBC".equals(algorithm)) {
            throw new IllegalArgumentException("Line mode supports AES-256-GCM, ChaCha20-Poly1305, and AES-256-CBC");
        }
        if (key == null || key.length != 32) throw new IllegalArgumentException(algorithm + " requires a 32-byte key");
    }

    public static void validateIvAndAad(String algorithm, byte[] iv, byte[] aad) {
        if ("AES-256-CBC".equals(algorithm)) {
            if (iv == null || iv.length != 16) throw new IllegalArgumentException("AES-256-CBC line mode requires a 16-byte IV");
            if (aad != null && aad.length > 0) throw new IllegalArgumentException("AAD is only available with authenticated encryption");
        }
    }

    public static Cipher newCipher(int operation, byte[] key, String algorithm, byte[] nonce, byte[] aad) throws Exception {
        boolean chacha = "ChaCha20-Poly1305".equals(algorithm);
        boolean cbc = "AES-256-CBC".equals(algorithm);
        Cipher cipher = Cipher.getInstance(chacha ? "ChaCha20-Poly1305" : cbc ? "AES/CBC/PKCS5Padding" : "AES/GCM/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, chacha ? "ChaCha20" : "AES");
        if (chacha || cbc) cipher.init(operation, secretKey, new IvParameterSpec(nonce));
        else cipher.init(operation, secretKey, new GCMParameterSpec(128, nonce));
        if (aad != null && aad.length > 0) cipher.updateAAD(aad);
        return cipher;
    }

    public static String prefix(LineFileCipher.Encoding encoding) { return encoding == LineFileCipher.Encoding.HEXADECIMAL ? HEX_PREFIX : BASE64_PREFIX; }

    public static LineFileCipher.Encoding encodingForPrefix(String prefix) {
        if (BASE64_PREFIX.equals(prefix)) return LineFileCipher.Encoding.BASE64URL;
        if (HEX_PREFIX.equals(prefix)) return LineFileCipher.Encoding.HEXADECIMAL;
        return null;
    }

    public static String cbcPrefix(LineFileCipher.Encoding encoding) { return encoding == LineFileCipher.Encoding.HEXADECIMAL ? CBC_HEX_PREFIX : CBC_BASE64_PREFIX; }

    public static boolean isCbcPrefix(String prefix) { return CBC_BASE64_PREFIX.equals(prefix) || CBC_HEX_PREFIX.equals(prefix); }

    public static LineFileCipher.Encoding encodingForCbcPrefix(String prefix) {
        if (CBC_BASE64_PREFIX.equals(prefix)) return LineFileCipher.Encoding.BASE64URL;
        if (CBC_HEX_PREFIX.equals(prefix)) return LineFileCipher.Encoding.HEXADECIMAL;
        return null;
    }

    public static String encode(byte[] bytes, LineFileCipher.Encoding encoding) {
        return encoding == LineFileCipher.Encoding.HEXADECIMAL ? HexFormat.of().formatHex(bytes) : Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] decode(String value, LineFileCipher.Encoding encoding, String field, long line) {
        try { return encoding == LineFileCipher.Encoding.HEXADECIMAL ? HexFormat.of().parseHex(value) : Base64.getUrlDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid " + field + " in encrypted line " + line); }
    }

    public static String encryptRecord(String input, byte[] key, String algorithm, byte[] aad, LineFileCipher.Encoding encoding,
                                       byte[] iv, Charset textCharset, boolean compactRecords) throws Exception {
        validateAlgorithmAndKey(algorithm, key);
        validateIvAndAad(algorithm, iv, aad);

        byte[] nonce = "AES-256-CBC".equals(algorithm) ? iv : new byte[NONCE_BYTES];
        if (!"AES-256-CBC".equals(algorithm)) RANDOM.nextBytes(nonce);
        Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, key, algorithm, nonce, aad);
        byte[] sealed = cipher.doFinal(input.getBytes(textCharset));

        return "AES-256-CBC".equals(algorithm)
                ? (compactRecords ? "" : cbcPrefix(encoding) + ".") + encode(sealed, encoding)
                : (compactRecords ? encode(nonce, encoding) : prefix(encoding) + "." + encode(nonce, encoding))
                        + "." + encode(sealed, encoding);
    }

    public static String decryptRecord(String record, byte[] key, String algorithm, byte[] aad, byte[] iv,
                                       LineFileCipher.Encoding compactCbcEncoding, Charset textCharset, long lineIndexForError) throws Exception {
        validateAlgorithmAndKey(algorithm, key);
        validateIvAndAad(algorithm, iv, aad);

        String[] parts = record.split("\\.", -1);
        boolean cbc = isCbcPrefix(parts.length == 2 ? parts[0] : null);
        LineFileCipher.Encoding encoding = cbc ? encodingForCbcPrefix(parts[0]) : encodingForPrefix(parts.length == 3 ? parts[0] : null);
        boolean compactCbc = "AES-256-CBC".equals(algorithm) && parts.length == 1;
        if (compactCbc) {
            cbc = true;
            encoding = compactCbcEncoding == null ? LineFileCipher.Encoding.BASE64URL : compactCbcEncoding;
        }
        boolean compactAead = !"AES-256-CBC".equals(algorithm) && parts.length == 2;
        if (compactAead) encoding = compactCbcEncoding == null ? LineFileCipher.Encoding.BASE64URL : compactCbcEncoding;
        if (encoding == null || (cbc && !"AES-256-CBC".equals(algorithm)) || (!cbc && "AES-256-CBC".equals(algorithm))) {
            throw new IllegalArgumentException("Invalid encrypted line " + lineIndexForError);
        }
        byte[] nonce = cbc ? iv : decode(parts[compactAead ? 0 : 1], encoding, "nonce", lineIndexForError);
        byte[] sealed = decode(parts[compactCbc ? 0 : compactAead ? 1 : cbc ? 1 : 2], encoding, "ciphertext", lineIndexForError);
        if ((!cbc && (nonce.length != NONCE_BYTES || sealed.length < TAG_BYTES)) || (cbc && sealed.length == 0)) {
            throw new IllegalArgumentException("Invalid encrypted line " + lineIndexForError);
        }
        byte[] plain = newCipher(Cipher.DECRYPT_MODE, key, algorithm, nonce, aad).doFinal(sealed);
        return new String(plain, textCharset);
    }
}
