package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.MACOperations;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import com.cryptocarver.utils.PaddingUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plumbing and flow control node handler for Process Designer (Phase 5B.1).
 * Covers: CONCAT, SLICE, PAD, UNPAD, XOR, ASSERT_EQUALS.
 */
public class PlumbingNodeHandler implements ProcessNodeHandler {

    private static final Set<String> TYPES = Set.of(
            "CONCAT", "SLICE", "PAD", "UNPAD", "XOR", "ASSERT_EQUALS"
    );

    private static final List<String> PADDING_OPTIONS = List.of(
            "PKCS7", "PKCS5", "ISO_9797_M1", "ISO_9797_M2", "ISO_7816_4", "ZERO"
    );

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<NodeDescriptor> descriptors() {
        return List.of(
                new NodeDescriptor(
                        "CONCAT",
                        "Plumbing",
                        "module.process.type.concat",
                        "module.process.desc.concat",
                        "🔗",
                        List.of()
                ),
                new NodeDescriptor(
                        "SLICE",
                        "Plumbing",
                        "module.process.type.slice",
                        "module.process.desc.slice",
                        "✂",
                        List.of(
                                new NodeParameter("offset", "module.process.param.offset", ParameterKind.NUMBER, "0"),
                                new NodeParameter("length", "module.process.param.length", ParameterKind.NUMBER, "-1")
                        )
                ),
                new NodeDescriptor(
                        "PAD",
                        "Plumbing",
                        "module.process.type.pad",
                        "module.process.desc.pad",
                        "🧱",
                        List.of(
                                new NodeParameter("paddingType", "module.process.param.paddingType", ParameterKind.COMBO, PADDING_OPTIONS, "PKCS7"),
                                new NodeParameter("blockSize", "module.process.param.blockSize", ParameterKind.NUMBER, "16")
                        )
                ),
                new NodeDescriptor(
                        "UNPAD",
                        "Plumbing",
                        "module.process.type.unpad",
                        "module.process.desc.unpad",
                        "🪓",
                        List.of(
                                new NodeParameter("paddingType", "module.process.param.paddingType", ParameterKind.COMBO, PADDING_OPTIONS, "PKCS7")
                        )
                ),
                new NodeDescriptor(
                        "XOR",
                        "Plumbing",
                        "module.process.type.xor",
                        "module.process.desc.xor",
                        "⊕",
                        List.of()
                ),
                new NodeDescriptor(
                        "ASSERT_EQUALS",
                        "Plumbing",
                        "module.process.type.assertEquals",
                        "module.process.desc.assertEquals",
                        "⚖",
                        List.of()
                )
        );
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        Set<Representation> allReps = Representation.standardValues();
        return switch (node.type) {
            case "CONCAT", "XOR" -> List.of(
                    new PortDefinition("a", allReps, true),
                    new PortDefinition("b", allReps, true)
            );
            case "SLICE", "PAD", "UNPAD" -> List.of(
                    new PortDefinition("input", allReps, true)
            );
            case "ASSERT_EQUALS" -> List.of(
                    new PortDefinition("actual", allReps, true),
                    new PortDefinition("expected", allReps, true)
            );
            default -> List.of();
        };
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        if ("ASSERT_EQUALS".equals(node.type)) {
            return inputs.getOrDefault("actual", Representation.BINARY);
        }
        return Representation.BINARY;
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) throws IllegalArgumentException {
        switch (node.type) {
            case "SLICE" -> {
                int offset = parseOffset(node);
                if (offset < 0) {
                    throw new IllegalArgumentException("Offset cannot be negative: " + offset);
                }
                int length = parseLength(node);
                if (length < -1) {
                    throw new IllegalArgumentException("Length cannot be less than -1: " + length);
                }
            }
            case "PAD" -> {
                parsePaddingType(node);
                int blockSize = parseBlockSize(node);
                if (blockSize <= 0 || blockSize > 256) {
                    throw new IllegalArgumentException("Invalid block size: " + blockSize);
                }
            }
            case "UNPAD" -> parsePaddingType(node);
            default -> { }
        }
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        return switch (node.type) {
            case "CONCAT" -> {
                byte[] a = inputs.get("a").bytes();
                byte[] b = inputs.get("b").bytes();
                byte[] combined = new byte[a.length + b.length];
                System.arraycopy(a, 0, combined, 0, a.length);
                System.arraycopy(b, 0, combined, a.length, b.length);
                yield FlowValue.binary(combined);
            }
            case "SLICE" -> {
                byte[] in = inputs.get("input").bytes();
                int offset = parseOffset(node);
                if (offset < 0) throw new IllegalArgumentException("Offset cannot be negative: " + offset);
                if (offset > in.length) {
                    throw new IllegalArgumentException("Offset " + offset + " exceeds data length " + in.length);
                }
                int length = parseLength(node);
                if (length < -1) throw new IllegalArgumentException("Length cannot be less than -1: " + length);
                int available = in.length - offset;
                int take = (length < 0) ? available : length;
                if (take > available) {
                    throw new IllegalArgumentException("Requested slice length " + take + " exceeds available bytes " + available);
                }
                byte[] sliced = Arrays.copyOfRange(in, offset, offset + take);
                yield FlowValue.binary(sliced);
            }
            case "PAD" -> {
                byte[] in = inputs.get("input").bytes();
                PaddingUtil.PaddingType type = parsePaddingType(node);
                int blockSize = parseBlockSize(node);
                byte[] padded = PaddingUtil.addPadding(in, blockSize, type);
                yield FlowValue.binary(padded);
            }
            case "UNPAD" -> {
                byte[] in = inputs.get("input").bytes();
                if (in == null || in.length == 0) {
                    throw new IllegalArgumentException("Padded data cannot be null or empty");
                }
                PaddingUtil.PaddingType type = parsePaddingType(node);
                byte[] unpadded = PaddingUtil.removePadding(in, type);
                yield FlowValue.binary(unpadded);
            }
            case "XOR" -> {
                byte[] a = inputs.get("a").bytes();
                byte[] b = inputs.get("b").bytes();
                if (a.length != b.length) {
                    throw new IllegalArgumentException("Arrays must have the same length for XOR (a=" + a.length + ", b=" + b.length + ")");
                }
                yield FlowValue.binary(KeyOperations.xor(a, b));
            }
            case "ASSERT_EQUALS" -> {
                FlowValue actual = inputs.get("actual");
                FlowValue expected = inputs.get("expected");
                if (actual == null || expected == null) {
                    throw new IllegalArgumentException("ASSERT_EQUALS requires both 'actual' and 'expected' inputs");
                }
                byte[] a = actual.bytes();
                byte[] e = expected.bytes();
                boolean equal = MACOperations.constantTimeEquals(e, a);
                if (!equal) {
                    throw new IllegalStateException("Assertion failed: actual byte length " + a.length + " vs expected byte length " + e.length);
                }
                yield actual;
            }
            default -> throw new IllegalArgumentException("Unknown plumbing operation: " + node.type);
        };
    }

    private static int parseOffset(ProcessDefinition.Node node) {
        String val = node.configuration.getOrDefault("offset", "0");
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid offset number: " + val);
        }
    }

    private static int parseLength(ProcessDefinition.Node node) {
        String val = node.configuration.getOrDefault("length", "-1");
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid length number: " + val);
        }
    }

    private static PaddingUtil.PaddingType parsePaddingType(ProcessDefinition.Node node) {
        String val = node.configuration.getOrDefault("paddingType", "PKCS7");
        try {
            return PaddingUtil.PaddingType.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported padding type: " + val);
        }
    }

    private static int parseBlockSize(ProcessDefinition.Node node) {
        String val = node.configuration.getOrDefault("blockSize", "16");
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid block size number: " + val);
        }
    }
}
