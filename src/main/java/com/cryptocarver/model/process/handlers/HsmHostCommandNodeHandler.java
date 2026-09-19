package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.hsm.PayShieldCommand;
import com.cryptocarver.crypto.hsm.PayShieldErrorCatalog;
import com.cryptocarver.crypto.hsm.PayShieldMessage;
import com.cryptocarver.crypto.hsm.PayShieldMessageCodec;
import com.cryptocarver.crypto.hsm.PayShieldNcResponse;
import com.cryptocarver.crypto.hsm.PayShieldResponse;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Process Designer adapter for the offline payShield host-frame bank. */
public final class HsmHostCommandNodeHandler implements ProcessNodeHandler {
    public static final Set<String> TYPES = Set.of(
            "HSM_HOST_COMPOSE", "HSM_HOST_PARSE_COMMAND", "HSM_HOST_PARSE_RESPONSE");
    private static final Set<Representation> TEXT = Set.of(Representation.TEXT_UTF8);
    private static final List<String> COMMAND_CODES = Arrays.stream(PayShieldCommand.values())
            .map(Enum::name).toList();

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<NodeDescriptor> descriptors() {
        return List.of(
                new NodeDescriptor("HSM_HOST_COMPOSE", "Payments",
                        "module.process.type.hsmHostCompose", "module.process.desc.hsmHostCompose", "🖥",
                        List.of(
                                plain("header", "module.process.param.hsmHeader", ParameterKind.TEXT, "0000"),
                                new NodeParameter("commandCode", "module.process.param.hsmCommandCode",
                                        ParameterKind.COMBO, COMMAND_CODES, "NC"),
                                secret("body", "module.process.param.hsmBody"),
                                secret("trailer", "module.process.param.hsmTrailer"),
                                plain("headerLength", "module.process.param.hsmHeaderLength",
                                        ParameterKind.NUMBER, "4"),
                                plain("tcpPrefix", "module.process.param.hsmTcpPrefix",
                                        ParameterKind.CHECKBOX, "false"))),
                new NodeDescriptor("HSM_HOST_PARSE_COMMAND", "Payments",
                        "module.process.type.hsmHostParseCommand", "module.process.desc.hsmHostParseCommand", "🔍",
                        parseParameters()),
                new NodeDescriptor("HSM_HOST_PARSE_RESPONSE", "Payments",
                        "module.process.type.hsmHostParseResponse", "module.process.desc.hsmHostParseResponse", "🔍",
                        parseParameters()));
    }

    private static List<NodeParameter> parseParameters() {
        return List.of(
                secret("frame", "module.process.param.hsmFrame"),
                plain("headerLength", "module.process.param.hsmHeaderLength", ParameterKind.NUMBER, "4"),
                plain("tcpPrefix", "module.process.param.hsmTcpPrefix", ParameterKind.CHECKBOX, "false"));
    }

    private static NodeParameter plain(String key, String label, ParameterKind kind, String defaultValue) {
        return new NodeParameter(key, label, kind, defaultValue);
    }

