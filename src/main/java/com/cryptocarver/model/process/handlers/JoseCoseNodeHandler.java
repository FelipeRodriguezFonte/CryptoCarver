package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.COSEOperations;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.SignerConfig;
import com.cryptocarver.model.process.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Thin Process Designer adapters for the local JOSE and COSE facades. */
public final class JoseCoseNodeHandler implements ProcessNodeHandler {
    public static final Set<String> TYPES = Set.of(
            "JWS_SIGN", "JWS_VERIFY", "JWS_DETACHED_SIGN", "JWS_DETACHED_VERIFY",
            "JWE_ENCRYPT", "JWE_DECRYPT", "JWT_INSPECT", "COSE_SIGN1", "COSE_VERIFY1",
            "COSE_MAC0", "COSE_VERIFY_MAC0", "COSE_ENCRYPT0", "COSE_DECRYPT0");

    @Override public Set<String> supportedTypes() { return TYPES; }

    @Override public List<NodeDescriptor> descriptors() {
        List<String> jws = List.of("HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512");
        List<String> jwe = List.of("dir", "RSA-OAEP-256");
        List<String> enc = List.of("A128GCM", "A192GCM", "A256GCM");
        List<String> coseSign = List.of("ES256", "ES384", "ES512", "PS256", "PS384", "PS512", "EDDSA");
        List<String> coseMac = List.of("HS256", "HS384", "HS512");
        List<String> coseEnc = List.of("A128GCM", "A192GCM", "A256GCM");
        return List.of(
                descriptor("JWS_SIGN", "jwsSign", params(combo("algorithm", jws, "HS256"), secret("key"))),
                descriptor("JWS_VERIFY", "jwsVerify", params(combo("algorithm", jws, "HS256"), secret("key"))),
                descriptor("JWS_DETACHED_SIGN", "jwsDetachedSign", params(combo("algorithm", jws, "HS256"), secret("key"), check("unencodedPayload", "false"))),
                descriptor("JWS_DETACHED_VERIFY", "jwsDetachedVerify", params(combo("algorithm", jws, "HS256"), secret("key"))),
                descriptor("JWE_ENCRYPT", "jweEncrypt", params(combo("keyAlgorithm", jwe, "dir"), combo("contentAlgorithm", enc, "A256GCM"), secret("cek"))),
                descriptor("JWE_DECRYPT", "jweDecrypt", params(secret("cek"))),
                descriptor("JWT_INSPECT", "jwtInspect", List.of()),
                descriptor("COSE_SIGN1", "coseSign1", params(combo("algorithm", coseSign, "ES256"), secret("privateKey"), multiline("publicKey"))),
                descriptor("COSE_VERIFY1", "coseVerify1", params(combo("algorithm", coseSign, "ES256"), multiline("publicKey"))),
                descriptor("COSE_MAC0", "coseMac0", params(combo("algorithm", coseMac, "HS256"), secret("key"))),
                descriptor("COSE_VERIFY_MAC0", "coseVerifyMac0", params(secret("key"))),
                descriptor("COSE_ENCRYPT0", "coseEncrypt0", params(combo("algorithm", coseEnc, "A256GCM"), secret("cek"))),
                descriptor("COSE_DECRYPT0", "coseDecrypt0", params(secret("cek")))
        );
    }

