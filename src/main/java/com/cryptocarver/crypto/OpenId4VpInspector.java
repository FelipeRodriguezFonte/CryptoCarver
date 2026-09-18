package com.cryptocarver.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads an OpenID4VP authorization request — the thing a verifier hands a
 * wallet before it will present anything.
 *
 * <p>Nothing here speaks the protocol. A request object is pasted in and read:
 * who is asking, under what identity, what they want back, and what they expect
 * the holder to sign. That is the part worth looking at when a presentation is
 * refused and nobody can say why, and it is the part a laboratory can inspect
 * without a wallet, a verifier or a network.</p>
 *
 * <h2>The client identifier carries its own scheme</h2>
 * <p>OpenID4VP 1.0 moved the scheme into the identifier itself, so a client id
 * reads {@code x509_san_dns:verifier.example} rather than being paired with a
 * separate {@code client_id_scheme}. Both shapes are read here, because
 * deployments in the field still carry the older one, and the scheme is what
 * decides how the request's signature is to be trusted at all — a
 * {@code redirect_uri} client is not authenticated by anything.</p>
 */
public final class OpenId4VpInspector {

    /** The identifier prefixes OpenID4VP 1.0 defines, with what each one means
     *  for trusting the request. */
    public enum ClientIdScheme {
        X509_SAN_DNS("x509_san_dns", "authenticated by a certificate whose SAN carries the DNS name"),
        X509_HASH("x509_hash", "authenticated by the hash of a certificate"),
        REDIRECT_URI("redirect_uri", "NOT authenticated: the request is unsigned and the identity is the redirect URI"),
        VERIFIER_ATTESTATION("verifier_attestation", "authenticated by an attestation from a trusted issuer"),
        OPENID_FEDERATION("openid_federation", "authenticated through an OpenID Federation trust chain"),
        DECENTRALIZED_IDENTIFIER("decentralized_identifier", "authenticated by a key resolved from a DID"),
        PRE_REGISTERED("pre-registered", "authenticated out of band, by prior registration");

        private final String prefix;
        private final String meaning;

        ClientIdScheme(String prefix, String meaning) {
            this.prefix = prefix;
            this.meaning = meaning;
        }

        public String prefix() {
            return prefix;
        }

        public String meaning() {
            return meaning;
        }

        public static ClientIdScheme fromPrefix(String value) {
            for (ClientIdScheme scheme : values()) {
                if (scheme.prefix.equals(value)) {
                    return scheme;
                }
            }
            return null;
        }
    }

    public record Finding(String severity, String message) {
    }

    public record RequestReport(JsonObject header,
                                JsonObject claims,
                                ClientIdScheme scheme,
                                String clientId,
                                List<JsonObject> transactionData,
                                boolean signatureVerified,
                                List<Finding> findings) {
    }

    private static final Gson GSON = new Gson();

    private OpenId4VpInspector() {
    }