    private static NodeParameter secret(String key, String label) {
        return new NodeParameter(key, label, ParameterKind.PASSWORD, List.of(), "", true);
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return switch (node.type) {
            case "HSM_HOST_PARSE_COMMAND", "HSM_HOST_PARSE_RESPONSE" ->
                    List.of(new PortDefinition("frame", TEXT, false));
            default -> List.of();
        };
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.TEXT_UTF8;
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) {
        if (!TYPES.contains(node.type)) {
            throw new IllegalArgumentException("Unsupported HSM host operation");
        }
        int headerLength = integer(node, "headerLength", 4);
        if (headerLength < 1 || headerLength > 255) {
            throw new IllegalArgumentException("HSM host header length must be between 1 and 255");
        }
        if ("HSM_HOST_COMPOSE".equals(node.type)) {
            require(node, "header");
            require(node, "commandCode");
            if (node.configuration.get("header").length() != headerLength) {
                throw new IllegalArgumentException("HSM host header does not match its configured length");
            }
            try {
                PayShieldCommand.valueOf(node.configuration.get("commandCode").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported HSM host command code");
            }
        } else {
            require(node, "frame");
        }
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs,
                             ExecutionContext context) {
        PayShieldMessageCodec codec = new PayShieldMessageCodec(
                integer(node, "headerLength", 4), bool(node, "tcpPrefix"));
        String output = switch (node.type) {
            case "HSM_HOST_COMPOSE" -> compose(node, codec);
            case "HSM_HOST_PARSE_COMMAND" -> describe(codec.parseCommand(
                    decodeFrame(runtimeText(node, inputs, "frame"), codec.tcpLengthPrefix())));
            case "HSM_HOST_PARSE_RESPONSE" -> describe(codec.parseResponse(
                    decodeFrame(runtimeText(node, inputs, "frame"), codec.tcpLengthPrefix())));
            default -> throw new IllegalArgumentException("Unsupported HSM host operation");
        };
        return FlowValue.text(output, StandardCharsets.UTF_8);
    }

    private static String compose(ProcessDefinition.Node node, PayShieldMessageCodec codec) {
        byte[] frame = codec.composeCommand(
                node.configuration.get("header").trim(),
                node.configuration.get("commandCode").trim().toUpperCase(Locale.ROOT),
                node.configuration.getOrDefault("body", "").getBytes(StandardCharsets.US_ASCII),
                node.configuration.getOrDefault("trailer", "").getBytes(StandardCharsets.US_ASCII));
        return codec.tcpLengthPrefix()
                ? HexFormat.of().withUpperCase().formatHex(frame)
                : new String(frame, StandardCharsets.US_ASCII);
    }

    private static byte[] decodeFrame(String value, boolean tcpPrefix) {
        if (!tcpPrefix) {
            return value.getBytes(StandardCharsets.US_ASCII);
        }
        try {
            return HexFormat.of().parseHex(value.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("TCP-prefixed HSM host frames must be hexadecimal");
        }
    }

    private static String describe(PayShieldMessage message) {
        PayShieldCommand command;
        try {
            command = PayShieldCommand.valueOf(message.code());
        } catch (IllegalArgumentException e) {
            command = null;
        }
        return "command=" + message.code()
                + "\nname=" + (command == null ? "unknown" : command.displayName())
                + "\nheader=" + message.header()
                + "\nbodyLength=" + message.body().length
                + "\nbodyOpaque=" + new String(message.body(), StandardCharsets.US_ASCII)
                + "\ntrailer=" + new String(message.trailer(), StandardCharsets.US_ASCII);
    }

    private static String describe(PayShieldResponse response) {
        StringBuilder description = new StringBuilder("response=")
                .append(response.responseCode())
                .append("\nheader=").append(response.header())
                .append("\nerror=").append(response.errorCode())
                .append("\nerrorMeaning=").append(PayShieldErrorCatalog.translate(response.errorCode()))
                .append("\ndataLength=").append(response.data().length)
                .append("\ndataOpaque=")
                .append(new String(response.data(), StandardCharsets.US_ASCII))
                .append("\ntrailer=")
                .append(new String(response.trailer(), StandardCharsets.US_ASCII));
        PayShieldNcResponse.from(response).ifPresent(nc -> description
                .append("\nlmkCheckValue=").append(nc.lmkCheckValue())
                .append("\nfirmwareVersion=").append(nc.firmwareVersion()));
        return description.toString();
    }

    private static String runtimeText(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String key) {
        FlowValue input = inputs.get(key);
        if (input != null) {
            if (input.representation() != Representation.TEXT_UTF8) {
                throw new IllegalArgumentException("HSM host frame input must be text");
            }
            return input.render().trim();
        }
        String configured = node.configuration.get(key);
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Missing required HSM host frame");
        }
        return configured.trim();
    }

    private static void require(ProcessDefinition.Node node, String key) {
        String value = node.configuration.get(key);
        if ((value == null || value.isBlank())
                && !"true".equalsIgnoreCase(node.configuration.get(key + "FromFlow"))
                && !NodeCatalog.isSupplied(node, key)) {
            throw new IllegalArgumentException("Missing required HSM host input: " + key);
        }
    }

    private static int integer(ProcessDefinition.Node node, String key, int defaultValue) {
        try {
            return Integer.parseInt(node.configuration.getOrDefault(key, Integer.toString(defaultValue)).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("HSM host " + key + " must be a decimal integer");
        }
    }

    private static boolean bool(ProcessDefinition.Node node, String key) {
        return Boolean.parseBoolean(node.configuration.getOrDefault(key, "false"));
    }
}
