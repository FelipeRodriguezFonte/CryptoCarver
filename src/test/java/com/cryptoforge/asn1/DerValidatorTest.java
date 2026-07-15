package com.cryptoforge.asn1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DerValidatorTest {
    @Test void acceptsCanonicalDerInteger() {
        assertTrue(DerValidator.validate(new byte[] { 0x02, 0x01, 0x01 }).validDer());
    }
    @Test void rejectsNonCanonicalLongFormLength() {
        DerValidator.Report report = DerValidator.validate(new byte[] { 0x02, (byte) 0x81, 0x01, 0x01 });
        assertFalse(report.validDer());
    }
    @Test void rejectsTrailingObject() {
        assertFalse(DerValidator.validate(new byte[] { 0x02, 0x01, 0x01, 0x05, 0x00 }).validDer());
    }
    @Test void canonicalizesBerLengthForm() throws Exception {
        assertArrayEquals(new byte[] { 0x02, 0x01, 0x01 }, DerValidator.canonicalize(new byte[] { 0x02, (byte) 0x81, 0x01, 0x01 }));
    }
}
