package com.cryptocarver.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JWSVerifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TS12 — Strong Customer Authentication with the European Digital Identity
 * Wallet: the place where the payments half of this application and the eIDAS
 * half meet.
 *
 * <p>PSD2 has always demanded <em>dynamic linking</em>: the thing the payer
 * authenticates has to be <em>this</em> payment, for this amount, to this
 * payee, so that a captured authentication cannot be replayed against a
 * different one. TS12 achieves that with the credential machinery rather than
 * with a bank's own channel — the wallet presents an SCA attestation as an
 * SD-JWT VC and puts the hash of the transaction into the Key Binding JWT it
 * signs. Verify that hash and you have verified what the user actually agreed
 * to.</p>
 *
 * <h2>What is checked, and where each rule comes from</h2>
 * <ul>
 *   <li>The credential declares {@code "category": "urn:eu:europa:ec:eudi:sua:sca"},
 *       which is what makes it an SCA attestation rather than any other
 *       credential that happens to be presentable.</li>
 *   <li>The Key Binding JWT carries {@code jti} — "a fresh, cryptographically
 *       random value with sufficient entropy", unique per presentation. It is
 *       the authentication code, so a short or repeated one defeats the point.</li>
 *   <li>It carries {@code response_mode}, copied from the authorization
 *       request, and {@code amr} with <b>at least two different</b>
 *       authentication categories — two factors, which is the whole of SCA.</li>
 *   <li>{@code transaction_data_hashes} covers every transaction data entry the
 *       verifier sent. OpenID4VP hashes the base64url string as transmitted, the
 *       same rule SD-JWT uses for a Disclosure, and the result is base64url
 *       without padding.</li>
 * </ul>
 *
 * <p>The {@code amr} token lists below are TS12's own and are reproduced rather
 * than invented; a token outside them is reported as unknown instead of being
 * silently counted towards the two factors.</p>
 */
public final class Ts12ScaOperations {

    /** The claim that marks an attestation as usable for SCA. */
    public static final String SCA_CATEGORY = "urn:eu:europa:ec:eudi:sua:sca";

    /** The four transaction data types TS12 defines. */
    public enum TransactionType {
        PAYMENT("urn:eudi:sca:payment:1"),
        LOGIN_RISK("urn:eudi:sca:login_risk_transaction:1"),
        ACCOUNT_ACCESS("urn:eudi:sca:account_access:1"),
        EMANDATE("urn:eudi:sca:emandate:1");

        private final String urn;

        TransactionType(String urn) {
            this.urn = urn;
        }

        public String urn() {
            return urn;
        }

