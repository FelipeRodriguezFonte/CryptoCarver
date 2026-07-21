package com.cryptocarver.asn1;

import org.bouncycastle.asn1.*;
import java.io.IOException;
import java.math.BigInteger;

public class ASN1Encoder {

    public static byte[] encodeInteger(String value, int radix) throws IOException {
        BigInteger bi = new BigInteger(value, radix);
        return new ASN1Integer(bi).getEncoded();
    }

    public static byte[] encodeOctetString(byte[] data) throws IOException {
        return new DEROctetString(data).getEncoded();
    }

    public static byte[] encodeUTF8String(String text) throws IOException {
        return new DERUTF8String(text).getEncoded();
    }

    public static byte[] encodePrintableString(String text) throws IOException {
        return new DERPrintableString(text).getEncoded();
    }

    public static byte[] encodeIA5String(String text) throws IOException {
        return new DERIA5String(text).getEncoded();
    }

    public static byte[] encodeOID(String oid) throws IOException {
        return new ASN1ObjectIdentifier(oid).getEncoded();
    }

    public static byte[] encodeBitString(byte[] data) throws IOException {
        return new DERBitString(data).getEncoded();
    }

    public static byte[] encodeBoolean(boolean value) throws IOException {
        return ASN1Boolean.getInstance(value).getEncoded();
    }

    public static byte[] encodeNull() throws IOException {
        return DERNull.INSTANCE.getEncoded();
    }

    /**
     * Wraps raw DER encoded objects into a SEQUENCE.
     * Use this to combine previously encoded items.
     * Content is assumed to be a valid stream of ASN.1 objects.
     */
    public static byte[] encodeSequence(byte[] content) throws IOException {
        // Create a SEQUENCE containing the raw bytes provided as content
        // Note: BouncyCastle's DERSequence expects ASN1Encodables.
        // We can parse the content back into objects to properly reconstruct the
        // sequence from individual DER blobs.

        ASN1EncodableVector vector = new ASN1EncodableVector();

        if (content != null && content.length > 0) {
            try (ASN1InputStream ais = new ASN1InputStream(content)) {
                ASN1Primitive primitive;
                while ((primitive = ais.readObject()) != null) {
                    vector.add(primitive);
                }
            }
        }

        return new DERSequence(vector).getEncoded();
    }

    /**
     * Wraps raw DER encoded objects into a SET.
     */
    public static byte[] encodeSet(byte[] content) throws IOException {
        ASN1EncodableVector vector = new ASN1EncodableVector();

        if (content != null && content.length > 0) {
            try (ASN1InputStream ais = new ASN1InputStream(content)) {
                ASN1Primitive primitive;
                while ((primitive = ais.readObject()) != null) {
                    vector.add(primitive);
                }
            }
        }

        return new DERSet(vector).getEncoded();
    }
}
