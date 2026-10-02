package com.cryptocarver.ui;

import com.cryptocarver.crypto.KdfWrapTestVectors;
import com.cryptocarver.util.DataConverter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Independent primitive/layout oracles for fixed UI-compatible laboratory vectors. */
final class KeysSplit2Vectors {
    record Kdf(String input, String salt, String info, String iterations, String length, byte[] expected) { }
    private KeysSplit2Vectors() { }

    static Kdf kdf(String name) throws Exception {
        if (name.startsWith("HKDF")) {
            byte[] input = new byte[22];
            Arrays.fill(input, (byte) 0x0b);
            byte[] salt = DataConverter.hexToBytes(KdfWrapTestVectors.HKDF_RFC5869_SALT);
            byte[] info = DataConverter.hexToBytes(KdfWrapTestVectors.HKDF_RFC5869_INFO);
            String hash = "Hmac" + name.substring(5);
            byte[] prk = mac(hash, salt, input);
            byte[] expanded = new byte[0];
            byte[] previous = new byte[0];
            for (int counter = 1; expanded.length < 42; counter++) {
                previous = mac(hash, prk, concat(previous, info, new byte[] {(byte) counter}));
                expanded = concat(expanded, previous);
            }
            byte[] expected = name.equals("HKDF-SHA256") ? DataConverter.hexToBytes(KdfWrapTestVectors.HKDF_RFC5869_OKM) : Arrays.copyOf(expanded, 42);
            return vector(input, salt, info, 1, expected);
        }
        if (name.startsWith("NIST")) {
            byte[] key = DataConverter.hexToBytes(KdfWrapTestVectors.KDF_AES128_KEY);
            byte[] label = "CryptoCarver".getBytes(StandardCharsets.US_ASCII);
            byte[] context = DataConverter.hexToBytes(KdfWrapTestVectors.KDF_CONTEXT);
            return vector(key, label, context, 1, mac("HmacSHA256", key, concat(new byte[] {0, 0, 0, 1}, label, new byte[] {0}, context, new byte[] {0, 0, 1, 0})));
        }
        if (name.startsWith("X9.63")) {
            byte[] input = DataConverter.hexToBytes(KdfWrapTestVectors.KEY_DATA_128);
            byte[] info = "ECIES".getBytes(StandardCharsets.US_ASCII);
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] expected = concat(digest.digest(concat(input, new byte[] {0, 0, 0, 1}, info)),
                    digest.digest(concat(input, new byte[] {0, 0, 0, 2}, info)));
            return vector(input, new byte[0], info, 1, Arrays.copyOf(expected, 40));
        }
        if (name.startsWith("PBKDF2")) {
            byte[] password = "password".getBytes(StandardCharsets.UTF_8);
            byte[] salt = "salt".getBytes(StandardCharsets.UTF_8);
            // RFC 6070 case 1 for SHA1; the same fixed parameters evaluated with HMAC-SHA256/512.
            String expected = switch (name) {
                case "PBKDF2-SHA1" -> "0C60C80F961F0E71F3A9B524AF6012062FE037A6";
                case "PBKDF2-SHA256" -> "120FB6CFFCF8B32C43E7225256C4F837A86548C92CCC35480805987CB70BE17B";
                default -> "867F70CF1ADE02CFF3752599A3A53DC4AF34C7A669815AE5D513554E1C8CF252";
            };
            return vector(password, salt, new byte[0], 1, DataConverter.hexToBytes(expected));
        }
        if (name.equals("SCrypt")) {
            // RFC 7914, N=16384/r=8/p=1, matches the screen's r/p policy.
            return vector("pleaseletmein".getBytes(StandardCharsets.UTF_8), "SodiumChloride".getBytes(StandardCharsets.UTF_8), new byte[0], 16384,
                    DataConverter.hexToBytes("7023BDCB3AFD7348461C06CD81FD38EBFDA8FBBA904F8E3EA9B543F6545DA1F2D5432955613F0FCF62D49705242A9AF9E61E85DC0D651E40DFCF017B45575887"));
        }
        // Fixed Argon2id vector for the screen's 64 MiB/four-lane policy. Oracle bypasses KeyDerivation.
        byte[] password = "password".getBytes(StandardCharsets.UTF_8);
        byte[] salt = "somesalt".getBytes(StandardCharsets.UTF_8);
        var generator = new Argon2BytesGenerator();
        generator.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt).withIterations(3).withMemoryAsKB(65536).withParallelism(4).build());
        byte[] expected = new byte[32];
        generator.generateBytes(password, expected);
        return vector(password, salt, new byte[0], 3, expected);
    }

    private static Kdf vector(byte[] input, byte[] salt, byte[] info, int iterations, byte[] expected) {
        return new Kdf(DataConverter.bytesToHex(input), DataConverter.bytesToHex(salt), DataConverter.bytesToHex(info),
                String.valueOf(iterations), String.valueOf(expected.length), expected);
    }
    private static byte[] mac(String name, byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(name);
        mac.init(new SecretKeySpec(key, name));
        return mac.doFinal(data);
    }
    private static byte[] concat(byte[]... parts) {
        byte[] output = new byte[Arrays.stream(parts).mapToInt(part -> part.length).sum()];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, output, offset, part.length);
            offset += part.length;
        }
        return output;
    }
}
