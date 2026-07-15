package com.cryptoforge.crypto.hsm;

import org.junit.jupiter.api.Test;
import javax.crypto.spec.SecretKeySpec;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class KeyMaterialFactoryTest {

    @Test
    void testFromSecretKey() {
        byte[] keyBytes = new byte[32];
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        
        KeyMaterial km = KeyMaterialFactory.fromSecretKey(
                "my-aes-key", 
                secretKey, 
                KeyExportability.NON_EXPORTABLE, 
                Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
        );
        
        assertEquals("my-aes-key", km.getId());
        assertEquals(KeyType.SYMMETRIC, km.getType());
        assertEquals("AES", km.getAlgorithm());
        assertEquals(256, km.getSize());
        assertEquals(KeyFormat.RAW, km.getFormat());
        assertTrue(km.getUsages().contains(KeyUsage.ENCRYPT));
        assertEquals(KeyExportability.NON_EXPORTABLE, km.getExportability());
        assertNotNull(km.getKey());
        assertNull(km.getCertificate());
    }
}
