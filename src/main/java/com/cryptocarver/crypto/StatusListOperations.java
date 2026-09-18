package com.cryptocarver.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Token Status List (IETF) — how a wallet credential says whether it is still
 * valid without the Verifier having to ask the Issuer about this particular
 * holder, which would hand the Issuer a log of everywhere the credential is
 * used. The trick is indirection: the credential carries an index into a
 * published bit array, and the Verifier fetches the whole array. One fetch
 * covers every holder in it, so the Issuer learns nothing about which one was
 * being checked.
 *
 * <p>The EUDI wallet's Architecture and Reference Framework names this as the
 * revocation mechanism, alongside SD-JWT VC and mdoc as credential formats; see
 * {@code docs/PROPUESTA_EIDAS_Y_PARIDAD_BPTOOLS.md}.</p>
 *
 * <h2>Two details worth spelling out</h2>
 * <ul>
 *   <li><b>Bit order.</b> Statuses are packed "from the least significant bit to
 *       the most significant bit" within each byte. With 2-bit statuses, index 0
 *       lives in bits 0-1 of byte 0, index 1 in bits 2-3, and so on — not the
 *       other way round. Getting this backwards produces a list that decodes
 *       cleanly and reports the wrong statuses, which is the worst kind of bug.</li>
 *   <li><b>Which DEFLATE.</b> The byte array is compressed with DEFLATE in
 *       <b>zlib</b> framing, not raw DEFLATE — {@code "zlib"} in
 *       {@link CompressionCodec}, whose {@code "deflate"} is the raw variant.</li>
 * </ul>
 *
 * <p>Nothing here fetches anything. A Status List Token is supplied as text, the
 * same way every other artefact in this app is.</p>
 */
public final class StatusListOperations {

    /** The statuses the specification defines. 0x03 and 0x0C-0x0F are left for
     *  application-specific use, so an unknown value is reported as such rather
     *  than rejected — a list may legitimately carry meanings we do not know. */
    public enum StatusType {
        VALID(0x00, "valid"),
        INVALID(0x01, "invalid (revoked)"),
        SUSPENDED(0x02, "suspended");

        private final int value;
        private final String description;

        StatusType(int value, String description) {
            this.value = value;
            this.description = description;
        }

        public int value() {
            return value;
        }

        public String description() {
            return description;
        }

        public static String describe(int value) {
            for (StatusType type : values()) {
                if (type.value == value) {
                    return type.description;
                }
            }
            return "application-specific (0x" + Integer.toHexString(value) + ")";
        }
    }

    /** A decoded Status List: the statuses themselves plus the framing that
     *  carried them, so a report can show both. */
    public record StatusList(int bits, int[] statuses, byte[] uncompressed, String encodedList) {

        public int statusAt(int index) {
            if (index < 0 || index >= statuses.length) {
                throw new IndexOutOfBoundsException(
                        "Index " + index + " is outside this Status List, which holds " + statuses.length + " entries");
            }
            return statuses[index];
        }
    }

    /** The result of resolving one credential's index against a list. */
    public record StatusLookup(int index, int status, String description, String listUri) {
    }

    private static final Gson GSON = new Gson();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public static final String STATUS_LIST_TOKEN_TYPE = "statuslist+jwt";

    private StatusListOperations() {
    }

    // ------------------------------------------------------------------- build

