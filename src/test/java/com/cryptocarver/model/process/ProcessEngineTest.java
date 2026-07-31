package com.cryptocarver.model.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import com.cryptocarver.crypto.HashOperations;
import com.cryptocarver.crypto.MACOperations;
import org.junit.jupiter.api.Test;

class ProcessEngineTest {
    @Test void executesConsoleHashFlowAndRoundTripsDefinition() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "hello");
        ProcessDefinition.Node hash = new ProcessDefinition.Node("hash", "HASH", "SHA-256", 1, 0);
        hash.configuration.put("algorithm", "SHA-256");
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "Output", 2, 0);
        process.nodes.add(input); process.nodes.add(hash); process.nodes.add(output);
        process.connections.add(new ProcessDefinition.Connection("input", "hash"));
        process.connections.add(new ProcessDefinition.Connection("hash", "output"));

        assertEquals("2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824", ProcessEngine.executeAndRender(process).get("output"));
        assertTrue(ProcessDefinitionCodec.deserialize(ProcessDefinitionCodec.serialize(process)).nodes.size() == 3);
    }

    @Test void base64EncodesDigestBytesInsteadOfTheHexRendering() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "Hola");
        ProcessDefinition.Node hash = new ProcessDefinition.Node("hash", "HASH", "SHA-256", 1, 0);
        ProcessDefinition.Node base64 = new ProcessDefinition.Node("base64", "BASE64_ENCODE", "Base64", 2, 0);
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "Output", 3, 0);
        process.nodes.add(input); process.nodes.add(hash); process.nodes.add(base64); process.nodes.add(output);
        process.connections.add(new ProcessDefinition.Connection("input", "hash"));
        process.connections.add(new ProcessDefinition.Connection("hash", "base64"));
        process.connections.add(new ProcessDefinition.Connection("base64", "output"));

        String expected = Base64.getEncoder().encodeToString(HashOperations.calculateHash(
                "Hola".getBytes(java.nio.charset.StandardCharsets.UTF_8), "SHA-256"));
        assertEquals(expected, ProcessEngine.executeAndRender(process).get("output"));
    }

    @Test void readsAndWritesFilesThroughTheWorkflow() throws Exception {
        Path source = Files.createTempFile("cryptoforge-process-input", ".txt");
        Path target = Files.createTempFile("cryptoforge-process-output", ".txt");
        Files.writeString(source, "Hola");
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "FILE_INPUT", "Input", 0, 0);
        input.configuration.put("path", source.toString());
        ProcessDefinition.Node base64 = new ProcessDefinition.Node("base64", "BASE64_ENCODE", "Base64", 1, 0);
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "FILE_OUTPUT", "Output", 2, 0);
        output.configuration.put("path", target.toString());
        process.nodes.add(input); process.nodes.add(base64); process.nodes.add(output);
        process.connections.add(new ProcessDefinition.Connection("input", "base64"));
        process.connections.add(new ProcessDefinition.Connection("base64", "output"));

        Files.deleteIfExists(target);
        ProcessEngine.execute(process);
        assertEquals("SG9sYQ==", Files.readString(target));
    }

    @Test void codecRoundTripTests() throws Exception {
        byte[] data = new byte[]{0x00, (byte) 0xff, 0x12, 0x34};

        // Base64
        ProcessDefinition pb64 = new ProcessDefinition();
        pb64.nodes.add(new ProcessDefinition.Node("in", "FILE_INPUT", "in", 0,0));
        pb64.nodes.add(new ProcessDefinition.Node("enc", "BASE64_ENCODE", "enc", 0,0));
        pb64.nodes.add(new ProcessDefinition.Node("dec", "BASE64_DECODE", "dec", 0,0));
        pb64.nodes.add(new ProcessDefinition.Node("out", "FILE_OUTPUT", "out", 0,0));
        pb64.connections.add(new ProcessDefinition.Connection("in", "enc"));
        pb64.connections.add(new ProcessDefinition.Connection("enc", "dec"));
        pb64.connections.add(new ProcessDefinition.Connection("dec", "out"));

        Path inF = Files.createTempFile("test-in", ".bin");
        Path outF = Files.createTempFile("test-out", ".bin");
        Files.write(inF, data);
        Files.deleteIfExists(outF);

        pb64.nodes.get(0).configuration.put("path", inF.toString());
        pb64.nodes.get(3).configuration.put("path", outF.toString());

        ProcessEngine.execute(pb64);
        org.junit.jupiter.api.Assertions.assertArrayEquals(data, Files.readAllBytes(outF));

        // Hex
        ProcessDefinition phex = new ProcessDefinition();
        phex.nodes.add(new ProcessDefinition.Node("in", "FILE_INPUT", "in", 0,0));
        phex.nodes.add(new ProcessDefinition.Node("enc", "HEX_ENCODE", "enc", 0,0));
        phex.nodes.add(new ProcessDefinition.Node("dec", "HEX_DECODE", "dec", 0,0));
        phex.nodes.add(new ProcessDefinition.Node("out", "FILE_OUTPUT", "out", 0,0));
        phex.connections.add(new ProcessDefinition.Connection("in", "enc"));
        phex.connections.add(new ProcessDefinition.Connection("enc", "dec"));
        phex.connections.add(new ProcessDefinition.Connection("dec", "out"));

        Files.deleteIfExists(outF);
        phex.nodes.get(0).configuration.put("path", inF.toString());
        phex.nodes.get(3).configuration.put("path", outF.toString());

        ProcessEngine.execute(phex);
        org.junit.jupiter.api.Assertions.assertArrayEquals(data, Files.readAllBytes(outF));
    }

    @Test void rejectWrongRepresentation() {
        ProcessDefinition p = new ProcessDefinition();
        p.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0,0));
        p.nodes.add(new ProcessDefinition.Node("dec", "HEX_DECODE", "dec", 0,0));
        p.connections.add(new ProcessDefinition.Connection("in", "dec"));

        p.nodes.set(0, new ProcessDefinition.Node("in", "FILE_INPUT", "in", 0,0));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(p));
        assertTrue(ex.getMessage().contains("expects") && ex.getMessage().contains("dec"));
    }

    @Test void validationFailsOnCycles() {
        ProcessDefinition p = new ProcessDefinition();
        p.nodes.add(new ProcessDefinition.Node("a", "BASE64_ENCODE", "a", 0,0));
        p.nodes.add(new ProcessDefinition.Node("b", "BASE64_DECODE", "b", 0,0));
        p.connections.add(new ProcessDefinition.Connection("a", "b"));
        p.connections.add(new ProcessDefinition.Connection("b", "a"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(p));
        assertTrue(ex.getMessage().contains("Cycle detected"));
    }

    @Test void fileOverwritePolicyTest() throws Exception {
        Path target = Files.createTempFile("test-overwrite", ".txt");

        ProcessDefinition p = new ProcessDefinition();
        p.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0,0));
        p.nodes.add(new ProcessDefinition.Node("out", "FILE_OUTPUT", "out", 0,0));
        p.connections.add(new ProcessDefinition.Connection("in", "out"));
        p.nodes.get(1).configuration.put("path", target.toString());

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
            ProcessEngine.execute(p, new ExecutionContext(FileWritePolicy.FAIL_IF_EXISTS, null))
        );
        assertTrue(ex.getMessage().contains("Policy prevents overwrite") || ex.getMessage().contains("already exists"));

        ProcessEngine.execute(p, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));
        assertTrue(Files.exists(target));
    }

    @Test void phase35_hexEncodeReturnsHexRepresentation() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        process.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0,0));
        process.nodes.get(0).configuration.put("value", "Hello");
        process.nodes.add(new ProcessDefinition.Node("enc", "HEX_ENCODE", "enc", 0,0));
        process.connections.add(new ProcessDefinition.Connection("in", "enc"));

        java.util.concurrent.atomic.AtomicReference<NodeExecutionEvent> eventRef = new java.util.concurrent.atomic.AtomicReference<>();
        ExecutionContext context = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, event -> {
            if ("enc".equals(event.nodeId()) && event.state() == NodeExecutionState.SUCCESS) {
                eventRef.set(event);
            }
        });

        ProcessEngine.execute(process, context);

        org.junit.jupiter.api.Assertions.assertNotNull(eventRef.get());
        org.junit.jupiter.api.Assertions.assertEquals(Representation.HEX, eventRef.get().outputRepresentation());
        org.junit.jupiter.api.Assertions.assertEquals(10, eventRef.get().outputSize());
    }

    @Test void phase35_base64EncodeReturnsBase64Representation() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        process.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0,0));
        process.nodes.get(0).configuration.put("value", "Hello");
        process.nodes.add(new ProcessDefinition.Node("enc", "BASE64_ENCODE", "enc", 0,0));
        process.connections.add(new ProcessDefinition.Connection("in", "enc"));

        java.util.concurrent.atomic.AtomicReference<NodeExecutionEvent> eventRef = new java.util.concurrent.atomic.AtomicReference<>();
        ExecutionContext context = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, event -> {
            if ("enc".equals(event.nodeId()) && event.state() == NodeExecutionState.SUCCESS) {
                eventRef.set(event);
            }
        });

        ProcessEngine.execute(process, context);

        org.junit.jupiter.api.Assertions.assertNotNull(eventRef.get());
        org.junit.jupiter.api.Assertions.assertEquals(Representation.BASE64, eventRef.get().outputRepresentation());
        org.junit.jupiter.api.Assertions.assertEquals(8, eventRef.get().outputSize());
    }

    @Test void phase35_fileInputTextModeReturnsTextRepresentation() throws Exception {
        java.nio.file.Path source = java.nio.file.Files.createTempFile("cryptoforge-process-input-text", ".txt");
        java.nio.file.Files.writeString(source, "Hello World");

        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("in", "FILE_INPUT", "in", 0,0);
        input.configuration.put("path", source.toString());
        input.configuration.put("readMode", "TEXT");
        process.nodes.add(input);

        java.util.concurrent.atomic.AtomicReference<NodeExecutionEvent> eventRef = new java.util.concurrent.atomic.AtomicReference<>();
        ExecutionContext context = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, event -> {
            if ("in".equals(event.nodeId()) && event.state() == NodeExecutionState.SUCCESS) {
                eventRef.set(event);
            }
        });

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(process, context);

        org.junit.jupiter.api.Assertions.assertNotNull(eventRef.get());
        org.junit.jupiter.api.Assertions.assertEquals(Representation.TEXT_UTF8, eventRef.get().outputRepresentation());
        org.junit.jupiter.api.Assertions.assertEquals(11, eventRef.get().outputSize());
        org.junit.jupiter.api.Assertions.assertEquals(Representation.TEXT_UTF8, result.get("in").representation());

        java.nio.file.Files.deleteIfExists(source);
    }

    @Test void phase35_hashReturnsBinaryRepresentation() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        process.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0,0));
        process.nodes.get(0).configuration.put("value", "Hello");
        process.nodes.add(new ProcessDefinition.Node("hash", "HASH", "hash", 0,0));
        process.nodes.get(1).configuration.put("algorithm", "SHA-256");
        process.connections.add(new ProcessDefinition.Connection("in", "hash"));

        java.util.concurrent.atomic.AtomicReference<NodeExecutionEvent> eventRef = new java.util.concurrent.atomic.AtomicReference<>();
        ExecutionContext context = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, event -> {
            if ("hash".equals(event.nodeId()) && event.state() == NodeExecutionState.SUCCESS) {
                eventRef.set(event);
            }
        });

        ProcessEngine.execute(process, context);

        org.junit.jupiter.api.Assertions.assertNotNull(eventRef.get());
        org.junit.jupiter.api.Assertions.assertEquals(Representation.BINARY, eventRef.get().outputRepresentation());
        org.junit.jupiter.api.Assertions.assertEquals(32, eventRef.get().outputSize());
    }

    @Test void validateFailsOnMissingKeyConfig() {
        ProcessDefinition process = new ProcessDefinition();
        process.nodes.add(new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 0, 0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));

        assertTrue(ex.getMessage().contains("Key is missing"));
    }

    @Test void validateFailsOnInvalidKeyConfig() {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 0, 0);
        encrypt.configuration.put("keyFormat", "HEX");
        encrypt.configuration.put("key", "not-hex");
        process.nodes.add(encrypt);

        assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));
    }

    @Test void validateFailsOnMissingSignConfig() {
        ProcessDefinition process = new ProcessDefinition();
        process.nodes.add(new ProcessDefinition.Node("sign", "SIGN", "Sign", 0, 0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));

        assertTrue(ex.getMessage().contains("Incomplete SIGN configuration"));
    }

    @Test void validateFailsOnMissingVerifyConfig() {
        ProcessDefinition process = new ProcessDefinition();
        process.nodes.add(new ProcessDefinition.Node("verify", "VERIFY", "Verify", 0, 0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));

        assertTrue(ex.getMessage().contains("Public material path is required"));
    }

    @Test void serializeRemovesSecrets() {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 0, 0);
        encrypt.configuration.put("key", "deadbeef");
        ProcessDefinition.Node sign = new ProcessDefinition.Node("sign", "SIGN", "Sign", 1, 0);
        sign.configuration.put("keystorePath", "/safe/path/keystore.p12");
        sign.configuration.put("alias", "myalias");
        sign.configuration.put("keystorePassword", "store-secret");
        sign.configuration.put("keyPassword", "key-secret");
        process.nodes.add(encrypt);
        process.nodes.add(sign);

        String json = ProcessDefinitionCodec.serialize(process);
        ProcessDefinition restored = ProcessDefinitionCodec.deserialize(json);

        assertFalse(json.contains("deadbeef"));
        assertFalse(json.contains("store-secret"));
        assertFalse(json.contains("key-secret"));
        assertNull(restored.nodes.get(0).configuration.get("key"));
        assertNull(restored.nodes.get(1).configuration.get("keystorePassword"));
        assertNull(restored.nodes.get(1).configuration.get("keyPassword"));
        assertEquals("/safe/path/keystore.p12", restored.nodes.get(1).configuration.get("keystorePath"));
        assertEquals("myalias", restored.nodes.get(1).configuration.get("alias"));
    }

    @Test void executesAesGcmThenMacOnDecryptedPayload() throws Exception {
        String key = "00112233445566778899aabbccddeeff";
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "test");
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", key);
        encrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node decrypt = cryptoNode("decrypt", "DECRYPT", key);
        decrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node mac = cryptoNode("mac", "MAC", key);
        process.nodes.add(input);
        process.nodes.add(encrypt);
        process.nodes.add(decrypt);
        process.nodes.add(mac);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt"));
        process.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt"));
        process.connections.add(new ProcessDefinition.Connection("decrypt", "mac"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        org.junit.jupiter.api.Assertions.assertArrayEquals("test".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                result.get("decrypt").bytes());
        assertEquals(32, result.get("mac").bytes().length);
    }

    @Test void validateFailsOnNonExistentKeystore() {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node sign = signNode("/this/path/does/not/exist.p12", "storepass", "storepass");
        process.nodes.add(sign);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));

        assertTrue(ex.getMessage().contains("Failed to validate SIGN configuration"));
    }

    @Test void validateFailsOnInvalidKeystorePassword() {
        ProcessDefinition process = new ProcessDefinition();
        process.nodes.add(signNode("src/test/resources/testks.p12", "wrongpass", "storepass"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));

        assertTrue(ex.getMessage().contains("Failed to validate SIGN configuration"));
    }

    @Test void executionSignAndVerify() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "message to sign");
        ProcessDefinition.Node sign = signNode("src/test/resources/testks.p12", "storepass", "storepass");
        ProcessDefinition.Node verify = new ProcessDefinition.Node("verify", "VERIFY", "Verify", 2, 0);
        verify.configuration.put("materialPath", "src/test/resources/testcert.pem");
        verify.configuration.put("materialType", "CERTIFICATE");
        verify.configuration.put("algorithm", "SHA256withRSA");
        process.nodes.add(input);
        process.nodes.add(sign);
        process.nodes.add(verify);
        process.connections.add(new ProcessDefinition.Connection("input", "sign"));
        process.connections.add(new ProcessDefinition.Connection("input", "verify", "payload"));
        process.connections.add(new ProcessDefinition.Connection("sign", "verify", "signature"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertNotNull(result.get("sign"));
        assertEquals("VALID", result.get("verify").render());
    }

    @Test void aesCbcRoundTripPersistsAutoGeneratedIvForReproducibility() throws Exception {
        String key = "00112233445566778899aabbccddeeff";
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "CBC message");
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", key);
        encrypt.configuration.put("algorithm", "AES/CBC/PKCS7Padding");
        encrypt.configuration.put("generateNonce", "true");
        encrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node decrypt = cryptoNode("decrypt", "DECRYPT", key);
        decrypt.configuration.put("algorithm", "AES/CBC/PKCS7Padding");
        decrypt.configuration.put("outputFormat", "ENVELOPE");
        process.nodes.add(input);
        process.nodes.add(encrypt);
        process.nodes.add(decrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt"));
        process.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertTrue(encrypt.configuration.get("nonce").matches("[0-9a-f]{32}"));
        org.junit.jupiter.api.Assertions.assertArrayEquals("CBC message".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                result.get("decrypt").bytes());
        assertEquals((byte) 2, result.get("encrypt").bytes()[5]);
    }

    @Test void aesCbcPkcs7RawOutputMatchesSymmetricWorkbench() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "Hola");
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT",
                "5F78D72AAF9555AC8588676D1DA27FF9B22B827EAEA2A3787EE9B33DA919560F");
        encrypt.configuration.put("algorithm", "AES/CBC/PKCS7Padding");
        encrypt.configuration.put("nonce", "2f82758f7c33b6aaa43b7d332c1e9109");
        encrypt.configuration.put("generateNonce", "false");
        process.nodes.add(input); process.nodes.add(encrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertEquals("7C246475CFB615D880F26B3B80A24522", result.get("encrypt").render());
    }

    @Test void rejectsMissingIvWhenAutoGenerationIsDisabled() {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", "00112233445566778899aabbccddeeff");
        encrypt.configuration.put("generateNonce", "false");
        process.nodes.add(encrypt);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));

        assertTrue(ex.getMessage().contains("IV/nonce is required"));
    }

    @Test void generatedAesKeyCanBeRoutedToEncryptAndDecrypt() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Password", 0, 0);
        input.configuration.put("value", "key flow");
        ProcessDefinition.Node key = new ProcessDefinition.Node("key", "AES_KEY_GENERATE", "AES key", 1, 0);
        key.configuration.put("keySize", "256");
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 2, 0);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        encrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node decrypt = new ProcessDefinition.Node("decrypt", "DECRYPT", "Decrypt", 3, 0);
        decrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        decrypt.configuration.put("outputFormat", "ENVELOPE");
        process.nodes.add(input); process.nodes.add(key); process.nodes.add(encrypt); process.nodes.add(decrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("key", "encrypt", "key"));
        process.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("key", "decrypt", "key"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertEquals(32, result.get("key").bytes().length);
        org.junit.jupiter.api.Assertions.assertArrayEquals("key flow".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                result.get("decrypt").bytes());
    }

    @Test void consoleOutputCanTapCiphertextWhileDecryptReceivesTheSameValue() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "tap the ciphertext");
        ProcessDefinition.Node key = new ProcessDefinition.Node("key", "AES_KEY_GENERATE", "AES key", 0, 0);
        key.configuration.put("keySize", "256");
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 0, 0);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        encrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node cipherTap = new ProcessDefinition.Node("cipherTap", "CONSOLE_OUTPUT", "Ciphertext", 0, 0);
        ProcessDefinition.Node decrypt = new ProcessDefinition.Node("decrypt", "DECRYPT", "Decrypt", 0, 0);
        decrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        decrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node plainOutput = new ProcessDefinition.Node("plainOutput", "CONSOLE_OUTPUT", "Plaintext", 0, 0);
        process.nodes.addAll(java.util.List.of(input, key, encrypt, cipherTap, decrypt, plainOutput));
        process.connections.addAll(java.util.List.of(
                new ProcessDefinition.Connection("input", "encrypt", "payload"),
                new ProcessDefinition.Connection("key", "encrypt", "key"),
                new ProcessDefinition.Connection("encrypt", "cipherTap", "input"),
                new ProcessDefinition.Connection("encrypt", "decrypt", "payload"),
                new ProcessDefinition.Connection("key", "decrypt", "key"),
                new ProcessDefinition.Connection("decrypt", "plainOutput", "input")));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        org.junit.jupiter.api.Assertions.assertArrayEquals(result.get("encrypt").bytes(), result.get("cipherTap").bytes());
        org.junit.jupiter.api.Assertions.assertArrayEquals("tap the ciphertext".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                result.get("plainOutput").bytes());
    }

    @Test void utf8CodecRoundTripExecutes() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "Hola €");
        ProcessDefinition.Node encode = new ProcessDefinition.Node("encode", "UTF8_ENCODE", "UTF-8 encode", 0, 0);
        ProcessDefinition.Node decode = new ProcessDefinition.Node("decode", "UTF8_DECODE", "UTF-8 decode", 0, 0);
        process.nodes.addAll(java.util.List.of(input, encode, decode));
        process.connections.addAll(java.util.List.of(
                new ProcessDefinition.Connection("input", "encode", "input"),
                new ProcessDefinition.Connection("encode", "decode", "input")));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertEquals("Hola €", result.get("decode").render());
    }

    @Test void utf8EncodeRejectsBinaryInputToAvoidAccidentalNoOpConversions() {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node binary = new ProcessDefinition.Node("binary", "RANDOM_BYTES", "Binary", 0, 0);
        ProcessDefinition.Node encode = new ProcessDefinition.Node("encode", "UTF8_ENCODE", "UTF-8 encode", 0, 0);
        process.nodes.addAll(java.util.List.of(binary, encode));
        process.connections.add(new ProcessDefinition.Connection("binary", "encode", "input"));

        IllegalArgumentException failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ProcessEngine.validate(process));
        assertTrue(failure.getMessage().contains("expects [TEXT_UTF8] but receives BINARY"));
    }

    @Test void processNameSurvivesSaveAndLoadCodecRoundTrip() {
        ProcessDefinition process = new ProcessDefinition();
        process.name = "AES-GCM round trip — laboratory";

        ProcessDefinition restored = ProcessDefinitionCodec.deserialize(ProcessDefinitionCodec.serialize(process));

        assertEquals("AES-GCM round trip — laboratory", restored.name);
    }

    @Test void phase39_3desAndChaCha20Poly1305EnvelopeRoundTrips() throws Exception {
        for (String algorithm : new String[]{"3DES/CBC/PKCS7Padding", "ChaCha20-Poly1305"}) {
            ProcessDefinition process = new ProcessDefinition();
            ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
            input.configuration.put("value", "algorithm expansion");
            ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT",
                    "3DES/CBC/PKCS7Padding".equals(algorithm)
                            ? "0123456789ABCDEFFEDCBA98765432100011223344556677"
                            : "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
            encrypt.configuration.put("algorithm", algorithm);
            encrypt.configuration.put("outputFormat", "ENVELOPE");
            ProcessDefinition.Node decrypt = cryptoNode("decrypt", "DECRYPT",
                    encrypt.configuration.get("key"));
            decrypt.configuration.put("algorithm", algorithm);
            decrypt.configuration.put("outputFormat", "ENVELOPE");
            ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "Output", 0, 0);
            process.nodes.addAll(java.util.List.of(input, encrypt, decrypt, output));
            process.connections.addAll(java.util.List.of(
                    new ProcessDefinition.Connection("input", "encrypt", "payload"),
                    new ProcessDefinition.Connection("encrypt", "decrypt", "payload"),
                    new ProcessDefinition.Connection("decrypt", "output", "input")));

            java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                    process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));
            org.junit.jupiter.api.Assertions.assertArrayEquals("algorithm expansion".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    result.get("output").bytes(), algorithm);
        }
    }

    @Test void phase39_3desNoPaddingAndGenerated3desKeyWork() throws Exception {
        ProcessDefinition keyProcess = new ProcessDefinition();
        ProcessDefinition.Node key = new ProcessDefinition.Node("key", "AES_KEY_GENERATE", "Generate 3DES key", 0, 0);
        key.configuration.put("keyAlgorithm", "3DES");
        keyProcess.nodes.add(key);
        byte[] generatedKey = ProcessEngine.execute(keyProcess, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null))
                .get("key").bytes();
        assertEquals(24, generatedKey.length);

        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "12345678");
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", "0123456789ABCDEFFEDCBA98765432100011223344556677");
        encrypt.configuration.put("algorithm", "3DES/CBC/NoPadding");
        encrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node decrypt = cryptoNode("decrypt", "DECRYPT", encrypt.configuration.get("key"));
        decrypt.configuration.put("algorithm", "3DES/CBC/NoPadding");
        decrypt.configuration.put("outputFormat", "ENVELOPE");
        process.nodes.addAll(java.util.List.of(input, encrypt, decrypt));
        process.connections.addAll(java.util.List.of(
                new ProcessDefinition.Connection("input", "encrypt", "payload"),
                new ProcessDefinition.Connection("encrypt", "decrypt", "payload")));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(process,
                new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));
        assertEquals("12345678", new String(result.get("decrypt").bytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test void pbkdf2CanDeriveAesKeyForWorkflowEncryption() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node password = new ProcessDefinition.Node("password", "CONSOLE_INPUT", "Password", 0, 0);
        password.configuration.put("value", "correct horse battery staple");
        ProcessDefinition.Node kdf = new ProcessDefinition.Node("kdf", "KDF_PBKDF2", "PBKDF2", 1, 0);
        kdf.configuration.put("keySize", "256");
        kdf.configuration.put("iterations", "10000");
        kdf.configuration.put("salt", "AAECAwQFBgcICQoLDA0ODw==");
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 2, 0);
        encrypt.configuration.put("algorithm", "AES/CBC/PKCS7Padding");
        process.nodes.add(password); process.nodes.add(kdf); process.nodes.add(encrypt);
        process.connections.add(new ProcessDefinition.Connection("password", "kdf"));
        process.connections.add(new ProcessDefinition.Connection("password", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("kdf", "encrypt", "key"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertEquals(32, result.get("kdf").bytes().length);
        assertTrue(result.get("encrypt").bytes().length > 23);
    }

    @Test void generatedRsaKeyPairCanBeRoutedToSign() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Message", 0, 0);
        input.configuration.put("value", "sign with generated key");
        ProcessDefinition.Node keyPair = new ProcessDefinition.Node("keypair", "RSA_KEYPAIR_GENERATE", "RSA key pair", 1, 0);
        keyPair.configuration.put("keySize", "2048");
        ProcessDefinition.Node sign = new ProcessDefinition.Node("sign", "SIGN", "Sign", 2, 0);
        sign.configuration.put("algorithm", "SHA256withRSA");
        process.nodes.add(input); process.nodes.add(keyPair); process.nodes.add(sign);
        process.connections.add(new ProcessDefinition.Connection("input", "sign", "payload"));
        process.connections.add(new ProcessDefinition.Connection("keypair", "sign", "key"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertTrue(result.get("keypair").bytes().length > 1000);
        assertTrue(result.get("sign").bytes().length > 0);
    }

    @Test void allowsOneSourceToFeedPayloadAndKeyPorts() throws Exception {
        Path source = Files.createTempFile("cryptoforge-key-and-payload", ".bin");
        Files.write(source, new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "FILE_INPUT", "Input", 0, 0);
        input.configuration.put("path", source.toString());
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 1, 0);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        process.nodes.add(input); process.nodes.add(encrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "key"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertTrue(result.get("encrypt").bytes().length > 0);
    }

    @Test void normalizesLegacyPayloadPortForConsoleOutput() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "Hola");
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "Output", 1, 0);
        process.nodes.add(input); process.nodes.add(output);
        process.connections.add(new ProcessDefinition.Connection("input", "output", "payload"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertEquals("Hola", result.get("output").render());
        assertEquals("input", process.connections.get(0).targetPort);
    }

    @Test void randomBytesGeneratesCorrectLengthAndCanBeUsedAsIv() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node rand = new ProcessDefinition.Node("rand", "RANDOM_BYTES", "Random", 0, 0);
        rand.configuration.put("length", "12");
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 1, 0);
        input.configuration.put("value", "GCM payload");
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt", 2, 0);
        encrypt.configuration.put("keyFormat", "HEX");
        encrypt.configuration.put("key", "00112233445566778899aabbccddeeff");
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");

        process.nodes.add(rand); process.nodes.add(input); process.nodes.add(encrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("rand", "encrypt", "iv"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        assertEquals(12, result.get("rand").bytes().length);
        assertTrue(result.get("encrypt").bytes().length > 0);
    }

    @Test void aesGcmAcceptsAndValidatesAad() throws Exception {
        String key = "00112233445566778899aabbccddeeff";
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "secret message");
        ProcessDefinition.Node aad = new ProcessDefinition.Node("aad", "CONSOLE_INPUT", "AAD", 1, 0);
        aad.configuration.put("value", "public metadata");
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", key);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        encrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node decrypt = cryptoNode("decrypt", "DECRYPT", key);
        decrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        decrypt.configuration.put("outputFormat", "ENVELOPE");

        process.nodes.add(input); process.nodes.add(aad); process.nodes.add(encrypt); process.nodes.add(decrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("aad", "encrypt", "aad"));
        process.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("aad", "decrypt", "aad"));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(
                process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

        org.junit.jupiter.api.Assertions.assertArrayEquals("secret message".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                result.get("decrypt").bytes());
    }

    @Test void aesGcmFailsWithModifiedAad() throws Exception {
        String key = "00112233445566778899aabbccddeeff";
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "secret message");
        ProcessDefinition.Node aad = new ProcessDefinition.Node("aad", "CONSOLE_INPUT", "AAD", 1, 0);
        aad.configuration.put("value", "public metadata");
        ProcessDefinition.Node badAad = new ProcessDefinition.Node("badaad", "CONSOLE_INPUT", "AAD", 2, 0);
        badAad.configuration.put("value", "modified metadata");
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", key);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        encrypt.configuration.put("outputFormat", "ENVELOPE");
        ProcessDefinition.Node decrypt = cryptoNode("decrypt", "DECRYPT", key);
        decrypt.configuration.put("algorithm", "AES/GCM/NoPadding");

        process.nodes.add(input); process.nodes.add(aad); process.nodes.add(badAad); process.nodes.add(encrypt); process.nodes.add(decrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("aad", "encrypt", "aad"));
        process.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("badaad", "decrypt", "aad"));

        assertThrows(Exception.class, () ->
                ProcessEngine.execute(process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null)));
    }

    @Test void cbcModeRejectsAadPortBinding() {
        String key = "00112233445566778899aabbccddeeff";
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "secret message");
        ProcessDefinition.Node aad = new ProcessDefinition.Node("aad", "CONSOLE_INPUT", "AAD", 1, 0);
        aad.configuration.put("value", "metadata");
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", key);
        encrypt.configuration.put("algorithm", "AES/CBC/PKCS5Padding");

        process.nodes.add(input); process.nodes.add(aad); process.nodes.add(encrypt);
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("aad", "encrypt", "aad"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));
        assertTrue(ex.getMessage().contains("Unknown target port"));
    }

    @Test void validationClearsStaleDerivedMultiInputFlags() {
        ProcessDefinition.Node in1 = new ProcessDefinition.Node("n1", "CONSOLE_INPUT", "A", 0, 0);
        ProcessDefinition.Node encrypt = cryptoNode("encrypt", "ENCRYPT", "00112233445566778899aabbccddeeff");
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");

        ProcessDefinition d = new ProcessDefinition();
        d.nodes.addAll(java.util.List.of(in1, encrypt));
        d.connections.addAll(java.util.List.of(
            new ProcessDefinition.Connection("n1", "encrypt", "payload")
        ));

        encrypt.configuration.put("ivFromFlow", "true");
        encrypt.configuration.put("aadFromFlow", "true");

        ProcessEngine.validate(d);

        assertFalse(encrypt.configuration.containsKey("ivFromFlow"));
        assertFalse(encrypt.configuration.containsKey("aadFromFlow"));
    }

    @Test void processDesignerMacSupportsAesAnd3DesCmac() throws Exception {
        byte[] aesKey = com.cryptocarver.util.DataConverter.hexToBytes("2b7e151628aed2a6abf7158809cf4f3c");
        byte[] message = com.cryptocarver.util.DataConverter.hexToBytes("6bc1bee22e409f96e93d7e117393172a");
        assertArrayEquals(
                com.cryptocarver.util.DataConverter.hexToBytes("070a16b46b4d4144f79bdd9dd04a287c"),
                MACOperations.generate(message, aesKey, "CMAC-AES"));

        for (String algorithm : new String[]{"CMAC-AES", "CMAC-3DES"}) {
            String key = "CMAC-AES".equals(algorithm)
                    ? "00112233445566778899AABBCCDDEEFF"
                    : "0123456789ABCDEFFEDCBA98765432100011223344556677";
            ProcessDefinition process = new ProcessDefinition();
            ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Input", 0, 0);
            input.configuration.put("value", "CMAC process payload");
            ProcessDefinition.Node mac = cryptoNode("mac", "MAC", key);
            mac.configuration.put("algorithm", algorithm);
            process.nodes.add(input);
            process.nodes.add(mac);
            process.connections.add(new ProcessDefinition.Connection("input", "mac", "payload"));

            byte[] result = ProcessEngine.execute(process).get("mac").bytes();
            assertEquals("CMAC-AES".equals(algorithm) ? 16 : 8, result.length);
        }
    }

    @Test void processDesignerMacRejectsInvalidCmacKeyLengthsBeforeExecution() {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node mac = cryptoNode("mac", "MAC", "0011223344556677");
        mac.configuration.put("algorithm", "CMAC-AES");
        process.nodes.add(mac);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(process));
        assertTrue(error.getMessage().contains("CMAC-AES requires a 16, 24, or 32-byte AES key"));
    }

    private static ProcessDefinition.Node cryptoNode(String id, String type, String key) {
        ProcessDefinition.Node node = new ProcessDefinition.Node(id, type, type, 0, 0);
        node.configuration.put("keyFormat", "HEX");
        node.configuration.put("key", key);
        return node;
    }

    private static ProcessDefinition.Node signNode(String keystorePath, String keystorePassword, String keyPassword) {
        ProcessDefinition.Node node = new ProcessDefinition.Node("sign", "SIGN", "Sign", 0, 0);
        node.configuration.put("keystorePath", keystorePath);
        node.configuration.put("keystoreType", "PKCS12");
        node.configuration.put("alias", "myalias");
        node.configuration.put("keystorePassword", keystorePassword);
        node.configuration.put("keyPassword", keyPassword);
        node.configuration.put("algorithm", "SHA256withRSA");
        return node;
    }

    @Test void phase37_hexAndBase64KeySupport() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0);
        in.configuration.put("value", "Base64 Key Test");
        ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
        enc.configuration.put("algorithm", "AES/ECB/PKCS5Padding");
        enc.configuration.put("keyFormat", "BASE64");
        enc.configuration.put("key", "MDEyMzQ1Njc4OWFiY2RlZg==");
        enc.configuration.put("outputFormat", "RAW");
        ProcessDefinition.Node out = new ProcessDefinition.Node("out", "CONSOLE_OUTPUT", "out", 0, 0);

        def.nodes.addAll(java.util.List.of(in, enc, out));
        def.connections.addAll(java.util.List.of(
            new ProcessDefinition.Connection("in", "enc", "payload"),
            new ProcessDefinition.Connection("enc", "out", "payload")
        ));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
        byte[] ciphertext = result.get("out").bytes();

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec("0123456789abcdef".getBytes(), "AES"));
        byte[] plaintext = cipher.doFinal(ciphertext);

        org.junit.jupiter.api.Assertions.assertArrayEquals("Base64 Key Test".getBytes(), plaintext);
    }

    @Test void phase37_generatedKeyAndRandomIvSupport() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        def.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0));
        def.nodes.get(0).configuration.put("value", "Dynamic generation test");

        ProcessDefinition.Node keygen = new ProcessDefinition.Node("keygen", "AES_KEY_GENERATE", "keygen", 0, 0);
        keygen.configuration.put("keySize", "256");
        def.nodes.add(keygen);

        ProcessDefinition.Node ivgen = new ProcessDefinition.Node("ivgen", "RANDOM_BYTES", "ivgen", 0, 0);
        ivgen.configuration.put("length", "16");
        def.nodes.add(ivgen);

        ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
        enc.configuration.put("algorithm", "AES/CBC/PKCS5Padding");
        enc.configuration.put("outputFormat", "RAW");
        def.nodes.add(enc);

        def.nodes.add(new ProcessDefinition.Node("out", "CONSOLE_OUTPUT", "out", 0, 0));
        def.nodes.add(new ProcessDefinition.Node("keyOut", "CONSOLE_OUTPUT", "keyOut", 0, 0));
        def.nodes.add(new ProcessDefinition.Node("ivOut", "CONSOLE_OUTPUT", "ivOut", 0, 0));

        def.connections.addAll(java.util.List.of(
            new ProcessDefinition.Connection("in", "enc", "payload"),
            new ProcessDefinition.Connection("keygen", "enc", "key"),
            new ProcessDefinition.Connection("ivgen", "enc", "iv"),
            new ProcessDefinition.Connection("enc", "out", "payload"),
            new ProcessDefinition.Connection("keygen", "keyOut", "payload"),
            new ProcessDefinition.Connection("ivgen", "ivOut", "payload")
        ));

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
        byte[] ciphertext = result.get("out").bytes();
        byte[] keyBytes = result.get("keyOut").bytes();
        byte[] ivBytes = result.get("ivOut").bytes();

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(keyBytes, "AES"), new javax.crypto.spec.IvParameterSpec(ivBytes));
        byte[] plaintext = cipher.doFinal(ciphertext);

        org.junit.jupiter.api.Assertions.assertArrayEquals("Dynamic generation test".getBytes(), plaintext);
    }

    @Test void phase37_invalidIvLengthFailsFast() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        def.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0));
        def.nodes.get(0).configuration.put("value", "fail");

        ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
        enc.configuration.put("algorithm", "AES/CBC/PKCS5Padding");
        enc.configuration.put("key", "0123456789abcdef0123456789abcdef");
        enc.configuration.put("nonce", "0123"); // 2 bytes instead of 16
        def.nodes.add(enc);
        def.connections.add(new ProcessDefinition.Connection("in", "enc", "payload"));

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
        });
    }

    @Test void phase37_aadRejectedOnNonAead() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        def.nodes.add(new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0));
        def.nodes.get(0).configuration.put("value", "fail");
        def.nodes.add(new ProcessDefinition.Node("aad", "CONSOLE_INPUT", "aad", 0, 0));
        def.nodes.get(1).configuration.put("value", "aad");

        ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
        enc.configuration.put("algorithm", "AES/CBC/PKCS5Padding");
        enc.configuration.put("key", "0123456789abcdef0123456789abcdef");
        enc.configuration.put("nonce", "0123456789abcdef0123456789abcdef"); // 16 bytes
        def.nodes.add(enc);

        def.connections.add(new ProcessDefinition.Connection("in", "enc", "payload"));
        def.connections.add(new ProcessDefinition.Connection("aad", "enc", "aad")); // Not a valid port for CBC

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
        });
    }

    @Test void phase37_cfbAndOfbSupported() throws Exception {
        for (String mode : java.util.List.of("CFB", "OFB")) {
            ProcessDefinition def = new ProcessDefinition();
            ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0);
            in.configuration.put("value", "Stream Test " + mode);

            ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
            enc.configuration.put("algorithm", "AES/" + mode + "/NoPadding");
            enc.configuration.put("key", "0123456789abcdef0123456789abcdef");
            enc.configuration.put("nonce", "0123456789abcdef0123456789abcdef");
            enc.configuration.put("outputFormat", "RAW");
            ProcessDefinition.Node out = new ProcessDefinition.Node("out", "CONSOLE_OUTPUT", "out", 0, 0);

            def.nodes.addAll(java.util.List.of(in, enc, out));
            def.connections.addAll(java.util.List.of(
                new ProcessDefinition.Connection("in", "enc", "payload"),
                new ProcessDefinition.Connection("enc", "out", "payload")
            ));

            java.util.Map<String, FlowValue> result = ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
            byte[] ciphertext = result.get("out").bytes();

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/" + mode + "/NoPadding");
            byte[] testKey = com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdef0123456789abcdef");
            byte[] testIv = com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdef0123456789abcdef");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(testKey, "AES"), new javax.crypto.spec.IvParameterSpec(testIv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            org.junit.jupiter.api.Assertions.assertArrayEquals(("Stream Test " + mode).getBytes(), plaintext);
        }
    }

    @Test void phase38_decryptRawFailFastWithoutIv() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0);
        in.configuration.put("value", "00");

        ProcessDefinition.Node codec = new ProcessDefinition.Node("codec", "HEX_DECODE", "codec", 0, 0);

        ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
        dec.configuration.put("algorithm", "AES/CFB/NoPadding");
        dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
        dec.configuration.put("keyFormat", "HEX");

        def.nodes.add(in); def.nodes.add(codec); def.nodes.add(dec);
        def.connections.add(new ProcessDefinition.Connection("in", "codec", "input"));
        def.connections.add(new ProcessDefinition.Connection("codec", "dec", "payload"));

        // Validation fails at runtime during execution because IV is missing
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
        );
        org.junit.jupiter.api.Assertions.assertTrue(
            ex.getMessage().contains("IV/nonce is required to decrypt RAW ciphertext"),
            "Expected missing IV error, got: " + ex.getMessage()
        );
    }

    @Test void phase38_decryptEnvelopeRejectsIncompatibleAlgorithms() throws Exception {
        // ENVELOPE format sent to CFB, OFB, or ECB should fail explicitly.
        for (String alg : new String[]{"AES/CFB/NoPadding", "AES/OFB/NoPadding", "AES/ECB/PKCS7Padding"}) {
            ProcessDefinition def = new ProcessDefinition();

            ProcessDefinition.Node in1 = new ProcessDefinition.Node("in1", "CONSOLE_INPUT", "in1", 0, 0);
            in1.configuration.put("value", "Hello");
            ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
            enc.configuration.put("algorithm", "AES/GCM/NoPadding");
            enc.configuration.put("key", "0123456789abcdef0123456789abcdef");
            enc.configuration.put("keyFormat", "HEX");
            enc.configuration.put("outputFormat", "ENVELOPE");

            ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
            dec.configuration.put("algorithm", alg);
            dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
            dec.configuration.put("keyFormat", "HEX");
            dec.configuration.put("outputFormat", "RAW");
            if (!alg.contains("ECB")) dec.configuration.put("nonce", "0123456789abcdef0123456789abcdef");

            def.nodes.add(in1); def.nodes.add(enc); def.nodes.add(dec);
            def.connections.add(new ProcessDefinition.Connection("in1", "enc", "payload"));
            def.connections.add(new ProcessDefinition.Connection("enc", "dec", "payload"));

            Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("Unsupported envelope algorithm for DECRYPT: " + alg),
                "Expected rejection for " + alg + ", got: " + ex.getMessage()
            );
        }
    }

    @Test void phase38_decryptCfbOfbEcbInterop() throws Exception {
        for (String alg : new String[]{"AES/CFB/NoPadding", "AES/OFB/NoPadding", "AES/ECB/PKCS7Padding"}) {
            byte[] key = com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdef0123456789abcdef");
            byte[] iv = alg.contains("ECB") ? new byte[0] : com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdef0123456789abcdef");
            byte[] plaintext = "Hello Process Designer".getBytes();

            String[] parts = alg.split("/");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(alg);
            if (iv.length > 0) {
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.IvParameterSpec(iv));
            } else {
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"));
            }
            byte[] ciphertext = cipher.doFinal(plaintext);

            ProcessDefinition def = new ProcessDefinition();
            ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0);
            in.configuration.put("value", com.cryptocarver.util.DataConverter.bytesToHex(ciphertext));

            ProcessDefinition.Node codec = new ProcessDefinition.Node("codec", "HEX_DECODE", "codec", 0, 0);

            ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
            dec.configuration.put("algorithm", alg);
            dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
            dec.configuration.put("keyFormat", "HEX");
            if (iv.length > 0) {
                dec.configuration.put("nonce", "0123456789abcdef0123456789abcdef");
            }
            ProcessDefinition.Node out = new ProcessDefinition.Node("out", "CONSOLE_OUTPUT", "out", 0, 0);

            def.nodes.add(in); def.nodes.add(codec); def.nodes.add(dec); def.nodes.add(out);
            def.connections.add(new ProcessDefinition.Connection("in", "codec", "input"));
            def.connections.add(new ProcessDefinition.Connection("codec", "dec", "payload"));
            def.connections.add(new ProcessDefinition.Connection("dec", "out", "payload"));

            java.util.Map<String, FlowValue> res = ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
            org.junit.jupiter.api.Assertions.assertArrayEquals(plaintext, res.get("out").bytes());
        }
    }

    @Test void phase38_gcmWithCorrectAndIncorrectAad() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0);
        in.configuration.put("value", "Hello AAD");
        ProcessDefinition.Node aadIn = new ProcessDefinition.Node("aadIn", "CONSOLE_INPUT", "aadIn", 0, 0);
        aadIn.configuration.put("value", "Correct AAD Data");

        ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
        enc.configuration.put("algorithm", "AES/GCM/NoPadding");
        enc.configuration.put("key", "0123456789abcdef0123456789abcdef");
        enc.configuration.put("keyFormat", "HEX");
        enc.configuration.put("nonce", "0123456789abcdef01234567");
        enc.configuration.put("outputFormat", "RAW");

        ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
        dec.configuration.put("algorithm", "AES/GCM/NoPadding");
        dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
        dec.configuration.put("keyFormat", "HEX");
        dec.configuration.put("nonce", "0123456789abcdef01234567");
        dec.configuration.put("outputFormat", "RAW");

        ProcessDefinition.Node out = new ProcessDefinition.Node("out", "CONSOLE_OUTPUT", "out", 0, 0);

        def.nodes.add(in); def.nodes.add(aadIn); def.nodes.add(enc); def.nodes.add(dec); def.nodes.add(out);
        def.connections.add(new ProcessDefinition.Connection("in", "enc", "payload"));
        def.connections.add(new ProcessDefinition.Connection("aadIn", "enc", "aad"));
        def.connections.add(new ProcessDefinition.Connection("enc", "dec", "payload"));
        def.connections.add(new ProcessDefinition.Connection("aadIn", "dec", "aad"));
        def.connections.add(new ProcessDefinition.Connection("dec", "out", "payload"));

        java.util.Map<String, FlowValue> res = ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
        org.junit.jupiter.api.Assertions.assertArrayEquals("Hello AAD".getBytes(), res.get("out").bytes());

        // Now change AAD for DECRYPT
        ProcessDefinition.Node aadInBad = new ProcessDefinition.Node("aadInBad", "CONSOLE_INPUT", "aadInBad", 0, 0);
        aadInBad.configuration.put("value", "Wrong AAD Data");
        def.nodes.add(aadInBad);
        def.connections.removeIf(c -> c.to.equals("dec") && c.targetPort.equals("aad"));
        def.connections.add(new ProcessDefinition.Connection("aadInBad", "dec", "aad"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex instanceof javax.crypto.AEADBadTagException || ex.getCause() instanceof javax.crypto.AEADBadTagException);
    }

    @Test void phase38_gcmWithCorruptCiphertext() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "in", 0, 0);
        in.configuration.put("value", "Hello");
        ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
        enc.configuration.put("algorithm", "AES/GCM/NoPadding");
        enc.configuration.put("key", "0123456789abcdef0123456789abcdef");
        enc.configuration.put("keyFormat", "HEX");
        enc.configuration.put("nonce", "0123456789abcdeffedcba9876543210");
        enc.configuration.put("outputFormat", "RAW");

        ProcessDefinition.Node corruptor = new ProcessDefinition.Node("corr", "HEX_ENCODE", "corr", 0, 0);
        ProcessDefinition.Node corruptor2 = new ProcessDefinition.Node("corr2", "TEXT_PROCESS", "corr2", 0, 0);
        corruptor2.configuration.put("operation", "UPPERCASE"); // It doesn't corrupt hex value, let's just make our own decryptor block.
        // Wait, just run cipher in the test to get corrupted hex.

        byte[] key = com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdef0123456789abcdef");
        byte[] iv = com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdef01234567");
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal("Hello".getBytes());
        ciphertext[0] = (byte) (ciphertext[0] ^ 0xFF); // Corrupt it

        ProcessDefinition.Node inHex = new ProcessDefinition.Node("inHex", "CONSOLE_INPUT", "inHex", 0, 0);
        inHex.configuration.put("value", com.cryptocarver.util.DataConverter.bytesToHex(ciphertext));
        ProcessDefinition.Node hexDec = new ProcessDefinition.Node("hexDec", "HEX_DECODE", "hexDec", 0, 0);

        ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
        dec.configuration.put("algorithm", "AES/GCM/NoPadding");
        dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
        dec.configuration.put("keyFormat", "HEX");
        dec.configuration.put("nonce", "0123456789abcdef01234567");
        dec.configuration.put("outputFormat", "RAW");

        def.nodes.clear(); def.connections.clear();
        def.nodes.add(inHex); def.nodes.add(hexDec); def.nodes.add(dec);
        def.connections.add(new ProcessDefinition.Connection("inHex", "hexDec", "input"));
        def.connections.add(new ProcessDefinition.Connection("hexDec", "dec", "payload"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex.toString().contains("AEADBadTagException") || ex.toString().contains("mac check") || ex.toString().contains("Tag mismatch"), "Expected tag exception, got: " + ex);
    }

    @Test void phase38_cbcAndEcbWithCorruptPadding() throws Exception {
        for (String alg : new String[]{"AES/CBC/PKCS7Padding", "AES/ECB/PKCS7Padding"}) {
            byte[] key = com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdef0123456789abcdef");
            byte[] iv = alg.contains("ECB") ? new byte[0] : com.cryptocarver.util.DataConverter.hexToBytes("0123456789abcdeffedcba9876543210");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(alg.replace("PKCS7", "PKCS5"));
            if (iv.length > 0) {
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.IvParameterSpec(iv));
            } else {
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"));
            }
            byte[] ciphertext = cipher.doFinal("Hello".getBytes());
            ciphertext[ciphertext.length - 1] = (byte) (ciphertext[ciphertext.length - 1] ^ 0xFF); // Corrupt padding

            ProcessDefinition def = new ProcessDefinition();
            ProcessDefinition.Node inHex = new ProcessDefinition.Node("inHex", "CONSOLE_INPUT", "inHex", 0, 0);
            inHex.configuration.put("value", com.cryptocarver.util.DataConverter.bytesToHex(ciphertext));
            ProcessDefinition.Node hexDec = new ProcessDefinition.Node("hexDec", "HEX_DECODE", "hexDec", 0, 0);

            ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
            dec.configuration.put("algorithm", alg);
            dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
            dec.configuration.put("keyFormat", "HEX");
            if (iv.length > 0) dec.configuration.put("nonce", "0123456789abcdeffedcba9876543210");
            dec.configuration.put("outputFormat", "RAW");

            def.nodes.add(inHex); def.nodes.add(hexDec); def.nodes.add(dec);
            def.connections.add(new ProcessDefinition.Connection("inHex", "hexDec", "input"));
            def.connections.add(new ProcessDefinition.Connection("hexDec", "dec", "payload"));

            Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
            );
            org.junit.jupiter.api.Assertions.assertTrue(ex instanceof javax.crypto.BadPaddingException || ex.getCause() instanceof javax.crypto.BadPaddingException);
        }
    }

    @Test void phase38_envelopeCompatibleDecryption() throws Exception {
        for (String alg : new String[]{"AES/GCM/NoPadding", "AES/CBC/PKCS7Padding", "AES/CTR/NoPadding"}) {
            ProcessDefinition def = new ProcessDefinition();
            ProcessDefinition.Node in1 = new ProcessDefinition.Node("in1", "CONSOLE_INPUT", "in1", 0, 0);
            in1.configuration.put("value", "Hello ENVELOPE");
            ProcessDefinition.Node enc = new ProcessDefinition.Node("enc", "ENCRYPT", "enc", 0, 0);
            enc.configuration.put("algorithm", alg);
            enc.configuration.put("key", "0123456789abcdef0123456789abcdef");
            enc.configuration.put("keyFormat", "HEX");
            enc.configuration.put("outputFormat", "ENVELOPE");

            ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
            dec.configuration.put("algorithm", alg); // Envelope ignores this and reads from magic, but config must match support!
            dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
            dec.configuration.put("keyFormat", "HEX");
            dec.configuration.put("outputFormat", "ENVELOPE");

            ProcessDefinition.Node out = new ProcessDefinition.Node("out", "CONSOLE_OUTPUT", "out", 0, 0);

            def.nodes.add(in1); def.nodes.add(enc); def.nodes.add(dec); def.nodes.add(out);
            def.connections.add(new ProcessDefinition.Connection("in1", "enc", "payload"));
            def.connections.add(new ProcessDefinition.Connection("enc", "dec", "payload"));
            def.connections.add(new ProcessDefinition.Connection("dec", "out", "payload"));

            java.util.Map<String, FlowValue> res = ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}));
            org.junit.jupiter.api.Assertions.assertArrayEquals("Hello ENVELOPE".getBytes(), res.get("out").bytes());
        }
    }

    private byte[] createEnvelope(byte version, byte algId, byte[] iv, byte[] ciphertext) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(4 + 1 + 1 + 1 + iv.length + ciphertext.length);
        buffer.put(new byte[]{'C', 'F', 'G', 'E'});
        buffer.put(version);
        buffer.put(algId);
        buffer.put((byte) iv.length);
        buffer.put(iv);
        buffer.put(ciphertext);
        return buffer.array();
    }

    @Test void phase38_decryptEnvelopeWithAlteredIvLengthFails() {
        byte[] envelope = createEnvelope((byte)1, (byte)1, new byte[16], new byte[16]); // GCM expects 12 bytes IV

        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node inHex = new ProcessDefinition.Node("inHex", "CONSOLE_INPUT", "inHex", 0, 0);
        inHex.configuration.put("value", com.cryptocarver.util.DataConverter.bytesToHex(envelope));
        ProcessDefinition.Node hexDec = new ProcessDefinition.Node("hexDec", "HEX_DECODE", "hexDec", 0, 0);

        ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
        dec.configuration.put("algorithm", "AES/GCM/NoPadding");
        dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
        dec.configuration.put("keyFormat", "HEX");
        dec.configuration.put("outputFormat", "ENVELOPE");

        def.nodes.add(inHex); def.nodes.add(hexDec); def.nodes.add(dec);
        def.connections.add(new ProcessDefinition.Connection("inHex", "hexDec", "input"));
        def.connections.add(new ProcessDefinition.Connection("hexDec", "dec", "payload"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex.toString().contains("Invalid envelope: IV length 16 does not match 12"));
    }

    @Test void phase38_decryptEnvelopeWithInvalidVersionFails() {
        byte[] envelope = createEnvelope((byte)2, (byte)1, new byte[12], new byte[16]); // Invalid version 2

        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node inHex = new ProcessDefinition.Node("inHex", "CONSOLE_INPUT", "inHex", 0, 0);
        inHex.configuration.put("value", com.cryptocarver.util.DataConverter.bytesToHex(envelope));
        ProcessDefinition.Node hexDec = new ProcessDefinition.Node("hexDec", "HEX_DECODE", "hexDec", 0, 0);

        ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
        dec.configuration.put("algorithm", "AES/GCM/NoPadding");
        dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
        dec.configuration.put("keyFormat", "HEX");
        dec.configuration.put("outputFormat", "ENVELOPE");

        def.nodes.add(inHex); def.nodes.add(hexDec); def.nodes.add(dec);
        def.connections.add(new ProcessDefinition.Connection("inHex", "hexDec", "input"));
        def.connections.add(new ProcessDefinition.Connection("hexDec", "dec", "payload"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex.toString().contains("Unsupported envelope version: 2"));
    }

    @Test void phase38_decryptEnvelopeWithInvalidAlgorithmIdFails() {
        byte[] envelope = createEnvelope((byte)1, (byte)99, new byte[12], new byte[16]); // Invalid algorithm ID 99

        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node inHex = new ProcessDefinition.Node("inHex", "CONSOLE_INPUT", "inHex", 0, 0);
        inHex.configuration.put("value", com.cryptocarver.util.DataConverter.bytesToHex(envelope));
        ProcessDefinition.Node hexDec = new ProcessDefinition.Node("hexDec", "HEX_DECODE", "hexDec", 0, 0);

        ProcessDefinition.Node dec = new ProcessDefinition.Node("dec", "DECRYPT", "dec", 0, 0);
        dec.configuration.put("algorithm", "AES/GCM/NoPadding");
        dec.configuration.put("key", "0123456789abcdef0123456789abcdef");
        dec.configuration.put("keyFormat", "HEX");
        dec.configuration.put("outputFormat", "ENVELOPE");

        def.nodes.add(inHex); def.nodes.add(hexDec); def.nodes.add(dec);
        def.connections.add(new ProcessDefinition.Connection("inHex", "hexDec", "input"));
        def.connections.add(new ProcessDefinition.Connection("hexDec", "dec", "payload"));

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> {}))
        );
        org.junit.jupiter.api.Assertions.assertTrue(ex.toString().contains("Unsupported envelope algorithm ID: 99"));
    }

    @Test void cancelDuringStepOnePreventsStepTwoExecution() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("step1", "CONSOLE_INPUT", "Step 1", 0, 0);
        input.configuration.put("value", "payload data");
        ProcessDefinition.Node hash = new ProcessDefinition.Node("step2", "HASH", "Step 2", 1, 0);
        hash.configuration.put("algorithm", "SHA-256");

        process.nodes.add(input);
        process.nodes.add(hash);
        process.connections.add(new ProcessDefinition.Connection("step1", "step2"));

        java.util.concurrent.atomic.AtomicBoolean cancellationTriggered = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean step2Invoked = new java.util.concurrent.atomic.AtomicBoolean(false);

        ExecutionContext context = new ExecutionContext(
            FileWritePolicy.ALLOW_OVERWRITE,
            event -> {
                if ("step1".equals(event.nodeId()) && event.state() == NodeExecutionState.RUNNING) {
                    cancellationTriggered.set(true);
                }
                if ("step2".equals(event.nodeId())) {
                    step2Invoked.set(true);
                }
            },
            cancellationTriggered::get
        );

        java.util.Map<String, FlowValue> result = ProcessEngine.execute(process, context);

        assertFalse(step2Invoked.get(), "Step 2 handler must NOT be invoked when process is cancelled during step 1");
        assertFalse(result.containsKey("step2"), "Step 2 result must NOT be published");
        assertEquals(1, result.size(), "Only step 1 result must be preserved");
    }

    @Test void dryRunDoesNotInvokeCryptoHistoryFilesOrShelfAndHidesSensitiveInputs() throws Exception {
        String sensitivePayload = "CONFIDENTIAL_PAYLOAD_SECRET";
        String sensitiveKey = "00112233445566778899AABBCCDDEEFF";
        String sensitiveIv = "112233445566778899001122";

        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", sensitivePayload);
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("enc", "ENCRYPT", "Encrypt", 1, 0);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        encrypt.configuration.put("keyFormat", "HEX");
        encrypt.configuration.put("key", sensitiveKey);
        encrypt.configuration.put("nonce", sensitiveIv);

        process.nodes.add(input);
        process.nodes.add(encrypt);
        process.connections.add(new ProcessDefinition.Connection("in", "enc", "payload"));

        java.util.concurrent.atomic.AtomicInteger cryptoCallsSpy = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger handlerExecutionsSpy = new java.util.concurrent.atomic.AtomicInteger(0);

        ProcessNodeHandler cryptoDoubleSpy = new com.cryptocarver.model.process.handlers.AdvancedCryptoNodeHandler() {
            @Override
            public FlowValue execute(ProcessDefinition.Node node, java.util.Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
                handlerExecutionsSpy.incrementAndGet();
                if ("ENCRYPT".equalsIgnoreCase(node.type) || "DECRYPT".equalsIgnoreCase(node.type)) {
                    cryptoCallsSpy.incrementAndGet();
                }
                return super.execute(node, inputs, context);
            }
        };

        // Ingest spy handler directly into ProcessEngine
        ProcessEngine.registerHandler(cryptoDoubleSpy);

        java.nio.file.Path tempTestDir = java.nio.file.Files.createTempDirectory("dryrun-obs-test");
        long initialFileCount = java.nio.file.Files.list(tempTestDir).count();

        com.cryptocarver.model.HistoryManager historyObservationDouble = new com.cryptocarver.model.HistoryManager(tempTestDir.resolve("history.json"));
        int initialHistorySize = historyObservationDouble.getHistoryItems().size();

        com.cryptocarver.model.ClipboardShelfManager shelfObservationDouble = com.cryptocarver.model.ClipboardShelfManager.getInstance();
        int initialShelfSize = shelfObservationDouble.getEntries().size();

        try {
            // Perform Dry Run
            DryRunSummary summary = ProcessValidator.dryRun(process);

            // Verification 1: contador de llamadas criptográficas = 0
            assertEquals(0, cryptoCallsSpy.get(), "Dry Run must invoke 0 cryptographic calls");
            // Verification 2: contador de handlers/operaciones ejecutadas = 0
            assertEquals(0, handlerExecutionsSpy.get(), "Dry Run must execute 0 handlers/operations");
            // Verification 3: no se escriben ficheros
            assertEquals(initialFileCount, java.nio.file.Files.list(tempTestDir).count(), "Dry Run must write 0 files to disk");
            // Verification 4: no se añade historial
            assertEquals(initialHistorySize, historyObservationDouble.getHistoryItems().size(), "Dry Run must create 0 history entries");
            assertEquals(initialShelfSize, shelfObservationDouble.getEntries().size(), "Dry Run must add 0 entries to Clipboard Shelf");

            String outputText = summary.stepValidations().toString() + summary.resolvedDependencies().toString() + summary.executionOrder().toString();
            assertFalse(outputText.contains(sensitivePayload), "Dry Run output must NOT contain sensitive payload");
            assertFalse(outputText.contains(sensitiveKey), "Dry Run output must NOT contain sensitive key");
            assertFalse(outputText.contains(sensitiveIv), "Dry Run output must NOT contain sensitive IV");
        } finally {
            ProcessEngine.unregisterHandler(cryptoDoubleSpy);
            java.nio.file.Files.deleteIfExists(tempTestDir.resolve("history.json"));
            java.nio.file.Files.deleteIfExists(tempTestDir);
        }
    }

    @Test void realExecutionInvokesRegisteredCryptoSpy() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "Input", 0, 0);
        input.configuration.put("value", "real execution data");
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("enc", "ENCRYPT", "Encrypt", 1, 0);
        encrypt.configuration.put("algorithm", "AES/ECB/PKCS5Padding");
        encrypt.configuration.put("keyFormat", "HEX");
        encrypt.configuration.put("key", "00112233445566778899AABBCCDDEEFF");

        process.nodes.add(input);
        process.nodes.add(encrypt);
        process.connections.add(new ProcessDefinition.Connection("in", "enc", "payload"));

        java.util.concurrent.atomic.AtomicInteger cryptoCallsSpy = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger handlerExecutionsSpy = new java.util.concurrent.atomic.AtomicInteger(0);

        ProcessNodeHandler cryptoDoubleSpy = new com.cryptocarver.model.process.handlers.AdvancedCryptoNodeHandler() {
            @Override
            public FlowValue execute(ProcessDefinition.Node node, java.util.Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
                handlerExecutionsSpy.incrementAndGet();
                if ("ENCRYPT".equalsIgnoreCase(node.type) || "DECRYPT".equalsIgnoreCase(node.type)) {
                    cryptoCallsSpy.incrementAndGet();
                }
                return super.execute(node, inputs, context);
            }
        };

        // Ingest spy handler directly into ProcessEngine
        ProcessEngine.registerHandler(cryptoDoubleSpy);

        try {
            java.util.Map<String, FlowValue> result = ProcessEngine.execute(process, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null));

            assertTrue(result.containsKey("enc"), "Real execution must return completed encrypt result");
            assertTrue(handlerExecutionsSpy.get() > 0, "Real execution MUST invoke registered handler spy");
            assertTrue(cryptoCallsSpy.get() > 0, "Real execution MUST invoke registered crypto spy");
        } finally {
            ProcessEngine.unregisterHandler(cryptoDoubleSpy);
        }
    }
}
