package com.cryptocarver.asn1;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

/** Validates that one ASN.1 object is fully consumed and encoded canonically as DER. */
public final class DerValidator {
    private DerValidator() { }

    public record Report(boolean validDer, String message, int inputBytes, int canonicalBytes) { }

    public static Report validate(byte[] input) {
        if (input == null || input.length == 0) return new Report(false, "Input is empty", 0, 0);
        try (ASN1InputStream stream = new ASN1InputStream(new ByteArrayInputStream(input))) {
            ASN1Primitive object = stream.readObject();
            if (object == null) return new Report(false, "No ASN.1 object found", input.length, 0);
            if (stream.readObject() != null) return new Report(false, "Trailing ASN.1 object or bytes found", input.length, 0);
            byte[] canonical = object.getEncoded("DER");
            if (!Arrays.equals(input, canonical)) {
                return new Report(false, "Valid BER but not canonical DER", input.length, canonical.length);
            }
            return new Report(true, "Canonical DER", input.length, canonical.length);
        } catch (Exception e) {
            return new Report(false, "Invalid ASN.1: " + e.getMessage(), input.length, 0);
        }
    }

    /** Re-encodes exactly one ASN.1 object as canonical DER; rejects trailing objects. */
    public static byte[] canonicalize(byte[] input) throws Exception {
        if (input == null || input.length == 0) throw new IllegalArgumentException("Input is empty");
        try (ASN1InputStream stream = new ASN1InputStream(new ByteArrayInputStream(input))) {
            ASN1Primitive object = stream.readObject();
            if (object == null || stream.readObject() != null) throw new IllegalArgumentException("Input must contain exactly one ASN.1 object");
            return object.getEncoded("DER");
        }
    }
}