    /**
     * Reads a request object.
     *
     * @param verifier verifies the JAR signature, or {@code null} to read
     *                 without verifying — which is reported rather than passed over
     */
    public static RequestReport inspectRequest(String requestObject, JWSVerifier verifier) throws Exception {
        List<Finding> findings = new ArrayList<>();
        String compact = requestObject.trim();

        JsonObject header;
        JsonObject claims;
        boolean verified = false;

        if (compact.startsWith("{")) {
            // A plain request, not a JAR. Legal in OpenID4VP and worth saying
            // out loud: nothing about it is signed.
            header = new JsonObject();
            claims = JsonParser.parseString(compact).getAsJsonObject();
            findings.add(new Finding("WARN",
                    "This request is not signed. Anything in it can have been altered in transit,"
                            + " and the wallet has nothing to authenticate the verifier with."));
        } else {
            JWSObject jws = JWSObject.parse(compact);
            header = JsonParser.parseString(jws.getHeader().toString()).getAsJsonObject();
            claims = JsonParser.parseString(jws.getPayload().toString()).getAsJsonObject();
            if (verifier == null) {
                findings.add(new Finding("WARN",
                        "The request signature was not verified: no key was supplied."));
            } else if (jws.verify(verifier)) {
                verified = true;
            } else {
                findings.add(new Finding("ERROR", "The request signature does not verify."));
            }
            if ("none".equalsIgnoreCase(String.valueOf(header.get("alg")).replace("\"", ""))) {
                findings.add(new Finding("ERROR", "alg is 'none': this request is not signed at all."));
            }
        }

        String clientId = claims.has("client_id") ? claims.get("client_id").getAsString() : null;
        ClientIdScheme scheme = resolveScheme(clientId, claims);
        if (clientId == null) {
            findings.add(new Finding("ERROR", "The request carries no client_id."));
        } else if (scheme == null) {
            findings.add(new Finding("WARN",
                    "The client identifier '" + clientId + "' names no scheme OpenID4VP defines,"
                            + " so how the wallet is meant to authenticate this verifier is undefined."));
        } else if (scheme == ClientIdScheme.REDIRECT_URI) {
            findings.add(new Finding("WARN",
                    "The redirect_uri scheme does not authenticate the verifier at all."));
        }

        if (!claims.has("nonce")) {
            findings.add(new Finding("ERROR",
                    "No nonce: without it the holder's signature is not tied to this request"
                            + " and an old presentation can be replayed into it."));
        }
        if (!claims.has("response_mode")) {
            findings.add(new Finding("WARN", "No response_mode."));
        }

        List<JsonObject> transactionData = new ArrayList<>();
        if (claims.has("transaction_data")) {
            for (JsonElement element : claims.getAsJsonArray("transaction_data")) {
                transactionData.add(Ts12ScaOperations.decodeTransactionData(element.getAsString()));
            }
            String responseMode = claims.has("response_mode")
                    ? claims.get("response_mode").getAsString() : "";
            if (!responseMode.endsWith(".jwt")) {
                // Transaction data describes a payment: amount, payee, account.
                // Returning it through an unencrypted response mode puts that in
                // the front channel.
                findings.add(new Finding("WARN",
                        "This request carries transaction data but asks for response_mode '"
                                + responseMode + "', which is not encrypted. Payment details would"
                                + " travel in the clear."));
            }
        }

        if (findings.isEmpty()) {
            findings.add(new Finding("INFO", "Nothing to report about this request."));
        }
        return new RequestReport(header, claims, scheme, clientId,
                List.copyOf(transactionData), verified, List.copyOf(findings));
    }

    /** 1.0 puts the scheme in the identifier; earlier drafts used a separate
     *  {@code client_id_scheme} claim, which is still met in the field. */
    private static ClientIdScheme resolveScheme(String clientId, JsonObject claims) {
        if (claims.has("client_id_scheme")) {
            return ClientIdScheme.fromPrefix(claims.get("client_id_scheme").getAsString());
        }
        if (clientId == null) {
            return null;
        }
        int colon = clientId.indexOf(':');
        if (colon < 0) {
            // No prefix at all is the pre-registered case.
            return ClientIdScheme.PRE_REGISTERED;
        }
        return ClientIdScheme.fromPrefix(clientId.substring(0, colon));
    }

    public static String describe(String requestObject, JWSVerifier verifier, Locale locale) throws Exception {
        boolean spanish = locale != null && "es".equals(locale.getLanguage());
        RequestReport report = inspectRequest(requestObject, verifier);
        StringBuilder text = new StringBuilder();

        text.append(spanish ? "Petición OpenID4VP" : "OpenID4VP request").append('\n');
        text.append("  client_id    : ").append(report.clientId()).append('\n');
        text.append("  ").append(spanish ? "esquema" : "scheme").append("      : ")
                .append(report.scheme() == null ? "-" : report.scheme().prefix()).append('\n');
        if (report.scheme() != null) {
            text.append("                 ").append(report.scheme().meaning()).append('\n');
        }
        text.append("  ").append(spanish ? "firma" : "signature").append("        : ")
                .append(report.signatureVerified()
                        ? (spanish ? "verificada" : "verified")
                        : (spanish ? "no verificada" : "not verified")).append('\n');
        for (String claim : List.of("response_type", "response_mode", "nonce", "state", "aud", "iss")) {
            if (report.claims().has(claim)) {
                text.append("  ").append(pad(claim)).append(": ")
                        .append(report.claims().get(claim)).append('\n');
            }
        }
        if (report.claims().has("dcql_query")) {
            text.append('\n').append(spanish ? "Consulta DCQL" : "DCQL query").append('\n');
            text.append("  ").append(GSON.toJson(report.claims().get("dcql_query"))).append('\n');
        }
        if (!report.transactionData().isEmpty()) {
            text.append('\n').append(spanish ? "Datos de transacción" : "Transaction data").append('\n');
            for (JsonObject entry : report.transactionData()) {
                text.append("  - ").append(GSON.toJson(entry)).append('\n');
            }
        }
        text.append('\n').append(spanish ? "Hallazgos" : "Findings").append('\n');
        for (Finding finding : report.findings()) {
            text.append("  [").append(finding.severity()).append("] ").append(finding.message()).append('\n');
        }
        return text.toString();
    }

    private static String pad(String value) {
        return value.length() >= 13 ? value : value + " ".repeat(13 - value.length());
    }
}
