package com.cryptocarver.model.process;

import com.cryptocarver.crypto.COSEOperations;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.model.process.handlers.JoseCoseNodeHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JoseCoseNodeHandlerTest {
    private final JoseCoseNodeHandler handler = new JoseCoseNodeHandler();
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test void joseNodesMatchFacadeAndRejectTampering() throws Exception {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        ProcessDefinition.Node sign = node("JWS_SIGN", "algorithm", "HS256", "key", KEY);
        String actual = handler.execute(sign, Map.of("payload", FlowValue.text("payload", StandardCharsets.UTF_8)), null).render();
        assertEquals(JOSEService.signJws("payload", "HS256", KEY), actual);
        ProcessDefinition.Node verify = node("JWS_VERIFY", "algorithm", "HS256", "key", KEY);
        assertEquals("payload", handler.execute(verify, Map.of("message", FlowValue.text(actual, StandardCharsets.UTF_8)), null).render());
        String tampered = actual.substring(0, actual.length() - 1) + (actual.endsWith("A") ? "B" : "A");
        assertThrows(Exception.class, () -> handler.execute(verify, Map.of("message", FlowValue.text(tampered, StandardCharsets.UTF_8)), null));

        ProcessDefinition.Node encrypt = node("JWE_ENCRYPT", "keyAlgorithm", "dir", "contentAlgorithm", "A256GCM", "cek", KEY);
        String jwe = handler.execute(encrypt, Map.of("payload", FlowValue.binary(payload)), null).render();
        assertEquals("payload", JOSEService.decryptJwe(jwe, KEY));
        ProcessDefinition.Node decrypt = node("JWE_DECRYPT", "cek", KEY);
        assertEquals("payload", handler.execute(decrypt, Map.of("message", FlowValue.text(jwe, StandardCharsets.UTF_8)), null).render());
        assertThrows(IllegalArgumentException.class, () -> JOSEService.encryptJwe("x", "RSA-OAEP", "A256GCM", KEY));
        assertTrue(handler.execute(node("JWT_INSPECT"), Map.of("message", FlowValue.text(actual, StandardCharsets.UTF_8)), null).render().contains("\"verified\":false"));
    }

    @Test void coseNodesMatchFacadeAndRejectTampering() throws Exception {
        byte[] payload = "cose payload".getBytes(StandardCharsets.UTF_8);
        byte[] key = KEY.getBytes(StandardCharsets.UTF_8);
        ProcessDefinition.Node mac = node("COSE_MAC0", "algorithm", "HS256", "key", KEY);
        byte[] encoded = handler.execute(mac, Map.of("payload", FlowValue.binary(payload)), null).bytes();
        assertArrayEquals(COSEOperations.mac0(payload, key, COSEOperations.MacAlgorithm.HS256), encoded);
        ProcessDefinition.Node verifyMac = node("COSE_VERIFY_MAC0", "key", KEY);
        assertArrayEquals(payload, handler.execute(verifyMac, Map.of("message", FlowValue.binary(encoded)), null).bytes());
        encoded[encoded.length - 1] ^= 1;
        assertThrows(Exception.class, () -> handler.execute(verifyMac, Map.of("message", FlowValue.binary(encoded)), null));

        ProcessDefinition.Node encrypt = node("COSE_ENCRYPT0", "algorithm", "A256GCM", "cek", KEY);
        byte[] encrypted = handler.execute(encrypt, Map.of("payload", FlowValue.binary(payload)), null).bytes();
        assertArrayEquals(payload, handler.execute(node("COSE_DECRYPT0", "cek", KEY), Map.of("message", FlowValue.binary(encrypted)), null).bytes());

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        ProcessDefinition.Node sign = node("COSE_SIGN1", "algorithm", "ES256", "privateKey", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), "publicKey", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        byte[] signed = handler.execute(sign, Map.of("payload", FlowValue.binary(payload)), null).bytes();
        ProcessDefinition.Node verify = node("COSE_VERIFY1", "algorithm", "ES256", "publicKey", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        assertArrayEquals(payload, handler.execute(verify, Map.of("message", FlowValue.binary(signed)), null).bytes());
    }

    @Test void graphFixturesAreSecretFreeAndSelfVerify() throws Exception {
        ProcessDefinition jws = load("ola5b3a_jws_roundtrip.cfprocess.json");
        jws.nodes.stream().filter(n -> n.type.startsWith("JWS_")).forEach(n -> n.configuration.put("key", KEY));
        assertEquals("authenticated payload", ProcessEngine.execute(jws).get("assert").render());

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1")); KeyPair pair = generator.generateKeyPair();
        ProcessDefinition cose = load("ola5b3a_cose_sign1_roundtrip.cfprocess.json");
        cose.nodes.stream().filter(n -> "COSE_SIGN1".equals(n.type)).forEach(n -> { n.configuration.put("privateKey", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())); n.configuration.put("publicKey", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())); });
        cose.nodes.stream().filter(n -> "COSE_VERIFY1".equals(n.type)).forEach(n -> n.configuration.put("publicKey", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
        assertEquals("authenticated COSE payload", new String(ProcessEngine.execute(cose).get("assert").bytes(), StandardCharsets.UTF_8));
        assertFalse(Files.readString(Path.of("src/test/resources/process/ola5b3a_jws_roundtrip.cfprocess.json")).contains(KEY));
    }

    @Test void every5b3aTypeHasDedicatedPreflightFailure() {
        for (String type : JoseCoseNodeHandler.TYPES) {
            ProcessDefinition definition = new ProcessDefinition();
            ProcessDefinition.Node operation = new ProcessDefinition.Node("op", type, type, 0, 0);
            operation.configuration.putAll(NodeCatalog.defaultConfiguration(type));
            definition.nodes.add(operation);
            assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition), type);
        }
    }

    private static ProcessDefinition load(String file) throws Exception { return ProcessDefinitionCodec.deserialize(Files.readString(Path.of("src/test/resources/process", file))); }
    private static ProcessDefinition.Node node(String type, String... values) { ProcessDefinition.Node node = new ProcessDefinition.Node(type, type, type, 0, 0); for (int i = 0; i < values.length; i += 2) node.configuration.put(values[i], values[i + 1]); return node; }
}
