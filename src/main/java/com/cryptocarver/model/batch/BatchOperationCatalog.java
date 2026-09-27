package com.cryptocarver.model.batch;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecException;
import com.cryptocarver.codec.CodecRegistry;
import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.crypto.EmvTlv;
import com.cryptocarver.crypto.HashOperations;
import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.crypto.TR31Operations;
import com.cryptocarver.crypto.smartcard.ApduStatus;
import com.cryptocarver.model.SafeTransformations;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Catalog of safe, local, deterministic, and stateless batch operations. */
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
    public static final String LUHN_CALCULATE_CHECK_DIGIT = "Luhn (Mod 10) → Calculate Check Digit";
    public static final String LUHN_VALIDATE_CHECK_DIGIT = "Luhn (Mod 10) → Validate Check Digit";
    public static final String VERHOEFF_CALCULATE_CHECK_DIGIT = "Verhoeff → Calculate Check Digit";
    public static final String VERHOEFF_VALIDATE_CHECK_DIGIT = "Verhoeff → Validate Check Digit";
    public static final String DAMM_CALCULATE_CHECK_DIGIT = "Damm → Calculate Check Digit";
    public static final String DAMM_VALIDATE_CHECK_DIGIT = "Damm → Validate Check Digit";

    private static final String SHA1 = "SHA-1 (UTF-8 → Hex)";
    private static final String SHA224 = "SHA-224 (UTF-8 → Hex)";
    private static final String SHA3_256 = "SHA3-256 (UTF-8 → Hex)";
    private static final String SHA3_512 = "SHA3-512 (UTF-8 → Hex)";
    private static final String MD5 = "MD5 (legacy; UTF-8 → Hex)";
    private static final String CRC32 = "CRC32 (UTF-8 → Hex)";
    private static final String CRC32C = "CRC32C (UTF-8 → Hex)";
    private static final String UTF8_TO_BASE32 = "UTF-8 → Base32";
    private static final String BASE32_TO_UTF8 = "Base32 → UTF-8";
    private static final String UTF8_TO_BASE58 = "UTF-8 → Base58";
    private static final String BASE58_TO_UTF8 = "Base58 → UTF-8";
    private static final String UTF8_TO_BASE94 = "UTF-8 → Base94";
    private static final String BASE94_TO_UTF8 = "Base94 → UTF-8";
    private static final String DECIMAL_TO_BCD = "Decimal → Packed BCD Hex";
    private static final String BCD_TO_DECIMAL = "Packed BCD Hex → Decimal";
    private static final String AMEX_CHECK = "AMEX SE → Calculate Check Digit";
    private static final String PAN_VALIDATE = "PAN → Validate (Luhn, 13–19 digits)";
    private static final String TRACK2 = "Track 2 → Analyze JSON";
    private static final String EMV_TLV = "EMV TLV → JSON";
    private static final String APDU_STATUS = "APDU Status → Inspect";
    private static final String ASN1_INSPECT = "ASN.1 → Inspect";
    private static final String TLV_INSPECT = "TLV → Inspect";
    private static final String TR31_HEADER_JSON = "TR-31 → Header JSON";

    private static final List<String> OPERATIONS = List.of(
            SHA256_UTF8_HEX, SHA384_UTF8_HEX, SHA512_UTF8_HEX,
            UTF8_TO_HEX, HEX_TO_UTF8, UTF8_TO_BASE64, BASE64_TO_UTF8,
            HEX_TO_BASE64, BASE64_TO_HEX, UTF8_TO_BASE64URL, BASE64URL_TO_UTF8,
            LUHN_CALCULATE_CHECK_DIGIT, LUHN_VALIDATE_CHECK_DIGIT,
            VERHOEFF_CALCULATE_CHECK_DIGIT, VERHOEFF_VALIDATE_CHECK_DIGIT,
            DAMM_CALCULATE_CHECK_DIGIT, DAMM_VALIDATE_CHECK_DIGIT,
            SHA1, SHA224, SHA3_256, SHA3_512, MD5, CRC32, CRC32C,
            UTF8_TO_BASE32, BASE32_TO_UTF8, UTF8_TO_BASE58, BASE58_TO_UTF8,
            UTF8_TO_BASE94, BASE94_TO_UTF8, DECIMAL_TO_BCD, BCD_TO_DECIMAL,
            AMEX_CHECK, PAN_VALIDATE, TRACK2, EMV_TLV, APDU_STATUS, ASN1_INSPECT, TLV_INSPECT, TR31_HEADER_JSON
    );
    private static final Map<String, String> LOOKUP;

    static {
        Map<String, String> map = new HashMap<>();
        for (String op : OPERATIONS) map.put(op.toLowerCase(Locale.ROOT), op);
        map.put("sha-256", SHA256_UTF8_HEX); map.put("sha-384", SHA384_UTF8_HEX); map.put("sha-512", SHA512_UTF8_HEX);
        map.put("sha256", SHA256_UTF8_HEX);
        map.put("sha-1", SHA1); map.put("sha-224", SHA224);
        map.put("utf-8 → hex", UTF8_TO_HEX); map.put("utf-8 -> hex", UTF8_TO_HEX);
        map.put("hex → utf-8", HEX_TO_UTF8); map.put("hex -> utf-8", HEX_TO_UTF8);
        map.put("utf-8 → base64url", UTF8_TO_BASE64URL); map.put("utf-8 -> base64url", UTF8_TO_BASE64URL);
        map.put("base64url → utf-8", BASE64URL_TO_UTF8); map.put("base64url -> utf-8", BASE64URL_TO_UTF8);
        map.put("hex → base64", HEX_TO_BASE64); map.put("hex -> base64", HEX_TO_BASE64);
        map.put("base64 → hex", BASE64_TO_HEX); map.put("base64 -> hex", BASE64_TO_HEX);
        map.put("base64url-encode", UTF8_TO_BASE64URL); map.put("base64url-decode", BASE64URL_TO_UTF8);
        map.put("inspect-asn1", ASN1_INSPECT); map.put("inspect-tlv", TLV_INSPECT);
        map.put("sha3-256", SHA3_256); map.put("sha3-512", SHA3_512); map.put("md5", MD5);
        map.put("crc32", CRC32); map.put("crc32c", CRC32C);
        for (String op : OPERATIONS) map.put(slugCanonical(op), op);
        LOOKUP = Map.copyOf(map);
    }

    private BatchOperationCatalog() { }
    public static List<String> getAvailableOperations() { return OPERATIONS; }
    public static String resolveOperationName(String name) { return name == null || name.isBlank() ? null : LOOKUP.get(name.trim().toLowerCase(Locale.ROOT)); }
    public static boolean isSupportedOperation(String name) { return resolveOperationName(name) != null; }

    /** Stable URL-safe identifier for an operation; e.g. "SHA-256" becomes "sha-256". */
    public static String slug(String operationName) {
        String canonical = resolveOperationName(operationName);
        if (canonical == null) throw new IllegalArgumentException("Unsupported batch operation: " + operationName);
        return slugCanonical(canonical);
    }

    private static String slugCanonical(String canonical) {
        if (canonical.equals(SHA256_UTF8_HEX)) return "sha-256";
        if (canonical.equals(SHA384_UTF8_HEX)) return "sha-384";
        if (canonical.equals(SHA512_UTF8_HEX)) return "sha-512";
        if (canonical.equals(SHA1)) return "sha-1";
        if (canonical.equals(SHA224)) return "sha-224";
        if (canonical.equals(SHA3_256)) return "sha3-256";
        if (canonical.equals(SHA3_512)) return "sha3-512";
        if (canonical.equals(MD5)) return "md5";
        if (canonical.equals(CRC32)) return "crc32";
        if (canonical.equals(CRC32C)) return "crc32c";
        if (canonical.equals(UTF8_TO_HEX)) return "utf8-to-hex";
        if (canonical.equals(HEX_TO_UTF8)) return "hex-to-utf8";
        return canonical.toLowerCase(Locale.ROOT).replace(" → ", "-to-")
                .replace("–", "-").replace(" ", "-").replace("(", "").replace(")", "")
                .replace(";", "").replace("/", "-").replace("utf-8", "utf8")
                .replaceAll("[^a-z0-9-]+", "-").replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    public static Map<String, String> descriptions() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String op : OPERATIONS) out.put(slug(op), description(op));
        return Map.copyOf(out);
    }

    private static String description(String op) {
        if (op.equals(MD5)) return "Calcula MD5; algoritmo legacy, únicamente para compatibilidad.";
        if (op.equals(PAN_VALIDATE)) return "Valida longitud de 13 a 19 dígitos y dígito Luhn.";
        if (op.equals(TRACK2)) return "Extrae PAN, caducidad y código de servicio en JSON.";
        if (op.equals(APDU_STATUS)) return "Interpreta una palabra de estado APDU hexadecimal.";
        if (op.equals(ASN1_INSPECT)) return "Interpreta entrada ASN.1 en hexadecimal o Base64.";
        if (op.equals(TLV_INSPECT) || op.equals(EMV_TLV)) return "Analiza estructura TLV EMV desde hexadecimal.";
        if (op.equals(CRC32C)) return "Calcula CRC-32C (CRC-32/ISCSI) en hexadecimal.";
        if (op.equals(CRC32)) return "Calcula CRC-32 en hexadecimal.";
        return "Ejecuta " + op + " sobre la entrada, sin claves ni estado de sesión.";
    }

    public static Map<String, String> execute(String operationName, Map<String, String> row, String inputColumn, String outputColumn) throws Exception {
        Objects.requireNonNull(row, "Row map cannot be null");
        Objects.requireNonNull(inputColumn, "Input column cannot be null");
        Objects.requireNonNull(outputColumn, "Output column cannot be null");
        String input = row.get(inputColumn);
        if (input == null) throw new IllegalArgumentException(inputColumn + " field is required");
        String op = resolveOperationName(operationName);
        if (op == null) throw new IllegalArgumentException("Unsupported batch operation: " + operationName);
        String result;
        try {
            result = switch (op) {
                case SHA256_UTF8_HEX -> SafeTransformations.sha256(input);
                case SHA384_UTF8_HEX -> SafeTransformations.sha384(input);
                case SHA512_UTF8_HEX -> SafeTransformations.sha512(input);
                case SHA1 -> hash(input, "SHA-1"); case SHA224 -> hash(input, "SHA-224");
                case SHA3_256 -> hash(input, "SHA3-256"); case SHA3_512 -> hash(input, "SHA3-512"); case MD5 -> hash(input, "MD5");
                case CRC32 -> crc(input, HashOperations.Crc32Variant.ISO_HDLC);
                case CRC32C -> crc(input, HashOperations.Crc32Variant.CASTAGNOLI);
                case UTF8_TO_HEX -> SafeTransformations.utf8ToHex(input); case HEX_TO_UTF8 -> SafeTransformations.hexToUtf8(input);
                case UTF8_TO_BASE64 -> SafeTransformations.utf8ToBase64(input); case BASE64_TO_UTF8 -> SafeTransformations.base64ToUtf8(input);
                case HEX_TO_BASE64 -> SafeTransformations.hexToBase64(input); case BASE64_TO_HEX -> SafeTransformations.base64ToHex(input);
                case UTF8_TO_BASE64URL -> SafeTransformations.encodeBase64Url(input); case BASE64URL_TO_UTF8 -> SafeTransformations.decodeBase64Url(input);
                case UTF8_TO_BASE32 -> encode(input, ByteFormat.BASE32); case BASE32_TO_UTF8 -> decode(input, ByteFormat.BASE32);
                case UTF8_TO_BASE58 -> encode(input, ByteFormat.BASE58); case BASE58_TO_UTF8 -> decode(input, ByteFormat.BASE58);
                case UTF8_TO_BASE94 -> encode(input, ByteFormat.BASE94); case BASE94_TO_UTF8 -> decode(input, ByteFormat.BASE94);
                case DECIMAL_TO_BCD -> java.util.HexFormat.of().withUpperCase().formatHex(com.cryptocarver.util.DataConverter.decimalToPackedBcd(input));
                case BCD_TO_DECIMAL -> com.cryptocarver.util.DataConverter.packedBcdToDecimal(java.util.HexFormat.of().parseHex(input));
                case LUHN_CALCULATE_CHECK_DIGIT -> checkDigit(input, "Luhn (Mod 10)");
                case LUHN_VALIDATE_CHECK_DIGIT -> validateDigit(input, "Luhn (Mod 10)");
                case VERHOEFF_CALCULATE_CHECK_DIGIT -> checkDigit(input, "Verhoeff");
                case VERHOEFF_VALIDATE_CHECK_DIGIT -> validateDigit(input, "Verhoeff");
                case DAMM_CALCULATE_CHECK_DIGIT -> checkDigit(input, "Damm");
                case DAMM_VALIDATE_CHECK_DIGIT -> validateDigit(input, "Damm");
                case AMEX_CHECK -> checkDigit(input, "AMEX SE (Luhn/Mod 10)");
                case PAN_VALIDATE -> Boolean.toString(isValidPan(input));
                case TRACK2 -> track2Json(input);
                case EMV_TLV -> new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(EmvTlv.parse(input));
                case APDU_STATUS -> new com.google.gson.Gson().toJson(ApduStatus.parse(input));
                case ASN1_INSPECT -> SafeTransformations.inspectAsn1(input);
                case TLV_INSPECT -> SafeTransformations.inspectTlv(input);
                case TR31_HEADER_JSON -> TR31Operations.parseHeaderJson(input);
                default -> throw new IllegalArgumentException("Unsupported batch operation: " + operationName);
            };
        } catch (CodecException e) { throw new IllegalArgumentException("Invalid " + e.getFormat().getDisplayName() + " format"); }
        catch (IllegalArgumentException e) {
            if (op.equals(BASE64URL_TO_UTF8)) throw new IllegalArgumentException("Invalid Base64URL format");
            if (op.equals(LUHN_CALCULATE_CHECK_DIGIT) || op.equals(LUHN_VALIDATE_CHECK_DIGIT)) {
                throw new IllegalArgumentException(input.isEmpty() || !input.matches("[0-9]+")
                        ? "Check digit input must contain ASCII digits only"
                        : "Check digit validation requires at least two digits");
            }
            throw e;
        }
        return Map.of(outputColumn, result);
    }

    private static String hash(String value, String algorithm) throws Exception {
        return hex(HashOperations.calculateHash(value.getBytes(StandardCharsets.UTF_8), algorithm));
    }
    private static String crc(String value, HashOperations.Crc32Variant variant) { return hex(HashOperations.calculateCrc32(value.getBytes(StandardCharsets.UTF_8), variant)); }
    private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
    private static String encode(String value, ByteFormat format) { return CodecRegistry.getInstance().encode(value.getBytes(StandardCharsets.UTF_8), format); }
    private static String decode(String value, ByteFormat format) { return new String(CodecRegistry.getInstance().decode(value, format), StandardCharsets.UTF_8); }
    private static String checkDigit(String input, String algorithm) {
        if (input.isEmpty() || !input.matches("[0-9]+")) throw new IllegalArgumentException("Input must contain ASCII digits only");
        return Integer.toString(CheckDigitCalculator.calculateCheckDigit(input, algorithm));
    }
    private static String validateDigit(String input, String algorithm) {
        if (input.length() < 2 || !input.matches("[0-9]+")) throw new IllegalArgumentException("Input must contain at least two ASCII digits");
        return Boolean.toString(CheckDigitCalculator.validateCheckDigit(input, algorithm));
    }
    private static boolean isValidPan(String pan) {
        return pan != null && pan.matches("[0-9]{13,19}") && CheckDigitCalculator.validateCheckDigit(pan, "Luhn (Mod 10)");
    }
    private static String track2Json(String input) {
        String normalized = input.trim().replace('D', '=').replace('d', '=');
        if (normalized.startsWith(";") && normalized.endsWith("?")) {
            // Already has ISO track sentinels.
        } else {
            normalized = ";" + normalized.replaceAll("F+$", "") + "?";
        }
        String analysis = PaymentOperations.parseTrack2(normalized);
        if (analysis.startsWith("Invalid Track 2 format")) throw new IllegalArgumentException(analysis);
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String line : analysis.split("\\R")) {
            if (line.startsWith("PAN: ")) result.put("pan", line.substring(5));
            else if (line.startsWith("Expiry: ")) result.put("expiry", line.substring(8).split(" ", 2)[0]);
            else if (line.startsWith("Service Code: ")) result.put("serviceCode", line.substring(14));
        }
        if (!result.keySet().containsAll(List.of("pan", "expiry", "serviceCode")))
            throw new IllegalArgumentException("Track 2 must contain PAN, expiry, and service code");
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
