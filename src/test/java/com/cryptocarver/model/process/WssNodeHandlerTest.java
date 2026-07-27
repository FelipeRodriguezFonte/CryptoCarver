package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WssNodeHandlerTest {

    @Test
    void encryptDecryptSoapBodyRoundTrip() throws Exception {
        ProcessDefinition process = roundTripProcess();

        Map<String, FlowValue> values = ProcessEngine.execute(process);

        assertEquals(Representation.TEXT_UTF8, values.get("encrypt").representation());
        assertTrue(values.get("encrypt").render().contains("EncryptedData"));
        assertFalse(values.get("encrypt").render().contains("SensitivePayload"));
        assertTrue(values.get("decrypt").render().contains("SensitivePayload"));
    }

    @Test
    void configurationFailsFastAndSecretsAreNotSerialized() {
        ProcessDefinition process = roundTripProcess();
        ProcessDefinition.Node decrypt = process.nodes.stream()
                .filter(node -> "decrypt".equals(node.id)).findFirst().orElseThrow();
        decrypt.configuration.put("keystorePassword", "visible-store-secret");
        decrypt.configuration.put("keyPassword", "visible-key-secret");

        String json = ProcessDefinitionCodec.serialize(process);
        assertFalse(json.contains("visible-store-secret"));
        assertFalse(json.contains("visible-key-secret"));

        decrypt.configuration.remove("keyPassword");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ProcessEngine.validate(process));
        assertTrue(error.getMessage().contains("Private key password"));
    }

    @Test
    void binarySoapInputIsRejectedByRepresentationContract() throws Exception {
        ProcessDefinition process = roundTripProcess();
        ProcessDefinition.Node input = process.nodes.stream()
                .filter(node -> "input".equals(node.id)).findFirst().orElseThrow();
        input.type = "FILE_INPUT";
        input.configuration.put("path", Files.createTempFile("wss-binary", ".xml").toString());
        input.configuration.put("readMode", "BINARY");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ProcessEngine.validate(process));
        assertTrue(error.getMessage().contains("expects"));
        assertTrue(error.getMessage().contains("TEXT_UTF8"));
    }

    @Test
    void signAndVerifySoapBodyRoundTrip() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = soapInput("input");
        ProcessDefinition.Node sign = new ProcessDefinition.Node(
                "sign", "WSS_SIGN_BODY", "Sign SOAP Body", 0, 0);
        sign.configuration.put("keystorePath", "src/test/resources/testks.p12");
        sign.configuration.put("keystoreType", "PKCS12");
        sign.configuration.put("keystorePassword", "storepass");
        sign.configuration.put("keyPassword", "storepass");
        sign.configuration.put("alias", "myalias");
        sign.configuration.put("signatureAlgorithm", "RSA_SHA256");
        sign.configuration.put("timestampEnabled", "true");
        sign.configuration.put("timestampMinutes", "5");
        sign.configuration.put("timestampSigned", "true");
        ProcessDefinition.Node verify = new ProcessDefinition.Node(
                "verify", "WSS_VERIFY_SIGNATURE", "Verify WSS Signature", 0, 0);
        verify.configuration.put("materialPath", "src/test/resources/testcert.pem");
        process.nodes.addAll(java.util.List.of(input, sign, verify));
        process.connections.add(new ProcessDefinition.Connection("input", "sign", "payload"));
        process.connections.add(new ProcessDefinition.Connection("sign", "verify", "payload"));

        Map<String, FlowValue> values = ProcessEngine.execute(process);

        assertTrue(values.get("sign").render().contains("Signature"));
        assertTrue(values.get("sign").render().contains("Timestamp"));
        assertTrue(values.get("verify").render().startsWith("VALID"));
    }

    @Test
    void addAndVerifyPasswordDigestUsernameToken() throws Exception {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = soapInput("input");
        ProcessDefinition.Node add = new ProcessDefinition.Node(
                "add", "WSS_USERNAME_TOKEN_ADD", "Add UsernameToken", 0, 0);
        add.configuration.put("username", "alice");
        add.configuration.put("wssPassword", "laboratory-password");
        add.configuration.put("passwordType", "PasswordDigest");
        ProcessDefinition.Node verify = new ProcessDefinition.Node(
                "verify", "WSS_USERNAME_TOKEN_VERIFY", "Verify UsernameToken", 0, 0);
        verify.configuration.put("username", "alice");
        verify.configuration.put("wssPassword", "laboratory-password");
        verify.configuration.put("maxAgeSeconds", "300");
        process.nodes.addAll(java.util.List.of(input, add, verify));
        process.connections.add(new ProcessDefinition.Connection("input", "add", "payload"));
        process.connections.add(new ProcessDefinition.Connection("add", "verify", "payload"));

        Map<String, FlowValue> values = ProcessEngine.execute(process);

        assertTrue(values.get("add").render().contains("UsernameToken"));
        assertFalse(values.get("add").render().contains("laboratory-password"));
        assertTrue(values.get("verify").render().startsWith("VALID"));
        assertFalse(ProcessDefinitionCodec.serialize(process).contains("laboratory-password"));
    }

    private static ProcessDefinition roundTripProcess() {
        ProcessDefinition process = new ProcessDefinition();
        ProcessDefinition.Node input = soapInput("input");
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node(
                "encrypt", "WSS_ENCRYPT_BODY", "Encrypt SOAP Body", 0, 0);
        encrypt.configuration.put("materialPath", "src/test/resources/testcert.pem");
        encrypt.configuration.put("dataAlgorithm", "AES-256-GCM");
        encrypt.configuration.put("keyTransportAlgorithm", "RSA-OAEP SHA-256");
        ProcessDefinition.Node decrypt = new ProcessDefinition.Node(
                "decrypt", "WSS_DECRYPT_BODY", "Decrypt SOAP Body", 0, 0);
        decrypt.configuration.put("keystorePath", "src/test/resources/testks.p12");
        decrypt.configuration.put("keystoreType", "PKCS12");
        decrypt.configuration.put("keystorePassword", "storepass");
        decrypt.configuration.put("keyPassword", "storepass");
        process.nodes.addAll(java.util.List.of(input, encrypt, decrypt));
        process.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        process.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt", "payload"));
        return process;
    }

    private static ProcessDefinition.Node soapInput(String id) {
        ProcessDefinition.Node input = new ProcessDefinition.Node(id, "CONSOLE_INPUT", "SOAP request", 0, 0);
        try {
            input.configuration.put("value", Files.readString(Path.of("src/test/resources/soap_test.xml")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return input;
    }
}
