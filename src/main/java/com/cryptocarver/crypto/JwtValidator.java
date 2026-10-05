package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verifies a signed JWT and checks its claims against the module's validation settings. */
public final class JwtValidator {

    /** Additional application/profile checks. Empty values leave that check disabled. */
    public record Advanced(String allowedAlgorithms, String expectedType, String expectedContentType,
            boolean rfc9068AccessToken, String expectedNonce, String accessToken, String authorizationCode,
            String expectedJkt, String expectedX5tS256, Set<String> understoodCriticalHeaders,
            boolean ignoreCriticalHeaders) {
        public Advanced {
            understoodCriticalHeaders = understoodCriticalHeaders == null ? Set.of()
                    : Set.copyOf(understoodCriticalHeaders);
        }

        public static Advanced defaults() {
            return new Advanced(null, null, null, false, null, null, null, null, null, Set.of(), false);
        }
    }

    public record Options(String expectedIssuer, String expectedAudience, long clockSkewSeconds,
            boolean checkTimes, boolean oidcStrict, SecretEncoding secretEncoding, boolean acceptNone,
            boolean trustHeaderKey, Advanced advanced) {
        public Options {
            advanced = advanced == null ? Advanced.defaults() : advanced;
        }
        public Options(String expectedIssuer, String expectedAudience, long clockSkewSeconds,
                boolean checkTimes, boolean oidcStrict, SecretEncoding secretEncoding, boolean acceptNone,
                boolean trustHeaderKey) {
            this(expectedIssuer, expectedAudience, clockSkewSeconds, checkTimes, oidcStrict, secretEncoding,
                    acceptNone, trustHeaderKey, Advanced.defaults());
        }
        public Options(String expectedIssuer, String expectedAudience, long clockSkewSeconds,
                boolean checkTimes, boolean oidcStrict, SecretEncoding secretEncoding, boolean acceptNone) {
            this(expectedIssuer, expectedAudience, clockSkewSeconds, checkTimes, oidcStrict,
                    secretEncoding, acceptNone, false, Advanced.defaults());
        }
        public Options(String expectedIssuer, String expectedAudience, long clockSkewSeconds,
                boolean checkTimes, boolean oidcStrict, SecretEncoding secretEncoding) {
            this(expectedIssuer, expectedAudience, clockSkewSeconds, checkTimes, oidcStrict,
                    secretEncoding, false, false, Advanced.defaults());
        }
    }

    /** A failed check; {@code code} maps to {@code module.jose.claim.<code>}, {@code argument} fills it. */
    public record Finding(String code, String argument) {
    }

    /** A non-failing but security-relevant condition shown separately from claim failures. */
    public record Warning(String code, String argument) {
    }

    public record Result(String header, String payload, boolean signatureValid, List<Finding> findings,
            List<Warning> warnings) {
        public Result(String header, String payload, boolean signatureValid, List<Finding> findings) {
            this(header, payload, signatureValid, findings, List.of());
        }
        public boolean valid() { return signatureValid && findings.isEmpty(); }
    }

    private static final Set<String> BASE_UNDERSTOOD_CRITICAL = Set.of("b64");

    private JwtValidator() {
    }

