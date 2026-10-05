package com.cryptocarver.crypto;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;

import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit laboratory support for unsigned JWS objects (RFC 7518 §3.6). */
public final class JoseNoneJws {
    public static final JWSAlgorithm ALGORITHM = new JWSAlgorithm("none");

    private JoseNoneJws() { }

    public static JWSSigner signer() {
        return new JWSSigner() {
            private final JCAContext context = new JCAContext();
            @Override public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
                requireNone(header);
                return new Base64URL("");
            }
            @Override public Set<JWSAlgorithm> supportedJWSAlgorithms() { return Set.of(ALGORITHM); }
            @Override public JCAContext getJCAContext() { return context; }
        };
    }

    public static JWSVerifier verifier() {
        return new JWSVerifier() {
            private final JCAContext context = new JCAContext();
            @Override public boolean verify(JWSHeader header, byte[] signingInput, Base64URL signature) {
                return isNone(header) && signature != null && signature.toString().isEmpty();
            }
            @Override public Set<JWSAlgorithm> supportedJWSAlgorithms() { return Set.of(ALGORITHM); }
            @Override public JCAContext getJCAContext() { return context; }
        };
    }

    public static boolean isNone(JWSHeader header) {
        return header != null && "none".equalsIgnoreCase(header.getAlgorithm().getName());
    }

    public static String compact(String payload) {
        return compactHeader(Map.of("alg", "none"), payload);
    }

    public static String compactJwt(Map<String, Object> claims) throws JOSEException {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "none");
        header.put("typ", "JWT");
        return compactHeader(header, com.nimbusds.jose.util.JSONObjectUtils.toJSONString(claims));
    }

    public static String signedJwt(Map<String, Object> claims, String serialization) throws JOSEException {
        String payload = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(claims);
        if ("Compact".equals(serialization)) return compactJwt(claims);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "none");
        header.put("typ", "JWT");
        String protectedPart = Base64URL.encode(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(header)).toString();
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("protected", protectedPart);
        signature.put("signature", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payload", Base64URL.encode(payload).toString());
        if ("Flattened JSON".equals(serialization)) {
            result.putAll(signature);
        } else if ("General JSON".equals(serialization)) {
            result.put("signatures", java.util.List.of(signature));
        } else {
            throw new IllegalArgumentException("Unsupported JWS serialization: " + serialization);
        }
        return com.nimbusds.jose.util.JSONObjectUtils.toJSONString(result);
    }

    public static String detached(String serialization) throws JOSEException {
        Map<String, Object> header = Map.of("alg", "none");
        String protectedPart = Base64URL.encode(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(header)).toString();
        if ("Compact".equals(serialization)) return protectedPart + "..";
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("protected", protectedPart);
        signature.put("signature", "");
        Map<String, Object> result = new LinkedHashMap<>();
        if ("Flattened JSON".equals(serialization)) result.putAll(signature);
        else if ("General JSON".equals(serialization)) result.put("signatures", java.util.List.of(signature));
        else throw new IllegalArgumentException("Unsupported JWS serialization: " + serialization);
        return com.nimbusds.jose.util.JSONObjectUtils.toJSONString(result);
    }

    public static boolean verifyDetached(String token, String payload) throws JOSEException {
        try {
            String trimmed = token.trim();
            if (!trimmed.startsWith("{")) {
                String[] segments = trimmed.split("\\.", -1);
                if (segments.length != 3 || !segments[1].isEmpty() || !segments[2].isEmpty() || payload == null) return false;
                Map<String, Object> compactHeader = com.nimbusds.jose.util.JSONObjectUtils.parse(
                        new Base64URL(segments[0]).decodeToString());
                return "none".equals(compactHeader.get("alg"));
            }
            Map<String, Object> members = com.nimbusds.jose.util.JSONObjectUtils.parse(trimmed);
            String protectedPart = (String) members.get("protected");
            if (protectedPart == null && members.get("signatures") instanceof java.util.List<?> signatures
                    && !signatures.isEmpty() && signatures.get(0) instanceof Map<?, ?> item) {
                protectedPart = (String) item.get("protected");
                if (!"".equals(item.get("signature"))) return false;
            } else if (!"".equals(members.get("signature"))) return false;
            Map<String, Object> header = com.nimbusds.jose.util.JSONObjectUtils.parse(new Base64URL(protectedPart).decodeToString());
            return "none".equals(header.get("alg")) && payload != null;
        } catch (Exception e) {
            throw new JOSEException("The unsecured detached JWS is invalid.", e);
        }
    }

    public static String payloadIfUnsigned(String token) throws JOSEException {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3 || !segments[2].isEmpty()) {
            throw new JOSEException("An unsecured compact JWS must have an empty signature segment.");
        }
        try {
            Map<String, Object> header = com.nimbusds.jose.util.JSONObjectUtils.parse(
                    new Base64URL(segments[0]).decodeToString());
            if (!"none".equals(header.get("alg"))) throw new JOSEException("The JWS header is not alg=none.");
            return new Base64URL(segments[1]).decodeToString();
        } catch (java.text.ParseException | IllegalArgumentException e) {
            throw new JOSEException("The unsecured JWS header or payload is invalid.", e);
        }
    }

    public static boolean isUnsecuredCompact(String token) {
        if (token == null) return false;
        String[] segments = token.trim().split("\\.", -1);
        if (segments.length != 3) return false;
        try {
            Map<String, Object> header = com.nimbusds.jose.util.JSONObjectUtils.parse(
                    new Base64URL(segments[0]).decodeToString());
            return "none".equals(header.get("alg"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String compactHeader(Map<String, Object> header, String payload) {
        String headerJson = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(header);
        String protectedPart = Base64URL.encode(headerJson.getBytes(StandardCharsets.UTF_8)).toString();
        String payloadPart = Base64URL.encode(payload.getBytes(StandardCharsets.UTF_8)).toString();
        return protectedPart + "." + payloadPart + ".";
    }

    private static void requireNone(JWSHeader header) throws JOSEException {
        if (!isNone(header)) throw new JOSEException("The unsecured JWS signer only supports alg=none.");
    }
}
