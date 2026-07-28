package com.cryptocarver.crypto.hsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.crypto.spec.SecretKeySpec;
import java.util.Set;
import java.io.File;
import java.nio.file.Files;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;

import static org.junit.jupiter.api.Assertions.*;

public class KeyLabTest {

    private File tempFile;

    @BeforeEach
    public void setUp() throws Exception {
        tempFile = File.createTempFile("key_lab_test_store", ".json");
        tempFile.deleteOnExit();
        SimulatedHsmProvider.getInstance().resetForTest(tempFile.toPath());
    }

    @AfterEach
    public void tearDown() {
        SimulatedHsmProvider.getInstance().clear();
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    public void testAesKcvCalculation() {
        byte[] rawKey = new byte[32];
        for (int i = 0; i < 32; i++) rawKey[i] = (byte) i;

        SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("aes-test-key", keySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));

        assertNotNull(km.getKcv());
        assertEquals(6, km.getKcv().length());

        byte[] zeroKey = new byte[32];
        SecretKeySpec zeroKeySpec = new SecretKeySpec(zeroKey, "AES");
        KeyMaterial zeroKm = KeyMaterialFactory.fromSecretKey("aes-zero-key", zeroKeySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        assertEquals("DC95C0", zeroKm.getKcv());
    }

    @Test
    public void testDesKcvCalculation() {
        byte[] zeroKey = new byte[8];
        SecretKeySpec zeroKeySpec = new SecretKeySpec(zeroKey, "DES");
        KeyMaterial zeroKm = KeyMaterialFactory.fromSecretKey("des-zero-key", zeroKeySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        assertEquals("8CA64D", zeroKm.getKcv());
    }

    @Test
    public void testDuplicateFingerprintChecking() {
        byte[] rawKey1 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKeySpec keySpec1 = new SecretKeySpec(rawKey1, "AES");

        KeyMaterial km1 = KeyMaterialFactory.fromSecretKey("key1", keySpec1, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        SimulatedHsmProvider.getInstance().importKey(km1);

        KeyMaterial km2 = KeyMaterialFactory.fromSecretKey("key2", keySpec1, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));

        var existing = SimulatedHsmProvider.getInstance().findKeyByFingerprint(km2.getFingerprint());
        assertNotNull(existing);
        assertEquals("key1", existing.getId());
    }

    @Test
    public void testMetadataImportExport() throws Exception {
        byte[] rawKey = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");

        KeyMaterial km = KeyMaterialFactory.fromSecretKey("key-to-export", keySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        km.setName("My Export Key");
        SimulatedHsmProvider.getInstance().importKey(km);

        File exportFile = File.createTempFile("key-lab-metadata", ".json");
        exportFile.deleteOnExit();

        SimulatedHsmProvider.getInstance().exportMetadata(exportFile);

        String content = Files.readString(exportFile.toPath());
        assertTrue(content.contains("My Export Key"));
        assertFalse(content.contains("keyMaterialHex"));

        SimulatedHsmProvider.getInstance().clear();
        assertNull(SimulatedHsmProvider.getInstance().getKeyMetadata("key-to-export"));

        SimulatedHsmProvider.getInstance().importMetadata(exportFile);

        KeyMaterial importedKm = SimulatedHsmProvider.getInstance().getKeyMetadata("key-to-export");
        assertNotNull(importedKm);
        assertEquals("My Export Key", importedKm.getName());
        assertEquals("ACTIVE", importedKm.getStatus());
        assertNull(importedKm.getKey());
    }

    @Test
    public void testSecretsNotPersisted() throws Exception {
        byte[] rawKey = new byte[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 11, 12, 13, 14, 15, 16};
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("secret-test-key", keySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        km.setName("My Secret Key");

        SimulatedHsmProvider.getInstance().importKey(km);

        String fileContent = Files.readString(tempFile.toPath());
        assertTrue(fileContent.contains("My Secret Key"));
        assertFalse(fileContent.contains("0A141E28323C46505A64"));
        assertFalse(fileContent.contains("keyMaterialHex"));
    }

    @Test
    public void testClearDoesNotTouchDefaultRoute() {
        String home = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
        File defaultFile = java.nio.file.Paths.get(home, ".cryptocarver", "key_lab.json").toFile();
        if (defaultFile.exists()) {
            long lastModifiedBefore = defaultFile.lastModified();
            SimulatedHsmProvider.getInstance().clear();
            assertEquals(lastModifiedBefore, defaultFile.lastModified(), "Default key_lab.json modified timestamp should not change");
        }
    }

    @Test
    public void testMetadataOnlyCannotEncryptUntilReimported() throws Exception {
        byte[] rawKey = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("ref-key", keySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT));
        km.setName("Reference Key");
        SimulatedHsmProvider.getInstance().importKey(km);

        File exportFile = File.createTempFile("metadata-ref-test", ".json");
        exportFile.deleteOnExit();
        SimulatedHsmProvider.getInstance().exportMetadata(exportFile);

        SimulatedHsmProvider.getInstance().clear();
        SimulatedHsmProvider.getInstance().importMetadata(exportFile);

        KeyMaterial refMeta = SimulatedHsmProvider.getInstance().getKeyMetadata("ref-key");
        assertNotNull(refMeta);
        assertNull(refMeta.getKey());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            SimulatedHsmProvider.getInstance().encryptSymmetric("ref-key", new byte[16], "AES", "ECB", "NoPadding", null);
        });
        assertTrue(ex.getMessage().contains("Key material is not available"));

        KeyMaterial restoredKm = KeyMaterialFactory.fromSecretKey("ref-key", keySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT));
        restoredKm.setName("Reference Key");
        SimulatedHsmProvider.getInstance().importKey(restoredKm, true);

        byte[] ciphertext = SimulatedHsmProvider.getInstance().encryptSymmetric("ref-key", new byte[16], "AES", "ECB", "NoPadding", null);
        assertNotNull(ciphertext);
    }

