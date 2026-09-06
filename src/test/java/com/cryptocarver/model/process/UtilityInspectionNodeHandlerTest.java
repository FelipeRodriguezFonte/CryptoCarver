package com.cryptocarver.model.process;

import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.model.process.handlers.UtilityInspectionNodeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilityInspectionNodeHandlerTest {

    private UtilityInspectionNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UtilityInspectionNodeHandler();
    }

    @Test
    void testAsn1DecodeBothFormatsAndPreflightRejection() throws Exception {
        // DER sequence containing integer 123 (0x7B): 30 03 02 01 7B
        byte[] der = new byte[]{0x30, 0x03, 0x02, 0x01, 0x7B};

        ProcessDefinition.Node node = new ProcessDefinition.Node("asn1", "ASN1_DECODE", "ASN1", 0, 0);
        node.configuration.put("outputFormat", "JSON");
        FlowValue jsonRes = handler.execute(node, Map.of("input", FlowValue.binary(der)), null);
        assertTrue(jsonRes.render().contains("SEQUENCE") || jsonRes.render().contains("tag"),
                "JSON output should contain tree elements");

        node.configuration.put("outputFormat", "MARKDOWN");
        FlowValue mdRes = handler.execute(node, Map.of("input", FlowValue.binary(der)), null);
        assertNotNull(mdRes.render());
        assertFalse(mdRes.render().isBlank());

        // Preflight validation rejection
        ProcessDefinition.Node invalid = new ProcessDefinition.Node("inv", "ASN1_DECODE", "Inv", 0, 0);
        invalid.configuration.put("outputFormat", "XML");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));
    }

    @Test
    void testCheckDigitCalcAndVerifyForSupportedAlgorithms() throws Exception {
        String base = "12345678";

        for (String alg : CheckDigitCalculator.SUPPORTED_ALGORITHMS) {
            ProcessDefinition.Node calc = new ProcessDefinition.Node("c", "CHECK_DIGIT_CALC", "Calc", 0, 0);
            calc.configuration.put("algorithm", alg);
            FlowValue calcResult = handler.execute(calc, Map.of("input", FlowValue.text(base, StandardCharsets.UTF_8)), null);
            String digit = calcResult.render();
            assertEquals(1, digit.length());

            ProcessDefinition.Node verify = new ProcessDefinition.Node("v", "CHECK_DIGIT_VERIFY", "Verify", 0, 0);
            verify.configuration.put("algorithm", alg);
            FlowValue verifyResult = handler.execute(verify, Map.of("input", FlowValue.text(base + digit, StandardCharsets.UTF_8)), null);
            assertEquals("true", verifyResult.render());

            // Corrupt digit
            int badDigit = (Integer.parseInt(digit) + 1) % 10;
            FlowValue badVerify = handler.execute(verify, Map.of("input", FlowValue.text(base + badDigit, StandardCharsets.UTF_8)), null);
            assertEquals("false", badVerify.render());
        }

        // Preflight validation rejection
        ProcessDefinition.Node invalid = new ProcessDefinition.Node("inv", "CHECK_DIGIT_CALC", "Inv", 0, 0);
        invalid.configuration.put("algorithm", "UnknownAlg");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));
    }

    @Test
    void testModularArithmeticOperationsAndPreflightRejection() throws Exception {
        ProcessDefinition.Node node = new ProcessDefinition.Node("mod", "MODULAR_ARITHMETIC", "Mod", 0, 0);
        node.configuration.put("modulus", "1F"); // 31 decimal (prime)

        // Addition: 0A + 10 mod 1F = 1A
        node.configuration.put("operation", "ADD");
        FlowValue addRes = handler.execute(node, Map.of(
                "a", FlowValue.text("0A", StandardCharsets.UTF_8),
                "b", FlowValue.text("10", StandardCharsets.UTF_8)
        ), null);
        assertEquals("1A", addRes.render());

        // Subtraction: 10 - 0A mod 1F = 06
        node.configuration.put("operation", "SUBTRACT");
        FlowValue subRes = handler.execute(node, Map.of(
                "a", FlowValue.text("10", StandardCharsets.UTF_8),
                "b", FlowValue.text("0A", StandardCharsets.UTF_8)
        ), null);
        assertEquals("6", subRes.render());

        // Exponentiation: 2 ^ 3 mod 1F = 8
        node.configuration.put("operation", "EXPONENTIATE");
        FlowValue expRes = handler.execute(node, Map.of(
                "a", FlowValue.text("02", StandardCharsets.UTF_8),
                "b", FlowValue.text("03", StandardCharsets.UTF_8)
        ), null);
        assertEquals("8", expRes.render());

        // GCD
        node.configuration.put("operation", "GCD");
        FlowValue gcdRes = handler.execute(node, Map.of(
                "a", FlowValue.text("0C", StandardCharsets.UTF_8), // 12
                "b", FlowValue.text("10", StandardCharsets.UTF_8)  // 16
        ), null);
        assertEquals("4", gcdRes.render());

        // Preflight validation rejection for invalid modulus
        ProcessDefinition.Node invalid = new ProcessDefinition.Node("inv", "MODULAR_ARITHMETIC", "Inv", 0, 0);
        invalid.configuration.put("operation", "ADD");
        invalid.configuration.put("modulus", "0"); // modulus <= 1
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));

        invalid.configuration.put("modulus", "NOT_A_HEX");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));
    }

    @Test
    void testUuidGenerateAllFormatsAndPreflightRejection() throws Exception {
        ProcessDefinition.Node node = new ProcessDefinition.Node("u", "UUID_GENERATE", "UUID", 0, 0);

        // Standard
        node.configuration.put("format", "STANDARD");
        FlowValue resStd = handler.execute(node, Map.of(), null);
        UUID.fromString(resStd.render()); // verifies valid UUID format

        // Without hyphens
        node.configuration.put("format", "WITHOUT_HYPHENS");
        FlowValue resNoHyphens = handler.execute(node, Map.of(), null);
        assertEquals(32, resNoHyphens.render().length());
        assertFalse(resNoHyphens.render().contains("-"));

        // Uppercase
        node.configuration.put("format", "UPPERCASE");
        FlowValue resUpper = handler.execute(node, Map.of(), null);
        assertEquals(resUpper.render().toUpperCase(), resUpper.render());

        // Preflight rejection
        ProcessDefinition.Node invalid = new ProcessDefinition.Node("inv", "UUID_GENERATE", "Inv", 0, 0);
        invalid.configuration.put("format", "INVALID_FORMAT");
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(invalid));
    }

    @Test
    void testByteStatisticsDiagnosticReport() throws Exception {
        byte[] payload = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x05, 0x05};

        ProcessDefinition.Node node = new ProcessDefinition.Node("stats", "BYTE_STATISTICS", "Stats", 0, 0);
        FlowValue res = handler.execute(node, Map.of("input", FlowValue.binary(payload)), null);

        String out = res.render();
        assertTrue(out.contains("Bytes: 8"));
        assertTrue(out.contains("Shannon entropy:"));
        assertTrue(out.contains("Distinct values:"));
    }

    @Test
    void testGraphIntegrationWithUtilities() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node uuidNode = new ProcessDefinition.Node("uuid", "UUID_GENERATE", "Gen", 0, 0);
        uuidNode.configuration.put("format", "STANDARD");

        ProcessDefinition.Node statsNode = new ProcessDefinition.Node("stats", "BYTE_STATISTICS", "Stats", 0, 0);

        def.nodes.addAll(List.of(uuidNode, statsNode));
        def.connections.add(new ProcessDefinition.Connection("uuid", "stats", "input"));

        Map<String, FlowValue> results = ProcessEngine.execute(def);
        assertTrue(results.containsKey("uuid"));
        assertTrue(results.containsKey("stats"));
        assertTrue(results.get("stats").render().contains("Bytes: 36")); // UUID with hyphens is 36 chars
    }
}
