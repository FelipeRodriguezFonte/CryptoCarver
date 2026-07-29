package com.cryptocarver.model;

import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.util.DataConverter;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.junit.jupiter.api.Assertions.*;

public class GeneratedKeySummaryTest {

    @BeforeAll
    public static void setUp() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    @DisplayName("AES-128 summary metadata and KCV correctness")
    public void testAes128Summary() throws Exception {
        byte[] key = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");
        GeneratedKeySummary summary = new GeneratedKeySummary(key, "AES-128", false);

        assertEquals("AES-128", summary.getAlgorithm());
        assertEquals(128, summary.getBitLength());
        assertEquals(16, summary.getByteLength());
        assertEquals("128 bits (16 bytes)", summary.getFormattedLength());
        assertEquals("Not applicable", summary.getParityStatus());
        assertEquals("Generated locally", summary.getOrigin());

        byte[] expectedKcv3 = KeyOperations.calculateKCV_AES(key);
        String expected3Hex = DataConverter.bytesToHex(expectedKcv3).toUpperCase();
        assertEquals(expected3Hex, summary.getKcv3BytesHex());

        byte[] expectedFull = KeyOperations.calculateFullZeroBlockKCV(key, "AES-128");
        String expectedFullHex = DataConverter.bytesToHex(expectedFull).toUpperCase();
        assertEquals(expectedFullHex, summary.getKcvFullHex());

        assertTrue(summary.getFormattedKcv().contains(expected3Hex));
        assertTrue(summary.getFormattedKcv().contains(expectedFullHex));

        assertNotNull(summary.getFingerprintTruncated());
        assertEquals(16, summary.getFingerprintTruncated().length());
    }

    @Test
    @DisplayName("AES-256 summary metadata and deterministic fingerprint")
    public void testAes256Summary() throws Exception {
        byte[] key = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
        GeneratedKeySummary summary1 = new GeneratedKeySummary(key, "AES-256", false);
        GeneratedKeySummary summary2 = new GeneratedKeySummary(key, "AES-256", false);

        assertEquals("AES-256", summary1.getAlgorithm());
        assertEquals(256, summary1.getBitLength());
        assertEquals(32, summary1.getByteLength());
        assertEquals(summary1.getFingerprintTruncated(), summary2.getFingerprintTruncated());
        assertEquals(summary1.getFormattedKcv(), summary2.getFormattedKcv());
    }

    @Test
    @DisplayName("DES & 3DES VISA KCV and Parity detection")
    public void testDesAnd3DesParity() throws Exception {
        byte[] desKey = DataConverter.hexToBytes("0123456789ABCDEF");
        GeneratedKeySummary summaryDes = new GeneratedKeySummary(desKey, "DES", true);

        assertEquals("DES", summaryDes.getAlgorithm());
        assertEquals(64, summaryDes.getBitLength());
        assertEquals("Applied", summaryDes.getParityStatus());
        assertNotNull(summaryDes.getKcv3BytesHex());

        byte[] tdesKey = DataConverter.hexToBytes("0123456789ABCDEF0123456789ABCDEF");
        GeneratedKeySummary summary3DesNoParity = new GeneratedKeySummary(tdesKey, "3DES", false);
        assertEquals("3DES", summary3DesNoParity.getAlgorithm());
        assertEquals(128, summary3DesNoParity.getBitLength());
        assertNotNull(summary3DesNoParity.getKcv3BytesHex());
    }

    @Test
    @DisplayName("KCV failure behavior produces explicit non-blocking error reason")
    public void testKcvFailure() {
        byte[] invalidKey = new byte[5]; // Invalid length for symmetric ciphers
        GeneratedKeySummary summary = new GeneratedKeySummary(invalidKey, "AES-128", false);

        assertNull(summary.getKcv3BytesHex());
        assertNotNull(summary.getKcvErrorReason());
        assertTrue(summary.getFormattedKcv().startsWith("KCV unavailable:"));
    }

    @Test
    @DisplayName("Secret key bytes do not leak in toString() or Gson serialization")
    public void testNonLeakage() {
        byte[] secretKey = DataConverter.hexToBytes("AABBCCDDEEFF00112233445566778899");
        String secretHex = "AABBCCDDEEFF00112233445566778899";

        GeneratedKeySummary summary = new GeneratedKeySummary(secretKey, "AES-128", false);

        assertFalse(summary.toString().contains(secretHex), "toString() must not contain secret key hex");

        Gson gson = new Gson();
        String json = gson.toJson(summary);
        assertFalse(json.contains(secretHex), "JSON serialization must not contain secret key hex");
        assertFalse(json.contains("rawKeyBytes"), "JSON serialization must not include transient rawKeyBytes");
    }
}
