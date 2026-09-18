package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CborInspectorTest {

    /**
     * Vectors from RFC 8949 Appendix A. They pin the decoder against the
     * specification rather than against itself, which is the point of using
     * published vectors at all.
     */
    @Test
    void decodesRfc8949AppendixAVectors() {
        assertEquals("0", CborInspector.diagnostic(CborInspector.parseHex("00")));
        assertEquals("-1", CborInspector.diagnostic(CborInspector.parseHex("20")));
        assertEquals("1000000", CborInspector.diagnostic(CborInspector.parseHex("1a000f4240")));
        assertEquals("\"IETF\"", CborInspector.diagnostic(CborInspector.parseHex("6449455446")));
        assertEquals("h'01020304'", CborInspector.diagnostic(CborInspector.parseHex("4401020304")));
        assertEquals("[1, 2, 3]", CborInspector.diagnostic(CborInspector.parseHex("83010203")));
        assertEquals("true", CborInspector.diagnostic(CborInspector.parseHex("f5")));
        assertEquals("null", CborInspector.diagnostic(CborInspector.parseHex("f6")));
        assertEquals("{\"a\": 1, \"b\": [2, 3]}",
                CborInspector.diagnostic(CborInspector.parseHex("a26161016162820203")));
    }

    /**
     * Tag 24 — "Encoded CBOR data item" (RFC 8949 §3.4.5.1) — is the reason this
     * class exists. mdoc wraps every {@code IssuerSignedItem} in one so the
     * digest covers exact bytes, and a viewer that reports "byte string, 9
     * bytes" and stops is no help at all. The diagnostic notation marks the
     * embedded item with {@code <<...>>}.
     */
    @Test
    void followsTag24IntoEmbeddedCbor() {
        byte[] inner = CborInspector.fromJson("{\"digestID\":3,\"elementIdentifier\":\"family_name\"}");
        byte[] wrapped = CborInspector.wrapAsEncodedCbor(inner);

        String diagnostic = CborInspector.diagnostic(wrapped);
        assertTrue(diagnostic.startsWith("24(<<"), diagnostic);
        assertTrue(diagnostic.contains("\"family_name\""),
                "The embedded item must be decoded, not printed as opaque bytes: " + diagnostic);

        String tree = CborInspector.tree(wrapped);
        assertTrue(tree.contains("encoded-CBOR"), tree);
        assertTrue(tree.contains("elementIdentifier"), tree);

        assertArrayEquals(inner, CborInspector.unwrapEncodedCbor(wrapped));
    }

    @Test
    void refusesToUnwrapSomethingThatIsNotTag24() {
        byte[] plain = CborInspector.fromJson("[1,2,3]");
        assertThrows(IllegalArgumentException.class, () -> CborInspector.unwrapEncodedCbor(plain));
    }

    @Test
    void treeShowsStructureAndSizes() {
        byte[] cbor = CborInspector.fromJson(
                "{\"a\":\"x\",\"b\":[1,2],\"c\":{\"d\":true}}");
        String tree = CborInspector.tree(cbor);

        assertTrue(tree.contains("map (3)"), tree);
        assertTrue(tree.contains("array (2)"), tree);
        assertTrue(tree.contains("[0]: "), tree);
        assertTrue(tree.contains("d: true"), tree);
    }

    @Test
    void longByteStringsAreTruncatedInTheTree() {
        StringBuilder hex = new StringBuilder("58ff"); // byte string, 255 bytes
        hex.append("ab".repeat(255));
        String tree = CborInspector.tree(CborInspector.parseHex(hex.toString()));

        assertTrue(tree.contains("bytes (255)"), tree);
        assertTrue(tree.contains("+223 bytes"), "Only the first 32 bytes are shown: " + tree);
    }

    @Test
    void summaryNamesTheTopLevelItem() {
        assertTrue(CborInspector.summary(CborInspector.fromJson("{\"a\":1}")).contains("map (1 entries)"));
        assertTrue(CborInspector.summary(CborInspector.fromJson("[1,2,3]")).contains("array (3 items)"));
        assertTrue(CborInspector.summary(CborInspector.wrapAsEncodedCbor(CborInspector.fromJson("1")))
                .contains("tagged"));
    }

    @Test
    void jsonRoundTripsForStructuresJsonCanExpress() {
        byte[] cbor = CborInspector.fromJson("{\"a\":1,\"b\":[true,null,\"x\"]}");
        assertEquals("{\"a\":1,\"b\":[true,null,\"x\"]}", CborInspector.toJson(cbor));
    }

    @Test
    void hexInputToleratesTheWayPeopleActuallyPasteIt() {
        byte[] expected = {(byte) 0x83, 0x01, 0x02, 0x03};
        assertArrayEquals(expected, CborInspector.parseHex("83 01 02 03"));
        assertArrayEquals(expected, CborInspector.parseHex("0x83010203"));
        assertArrayEquals(expected, CborInspector.parseHex("8301\n0203"));
        assertThrows(IllegalArgumentException.class, () -> CborInspector.parseHex("830102030"));
    }

    /** COSE labels its headers with small integers, so a map keyed by integers
     *  is the ordinary case there rather than an edge case: {1: -7} is
     *  "alg: ES256". The tree has to label those keys, not skip them. */
    @Test
    void mapKeysThatAreNotTextAreStillLabelled() {
        byte[] cbor = CborInspector.parseHex("a2012604450102030405");
        String tree = CborInspector.tree(cbor);
        assertTrue(tree.contains("1: "), tree);
        assertTrue(tree.contains("4: "), tree);
        assertTrue(tree.contains("bytes (5)"), tree);
    }

    /** A map that announces two entries and carries one is corrupt. An inspector
     *  must say so rather than render half a tree and let the reader believe it. */
    @Test
    void aTruncatedItemFailsLoudly() {
        byte[] truncated = CborInspector.parseHex("a20126");
        assertThrows(Exception.class, () -> CborInspector.tree(truncated));
    }
}
