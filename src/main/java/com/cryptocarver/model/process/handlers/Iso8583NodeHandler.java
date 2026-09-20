package com.cryptocarver.model.process.handlers;

import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;

import java.util.LinkedHashMap;
import com.cryptocarver.crypto.iso8583.Iso8583Operations;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Process Designer adapter for ISO 8583 message parsing and building.
 *
 * <p>The wire implementation lives in {@code Iso8583Operations}; this class is
 * deliberately only the process boundary and uses the core's typed Profile
 * contract for version, bitmap and length encoding.</p>
 */
public final class Iso8583NodeHandler implements ProcessNodeHandler {
    public static final String PARSE = "ISO8583_PARSE";
    public static final String BUILD = "ISO8583_BUILD";
    public static final String INSPECT = "ISO8583_INSPECT";
    public static final Set<String> TYPES = Set.of(PARSE, BUILD, INSPECT);
    private static final Set<Representation> TEXT = Set.of(Representation.TEXT_UTF8);
    private static final List<String> VERSIONS = List.of("1987", "1993");
    private static final List<String> BITMAPS = List.of("BINARY", "HEX_ASCII");
    private static final List<String> LENGTHS = List.of("ASCII", "BCD");

    @Override public Set<String> supportedTypes() { return TYPES; }

    @Override
    public List<NodeDescriptor> descriptors() {
        return List.of(
                new NodeDescriptor(PARSE, "Payments", "module.process.type.iso8583Parse",
                        "module.process.desc.iso8583Parse", "🧾", List.of(
                        new NodeParameter("version", "module.process.param.iso8583Version", ParameterKind.COMBO, VERSIONS, "1987"),
                        new NodeParameter("bitmapEncoding", "module.process.param.iso8583BitmapEncoding", ParameterKind.COMBO, BITMAPS, "HEX_ASCII"),
                        new NodeParameter("lengthEncoding", "module.process.param.iso8583LengthEncoding", ParameterKind.COMBO, LENGTHS, "ASCII"),
                        new NodeParameter("message", "module.process.param.iso8583Message", ParameterKind.PASSWORD, List.of(), "", true))),
                new NodeDescriptor(BUILD, "Payments", "module.process.type.iso8583Build",
                        "module.process.desc.iso8583Build", "🧱", List.of(
                        new NodeParameter("version", "module.process.param.iso8583Version", ParameterKind.COMBO, VERSIONS, "1987"),
                        new NodeParameter("bitmapEncoding", "module.process.param.iso8583BitmapEncoding", ParameterKind.COMBO, BITMAPS, "HEX_ASCII"),
                        new NodeParameter("lengthEncoding", "module.process.param.iso8583LengthEncoding", ParameterKind.COMBO, LENGTHS, "ASCII"),
                        new NodeParameter("mti", "module.process.param.iso8583Mti", ParameterKind.TEXT, "0200"),
                        new NodeParameter("fields", "module.process.param.iso8583Fields", ParameterKind.PASSWORD, List.of(), "", true))),
                new NodeDescriptor(INSPECT, "Payments", "module.process.type.iso8583Inspect",
                        "module.process.desc.iso8583Inspect", "🔎", List.of(
                        new NodeParameter("version", "module.process.param.iso8583Version", ParameterKind.COMBO, VERSIONS, "1987"),
                        new NodeParameter("bitmapEncoding", "module.process.param.iso8583BitmapEncoding", ParameterKind.COMBO, BITMAPS, "HEX_ASCII"),
                        new NodeParameter("lengthEncoding", "module.process.param.iso8583LengthEncoding", ParameterKind.COMBO, LENGTHS, "ASCII"),
                        new NodeParameter("message", "module.process.param.iso8583Message", ParameterKind.PASSWORD, List.of(), "", true)))
        );
    }

