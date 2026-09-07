package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.NodeExecutionState;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.Representation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDesignerTraceRedactionTest {

    private SecretVisibilityProfile originalProfile;
    private ProcessDesignerController controller;

    @BeforeEach
    void setUp() {
        originalProfile = AppSettings.getInstance().getSecretVisibilityProfile();
        controller = new ProcessDesignerController();
    }

    @AfterEach
    void tearDown() {
        AppSettings.getInstance().setSecretVisibilityProfile(originalProfile);
    }

    @Test
    void testTraceRedactionUnderFullLab() {
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);

        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node encNode = new ProcessDefinition.Node("node1", "ENCRYPT", "Encrypt Node", 0, 0);
        encNode.configuration.put("key", "00112233445566778899AABBCCDDEEFF");
        encNode.configuration.put("nonce", "A1B2C3D4E5F60708");
        encNode.configuration.put("keyFormat", "HEX");
        def.nodes.add(encNode);

        ProcessDefinition.Node keyNode = new ProcessDefinition.Node("node2", "AES_KEY_GENERATE", "Key Gen", 0, 0);
        def.nodes.add(keyNode);

        byte[] genBytes = HexFormat.of().parseHex("CAFEBABE11223344556677889900AABB");
        Map<String, FlowValue> result = Map.of(
                "node1", FlowValue.binary(new byte[]{1, 2, 3}),
                "node2", FlowValue.binary(genBytes)
        );

        List<NodeExecutionEvent> events = List.of(
                new NodeExecutionEvent("node1", 1, "Encrypt Node", "ENCRYPT", NodeExecutionState.SUCCESS,
                        Duration.ofMillis(10), Representation.TEXT_UTF8, 5, Representation.BINARY, 3, "OK"),
                new NodeExecutionEvent("node2", 2, "Key Gen", "AES_KEY_GENERATE", NodeExecutionState.SUCCESS,
                        Duration.ofMillis(5), null, 0, Representation.HEX, 16, "OK")
        );

        String trace = controller.renderExecutionResult(def, result, events, null);

        // FULL_LAB shows secrets in clear text
        assertTrue(trace.contains("00112233445566778899AABBCCDDEEFF"), "FULL_LAB must show clear key");
        assertTrue(trace.contains("A1B2C3D4E5F60708"), "FULL_LAB must show clear nonce");
        assertTrue(trace.contains("CAFEBABE11223344556677889900AABB"), "FULL_LAB must show clear generated material");
        assertFalse(trace.contains("***MASKED***"), "FULL_LAB must not contain MASKED indicator");
    }

    @Test
    void testTraceRedactionUnderMasked() {
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);

        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node encNode = new ProcessDefinition.Node("node1", "ENCRYPT", "Encrypt Node", 0, 0);
        encNode.configuration.put("key", "00112233445566778899AABBCCDDEEFF");
        encNode.configuration.put("nonce", "A1B2C3D4E5F60708");
        encNode.configuration.put("keyFormat", "HEX");
        def.nodes.add(encNode);

        ProcessDefinition.Node keyNode = new ProcessDefinition.Node("node2", "AES_KEY_GENERATE", "Key Gen", 0, 0);
        def.nodes.add(keyNode);

        byte[] genBytes = HexFormat.of().parseHex("CAFEBABE11223344556677889900AABB");
        Map<String, FlowValue> result = Map.of(
                "node1", FlowValue.binary(new byte[]{1, 2, 3}),
                "node2", FlowValue.binary(genBytes)
        );

        List<NodeExecutionEvent> events = List.of(
                new NodeExecutionEvent("node1", 1, "Encrypt Node", "ENCRYPT", NodeExecutionState.SUCCESS,
                        Duration.ofMillis(10), Representation.TEXT_UTF8, 5, Representation.BINARY, 3, "OK"),
                new NodeExecutionEvent("node2", 2, "Key Gen", "AES_KEY_GENERATE", NodeExecutionState.SUCCESS,
                        Duration.ofMillis(5), null, 0, Representation.HEX, 16, "OK")
        );

        String trace = controller.renderExecutionResult(def, result, events, null);

        // MASKED replaces secret values with ***MASKED***
        assertFalse(trace.contains("00112233445566778899AABBCCDDEEFF"), "MASKED must not show clear key");
        assertFalse(trace.contains("A1B2C3D4E5F60708"), "MASKED must not show clear nonce");
        assertFalse(trace.contains("CAFEBABE11223344556677889900AABB"), "MASKED must not show clear generated material");
        assertTrue(trace.contains("key (HEX): ***MASKED***"), "MASKED must mask key");
        assertTrue(trace.contains("IV/nonce (HEX): ***MASKED***"), "MASKED must mask nonce");
        assertTrue(trace.contains("generated material (HEX): ***MASKED***"), "MASKED must mask generated material");
    }

    @Test
    void testTraceRedactionUnderRedacted() {
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);

        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node encNode = new ProcessDefinition.Node("node1", "ENCRYPT", "Encrypt Node", 0, 0);
        encNode.configuration.put("key", "00112233445566778899AABBCCDDEEFF");
        encNode.configuration.put("nonce", "A1B2C3D4E5F60708");
        encNode.configuration.put("keyFormat", "HEX");
        def.nodes.add(encNode);

        ProcessDefinition.Node keyNode = new ProcessDefinition.Node("node2", "AES_KEY_GENERATE", "Key Gen", 0, 0);
        def.nodes.add(keyNode);

        byte[] genBytes = HexFormat.of().parseHex("CAFEBABE11223344556677889900AABB");
        Map<String, FlowValue> result = Map.of(
                "node1", FlowValue.binary(new byte[]{1, 2, 3}),
                "node2", FlowValue.binary(genBytes)
        );

        List<NodeExecutionEvent> events = List.of(
                new NodeExecutionEvent("node1", 1, "Encrypt Node", "ENCRYPT", NodeExecutionState.SUCCESS,
                        Duration.ofMillis(10), Representation.TEXT_UTF8, 5, Representation.BINARY, 3, "OK"),
                new NodeExecutionEvent("node2", 2, "Key Gen", "AES_KEY_GENERATE", NodeExecutionState.SUCCESS,
                        Duration.ofMillis(5), null, 0, Representation.HEX, 16, "OK")
        );

        String trace = controller.renderExecutionResult(def, result, events, null);

        // REDACTED omits lines completely
        assertFalse(trace.contains("00112233445566778899AABBCCDDEEFF"), "REDACTED must not show clear key");
        assertFalse(trace.contains("A1B2C3D4E5F60708"), "REDACTED must not show clear nonce");
        assertFalse(trace.contains("CAFEBABE11223344556677889900AABB"), "REDACTED must not show clear generated material");
        assertFalse(trace.contains("key (HEX)"), "REDACTED must omit key line completely");
        assertFalse(trace.contains("IV/nonce (HEX)"), "REDACTED must omit nonce line completely");
        assertFalse(trace.contains("generated material (HEX)"), "REDACTED must omit generated material line completely");
    }

    @Test
    void paymentPinBlockOutputIsRedactedUnderMaskedAndRedacted() throws Exception {
        String pan = "4761739001010010";
        String pin = "1234";
        String block = com.cryptocarver.crypto.PaymentOperations.encodePinBlock(pin, pan, "Format 0 (ISO-0)");
        ProcessDefinition def = new ProcessDefinition();
        def.nodes.add(new ProcessDefinition.Node("payment", "PIN_BLOCK_ENCODE", "PIN block", 0, 0));
        Map<String, FlowValue> result = Map.of("payment", FlowValue.hex(block.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        List<NodeExecutionEvent> events = List.of(new NodeExecutionEvent("payment", 1, "PIN block", "PIN_BLOCK_ENCODE",
                NodeExecutionState.SUCCESS, Duration.ZERO, Representation.TEXT_UTF8, pin.length() + pan.length(),
                Representation.HEX, block.length(), "OK"));

        for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            String trace = controller.renderExecutionResult(def, result, events, null);
            assertFalse(trace.contains(pin) || trace.contains(pan), "PIN/PAN must not appear: " + profile);
            if (profile == SecretVisibilityProfile.FULL_LAB) assertTrue(trace.contains(block));
            else assertFalse(trace.contains(block), "PIN block must be hidden: " + profile);
        }
    }
}
