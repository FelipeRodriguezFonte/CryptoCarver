package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.KeyDerivation;
import com.cryptocarver.crypto.KeyMaterialInspector;
import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.KeyWrapOperations;
import com.cryptocarver.crypto.TR31Operations;
import com.cryptocarver.crypto.icsf.IcsfTokenParser;
import com.cryptocarver.crypto.icsf.IcsfTokenReport;
import com.cryptocarver.crypto.icsf.Origin;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import org.bouncycastle.crypto.Digest;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Process Designer adapter for the key-side of Phase 5B.2.
 *
 * <p>This class deliberately contains no cryptographic implementation. Every
 * operation below is a thin conversion layer around the already tested crypto
 * facades. Secret configuration fields are supported for the inspector's
 * transient-secret injection, while normal process composition uses the
 * sensitive HEX ports.</p>
 */
public final class KeyOperationsNodeHandler implements ProcessNodeHandler {
    public static final Set<String> TYPES = Set.of(
            "KCV", "KEY_SPLIT_XOR", "KEY_COMBINE_XOR", "COMPONENT_SELECT", "PARITY_ADJUST", "PARITY_CHECK",
            "KDF_HKDF", "KDF_SP800_108", "KDF_X963", "KDF_SCRYPT", "KDF_ARGON2",
            "AES_KEYWRAP_3394", "AES_UNWRAP_3394", "AES_KEYWRAP_5649", "AES_UNWRAP_5649",
            "TR31_WRAP", "TR31_UNWRAP", "TR31_PARSE_HEADER", "ICSF_TOKEN_PARSE",
            "KEYPAIR_GENERATE", "KEY_MATERIAL_INSPECT", "RSA_KEYPAIR_GENERATE");

    private static final Set<Representation> HEX = Set.of(Representation.HEX);
    private static final Set<Representation> HEX_COMPONENTS = Set.of(Representation.HEX_COMPONENTS);
    private static final Set<Representation> TEXT = Set.of(Representation.TEXT_UTF8);
    private static final Set<Representation> BINARY = Set.of(Representation.BINARY);
    private static final Set<Representation> HEX_OR_BINARY = Set.of(Representation.HEX, Representation.BINARY);
    private static final List<String> DIGESTS = List.of("SHA-1", "SHA-256", "SHA-512");
    private static final List<String> KCV_METHODS = List.of(
            "VISA", "IBM", "ATALLA", "ATALLA_R", "FUTUREX", "CMAC", "AES", "SHA256", "FULL_ZERO_BLOCK");

