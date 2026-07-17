package com.cryptoforge.crypto.hsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.crypto.spec.SecretKeySpec;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedHsmProviderTest {

    private SimulatedHsmProvider hsm;

    @BeforeEach
    void setUp() {
        hsm = SimulatedHsmProvider.getInstance();
        hsm.clear();
    }

    @Test
    void testImportAndGetMetadata() {
        SecretKeySpec secretKey = new SecretKeySpec(new byte[16], "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("key1", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT));

        hsm.importKey(km);

        KeyMaterial meta = hsm.getKeyMetadata("key1");
        assertNotNull(meta);
        assertEquals("key1", meta.getId());
        assertNull(meta.getKey(), "NON_EXPORTABLE key metadata should not expose the raw key");
    }

    @Test
    void testExportRestrictions() {
        SecretKeySpec secretKey = new SecretKeySpec(new byte[16], "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("key-no-exp", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        hsm.importKey(km);

        assertThrows(UnsupportedOperationException.class, () -> hsm.exportKey("key-no-exp"));

        KeyMaterial kmExp = KeyMaterialFactory.fromSecretKey("key-exp", secretKey, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        hsm.importKey(kmExp);

        assertNotNull(hsm.exportKey("key-exp"));
    }

    @Test
    void testUsageEnforcement() throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(new byte[16], "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("key-enc", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        hsm.importKey(km);

        // Cannot use for MAC
        assertThrows(IllegalArgumentException.class, () -> hsm.generateMac("key-enc", new byte[16], "HMAC-SHA256"));

        // Can use for ENCRYPT
        byte[] ciphertext = hsm.encryptSymmetric("key-enc", new byte[16], "AES", "ECB", "NoPadding", null, null);
        assertNotNull(ciphertext);
    }

    @Test
    void testListKeyIdsUsageFilter() {
        SecretKeySpec secretKey = new SecretKeySpec(new byte[16], "AES");
        hsm.importKey(KeyMaterialFactory.fromSecretKey("key-enc", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT)));
        hsm.importKey(KeyMaterialFactory.fromSecretKey("key-mac", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.MAC)));

        Set<String> encKeys = hsm.listKeyIds(KeyUsage.ENCRYPT);
        assertTrue(encKeys.contains("key-enc"));
        assertFalse(encKeys.contains("key-mac"));

        Set<String> macKeys = hsm.listKeyIds(KeyUsage.MAC);
        assertTrue(macKeys.contains("key-mac"));
        assertFalse(macKeys.contains("key-enc"));

        Set<String> allKeys = hsm.listKeyIds();
        assertEquals(2, allKeys.size());
    }

    @Test
    void testAesRoundTrip() throws Exception {
        byte[] keyBytes = "1234567890123456".getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        hsm.importKey(KeyMaterialFactory.fromSecretKey("aes-key", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT)));

        byte[] plaintext = "hello world".getBytes();
        byte[] ciphertext = hsm.encryptSymmetric("aes-key", plaintext, "AES", "ECB", "PKCS5Padding", null, null);
        assertNotNull(ciphertext);
        assertNotEquals(plaintext, ciphertext);

        byte[] decrypted = hsm.decryptSymmetric("aes-key", ciphertext, "AES", "ECB", "PKCS5Padding", null, null);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void testMacRoundTrip() throws Exception {
        byte[] keyBytes = "1234567890123456".getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HMAC-SHA256");
        hsm.importKey(KeyMaterialFactory.fromSecretKey("mac-key", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.MAC)));

        byte[] data = "hello world".getBytes();
        byte[] hsmMac = hsm.generateMac("mac-key", data, "HMAC-SHA256");

        byte[] manualMac = com.cryptoforge.crypto.MACOperations.generate(data, keyBytes, "HMAC-SHA256");
        assertArrayEquals(manualMac, hsmMac);
    }

    @Test
    void testDuplicateImportWithoutReplace() {
        SecretKeySpec secretKey = new SecretKeySpec(new byte[16], "AES");
        hsm.importKey(KeyMaterialFactory.fromSecretKey("dup-key", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            hsm.importKey(KeyMaterialFactory.fromSecretKey("dup-key", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT)))
        );
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void testDuplicateImportWithReplace() {
        SecretKeySpec secretKey1 = new SecretKeySpec(new byte[16], "AES");
        hsm.importKey(KeyMaterialFactory.fromSecretKey("replace-key", secretKey1, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT)));
        assertEquals(128, hsm.getKeyMetadata("replace-key").getSize());

        SecretKeySpec secretKey2 = new SecretKeySpec(new byte[32], "AES");
        hsm.importKey(KeyMaterialFactory.fromSecretKey("replace-key", secretKey2, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT)), true);

        KeyMaterial meta = hsm.getKeyMetadata("replace-key");
        assertNotNull(meta);
        assertEquals(256, meta.getSize());
    }
}
