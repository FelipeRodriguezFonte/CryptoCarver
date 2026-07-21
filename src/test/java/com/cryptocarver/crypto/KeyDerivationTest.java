package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

class KeyDerivationTest {
    @Test
    void hkdfExtractAndExpandMatchRfc5869Sha256CaseOne() {
        byte[] ikm = new byte[22]; java.util.Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = DataConverter.hexToBytes("000102030405060708090A0B0C");
        byte[] info = DataConverter.hexToBytes("F0F1F2F3F4F5F6F7F8F9");
        byte[] prk = KeyDerivation.hkdfExtract(ikm, salt, KeyDerivation.getDigest("SHA-256"));
        assertArrayEquals(DataConverter.hexToBytes("077709362C2E32DF0DDC3F0DC47BBA6390B6C73BB50F9C3122EC844AD7C2B3E5"), prk);
        assertArrayEquals(DataConverter.hexToBytes("3CB25F25FAACD57A90434F64D0362F2A2D2D0A90CF1A5A4C5DB02D56ECC4C5BF34007208D5B887185865"),
                KeyDerivation.hkdfExpand(prk, info, 42, KeyDerivation.getDigest("SHA-256")));
    }

    @Test
    void counterKdfFollowsSp800108FixedInputLayout() throws Exception {
        byte[] key = DataConverter.hexToBytes("603DEB1015CA71BE2B73AEF0857D7781");
        byte[] label = "CryptoCarver".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] context = DataConverter.hexToBytes("01020304");
        byte[] actual = KeyDerivation.sp800108Counter(key, label, context, 32, KeyDerivation.getDigest("SHA-256"));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] expected = mac.doFinal(concat(new byte[] {0, 0, 0, 1}, label, new byte[] {0}, context,
                new byte[] {0, 0, 1, 0}));
        assertArrayEquals(expected, actual);
    }

    @Test
    void x963FollowsSharedSecretCounterInfoLayout() throws Exception {
        byte[] secret = DataConverter.hexToBytes("00112233445566778899AABBCCDDEEFF");
        byte[] info = "ECIES".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] actual = KeyDerivation.x963(secret, info, 40, KeyDerivation.getDigest("SHA-256"));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] expected = concat(sha256.digest(concat(secret, new byte[] {0, 0, 0, 1}, info)),
                sha256.digest(concat(secret, new byte[] {0, 0, 0, 2}, info)));
        assertArrayEquals(java.util.Arrays.copyOf(expected, 40), actual);
    }

    @Test
    void kdfRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> KeyDerivation.hkdf(null, null, null, 32, KeyDerivation.getDigest("SHA-256")));
        assertThrows(IllegalArgumentException.class,
                () -> KeyDerivation.sp800108Counter(new byte[0], null, null, 32, KeyDerivation.getDigest("SHA-256")));
    }

    private static byte[] concat(byte[]... parts) {
        int size = java.util.Arrays.stream(parts).mapToInt(part -> part.length).sum();
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