    public static Result validate(String token, String key, Options options, Instant now) throws Exception {
        String headerText;
        JWTClaimsSet claims;
        JWSHeader header = null;
        String tokenType = null;
        String contentType = null;
        boolean signatureValid;
        List<Finding> findings = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        Advanced advanced = options.advanced();
        if (JoseNoneJws.isUnsecuredCompact(token)) {
            String payload = JoseNoneJws.payloadIfUnsigned(token.trim());
            String headerB64 = token.trim().split("\\.", -1)[0];
            headerText = new com.nimbusds.jose.util.Base64URL(headerB64).decodeToString();
            Map<String, Object> unsecuredHeader = com.nimbusds.jose.util.JSONObjectUtils.parse(headerText);
            tokenType = stringValue(unsecuredHeader.get("typ"));
            contentType = stringValue(unsecuredHeader.get("cty"));
            claims = JWTClaimsSet.parse(payload);
            String algorithmName = stringValue(unsecuredHeader.get("alg"));
            boolean algorithmAllowed = isAlgorithmAllowed(algorithmName, advanced.allowedAlgorithms());
            if (!algorithmAllowed) findings.add(new Finding("algorithmNotAllowed", algorithmName));
            signatureValid = options.acceptNone() && algorithmAllowed;
        } else {
            SignedJWT jwt = SignedJWT.parse(token.trim());
            claims = jwt.getJWTClaimsSet();
            header = jwt.getHeader();
            tokenType = header.getType() == null ? null : header.getType().toString();
            contentType = header.getContentType();
            headerText = header.toString();
            Set<String> critical = header.getCriticalParams() == null ? Set.of() : header.getCriticalParams();
            Set<String> understood = new java.util.HashSet<>(BASE_UNDERSTOOD_CRITICAL);
            understood.addAll(advanced.understoodCriticalHeaders());
            Set<String> unknown = new java.util.HashSet<>(critical);
            unknown.removeAll(understood);
            if (!unknown.isEmpty()) {
                if (advanced.ignoreCriticalHeaders()) {
                    unknown.stream().sorted().forEach(name -> warnings.add(new Warning("ignoredCrit", name)));
                } else {
                    unknown.stream().sorted().forEach(name -> findings.add(new Finding("unsupportedCrit", name)));
                }
            }
            boolean algorithmAllowed = isAlgorithmAllowed(header.getAlgorithm(), advanced.allowedAlgorithms());
            if (!algorithmAllowed) findings.add(new Finding("algorithmNotAllowed", header.getAlgorithm().getName()));
            if (usesRsaPublicKeyAsHmacSecret(header.getAlgorithm(), key)) {
                warnings.add(new Warning("algorithmConfusion", header.getAlgorithm().getName()));
            }
            Set<String> deferred = advanced.ignoreCriticalHeaders() ? unknown : Set.of();
            if (!algorithmAllowed) {
                signatureValid = false;
            } else if (usesRsaPublicKeyAsHmacSecret(header.getAlgorithm(), key)) {
                // Reject this known algorithm-confusion configuration while returning a user-visible warning.
                signatureValid = false;
            } else {
                signatureValid = jwt.verify(JOSEService.resolveVerifier(header, key,
                        options.secretEncoding(), options.trustHeaderKey(), understood, deferred));
            }
        }

        checkText("type", advanced.expectedType(), tokenType, findings);
        checkText("contentType", advanced.expectedContentType(), contentType, findings);
        if (present(options.expectedIssuer()) && !options.expectedIssuer().trim().equals(claims.getIssuer())) {
            findings.add(new Finding("issuer", options.expectedIssuer().trim()));
        }
        if (present(options.expectedAudience())
                && (claims.getAudience() == null || !claims.getAudience().contains(options.expectedAudience().trim()))) {
            findings.add(new Finding("audience", options.expectedAudience().trim()));
        }
        if (options.oidcStrict()) {
            if (claims.getExpirationTime() == null) findings.add(new Finding("missing", "exp"));
            if (claims.getIssueTime() == null) findings.add(new Finding("missing", "iat"));
            if (claims.getIssuer() == null) findings.add(new Finding("missing", "iss"));
            if (claims.getAudience() == null || claims.getAudience().isEmpty()) findings.add(new Finding("missing", "aud"));
        }
        if (advanced.rfc9068AccessToken()) {
            for (String name : List.of("iss", "exp", "aud", "sub", "client_id", "iat", "jti")) {
                if (!claims.getClaims().containsKey(name) || claims.getClaim(name) == null) {
                    findings.add(new Finding("missing", name));
                }
            }
            if (!"at+jwt".equals(tokenType) && !"application/at+jwt".equals(tokenType)) {
                findings.add(new Finding("type", "at+jwt"));
            }
        }
        checkText("nonce", advanced.expectedNonce(), stringValue(claims.getClaim("nonce")), findings);
        if (present(advanced.accessToken())) checkHashClaim("at_hash", advanced.accessToken(), header, claims, findings);
        if (present(advanced.authorizationCode())) checkHashClaim("c_hash", advanced.authorizationCode(), header, claims, findings);
        Object confirmation = claims.getClaim("cnf");
        if (confirmation instanceof Map<?, ?> cnf) {
            checkText("jkt", advanced.expectedJkt(), stringValue(cnf.get("jkt")), findings);
            checkText("x5tS256", advanced.expectedX5tS256(), stringValue(cnf.get("x5t#S256")), findings);
        } else {
            if (present(advanced.expectedJkt())) findings.add(new Finding("jkt", advanced.expectedJkt()));
            if (present(advanced.expectedX5tS256())) findings.add(new Finding("x5tS256", advanced.expectedX5tS256()));
        }
        if (options.checkTimes() || options.oidcStrict() || advanced.rfc9068AccessToken()) {
            long skew = options.clockSkewSeconds() * 1000L;
            long nowMillis = now.toEpochMilli();
            Date exp = claims.getExpirationTime();
            Date nbf = claims.getNotBeforeTime();
            Date iat = claims.getIssueTime();
            if (exp != null && nowMillis > exp.getTime() + skew) findings.add(new Finding("expired", exp.toInstant().toString()));
            if (nbf != null && nowMillis < nbf.getTime() - skew) findings.add(new Finding("notYetValid", nbf.toInstant().toString()));
            if (iat != null && nowMillis < iat.getTime() - skew) findings.add(new Finding("issuedInFuture", iat.toInstant().toString()));
        }
        return new Result(headerText, claims.toString(), signatureValid, List.copyOf(findings), List.copyOf(warnings));
    }

