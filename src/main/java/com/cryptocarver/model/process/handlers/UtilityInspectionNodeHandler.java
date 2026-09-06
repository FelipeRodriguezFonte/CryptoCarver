package com.cryptocarver.model.process.handlers;

import com.cryptocarver.asn1.ASN1Parser;
import com.cryptocarver.asn1.ASN1TreeExporter;
import com.cryptocarver.asn1.ASN1TreeNode;
import com.cryptocarver.crypto.ByteStatistics;
import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.crypto.ModularArithmetic;
import com.cryptocarver.crypto.UUIDGenerator;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Node handler for utility, inspection and diagnostic operations (Phase 5B.1).
 * Covers: ASN1_DECODE, CHECK_DIGIT_CALC, CHECK_DIGIT_VERIFY,
 * MODULAR_ARITHMETIC, UUID_GENERATE, BYTE_STATISTICS.
 */
public class UtilityInspectionNodeHandler implements ProcessNodeHandler {

    private static final Set<String> TYPES = Set.of(
            "ASN1_DECODE",
            "CHECK_DIGIT_CALC", "CHECK_DIGIT_VERIFY",
            "MODULAR_ARITHMETIC",
            "UUID_GENERATE",
            "BYTE_STATISTICS"
    );

    private static final List<String> ASN1_FORMATS = List.of("JSON", "MARKDOWN");

    private static final List<String> MODULAR_OPERATIONS = List.of(
            "ADD", "SUBTRACT", "MULTIPLY", "EXPONENTIATE",
            "ADDITIVE_INVERSE", "MULTIPLICATIVE_INVERSE", "GCD"
    );

