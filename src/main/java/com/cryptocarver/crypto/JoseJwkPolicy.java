package com.cryptocarver.crypto;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.*;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Local-only helpers for JWK metadata and explicitly trusted-in-token JWS keys. */
public final class JoseJwkPolicy {
    public enum Operation { SIGN, VERIFY, ENCRYPT, DECRYPT }

    private JoseJwkPolicy() { }

    /** Returns advisory text; metadata never overrides cryptographic verification. */
    public static String metadataWarning(String json, Operation operation) {
        if (json == null || json.isBlank()) return null;
        try {
            String text = json.trim();
            JWK key;
            if (text.startsWith("{") && text.contains("\"keys\"")) {
                List<JWK> keys = JWKSet.parse(text).getKeys();
                key = keys.isEmpty() ? null : keys.get(0);
            } else if (text.startsWith("{")) key = JWK.parse(text);
            else return null;
            if (key == null) return null;
            List<String> problems = new ArrayList<>();
            String expected = operation == Operation.SIGN || operation == Operation.VERIFY ? "sig" : "enc";
            if (key.getKeyUse() != null && !expected.equals(key.getKeyUse().identifier())) {
                problems.add("use=" + key.getKeyUse().identifier() + " for " + expected + " operation");
            }
            Set<KeyOperation> ops = key.getKeyOperations();
            if (ops != null && !ops.isEmpty()) {
                String required = switch (operation) {
                    case SIGN -> "sign";
                    case VERIFY -> "verify";
                    case ENCRYPT -> "encrypt";
                    case DECRYPT -> "decrypt";
                };
                if (ops.stream().noneMatch(value -> required.equals(value.identifier()))) {
                    problems.add("key_ops does not include " + required);
                }
            }
            return problems.isEmpty() ? null : String.join("; ", problems);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static JWK withMetadata(JWK key, String use, String keyOps) throws Exception {
        KeyUse keyUse = use == null || use.isBlank() ? key.getKeyUse() : KeyUse.parse(use.trim());
        Set<KeyOperation> operations = parseOperations(keyOps);
        if (operations.isEmpty()) operations = key.getKeyOperations();
        if (key instanceof RSAKey value) return new RSAKey.Builder(value).keyUse(keyUse).keyOperations(operations).build();
        if (key instanceof ECKey value) return new ECKey.Builder(value).keyUse(keyUse).keyOperations(operations).build();
        if (key instanceof OctetSequenceKey value) return new OctetSequenceKey.Builder(value).keyUse(keyUse).keyOperations(operations).build();
        if (key instanceof OctetKeyPair value) return new OctetKeyPair.Builder(value).keyUse(keyUse).keyOperations(operations).build();
        return key;
    }

    public static Set<KeyOperation> parseOperations(String value) {
        Set<KeyOperation> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        for (String part : value.split(",")) {
            String operation = part.trim();
            if (!operation.isEmpty()) {
                result.add(java.util.Arrays.stream(KeyOperation.values())
                        .filter(candidate -> candidate.identifier().equals(operation)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown JWK key operation: " + operation)));
            }
        }
        return result;
    }

    /** Returns a public-key encoding only when the caller explicitly opts into token headers. */
    public static String publicKeyMaterialFromHeader(JWSHeader header) throws Exception {
        JWK embedded = header.getJWK();
        if (embedded != null) {
            if (embedded.isPrivate()) throw new IllegalArgumentException("The embedded jwk must not contain private key material.");
            return embedded.toPublicJWK().toJSONString();
        }
        List<?> encodedChain = header.getX509CertChain();
        if (encodedChain == null || encodedChain.isEmpty()) {
            throw new IllegalArgumentException("The token header contains no embedded jwk or x5c key.");
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> chain = new ArrayList<>();
        for (Object item : encodedChain) {
            byte[] der = Base64.getDecoder().decode(item.toString());
            chain.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
        }
        X509Certificate leaf = chain.get(0);
        checkThumbprint(header.getX509CertThumbprint() == null ? null : header.getX509CertThumbprint().decode(),
                leaf, "SHA-1", "x5t");
        checkThumbprint(header.getX509CertSHA256Thumbprint() == null ? null : header.getX509CertSHA256Thumbprint().decode(),
                leaf, "SHA-256", "x5t#S256");
        // Check that the supplied certificates form a linked signature chain. Trust-anchor
        // validation remains the caller's responsibility; an in-token chain is not trusted.
        for (int index = 0; index + 1 < chain.size(); index++) {
            chain.get(index).verify(chain.get(index + 1).getPublicKey());
        }
        String type = leaf.getPublicKey().getFormat();
        if (type == null) throw new IllegalArgumentException("The x5c leaf certificate has no exportable public key.");
        return "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(leaf.getEncoded())
                + "\n-----END CERTIFICATE-----";
    }

    private static void checkThumbprint(byte[] expected, X509Certificate certificate, String digest, String name) throws Exception {
        if (expected == null) return;
        byte[] actual = MessageDigest.getInstance(digest).digest(certificate.getEncoded());
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException(name + " does not match the first certificate in x5c.");
        }
    }
}
