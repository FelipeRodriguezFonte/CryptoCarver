package com.cryptocarver.model.process.handlers;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecRegistry;
import com.cryptocarver.crypto.CompressionCodec;
import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Node handler for encodings, code pages, compression and character set conversion (Phase 5B.1).
 * Covers: BASE32_ENCODE, BASE32_DECODE, BASE58_ENCODE, BASE58_DECODE,
 * BASE58CHECK_ENCODE, BASE58CHECK_DECODE, EBCDIC_ENCODE, EBCDIC_DECODE,
 * COMPRESS, DECOMPRESS, CHARSET_CONVERT.
 */
public class EncodingFormatNodeHandler implements ProcessNodeHandler {

    private static final Set<String> TYPES = Set.of(
            "BASE32_ENCODE", "BASE32_DECODE",
            "BASE58_ENCODE", "BASE58_DECODE",
            "BASE58CHECK_ENCODE", "BASE58CHECK_DECODE",
            "EBCDIC_ENCODE", "EBCDIC_DECODE",
            "COMPRESS", "DECOMPRESS",
            "CHARSET_CONVERT"
    );

    private static final List<String> COMPRESSION_FORMATS = List.of("gzip", "zlib", "deflate");

    private static final List<String> CHARSET_OPTIONS = List.of(
            "UTF-8", "ISO-8859-1", "US-ASCII", "UTF-16", "UTF-16BE", "UTF-16LE", "windows-1252"
    );

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<NodeDescriptor> descriptors() {
        List<String> ebcdicPages = new ArrayList<>(EBCDICConverter.supportedCodePages().keySet());
        return List.of(
                new NodeDescriptor(
                        "BASE32_ENCODE",
                        "Conversions",
                        "module.process.type.base32Encode",
                        "module.process.desc.base32Encode",
                        "🔡",
                        List.of()
                ),
                new NodeDescriptor(
                        "BASE32_DECODE",
                        "Conversions",
                        "module.process.type.base32Decode",
                        "module.process.desc.base32Decode",
                        "🔡",
                        List.of()
                ),
                new NodeDescriptor(
                        "BASE58_ENCODE",
                        "Conversions",
                        "module.process.type.base58Encode",
                        "module.process.desc.base58Encode",
                        "🔤",
                        List.of()
                ),
                new NodeDescriptor(
                        "BASE58_DECODE",
                        "Conversions",
                        "module.process.type.base58Decode",
                        "module.process.desc.base58Decode",
                        "🔤",
                        List.of()
                ),
                new NodeDescriptor(
                        "BASE58CHECK_ENCODE",
                        "Conversions",
                        "module.process.type.base58CheckEncode",
                        "module.process.desc.base58CheckEncode",
                        "🪙",
                        List.of()
                ),
                new NodeDescriptor(
                        "BASE58CHECK_DECODE",
                        "Conversions",
                        "module.process.type.base58CheckDecode",
                        "module.process.desc.base58CheckDecode",
                        "🪙",
                        List.of()
                ),
                new NodeDescriptor(
                        "EBCDIC_ENCODE",
                        "Conversions",
                        "module.process.type.ebcdicEncode",
                        "module.process.desc.ebcdicEncode",
                        "📠",
                        List.of(
                                new NodeParameter("codePage", "module.process.param.codePage", ParameterKind.COMBO, ebcdicPages, "IBM037 — US/Canada")
                        )
                ),
                new NodeDescriptor(
                        "EBCDIC_DECODE",
                        "Conversions",
                        "module.process.type.ebcdicDecode",
                        "module.process.desc.ebcdicDecode",
                        "📠",
                        List.of(
                                new NodeParameter("codePage", "module.process.param.codePage", ParameterKind.COMBO, ebcdicPages, "IBM037 — US/Canada")
                        )
                ),
                new NodeDescriptor(
                        "COMPRESS",
                        "Conversions",
                        "module.process.type.compress",
                        "module.process.desc.compress",
                        "🗜",
                        List.of(
                                new NodeParameter("format", "module.process.param.format", ParameterKind.COMBO, COMPRESSION_FORMATS, "gzip")
                        )
                ),
                new NodeDescriptor(
                        "DECOMPRESS",
                        "Conversions",
                        "module.process.type.decompress",
                        "module.process.desc.decompress",
                        "🗜",
                        List.of(
                                new NodeParameter("format", "module.process.param.format", ParameterKind.COMBO, COMPRESSION_FORMATS, "gzip")
                        )
                ),
                new NodeDescriptor(
                        "CHARSET_CONVERT",
                        "Conversions",
                        "module.process.type.charsetConvert",
                        "module.process.desc.charsetConvert",
                        "🔣",
                        List.of(
                                new NodeParameter("sourceCharset", "module.process.param.sourceCharset", ParameterKind.COMBO, CHARSET_OPTIONS, "ISO-8859-1"),
                                new NodeParameter("targetCharset", "module.process.param.targetCharset", ParameterKind.COMBO, CHARSET_OPTIONS, "UTF-8")
                        )
                )
        );
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return List.of(new PortDefinition("input", Representation.standardValues(), true));
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return switch (node.type) {
            case "BASE32_ENCODE", "BASE58_ENCODE", "BASE58CHECK_ENCODE",
                 "EBCDIC_DECODE", "CHARSET_CONVERT" -> Representation.TEXT_UTF8;
            case "BASE32_DECODE", "BASE58_DECODE", "BASE58CHECK_DECODE",
                 "EBCDIC_ENCODE", "COMPRESS", "DECOMPRESS" -> Representation.BINARY;
            default -> Representation.BINARY;
        };
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) throws IllegalArgumentException {
        switch (node.type) {
            case "EBCDIC_ENCODE", "EBCDIC_DECODE" -> {
                String cp = node.configuration.getOrDefault("codePage", "IBM037 — US/Canada");
                if (!EBCDICConverter.supportedCodePages().containsKey(cp)) {
                    throw new IllegalArgumentException("Unsupported EBCDIC code page: " + cp);
                }
            }
            case "COMPRESS", "DECOMPRESS" -> {
                String fmt = node.configuration.getOrDefault("format", "gzip");
                if (!COMPRESSION_FORMATS.contains(fmt)) {
                    throw new IllegalArgumentException("Unsupported compression format: " + fmt);
                }
            }
            case "CHARSET_CONVERT" -> {
                String src = node.configuration.getOrDefault("sourceCharset", "ISO-8859-1");
                String dst = node.configuration.getOrDefault("targetCharset", "UTF-8");
                try {
                    Charset.forName(src);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid source charset: " + src);
                }
                try {
                    Charset.forName(dst);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid target charset: " + dst);
                }
            }
            default -> { }
        }
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        FlowValue input = inputs.get("input");
        if (input == null) {
            throw new IllegalArgumentException("Required input port 'input' is missing");
        }
        byte[] bytes = input.bytes();

        return switch (node.type) {
            case "BASE32_ENCODE" -> {
                String encoded = CodecRegistry.getInstance().encode(bytes, ByteFormat.BASE32);
                yield FlowValue.text(encoded, StandardCharsets.UTF_8);
            }
            case "BASE32_DECODE" -> {
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                byte[] decoded = CodecRegistry.getInstance().decode(text, ByteFormat.BASE32);
                yield FlowValue.binary(decoded);
            }
            case "BASE58_ENCODE" -> {
                String encoded = CodecRegistry.getInstance().encode(bytes, ByteFormat.BASE58);
                yield FlowValue.text(encoded, StandardCharsets.UTF_8);
            }
            case "BASE58_DECODE" -> {
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                byte[] decoded = CodecRegistry.getInstance().decode(text, ByteFormat.BASE58);
                yield FlowValue.binary(decoded);
            }
            case "BASE58CHECK_ENCODE" -> {
                String encoded = CodecRegistry.getInstance().encode(bytes, ByteFormat.BASE58_CHECK);
                yield FlowValue.text(encoded, StandardCharsets.UTF_8);
            }
            case "BASE58CHECK_DECODE" -> {
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                byte[] decoded = CodecRegistry.getInstance().decode(text, ByteFormat.BASE58_CHECK);
                yield FlowValue.binary(decoded);
            }
            case "EBCDIC_ENCODE" -> {
                String cp = node.configuration.getOrDefault("codePage", "IBM037 — US/Canada");
                String text = new String(bytes, input.charset() != null ? input.charset() : StandardCharsets.UTF_8);
                byte[] encoded = EBCDICConverter.encode(text, cp);
                yield FlowValue.binary(encoded);
            }
            case "EBCDIC_DECODE" -> {
                String cp = node.configuration.getOrDefault("codePage", "IBM037 — US/Canada");
                String decoded = EBCDICConverter.decode(bytes, cp);
                yield FlowValue.text(decoded, StandardCharsets.UTF_8);
            }
            case "COMPRESS" -> {
                String fmt = node.configuration.getOrDefault("format", "gzip");
                byte[] compressed = CompressionCodec.compress(bytes, fmt);
                yield FlowValue.binary(compressed);
            }
            case "DECOMPRESS" -> {
                String fmt = node.configuration.getOrDefault("format", "gzip");
                byte[] decompressed = CompressionCodec.decompress(bytes, fmt);
                yield FlowValue.binary(decompressed);
            }
            case "CHARSET_CONVERT" -> {
                String src = node.configuration.getOrDefault("sourceCharset", "ISO-8859-1");
                String dst = node.configuration.getOrDefault("targetCharset", "UTF-8");
                Charset srcCs = Charset.forName(src);
                Charset dstCs = Charset.forName(dst);
                String decoded = new String(bytes, srcCs);
                yield FlowValue.text(decoded, dstCs);
            }
            default -> throw new IllegalArgumentException("Unknown encoding operation: " + node.type);
        };
    }
}
