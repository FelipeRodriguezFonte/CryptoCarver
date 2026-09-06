package com.cryptocarver.model.process;

import com.cryptocarver.model.process.handlers.EncodingFormatNodeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodingFormatNodeHandlerTest {

    private EncodingFormatNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EncodingFormatNodeHandler();
    }

    @Test
    void testBase32RoundTripAndGraph() throws Exception {
        byte[] payload = "Base32TestVector123".getBytes(StandardCharsets.UTF_8);

        ProcessDefinition.Node enc = new ProcessDefinition.Node("e", "BASE32_ENCODE", "Enc", 0, 0);
        ProcessDefinition.Node dec = new ProcessDefinition.Node("d", "BASE32_DECODE", "Dec", 0, 0);

        FlowValue encoded = handler.execute(enc, Map.of("input", FlowValue.binary(payload)), null);
        assertNotNull(encoded.render());
        assertTrue(encoded.render().length() > 0);

        FlowValue decoded = handler.execute(dec, Map.of("input", encoded), null);
        assertArrayEquals(payload, decoded.bytes());

        // Graph execution
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "In", 0, 0);
        in.configuration.put("value", "Hello Base32");
        def.nodes.addAll(List.of(in, enc, dec));
        def.connections.add(new ProcessDefinition.Connection("in", "e", "input"));
        def.connections.add(new ProcessDefinition.Connection("e", "d", "input"));

        Map<String, FlowValue> graphRes = ProcessEngine.execute(def);
        assertEquals("Hello Base32", new String(graphRes.get("d").bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void testBase58RoundTrip() throws Exception {
        byte[] payload = "Base58PayloadData".getBytes(StandardCharsets.UTF_8);

        ProcessDefinition.Node enc = new ProcessDefinition.Node("e", "BASE58_ENCODE", "Enc", 0, 0);
        ProcessDefinition.Node dec = new ProcessDefinition.Node("d", "BASE58_DECODE", "Dec", 0, 0);

        FlowValue encoded = handler.execute(enc, Map.of("input", FlowValue.binary(payload)), null);
        FlowValue decoded = handler.execute(dec, Map.of("input", encoded), null);
        assertArrayEquals(payload, decoded.bytes());
    }

    @Test
    void testBase58CheckRoundTrip() throws Exception {
        byte[] payload = "Base58CheckWithChecksum".getBytes(StandardCharsets.UTF_8);

        ProcessDefinition.Node enc = new ProcessDefinition.Node("e", "BASE58CHECK_ENCODE", "Enc", 0, 0);
        ProcessDefinition.Node dec = new ProcessDefinition.Node("d", "BASE58CHECK_DECODE", "Dec", 0, 0);

        FlowValue encoded = handler.execute(enc, Map.of("input", FlowValue.binary(payload)), null);
        FlowValue decoded = handler.execute(dec, Map.of("input", encoded), null);
        assertArrayEquals(payload, decoded.bytes());
    }

    @Test
    void testEbcdicRoundTripAndPreflightRejection() throws Exception {
        String msg = "HELLO IBM MAINFRAME";

        ProcessDefinition.Node enc = new ProcessDefinition.Node("e", "EBCDIC_ENCODE", "Enc", 0, 0);
        enc.configuration.put("codePage", "IBM284 — Spain");

        ProcessDefinition.Node dec = new ProcessDefinition.Node("d", "EBCDIC_DECODE", "Dec", 0, 0);
        dec.configuration.put("codePage", "IBM284 — Spain");

        FlowValue encoded = handler.execute(enc, Map.of("input", FlowValue.text(msg, StandardCharsets.UTF_8)), null);
        FlowValue decoded = handler.execute(dec, Map.of("input", encoded), null);
        assertEquals(msg, decoded.render());

        // Preflight validation rejection for invalid code page
        ProcessDefinition.Node invalid = new ProcessDefinition.Node("inv", "EBCDIC_ENCODE", "Inv", 0, 0);
        invalid.configuration.put("codePage", "NON_EXISTENT_CODEPAGE");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));
    }

    @Test
    void testCompressDecompressRoundTripAndPreflightRejection() throws Exception {
        byte[] data = "Repeated data for compression testing: 1234567890 1234567890 1234567890".getBytes(StandardCharsets.UTF_8);

        for (String fmt : List.of("gzip", "zlib", "deflate")) {
            ProcessDefinition.Node comp = new ProcessDefinition.Node("c", "COMPRESS", "Comp", 0, 0);
            comp.configuration.put("format", fmt);
            ProcessDefinition.Node decomp = new ProcessDefinition.Node("d", "DECOMPRESS", "Decomp", 0, 0);
            decomp.configuration.put("format", fmt);

            FlowValue compressed = handler.execute(comp, Map.of("input", FlowValue.binary(data)), null);
            FlowValue decompressed = handler.execute(decomp, Map.of("input", compressed), null);
            assertArrayEquals(data, decompressed.bytes());
        }

        // Preflight validation rejection
        ProcessDefinition.Node invalid = new ProcessDefinition.Node("inv", "COMPRESS", "Inv", 0, 0);
        invalid.configuration.put("format", "bzip2");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));
    }

    @Test
    void testCharsetConvertAndPreflightRejection() throws Exception {
        String original = "España: Olé y éxito";
        byte[] isoBytes = original.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        ProcessDefinition.Node node = new ProcessDefinition.Node("cc", "CHARSET_CONVERT", "Charset", 0, 0);
        node.configuration.put("sourceCharset", "ISO-8859-1");
        node.configuration.put("targetCharset", "UTF-8");

        FlowValue result = handler.execute(node, Map.of("input", FlowValue.binary(isoBytes)), null);
        assertEquals(original, result.render());

        // Preflight validation rejection for invalid charsets
        ProcessDefinition.Node invalid = new ProcessDefinition.Node("inv", "CHARSET_CONVERT", "Inv", 0, 0);
        invalid.configuration.put("sourceCharset", "INVALID_CHARSET_NAME_XYZ");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));
    }
}
