package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationFormatRegistryTest {

    @Test
    void testHashingProfile() {
        OperationFormatProfile profile = OperationFormatRegistry.getInstance().getProfile("Hashing");
        assertNotNull(profile);
        assertEquals("Hashing", profile.operationPath());
        assertEquals("Text (UTF-8)", profile.defaultInputFormat());
        assertEquals("Hexadecimal", profile.defaultOutputFormat());
        assertTrue(profile.allowedInputFormats().contains("Hexadecimal"));
        assertTrue(profile.allowedOutputFormats().contains("Base64"));
        assertEquals(DataType.BYTES, profile.inputDataType());
        assertEquals(DataType.BYTES, profile.outputDataType());
    }

    @Test
    void testFallbackProfile() {
        OperationFormatProfile profile = OperationFormatRegistry.getInstance().getProfile("Unknown Operation XYZ");
        assertNotNull(profile);
        assertEquals("Fallback", profile.operationPath());
        assertTrue(profile.allowedInputFormats().isEmpty());
    }

    @Test
    void testFileCipherProfile() {
        OperationFormatProfile profile = OperationFormatRegistry.getInstance().getProfile("File Cipher (Streaming)");
        assertNotNull(profile);
        assertEquals("File Cipher (Streaming)", profile.operationPath());
        assertTrue(profile.allowedInputFormats().isEmpty());
        assertTrue(profile.allowedOutputFormats().isEmpty());
        assertNull(profile.defaultInputFormat());
        assertNull(profile.defaultOutputFormat());
        assertEquals(DataType.FILE, profile.inputDataType());
        assertEquals(DataType.FILE, profile.outputDataType());
    }
}