        public static TransactionType fromUrn(String urn) {
            for (TransactionType type : values()) {
                if (type.urn.equals(urn)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Not a TS12 transaction data type: " + urn);
        }
    }

    /**
     * The three factor categories of PSD2, with the tokens TS12 allows in each.
     * Two <em>categories</em> are required, not two tokens: two PINs are one
     * factor twice over.
     */
    public enum AmrCategory {
        KNOWLEDGE(Set.of("pin_less_than_6_digits", "pin_6_or_more_digits",
                "passphrase_less_than_8_chars", "passphrase_8_to_11_chars",
                "passphrase_12_or_more_chars", "pattern", "other")),
        POSSESSION(Set.of("key_in_remote_wscd", "key_in_local_external_wscd",
                "key_in_local_internal_wscd", "key_in_local_native_wscd", "other")),
        INHERENCE(Set.of("fingerprint_device", "fingerprint_external",
                "face_device", "face_external", "other"));

        private final Set<String> tokens;

        AmrCategory(Set<String> tokens) {
            this.tokens = tokens;
        }

        public Set<String> tokens() {
            return tokens;
        }
    }

    public record Finding(String severity, String requirement, String message) {
    }

    public record ScaReport(boolean credentialVerified,
                            JsonObject claims,
                            JsonObject keyBindingClaims,
                            List<AmrCategory> factors,
                            List<Finding> findings) {

        public boolean acceptable() {
            return findings.stream().noneMatch(finding -> "ERROR".equals(finding.severity()));
        }
    }

    private static final Gson GSON = new Gson();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    /** {@code jti} is the authentication code. 128 bits is the floor worth
     *  accepting; below that a captured code starts being guessable. */
    private static final int MINIMUM_JTI_BYTES = 16;

    private Ts12ScaOperations() {
    }

    // ------------------------------------------------------ transaction data

    /**
     * Builds one OpenID4VP {@code transaction_data} entry: a JSON object,
     * base64url-encoded. The encoded string is what travels and what gets
     * hashed, so it is returned rather than the object.
     *
     * @param credentialIds the credential the entry applies to, as named in the query
     * @param payloadJson   the type's own payload — for a payment, the amount and payee
     */
    public static String encodeTransactionData(TransactionType type,
                                               Collection<String> credentialIds,
                                               String payloadJson,
                                               String hashAlgorithm) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", type.urn());
        JsonArray ids = new JsonArray();
        credentialIds.forEach(ids::add);
        entry.add("credential_ids", ids);
        JsonArray algorithms = new JsonArray();
        algorithms.add(requireHashAlgorithm(hashAlgorithm));
        entry.add("transaction_data_hashes_alg", algorithms);
        entry.add("payload", JsonParser.parseString(payloadJson));
        return B64.encodeToString(GSON.toJson(entry).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hashes a transaction data entry the way OpenID4VP does: over the
     * base64url string as transmitted, not over the JSON inside it. Same rule
     * as an SD-JWT Disclosure, and for the same reason — the verifier never has
     * to re-serialize anything to check the binding.
     */
    public static String hashTransactionData(String encodedEntry, String hashAlgorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(jcaName(requireHashAlgorithm(hashAlgorithm)));
        return B64.encodeToString(digest.digest(encodedEntry.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Decodes an entry for reading. */
    public static JsonObject decodeTransactionData(String encodedEntry) {
        String json = new String(Base64.getUrlDecoder().decode(encodedEntry), StandardCharsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // ------------------------------------------------------------- verifying

    /**
     * Verifies an SCA presentation: the SD-JWT VC itself, then everything TS12
     * adds on top.
     *
     * @param transactionData the entries the verifier sent, in the form they
     *                        were sent — base64url strings. Every one of them
     *                        must be covered by the Key Binding JWT.
     * @param expectedResponseMode the {@code response_mode} of the authorization
     *                             request, which the KB-JWT has to echo
     */
    public static ScaReport verify(String presentation,
                                   List<String> transactionData,
                                   JWSVerifier issuerVerifier,
                                   JWSVerifier holderVerifier,
                                   String expectedAudience,
                                   String expectedNonce,
                                   String expectedResponseMode) throws Exception {
        List<Finding> findings = new ArrayList<>();

        SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(
                presentation, issuerVerifier, holderVerifier, expectedAudience, expectedNonce);
        verified.notes().forEach(note -> findings.add(new Finding("WARN", null, note)));

        if (!verified.keyBindingPresent()) {
            findings.add(new Finding("ERROR", "TS12",
                    "An SCA presentation without a Key Binding JWT is bound to nothing:"
                            + " there is no signature over the transaction at all."));
            return new ScaReport(true, verified.claims(), null, List.of(), List.copyOf(findings));
        }
        JsonObject kb = verified.keyBindingClaims();

        // The credential has to say it is for SCA.
        JsonElement category = verified.claims().get("category");
        if (category == null || !SCA_CATEGORY.equals(category.getAsString())) {
            findings.add(new Finding("ERROR", "TS12 4.1",
                    "This credential does not declare category " + SCA_CATEGORY
                            + ", so it is not an SCA attestation."));
        }

        // jti: the authentication code.
        if (!kb.has("jti")) {
            findings.add(new Finding("ERROR", "TS12 5.2",
                    "The Key Binding JWT carries no jti, so the presentation has no authentication code."));
        } else {
            String jti = kb.get("jti").getAsString();
            if (entropyBytes(jti) < MINIMUM_JTI_BYTES) {
                findings.add(new Finding("WARN", "TS12 5.2",
                        "jti carries roughly " + entropyBytes(jti) + " bytes; TS12 asks for a fresh,"
                                + " cryptographically random value with sufficient entropy, and this one"
                                + " is short enough to be worth questioning."));
            }
        }

        // response_mode, copied from the authorization request.
        if (!kb.has("response_mode")) {
            findings.add(new Finding("ERROR", "TS12 5.2",
                    "The Key Binding JWT carries no response_mode."));
        } else if (expectedResponseMode != null
                && !expectedResponseMode.equals(kb.get("response_mode").getAsString())) {
            findings.add(new Finding("ERROR", "TS12 5.2",
                    "response_mode is '" + kb.get("response_mode").getAsString()
                            + "' but the request used '" + expectedResponseMode + "'."));
        }

        List<AmrCategory> factors = checkAuthenticationFactors(kb, findings);
        checkTransactionBinding(kb, transactionData, findings);

        if (findings.isEmpty()) {
            findings.add(new Finding("INFO", null,
                    "The presentation is bound to this transaction and to two distinct factors."));
        }
        return new ScaReport(true, verified.claims(), kb, factors, List.copyOf(findings));
    }

    /**
     * {@code amr} must name at least two <em>different</em> categories. A wallet
     * that reports two knowledge tokens has authenticated once, not twice, and
     * counting tokens instead of categories is the way that goes unnoticed.
     */
    private static List<AmrCategory> checkAuthenticationFactors(JsonObject kb, List<Finding> findings) {
        if (!kb.has("amr")) {
            findings.add(new Finding("ERROR", "TS12 5.2",
                    "The Key Binding JWT carries no amr, so nothing says how the user authenticated."));
            return List.of();
        }
        Set<AmrCategory> categories = new LinkedHashSet<>();
        for (JsonElement element : kb.getAsJsonArray("amr")) {
            String token = element.isJsonObject()
                    ? firstMember(element.getAsJsonObject())
                    : element.getAsString();
            AmrCategory category = categoryOf(token);
            if (category == null) {
                findings.add(new Finding("WARN", "TS12 5.2",
                        "amr names '" + token + "', which is not one of the tokens TS12 defines;"
                                + " it is not counted towards the two factors."));
            } else {
                categories.add(category);
            }
        }
        if (categories.size() < 2) {
            findings.add(new Finding("ERROR", "TS12 5.2",
                    "amr names " + categories.size() + " authentication category; strong customer"
                            + " authentication needs two different ones, and "
                            + categories + " is what this presentation proves."));
        }
        return List.copyOf(categories);
    }

    /** Which category a token belongs to, or {@code null} if TS12 does not
     *  define it. {@code other} appears in all three lists and cannot be
     *  attributed, so it is treated as unknown rather than guessed. */
    public static AmrCategory categoryOf(String token) {
        if ("other".equals(token)) {
            return null;
        }
        for (AmrCategory category : AmrCategory.values()) {
            if (category.tokens().contains(token)) {
                return category;
            }
        }
        return null;
    }

    /**
     * The dynamic link itself. Every entry the verifier sent has to appear as a
     * hash in the Key Binding JWT: that is what makes the signature a signature
     * over <em>this</em> payment and not over the act of authenticating.
     */
    private static void checkTransactionBinding(JsonObject kb,
                                                List<String> transactionData,
                                                List<Finding> findings) throws Exception {
        if (transactionData == null || transactionData.isEmpty()) {
            return;
        }
        if (!kb.has("transaction_data_hashes")) {
            findings.add(new Finding("ERROR", "OpenID4VP 8.4",
                    "The verifier sent transaction data but the Key Binding JWT carries no"
                            + " transaction_data_hashes: this presentation authenticates a person,"
                            + " not a transaction."));
            return;
        }
        String algorithm = kb.has("transaction_data_hashes_alg")
                ? kb.get("transaction_data_hashes_alg").getAsString()
                : "sha-256";

        Set<String> presented = new LinkedHashSet<>();
        kb.getAsJsonArray("transaction_data_hashes").forEach(element -> presented.add(element.getAsString()));

        for (String entry : transactionData) {
            String expected = hashTransactionData(entry, algorithm);
            if (!presented.contains(expected)) {
                JsonObject decoded = decodeTransactionData(entry);
                String type = decoded.has("type") ? decoded.get("type").getAsString() : "unknown";
                findings.add(new Finding("ERROR", "OpenID4VP 8.4",
                        "The transaction data of type " + type + " is not covered by the Key Binding"
                                + " JWT. The user did not sign this transaction."));
            }
        }
    }

    // ------------------------------------------------------------- reporting

    public static String describe(ScaReport report, Locale locale) {
        boolean spanish = locale != null && "es".equals(locale.getLanguage());
        StringBuilder text = new StringBuilder();
        text.append(spanish ? "SCA con la cartera (TS12)" : "SCA with the wallet (TS12)").append('\n');

        text.append("  ").append(spanish ? "factores" : "factors").append(": ");
        text.append(report.factors().isEmpty()
                ? (spanish ? "ninguno reconocido" : "none recognised")
                : String.join(", ", report.factors().stream().map(Enum::name).toList()));
        text.append('\n');

        if (report.keyBindingClaims() != null) {
            JsonObject kb = report.keyBindingClaims();
            text.append("  jti          : ").append(kb.has("jti") ? kb.get("jti").getAsString() : "-").append('\n');
            text.append("  response_mode: ")
                    .append(kb.has("response_mode") ? kb.get("response_mode").getAsString() : "-").append('\n');
            if (kb.has("transaction_data_hashes")) {
                text.append("  ").append(spanish ? "transacciones firmadas" : "transactions signed")
                        .append(": ").append(kb.getAsJsonArray("transaction_data_hashes").size()).append('\n');
            }
        }

        text.append('\n').append(spanish ? "Claims de la credencial" : "Credential claims").append('\n');
        text.append("  ").append(GSON.toJson(report.claims())).append('\n');

        text.append('\n').append(spanish ? "Hallazgos" : "Findings").append('\n');
        for (Finding finding : report.findings()) {
            text.append("  [").append(finding.severity()).append(']');
            if (finding.requirement() != null) {
                text.append(' ').append(finding.requirement());
            }
            text.append(' ').append(finding.message()).append('\n');
        }

        text.append('\n').append(report.acceptable()
                ? (spanish ? "El enlace dinámico se sostiene." : "The dynamic link holds.")
                : (spanish ? "El enlace dinámico NO se sostiene." : "The dynamic link does NOT hold."))
                .append('\n');
        return text.toString();
    }

    // ---------------------------------------------------------------- utils

    private static String firstMember(JsonObject object) {
        // TS12's amr entries can be objects keyed by category; either shape is
        // reduced to the token so one code path checks both.
        for (String name : object.keySet()) {
            JsonElement value = object.get(name);
            return value.isJsonPrimitive() ? value.getAsString() : name;
        }
        return "";
    }

    private static String requireHashAlgorithm(String algorithm) {
        String value = algorithm == null || algorithm.isBlank() ? "sha-256" : algorithm;
        jcaName(value);
        return value;
    }

    /** The registry spelling, as in SD-JWT's {@code _sd_alg}, not the JCA one. */
    private static String jcaName(String registryName) {
        return switch (registryName.toLowerCase(Locale.ROOT)) {
            case "sha-256" -> "SHA-256";
            case "sha-384" -> "SHA-384";
            case "sha-512" -> "SHA-512";
            default -> throw new IllegalArgumentException("Unsupported hash algorithm: " + registryName);
        };
    }

    /** A rough size for a {@code jti}: how many bytes it would decode to if it
     *  is base64url, otherwise its length. Enough to catch a counter or a short
     *  string without pretending to measure real entropy. */
    private static int entropyBytes(String jti) {
        try {
            return Base64.getUrlDecoder().decode(jti).length;
        } catch (IllegalArgumentException notBase64) {
            return jti.length();
        }
    }
}
