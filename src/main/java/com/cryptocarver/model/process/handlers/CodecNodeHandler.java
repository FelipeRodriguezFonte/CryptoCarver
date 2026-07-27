package com.cryptocarver.model.process.handlers;

import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import java.util.List;
import java.util.Map;
import com.cryptocarver.model.process.Representation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

public class CodecNodeHandler implements ProcessNodeHandler {
    @Override
    public Set<String> supportedTypes() {
        return Set.of(
            "BASE64", // Legacy MVP support
            "BASE64_ENCODE", "BASE64_DECODE",
            "BASE64URL_ENCODE", "BASE64URL_DECODE",
            "HEX_ENCODE", "HEX_DECODE",
            "UTF8_ENCODE", "UTF8_DECODE"
        );
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        if ("HEX_DECODE".equals(node.type)) {
            return List.of(new PortDefinition("input", Set.of(Representation.HEX, Representation.TEXT_UTF8), true));
        } else if ("BASE64_DECODE".equals(node.type)) {
            return List.of(new PortDefinition("input", Set.of(Representation.BASE64, Representation.TEXT_UTF8), true));
        } else if ("BASE64URL_DECODE".equals(node.type)) {
            return List.of(new PortDefinition("input", Set.of(Representation.BASE64URL, Representation.TEXT_UTF8), true));
        } else if ("UTF8_ENCODE".equals(node.type)) {
            return List.of(new PortDefinition("input", Set.of(Representation.TEXT_UTF8), true));
        } else if ("UTF8_DECODE".equals(node.type)) {
            return List.of(new PortDefinition("input", Set.of(Representation.BINARY), true));
        }
        return List.of(new PortDefinition("input", Set.of(Representation.values()), true));
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        if ("HEX_ENCODE".equals(node.type)) return Representation.HEX;
        if ("BASE64_ENCODE".equals(node.type)) return Representation.BASE64;
        if ("BASE64URL_ENCODE".equals(node.type)) return Representation.BASE64URL;
        if ("UTF8_ENCODE".equals(node.type)) return Representation.BINARY;
        if ("UTF8_DECODE".equals(node.type)) return Representation.TEXT_UTF8;
        return Representation.BINARY;
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        FlowValue input = inputs.getOrDefault("input", FlowValue.binary(new byte[0]));
        switch (node.type) {
            case "HEX_ENCODE":
                return FlowValue.hex(java.util.HexFormat.of().formatHex(input.bytes()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "HEX_DECODE":
                return FlowValue.binary(java.util.HexFormat.of().parseHex(input.render()));
            case "BASE64_ENCODE":
                return FlowValue.base64(java.util.Base64.getEncoder().encodeToString(input.bytes()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "BASE64_DECODE":
                return FlowValue.binary(java.util.Base64.getDecoder().decode(input.render().trim()));
            case "BASE64URL_ENCODE":
                return FlowValue.base64Url(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input.bytes()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "BASE64URL_DECODE":
                return FlowValue.binary(java.util.Base64.getUrlDecoder().decode(input.render().trim()));
            case "UTF8_ENCODE": {
                java.nio.charset.Charset sourceCharset = input.charset() != null
                        ? input.charset() : java.nio.charset.StandardCharsets.UTF_8;
                String text = new String(input.bytes(), sourceCharset);
                return FlowValue.binary(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            case "UTF8_DECODE":
                return FlowValue.text(new String(input.bytes(), java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.charset.StandardCharsets.UTF_8);
            case "CHARSET_CONVERT":
                String source = node.configuration.getOrDefault("sourceCharset", "UTF-8");
                String target = node.configuration.getOrDefault("targetCharset", "UTF-8");
                String text = new String(input.bytes(), java.nio.charset.Charset.forName(source));
                return FlowValue.text(text, java.nio.charset.Charset.forName(target));
            default:
                throw new IllegalArgumentException("Unknown codec type: " + node.type);
        }
    }
}
