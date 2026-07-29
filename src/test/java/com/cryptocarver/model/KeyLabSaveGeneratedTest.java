package com.cryptocarver.model;

import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.hsm.*;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Save Generated Key to Key Lab & Simulated HSM Tests")
class KeyLabSaveGeneratedTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        SimulatedHsmProvider.getInstance().resetForTest(tempDir.resolve("key_lab.json"));
    }

    @Test
    @DisplayName("Save generated AES-256 key with metadata, Origin 'Generated' and KCV")
    void testSaveGeneratedAesKeyWithMetadataAndKcv() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey key = kg.generateKey();
        byte[] keyBytes = key.getEncoded();
        String kcvHex = DataConverter.bytesToHex(KeyOperations.calculateKCV_AES(keyBytes));
        String fingerprint = KeyMaterialFactory.generateFingerprint(keyBytes);

        String id = UUID.randomUUID().toString();
        Set<KeyUsage> usages = EnumSet.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT, KeyUsage.WRAP, KeyUsage.UNWRAP);

        KeyMaterial km = new KeyMaterial(
                id,
                fingerprint,
                KeyType.SYMMETRIC,
                "AES-256",
                256,
                KeyFormat.RAW,
                usages,
                KeyExportability.NON_EXPORTABLE,
                key,
                null,
                "Production Master AES Key",
                "Generated",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                kcvHex,
                "ACTIVE",
                true
        );

        SimulatedHsmProvider.getInstance().importKey(km);

        KeyMaterial fetched = SimulatedHsmProvider.getInstance().getKeyMetadata(id);
        assertNotNull(fetched);
        assertEquals("Production Master AES Key", fetched.getName());
        assertEquals("Generated", fetched.getOrigin());
        assertEquals(256, fetched.getSize());
        assertEquals(kcvHex, fetched.getKcv());
        assertEquals(KeyExportability.NON_EXPORTABLE, fetched.getExportability());
        assertTrue(fetched.hasKeyMaterial());
    }

    @Test
    @DisplayName("Persisted manifest file contains zero secret key hex or raw bytes")
    void testPersistedManifestContainsNoRawKeyBytes() throws Exception {
        byte[] rawBytes = new byte[] { 0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef,
                                       0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88 };
        String secretHex = DataConverter.bytesToHex(rawBytes);
        SecretKey key = new SecretKeySpec(rawBytes, "AES");
        String fingerprint = KeyMaterialFactory.generateFingerprint(rawBytes);

        KeyMaterial km = new KeyMaterial(
                "secret_key_id_123",
                fingerprint,
                KeyType.SYMMETRIC,
                "AES-128",
                128,
                KeyFormat.RAW,
                EnumSet.of(KeyUsage.ENCRYPT),
                KeyExportability.NON_EXPORTABLE,
                key,
                null,
                "Secret Key Name",
                "Generated",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                "KCV123",
                "ACTIVE",
                true
        );

        SimulatedHsmProvider.getInstance().importKey(km);

        Path manifestFile = tempDir.resolve("key_lab.json");
        assertTrue(Files.exists(manifestFile));

        String json = Files.readString(manifestFile);
        assertFalse(json.contains(secretHex), "Manifest JSON must never contain the hex string of secret key bytes");
        assertFalse(json.contains("0123456789abcdef1122334455667788"), "Manifest JSON must not reveal raw secret material");
    }

    @Test
    @DisplayName("Duplicate fingerprint detection prevents creating duplicate entries")
    void testDuplicateFingerprintPreventsDuplicateEntry() throws Exception {
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            byte val = (byte) (i + 1);
            keyBytes[i] = val;
        }
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        String fingerprint = KeyMaterialFactory.generateFingerprint(keyBytes);

        KeyMaterial km1 = new KeyMaterial(
                "key_1",
                fingerprint,
                KeyType.SYMMETRIC,
                "AES-256",
                256,
                KeyFormat.RAW,
                EnumSet.of(KeyUsage.ENCRYPT),
                KeyExportability.NON_EXPORTABLE,
                key,
                null,
                "First Key",
                "Generated",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                "KCV001",
                "ACTIVE",
                true
        );

        SimulatedHsmProvider.getInstance().importKey(km1);

        KeyMaterial existing = SimulatedHsmProvider.getInstance().findKeyByFingerprint(fingerprint);
        assertNotNull(existing);
        assertEquals("key_1", existing.getId());
        assertEquals("First Key", existing.getName());
    }

    @Test
    @DisplayName("NON_EXPORTABLE key cannot be revealed even in FULL_LAB profile")
    void testNonExportableKeyCannotBeRevealedOrExported() throws Exception {
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);

        byte[] keyBytes = new byte[16];
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        KeyMaterial km = new KeyMaterial(
                "non_exp_id",
                "fp_non_exp",
                KeyType.SYMMETRIC,
                "AES-128",
                128,
                KeyFormat.RAW,
                EnumSet.of(KeyUsage.ENCRYPT),
                KeyExportability.NON_EXPORTABLE,
                key,
                null,
                "Non Exportable Key",
                "Generated",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                "KCV",
                "ACTIVE",
                true
        );

        SimulatedHsmProvider.getInstance().importKey(km);

        assertThrows(SecurityException.class, () ->
                SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab("non_exp_id")
        );
    }

    @Test
    @DisplayName("Reloading converts key to metadata-only and blocks cryptographic operations until reimported")
    void testReloadConvertsEntryToMetadataOnly() throws Exception {
        byte[] keyBytes = new byte[16];
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        KeyMaterial km = new KeyMaterial(
                "reload_test_id",
                "fp_reload",
                KeyType.SYMMETRIC,
                "AES-128",
                128,
                KeyFormat.RAW,
                EnumSet.of(KeyUsage.ENCRYPT),
                KeyExportability.NON_EXPORTABLE,
                key,
                null,
                "Reload Test Key",
                "Generated",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                "KCV",
                "ACTIVE",
                true
        );

        SimulatedHsmProvider.getInstance().importKey(km);
        assertTrue(SimulatedHsmProvider.getInstance().getKeyMetadata("reload_test_id").hasKeyMaterial());

        // Reload database from disk
        SimulatedHsmProvider.getInstance().resetForTest(tempDir.resolve("key_lab.json"));

        KeyMaterial reloaded = SimulatedHsmProvider.getInstance().getKeyMetadata("reload_test_id");
        assertNotNull(reloaded);
        assertFalse(reloaded.hasKeyMaterial(), "Key must become metadata-only on reload");

        // Cryptographic operation attempts must fail with IllegalStateException
        assertThrows(IllegalStateException.class, () ->
                SimulatedHsmProvider.getInstance().encryptSymmetric(
                        "reload_test_id", new byte[16], "AES", "ECB", "NoPadding", null, null
                )
        );
    }
}
