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
    void testUsageEnforcement() {
        SecretKeySpec secretKey = new SecretKeySpec(new byte[16], "AES");
        KeyMaterial km = KeyMaterialFactory.fromSecretKey("key-enc", secretKey, KeyExportability.NON_EXPORTABLE, Set.of(KeyUsage.ENCRYPT));
        hsm.importKey(km);
        
        assertThrows(IllegalStateException.class, () -> hsm.getRawKeyForInternalUse("key-enc", KeyUsage.SIGN));
        assertNotNull(hsm.getRawKeyForInternalUse("key-enc", KeyUsage.ENCRYPT));
    }
}
