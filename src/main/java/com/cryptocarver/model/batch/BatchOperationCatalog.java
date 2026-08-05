package com.cryptocarver.model.batch;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecException;
import com.cryptocarver.model.SafeTransformations;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Catalog of safe, local, deterministic, and stateless batch operations for Batch Runner.
 */
public final class BatchOperationCatalog {

    public static final String SHA256_UTF8_HEX = "SHA-256 (UTF-8 → Hex)";
    public static final String SHA384_UTF8_HEX = "SHA-384 (UTF-8 → Hex)";
    public static final String SHA512_UTF8_HEX = "SHA-512 (UTF-8 → Hex)";

    public static final String UTF8_TO_HEX = "UTF-8 → Hexadecimal";
    public static final String HEX_TO_UTF8 = "Hexadecimal → UTF-8";

    public static final String UTF8_TO_BASE64 = "UTF-8 → Base64";
    public static final String BASE64_TO_UTF8 = "Base64 → UTF-8";

    public static final String HEX_TO_BASE64 = "Hexadecimal → Base64";
    public static final String BASE64_TO_HEX = "Base64 → Hexadecimal";

    public static final String UTF8_TO_BASE64URL = "UTF-8 → Base64URL";
    public static final String BASE64URL_TO_UTF8 = "Base64URL → UTF-8";

    private static final List<String> OPERATIONS = List.of(
            SHA256_UTF8_HEX,
            SHA384_UTF8_HEX,
            SHA512_UTF8_HEX,
            UTF8_TO_HEX,
            HEX_TO_UTF8,
            UTF8_TO_BASE64,
            BASE64_TO_UTF8,
            HEX_TO_BASE64,
            BASE64_TO_HEX,
            UTF8_TO_BASE64URL,
            BASE64URL_TO_UTF8
    );

    private static final Map<String, String> LOOKUP;

    static {
        Map<String, String> map = new HashMap<>();
        for (String op : OPERATIONS) {
            map.put(op.toLowerCase(Locale.ROOT), op);
        }
        // Explicit fixed historical aliases
        map.put("sha-256", SHA256_UTF8_HEX);
        map.put("sha-384", SHA384_UTF8_HEX);
        map.put("sha-512", SHA512_UTF8_HEX);
        map.put("utf-8 → hex", UTF8_TO_HEX);
        map.put("utf-8 -> hex", UTF8_TO_HEX);
        map.put("hex → utf-8", HEX_TO_UTF8);
        map.put("hex -> utf-8", HEX_TO_UTF8);
        map.put("utf-8 → base64url", UTF8_TO_BASE64URL);
        map.put("utf-8 -> base64url", UTF8_TO_BASE64URL);
        map.put("base64url → utf-8", BASE64URL_TO_UTF8);
        map.put("base64url -> utf-8", BASE64URL_TO_UTF8);
        map.put("hex → base64", HEX_TO_BASE64);
        map.put("hex -> base64", HEX_TO_BASE64);
        map.put("base64 → hex", BASE64_TO_HEX);
        map.put("base64 -> hex", BASE64_TO_HEX);

        LOOKUP = Map.copyOf(map);
    }

    private BatchOperationCatalog() { }

    public static List<String> getAvailableOperations() {
        return OPERATIONS;
    }

    public static String resolveOperationName(String operationName) {
        if (operationName == null || operationName.isBlank()) return null;
        return LOOKUP.get(operationName.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isSupportedOperation(String operationName) {
        return resolveOperationName(operationName) != null;
    }

    public static Map<String, String> execute(String operationName, Map<String, String> row, String inputColumn, String outputColumn) throws Exception {
        Objects.requireNonNull(row, "Row map cannot be null");
        Objects.requireNonNull(inputColumn, "Input column cannot be null");
        Objects.requireNonNull(outputColumn, "Output column cannot be null");

        String input = row.get(inputColumn);
        if (input == null) {
            throw new IllegalArgumentException(inputColumn + " field is required");
        }

        String canonical = resolveOperationName(operationName);
        if (canonical == null) {
            throw new IllegalArgumentException("Unsupported batch operation: " + operationName);
        }

        String result;
        try {
            result = switch (canonical) {
                case SHA256_UTF8_HEX -> SafeTransformations.sha256(input);
                case SHA384_UTF8_HEX -> SafeTransformations.sha384(input);
                case SHA512_UTF8_HEX -> SafeTransformations.sha512(input);

                case UTF8_TO_HEX -> SafeTransformations.utf8ToHex(input);
                case HEX_TO_UTF8 -> SafeTransformations.hexToUtf8(input);

                case UTF8_TO_BASE64 -> SafeTransformations.utf8ToBase64(input);
                case BASE64_TO_UTF8 -> SafeTransformations.base64ToUtf8(input);

                case HEX_TO_BASE64 -> SafeTransformations.hexToBase64(input);
                case BASE64_TO_HEX -> SafeTransformations.base64ToHex(input);

                case UTF8_TO_BASE64URL -> SafeTransformations.encodeBase64Url(input);
                case BASE64URL_TO_UTF8 -> SafeTransformations.decodeBase64Url(input);

                default -> throw new IllegalArgumentException("Unsupported batch operation: " + operationName);
            };
        } catch (CodecException e) {
            throw new IllegalArgumentException(sanitizeCodecError(e));
        } catch (IllegalArgumentException e) {
            if (canonical.equals(BASE64URL_TO_UTF8)) {
                throw new IllegalArgumentException("Invalid Base64URL format");
            }
            throw e;
        } catch (Exception e) {
            if (canonical.equals(HEX_TO_UTF8) || canonical.equals(HEX_TO_BASE64)) {
                throw new IllegalArgumentException("Invalid Hexadecimal format");
            }
            if (canonical.equals(BASE64_TO_UTF8) || canonical.equals(BASE64_TO_HEX)) {
                throw new IllegalArgumentException("Invalid Base64 format");
            }
            if (canonical.equals(BASE64URL_TO_UTF8)) {
                throw new IllegalArgumentException("Invalid Base64URL format");
            }
            throw new IllegalArgumentException("Batch operation failed");
        }

        return Map.of(outputColumn, result);
    }

    private static String sanitizeCodecError(CodecException e) {
        if (e.getFormat() == ByteFormat.HEX) {
            return "Invalid Hexadecimal format";
        }
        if (e.getFormat() == ByteFormat.BASE64) {
            return "Invalid Base64 format";
        }
        if (e.getFormat() == ByteFormat.BASE64_URL) {
            return "Invalid Base64URL format";
        }
        return "Batch operation failed";
    }
}
