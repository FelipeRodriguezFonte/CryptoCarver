package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FileWritePolicy;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.Representation;
import com.cryptocarver.model.process.handlers.PlumbingNodeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that no NodeExecutionEvent.safeMessage, execution row, or error message
 * contains secret keys, PINs, or PANs across all three visibility profiles (§5.4).
 */
class ProcessTelemetryRedactionTest {

    private SecretVisibilityProfile originalProfile;

    @BeforeEach
    void setUp() {
        originalProfile = AppSettings.getInstance().getSecretVisibilityProfile();
    }

    @AfterEach
    void tearDown() {
        AppSettings.getInstance().setSecretVisibilityProfile(originalProfile);
    }

    @Test
    void testSafeMessageAndTelemetryNeverLeakSecrets() throws Exception {
        String testKeyHex = "0123456789ABCDEF0123456789ABCDEF";
        String secretPin = "1234";

        for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);

            ProcessDefinition def = new ProcessDefinition();
            ProcessDefinition.Node inNode = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "Input", 0, 0);
            inNode.configuration.put("value", "Plaintext Data");
            def.nodes.add(inNode);

            ProcessDefinition.Node encNode = new ProcessDefinition.Node("enc", "ENCRYPT", "Encrypt", 100, 0);
            encNode.configuration.put("algorithm", "AES/CBC/PKCS7Padding");
            encNode.configuration.put("key", testKeyHex);
            encNode.configuration.put("nonce", "00000000000000000000000000000000");
            encNode.configuration.put("keyFormat", "HEX");
            def.nodes.add(encNode);

            def.connections.add(new ProcessDefinition.Connection("in", "enc", "payload"));

            List<NodeExecutionEvent> events = new ArrayList<>();
            ExecutionContext context = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, events::add);
            Map<String, FlowValue> results = ProcessEngine.execute(def, context);

            for (NodeExecutionEvent event : events) {
                if (event.safeMessage() != null) {
                    assertFalse(event.safeMessage().contains(testKeyHex),
                            "safeMessage must never contain key under profile: " + profile);
                    assertFalse(event.safeMessage().contains(secretPin),
                            "safeMessage must never contain PIN under profile: " + profile);
                }
            }

            ProcessDesignerController controller = new ProcessDesignerController();
            String trace = controller.renderExecutionResult(def, results, events, null);

            if (profile == SecretVisibilityProfile.MASKED) {
                assertFalse(trace.contains(testKeyHex), "MASKED trace must not contain clear secret key");
            } else if (profile == SecretVisibilityProfile.REDACTED) {
                assertFalse(trace.contains(testKeyHex), "REDACTED trace must not contain clear secret key");
                assertFalse(trace.contains("key (HEX)"), "REDACTED trace must omit key line completely");
            }
        }
    }

    @Test
    void testErrorMessageDoesNotLeakSecretsOnFailure() {
        String secretKey = "FEDCBA9876543210FEDCBA9876543210";

        for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);

            ProcessDefinition def = new ProcessDefinition();
            ProcessDefinition.Node failNode = new ProcessDefinition.Node("fail", "ASSERT_EQUALS", "Assert", 0, 0);
            def.nodes.add(failNode);

            try {
                // actual != expected with intentional mismatch
                Map<String, FlowValue> inputs = Map.of(
                        "actual", FlowValue.binary(secretKey.getBytes()),
                        "expected", FlowValue.binary("DIFFERENT_DATA".getBytes())
                );
                PlumbingNodeHandler handler = new PlumbingNodeHandler();
                handler.execute(failNode, inputs, null);
            } catch (Exception ex) {
                String errorMsg = ex.getMessage();
                assertFalse(errorMsg.contains(secretKey),
                        "Exception message must never contain secret payload: " + errorMsg);
            }
        }
    }

    @Test
    void keyOperationOutputsFollowVisibilityProfiles() throws Exception {
        String key = "00112233445566778899AABBCCDDEEFF";
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node source = new ProcessDefinition.Node("source", "CONSOLE_INPUT", "Key", 0, 0);
        source.configuration.put("value", key);
        ProcessDefinition.Node decode = new ProcessDefinition.Node("decode", "HEX_DECODE", "Decode", 100, 0);
        ProcessDefinition.Node encode = new ProcessDefinition.Node("encode", "HEX_ENCODE", "Encode", 200, 0);
        ProcessDefinition.Node split = new ProcessDefinition.Node("split", "KEY_SPLIT_XOR", "Split", 300, 0);
        split.configuration.put("componentCount", "3");
        def.nodes.addAll(List.of(source, decode, encode, split));
        def.connections.add(new ProcessDefinition.Connection("source", "decode", "input"));
        def.connections.add(new ProcessDefinition.Connection("decode", "encode", "input"));
        def.connections.add(new ProcessDefinition.Connection("encode", "split", "key"));

        for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            List<NodeExecutionEvent> events = new ArrayList<>();
            Map<String, FlowValue> results = ProcessEngine.execute(def,
                    new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, events::add));
            String trace = new ProcessDesignerController().renderExecutionResult(def, results, events, null);
            String bundle = results.get("split").render();
            if (profile == SecretVisibilityProfile.FULL_LAB) {
                assertTrue(trace.contains(bundle));
            } else {
                assertFalse(trace.contains(bundle));
            }
        }
    }

    @Test
    void paymentPinAndPinBlockTelemetryFollowVisibilityProfiles() throws Exception {
        String pan = "4761739001010010";
        String pin = "1234";
        String block = com.cryptocarver.crypto.PaymentOperations.encodePinBlock(pin, pan, "Format 0 (ISO-0)");
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node node = new ProcessDefinition.Node("pinBlock", "PIN_BLOCK_ENCODE", "PIN block", 0, 0);
        def.nodes.add(node);
        Map<String, FlowValue> result = Map.of("pinBlock", FlowValue.hex(block.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        List<NodeExecutionEvent> events = List.of(new NodeExecutionEvent("pinBlock", 1, "PIN block", "PIN_BLOCK_ENCODE",
                com.cryptocarver.model.process.NodeExecutionState.SUCCESS, java.time.Duration.ZERO,
                Representation.TEXT_UTF8, pin.length() + pan.length(), Representation.HEX, block.length(), "OK"));

        for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            String trace = new ProcessDesignerController().renderExecutionResult(def, result, events, null);
            assertFalse(trace.contains(pin) || trace.contains(pan), "PIN/PAN must not be printed in telemetry: " + profile);
            if (profile == SecretVisibilityProfile.FULL_LAB) assertTrue(trace.contains(block));
            else assertFalse(trace.contains(block), "PIN block must be hidden: " + profile);
        }
    }

    @Test
    void pqcPrivateAndSharedSecretOutputsAreRedactedOutsideFullLab() {
        byte[] secret = "PQC_PRIVATE_OR_SHARED_SECRET".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (String type : List.of("PQC_KEYPAIR_GENERATE", "PQC_KEM_DECAPSULATE")) {
            ProcessDefinition def = new ProcessDefinition();
            def.nodes.add(new ProcessDefinition.Node("pqc", type, "PQC", 0, 0));
            Map<String, FlowValue> results = Map.of("pqc", FlowValue.binary(secret));
            List<NodeExecutionEvent> events = List.of(new NodeExecutionEvent("pqc", 1, "PQC", type,
                    com.cryptocarver.model.process.NodeExecutionState.SUCCESS, java.time.Duration.ZERO,
                    null, 0, Representation.BINARY, secret.length, "Success"));
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                String trace = new ProcessDesignerController().renderExecutionResult(def, results, events, null);
                if (profile == SecretVisibilityProfile.FULL_LAB) assertTrue(trace.contains("5051435F505249564154455F4F525F5348415245445F534543524554"));
                else assertFalse(trace.contains("5051435F505249564154455F4F525F5348415245445F534543524554"));
            }
        }
    }

    @Test
    void jweAndCmsTelemetryNeverExposeContentEncryptionKeys() {
        String cek = "0123456789abcdef0123456789abcdef";
        for (String type : List.of("JWE_ENCRYPT", "CMS_ENVELOPE")) {
            ProcessDefinition def = new ProcessDefinition();
            ProcessDefinition.Node node = new ProcessDefinition.Node("envelope", type, "Envelope", 0, 0);
            node.configuration.put("cek", cek);
            def.nodes.add(node);
            Map<String, FlowValue> result = Map.of("envelope", FlowValue.binary(new byte[] {1, 2, 3}));
            List<NodeExecutionEvent> events = List.of(new NodeExecutionEvent("envelope", 1, "Envelope", type,
                    com.cryptocarver.model.process.NodeExecutionState.SUCCESS, java.time.Duration.ZERO,
                    Representation.TEXT_UTF8, 7, Representation.BINARY, 3, "Success"));
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                assertFalse(new ProcessDesignerController().renderExecutionResult(def, result, events, null).contains(cek));
            }
        }
    }
}