    @Override public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return switch (node.type.toUpperCase(Locale.ROOT)) {
            case PARSE, INSPECT -> List.of(new PortDefinition("message", TEXT, true));
            case BUILD -> List.of(new PortDefinition("fields", TEXT, false));
            default -> List.of();
        };
    }

    @Override public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.TEXT_UTF8;
    }

    @Override public void validateConfiguration(ProcessDefinition.Node node) {
        String type = node.type.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported ISO 8583 operation");
        oneOf(node, "version", VERSIONS); oneOf(node, "bitmapEncoding", BITMAPS); oneOf(node, "lengthEncoding", LENGTHS);
        if (PARSE.equals(type) || INSPECT.equals(type)) require(node, "message");
        if (BUILD.equals(type)) {
            require(node, "mti");
            String mti = node.configuration.get("mti");
            if (mti != null && !mti.matches("[0-9]{4}")) throw new IllegalArgumentException("ISO 8583 MTI must contain four digits");
            require(node, "fields");
        }
    }

    @Override public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) {
        validateConfiguration(node);
        String type = node.type.toUpperCase(Locale.ROOT);
        String payload = text(node, inputs, type.equals(BUILD) ? "fields" : "message");
        String version = setting(node, "version", "1987");
        String bitmap = setting(node, "bitmapEncoding", "HEX_ASCII");
        String length = setting(node, "lengthEncoding", "ASCII");
        try {
            Iso8583Operations.Profile profile = profile(version, bitmap, length);
            if (BUILD.equals(type)) {
                byte[] built = Iso8583Operations.build(node.configuration.get("mti"), fields(payload), profile);
                String rendered = profile.bitmapEncoding() == Iso8583Operations.BitmapEncoding.ASCII_HEX
                        ? new String(built, StandardCharsets.US_ASCII)
                        : java.util.HexFormat.of().withUpperCase().formatHex(built);
                return FlowValue.text(rendered, StandardCharsets.UTF_8);
            }
            Iso8583Operations.Message message = Iso8583Operations.parse(payload, profile);
            return FlowValue.text(message.report(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("ISO 8583 operation failed safely", e);
        }
    }

    private static Iso8583Operations.Profile profile(String version, String bitmap, String length) {
        Iso8583Operations.Version v = "1987".equals(version) ? Iso8583Operations.Version.ISO_1987 : Iso8583Operations.Version.ISO_1993;
        Iso8583Operations.BitmapEncoding b = "BINARY".equals(bitmap) ? Iso8583Operations.BitmapEncoding.BINARY : Iso8583Operations.BitmapEncoding.ASCII_HEX;
        return new Iso8583Operations.Profile(v, b, Iso8583Operations.LengthEncoding.valueOf(length), false);
    }

    private static Map<Integer, String> fields(String source) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (String part : source.replace('{', ' ').replace('}', ' ').split("[,;\\n]")) {
            String[] kv = part.trim().split("[:=]", 2);
            if (kv.length == 2 && kv[0].trim().matches("\\d+")) out.put(Integer.parseInt(kv[0].trim()), kv[1].trim().replaceAll("^\\\"|\\\"$", ""));
        }
        if (out.isEmpty()) throw new IllegalArgumentException("ISO 8583 fields must be field=value pairs");
        return out;
    }

    private static String text(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String key) {
        FlowValue value = inputs.get(key);
        if (value != null) {
            if (value.representation() != Representation.TEXT_UTF8) throw new IllegalArgumentException("ISO 8583 input must be UTF-8 text");
            return value.render();
        }
        String configured = node.configuration.get(key);
        if (configured == null || configured.isBlank()) throw new IllegalArgumentException("Missing ISO 8583 input: " + key);
        return configured;
    }
    private static void require(ProcessDefinition.Node node, String key) {
        if ((node.configuration.get(key) == null || node.configuration.get(key).isBlank())
                && !"true".equalsIgnoreCase(node.configuration.get(key + "FromFlow"))
                && !NodeCatalog.isSupplied(node, key)) throw new IllegalArgumentException("Missing ISO 8583 input: " + key);
    }
    private static String setting(ProcessDefinition.Node node, String key, String fallback) {
        String value = node.configuration.get(key); return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
    private static void oneOf(ProcessDefinition.Node node, String key, List<String> values) {
        String value = setting(node, key, values.get(0));
        if (values.stream().noneMatch(value::equalsIgnoreCase)) throw new IllegalArgumentException("Invalid ISO 8583 " + key + ": " + value);
    }
}
