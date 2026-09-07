package com.cryptocarver.ui;

import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessDefinitionCodec;
import com.cryptocarver.model.process.ProcessEngine;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProcessDesignerLegacyProcessTest {

    @Test
    void goldenProcessLoadsAndExecutesWithIdenticalConfiguration() throws Exception {
        String json;
        try (InputStream in = getClass().getResourceAsStream("/process/legacy-aes-gcm-roundtrip.cfprocess.json")) {
            assertNotNull(in, "Legacy golden fixture must exist");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ProcessDefinition definition = ProcessDefinitionCodec.deserialize(json);
        assertEquals("AES-GCM encrypt and decrypt", definition.name);
        assertEquals(7, definition.nodes.size());
        assertEquals(8, definition.connections.size());

        // Verify configuration maps node-by-node
        ProcessDefinition.Node inputNode = definition.nodes.stream()
                .filter(n -> "input".equals(n.id)).findFirst().orElseThrow();
        assertEquals("Hello, CryptoForge", inputNode.configuration.get("value"));

        ProcessDefinition.Node keyNode = definition.nodes.stream()
                .filter(n -> "key".equals(n.id)).findFirst().orElseThrow();
        assertEquals("256", keyNode.configuration.get("keySize"));
        assertEquals("AES", keyNode.configuration.get("keyAlgorithm"));

        ProcessDefinition.Node ivNode = definition.nodes.stream()
                .filter(n -> "iv".equals(n.id)).findFirst().orElseThrow();
        assertEquals("12", ivNode.configuration.get("length"));

        ProcessDefinition.Node encryptNode = definition.nodes.stream()
                .filter(n -> "encrypt".equals(n.id)).findFirst().orElseThrow();
        assertEquals("AES/GCM/NoPadding", encryptNode.configuration.get("algorithm"));
        assertEquals("HEX", encryptNode.configuration.get("keyFormat"));
        assertEquals("false", encryptNode.configuration.get("generateNonce"));
        assertEquals("RAW", encryptNode.configuration.get("outputFormat"));

        ProcessDefinition.Node decryptNode = definition.nodes.stream()
                .filter(n -> "decrypt".equals(n.id)).findFirst().orElseThrow();
        assertEquals("AES/GCM/NoPadding", decryptNode.configuration.get("algorithm"));
        assertEquals("HEX", decryptNode.configuration.get("keyFormat"));
        assertEquals("false", decryptNode.configuration.get("generateNonce"));
        assertEquals("RAW", decryptNode.configuration.get("outputFormat"));

        // Execute workflow and verify output
        Map<String, FlowValue> results = ProcessEngine.execute(definition);
        assertEquals("Hello, CryptoForge", results.get("output").render());
    }

    @Test
    void ola1DigestVerificationProcessExecutesAndSelfVerifiesWithAssertEquals() throws Exception {
        String json;
        try (InputStream in = getClass().getResourceAsStream("/process/ola1_digest_verification.cfprocess.json")) {
            assertNotNull(in, "Ola 1 golden fixture must exist");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ProcessDefinition definition = ProcessDefinitionCodec.deserialize(json);
        assertEquals("Ola 1 Digest Verification", definition.name);
        assertEquals(5, definition.nodes.size());
        assertEquals(4, definition.connections.size());

        Map<String, FlowValue> results = ProcessEngine.execute(definition);
        assertNotNull(results.get("assert_match"));
        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
                results.get("assert_match").render().toLowerCase());
    }

    @Test
    void ola1CompressionAndPaddingProcessesSelfVerify() throws Exception {
        for (String name : new String[]{"ola1_compress_gzip_verification.cfprocess.json", "ola1_pad_pkcs7_verification.cfprocess.json"}) {
            ProcessDefinition definition = loadProcess(name);
            Map<String, FlowValue> results = ProcessEngine.execute(definition);
            assertNotNull(results.get("assert"), "Fixture must finish at ASSERT_EQUALS: " + name);
        }
    }

    @Test
    void ola5b2aKeyProcessesSelfVerify() throws Exception {
        for (String name : new String[]{"ola5b2a_kcv_verification.cfprocess.json",
                "ola5b2a_split_combine_verification.cfprocess.json",
                "ola5b2a_aes3394_roundtrip.cfprocess.json"}) {
            ProcessDefinition definition = loadProcess(name);
            Map<String, FlowValue> results = ProcessEngine.execute(definition);
            assertNotNull(results.get("assert"), "Fixture must finish at ASSERT_EQUALS: " + name);
        }
    }

    @Test
    void ola5b2bPaymentAndComponentProcessesSelfVerify() throws Exception {
        for (String name : new String[]{"ola5b2b_cvv_verification.cfprocess.json",
                "ola5b2b_pin_block_roundtrip.cfprocess.json",
                "ola5b2b_component_select_verification.cfprocess.json"}) {
            ProcessDefinition definition = loadProcess(name);
            Map<String, FlowValue> results = ProcessEngine.execute(definition);
            assertNotNull(results.get("assert"), "Fixture must finish at ASSERT_EQUALS: " + name);
        }
    }

    private ProcessDefinition loadProcess(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/process/" + name)) {
            assertNotNull(in, "Process fixture must exist: " + name);
            return ProcessDefinitionCodec.deserialize(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