    private static final List<String> UUID_FORMATS = List.of(
            "STANDARD", "WITHOUT_HYPHENS", "UPPERCASE"
    );

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<NodeDescriptor> descriptors() {
        return List.of(
                new NodeDescriptor(
                        "ASN1_DECODE",
                        "Utilities",
                        "module.process.type.asn1Decode",
                        "module.process.desc.asn1Decode",
                        "🌳",
                        List.of(
                                new NodeParameter("outputFormat", "module.process.param.outputFormat", ParameterKind.COMBO, ASN1_FORMATS, "JSON")
                        )
                ),
                new NodeDescriptor(
                        "CHECK_DIGIT_CALC",
                        "Utilities",
                        "module.process.type.checkDigitCalc",
                        "module.process.desc.checkDigitCalc",
                        "🔢",
                        List.of(
                                new NodeParameter("algorithm", "module.process.param.algorithm", ParameterKind.COMBO,
                                        CheckDigitCalculator.SUPPORTED_ALGORITHMS, "Luhn (Mod 10)")
                        )
                ),
                new NodeDescriptor(
                        "CHECK_DIGIT_VERIFY",
                        "Utilities",
                        "module.process.type.checkDigitVerify",
                        "module.process.desc.checkDigitVerify",
                        "✅",
                        List.of(
                                new NodeParameter("algorithm", "module.process.param.algorithm", ParameterKind.COMBO,
                                        CheckDigitCalculator.SUPPORTED_ALGORITHMS, "Luhn (Mod 10)")
                        )
                ),
                new NodeDescriptor(
                        "MODULAR_ARITHMETIC",
                        "Utilities",
                        "module.process.type.modularArithmetic",
                        "module.process.desc.modularArithmetic",
                        "🧮",
                        List.of(
                                new NodeParameter("operation", "module.process.param.operation", ParameterKind.COMBO, MODULAR_OPERATIONS, "ADD"),
                                new NodeParameter("modulus", "module.process.param.modulus", ParameterKind.HEX, "10001")
                        )
                ),
                new NodeDescriptor(
                        "UUID_GENERATE",
                        "Utilities",
                        "module.process.type.uuidGenerate",
                        "module.process.desc.uuidGenerate",
                        "🆔",
                        List.of(
                                new NodeParameter("format", "module.process.param.format", ParameterKind.COMBO, UUID_FORMATS, "STANDARD")
                        )
                ),
                new NodeDescriptor(
                        "BYTE_STATISTICS",
                        "Utilities",
                        "module.process.type.byteStatistics",
                        "module.process.desc.byteStatistics",
                        "📊",
                        List.of()
                )
        );
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        Set<Representation> allReps = Representation.standardValues();
        return switch (node.type) {
            case "ASN1_DECODE", "CHECK_DIGIT_CALC", "CHECK_DIGIT_VERIFY", "BYTE_STATISTICS" -> List.of(
                    new PortDefinition("input", allReps, true)
            );
            case "MODULAR_ARITHMETIC" -> {
                String op = node.configuration.getOrDefault("operation", "ADD").trim().toUpperCase();
                boolean isUnary = "ADDITIVE_INVERSE".equals(op) || "MULTIPLICATIVE_INVERSE".equals(op);
                yield List.of(
                        new PortDefinition("a", allReps, true),
                        new PortDefinition("b", allReps, !isUnary)
                );
            }
            case "UUID_GENERATE" -> List.of();
            default -> List.of();
        };
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.TEXT_UTF8;
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) throws IllegalArgumentException {
        switch (node.type) {
            case "ASN1_DECODE" -> {
                String fmt = node.configuration.getOrDefault("outputFormat", "JSON");
                if (!ASN1_FORMATS.contains(fmt.toUpperCase())) {
                    throw new IllegalArgumentException("Unsupported ASN.1 output format: " + fmt);
                }
            }
            case "CHECK_DIGIT_CALC", "CHECK_DIGIT_VERIFY" -> {
                String alg = node.configuration.getOrDefault("algorithm", "Luhn (Mod 10)");
                if (!CheckDigitCalculator.SUPPORTED_ALGORITHMS.contains(alg)) {
                    throw new IllegalArgumentException("Unsupported check digit algorithm: " + alg);
                }
            }
            case "MODULAR_ARITHMETIC" -> {
                String op = node.configuration.getOrDefault("operation", "ADD").trim().toUpperCase();
                if (!MODULAR_OPERATIONS.contains(op)) {
                    throw new IllegalArgumentException("Unsupported modular arithmetic operation: " + op);
                }
                if (!"GCD".equals(op)) {
                    String mod = node.configuration.getOrDefault("modulus", "10001").trim();
                    try {
                        String cleanMod = mod.startsWith("0x") || mod.startsWith("0X") ? mod.substring(2) : mod;
                        BigInteger m = new BigInteger(cleanMod, 16);
                        if (m.compareTo(BigInteger.ONE) <= 0) {
                            throw new IllegalArgumentException("Modulus must be greater than 1: " + mod);
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid hex modulus: " + mod);
                    }
                }
            }
            case "UUID_GENERATE" -> {
                String fmt = node.configuration.getOrDefault("format", "STANDARD").trim().toUpperCase();
                if (!UUID_FORMATS.contains(fmt)) {
                    throw new IllegalArgumentException("Unsupported UUID format: " + fmt);
                }
            }
            default -> { }
        }
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        return switch (node.type) {
            case "ASN1_DECODE" -> {
                FlowValue in = inputs.get("input");
                if (in == null) throw new IllegalArgumentException("Missing required input for ASN1_DECODE");
                ASN1TreeNode root = ASN1Parser.parse(in.bytes());
                String fmt = node.configuration.getOrDefault("outputFormat", "JSON").trim().toUpperCase();
                String text = "MARKDOWN".equals(fmt) ? ASN1TreeExporter.toMarkdown(root) : ASN1TreeExporter.toJson(root);
                yield FlowValue.text(text, StandardCharsets.UTF_8);
            }
            case "CHECK_DIGIT_CALC" -> {
                FlowValue in = inputs.get("input");
                if (in == null) throw new IllegalArgumentException("Missing required input for CHECK_DIGIT_CALC");
                String alg = node.configuration.getOrDefault("algorithm", "Luhn (Mod 10)");
                String text = new String(in.bytes(), StandardCharsets.UTF_8).trim();
                int digit = CheckDigitCalculator.calculateCheckDigit(text, alg);
                yield FlowValue.text(String.valueOf(digit), StandardCharsets.UTF_8);
            }
            case "CHECK_DIGIT_VERIFY" -> {
                FlowValue in = inputs.get("input");
                if (in == null) throw new IllegalArgumentException("Missing required input for CHECK_DIGIT_VERIFY");
                String alg = node.configuration.getOrDefault("algorithm", "Luhn (Mod 10)");
                String text = new String(in.bytes(), StandardCharsets.UTF_8).trim();
                boolean valid = CheckDigitCalculator.validateCheckDigit(text, alg);
                yield FlowValue.text(String.valueOf(valid), StandardCharsets.UTF_8);
            }
            case "MODULAR_ARITHMETIC" -> {
                String op = node.configuration.getOrDefault("operation", "ADD").trim().toUpperCase();
                String mod = node.configuration.getOrDefault("modulus", "10001").trim();
                String cleanMod = mod.startsWith("0x") || mod.startsWith("0X") ? mod.substring(2) : mod;
                String aHex = extractHex(inputs.get("a"));

                String resultHex = switch (op) {
                    case "ADD" -> ModularArithmetic.modularAddition(aHex, extractHex(inputs.get("b")), cleanMod);
                    case "SUBTRACT" -> ModularArithmetic.modularSubtraction(aHex, extractHex(inputs.get("b")), cleanMod);
                    case "MULTIPLY" -> ModularArithmetic.modularMultiplication(aHex, extractHex(inputs.get("b")), cleanMod);
                    case "EXPONENTIATE" -> ModularArithmetic.modularExponentiation(aHex, extractHex(inputs.get("b")), cleanMod);
                    case "ADDITIVE_INVERSE" -> ModularArithmetic.modularInverse(aHex, cleanMod);
                    case "MULTIPLICATIVE_INVERSE" -> ModularArithmetic.modularReciprocal(aHex, cleanMod);
                    case "GCD" -> ModularArithmetic.gcd(aHex, extractHex(inputs.get("b")));
                    default -> throw new IllegalArgumentException("Unsupported operation: " + op);
                };
                yield FlowValue.text(resultHex, StandardCharsets.UTF_8);
            }
            case "UUID_GENERATE" -> {
                String fmt = node.configuration.getOrDefault("format", "STANDARD").trim().toUpperCase();
                String uuid = switch (fmt) {
                    case "WITHOUT_HYPHENS" -> UUIDGenerator.generateUUIDWithoutHyphens();
                    case "UPPERCASE" -> UUIDGenerator.generateUppercaseUUID();
                    default -> UUIDGenerator.generateUUID();
                };
                yield FlowValue.text(uuid, StandardCharsets.UTF_8);
            }
            case "BYTE_STATISTICS" -> {
                FlowValue in = inputs.get("input");
                if (in == null) throw new IllegalArgumentException("Missing required input for BYTE_STATISTICS");
                String stats = ByteStatistics.analyze(in.bytes());
                yield FlowValue.text(stats, StandardCharsets.UTF_8);
            }
            default -> throw new IllegalArgumentException("Unknown utility operation: " + node.type);
        };
    }

    private static String extractHex(FlowValue val) {
        if (val == null) throw new IllegalArgumentException("Required operand input is missing");
        if (val.representation() == Representation.BINARY) {
            return HexFormat.of().formatHex(val.bytes());
        }
        String text = new String(val.bytes(), StandardCharsets.UTF_8).trim();
        if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        return text;
    }
}
