package com.cryptocarver.model.process.handlers;

import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import com.cryptocarver.crypto.FormatPreservingEncryption;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Process Designer adapter for format-preserving encryption (FF1/FF3-1). */
public final class FormatPreservingEncryptionNodeHandler implements ProcessNodeHandler {
    public static final Set<String> TYPES = Set.of("FPE_ENCRYPT", "FPE_DECRYPT");
    private static final Set<Representation> TEXT = Set.of(Representation.TEXT_UTF8);
    private static final Set<Representation> HEX = Set.of(Representation.HEX);
    private static final List<String> ALPHABETS = List.of("DECIMAL", "HEX_LOWER", "BASE36_UPPER", "BASE62", "CUSTOM");
    private static final List<String> ALGORITHMS = List.of("FF1", "FF3_1");

    @Override public Set<String> supportedTypes() { return TYPES; }

    @Override public List<NodeDescriptor> descriptors() {
        List<NodeParameter> params = List.of(
                new NodeParameter("algorithm", "module.process.param.algorithm", ParameterKind.COMBO, ALGORITHMS, "FF1"),
                NodeParameter.sensitivePassword("key", "module.process.param.key"),
                new NodeParameter("tweak", "module.process.param.tweak", ParameterKind.HEX, ""),
                new NodeParameter("alphabet", "module.process.param.alphabet", ParameterKind.COMBO, ALPHABETS, "DECIMAL"),
                new NodeParameter("customAlphabet", "module.process.param.customAlphabet", ParameterKind.TEXT, "", "alphabet=CUSTOM")
        );
        return List.of(
                new NodeDescriptor("FPE_ENCRYPT", "Crypto", "module.process.type.fpeEncrypt", "module.process.desc.fpeEncrypt", "🔐", params),
                new NodeDescriptor("FPE_DECRYPT", "Crypto", "module.process.type.fpeDecrypt", "module.process.desc.fpeDecrypt", "🔓", params)
        );
    }

    @Override public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return List.of(new PortDefinition("input", TEXT, true),
                new PortDefinition("key", HEX, false), new PortDefinition("tweak", HEX, false));
    }

    @Override public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.TEXT_UTF8;
    }

    @Override public void validateConfiguration(ProcessDefinition.Node node) {
        String algorithm = setting(node, "algorithm", "FF1");
        if (!ALGORITHMS.contains(algorithm)) throw new IllegalArgumentException("Unsupported FPE algorithm: " + algorithm);
        String alphabet = setting(node, "alphabet", "DECIMAL").toUpperCase(Locale.ROOT);
        if (!ALPHABETS.contains(alphabet)) throw new IllegalArgumentException("Unsupported FPE alphabet: " + alphabet);
        if ("CUSTOM".equals(alphabet) && setting(node, "customAlphabet", "").isEmpty()) {
            throw new IllegalArgumentException("Custom alphabet is required when alphabet is CUSTOM");
        }
        if (!node.configuration.containsKey("keyFromFlow")) {
            String key = setting(node, "key", "");
            if (key.isEmpty()) throw new IllegalArgumentException("FPE key is required (configure key or connect key port)");
            parseHex(key, "key");
        }
        String tweak = setting(node, "tweak", "");
        if (!tweak.isEmpty() && !node.configuration.containsKey("tweakFromFlow")) parseHex(tweak, "tweak");
    }

    @Override public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) {
        String input = text(inputs.get("input"));
        byte[] key = inputs.containsKey("key") ? parseHex(new String(inputs.get("key").bytes(), StandardCharsets.UTF_8), "key") : parseHex(setting(node, "key", ""), "key");
        byte[] tweak = inputs.containsKey("tweak") ? parseHex(new String(inputs.get("tweak").bytes(), StandardCharsets.UTF_8), "tweak") : parseHex(setting(node, "tweak", ""), "tweak");
        String alphabet = setting(node, "alphabet", "DECIMAL").toUpperCase(Locale.ROOT);
        String alphabetChars = "CUSTOM".equals(alphabet) ? setting(node, "customAlphabet", "") : alphabetChars(alphabet);
        String result = invokeCore(node.type.toUpperCase(Locale.ROOT).endsWith("DECRYPT"), input, key, tweak,
                setting(node, "algorithm", "FF1"), alphabetChars);
        return FlowValue.text(result, StandardCharsets.UTF_8);
    }

    private static String invokeCore(boolean decrypt, String input, byte[] key, byte[] tweak, String algorithm, String alphabet) {
        FormatPreservingEncryption.Algorithm selected = FormatPreservingEncryption.Algorithm.valueOf(algorithm);
        return decrypt
                ? FormatPreservingEncryption.decrypt(selected, input, key, alphabet, tweak)
                : FormatPreservingEncryption.encrypt(selected, input, key, alphabet, tweak);
    }

    private static String alphabetChars(String name) {
        return switch (name) {
            case "DECIMAL" -> "0123456789";
            case "HEX_LOWER" -> "0123456789abcdef";
            case "BASE36_UPPER" -> "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            case "BASE62" -> "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
            default -> throw new IllegalArgumentException("Unsupported FPE alphabet: " + name);
        };
    }

    private static String setting(ProcessDefinition.Node n, String key, String fallback) {
        return n.configuration.getOrDefault(key, fallback);
    }
    private static String text(FlowValue v) {
        if (v == null) throw new IllegalArgumentException("Required input port 'input' is missing");
        return new String(v.bytes(), v.charset() != null ? v.charset() : StandardCharsets.UTF_8);
    }
    private static byte[] parseHex(String value, String label) {
        if (value == null || value.isEmpty()) return new byte[0];
        try { return HexFormat.of().parseHex(value.replaceAll("\\s+", "")); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid hexadecimal " + label, e); }
    }
}