    @Test
    public void testLegacyJsonWithSecretsIgnoredOnImport() throws Exception {
        // Create a JSON string with legacy "keyMaterialHex"
        String legacyJson = "[" +
                "  {" +
                "    \"id\": \"legacy-key-id\"," +
                "    \"fingerprint\": \"some-fingerprint\"," +
                "    \"type\": \"SYMMETRIC\"," +
                "    \"algorithm\": \"AES\"," +
                "    \"size\": 128," +
                "    \"format\": \"RAW\"," +
                "    \"usages\": [\"ENCRYPT\"]," +
                "    \"exportability\": \"EXPORTABLE\"," +
                "    \"name\": \"Legacy Key\"," +
                "    \"origin\": \"imported\"," +
                "    \"created\": 12345678," +
                "    \"modified\": 12345678," +
                "    \"kcv\": \"AAAAAA\"," +
                "    \"status\": \"ACTIVE\"," +
                "    \"keyMaterialHex\": \"000102030405060708090a0b0c0d0e0f\"" +
                "  }" +
                "]";

        File legacyFile = File.createTempFile("legacy-import", ".json");
        legacyFile.deleteOnExit();
        Files.writeString(legacyFile.toPath(), legacyJson);

        // Import the legacy metadata manifest
        SimulatedHsmProvider.getInstance().importMetadata(legacyFile);

        // Verify the key is imported as reference-only (secrets ignored)
        KeyMaterial km = SimulatedHsmProvider.getInstance().getKeyMetadata("legacy-key-id");
        assertNotNull(km);
        assertEquals("Legacy Key", km.getName());
        assertNull(km.getKey(), "Key material must be ignored and not rehydrated");

        // Reveal exportable key should return null since no secret was loaded into memory
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        byte[] rawBytes = SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab("legacy-key-id");
        assertNull(rawBytes, "Should be null since memory key is null");

        // Try encrypting must throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            SimulatedHsmProvider.getInstance().encryptSymmetric("legacy-key-id", new byte[16], "AES", "ECB", "NoPadding", null);
        });
    }

    @Test
    public void testRevealRestrictions() throws Exception {
        byte[] rawKey = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");

        // 1. Exportable key
        KeyMaterial expKm = KeyMaterialFactory.fromSecretKey("exp-key", keySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        SimulatedHsmProvider.getInstance().importKey(expKm);

        // 2. Non-exportable key
        KeyMaterial nonExpKm = KeyMaterialFactory.fromSecretKey("non-exp-key", keySpec, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        SimulatedHsmProvider.getInstance().importKey(nonExpKm);

        // Test reveal under MASKED profile
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);
        assertThrows(SecurityException.class, () -> {
            SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab("exp-key");
        });

        // Test reveal under FULL_LAB profile
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        byte[] expBytes = SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab("exp-key");
        assertArrayEquals(rawKey, expBytes);

        // Test reveal of NON_EXPORTABLE key under FULL_LAB profile
        assertThrows(SecurityException.class, () -> {
            SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab("non-exp-key");
        });
    }

    @Test
    public void testSaveMetadataPreservesKeyMaterial() throws Exception {
        byte[] rawKey = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("preservation-key", keySpec, KeyExportability.EXPORTABLE, Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT));
        SimulatedHsmProvider.getInstance().importKey(km);

        // Update metadata using updateKeyMetadata
        SimulatedHsmProvider.getInstance().updateKeyMetadata("preservation-key", "New Preserved Name", "ACTIVE");

        // Verify metadata was updated
        KeyMaterial updated = SimulatedHsmProvider.getInstance().getKeyMetadata("preservation-key");
        assertNotNull(updated);
        assertEquals("New Preserved Name", updated.getName());

        // Verify key material is still present and encryption/decryption works
        byte[] plaintext = "hello world12345".getBytes();
        byte[] ciphertext = SimulatedHsmProvider.getInstance().encryptSymmetric("preservation-key", plaintext, "AES", "ECB", "NoPadding", null);
        byte[] decrypted = SimulatedHsmProvider.getInstance().decryptSymmetric("preservation-key", ciphertext, "AES", "ECB", "NoPadding", null);
        assertArrayEquals(plaintext, decrypted);
    }
}
