package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.cryptocarver.crypto.EBCDICConverter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Codec for serializing and deserializing FileCipherRecipe objects.
 */
public class FileCipherRecipeCodec {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final List<String> FORBIDDEN_KEYS = Arrays.asList(
            "key", "secretkey", "privatekey", "password"
    );

    private static final List<String> ALLOWED_ROOT_KEYS = Arrays.asList(
            "version", "algorithm", "linesMode", "lineEncoding", "compactMode", "charset", "aadHex", "ivNonceHex", "tagRef"
    );

    private static final List<String> ALLOWED_ALGORITHMS = Arrays.asList(
            "AES-256-CBC", "AES-256-GCM", "ChaCha20-Poly1305"
    );

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]*$");

    /**
     * Serializes a FileCipherRecipe to a JSON string.
     *
     * @param recipe The recipe to serialize.
     * @return The JSON representation of the recipe.
     */
    public static String serialize(FileCipherRecipe recipe) {
        if (recipe == null) {
            return "{}";
        }
        FileCipherRecipe validated = normalizeAndValidate(recipe);
        return GSON.toJson(validated);
    }

    /**
     * Deserializes a JSON string into a FileCipherRecipe, strictly validating all fields.
     *
     * @param json The JSON string to deserialize.
     * @return The deserialized FileCipherRecipe.
     * @throws IllegalArgumentException if the JSON is malformed, version is unsupported, or contains security violations.
     */
    public static FileCipherRecipe deserialize(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

        try {
            JsonElement rootElement = JsonParser.parseString(json);
            if (!rootElement.isJsonObject()) {
                throw new IllegalArgumentException("JSON root must be an object");
            }
            JsonObject rootObj = rootElement.getAsJsonObject();

            // 1. Explicitly check 'version' in root JSON object
            if (!rootObj.has("version")) {
                throw new IllegalArgumentException("JSON recipe must contain a 'version' property");
            }
            JsonElement versionElement = rootObj.get("version");
            if (versionElement.isJsonNull() || !versionElement.isJsonPrimitive() || !versionElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("'version' must be a string");
            }
            String versionStr = versionElement.getAsString();
            if (!FileCipherRecipe.CURRENT_VERSION.equals(versionStr)) {
                throw new IllegalArgumentException("Unsupported recipe version: " + versionStr +
                        ". Expected: " + FileCipherRecipe.CURRENT_VERSION);
            }

            // 2. Lista blanca en el objeto raíz
            for (String key : rootObj.keySet()) {
                if (!ALLOWED_ROOT_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Unknown property in recipe root: " + key);
                }
            }

            // 3. Prohibir claves secretas recursivamente
            checkForbiddenKeys(rootElement);

            FileCipherRecipe recipe = GSON.fromJson(json, FileCipherRecipe.class);
            if (recipe == null) {
                throw new IllegalArgumentException("JSON string does not represent a valid recipe");
            }

            return normalizeAndValidate(recipe);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed JSON format", e);
        }
    }

    private static FileCipherRecipe normalizeAndValidate(FileCipherRecipe recipe) {
        // Validación de versión en el objeto (por si se construyó externamente con versión incorrecta)
        if (!FileCipherRecipe.CURRENT_VERSION.equals(recipe.getVersion())) {
            throw new IllegalArgumentException("Unsupported recipe version: " + recipe.getVersion() +
                    ". Expected: " + FileCipherRecipe.CURRENT_VERSION);
        }

        // Normalización y validación de algoritmo
        String algorithm = recipe.getAlgorithm();
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm is required");
        }
        String canonicalAlgorithm = null;
        for (String allowed : ALLOWED_ALGORITHMS) {
            if (allowed.equalsIgnoreCase(algorithm)) {
                canonicalAlgorithm = allowed;
                break;
            }
        }
        if (canonicalAlgorithm == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }

        boolean isAead = "AES-256-GCM".equals(canonicalAlgorithm) || "ChaCha20-Poly1305".equals(canonicalAlgorithm);
        boolean isCbc = "AES-256-CBC".equals(canonicalAlgorithm);

        // Normalización y validación de lineEncoding
        String canonicalLineEncoding = null;
        if (recipe.getLineEncoding() != null) {
            if (recipe.getLineEncoding().equalsIgnoreCase("Base64URL")) {
                canonicalLineEncoding = "Base64URL";
            } else if (recipe.getLineEncoding().equalsIgnoreCase("Hexadecimal")) {
                canonicalLineEncoding = "Hexadecimal";
            } else {
                throw new IllegalArgumentException("Unsupported line encoding: " + recipe.getLineEncoding());
            }
        }

        // Normalización y validación de charset
        String canonicalCharset = null;
        if (recipe.getCharset() != null) {
            if (recipe.getCharset().equalsIgnoreCase("UTF-8")) {
                canonicalCharset = "UTF-8";
            } else {
                for (String cp : EBCDICConverter.supportedCodePages().keySet()) {
                    if (cp.equalsIgnoreCase(recipe.getCharset())) {
                        canonicalCharset = cp;
                        break;
                    }
                }
                if (canonicalCharset == null) {
                    throw new IllegalArgumentException("Unsupported charset: " + recipe.getCharset());
                }
            }
        }

        if (recipe.isLinesMode() && canonicalLineEncoding == null) {
            throw new IllegalArgumentException("Line encoding is required in lines mode");
        }
        if (recipe.isLinesMode() && canonicalCharset == null) {
            throw new IllegalArgumentException("Text charset is required in lines mode");
        }

        // Validación de hexadecimales
        String aadHex = recipe.getAadHex();
        if (aadHex != null) {
            if (!HEX_PATTERN.matcher(aadHex).matches()) {
                throw new IllegalArgumentException("AAD must be a valid hexadecimal string");
            }
            if (aadHex.length() % 2 != 0 || aadHex.isEmpty()) {
                throw new IllegalArgumentException("AAD must be non-empty and have an even length");
            }
        }

        String ivNonceHex = recipe.getIvNonceHex();
        if (ivNonceHex != null) {
            if (!HEX_PATTERN.matcher(ivNonceHex).matches()) {
                throw new IllegalArgumentException("IV/Nonce must be a valid hexadecimal string");
            }
            if (ivNonceHex.length() % 2 != 0 || ivNonceHex.isEmpty()) {
                throw new IllegalArgumentException("IV/Nonce must be non-empty and have an even length");
            }
        }

        // Compatibilidad por algoritmo y modo
        if (isCbc) {
            if (ivNonceHex == null || ivNonceHex.length() != 32) {
                throw new IllegalArgumentException("CBC mode requires a 16-byte IV (32 hex characters)");
            }
            if (aadHex != null) {
                throw new IllegalArgumentException("AAD is only supported for AEAD algorithms (GCM/ChaCha20)");
            }
        }

        if (isAead) {
            if (recipe.isLinesMode()) {
                if (ivNonceHex != null) {
                    throw new IllegalArgumentException("IV/Nonce should not be provided for AEAD algorithms in lines mode");
                }
            } else {
                // En modo fichero completo, AEAD requiere un nonce válido
                if (ivNonceHex == null || ivNonceHex.length() != 24) {
                    throw new IllegalArgumentException("AEAD nonce must be provided in full file mode and be exactly 12 bytes (24 hex characters)");
                }
            }
        }

        // Seguridad de tagRef
        String tagRef = recipe.getTagRef();
        if (isAead && !recipe.isLinesMode() && (tagRef == null || tagRef.isEmpty())) {
            throw new IllegalArgumentException("AEAD full file mode requires a detached tag filename");
        }
        if (tagRef != null && !tagRef.isEmpty()) {
            if (tagRef.contains("/") || tagRef.contains("\\") || tagRef.contains("..")) {
                throw new IllegalArgumentException("tagRef cannot contain path separators or parent directory references");
            }
            Path p = Path.of(tagRef);
            if (p.isAbsolute() || !p.getFileName().toString().equals(tagRef)) {
                throw new IllegalArgumentException("tagRef must be a simple filename, not a path");
            }
        }

        return new FileCipherRecipe(
                canonicalAlgorithm,
                recipe.isLinesMode(),
                canonicalLineEncoding,
                recipe.isCompactMode(),
                canonicalCharset,
                aadHex,
                ivNonceHex,
                tagRef
        );
    }

    private static void checkForbiddenKeys(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : obj.keySet()) {
                if (FORBIDDEN_KEYS.contains(key.toLowerCase())) {
                    throw new IllegalArgumentException("Security violation: JSON recipe cannot contain secret key material ('" + key + "')");
                }
                checkForbiddenKeys(obj.get(key));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                checkForbiddenKeys(child);
            }
        }
    }
}
