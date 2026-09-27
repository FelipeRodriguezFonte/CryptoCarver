package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/** Verifies a signed JWT and checks its claims against the module's validation settings. */
public final class JwtValidator {

    /** {@code b64} is the only critical header this module understands (RFC 7797). */
    private static final Set<String> UNDERSTOOD_CRITICAL = Set.of("b64");

    public record Options(String expectedIssuer, String expectedAudience, long clockSkewSeconds,
            boolean checkTimes, boolean oidcStrict, SecretEncoding secretEncoding) {
    }

    /** A failed check; {@code code} maps to {@code module.jose.claim.<code>}, {@code argument} fills it. */
    public record Finding(String code, String argument) {
    }

    public record Result(String header, String payload, boolean signatureValid, List<Finding> findings) {
        public boolean valid() {
            return signatureValid && findings.isEmpty();
        }
    }

    private JwtValidator() {
    }

    public static Result validate(String token, String key, Options options, Instant now) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token.trim());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        List<Finding> findings = new ArrayList<>();

        Set<String> critical = jwt.getHeader().getCriticalParams();
        if (critical != null) {
            for (String name : critical) {
                if (!UNDERSTOOD_CRITICAL.contains(name)) findings.add(new Finding("unsupportedCrit", name));
            }
        }
        boolean signatureValid = jwt.verify(JOSEService.resolveVerifier(jwt.getHeader(), key, options.secretEncoding()));

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
        if (options.checkTimes() || options.oidcStrict()) {
            long skew = options.clockSkewSeconds() * 1000L;
            long nowMillis = now.toEpochMilli();
            Date exp = claims.getExpirationTime();
            Date nbf = claims.getNotBeforeTime();
            Date iat = claims.getIssueTime();
            if (exp != null && nowMillis > exp.getTime() + skew) findings.add(new Finding("expired", exp.toInstant().toString()));
            if (nbf != null && nowMillis < nbf.getTime() - skew) findings.add(new Finding("notYetValid", nbf.toInstant().toString()));
            if (iat != null && nowMillis < iat.getTime() - skew) findings.add(new Finding("issuedInFuture", iat.toInstant().toString()));
        }
        return new Result(jwt.getHeader().toString(), claims.toString(), signatureValid, List.copyOf(findings));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
