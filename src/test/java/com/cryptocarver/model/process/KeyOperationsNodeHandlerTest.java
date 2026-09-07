package com.cryptocarver.model.process;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.KeyDerivation;
import com.cryptocarver.crypto.KeyMaterialInspector;
import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.KeyWrapOperations;
import com.cryptocarver.crypto.TR31Operations;
import com.cryptocarver.crypto.icsf.IcsfTokenParser;
import com.cryptocarver.crypto.icsf.IcsfTokenReport;
import com.cryptocarver.crypto.icsf.Origin;
import com.cryptocarver.model.process.handlers.KeyOperationsNodeHandler;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyOperationsNodeHandlerTest {
    private static final KeyOperationsNodeHandler HANDLER = new KeyOperationsNodeHandler();
    private static final byte[] KEY = HexFormat.of().parseHex("00112233445566778899AABBCCDDEEFF");
    private static final byte[] KEK = HexFormat.of().parseHex("000102030405060708090A0B0C0D0E0F");

    private static FlowValue hex(byte[] bytes) {
        return FlowValue.hex(HexFormat.of().withUpperCase().formatHex(bytes).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void knownVectorsDelegateToKeyFacades() throws Exception {
        ProcessDefinition.Node kcv = node("KCV");
        for (String method : List.of("VISA", "IBM", "ATALLA", "ATALLA_R", "FUTUREX", "CMAC", "AES", "SHA256")) {
            kcv.configuration.put("method", method);
            byte[] direct = switch (method) {
                case "VISA" -> KeyOperations.calculateKCV_VISA(KEY);
                case "IBM" -> KeyOperations.calculateKCV_IBM(KEY);
                case "ATALLA" -> KeyOperations.calculateKCV_ATALLA(KEY);
                case "ATALLA_R" -> KeyOperations.calculateKCV_ATALLA_R(KEY);
                case "FUTUREX" -> KeyOperations.calculateKCV_FUTUREX(KEY);
                case "CMAC" -> KeyOperations.calculateKCV_CMAC(KEY);
                case "AES" -> KeyOperations.calculateKCV_AES(KEY);
                default -> KeyOperations.calculateKCV_SHA256(KEY);
            };
            assertEquals(HexFormat.of().withUpperCase().formatHex(direct), HANDLER.execute(kcv, Map.of("key", hex(KEY)), null).render());
        }

        ProcessDefinition.Node adjust = node("PARITY_ADJUST");
        byte[] adjusted = KEY.clone(); KeyOperations.applyOddParity(adjusted);
        assertEquals(HexFormat.of().withUpperCase().formatHex(adjusted), HANDLER.execute(adjust, Map.of("key", hex(KEY)), null).render());
        ProcessDefinition.Node check = node("PARITY_CHECK");
        assertEquals(KeyOperations.detectParity(KEY).toString(), HANDLER.execute(check, Map.of("key", hex(KEY)), null).render());

        byte[][] split = KeyOperations.splitKey(KEY, 3);
        String bundle = HexFormat.of().formatHex(split[0]) + ":" + HexFormat.of().formatHex(split[1]) + ":" + HexFormat.of().formatHex(split[2]);
        ProcessDefinition.Node splitNode = node("KEY_SPLIT_XOR"); splitNode.configuration.put("componentCount", "3");
        String handlerBundle = HANDLER.execute(splitNode, Map.of("key", hex(KEY)), null).render();
        assertArrayEquals(KEY, KeyOperations.combineKeyComponents(java.util.Arrays.stream(handlerBundle.split(":"))
                .map(HexFormat.of()::parseHex).toArray(byte[][]::new)));
        ProcessDefinition.Node combine = node("KEY_COMBINE_XOR");
        assertEquals(HexFormat.of().withUpperCase().formatHex(KeyOperations.combineKeyComponents(split)),
                HANDLER.execute(combine, Map.of("components", FlowValue.hex(bundle.getBytes(StandardCharsets.UTF_8))), null).render());

        byte[] salt = HexFormat.of().parseHex("000102030405060708090A0B0C");
        byte[] info = HexFormat.of().parseHex("F0F1F2F3F4F5F6F7F8F9");
        ProcessDefinition.Node hkdf = node("KDF_HKDF"); hkdf.configuration.put("outputLength", "42");
        assertArrayEquals(KeyDerivation.hkdf(KEY, salt, info, 42, KeyDerivation.getDigest("SHA-256")),
                HANDLER.execute(hkdf, Map.of("ikm", FlowValue.binary(KEY), "salt", FlowValue.binary(salt), "info", FlowValue.binary(info)), null).bytes());
        ProcessDefinition.Node sp = node("KDF_SP800_108");
        assertArrayEquals(KeyDerivation.sp800108Counter(KEY, info, salt, 32, KeyDerivation.getDigest("SHA-256")),
                HANDLER.execute(sp, Map.of("key", FlowValue.binary(KEY), "label", FlowValue.binary(info), "context", FlowValue.binary(salt)), null).bytes());
        ProcessDefinition.Node x963 = node("KDF_X963");
        assertArrayEquals(KeyDerivation.x963(KEY, info, 32, KeyDerivation.getDigest("SHA-256")),
                HANDLER.execute(x963, Map.of("sharedSecret", FlowValue.binary(KEY), "sharedInfo", FlowValue.binary(info)), null).bytes());
        ProcessDefinition.Node scrypt = node("KDF_SCRYPT"); scrypt.configuration.put("N", "16"); scrypt.configuration.put("r", "1"); scrypt.configuration.put("outputLength", "16");
        assertArrayEquals(KeyDerivation.scrypt(KEY, salt, 16, 1, 1, 16),
                HANDLER.execute(scrypt, Map.of("password", FlowValue.binary(KEY), "salt", FlowValue.binary(salt)), null).bytes());
        ProcessDefinition.Node argon = node("KDF_ARGON2"); argon.configuration.put("iterations", "1"); argon.configuration.put("memory", "8192"); argon.configuration.put("outputLength", "16");
        assertArrayEquals(KeyDerivation.argon2(KEY, salt, 1, 8192, 1, 16),
                HANDLER.execute(argon, Map.of("password", FlowValue.binary(KEY), "salt", FlowValue.binary(salt)), null).bytes());

        byte[] rfc3394 = KeyWrapOperations.wrapRfc3394(KEK, KEY);
        byte[] rfc5649 = KeyWrapOperations.wrapRfc5649(KEK, HexFormat.of().parseHex("466F7250617369"));
        assertArrayEquals(rfc3394, HANDLER.execute(node("AES_KEYWRAP_3394"), Map.of("kek", FlowValue.binary(KEK), "keyData", FlowValue.binary(KEY)), null).bytes());
        assertArrayEquals(KEY, HANDLER.execute(node("AES_UNWRAP_3394"), Map.of("kek", FlowValue.binary(KEK), "wrapped", FlowValue.binary(rfc3394)), null).bytes());
        assertArrayEquals(rfc5649, HANDLER.execute(node("AES_KEYWRAP_5649"), Map.of("kek", FlowValue.binary(KEK), "keyData", FlowValue.binary(HexFormat.of().parseHex("466F7250617369"))), null).bytes());
        assertArrayEquals(HexFormat.of().parseHex("466F7250617369"), HANDLER.execute(node("AES_UNWRAP_5649"), Map.of("kek", FlowValue.binary(KEK), "wrapped", FlowValue.binary(rfc5649)), null).bytes());

        String kbpk = "0123456789ABCDEFFEDCBA9876543210";
        String keyHex = "00112233445566778899AABBCCDDEEFF";
        String block = TR31Operations.wrapKey(kbpk, keyHex, "P0", 'B', 'T', 'E', 'N');
        ProcessDefinition.Node tr31 = node("TR31_WRAP");
        assertEquals(block, HANDLER.execute(tr31, Map.of("kbpk", hexString(kbpk), "key", hexString(keyHex)), null).render());
        ProcessDefinition.Node unwrap = node("TR31_UNWRAP");
        assertEquals(keyHex, HANDLER.execute(unwrap, Map.of("kbpk", hexString(kbpk), "keyBlock", FlowValue.text(block, StandardCharsets.UTF_8)), null).render());
        ProcessDefinition.Node header = node("TR31_PARSE_HEADER");
        assertEquals(TR31Operations.parseHeader(block), HANDLER.execute(header, Map.of("keyBlock", FlowValue.text(block, StandardCharsets.UTF_8)), null).render());

        byte[] token = new byte[]{0, 0, 0, 0, 5, 0, 0, 0};
        ProcessDefinition.Node icsf = node("ICSF_TOKEN_PARSE");
        assertEquals(IcsfTokenReport.renderText(IcsfTokenParser.parse(token, Origin.INFER), Origin.INFER, token),
                HANDLER.execute(icsf, Map.of("token", hex(token)), null).render());

        ProcessDefinition.Node pair = node("KEYPAIR_GENERATE"); pair.configuration.put("algorithm", "EdDSA");
        assertTrue(HANDLER.execute(pair, Map.of(), null).bytes().length > 0);
        ProcessDefinition.Node inspect = node("KEY_MATERIAL_INSPECT"); inspect.configuration.put("algorithm", "AES");
        assertEquals(KeyMaterialInspector.describeKey(new SecretKeySpec(KEY, "AES")),
                HANDLER.execute(inspect, Map.of("key", FlowValue.binary(KEY)), null).render());
    }

    @Test
    void everyKeyNodeRejectsIncompleteConfigurationDuringPreflight() {
        List<String> types = List.of("KCV", "KEY_SPLIT_XOR", "KEY_COMBINE_XOR", "PARITY_ADJUST", "PARITY_CHECK",
                "KDF_HKDF", "KDF_SP800_108", "KDF_X963", "KDF_SCRYPT", "KDF_ARGON2",
                "AES_KEYWRAP_3394", "AES_UNWRAP_3394", "AES_KEYWRAP_5649", "AES_UNWRAP_5649",
                "TR31_WRAP", "TR31_UNWRAP", "TR31_PARSE_HEADER", "ICSF_TOKEN_PARSE", "KEY_MATERIAL_INSPECT");
        for (String type : types) {
            ProcessDefinition definition = new ProcessDefinition();
            definition.nodes.add(new ProcessDefinition.Node("n", type, type, 0, 0));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ProcessEngine.validate(definition), "Incomplete node must fail preflight: " + type);
            assertFalse(error.getMessage().matches(".*[0-9A-Fa-f]{8,}.*"), "Preflight must not echo key material: " + type);
        }
        ProcessDefinition pair = new ProcessDefinition();
        ProcessDefinition.Node pairNode = new ProcessDefinition.Node("n", "KEYPAIR_GENERATE", "pair", 0, 0);
        pairNode.configuration.put("algorithm", "not-an-algorithm"); pair.nodes.add(pairNode);
        assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(pair));
    }

    @Test
    void malformedHexIsRejectedBeforeFacadeExecution() {
        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "input", 0, 0);
        input.configuration.put("value", "not hex"); definition.nodes.add(input);
        ProcessDefinition.Node kcv = new ProcessDefinition.Node("kcv", "KCV", "kcv", 0, 0);
        definition.nodes.add(kcv);
        definition.connections.add(new ProcessDefinition.Connection("input", "kcv", "key"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition));
        assertTrue(error.getMessage().contains("expects") || error.getMessage().contains("HEX") || error.getMessage().contains("representation"));
    }

    @Test
    void componentSelectConsumesOnlyTheBundleAndEmitsPlainHex() throws Exception {
        String bundle = "0011223344556677:8899AABBCCDDEEFF:1020304050607080";
        ProcessDefinition.Node select = node("COMPONENT_SELECT");
        select.configuration.put("index", "2");
        FlowValue selected = HANDLER.execute(select,
                Map.of("components", FlowValue.hexComponents(bundle.getBytes(StandardCharsets.UTF_8))), null);
        assertEquals(Representation.HEX, selected.representation());
        assertEquals("8899AABBCCDDEEFF", selected.render());

        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Bundle", 0, 0);
        input.configuration.put("value", bundle);
        ProcessDefinition.Node selectNode = node("COMPONENT_SELECT");
        selectNode.configuration.put("index", "2");
        definition.nodes.add(input); definition.nodes.add(selectNode);
        definition.connections.add(new ProcessDefinition.Connection("input", "select", "components"));
        assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition));
    }

    private static ProcessDefinition.Node node(String type) {
        return new ProcessDefinition.Node(type.toLowerCase(), type, type, 0, 0);
    }

    private static FlowValue hexString(String value) { return FlowValue.hex(value.getBytes(StandardCharsets.UTF_8)); }
}
