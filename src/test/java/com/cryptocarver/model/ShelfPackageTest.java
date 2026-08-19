package com.cryptocarver.model;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.util.DataConverter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShelfPackageTest {
    private static final byte[] KEY = DataConverter.hexToBytes("00112233445566778899AABBCCDDEEFF");
    private static final byte[] IV = DataConverter.hexToBytes("AABBCCDDEEFF001122334455");
    private static final byte[] AAD = DataConverter.hexToBytes("01020304");

    @Test
    void aesGcmHexRoundTripSurvivesShelfPersistence() throws Exception {
        assertRoundTrip("Hexadecimal", DataConverter.bytesToHex(KEY), DataConverter.bytesToHex(IV), DataConverter.bytesToHex(AAD));
    }

    @Test
    void aesGcmBase64RoundTripSurvivesShelfPersistence() throws Exception {
        assertRoundTrip("Base64", Base64.getEncoder().encodeToString(KEY),
                DataConverter.bytesToHex(IV), DataConverter.bytesToHex(AAD));
    }

    private void assertRoundTrip(String outputFormat, String ignoredKeyEncoding,
                                 String nonceEncoding, String aadEncoding) throws Exception {
        byte[] plaintext = "shelf-aead-round-trip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] combined = SymmetricCipher.encrypt(plaintext, KEY, "AES-128", "GCM", "NoPadding", IV, AAD);
        byte[] ciphertext = java.util.Arrays.copyOf(combined, combined.length - 16);
        byte[] tag = java.util.Arrays.copyOfRange(combined, combined.length - 16, combined.length);
        String ciphertextText = "Hexadecimal".equals(outputFormat)
                ? DataConverter.bytesToHex(ciphertext) : Base64.getEncoder().encodeToString(ciphertext);
        String tagText = DataConverter.bytesToHex(tag);

        Map<String, String> artifacts = new LinkedHashMap<>();
        artifacts.put("ciphertext", ciphertextText);
        artifacts.put("algorithm", "AES-128");
        artifacts.put("mode", "GCM");
        artifacts.put("padding", "NoPadding");
        artifacts.put("format", outputFormat);
        artifacts.put("authTag", tagText);
        artifacts.put("nonce", nonceEncoding);
        artifacts.put("aad", aadEncoding);
        ClipboardEntry entry = new ClipboardEntry("AES-GCM", ciphertextText, ClipboardEntry.Format.HEX,
                OperationDetail.Classification.SENSITIVE, "Symmetric Encrypt", "AES-128")
                .withShelfPackage(ShelfPackage.authenticatedCipher(artifacts));

        Path dir = Files.createTempDirectory("shelf-aead-");
        Path shelfPath = dir.resolve("shelf.json");
        ClipboardShelfManager shelf = new ClipboardShelfManager(shelfPath);
        shelf.addEntry(entry);
        ClipboardEntry loaded = new ClipboardShelfManager(shelfPath).getEntries().get(0);
        assertEquals(ClipboardEntry.EntryKind.STRUCTURED, loaded.getEntryKind());
        assertEquals("GCM", loaded.getShelfPackage().artifact("mode"));
        assertEquals(outputFormat, loaded.getShelfPackage().artifact("format"));

        byte[] loadedCiphertext = "Hexadecimal".equals(outputFormat)
                ? DataConverter.hexToBytes(loaded.getShelfPackage().artifact("ciphertext"))
                : Base64.getDecoder().decode(loaded.getShelfPackage().artifact("ciphertext"));
        byte[] loadedTag = DataConverter.hexToBytes(loaded.getShelfPackage().artifact("authTag"));
        byte[] loadedIv = DataConverter.hexToBytes(loaded.getShelfPackage().artifact("nonce"));
        byte[] loadedAad = DataConverter.hexToBytes(loaded.getShelfPackage().artifact("aad"));
        byte[] recovered = SymmetricCipher.decrypt(
                concat(loadedCiphertext, loadedTag), KEY, "AES-128", "GCM", "NoPadding", loadedIv, loadedAad);
        assertArrayEquals(plaintext, recovered);
    }

    @Test
    void historicalEntryWithoutMetadataRemainsSimple() throws Exception {
        Path dir = Files.createTempDirectory("shelf-legacy-");
        Path path = dir.resolve("shelf.json");
        Files.writeString(path, "[{\"label\":\"old\",\"value\":\"0011\",\"format\":\"HEX\",\"sourceOperation\":\"Hashing\"}]");
        ClipboardEntry loaded = new ClipboardShelfManager(path).getEntries().get(0);
        assertEquals(ClipboardEntry.EntryKind.SIMPLE, loaded.getEntryKind());
        assertEquals("0011", loaded.getValue());
        assertNull(loaded.getShelfPackage());
    }

    @Test
    void packageSerializationCannotContainSecretMaterial() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ciphertext", "0011");
        fields.put("algorithm", "AES-128");
        fields.put("mode", "GCM");
        fields.put("padding", "NoPadding");
        fields.put("format", "Hexadecimal");
        fields.put("authTag", "AABB");
        fields.put("nonce", "CCDD");
        fields.put("aad", "EEFF");
        fields.put("key", "MUST-NOT-SERIALIZE");
        ShelfPackage pkg = ShelfPackage.authenticatedCipher(fields);
        String json = new GsonBuilder().create().toJson(pkg);
        assertFalse(json.toLowerCase().contains("must-not-serialize"));
        assertFalse(json.toLowerCase().contains("password"));
        assertFalse(json.toLowerCase().contains("pin"));
        assertFalse(json.toLowerCase().contains("credential"));
        assertFalse(json.toLowerCase().contains("hsm"));
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] all = new byte[left.length + right.length];
        System.arraycopy(left, 0, all, 0, left.length);
        System.arraycopy(right, 0, all, left.length, right.length);
        return all;
    }
}
