package com.cryptocarver.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;

import static org.junit.jupiter.api.Assertions.*;

public class UserFacingErrorMapperTest {

    @Test
    @DisplayName("AEADBadTagException is mapped to clear authentication tag error and remedy")
    void testAEADBadTagExceptionMapping() {
        AEADBadTagException ex = new AEADBadTagException("Tag mismatch");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Decryption Error", "gcmTagField");

        assertEquals("Authentication Tag Verification Failed", error.title());
        assertFalse(error.title().contains("AEADBadTagException"));
        assertTrue(error.remedy().toLowerCase().contains("check that the key"));
        assertEquals("gcmTagField", error.fieldKey());
        assertSame(ex, error.cause());
    }

    @Test
    @DisplayName("GCM tag corrupt regression produces user-friendly message, not exception class name")
    void testGcmTagCorruptRegression() {
        RuntimeException ex = new RuntimeException("mac check in GCM failed");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Decryption Error", "tag");

        assertEquals("Authentication Tag Verification Failed", error.title());
        assertFalse(error.detail().startsWith("java.lang.RuntimeException"));
        assertTrue(error.remedy().contains("AAD"));
        assertEquals("tag", error.fieldKey());
    }

    @Test
    @DisplayName("BadPaddingException is mapped to padding error and key/IV remedy")
    void testBadPaddingExceptionMapping() {
        BadPaddingException ex = new BadPaddingException("Given final block not properly padded");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Cipher Error", "cipherInputArea");

        assertEquals("Decryption / Padding Error", error.title());
        assertTrue(error.remedy().contains("padding mode"));
        assertEquals("cipherInputArea", error.fieldKey());
    }

    @Test
    @DisplayName("InvalidKeyException is mapped to key parameter error and length remedy")
    void testInvalidKeyExceptionMapping() {
        InvalidKeyException ex = new InvalidKeyException("Invalid AES key length: 10 bytes");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Encryption Error", "symmetricKeyField");

        assertEquals("Invalid Key Parameter", error.title());
        assertTrue(error.remedy().contains("128, 192, or 256 bits"));
        assertEquals("symmetricKeyField", error.fieldKey());
    }

    @Test
    @DisplayName("IllegalArgumentException with hex format message is mapped to hex error")
    void testHexIllegalArgumentExceptionMapping() {
        IllegalArgumentException ex = new IllegalArgumentException("Input contains invalid hex string with odd number of digits");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Validation Error", "input");

        assertEquals("Invalid Hexadecimal Format", error.title());
        assertTrue(error.remedy().contains("hexadecimal characters"));
        assertEquals("input", error.fieldKey());
    }

    @Test
    @DisplayName("IllegalArgumentException with base64 message is mapped to base64 error")
    void testBase64IllegalArgumentExceptionMapping() {
        IllegalArgumentException ex = new IllegalArgumentException("Illegal base64 character 3a");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Validation Error", "input");

        assertEquals("Invalid Base64 Format", error.title());
        assertTrue(error.remedy().contains("Base64 string"));
        assertEquals("input", error.fieldKey());
    }

    @Test
    @DisplayName("CertificateException is mapped to certificate/key parsing error")
    void testCertificateExceptionMapping() {
        CertificateException ex = new CertificateException("Could not parse certificate: PEM header missing");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Parse Error", "certInputArea");

        assertEquals("Invalid Certificate or Key Format", error.title());
        assertTrue(error.remedy().contains("X.509 PEM or DER"));
        assertEquals("certInputArea", error.fieldKey());
    }

    @Test
    @DisplayName("SocketTimeoutException for TSA is mapped to timestamp authority error")
    void testTsaTimeoutMapping() {
        SocketTimeoutException ex = new SocketTimeoutException("Read timed out from TSA server");
        UserFacingError error = UserFacingErrorMapper.map(ex, "Timestamp Error", null);

        assertEquals("Timestamp Authority Error", error.title());
        assertTrue(error.remedy().contains("TSA server"));
        assertEquals("tsaUrl", error.fieldKey());
    }

    @Test
    @DisplayName("String title/message overload maps GCM and padding errors cleanly")
    void testStringOverloadMapping() {
        UserFacingError gcmError = UserFacingErrorMapper.map("Decryption Failed", "GCM tag mismatch detected", "gcmTagField");
        assertEquals("Authentication Tag Verification Failed", gcmError.title());
        assertEquals("gcmTagField", gcmError.fieldKey());

        UserFacingError generalError = UserFacingErrorMapper.map("Custom Title", "Custom detail message", "customField");
        assertEquals("Custom Title", generalError.title());
        assertEquals("customField", generalError.fieldKey());
    }
}