    @Override public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        Set<Representation> any = Representation.standardValues();
        return switch (node.type) {
            case "JWS_SIGN", "JWE_ENCRYPT", "COSE_MAC0", "COSE_ENCRYPT0" -> List.of(port("payload", any, true), port(keyName(node.type), any, false));
            case "JWS_VERIFY", "JWE_DECRYPT", "COSE_VERIFY_MAC0", "COSE_DECRYPT0" -> List.of(port("message", any, true), port(keyName(node.type), any, false));
            case "JWS_DETACHED_SIGN" -> List.of(port("payload", any, true), port("key", any, false));
            case "JWS_DETACHED_VERIFY" -> List.of(port("message", any, true), port("payload", any, true), port("key", any, false));
            case "JWT_INSPECT" -> List.of(port("message", any, true));
            case "COSE_SIGN1" -> List.of(port("payload", any, true), port("privateKey", any, false), port("publicKey", any, false));
            case "COSE_VERIFY1" -> List.of(port("message", any, true), port("publicKey", any, false));
            default -> throw new IllegalArgumentException("Unsupported JOSE/COSE node: " + node.type);
        };
    }

    @Override public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        if (node.type.startsWith("COSE_")) return Representation.BINARY;
        return Representation.TEXT_UTF8;
    }

    @Override public void validateConfiguration(ProcessDefinition.Node node) {
        switch (node.type) {
            case "JWT_INSPECT" -> { return; }
            case "JWS_SIGN", "JWS_VERIFY", "JWS_DETACHED_SIGN", "JWS_DETACHED_VERIFY" -> {
                enumValue(node, "algorithm", Set.of("HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512"));
                supplied(node, "key");
            }
            case "JWE_ENCRYPT" -> {
                enumValue(node, "keyAlgorithm", Set.of("dir", "RSA-OAEP-256"));
                enumValue(node, "contentAlgorithm", Set.of("A128GCM", "A192GCM", "A256GCM"));
                supplied(node, "cek");
            }
            case "JWE_DECRYPT", "COSE_DECRYPT0" -> supplied(node, "cek");
            case "COSE_ENCRYPT0" -> { enumValue(node, "algorithm", Set.of("A128GCM", "A192GCM", "A256GCM")); supplied(node, "cek"); }
            case "COSE_MAC0", "COSE_VERIFY_MAC0" -> {
                if ("COSE_MAC0".equals(node.type)) enumValue(node, "algorithm", Set.of("HS256", "HS384", "HS512"));
                supplied(node, "key");
            }
            case "COSE_SIGN1" -> {
                enumValue(node, "algorithm", Set.of("ES256", "ES384", "ES512", "PS256", "PS384", "PS512", "EDDSA"));
                supplied(node, "privateKey");
            }
            case "COSE_VERIFY1" -> {
                enumValue(node, "algorithm", Set.of("ES256", "ES384", "ES512", "PS256", "PS384", "PS512", "EDDSA"));
                supplied(node, "publicKey");
            }
            default -> throw new IllegalArgumentException("Unsupported JOSE/COSE node: " + node.type);
        }
    }

    @Override public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        return switch (node.type) {
            case "JWS_SIGN" -> text(JOSEService.signJws(string(inputs, "payload"), value(node, "algorithm", "HS256"), setting(node, inputs, "key")));
            case "JWS_VERIFY" -> text(JOSEService.verifyJws(string(inputs, "message"), value(node, "algorithm", "HS256"), setting(node, inputs, "key")));
            case "JWS_DETACHED_SIGN" -> text(JOSEService.generateDetachedJWS(string(inputs, "payload"), List.of(new SignerConfig(value(node, "algorithm", "HS256"), setting(node, inputs, "key"))), "Compact", Boolean.parseBoolean(value(node, "unencodedPayload", "false"))));
            case "JWS_DETACHED_VERIFY" -> {
                String payload = string(inputs, "payload");
                if (!JOSEService.verifyDetachedJWS(string(inputs, "message"), payload, value(node, "algorithm", "HS256"), setting(node, inputs, "key"))) throw new IllegalArgumentException("Detached JWS verification failed");
                yield text(payload);
            }
            case "JWE_ENCRYPT" -> text(JOSEService.encryptJwe(string(inputs, "payload"), value(node, "keyAlgorithm", "dir"), value(node, "contentAlgorithm", "A256GCM"), setting(node, inputs, "cek")));
            case "JWE_DECRYPT" -> text(JOSEService.decryptJwe(string(inputs, "message"), setting(node, inputs, "cek")));
            case "JWT_INSPECT" -> text(JOSEService.inspectJwt(string(inputs, "message")));
            case "COSE_SIGN1" -> FlowValue.binary(COSEOperations.sign1(bytes(inputs, "payload"), privateKey(node, inputs), optionalPublicKey(node, inputs), COSEOperations.SignAlgorithm.valueOf(value(node, "algorithm", "ES256"))));
            case "COSE_VERIFY1" -> {
                COSEOperations.Sign1Result result = COSEOperations.verify1(bytes(inputs, "message"), publicKey(node, inputs));
                if (!result.isVerified()) throw new IllegalArgumentException("COSE_Sign1 verification failed");
                yield FlowValue.binary(result.getPayload());
            }
            case "COSE_MAC0" -> FlowValue.binary(COSEOperations.mac0(bytes(inputs, "payload"), secretBytes(node, inputs, "key"), COSEOperations.MacAlgorithm.valueOf(value(node, "algorithm", "HS256"))));
            case "COSE_VERIFY_MAC0" -> {
                COSEOperations.Mac0Result result = COSEOperations.verifyMac0(bytes(inputs, "message"), secretBytes(node, inputs, "key"));
                if (!result.isVerified()) throw new IllegalArgumentException("COSE_Mac0 verification failed");
                yield FlowValue.binary(result.getPayload());
            }
            case "COSE_ENCRYPT0" -> FlowValue.binary(COSEOperations.encrypt0(bytes(inputs, "payload"), secretBytes(node, inputs, "cek"), COSEOperations.EncryptAlgorithm.valueOf(value(node, "algorithm", "A256GCM"))));
            case "COSE_DECRYPT0" -> FlowValue.binary(COSEOperations.decrypt0(bytes(inputs, "message"), secretBytes(node, inputs, "cek")));
            default -> throw new IllegalArgumentException("Unsupported JOSE/COSE node: " + node.type);
        };
    }

    private static NodeDescriptor descriptor(String type, String key, List<NodeParameter> params) { return new NodeDescriptor(type, "Envelopes / JOSE-COSE", "module.process.type." + key, "module.process.desc." + key, "✉", params); }
    private static List<NodeParameter> params(NodeParameter... p) { return List.of(p); }
    private static NodeParameter combo(String k, List<String> values, String d) { return new NodeParameter(k, "module.process.param." + k, ParameterKind.COMBO, values, d); }
    private static NodeParameter secret(String k) { return new NodeParameter(k, "module.process.param." + k, ParameterKind.PASSWORD, List.of(), "", true); }
    private static NodeParameter multiline(String k) { return new NodeParameter(k, "module.process.param." + k, ParameterKind.MULTILINE, ""); }
    private static NodeParameter check(String k, String d) { return new NodeParameter(k, "module.process.param." + k, ParameterKind.CHECKBOX, d); }
    private static PortDefinition port(String n, Set<Representation> r, boolean required) { return new PortDefinition(n, r, required); }
    private static String keyName(String type) { return type.contains("JWE") || type.contains("ENCRYPT0") || type.contains("DECRYPT0") ? "cek" : "key"; }
    private static String value(ProcessDefinition.Node n, String k, String d) { return n.configuration.getOrDefault(k, d); }
    private static void supplied(ProcessDefinition.Node n, String k) { if (!n.configuration.containsKey(k) && !"true".equalsIgnoreCase(n.configuration.get(k + "FromFlow")) && !NodeCatalog.isSupplied(n, k)) throw new IllegalArgumentException(k + " is required"); }
    private static void enumValue(ProcessDefinition.Node n, String k, Set<String> allowed) { if (!allowed.contains(value(n, k, ""))) throw new IllegalArgumentException("Unsupported " + k); }
    private static byte[] bytes(Map<String, FlowValue> in, String k) { FlowValue v = in.get(k); if (v == null) throw new IllegalArgumentException(k + " is required"); return v.bytes(); }
    private static String string(Map<String, FlowValue> in, String k) { FlowValue v = in.get(k); if (v == null) throw new IllegalArgumentException(k + " is required"); return new String(v.bytes(), v.charset() == null ? StandardCharsets.UTF_8 : v.charset()); }
    private static String setting(ProcessDefinition.Node n, Map<String, FlowValue> in, String k) { FlowValue v = in.get(k); return v == null ? n.configuration.get(k) : new String(v.bytes(), v.charset() == null ? StandardCharsets.UTF_8 : v.charset()); }
    private static byte[] secretBytes(ProcessDefinition.Node n, Map<String, FlowValue> in, String k) { FlowValue v = in.get(k); return v == null ? Objects.requireNonNull(n.configuration.get(k), k + " is required").getBytes(StandardCharsets.UTF_8) : v.bytes(); }
    private static PrivateKey privateKey(ProcessDefinition.Node n, Map<String, FlowValue> in) throws Exception { return (PrivateKey) decodeKey(settingBytes(n, in, "privateKey"), value(n, "algorithm", "ES256"), true); }
    private static PublicKey publicKey(ProcessDefinition.Node n, Map<String, FlowValue> in) throws Exception { return (PublicKey) decodeKey(settingBytes(n, in, "publicKey"), value(n, "algorithm", "ES256"), false); }
    private static PublicKey optionalPublicKey(ProcessDefinition.Node n, Map<String, FlowValue> in) throws Exception { return in.containsKey("publicKey") || n.configuration.containsKey("publicKey") ? publicKey(n, in) : null; }
    private static byte[] settingBytes(ProcessDefinition.Node n, Map<String, FlowValue> in, String k) { FlowValue v = in.get(k); if (v != null) return v.bytes(); String s = n.configuration.get(k); if (s == null) throw new IllegalArgumentException(k + " is required"); return decodePemOrBase64(s); }
    private static java.security.Key decodeKey(byte[] encoded, String algorithm, boolean privatePart) throws Exception { String factory = algorithm.startsWith("ES") ? "EC" : algorithm.equals("EDDSA") ? "Ed25519" : "RSA"; KeyFactory kf = KeyFactory.getInstance(factory); return privatePart ? kf.generatePrivate(new PKCS8EncodedKeySpec(encoded)) : kf.generatePublic(new X509EncodedKeySpec(encoded)); }
    private static byte[] decodePemOrBase64(String value) { String normalized = value.replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", ""); return Base64.getDecoder().decode(normalized); }
    private static FlowValue text(String value) { return FlowValue.text(value, StandardCharsets.UTF_8); }
}
