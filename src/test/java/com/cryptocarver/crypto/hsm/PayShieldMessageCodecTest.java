package com.cryptocarver.crypto.hsm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayShieldMessageCodecTest {
    private static final byte[] EMPTY = new byte[0];

    @Test
    void commandFramingSurvivesARoundTrip() {
        PayShieldMessageCodec codec = new PayShieldMessageCodec(4);
        byte[] body = "OPAQUE-BODY".getBytes(StandardCharsets.US_ASCII);
        byte[] trailer = "T1".getBytes(StandardCharsets.US_ASCII);

        byte[] encoded = codec.composeCommand("0001", "ZZ", body, trailer);

        assertEquals(new PayShieldMessage("0001", "ZZ", body, trailer),
                codec.parseCommand(encoded));
    }

    @Test
    void responseFramingWithTcpPrefixSurvivesARoundTrip() {
        PayShieldMessageCodec codec = new PayShieldMessageCodec(4, true);
        byte[] data = "OPAQUE-DATA".getBytes(StandardCharsets.US_ASCII);

        byte[] encoded = codec.composeResponse("0001", "ZZ", "00", data, EMPTY);

        assertEquals(new PayShieldResponse("0001", "ZZ", "00", data, EMPTY),
                codec.parseResponse(encoded));
        assertEquals(encoded.length - 2, ((encoded[0] & 0xff) << 8) | (encoded[1] & 0xff));
    }

    /**
     * Origin not recorded: this value was already in the tree when provenance
     * was questioned, and no capture supports it. NC-00 in the external HSM console
     * capture recipe will replace it.
     */
    @Test
    void suppliedNcValueParsesIntoPendingDeclarativeFields() {
        PayShieldMessageCodec codec = new PayShieldMessageCodec(4);
        PayShieldResponse response = codec.parseResponse(
                "0000ND007B44AC1DDEE2A94B0007-E000".getBytes(StandardCharsets.US_ASCII));

        assertEquals("0000", response.header());
        assertEquals("ND", response.responseCode());
        assertEquals("00", response.errorCode());
        PayShieldBodyDecomposer.Decomposition decomposition =
                PayShieldBodyDecomposer.decompose(response).orElseThrow();
        assertEquals("7B44AC1DDEE2A94B", decomposition.value("lmkCheckValue").orElseThrow());
        assertEquals("0007-E000", decomposition.value("firmwareVersion").orElseThrow());
    }

    @Test
    void ncCommandWithoutTrailerMatchesTheKnownRequestShape() {
        PayShieldMessageCodec codec = new PayShieldMessageCodec(4);

        byte[] encoded = codec.composeCommand("0000", "NC", EMPTY, EMPTY);

        assertArrayEquals("0000NC".getBytes(StandardCharsets.US_ASCII), encoded);
        assertEquals("NC", codec.parseCommand(encoded).code());
    }

    @Test
    void ncFieldParserRefusesToGuessFromAnUnseenShape() {
        PayShieldResponse differentShape = new PayShieldResponse(
                "0000", "ND", "00", "SHORT".getBytes(StandardCharsets.US_ASCII), EMPTY);

        assertFalse(PayShieldBodyDecomposer.decompose(differentShape).isPresent());
    }

    @Test
    void malformedFramesAreRejected() {
        PayShieldMessageCodec tcpCodec = new PayShieldMessageCodec(4, true);

        assertThrows(IllegalArgumentException.class,
                () -> tcpCodec.parseCommand(new byte[]{0, 3, '0', '0', '0'}));
        assertThrows(IllegalArgumentException.class,
                () -> tcpCodec.composeCommand("000", "NC", EMPTY, EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> tcpCodec.composeCommand("0001", "N", EMPTY, EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> tcpCodec.composeCommand("0001", "ND00", EMPTY, EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> new PayShieldMessageCodec(4).parseCommand(
                        "0001N".getBytes(StandardCharsets.US_ASCII)));
    }
}