    @Override
    public Set<String> supportedTypes() { return TYPES; }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return switch (node.type.toUpperCase(Locale.ROOT)) {
            case "KCV", "PARITY_ADJUST", "PARITY_CHECK" -> List.of(optionalPort("key", HEX));
            case "KEY_SPLIT_XOR" -> List.of(optionalPort("key", HEX));
            case "KEY_COMBINE_XOR" -> List.of(optionalPort("components", HEX_COMPONENTS));
            case "COMPONENT_SELECT" -> List.of(optionalPort("components", HEX_COMPONENTS));
            case "KDF_HKDF" -> List.of(optionalPort("ikm", HEX), new PortDefinition("salt", HEX, false),
                    new PortDefinition("info", HEX, false));
            case "KDF_SP800_108" -> List.of(optionalPort("key", HEX), new PortDefinition("label", HEX, false),
                    new PortDefinition("context", HEX, false));
            case "KDF_X963" -> List.of(optionalPort("sharedSecret", HEX), new PortDefinition("sharedInfo", HEX, false));
            case "KDF_SCRYPT", "KDF_ARGON2" -> List.of(optionalPort("password", HEX), new PortDefinition("salt", HEX, false));
            case "AES_KEYWRAP_3394", "AES_KEYWRAP_5649" -> List.of(optionalPort("kek", HEX), optionalPort("keyData", HEX));
            case "AES_UNWRAP_3394", "AES_UNWRAP_5649" -> List.of(optionalPort("kek", HEX), new PortDefinition("wrapped", BINARY, false));
            case "TR31_WRAP" -> List.of(optionalPort("kbpk", HEX), optionalPort("key", HEX));
            case "TR31_UNWRAP" -> List.of(optionalPort("kbpk", HEX), new PortDefinition("keyBlock", TEXT, false));
            case "TR31_PARSE_HEADER" -> List.of(new PortDefinition("keyBlock", TEXT, false));
            case "ICSF_TOKEN_PARSE" -> List.of(new PortDefinition("token", HEX, false));
            case "KEY_MATERIAL_INSPECT" -> List.of(optionalPort("key", BINARY));
            default -> List.of();
        };
    }

    private static PortDefinition optionalPort(String name, Set<Representation> representations) {
        return new PortDefinition(name, representations, false);
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return switch (node.type.toUpperCase(Locale.ROOT)) {
            case "KCV", "KEY_COMBINE_XOR", "COMPONENT_SELECT", "PARITY_ADJUST", "PARITY_CHECK" -> Representation.HEX;
            case "KEY_SPLIT_XOR" -> Representation.HEX_COMPONENTS;
            case "TR31_WRAP", "TR31_PARSE_HEADER", "ICSF_TOKEN_PARSE", "KEY_MATERIAL_INSPECT" -> Representation.TEXT_UTF8;
            case "TR31_UNWRAP" -> Representation.HEX;
            case "AES_KEYWRAP_3394", "AES_KEYWRAP_5649", "AES_UNWRAP_3394", "AES_UNWRAP_5649",
                    "KDF_HKDF", "KDF_SP800_108", "KDF_X963", "KDF_SCRYPT", "KDF_ARGON2",
                    "KEYPAIR_GENERATE", "RSA_KEYPAIR_GENERATE" -> Representation.BINARY;
            default -> Representation.BINARY;
        };
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) {
        String type = node.type.toUpperCase(Locale.ROOT);
        for (String field : List.of("key", "kek", "keyData", "ikm", "sharedSecret", "password", "salt", "token")) {
            validateConfiguredHex(node, field);
        }
        switch (type) {
            case "KCV" -> { oneOf(node, "method", KCV_METHODS); requireInput(node, "key"); validateConfiguredKcvLength(node); }
            case "KEY_SPLIT_XOR" -> { integer(node, "componentCount", 2, 2, 5); requireInput(node, "key"); }
            case "KEY_COMBINE_XOR" -> requireInput(node, "components");
            case "COMPONENT_SELECT" -> { requireInput(node, "components"); integer(node, "index", 1, 1, 5); }
            case "PARITY_ADJUST", "PARITY_CHECK" -> requireInput(node, "key");
            case "KDF_HKDF" -> {
                requireInput(node, "ikm");
                oneOf(node, "digest", DIGESTS);
                outputLength(node, digest(node, "digest"), 32, 255 * digest(node, "digest").getDigestSize());
            }
            case "KDF_SP800_108" -> {
                requireInput(node, "key");
                oneOf(node, "digest", DIGESTS);
                outputLength(node, digest(node, "digest"), 32, Integer.MAX_VALUE);
            }
            case "KDF_X963" -> {
                requireInput(node, "sharedSecret");
                oneOf(node, "digest", DIGESTS);
                outputLength(node, digest(node, "digest"), 32, Integer.MAX_VALUE);
            }
            case "KDF_SCRYPT" -> {
                requireInput(node, "password"); requireSalt(node);
                scryptCost(node);
                positive(node, "r", 8);
                positive(node, "p", 1);
                positive(node, "outputLength", 32);
            }
            case "KDF_ARGON2" -> {
                requireInput(node, "password"); requireSalt(node);
                positive(node, "iterations", 3);
                argonMemory(node);
                positive(node, "outputLength", 32);
            }
            case "TR31_WRAP" -> {
                requireInput(node, "kbpk"); requireInput(node, "key");
                if (node.configuration.containsKey("usage")) requiredText(node, "usage");
                singleChar(node, "version");
                singleChar(node, "algorithm");
                singleChar(node, "mode");
                singleChar(node, "exportability");
                try {
                    com.cryptocarver.crypto.TR31.validateMatrix(firstChar(node, "version", 'B'), firstChar(node, "algorithm", 'T'),
                            setting(node, "usage", "P0"), firstChar(node, "mode", 'E'), firstChar(node, "exportability", 'N'));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid TR-31 header option");
                }
            }
            case "TR31_UNWRAP" -> { requireInput(node, "kbpk"); requireInput(node, "keyBlock"); }
            case "TR31_PARSE_HEADER" -> requireInput(node, "keyBlock");
            case "ICSF_TOKEN_PARSE" -> requireInput(node, "token");
            case "AES_KEYWRAP_3394", "AES_KEYWRAP_5649" -> { requireInput(node, "kek"); requireInput(node, "keyData"); validateConfiguredAesLengths(node); }
            case "AES_UNWRAP_3394", "AES_UNWRAP_5649" -> { requireInput(node, "kek"); requireInput(node, "wrapped"); }
            case "KEYPAIR_GENERATE", "RSA_KEYPAIR_GENERATE" -> oneOf(node, "algorithm",
                    List.of("RSA", "DSA", "ECDSA", "EdDSA", "ED25519"));
            case "KEY_MATERIAL_INSPECT" -> { oneOf(node, "algorithm", List.of("AES", "DESede", "RSA", "DSA", "EC", "Ed25519")); requireInput(node, "key"); }
            default -> { }
        }
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        try {
            String type = node.type.toUpperCase(Locale.ROOT);
            return switch (type) {
                case "KCV" -> hex(kcv(node, bytes(node, inputs, "key")));
                case "KEY_SPLIT_XOR" -> split(node, bytes(node, inputs, "key"));
                case "KEY_COMBINE_XOR" -> hex(KeyOperations.combineKeyComponents(parseComponents(inputs, node)));
                case "COMPONENT_SELECT" -> selectComponent(node, inputs);
                case "PARITY_ADJUST" -> parityAdjust(bytes(node, inputs, "key"));
                case "PARITY_CHECK" -> text(KeyOperations.detectParity(bytes(node, inputs, "key")).toString());
                case "KDF_HKDF" -> binary(KeyDerivation.hkdf(bytes(node, inputs, "ikm"), optionalBytes(inputs, node, "salt"),
                        optionalBytes(inputs, node, "info"), integer(node, "outputLength", 32, 1, 255 * digest(node, "digest").getDigestSize()), digest(node, "digest")));
                case "KDF_SP800_108" -> binary(KeyDerivation.sp800108Counter(bytes(node, inputs, "key"), optionalBytes(inputs, node, "label"),
                        optionalBytes(inputs, node, "context"), integer(node, "outputLength", 32, 1, Integer.MAX_VALUE), digest(node, "digest")));
                case "KDF_X963" -> binary(KeyDerivation.x963(bytes(node, inputs, "sharedSecret"), optionalBytes(inputs, node, "sharedInfo"),
                        integer(node, "outputLength", 32, 1, Integer.MAX_VALUE), digest(node, "digest")));
                case "KDF_SCRYPT" -> binary(KeyDerivation.scrypt(bytes(node, inputs, "password"), requiredOptionalBytes(inputs, node, "salt"),
                        integer(node, "N", 16_384, 2, Integer.MAX_VALUE), integer(node, "r", 8, 1, Integer.MAX_VALUE),
                        integer(node, "p", 1, 1, Integer.MAX_VALUE), integer(node, "outputLength", 32, 1, Integer.MAX_VALUE)));
                case "KDF_ARGON2" -> binary(KeyDerivation.argon2(bytes(node, inputs, "password"), requiredOptionalBytes(inputs, node, "salt"),
                        integer(node, "iterations", 3, 1, Integer.MAX_VALUE), integer(node, "memory", 65_536, 8, Integer.MAX_VALUE),
                        integer(node, "parallelism", 1, 1, Integer.MAX_VALUE), integer(node, "outputLength", 32, 1, Integer.MAX_VALUE)));
                case "AES_KEYWRAP_3394" -> binary(KeyWrapOperations.wrapRfc3394(bytes(node, inputs, "kek"), bytes(node, inputs, "keyData")));
                case "AES_UNWRAP_3394" -> binary(KeyWrapOperations.unwrapRfc3394(bytes(node, inputs, "kek"), bytes(node, inputs, "wrapped")));
                case "AES_KEYWRAP_5649" -> binary(KeyWrapOperations.wrapRfc5649(bytes(node, inputs, "kek"), bytes(node, inputs, "keyData")));
                case "AES_UNWRAP_5649" -> binary(KeyWrapOperations.unwrapRfc5649(bytes(node, inputs, "kek"), bytes(node, inputs, "wrapped")));
                case "TR31_WRAP" -> text(TR31Operations.wrapKey(stringBytes(inputs, node, "kbpk"), stringBytes(inputs, node, "key"),
                        setting(node, "usage", "P0").toUpperCase(Locale.ROOT), firstChar(node, "version", 'B'), firstChar(node, "algorithm", 'T'),
                        firstChar(node, "mode", 'E'), firstChar(node, "exportability", 'N')));
                case "TR31_UNWRAP" -> FlowValue.hex(TR31Operations.unwrapKey(stringBytes(inputs, node, "kbpk"), stringBytes(inputs, node, "keyBlock"))
                        .getBytes(StandardCharsets.UTF_8));
                case "TR31_PARSE_HEADER" -> text(TR31Operations.parseHeader(stringBytes(inputs, node, "keyBlock")));
                case "ICSF_TOKEN_PARSE" -> parseToken(node, inputs.get("token"));
                case "KEYPAIR_GENERATE", "RSA_KEYPAIR_GENERATE" -> binary(generateKeyPair(node).getPrivate().getEncoded());
                case "KEY_MATERIAL_INSPECT" -> text(KeyMaterialInspector.describeKey(decodeKey(node, bytes(node, inputs, "key"))));
                default -> throw new IllegalArgumentException("Unsupported key operation");
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Facade exceptions are intentionally not exposed to telemetry: providers
            // occasionally include algorithm/provider details that can contain input.
            throw new IllegalArgumentException("Key operation failed safely.");
        }
    }

    private static FlowValue parseToken(ProcessDefinition.Node node, FlowValue token) {
        byte[] raw = decodeHexValue(token, "token");
        Origin origin = Origin.fromValue(setting(node, "origin", "inferir"));
        com.cryptocarver.crypto.icsf.ParseResult result = IcsfTokenParser.parse(raw, origin);
        return text(IcsfTokenReport.renderText(result, origin, raw));
    }

    private static Key decodeKey(ProcessDefinition.Node node, byte[] encoded) throws Exception {
        String algorithm = setting(node, "algorithm", "AES");
        String kind = setting(node, "keyKind", "SECRET").toUpperCase(Locale.ROOT);
        if ("SECRET".equals(kind)) return new SecretKeySpec(encoded, algorithm);
        KeyFactory factory = KeyFactory.getInstance("EC".equalsIgnoreCase(algorithm) ? "EC" : algorithm);
        EncodedKeySpec spec = "PUBLIC".equals(kind) ? new X509EncodedKeySpec(encoded) : new PKCS8EncodedKeySpec(encoded);
        return "PUBLIC".equals(kind) ? factory.generatePublic(spec) : factory.generatePrivate(spec);
    }

    private static KeyPair generateKeyPair(ProcessDefinition.Node node) throws Exception {
        String algorithm = setting(node, "algorithm", "RSA");
        return switch (algorithm.toUpperCase(Locale.ROOT)) {
            case "RSA" -> AsymmetricKeyOperations.generateRSAKeyPair(integer(node, "keySize", 2048, 1024, 16_384));
            case "DSA" -> AsymmetricKeyOperations.generateDSAKeyPair(setting(node, "keySize", "2048/256"));
            case "ECDSA", "EC" -> AsymmetricKeyOperations.generateECDSAFpKeyPair(setting(node, "curve", "secp256r1"));
            case "EDDSA", "ED25519" -> AsymmetricKeyOperations.generateEd25519KeyPair();
            default -> throw new IllegalArgumentException("Unsupported key-pair algorithm");
        };
    }

    private static byte[] kcv(ProcessDefinition.Node node, byte[] key) throws Exception {
        return switch (setting(node, "method", "VISA").toUpperCase(Locale.ROOT)) {
            case "VISA" -> KeyOperations.calculateKCV_VISA(key);
            case "IBM" -> KeyOperations.calculateKCV_IBM(key);
            case "ATALLA" -> KeyOperations.calculateKCV_ATALLA(key);
            case "ATALLA_R" -> KeyOperations.calculateKCV_ATALLA_R(key);
            case "FUTUREX" -> KeyOperations.calculateKCV_FUTUREX(key);
            case "CMAC" -> KeyOperations.calculateKCV_CMAC(key);
            case "AES" -> KeyOperations.calculateKCV_AES(key);
            case "SHA256" -> KeyOperations.calculateKCV_SHA256(key);
            case "FULL_ZERO_BLOCK" -> KeyOperations.calculateFullZeroBlockKCV(key, setting(node, "algorithm", "AES"));
            default -> throw new IllegalArgumentException("Unsupported KCV method");
        };
    }

    private static FlowValue split(ProcessDefinition.Node node, byte[] key) {
        byte[][] components = KeyOperations.splitKey(key, integer(node, "componentCount", 2, 2, 5));
        // The current process SPI has one FlowValue per node. Keep each component
        // independently addressable in a lossless HEX bundle for KEY_COMBINE_XOR.
        String bundle = Arrays.stream(components).map(HexFormat.of()::formatHex).reduce((a, b) -> a + ":" + b).orElse("");
        return FlowValue.hexComponents(bundle.getBytes(StandardCharsets.UTF_8));
    }

    private static FlowValue parityAdjust(byte[] source) {
        byte[] adjusted = source.clone();
        KeyOperations.applyOddParity(adjusted);
        return hex(adjusted);
    }

    private static byte[][] parseComponents(Map<String, FlowValue> inputs, ProcessDefinition.Node node) {
        String bundle = stringBytes(inputs, node, "components");
        String[] parts = bundle.split(":", -1);
        if (parts.length < 2 || parts.length > 5) throw new IllegalArgumentException("Invalid XOR component bundle");
        byte[][] result = new byte[parts.length][];
        int length = -1;
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = HexFormat.of().parseHex(parts[i]); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid XOR component representation"); }
            if (length < 0) length = result[i].length;
            if (result[i].length != length) throw new IllegalArgumentException("XOR components must have equal length");
        }
        return result;
    }

    private static FlowValue selectComponent(ProcessDefinition.Node node, Map<String, FlowValue> inputs) {
        byte[][] components = parseComponents(inputs, node);
        int index = integer(node, "index", 1, 1, components.length);
        return hex(components[index - 1]);
    }

    private static Digest digest(ProcessDefinition.Node node, String name) { return KeyDerivation.getDigest(setting(node, name, "SHA-256")); }

    private static byte[] bytes(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String port) {
        FlowValue value = inputs.get(port);
        if (value != null) return decodeHexValue(value, port);
        String configured = node.configuration.get(port);
        if (configured != null && !configured.isBlank()) return parseHex(configured, port);
        throw new IllegalArgumentException("Missing required key input");
    }

    private static byte[] optionalBytes(Map<String, FlowValue> inputs, ProcessDefinition.Node node, String port) {
        FlowValue value = inputs.get(port);
        if (value != null) return decodeHexValue(value, port);
        String configured = node.configuration.get(port);
        return configured == null || configured.isBlank() ? new byte[0] : parseHex(configured, port);
    }

    private static byte[] requiredOptionalBytes(Map<String, FlowValue> inputs, ProcessDefinition.Node node, String port) {
        byte[] value = optionalBytes(inputs, node, port);
        if (value.length == 0) throw new IllegalArgumentException("Missing required KDF salt");
        return value;
    }

    private static byte[] decodeHexValue(FlowValue value, String name) {
        if (value == null) throw new IllegalArgumentException("Missing required key input");
        if (value.representation() == Representation.HEX || value.representation() == Representation.TEXT_UTF8) {
            return parseHex(value.render().trim(), name);
        }
        return value.bytes().clone();
    }

    private static byte[] parseHex(String value, String name) {
        try { return HexFormat.of().parseHex(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid HEX representation for key input"); }
    }

    private static String stringBytes(Map<String, FlowValue> inputs, ProcessDefinition.Node node, String port) {
        FlowValue value = inputs.get(port);
        if (value != null) return value.render().trim();
        String configured = node.configuration.get(port);
        if (configured == null || configured.isBlank()) throw new IllegalArgumentException("Missing required key input");
        return configured.trim();
    }

    private static String setting(ProcessDefinition.Node node, String key, String defaultValue) {
        String value = node.configuration.get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static char firstChar(ProcessDefinition.Node node, String key, char defaultValue) { return setting(node, key, String.valueOf(defaultValue)).charAt(0); }

    private static FlowValue binary(byte[] value) { return FlowValue.binary(value); }
    private static FlowValue hex(byte[] value) { return FlowValue.hex(HexFormat.of().withUpperCase().formatHex(value).getBytes(StandardCharsets.UTF_8)); }
    private static FlowValue text(String value) { return FlowValue.text(value, StandardCharsets.UTF_8); }

    private static void requiredText(ProcessDefinition.Node node, String key) {
        if (node.configuration.get(key) == null || node.configuration.get(key).isBlank()) throw new IllegalArgumentException("Missing required configuration");
    }

    private static void requireInput(ProcessDefinition.Node node, String name) {
        String configured = node.configuration.get(name);
        if ((configured == null || configured.isBlank())
                && !"true".equalsIgnoreCase(node.configuration.get(name + "FromFlow"))
                && !("key".equals(name) && "true".equalsIgnoreCase(node.configuration.get("keyFromFlow")))
                && !NodeCatalog.isSupplied(node, name)) {
            throw new IllegalArgumentException("Missing required key input");
        }
    }

    private static void requireSalt(ProcessDefinition.Node node) { requireInput(node, "salt"); }

    private static void outputLength(ProcessDefinition.Node node, Digest digest, int defaultValue, int max) {
        integer(node, "outputLength", defaultValue, 1, max);
    }

    private static int scryptCost(ProcessDefinition.Node node) {
        int n = integer(node, "N", 16_384, 2, Integer.MAX_VALUE);
        if ((n & (n - 1)) != 0) throw new IllegalArgumentException("scrypt N must be a power of two");
        return n;
    }

    private static int argonMemory(ProcessDefinition.Node node) {
        int parallelism = integer(node, "parallelism", 1, 1, Integer.MAX_VALUE);
        int memory = integer(node, "memory", 65_536, 8 * parallelism, Integer.MAX_VALUE);
        return memory;
    }

    private static void singleChar(ProcessDefinition.Node node, String key) {
        String value = node.configuration.get(key);
        if (value == null || value.isBlank()) return;
        if (value.length() != 1) throw new IllegalArgumentException("Invalid TR-31 header option");
    }

    private static void validateConfiguredHex(ProcessDefinition.Node node, String field) {
        String value = node.configuration.get(field);
        if (value == null || value.isBlank()) return;
        // Component bundles and wrapped blobs have their own framing; ordinary
        // key inputs are deliberately strict HEX and never silently converted.
        if ("components".equals(field) || "wrapped".equals(field)) return;
        parseHex(value.trim(), field);
    }

    private static void validateConfiguredKcvLength(ProcessDefinition.Node node) {
        String value = node.configuration.get("key");
        if (value == null || value.isBlank()) return;
        int length = parseHex(value, "key").length;
        String method = setting(node, "method", "VISA").toUpperCase(Locale.ROOT);
        boolean des = method.equals("VISA") || method.equals("IBM") || method.equals("ATALLA")
                || method.equals("ATALLA_R") || method.equals("FUTUREX");
        if (des && !(length == 8 || length == 16 || length == 24)) {
            throw new IllegalArgumentException("Invalid key length for KCV method");
        }
        if ((method.equals("AES") || method.equals("CMAC")) && !(length == 16 || length == 24 || length == 32)) {
            throw new IllegalArgumentException("Invalid key length for KCV method");
        }
    }

    private static void validateConfiguredAesLengths(ProcessDefinition.Node node) {
        String kek = node.configuration.get("kek");
        if (kek != null && !kek.isBlank() && !Set.of(16, 24, 32).contains(parseHex(kek, "kek").length)) {
            throw new IllegalArgumentException("Invalid AES KEK length");
        }
        String data = node.configuration.get("keyData");
        if (data != null && !data.isBlank() && "AES_KEYWRAP_3394".equalsIgnoreCase(node.type)
                && (parseHex(data, "keyData").length < 16 || parseHex(data, "keyData").length % 8 != 0)) {
            throw new IllegalArgumentException("Invalid RFC 3394 key-data length");
        }
    }

    private static void oneOf(ProcessDefinition.Node node, String key, List<String> values) {
        String actual = setting(node, key, values.get(0));
        if (values.stream().noneMatch(value -> value.equalsIgnoreCase(actual))) throw new IllegalArgumentException("Invalid key operation option");
    }

    private static int integer(ProcessDefinition.Node node, String key, int defaultValue, int min, int max) {
        int value;
        try { value = Integer.parseInt(setting(node, key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Key operation number is invalid"); }
        if (value < min || value > max) throw new IllegalArgumentException("Key operation number is out of range");
        return value;
    }

    private static void positive(ProcessDefinition.Node node, String key, int defaultValue) { integer(node, key, defaultValue, 1, Integer.MAX_VALUE); }

    private static String sensitiveLabel(String type) { return type.toLowerCase(Locale.ROOT); }

    private static NodeParameter secret(String key, String labelKey) {
        return new NodeParameter(key, labelKey, ParameterKind.PASSWORD, List.of(), "", true, null, null);
    }

    private static NodeParameter combo(String key, String labelKey, List<String> values, String defaultValue) {
        return new NodeParameter(key, labelKey, ParameterKind.COMBO, values, defaultValue);
    }

    private static NodeParameter number(String key, String labelKey, String defaultValue) {
        return new NodeParameter(key, labelKey, ParameterKind.NUMBER, defaultValue);
    }

    @Override
    public List<NodeDescriptor> descriptors() {
        List<NodeDescriptor> result = new ArrayList<>();
        result.add(new NodeDescriptor("KCV", "Key Operations", "module.process.type.key.kcv", "module.process.desc.key.kcv", "🔎",
                List.of(combo("method", "module.process.param.key.kcvMethod", KCV_METHODS, "VISA"),
                        combo("algorithm", "module.process.param.algorithm", List.of("DES", "AES"), "AES"), secret("key", "module.process.param.keyMaterial"))));
        result.add(new NodeDescriptor("KEY_SPLIT_XOR", "Key Operations", "module.process.type.key.split", "module.process.desc.key.split", "🧩",
                List.of(number("componentCount", "module.process.param.components", "2"), secret("key", "module.process.param.keyMaterial"))));
        result.add(new NodeDescriptor("KEY_COMBINE_XOR", "Key Operations", "module.process.type.key.combine", "module.process.desc.key.combine", "🧩",
                List.of(secret("components", "module.process.param.componentsMaterial"))));
        result.add(new NodeDescriptor("COMPONENT_SELECT", "Key Operations", "module.process.type.key.componentSelect", "module.process.desc.key.componentSelect", "🔎",
                List.of(number("index", "module.process.param.componentIndex", "1"), secret("components", "module.process.param.componentsMaterial"))));
        result.add(new NodeDescriptor("PARITY_ADJUST", "Key Operations", "module.process.type.key.parityAdjust", "module.process.desc.key.parityAdjust", "⚖",
                List.of(secret("key", "module.process.param.keyMaterial"))));
        result.add(new NodeDescriptor("PARITY_CHECK", "Key Operations", "module.process.type.key.parityCheck", "module.process.desc.key.parityCheck", "⚖",
                List.of(secret("key", "module.process.param.keyMaterial"))));
        result.addAll(kdfDescriptors());
        result.add(new NodeDescriptor("AES_KEYWRAP_3394", "Key Operations", "module.process.type.key.wrap3394", "module.process.desc.key.wrap3394", "📦",
                List.of(secret("kek", "module.process.param.kek"), secret("keyData", "module.process.param.keyMaterial"))));
        result.add(new NodeDescriptor("AES_UNWRAP_3394", "Key Operations", "module.process.type.key.unwrap3394", "module.process.desc.key.unwrap3394", "📦",
                List.of(secret("kek", "module.process.param.kek"), secret("wrapped", "module.process.param.wrappedMaterial"))));
        result.add(new NodeDescriptor("AES_KEYWRAP_5649", "Key Operations", "module.process.type.key.wrap5649", "module.process.desc.key.wrap5649", "📦",
                List.of(secret("kek", "module.process.param.kek"), secret("keyData", "module.process.param.keyMaterial"))));
        result.add(new NodeDescriptor("AES_UNWRAP_5649", "Key Operations", "module.process.type.key.unwrap5649", "module.process.desc.key.unwrap5649", "📦",
                List.of(secret("kek", "module.process.param.kek"), secret("wrapped", "module.process.param.wrappedMaterial"))));
        result.addAll(tr31Descriptors());
        result.add(new NodeDescriptor("ICSF_TOKEN_PARSE", "Key Operations", "module.process.type.key.icsfParse", "module.process.desc.key.icsfParse", "🔬",
                List.of(combo("origin", "module.process.param.origin", List.of("inferir", "kds-crudo", "key-record-read"), "inferir"), secret("token", "module.process.param.tokenMaterial"))));
        result.add(new NodeDescriptor("KEYPAIR_GENERATE", "Key Operations", "module.process.type.keypairGenerate", "module.process.desc.keypairGenerate", "🗝",
                List.of(combo("algorithm", "module.process.param.algorithm", List.of("RSA", "DSA", "ECDSA", "EdDSA"), "RSA"),
                        combo("keySize", "module.process.param.keySize", List.of("2048", "3072", "4096"), "2048"),
                        combo("curve", "module.process.param.curve", List.of("secp256r1", "secp384r1", "secp521r1"), "secp256r1"))));
        result.add(new NodeDescriptor("KEY_MATERIAL_INSPECT", "Key Operations", "module.process.type.key.inspect", "module.process.desc.key.inspect", "🧾",
                List.of(combo("algorithm", "module.process.param.algorithm", List.of("AES", "DESede", "RSA", "DSA", "EC", "Ed25519"), "AES"),
                        secret("key", "module.process.param.keyMaterial"),
                        combo("keyKind", "module.process.param.keyKind", List.of("SECRET", "PUBLIC", "PRIVATE"), "SECRET"))));
        result.add(new NodeDescriptor("RSA_KEYPAIR_GENERATE", "Key Material", "module.process.type.rsaKeypairGenerate", "module.process.desc.rsaKeypairGenerate", "🗝",
                List.of(combo("algorithm", "module.process.param.algorithm", List.of("RSA"), "RSA"),
                        combo("keySize", "module.process.param.keySize", List.of("2048", "3072", "4096"), "2048"))));
        return result;
    }

    private static List<NodeDescriptor> kdfDescriptors() {
        List<NodeDescriptor> list = new ArrayList<>();
        list.add(new NodeDescriptor("KDF_HKDF", "Key Derivation", "module.process.type.key.hkdf", "module.process.desc.key.hkdf", "🧬",
                List.of(combo("digest", "module.process.param.digest", DIGESTS, "SHA-256"), number("outputLength", "module.process.param.outputLength", "32"), secret("ikm", "module.process.param.keyMaterial"))));
        list.add(new NodeDescriptor("KDF_SP800_108", "Key Derivation", "module.process.type.key.sp800108", "module.process.desc.key.sp800108", "🧬",
                List.of(combo("digest", "module.process.param.digest", DIGESTS, "SHA-256"), number("outputLength", "module.process.param.outputLength", "32"), secret("key", "module.process.param.keyMaterial"))));
        list.add(new NodeDescriptor("KDF_X963", "Key Derivation", "module.process.type.key.x963", "module.process.desc.key.x963", "🧬",
                List.of(combo("digest", "module.process.param.digest", DIGESTS, "SHA-256"), number("outputLength", "module.process.param.outputLength", "32"), secret("sharedSecret", "module.process.param.keyMaterial"))));
        list.add(new NodeDescriptor("KDF_SCRYPT", "Key Derivation", "module.process.type.key.scrypt", "module.process.desc.key.scrypt", "🧬",
                List.of(number("N", "module.process.param.scryptN", "16384"), number("r", "module.process.param.scryptR", "8"), number("p", "module.process.param.scryptP", "1"), number("outputLength", "module.process.param.outputLength", "32"), secret("password", "module.process.param.password"))));
        list.add(new NodeDescriptor("KDF_ARGON2", "Key Derivation", "module.process.type.key.argon2", "module.process.desc.key.argon2", "🧬",
                List.of(number("iterations", "module.process.param.iterations", "3"), number("memory", "module.process.param.argonMemory", "65536"), number("parallelism", "module.process.param.parallelism", "1"), number("outputLength", "module.process.param.outputLength", "32"), secret("password", "module.process.param.password"))));
        return list;
    }

    private static List<NodeDescriptor> tr31Descriptors() {
        List<NodeDescriptor> list = new ArrayList<>();
        list.add(new NodeDescriptor("TR31_WRAP", "Key Operations", "module.process.type.key.tr31Wrap", "module.process.desc.key.tr31Wrap", "📦",
                List.of(secret("kbpk", "module.process.param.kbpk"), secret("key", "module.process.param.keyMaterial"), new NodeParameter("usage", "module.process.param.tr31Usage", ParameterKind.TEXT, "P0"), combo("version", "module.process.param.tr31Version", List.of("A", "B", "C", "D"), "B"), new NodeParameter("algorithm", "module.process.param.tr31Algorithm", ParameterKind.TEXT, "T"), new NodeParameter("mode", "module.process.param.tr31Mode", ParameterKind.TEXT, "E"), new NodeParameter("exportability", "module.process.param.tr31Exportability", ParameterKind.TEXT, "N"))));
        list.add(new NodeDescriptor("TR31_UNWRAP", "Key Operations", "module.process.type.key.tr31Unwrap", "module.process.desc.key.tr31Unwrap", "📦", List.of(secret("kbpk", "module.process.param.kbpk"), secret("keyBlock", "module.process.param.keyBlock"))));
        list.add(new NodeDescriptor("TR31_PARSE_HEADER", "Key Operations", "module.process.type.key.tr31ParseHeader", "module.process.desc.key.tr31ParseHeader", "🔎", List.of(secret("keyBlock", "module.process.param.keyBlock"))));
        return list;
    }
}
