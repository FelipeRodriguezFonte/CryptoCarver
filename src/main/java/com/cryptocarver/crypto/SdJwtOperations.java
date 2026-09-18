package com.cryptocarver.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SD-JWT — Selective Disclosure for JSON Web Tokens, <b>RFC 9901</b> (published
 * 19 November 2025), plus the SD-JWT VC profile on top of it. This is the
 * credential format of the European Digital Identity Wallet, so it is the piece
 * the December 2026 deadline actually rests on; see
 * {@code docs/PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md}.
 *
 * <p>The mechanism is "salted hashes". For every claim the Issuer wants to make
 * selectively disclosable it does <em>not</em> put the cleartext in the signed
 * payload; it puts a digest there instead, and hands the Holder a
 * <em>Disclosure</em> — a base64url-encoded JSON array carrying the salt, the
 * name and the value. The Holder forwards only the Disclosures it chooses to
 * reveal, and the Verifier re-computes their digests and checks they were in the
 * signed payload all along. The signature never changes, which is the whole
 * point: the Issuer signs once and the Holder narrows afterwards.</p>
 *
 * <h2>Three details that are easy to get wrong, and are not guesses here</h2>
 * <ul>
 *   <li><b>What gets hashed.</b> RFC 9901 §4.2.3: "The digest MUST be computed
 *       over the US-ASCII bytes of the base64url-encoded value that is the
 *       Disclosure" — the base64url <em>string</em>, not the JSON bytes it
 *       decodes to. A verifier therefore never re-serializes anything, which is
 *       why the Issuer's choice of JSON whitespace is irrelevant to interop.
 *       {@link #digestOf} hashes the received string exactly as received.</li>
 *   <li><b>The trailing tilde.</b> §4: without a Key Binding JWT "the last
 *       element MUST be an empty string and the last separating tilde character
 *       MUST NOT be omitted". So {@code <JWT>~<D1>~<D2>~} is a presentation
 *       without key binding and {@code <JWT>~<D1>~<D2>~<KB>} is one with it.
 *       The trailing tilde is what tells the two apart; dropping it is the
 *       classic bug.</li>
 *   <li><b>What {@code sd_hash} covers.</b> §4.3.1: the US-ASCII bytes of the
 *       Issuer-signed JWT, a tilde, and the presented Disclosures "each followed
 *       by a tilde character" — that is, everything up to and including the last
 *       tilde, and nothing of the KB-JWT itself. {@link #keyBindingInput} builds
 *       exactly that string.</li>
 * </ul>
 *
 * <h2>Scope of this implementation</h2>
 * <p>Issuance, presentation and <b>offline</b> verification of the artefacts.
 * There is no OpenID4VCI issuance protocol and no OpenID4VP presentation
 * protocol here: this class manipulates tokens, it does not talk to anyone over
 * a network. That matches the rest of the app, whose local API listens only on
 * {@code 127.0.0.1} and accepts no keys.</p>
 *
 * <p>Selective disclosure is supported for object properties at any depth via
 * dotted paths ({@code address.locality}) and for whole arrays via a
 * {@code []} suffix ({@code nationalities[]}), which makes every element of that
 * array individually disclosable. Disclosing <em>some</em> elements of an array
 * while leaving others in the clear is not exposed as a path syntax; it is
 * expressible at presentation time, because each element already gets its own
 * Disclosure.</p>
 *
 * <p><b>Security note.</b> This is laboratory material. Salts, Disclosures and
 * key-binding private keys are secrets: a Disclosure is the cleartext of the
 * claim it hides, so a full presentation reveals everything it carries.</p>
 */
public final class SdJwtOperations {

    /** RFC 9901 §4.1.1 — the {@code _sd_alg} claim names an entry of the IANA
     *  "Named Information Hash Algorithm" registry, which is why these are
     *  {@code sha-256} and not the JCA spelling {@code SHA-256}. SHA-256 is the
     *  default when the claim is absent and the one implementations MUST support. */
    public enum HashAlgorithm {
        SHA_256("sha-256", "SHA-256"),
        SHA_384("sha-384", "SHA-384"),
        SHA_512("sha-512", "SHA-512");

        private final String registryName;
        private final String jcaName;

        HashAlgorithm(String registryName, String jcaName) {
            this.registryName = registryName;
            this.jcaName = jcaName;
        }

        public String registryName() {
            return registryName;
        }

        public String jcaName() {
            return jcaName;
        }

        public static HashAlgorithm fromRegistryName(String name) {
            for (HashAlgorithm candidate : values()) {
                if (candidate.registryName.equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("Unsupported _sd_alg value: " + name);
        }
    }

    /**
     * One Disclosure. {@code claimName} is {@code null} for an array element,
     * which RFC 9901 §4.2.2 encodes as a two-element array {@code [salt, value]}
     * rather than the three-element {@code [salt, name, value]} of §4.2.1.
     *
     * @param encoded the base64url string as it travels, and as it is hashed
     * @param digest  base64url of the hash of {@code encoded}'s US-ASCII bytes
     */
    public record Disclosure(String salt, String claimName, JsonElement value, String encoded, String digest) {

        public boolean isArrayElement() {
            return claimName == null;
        }

        /** What a UI shows in a picker: the claim name, or the value for array elements. */
        public String label() {
            return claimName != null ? claimName : String.valueOf(value);
        }
    }

    /** An issued SD-JWT: the signed JWT, every Disclosure produced for it, and
     *  the full serialization with all Disclosures attached (what an Issuer
     *  hands the Holder — not what the Holder should forward to a Verifier). */
    public record IssuedSdJwt(String issuerJwt,
                              List<Disclosure> disclosures,
                              String serialized,
                              HashAlgorithm hashAlgorithm) {
    }

    /** Everything needed to add a Key Binding JWT at presentation time. RFC 9901
     *  §4.3 makes {@code iat}, {@code aud}, {@code nonce} and {@code sd_hash}
     *  required; {@code sd_hash} is computed here, the rest comes from here.
     *  {@code extraClaims} carries profile additions — TS12's {@code jti},
     *  {@code amr} and {@code transaction_data_hashes} ride in through it. */
    public record KeyBinding(String audience,
                             String nonce,
                             JWSAlgorithm algorithm,
                             JWSSigner signer,
                             Instant issuedAt,
                             JsonObject extraClaims) {

        public KeyBinding(String audience, String nonce, JWSAlgorithm algorithm, JWSSigner signer) {
            this(audience, nonce, algorithm, signer, Instant.now(), new JsonObject());
        }
    }

    /** The outcome of verifying a presentation: the payload with every disclosed
     *  claim put back where it belongs and the {@code _sd} machinery removed. */
    public record VerifiedSdJwt(JsonObject claims,
                                List<Disclosure> presentedDisclosures,
                                boolean keyBindingPresent,
                                JsonObject keyBindingClaims,
                                List<String> notes) {

        public String claimsJson() {
            return GSON.toJson(claims);
        }
    }

    /** A structural read of a presentation that needs no keys at all — what the
     *  inspector panel shows for a token pasted from somewhere else. */
    public record ParsedSdJwt(JsonObject header,
                              JsonObject payload,
                              List<Disclosure> disclosures,
                              String keyBindingJwt,
                              JsonObject keyBindingHeader,
                              JsonObject keyBindingClaims) {

        public boolean hasKeyBinding() {
            return keyBindingJwt != null;
        }
    }

    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    /** RFC 9901 §4.2.1: the claim name of a Disclosure "MUST NOT be _sd or ...". */
    private static final String SD_CLAIM = "_sd";
    private static final String SD_ALG_CLAIM = "_sd_alg";
    private static final String ARRAY_ELEMENT_KEY = "...";

    /** SD-JWT VC. The {@code typ} was {@code vc+sd-jwt} from July 2023 until
     *  November 2024, when it changed to {@code dc+sd-jwt} to stop colliding with
     *  the media type W3C registered for its own Verifiable Credentials data
     *  model. The draft recommends accepting both "for a reasonable transitional
     *  period", so {@link #verify} accepts either and this is what it issues. */
    public static final String SD_JWT_VC_TYPE = "dc+sd-jwt";
    public static final String SD_JWT_VC_LEGACY_TYPE = "vc+sd-jwt";
    public static final String KEY_BINDING_TYPE = "kb+jwt";

    private SdJwtOperations() {
    }

    // ------------------------------------------------------------------ issue

    /**
     * Issues an SD-JWT.
     *
     * @param claimsJson        the payload the Issuer wants to attest, as JSON
     * @param disclosablePaths  dotted paths to make selectively disclosable;
     *                          a {@code []} suffix turns every element of that
     *                          array into its own Disclosure
     * @param decoyCount        decoy digests added to the top-level {@code _sd}
     *                          (RFC 9901 §4.2.5) so the count of digests stops
     *                          revealing how many claims were withheld
     * @param headersJson       extra JOSE header parameters, or {@code null}
     */
    public static IssuedSdJwt issue(String claimsJson,
                                    List<String> disclosablePaths,
                                    int decoyCount,
                                    HashAlgorithm hashAlgorithm,
                                    JWSAlgorithm jwsAlgorithm,
                                    JWSSigner signer,
                                    String headersJson) throws Exception {
        if (decoyCount < 0) {
            throw new IllegalArgumentException("Decoy count cannot be negative");
        }
        JsonObject payload = JsonParser.parseString(claimsJson).getAsJsonObject().deepCopy();
        List<Disclosure> disclosures = new ArrayList<>();

        for (String path : disclosablePaths == null ? List.<String>of() : disclosablePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            if (path.endsWith("[]")) {
                disclosures.addAll(makeArrayDisclosable(payload, path.substring(0, path.length() - 2), hashAlgorithm));
            } else {
                disclosures.add(makePropertyDisclosable(payload, path, hashAlgorithm));
            }
        }

        for (int i = 0; i < decoyCount; i++) {
            addDigest(payload, decoyDigest(hashAlgorithm));
        }
        shuffleSdArrays(payload);

        // Always written out, even for the default sha-256. The RFC lets it be
        // omitted, but a laboratory artefact that states its own hash algorithm
        // is one less thing to infer when a presentation fails to verify.
        payload.addProperty(SD_ALG_CLAIM, hashAlgorithm.registryName());

        String issuerJwt = sign(payload, jwsAlgorithm, signer, headersJson);
        String serialized = serialize(issuerJwt, disclosures, null);
        return new IssuedSdJwt(issuerJwt, List.copyOf(disclosures), serialized, hashAlgorithm);
    }

    /**
     * Issues an SD-JWT VC: the same thing with the profile's header and claims
     * filled in. {@code vct} is the credential type identifier and {@code cnf}
     * carries the Holder's public key, which is what a Key Binding JWT is later
     * verified against — without {@code cnf} a presentation cannot be bound to
     * anyone, so key binding is unavailable for that credential by construction.
     *
     * @param confirmationJwkJson the Holder's public key as a JWK, or {@code null}
     * @param statusJson          a {@code status} claim (Token Status List
     *                            reference, see {@link StatusListOperations}), or
     *                            {@code null}
     */
    public static IssuedSdJwt issueVerifiableCredential(String claimsJson,
                                                        String vct,
                                                        String issuer,
                                                        String confirmationJwkJson,
                                                        String statusJson,
                                                        List<String> disclosablePaths,
                                                        int decoyCount,
                                                        HashAlgorithm hashAlgorithm,
                                                        JWSAlgorithm jwsAlgorithm,
                                                        JWSSigner signer) throws Exception {
        JsonObject claims = JsonParser.parseString(claimsJson).getAsJsonObject().deepCopy();
        if (vct == null || vct.isBlank()) {
            throw new IllegalArgumentException("SD-JWT VC requires a vct (verifiable credential type)");
        }
        claims.addProperty("vct", vct);
        if (issuer != null && !issuer.isBlank()) {
            claims.addProperty("iss", issuer);
        }
        if (confirmationJwkJson != null && !confirmationJwkJson.isBlank()) {
            JsonObject cnf = new JsonObject();
            cnf.add("jwk", JsonParser.parseString(confirmationJwkJson));
            claims.add("cnf", cnf);
        }
        if (statusJson != null && !statusJson.isBlank()) {
            claims.add("status", JsonParser.parseString(statusJson));
        }
        JsonObject headers = new JsonObject();
        headers.addProperty("typ", SD_JWT_VC_TYPE);
        return issue(GSON.toJson(claims), disclosablePaths, decoyCount, hashAlgorithm,
                jwsAlgorithm, signer, GSON.toJson(headers));
    }

    // ------------------------------------------------------------------- present

    /**
     * Narrows an issued SD-JWT to the Disclosures named in {@code revealDigests}
     * and, optionally, binds the result to a Verifier and a nonce.
     *
     * <p>Selection is by digest rather than by claim name on purpose: two claims
     * can share a name at different depths, and the digest is the thing the
     * signed payload actually refers to.</p>
     */
    public static String present(IssuedSdJwt issued,
                                 Collection<String> revealDigests,
                                 KeyBinding keyBinding) throws Exception {
        Set<String> wanted = revealDigests == null ? Set.of() : new LinkedHashSet<>(revealDigests);
        List<Disclosure> selected = new ArrayList<>();
        for (Disclosure disclosure : issued.disclosures()) {
            if (wanted.contains(disclosure.digest())) {
                selected.add(disclosure);
            }
        }
        Set<String> found = new LinkedHashSet<>();
        selected.forEach(d -> found.add(d.digest()));
        for (String digest : wanted) {
            if (!found.contains(digest)) {
                throw new IllegalArgumentException("No Disclosure in this SD-JWT has digest " + digest);
            }
        }
        return serialize(issued.issuerJwt(), selected, buildKeyBindingJwt(
                issued.issuerJwt(), selected, issued.hashAlgorithm(), keyBinding));
    }

    // -------------------------------------------------------------------- parse

    /**
     * Reads a presentation without verifying anything. Useful on its own — this
     * is what lets the inspector show what a token from elsewhere contains —
     * and it is the first step of {@link #verify}.
     */
    public static ParsedSdJwt parse(String serialized) throws Exception {
        if (serialized == null || serialized.isBlank()) {
            throw new IllegalArgumentException("Empty SD-JWT");
        }
        // -1 keeps the trailing empty element that §4 requires when there is no
        // KB-JWT; the default split() would silently drop it and make the two
        // serializations indistinguishable.
        String[] parts = serialized.trim().split("~", -1);
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Not an SD-JWT: no tilde found. Even with no Disclosures the serialization ends in '~'");
        }
        String issuerJwt = parts[0];
        String last = parts[parts.length - 1];
        boolean hasKeyBinding = !last.isEmpty();

        JWSObject jws = JWSObject.parse(issuerJwt);
        JsonObject header = JsonParser.parseString(jws.getHeader().toString()).getAsJsonObject();
        JsonObject payload = JsonParser.parseString(jws.getPayload().toString()).getAsJsonObject();
        HashAlgorithm hashAlgorithm = hashAlgorithmOf(payload);

        List<Disclosure> disclosures = new ArrayList<>();
        int end = parts.length - 1; // the trailing element is the KB-JWT or the empty string
        for (int i = 1; i < end; i++) {
            if (!parts[i].isEmpty()) {
                disclosures.add(decodeDisclosure(parts[i], hashAlgorithm));
            }
        }

        JsonObject kbHeader = null;
        JsonObject kbClaims = null;
        if (hasKeyBinding) {
            JWSObject kb = JWSObject.parse(last);
            kbHeader = JsonParser.parseString(kb.getHeader().toString()).getAsJsonObject();
            kbClaims = JsonParser.parseString(kb.getPayload().toString()).getAsJsonObject();
        }
        return new ParsedSdJwt(header, payload, disclosures,
                hasKeyBinding ? last : null, kbHeader, kbClaims);
    }

    // ------------------------------------------------------------------- verify

    /**
     * Verifies a presentation and rebuilds the payload from it.
     *
     * <p>Beyond the signature this enforces the checks of RFC 9901 §7 that are
     * easy to skip and are exactly where forgeries hide: every digest appears at
     * most once, every Disclosure handed over is actually referenced by a digest
     * (an unreferenced one MUST be rejected — it is a claim nobody signed), a
     * disclosed claim never overwrites one already present in the payload, and
     * no Disclosure is named {@code _sd} or {@code ...}.</p>
     *
     * @param expectedAudience checked against the KB-JWT's {@code aud}, or {@code null} to skip
     * @param expectedNonce    checked against the KB-JWT's {@code nonce}, or {@code null} to skip
     * @param holderVerifier   verifies the KB-JWT; {@code null} means "do not
     *                         verify key binding cryptographically", which is
     *                         reported in the notes rather than passed over
     */
    public static VerifiedSdJwt verify(String serialized,
                                       JWSVerifier issuerVerifier,
                                       JWSVerifier holderVerifier,
                                       String expectedAudience,
                                       String expectedNonce) throws Exception {
        ParsedSdJwt parsed = parse(serialized);
        List<String> notes = new ArrayList<>();

        JWSObject issuerJws = JWSObject.parse(serialized.trim().split("~", -1)[0]);
        if (issuerVerifier == null) {
            notes.add("Issuer signature was not verified: no verification key was supplied");
        } else if (!issuerJws.verify(issuerVerifier)) {
            throw new SecurityException("The Issuer-signed JWT does not verify under the supplied key");
        }

        HashAlgorithm hashAlgorithm = hashAlgorithmOf(parsed.payload());
        Map<String, Disclosure> byDigest = new LinkedHashMap<>();
        for (Disclosure disclosure : parsed.disclosures()) {
            if (SD_CLAIM.equals(disclosure.claimName()) || ARRAY_ELEMENT_KEY.equals(disclosure.claimName())) {
                throw new SecurityException(
                        "A Disclosure claims the reserved name '" + disclosure.claimName() + "'");
            }
            if (byDigest.put(disclosure.digest(), disclosure) != null) {
                throw new SecurityException("The same Disclosure was presented twice: " + disclosure.digest());
            }
        }

        Set<String> used = new LinkedHashSet<>();
        JsonObject claims = parsed.payload().deepCopy();
        claims.remove(SD_ALG_CLAIM);
        resolve(claims, byDigest, used);

        for (Map.Entry<String, Disclosure> entry : byDigest.entrySet()) {
            if (!used.contains(entry.getKey())) {
                // RFC 9901 §7.3: a Disclosure whose digest is nowhere in the
                // payload was never signed by the Issuer. Accepting it would let
                // a Holder append any claim it liked.
                throw new SecurityException("Presented Disclosure is not referenced by any digest: "
                        + entry.getValue().label());
            }
        }

        JsonObject kbClaims = parsed.keyBindingClaims();
        if (parsed.hasKeyBinding()) {
            verifyKeyBinding(parsed, serialized, hashAlgorithm, holderVerifier,
                    expectedAudience, expectedNonce, notes);
        } else if (expectedAudience != null || expectedNonce != null) {
            throw new SecurityException(
                    "An audience or nonce was expected but the presentation carries no Key Binding JWT");
        }

        return new VerifiedSdJwt(claims, parsed.disclosures(), parsed.hasKeyBinding(), kbClaims, notes);
    }

    private static void verifyKeyBinding(ParsedSdJwt parsed,
                                         String serialized,
                                         HashAlgorithm hashAlgorithm,
                                         JWSVerifier holderVerifier,
                                         String expectedAudience,
                                         String expectedNonce,
                                         List<String> notes) throws Exception {
        JsonObject header = parsed.keyBindingHeader();
        String typ = header.has("typ") ? header.get("typ").getAsString() : null;
        if (!KEY_BINDING_TYPE.equals(typ)) {
            throw new SecurityException("Key Binding JWT must carry typ=" + KEY_BINDING_TYPE + ", found: " + typ);
        }
        JsonObject claims = parsed.keyBindingClaims();
        for (String required : List.of("iat", "aud", "nonce", "sd_hash")) {
            if (!claims.has(required)) {
                throw new SecurityException("Key Binding JWT is missing the required claim '" + required + "'");
            }
        }

        String expectedSdHash = base64Url(hash(
                keyBindingInput(serialized).getBytes(StandardCharsets.US_ASCII), hashAlgorithm));
        String presentedSdHash = claims.get("sd_hash").getAsString();
        if (!MessageDigest.isEqual(expectedSdHash.getBytes(StandardCharsets.US_ASCII),
                presentedSdHash.getBytes(StandardCharsets.US_ASCII))) {
            // This is the check that ties the KB-JWT to *this* set of
            // Disclosures. Without it a KB-JWT could be lifted onto a different
            // presentation of the same credential.
            throw new SecurityException("Key Binding sd_hash does not cover this presentation. Expected "
                    + expectedSdHash + ", found " + presentedSdHash);
        }
        if (expectedAudience != null && !expectedAudience.equals(claims.get("aud").getAsString())) {
            throw new SecurityException("Key Binding aud is " + claims.get("aud").getAsString()
                    + ", expected " + expectedAudience);
        }
        if (expectedNonce != null && !expectedNonce.equals(claims.get("nonce").getAsString())) {
            throw new SecurityException("Key Binding nonce does not match the one the Verifier issued");
        }
        if (holderVerifier == null) {
            notes.add("Key Binding JWT signature was not verified: no Holder key was supplied");
        } else if (!JWSObject.parse(parsed.keyBindingJwt()).verify(holderVerifier)) {
            throw new SecurityException("The Key Binding JWT does not verify under the supplied Holder key");
        }
    }

    // ------------------------------------------------------- disclosure plumbing

    private static Disclosure makePropertyDisclosable(JsonObject root, String path, HashAlgorithm hashAlgorithm) {
        int lastDot = path.lastIndexOf('.');
        String parentPath = lastDot < 0 ? "" : path.substring(0, lastDot);
        String claimName = lastDot < 0 ? path : path.substring(lastDot + 1);
        JsonObject parent = resolveObject(root, parentPath);
        if (!parent.has(claimName)) {
            throw new IllegalArgumentException("No claim at path '" + path + "' to make disclosable");
        }
        if (SD_CLAIM.equals(claimName) || ARRAY_ELEMENT_KEY.equals(claimName)) {
            throw new IllegalArgumentException("'" + claimName + "' is reserved and cannot be a Disclosure name");
        }
        JsonElement value = parent.remove(claimName);
        Disclosure disclosure = buildDisclosure(claimName, value, hashAlgorithm);
        addDigest(parent, disclosure.digest());
        return disclosure;
    }

    private static List<Disclosure> makeArrayDisclosable(JsonObject root, String path, HashAlgorithm hashAlgorithm) {
        int lastDot = path.lastIndexOf('.');
        String parentPath = lastDot < 0 ? "" : path.substring(0, lastDot);
        String name = lastDot < 0 ? path : path.substring(lastDot + 1);
        JsonObject parent = resolveObject(root, parentPath);
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("No array at path '" + path + "'");
        }
        JsonArray array = element.getAsJsonArray();
        JsonArray replacement = new JsonArray();
        List<Disclosure> produced = new ArrayList<>();
        for (JsonElement item : array) {
            Disclosure disclosure = buildDisclosure(null, item, hashAlgorithm);
            produced.add(disclosure);
            // §4.2.4.2: a concealed array element becomes an object whose single
            // member is named "..." and holds the digest.
            JsonObject placeholder = new JsonObject();
            placeholder.addProperty(ARRAY_ELEMENT_KEY, disclosure.digest());
            replacement.add(placeholder);
        }
        parent.add(name, replacement);
        return produced;
    }

    private static Disclosure buildDisclosure(String claimName, JsonElement value, HashAlgorithm hashAlgorithm) {
        return buildDisclosure(newSalt(), claimName, value, hashAlgorithm);
    }

    private static Disclosure buildDisclosure(String salt, String claimName, JsonElement value,
                                              HashAlgorithm hashAlgorithm) {
        String encoded = encodeDisclosure(salt, claimName, value);
        return new Disclosure(salt, claimName, value, encoded, digestOf(encoded, hashAlgorithm));
    }

    /**
     * Encodes one Disclosure by hand — useful on its own, and the seam the RFC
     * vectors are checked through.
     *
     * <p><b>The whitespace is deliberate.</b> RFC 9901 hashes the base64url
     * string that travels, so the Issuer's JSON formatting cannot affect interop
     * and any spacing would be correct. This writes {@code ", "} between
     * elements anyway, because that is what the RFC's own worked examples and
     * the reference implementations emit: with the same salt, this produces the
     * same Disclosure string as they do, byte for byte, and a Disclosure from
     * here can be laid beside one from there. Same reasoning as the byte-4
     * choice in the ICSF export panel — reproduce what the other side actually
     * writes, so comparison is possible at all.</p>
     *
     * @param claimName {@code null} for an array element, which makes it the
     *                  two-element form of §4.2.2
     */
    public static String encodeDisclosure(String salt, String claimName, JsonElement value) {
        StringBuilder json = new StringBuilder("[");
        json.append(GSON.toJson(new JsonPrimitive(salt)));
        if (claimName != null) {
            json.append(", ").append(GSON.toJson(new JsonPrimitive(claimName)));
        }
        json.append(", ").append(GSON.toJson(value)).append(']');
        return B64.encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Disclosure decodeDisclosure(String encoded, HashAlgorithm hashAlgorithm) {
        String json = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        if (array.size() == 2) {
            return new Disclosure(array.get(0).getAsString(), null, array.get(1),
                    encoded, digestOf(encoded, hashAlgorithm));
        }
        if (array.size() == 3) {
            return new Disclosure(array.get(0).getAsString(), array.get(1).getAsString(), array.get(2),
                    encoded, digestOf(encoded, hashAlgorithm));
        }
        throw new IllegalArgumentException(
                "A Disclosure must be a 2- or 3-element JSON array, this one has " + array.size());
    }

    /** RFC 9901 §4.2.3 — hash the US-ASCII bytes of the base64url string itself. */
    public static String digestOf(String encodedDisclosure, HashAlgorithm hashAlgorithm) {
        return base64Url(hash(encodedDisclosure.getBytes(StandardCharsets.US_ASCII), hashAlgorithm));
    }

    /**
     * Walks the payload replacing digests with the claims they stand for.
     * Recurses into nested objects and arrays because {@code _sd} may appear at
     * any level, and a disclosed value may itself contain more {@code _sd}.
     */
    private static void resolve(JsonObject node,
                                Map<String, Disclosure> byDigest,
                                Set<String> used) {
        JsonElement sd = node.remove(SD_CLAIM);
        if (sd != null) {
            if (!sd.isJsonArray()) {
                throw new SecurityException("'_sd' must be an array of digests");
            }
            Set<String> seen = new LinkedHashSet<>();
            for (JsonElement digestElement : sd.getAsJsonArray()) {
                String digest = digestElement.getAsString();
                if (!seen.add(digest)) {
                    throw new SecurityException("The same digest appears twice in one '_sd' array: " + digest);
                }
                Disclosure disclosure = byDigest.get(digest);
                if (disclosure == null) {
                    continue; // withheld, or a decoy — both are normal and silent
                }
                if (disclosure.isArrayElement()) {
                    throw new SecurityException(
                            "An array-element Disclosure was referenced from an '_sd' array: " + digest);
                }
                if (node.has(disclosure.claimName())) {
                    throw new SecurityException("Disclosed claim '" + disclosure.claimName()
                            + "' would overwrite a claim already present in the payload");
                }
                node.add(disclosure.claimName(), disclosure.value());
                used.add(digest);
            }
        }
        for (String name : new ArrayList<>(node.keySet())) {
            JsonElement child = node.get(name);
            if (child.isJsonObject()) {
                resolve(child.getAsJsonObject(), byDigest, used);
            } else if (child.isJsonArray()) {
                node.add(name, resolveArray(child.getAsJsonArray(), byDigest, used));
            }
        }
    }

    private static JsonArray resolveArray(JsonArray array,
                                          Map<String, Disclosure> byDigest,
                                          Set<String> used) {
        JsonArray result = new JsonArray();
        for (JsonElement item : array) {
            if (item.isJsonObject() && item.getAsJsonObject().size() == 1
                    && item.getAsJsonObject().has(ARRAY_ELEMENT_KEY)) {
                String digest = item.getAsJsonObject().get(ARRAY_ELEMENT_KEY).getAsString();
                Disclosure disclosure = byDigest.get(digest);
                if (disclosure == null) {
                    continue; // withheld element: it vanishes rather than leaving a hole
                }
                if (!disclosure.isArrayElement()) {
                    throw new SecurityException(
                            "A named-claim Disclosure was referenced from an array element: " + digest);
                }
                used.add(digest);
                JsonElement value = disclosure.value();
                if (value.isJsonObject()) {
                    JsonObject copy = value.deepCopy().getAsJsonObject();
                    resolve(copy, byDigest, used);
                    result.add(copy);
                } else {
                    result.add(value);
                }
            } else if (item.isJsonObject()) {
                JsonObject copy = item.deepCopy().getAsJsonObject();
                resolve(copy, byDigest, used);
                result.add(copy);
            } else if (item.isJsonArray()) {
                result.add(resolveArray(item.getAsJsonArray(), byDigest, used));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    // -------------------------------------------------------------- serializing

    /** RFC 9901 §4 — Disclosures each followed by a tilde, then the KB-JWT or
     *  nothing at all, which leaves the mandatory trailing tilde in place. */
    private static String serialize(String issuerJwt, List<Disclosure> disclosures, String keyBindingJwt) {
        StringBuilder builder = new StringBuilder(issuerJwt);
        builder.append('~');
        for (Disclosure disclosure : disclosures) {
            builder.append(disclosure.encoded()).append('~');
        }
        if (keyBindingJwt != null) {
            builder.append(keyBindingJwt);
        }
        return builder.toString();
    }

    /** §4.3.1 — everything up to and including the final tilde; the KB-JWT is
     *  excluded because it is the thing being computed. */
    static String keyBindingInput(String serialized) {
        int lastTilde = serialized.lastIndexOf('~');
        return serialized.substring(0, lastTilde + 1);
    }

    private static String buildKeyBindingJwt(String issuerJwt,
                                             List<Disclosure> disclosures,
                                             HashAlgorithm hashAlgorithm,
                                             KeyBinding binding) throws Exception {
        if (binding == null) {
            return null;
        }
        String withoutKeyBinding = serialize(issuerJwt, disclosures, null);
        String sdHash = base64Url(hash(
                withoutKeyBinding.getBytes(StandardCharsets.US_ASCII), hashAlgorithm));

        JsonObject claims = binding.extraClaims() == null
                ? new JsonObject()
                : binding.extraClaims().deepCopy();
        Instant issuedAt = binding.issuedAt() == null ? Instant.now() : binding.issuedAt();
        claims.addProperty("iat", issuedAt.getEpochSecond());
        claims.addProperty("aud", binding.audience());
        claims.addProperty("nonce", binding.nonce());
        claims.addProperty("sd_hash", sdHash);

        JWSHeader header = new JWSHeader.Builder(binding.algorithm())
                .type(new JOSEObjectType(KEY_BINDING_TYPE))
                .build();
        JWSObject jws = new JWSObject(header, new Payload(GSON.toJson(claims)));
        jws.sign(binding.signer());
        return jws.serialize();
    }

    private static String sign(JsonObject payload,
                               JWSAlgorithm algorithm,
                               JWSSigner signer,
                               String headersJson) throws Exception {
        JWSHeader.Builder builder = new JWSHeader.Builder(algorithm);
        if (headersJson != null && !headersJson.isBlank()) {
            JsonObject headers = JsonParser.parseString(headersJson).getAsJsonObject();
            if (headers.has("typ")) {
                builder.type(new JOSEObjectType(headers.get("typ").getAsString()));
                headers.remove("typ");
            }
            if (headers.has("kid")) {
                builder.keyID(headers.get("kid").getAsString());
                headers.remove("kid");
            }
            Map<String, Object> custom = new LinkedHashMap<>();
            for (String name : headers.keySet()) {
                custom.put(name, GSON.fromJson(headers.get(name), Object.class));
            }
            if (!custom.isEmpty()) {
                builder.customParams(custom);
            }
        }
        JWSObject jws = new JWSObject(builder.build(), new Payload(GSON.toJson(payload)));
        jws.sign(signer);
        return jws.serialize();
    }

    // -------------------------------------------------------------------- utils

    private static JsonObject resolveObject(JsonObject root, String path) {
        if (path.isEmpty()) {
            return root;
        }
        JsonObject current = root;
        for (String segment : path.split("\\.")) {
            JsonElement next = current.get(segment);
            if (next == null || !next.isJsonObject()) {
                throw new IllegalArgumentException("No object at path segment '" + segment + "'");
            }
            current = next.getAsJsonObject();
        }
        return current;
    }

    private static void addDigest(JsonObject node, String digest) {
        JsonArray sd = node.has(SD_CLAIM) ? node.getAsJsonArray(SD_CLAIM) : new JsonArray();
        sd.add(new JsonPrimitive(digest));
        node.add(SD_CLAIM, sd);
    }

    /** §4.2.4.1: "The Issuer MUST hide the original order of the claims" — the
     *  digests are added in declaration order above, so they are shuffled here
     *  before signing. Otherwise the position of a digest would leak which claim
     *  it stands for whenever the Verifier knows the credential's shape. */
    private static void shuffleSdArrays(JsonElement node) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has(SD_CLAIM)) {
                JsonArray sd = object.getAsJsonArray(SD_CLAIM);
                List<JsonElement> items = new ArrayList<>();
                sd.forEach(items::add);
                Collections.shuffle(items, RANDOM);
                JsonArray shuffled = new JsonArray();
                items.forEach(shuffled::add);
                object.add(SD_CLAIM, shuffled);
            }
            for (String name : new ArrayList<>(object.keySet())) {
                if (!SD_CLAIM.equals(name)) {
                    shuffleSdArrays(object.get(name));
                }
            }
        } else if (node.isJsonArray()) {
            node.getAsJsonArray().forEach(SdJwtOperations::shuffleSdArrays);
        }
    }

    private static HashAlgorithm hashAlgorithmOf(JsonObject payload) {
        if (!payload.has(SD_ALG_CLAIM)) {
            return HashAlgorithm.SHA_256;
        }
        return HashAlgorithm.fromRegistryName(payload.get(SD_ALG_CLAIM).getAsString());
    }

    /** 128 bits, the size RFC 9901 §4.2.1 recommends. The salt is what stops a
     *  Verifier from brute-forcing a withheld claim whose value space is small —
     *  a birth date or a postcode would otherwise fall in seconds. */
    private static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return B64.encodeToString(salt);
    }

    private static String decoyDigest(HashAlgorithm hashAlgorithm) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return base64Url(hash(B64.encodeToString(random).getBytes(StandardCharsets.US_ASCII), hashAlgorithm));
    }

    private static byte[] hash(byte[] input, HashAlgorithm hashAlgorithm) {
        try {
            return MessageDigest.getInstance(hashAlgorithm.jcaName()).digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash algorithm unavailable: " + hashAlgorithm.jcaName(), e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    /** Renders a presentation for a human: one line per component, Disclosures
     *  decoded. This is the report the UI shows, and it is deliberately verbose
     *  about what is concealed versus revealed. */
    public static String describe(String serialized, Locale locale) throws Exception {
        ParsedSdJwt parsed = parse(serialized);
        StringBuilder report = new StringBuilder();
        boolean spanish = locale != null && "es".equals(locale.getLanguage());
        report.append(spanish ? "JWT firmado por el emisor" : "Issuer-signed JWT").append('\n');
        report.append("  header : ").append(GSON.toJson(parsed.header())).append('\n');
        report.append("  payload: ").append(GSON.toJson(parsed.payload())).append('\n');
        report.append('\n').append(spanish ? "Disclosures presentadas: " : "Disclosures presented: ")
                .append(parsed.disclosures().size()).append('\n');
        for (Disclosure disclosure : parsed.disclosures()) {
            report.append("  - ").append(disclosure.isArrayElement()
                            ? (spanish ? "[elemento de array]" : "[array element]")
                            : disclosure.claimName())
                    .append(" = ").append(GSON.toJson(disclosure.value()))
                    .append("\n    salt   : ").append(disclosure.salt())
                    .append("\n    digest : ").append(disclosure.digest()).append('\n');
        }
        report.append('\n').append(spanish ? "Key Binding: " : "Key Binding: ")
                .append(parsed.hasKeyBinding()
                        ? GSON.toJson(parsed.keyBindingClaims())
                        : (spanish ? "ausente" : "absent"))
                .append('\n');
        return report.toString();
    }
}
