package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Serializes and deserializes {@link CryptoEnvelope}, following the same defensive posture as
 * {@link FileCipherRecipeCodec}: an explicit allow-list of root keys, a recursive scan that
 * rejects any field that looks like it might carry secret key material, and strict validation of
 * every field on the way in — never trust a pasted/loaded envelope blindly.
 *
 * <p>Deserialization deliberately does not use {@code Gson#fromJson(String, Class)} directly: that
 * would construct {@link CryptoEnvelope} via reflection, bypassing its builder (and therefore its
 * validation). Instead this codec parses to a {@link JsonObject}, validates every field, and
 * rebuilds the envelope through {@link CryptoEnvelope#forAlgorithm(String)}.</p>
 */
public final class CryptoEnvelopeCodec {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final List<String> FORBIDDEN_KEYS = Arrays.asList(
            "key", "secretkey", "privatekey", "password"
    );

    private static final List<String> ALLOWED_ROOT_KEYS = Arrays.asList(
            "envVersion", "alg", "kid", "keyVersion", "kcv", "createdAt",
            "ivNonceHex", "aadHex", "ciphertextB64", "extensions"
    );

    /**
     * Algorithms this codec currently recognizes. Grows as more producers (beyond
     * {@code RsaKeyWrapOperations}) start emitting envelopes — add the new canonical name here,
     * do not remove the allow-list.
     */
    private static final List<String> ALLOWED_ALGORITHMS = Arrays.asList(
            "RSA-OAEP-256", "RSA-OAEP-512", "TR34-CMS"
    );

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]*$");
    private static final String COMPACT_PREFIX = "CCE1.";

    private CryptoEnvelopeCodec() {
    }

    // ------------------------------------------------------------------
    // JSON profile
    // ------------------------------------------------------------------

    /** Serializes as a plain, human-readable JSON object — best when the consumer speaks JSON. */
    public static String serializeJson(CryptoEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Envelope cannot be null");
        }
        return GSON.toJson(envelope);
    }

    /** Deserializes a plain JSON envelope, strictly validating every field. */
    public static CryptoEnvelope deserializeJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        JsonObject root = parseRootObject(json);
        return buildFromValidatedObject(root);
    }

    // ------------------------------------------------------------------
    // Compact profile — CCE1.<base64url(header)>.<base64url(ciphertext)>
    // ------------------------------------------------------------------

    /** Serializes as a compact, copy-paste-friendly string, mirroring JWE's dotted-segment shape. */
    public static String serializeCompact(CryptoEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Envelope cannot be null");
        }
        JsonObject header = GSON.toJsonTree(envelope).getAsJsonObject();
        header.remove("ciphertextB64");
        byte[] ciphertext = Base64.getDecoder().decode(envelope.getCiphertextB64());
        String headerSegment = base64Url(header.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String ciphertextSegment = base64Url(ciphertext);
        return COMPACT_PREFIX + headerSegment + "." + ciphertextSegment;
    }

    /** Deserializes a compact envelope, strictly validating every field. */
    public static CryptoEnvelope deserializeCompact(String compact) {
        if (compact == null || compact.isBlank()) {
            throw new IllegalArgumentException("Compact envelope cannot be null or empty");
        }
        if (!compact.startsWith(COMPACT_PREFIX)) {
            throw new IllegalArgumentException("Compact envelope must start with \"" + COMPACT_PREFIX + "\"");
        }
        String remainder = compact.substring(COMPACT_PREFIX.length());
        String[] parts = remainder.split("\\.", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("Compact envelope must have the shape CCE1.<header>.<ciphertext>");
        }
        String headerJson;
        byte[] ciphertext;
        try {
            headerJson = new String(base64UrlDecode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
            ciphertext = base64UrlDecode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed base64url segment in compact envelope", e);
        }
        JsonObject header = parseRootObject(headerJson);
        header.addProperty("ciphertextB64", Base64.getEncoder().encodeToString(ciphertext));
        return buildFromValidatedObject(header);
    }

    // ------------------------------------------------------------------
    // Auto-detection helpers, used by the Envelope Inspector / import flows
    // ------------------------------------------------------------------

    /** True if the given pasted text looks like a serialized {@link CryptoEnvelope} of either profile. */
    public static boolean looksLikeEnvelope(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.startsWith(COMPACT_PREFIX)) return true;
        return trimmed.startsWith("{") && trimmed.contains("\"envVersion\"");
    }

    /** Deserializes either profile, detected from the text shape. */
    public static CryptoEnvelope deserializeAuto(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Envelope text cannot be null or empty");
        }
        String trimmed = text.trim();
        if (trimmed.startsWith(COMPACT_PREFIX)) {
            return deserializeCompact(trimmed);
        }
        if (trimmed.startsWith("{")) {
            return deserializeJson(trimmed);
        }
        throw new IllegalArgumentException("Unrecognized envelope format: expected \"" + COMPACT_PREFIX
                + "...\" or a JSON object");
    }

    // ------------------------------------------------------------------
    // Shared validation
    // ------------------------------------------------------------------

    private static JsonObject parseRootObject(String json) {
        try {
            JsonElement rootElement = JsonParser.parseString(json);
            if (!rootElement.isJsonObject()) {
                throw new IllegalArgumentException("Envelope JSON root must be an object");
            }
            JsonObject root = rootElement.getAsJsonObject();
            for (String key : root.keySet()) {
                if (!ALLOWED_ROOT_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Unknown property in envelope: " + key);
                }
            }
            checkForbiddenKeys(root);
            return root;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed JSON format", e);
        }
    }

    private static CryptoEnvelope buildFromValidatedObject(JsonObject root) {
        String envVersion = optString(root, "envVersion");
        if (envVersion == null || !CryptoEnvelope.CURRENT_VERSION.equals(envVersion)) {
            throw new IllegalArgumentException("Unsupported envelope version: " + envVersion
                    + ". Expected: " + CryptoEnvelope.CURRENT_VERSION);
        }

        String algorithm = optString(root, "alg");
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm ('alg') is required");
        }
        String canonicalAlg = ALLOWED_ALGORITHMS.stream()
                .filter(allowed -> allowed.equalsIgnoreCase(algorithm))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported envelope algorithm: " + algorithm));

        String ciphertextB64 = optString(root, "ciphertextB64");
        if (ciphertextB64 == null || ciphertextB64.isBlank()) {
            throw new IllegalArgumentException("Ciphertext ('ciphertextB64') is required");
        }
        try {
            Base64.getDecoder().decode(ciphertextB64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ciphertext ('ciphertextB64') is not valid base64", e);
        }

        String ivNonceHex = validateHex(optString(root, "ivNonceHex"), "ivNonceHex");
        String aadHex = validateHex(optString(root, "aadHex"), "aadHex");
        String kcv = validateHex(optString(root, "kcv"), "kcv");

        String createdAt = optString(root, "createdAt");
        if (createdAt != null) {
            try {
                Instant.parse(createdAt);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("'createdAt' must be an ISO-8601 instant", e);
            }
        }

        Integer keyVersion = null;
        if (root.has("keyVersion") && !root.get("keyVersion").isJsonNull()) {
            try {
                keyVersion = root.get("keyVersion").getAsInt();
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'keyVersion' must be an integer", e);
            }
            if (keyVersion < 0) {
                throw new IllegalArgumentException("'keyVersion' cannot be negative");
            }
        }

        CryptoEnvelope.Builder builder = CryptoEnvelope.forAlgorithm(canonicalAlg)
                .kid(optString(root, "kid"))
                .keyVersion(keyVersion)
                .kcv(kcv)
                .createdAt(createdAt)
                .ivNonceHex(ivNonceHex)
                .aadHex(aadHex)
                .ciphertextB64(ciphertextB64);

        if (root.has("extensions") && !root.get("extensions").isJsonNull()) {
            JsonElement extEl = root.get("extensions");
            if (!extEl.isJsonObject()) {
                throw new IllegalArgumentException("'extensions' must be an object of string to string");
            }
            for (Map.Entry<String, JsonElement> entry : extEl.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("'extensions' values must be strings (key: " + entry.getKey() + ")");
                }
                builder.extension(entry.getKey(), value.getAsString());
            }
        }

        return builder.build();
    }

    private static void checkForbiddenKeys(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : obj.keySet()) {
                if (FORBIDDEN_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("Security violation: envelope cannot contain secret key material ('" + key + "')");
                }
                checkForbiddenKeys(obj.get(key));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                checkForbiddenKeys(child);
            }
        }
    }

    private static String validateHex(String value, String fieldName) {
        if (value == null) return null;
        if (!HEX_PATTERN.matcher(value).matches() || value.isEmpty() || value.length() % 2 != 0) {
            throw new IllegalArgumentException("'" + fieldName + "' must be a non-empty, even-length hexadecimal string");
        }
        return value;
    }

    private static String optString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return el.getAsString();
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }
}