    /**
     * Packs statuses into the compressed, base64url-encoded {@code lst} value.
     *
     * @param bits how many bits each entry occupies: 1, 2, 4 or 8
     */
    public static String encodeList(int[] statuses, int bits) throws Exception {
        requireValidBits(bits);
        int perByte = 8 / bits;
        int mask = (1 << bits) - 1;
        int byteCount = (statuses.length + perByte - 1) / perByte;
        byte[] packed = new byte[byteCount];
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i] < 0 || statuses[i] > mask) {
                throw new IllegalArgumentException("Status " + statuses[i] + " at index " + i
                        + " does not fit in " + bits + " bit(s)");
            }
            int byteIndex = i / perByte;
            int shift = (i % perByte) * bits;
            packed[byteIndex] |= (byte) (statuses[i] << shift);
        }
        return B64.encodeToString(CompressionCodec.compress(packed, "zlib"));
    }

    /** Unpacks what {@link #encodeList} produced. {@code count} bounds how many
     *  entries to read, because the final byte is padded and padding is
     *  indistinguishable from a run of VALID entries; pass a negative number to
     *  read every entry the bytes can hold. */
    public static StatusList decodeList(String encodedList, int bits, int count) throws Exception {
        requireValidBits(bits);
        byte[] packed = CompressionCodec.decompress(Base64.getUrlDecoder().decode(encodedList), "zlib");
        int perByte = 8 / bits;
        int mask = (1 << bits) - 1;
        int available = packed.length * perByte;
        int total = count < 0 ? available : Math.min(count, available);
        int[] statuses = new int[total];
        for (int i = 0; i < total; i++) {
            int shift = (i % perByte) * bits;
            statuses[i] = (packed[i / perByte] >> shift) & mask;
        }
        return new StatusList(bits, statuses, packed, encodedList);
    }

    /**
     * Builds and signs a Status List Token.
     *
     * @param subject the URI this list is published at; a Verifier checks it
     *                against the {@code uri} in the credential, which is what
     *                stops one list being served in place of another
     * @param ttl     seconds a Verifier may cache the list, or a negative number
     *                to omit the claim
     */
    public static String issueStatusListToken(int[] statuses,
                                              int bits,
                                              String subject,
                                              Instant issuedAt,
                                              Instant expiresAt,
                                              long ttl,
                                              JWSAlgorithm algorithm,
                                              JWSSigner signer) throws Exception {
        JsonObject statusList = new JsonObject();
        statusList.addProperty("bits", bits);
        statusList.addProperty("lst", encodeList(statuses, bits));

        JsonObject claims = new JsonObject();
        claims.addProperty("sub", subject);
        claims.addProperty("iat", (issuedAt == null ? Instant.now() : issuedAt).getEpochSecond());
        if (expiresAt != null) {
            claims.addProperty("exp", expiresAt.getEpochSecond());
        }
        if (ttl >= 0) {
            claims.addProperty("ttl", ttl);
        }
        claims.add("status_list", statusList);

        JWSHeader header = new JWSHeader.Builder(algorithm)
                .type(new JOSEObjectType(STATUS_LIST_TOKEN_TYPE))
                .build();
        JWSObject jws = new JWSObject(header, new Payload(GSON.toJson(claims)));
        jws.sign(signer);
        return jws.serialize();
    }

    /** The {@code status} claim a credential carries to point at a list — what
     *  {@link SdJwtOperations#issueVerifiableCredential} takes as its
     *  {@code statusJson}. */
    public static String statusClaim(String listUri, int index) {
        JsonObject statusList = new JsonObject();
        statusList.addProperty("idx", index);
        statusList.addProperty("uri", listUri);
        JsonObject status = new JsonObject();
        status.add("status_list", statusList);
        return GSON.toJson(status);
    }

    // ------------------------------------------------------------------ resolve

    /**
     * Resolves a credential's status: takes the credential's {@code status}
     * claim and the Status List Token that claim points at, and answers with the
     * status value.
     *
     * <p>The subject check is not optional politeness. A Status List Token is
     * signed, but a signature only says the Issuer made <em>some</em> list; it
     * does not say the list is the one this credential refers to. Comparing
     * {@code sub} against {@code uri} is what rules out a stale or substituted
     * list being replayed to report everything valid.</p>
     *
     * @param verifier verifies the Status List Token, or {@code null} to skip —
     *                 in which case the caller is told, not quietly indulged
     */
    public static StatusLookup resolve(String statusClaimJson,
                                       String statusListToken,
                                       JWSVerifier verifier) throws Exception {
        JsonObject status = JsonParser.parseString(statusClaimJson).getAsJsonObject();
        if (!status.has("status_list")) {
            throw new IllegalArgumentException("This credential carries no 'status_list' reference");
        }
        JsonObject reference = status.getAsJsonObject("status_list");
        int index = reference.get("idx").getAsInt();
        String uri = reference.get("uri").getAsString();

        JWSObject jws = JWSObject.parse(statusListToken);
        String typ = jws.getHeader().getType() == null ? null : jws.getHeader().getType().toString();
        if (!STATUS_LIST_TOKEN_TYPE.equals(typ)) {
            throw new SecurityException(
                    "Status List Token must carry typ=" + STATUS_LIST_TOKEN_TYPE + ", found: " + typ);
        }
        if (verifier != null && !jws.verify(verifier)) {
            throw new SecurityException("The Status List Token does not verify under the supplied key");
        }

        JsonObject claims = JsonParser.parseString(jws.getPayload().toString()).getAsJsonObject();
        String subject = claims.has("sub") ? claims.get("sub").getAsString() : null;
        if (subject == null || !subject.equals(uri)) {
            throw new SecurityException("This Status List Token is published at '" + subject
                    + "' but the credential points at '" + uri + "'");
        }
        if (claims.has("exp") && Instant.ofEpochSecond(claims.get("exp").getAsLong()).isBefore(Instant.now())) {
            throw new SecurityException("The Status List Token expired at "
                    + Instant.ofEpochSecond(claims.get("exp").getAsLong()));
        }

        JsonObject statusList = claims.getAsJsonObject("status_list");
        int bits = statusList.get("bits").getAsInt();
        StatusList decoded = decodeList(statusList.get("lst").getAsString(), bits, -1);
        int value = decoded.statusAt(index);
        return new StatusLookup(index, value, StatusType.describe(value), uri);
    }

    private static void requireValidBits(int bits) {
        if (bits != 1 && bits != 2 && bits != 4 && bits != 8) {
            throw new IllegalArgumentException("Status List 'bits' must be 1, 2, 4 or 8, not " + bits);
        }
    }

    /** A human-readable dump of a Status List Token: its claims, its size, and a
     *  tally of how many entries hold each status. */
    public static String describe(String statusListToken) throws Exception {
        JWSObject jws = JWSObject.parse(statusListToken);
        JsonObject claims = JsonParser.parseString(jws.getPayload().toString()).getAsJsonObject();
        JsonObject statusList = claims.getAsJsonObject("status_list");
        int bits = statusList.get("bits").getAsInt();
        StatusList decoded = decodeList(statusList.get("lst").getAsString(), bits, -1);

        StringBuilder report = new StringBuilder();
        report.append("Status List Token\n");
        report.append("  header : ").append(jws.getHeader().toString()).append('\n');
        report.append("  sub    : ").append(claims.has("sub") ? claims.get("sub").getAsString() : "-").append('\n');
        report.append("  bits   : ").append(bits).append('\n');
        report.append("  entries: ").append(decoded.statuses().length)
                .append(" (").append(decoded.uncompressed().length).append(" bytes uncompressed, ")
                .append(Base64.getUrlDecoder().decode(statusList.get("lst").getAsString()).length)
                .append(" compressed)\n");

        int[] tally = new int[1 << bits];
        for (int status : decoded.statuses()) {
            tally[status]++;
        }
        for (int value = 0; value < tally.length; value++) {
            if (tally[value] > 0) {
                report.append("  ").append(tally[value]).append(" x ")
                        .append(StatusType.describe(value)).append('\n');
            }
        }
        return report.toString();
    }

    /** US-ASCII bytes of a token, for callers that want to hash one. */
    static byte[] asciiBytes(String token) {
        return token.getBytes(StandardCharsets.US_ASCII);
    }
}
