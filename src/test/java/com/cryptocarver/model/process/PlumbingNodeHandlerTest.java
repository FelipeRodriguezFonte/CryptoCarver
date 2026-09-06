package com.cryptocarver.model.process;

import com.cryptocarver.model.process.handlers.PlumbingNodeHandler;
import com.cryptocarver.utils.PaddingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlumbingNodeHandlerTest {

    private PlumbingNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlumbingNodeHandler();
    }

    @Test
    void testConcatKnownVectorAndGraph() throws Exception {
        byte[] a = new byte[]{0x10, 0x20};
        byte[] b = new byte[]{0x30, 0x40, 0x50};

        ProcessDefinition.Node node = new ProcessDefinition.Node("concat", "CONCAT", "CONCAT", 0, 0);
        FlowValue result = handler.execute(node, Map.of(
                "a", FlowValue.binary(a),
                "b", FlowValue.binary(b)
        ), null);

        assertArrayEquals(new byte[]{0x10, 0x20, 0x30, 0x40, 0x50}, result.bytes());

        // Graph execution
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node in1 = new ProcessDefinition.Node("in1", "CONSOLE_INPUT", "In1", 0, 0);
        in1.configuration.put("value", "Hello ");
        ProcessDefinition.Node in2 = new ProcessDefinition.Node("in2", "CONSOLE_INPUT", "In2", 0, 0);
        in2.configuration.put("value", "World");
        ProcessDefinition.Node cNode = new ProcessDefinition.Node("c", "CONCAT", "Concat", 0, 0);
        def.nodes.addAll(java.util.List.of(in1, in2, cNode));
        def.connections.add(new ProcessDefinition.Connection("in1", "c", "a"));
        def.connections.add(new ProcessDefinition.Connection("in2", "c", "b"));

        Map<String, FlowValue> graphResult = ProcessEngine.execute(def);
        assertEquals("Hello World", new String(graphResult.get("c").bytes(), StandardCharsets.UTF_8));
    }

    @Test
    void testSliceKnownVectorAndRejection() throws Exception {
        byte[] data = "CryptoCarverPlatform".getBytes(StandardCharsets.UTF_8);

        ProcessDefinition.Node node = new ProcessDefinition.Node("s", "SLICE", "Slice", 0, 0);
        node.configuration.put("offset", "6");
        node.configuration.put("length", "6");

        FlowValue result = handler.execute(node, Map.of("input", FlowValue.binary(data)), null);
        assertEquals("Carver", new String(result.bytes(), StandardCharsets.UTF_8));

        // Remainder slice (length = -1)
        node.configuration.put("offset", "12");
        node.configuration.put("length", "-1");
        FlowValue remResult = handler.execute(node, Map.of("input", FlowValue.binary(data)), null);
        assertEquals("Platform", new String(remResult.bytes(), StandardCharsets.UTF_8));

        // Safe rejection on out of bounds
        node.configuration.put("offset", "50");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(node, Map.of("input", FlowValue.binary(data)), null));

        node.configuration.put("offset", "0");
        node.configuration.put("length", "100");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(node, Map.of("input", FlowValue.binary(data)), null));

        // Preflight validation rejection
        ProcessDefinition.Node invalidNode = new ProcessDefinition.Node("inv", "SLICE", "Slice", 0, 0);
        invalidNode.configuration.put("offset", "-5");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalidNode));

        invalidNode.configuration.put("offset", "0");
        invalidNode.configuration.put("length", "-2");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalidNode));
    }

    @Test
    void testPadAndUnpadRoundTripAndPreflightRejection() throws Exception {
        byte[] raw = "TestPayload123".getBytes(StandardCharsets.UTF_8);

        for (String padType : java.util.List.of("PKCS7", "PKCS5", "ISO_9797_M2", "ISO_7816_4")) {
            ProcessDefinition.Node padNode = new ProcessDefinition.Node("p", "PAD", "Pad", 0, 0);
            padNode.configuration.put("paddingType", padType);
            padNode.configuration.put("blockSize", "16");

            ProcessDefinition.Node unpadNode = new ProcessDefinition.Node("u", "UNPAD", "Unpad", 0, 0);
            unpadNode.configuration.put("paddingType", padType);

            FlowValue padded = handler.execute(padNode, Map.of("input", FlowValue.binary(raw)), null);
            assertTrue(padded.bytes().length % 16 == 0);

            FlowValue unpadded = handler.execute(unpadNode, Map.of("input", padded), null);
            assertArrayEquals(raw, unpadded.bytes(), "Round-trip must match for " + padType);
        }

        // Preflight validation rejection for invalid block size
        ProcessDefinition.Node invalidPad = new ProcessDefinition.Node("inv", "PAD", "Pad", 0, 0);
        invalidPad.configuration.put("blockSize", "0");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalidPad));

        invalidPad.configuration.put("blockSize", "512");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalidPad));

        invalidPad.configuration.put("blockSize", "16");
        invalidPad.configuration.put("paddingType", "UNKNOWN_PAD");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalidPad));
    }

    @Test
    void testXorKnownVectorAndLengthMismatchRejection() throws Exception {
        byte[] a = HexFormat.of().parseHex("AA55AA55");
        byte[] b = HexFormat.of().parseHex("55AA55AA");

        ProcessDefinition.Node node = new ProcessDefinition.Node("xor", "XOR", "XOR", 0, 0);
        FlowValue res = handler.execute(node, Map.of(
                "a", FlowValue.binary(a),
                "b", FlowValue.binary(b)
        ), null);

        assertArrayEquals(HexFormat.of().parseHex("FFFFFFFF"), res.bytes());

        // Length mismatch rejection
        byte[] bShort = HexFormat.of().parseHex("55AA");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> handler.execute(node, Map.of(
                "a", FlowValue.binary(a),
                "b", FlowValue.binary(bShort)
        ), null));
        assertTrue(ex.getMessage().contains("same length"));
    }

    @Test
    void testAssertEqualsMatchesAndFailsWithoutLeakingData() throws Exception {
        byte[] actual = "SecretAssertionToken_123456789".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "SecretAssertionToken_123456789".getBytes(StandardCharsets.UTF_8);

        ProcessDefinition.Node node = new ProcessDefinition.Node("assert", "ASSERT_EQUALS", "Assert", 0, 0);
        FlowValue res = handler.execute(node, Map.of(
                "actual", FlowValue.binary(actual),
                "expected", FlowValue.binary(expected)
        ), null);
        assertArrayEquals(actual, res.bytes());

        // Mismatch test
        byte[] different = "SecretAssertionToken_DIFFERENT".getBytes(StandardCharsets.UTF_8);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> handler.execute(node, Map.of(
                "actual", FlowValue.binary(actual),
                "expected", FlowValue.binary(different)
        ), null));

        // Verify the exception message does NOT leak the secret tokens
        assertFalse(ex.getMessage().contains("SecretAssertionToken"),
                "Assertion failed message must not leak token content!");
        assertTrue(ex.getMessage().contains("Assertion failed"));
    }
}
