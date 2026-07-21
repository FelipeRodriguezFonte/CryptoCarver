package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;

class CompressionCodecTest {
    @Test
    void roundTripsSupportedFormats() throws Exception {
        byte[] input = "CryptoCarver compression test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (String format : new String[] {"gzip", "zlib", "deflate"}) {
            assertArrayEquals(input, CompressionCodec.decompress(CompressionCodec.compress(input, format), format));
        }
    }
}
