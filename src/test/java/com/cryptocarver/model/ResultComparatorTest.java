package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultComparatorTest {

    @Test
    void testEqualHexComparison() {
        ClipboardEntry e1 = new ClipboardEntry("Res1", "00112233", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Enc");
        ClipboardEntry e2 = new ClipboardEntry("Res2", "00112233", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Enc");

        ResultComparator.ComparisonDetails details = ResultComparator.compare(e1, e2, SecretVisibilityProfile.FULL_LAB);
        assertEquals(ResultComparator.Status.EQUAL, details.status());
        assertEquals(100.0, details.matchPercentage());
        assertNull(details.firstDifferenceOffset());
    }

    @Test
    void testDifferentHexComparison() {
        ClipboardEntry e1 = new ClipboardEntry("Res1", "00112233", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Enc");
        ClipboardEntry e2 = new ClipboardEntry("Res2", "00119933", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Enc");

        ResultComparator.ComparisonDetails details = ResultComparator.compare(e1, e2, SecretVisibilityProfile.FULL_LAB);
        assertEquals(ResultComparator.Status.DIFFERENT, details.status());
        assertEquals(2L, details.firstDifferenceOffset(), "First byte difference must be at index 2 (byte 2)");
        assertTrue(details.matchPercentage() < 100.0);
    }

    @Test
    void testLengthMismatchComparison() {
        ClipboardEntry e1 = new ClipboardEntry("Res1", "00112233", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Enc");
        ClipboardEntry e2 = new ClipboardEntry("Res2", "001122334455", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Enc");

        ResultComparator.ComparisonDetails details = ResultComparator.compare(e1, e2, SecretVisibilityProfile.FULL_LAB);
        assertEquals(ResultComparator.Status.DIFFERENT, details.status());
        assertEquals(4, details.length1());
        assertEquals(6, details.length2());
    }

    @Test
    void testIncompatibleFormats() {
        ClipboardEntry e1 = new ClipboardEntry("Res1", "00112233", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Enc");
        ClipboardEntry e2 = new ClipboardEntry("Res2", "-----BEGIN CERTIFICATE-----\nMII...", ClipboardEntry.Format.PEM, OperationDetail.Classification.PUBLIC, "Cert");

        ResultComparator.ComparisonDetails details = ResultComparator.compare(e1, e2, SecretVisibilityProfile.FULL_LAB);
        assertEquals(ResultComparator.Status.NOT_COMPARABLE, details.status());
        assertTrue(details.summary().contains("Incompatible formats"));
    }

    @Test
    void testRedactedSecurityProfileExcludesSecrets() {
        ClipboardEntry e1 = new ClipboardEntry("Res1", "SECRET_KEY_1", ClipboardEntry.Format.TEXT, OperationDetail.Classification.SECRET, "KeyGen");
        ClipboardEntry e2 = new ClipboardEntry("Res2", "SECRET_KEY_2", ClipboardEntry.Format.TEXT, OperationDetail.Classification.SECRET, "KeyGen");

        ResultComparator.ComparisonDetails details = ResultComparator.compare(e1, e2, SecretVisibilityProfile.REDACTED);
        assertEquals(ResultComparator.Status.NOT_COMPARABLE, details.status());
        assertEquals("[REDACTED]", details.fingerprint1());
        assertEquals("[REDACTED]", details.fingerprint2());
        assertEquals(0, details.length1(), "Redacted comparison must not disclose payload length");
        assertEquals(0, details.length2(), "Redacted comparison must not disclose payload length");
        assertFalse(details.summary().contains("SECRET_KEY"));
        assertFalse(details.textualDiff().contains("SECRET_KEY"));
    }

    @Test
    void testEquivalentBase64AndBase64UrlUseSameDecodedFingerprint() {
        ClipboardEntry b64 = new ClipboardEntry("Base64", "+w==", ClipboardEntry.Format.BASE64,
                OperationDetail.Classification.PUBLIC, "Test");
        ClipboardEntry b64Url = new ClipboardEntry("Base64URL", "-w", ClipboardEntry.Format.BASE64URL,
                OperationDetail.Classification.PUBLIC, "Test");

        ResultComparator.ComparisonDetails details = ResultComparator.compare(b64, b64Url,
                SecretVisibilityProfile.FULL_LAB);
        assertEquals(ResultComparator.Status.EQUAL, details.status());
        assertEquals(details.fingerprint1(), details.fingerprint2());
    }
}