    private static boolean isAlgorithmAllowed(JWSAlgorithm algorithm, String allowlist) {
        return isAlgorithmAllowed(algorithm.getName(), allowlist);
    }

    private static boolean isAlgorithmAllowed(String algorithmName, String allowlist) {
        if (!present(allowlist)) return true;
        for (String candidate : allowlist.split(",")) {
            if (algorithmName != null && algorithmName.equals(candidate.trim())) return true;
        }
        return false;
    }

    private static boolean usesRsaPublicKeyAsHmacSecret(JWSAlgorithm algorithm, String key) {
        if (key == null || !JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) return false;
        try {
            String trimmed = key.trim();
            if (trimmed.startsWith("{") && trimmed.contains("\"kty\"")) {
                return "RSA".equals(com.nimbusds.jose.util.JSONObjectUtils.parse(trimmed).get("kty"));
            }
            return trimmed.contains("BEGIN PUBLIC KEY") && JoseKeyMaterial.publicKey(trimmed) instanceof java.security.interfaces.RSAPublicKey;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void checkHashClaim(String claimName, String value, JWSHeader header, JWTClaimsSet claims,
            List<Finding> findings) throws Exception {
        String actual = stringValue(claims.getClaim(claimName));
        if (actual == null) {
            findings.add(new Finding("missing", claimName));
            return;
        }
        String algorithm = hashAlgorithm(header == null ? null : header.getAlgorithm());
        if (algorithm == null || !constantTimeEquals(actual, halfHash(algorithm, value))) {
            findings.add(new Finding(claimName, "mismatch"));
        }
    }

    private static String hashAlgorithm(JWSAlgorithm algorithm) {
        if (algorithm == null) return null;
        String name = algorithm.getName();
        if (name.endsWith("256")) return "SHA-256";
        if (name.endsWith("384")) return "SHA-384";
        if (name.endsWith("512")) return "SHA-512";
        return null;
    }

    private static String halfHash(String algorithm, String value) throws Exception {
        byte[] digest = MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(digest, digest.length / 2));
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static void checkText(String code, String expected, String actual, List<Finding> findings) {
        if (present(expected) && !expected.trim().equals(actual)) findings.add(new Finding(code, expected.trim()));
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
