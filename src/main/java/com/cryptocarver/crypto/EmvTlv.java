package com.cryptocarver.crypto;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/** Minimal strict BER-TLV parser with a practical EMV tag dictionary. */
public final class EmvTlv {
    private EmvTlv() { }
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("4F", "Application Identifier (AID)"), Map.entry("50", "Application Label"),
            Map.entry("57", "Track 2 Equivalent Data"), Map.entry("5A", "Application PAN"),
            Map.entry("5F2A", "Transaction Currency Code"), Map.entry("5F34", "PAN Sequence Number"),
            Map.entry("5F36", "Transaction Currency Exponent"), Map.entry("5F24", "Application Expiration Date"),
            Map.entry("82", "Application Interchange Profile"), Map.entry("84", "Dedicated File Name"),
            Map.entry("8A", "Authorization Response Code"), Map.entry("91", "Issuer Authentication Data"),
            Map.entry("95", "Terminal Verification Results"), Map.entry("9A", "Transaction Date"),
            Map.entry("9C", "Transaction Type"), Map.entry("9F02", "Amount, Authorised"),
            Map.entry("9F03", "Amount, Other"), Map.entry("9F10", "Issuer Application Data"),
            Map.entry("9F1A", "Terminal Country Code"), Map.entry("9F26", "Application Cryptogram"),
            Map.entry("9F27", "Cryptogram Information Data"), Map.entry("9F36", "Application Transaction Counter"),
            Map.entry("9F37", "Unpredictable Number"), Map.entry("9F38", "Processing Options Data Object List (PDOL)"),
            Map.entry("8C", "Card Risk Management Data Object List 1 (CDOL1)"),
            Map.entry("8D", "Card Risk Management Data Object List 2 (CDOL2)"),
            Map.entry("70", "EMV Record Template"), Map.entry("77", "Response Message Template Format 2"));

    private static final Map<String, Integer> FIXED_LENGTHS = Map.ofEntries(
            Map.entry("5F2A", 2), Map.entry("5F24", 3), Map.entry("5F34", 1), Map.entry("5F36", 1),
            Map.entry("82", 2), Map.entry("8A", 2), Map.entry("95", 5), Map.entry("9A", 3), Map.entry("9C", 1),
            Map.entry("9F02", 6), Map.entry("9F03", 6), Map.entry("9F1A", 2), Map.entry("9F26", 8),
            Map.entry("9F27", 1), Map.entry("9F36", 2), Map.entry("9F37", 4));

    public record Item(String tag, String name, int length, String value, boolean constructed, List<Item> children) { }
    /** A non-cryptographic structural assessment of a decoded EMV data set. */
    public record Analysis(int topLevelItems, int totalItems, int totalValueBytes,
            Map<String, String> firstValues, List<String> warnings) { }
    /** Exact DOL material, including zero-filled fields, for reproducible ARQC inputs. */
    public record DolField(String tag, String name, int length, String value, boolean supplied) { }
    public record DolBuildResult(String data, List<DolField> fields, List<String> warnings) { }

    public static String interpretation(Item item) {
        String value = item.value();
        return switch (item.tag()) {
            case "9F02", "9F03" -> "amount minor units: " + bcd(value);
            case "5A" -> "PAN: " + bcd(value).replaceAll("F$", "");
            case "5F24" -> bcdDate("expiry", value);
            case "9A" -> bcdDate("date", value);
            case "5F2A" -> "currency: " + currencyName(Integer.parseInt(value, 16)) + " (" + Integer.parseInt(value, 16) + ")";
            case "9F1A" -> "country: " + countryName(Integer.parseInt(value, 16)) + " (" + Integer.parseInt(value, 16) + ")";
            case "9F36" -> "counter: " + Integer.parseInt(value, 16);
            case "50" -> "label: " + new String(hexToBytes(value), java.nio.charset.StandardCharsets.US_ASCII);
            case "57" -> "track 2: " + bcd(value).replace('D', '=');
            case "9F27" -> "cryptogram type: " + cryptogramType(value);
            case "9C" -> "transaction type: " + transactionType(value);
            default -> "";
        };
    }

    /** Produces a compact assessment suitable for a UI or a batch report. */
    public static Analysis analyze(String hex) {
        List<Item> roots = parse(hex);
        List<Item> flat = new ArrayList<>(); flatten(roots, flat);
        Map<String, String> firstValues = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>(); int valueBytes = 0;
        for (Item item : flat) {
            valueBytes += item.length();
            if (!item.constructed()) firstValues.putIfAbsent(item.tag(), item.value());
            Integer expected = FIXED_LENGTHS.get(item.tag());
            if (expected != null && item.length() != expected) {
                warnings.add(item.tag() + " declares " + item.length() + " bytes; EMV commonly expects " + expected);
            }
        }
        if (!firstValues.containsKey("9F02")) warnings.add("9F02 Amount, Authorised is not present");
        if (!firstValues.containsKey("9F26")) warnings.add("9F26 Application Cryptogram is not present");
        if (firstValues.containsKey("9F26") && !firstValues.containsKey("9F27")) warnings.add("9F26 is present without 9F27 Cryptogram Information Data");
        return new Analysis(roots.size(), flat.size(), valueBytes, Map.copyOf(firstValues), List.copyOf(warnings));
    }

    /** Formats the key transaction fields without treating this as an ARQC verification. */
    public static String transactionSummary(Analysis analysis) {
        StringBuilder out = new StringBuilder(); Map<String, String> values = analysis.firstValues();
        if (values.containsKey("9F02")) appendSummary(out, "Amount, authorised", interpretation(new Item("9F02", "", 6, values.get("9F02"), false, List.of())));
        if (values.containsKey("9F03")) appendSummary(out, "Amount, other", interpretation(new Item("9F03", "", 6, values.get("9F03"), false, List.of())));
        if (values.containsKey("5F2A")) appendSummary(out, "Currency", interpretation(new Item("5F2A", "", 2, values.get("5F2A"), false, List.of())));
        if (values.containsKey("9F1A")) appendSummary(out, "Terminal country", interpretation(new Item("9F1A", "", 2, values.get("9F1A"), false, List.of())));
        if (values.containsKey("9A")) appendSummary(out, "Date", interpretation(new Item("9A", "", 3, values.get("9A"), false, List.of())));
        if (values.containsKey("9C")) appendSummary(out, "Transaction type", transactionType(values.get("9C")));
        if (values.containsKey("9F36")) appendSummary(out, "ATC", interpretation(new Item("9F36", "", 2, values.get("9F36"), false, List.of())));
        if (values.containsKey("9F27")) appendSummary(out, "Cryptogram type", cryptogramType(values.get("9F27")));
        if (values.containsKey("9F26")) appendSummary(out, "Application cryptogram", values.get("9F26"));
        if (out.isEmpty()) out.append("No common transaction fields found.");
        return out.toString();
    }

    private static void appendSummary(StringBuilder out, String label, String value) { if (value != null && !value.isBlank()) out.append(label).append(": ").append(value).append("\n"); }
    private static void flatten(List<Item> items, List<Item> output) { for (Item item : items) { output.add(item); flatten(item.children(), output); } }
    private static String bcdDate(String label, String value) { String bcd = bcd(value); return bcd.length() == 6 && !bcd.startsWith("(") ? label + ": 20" + bcd.substring(0, 2) + "-" + bcd.substring(2, 4) + "-" + bcd.substring(4, 6) : label + ": invalid BCD date"; }
    private static String currencyName(int code) { return switch (code) { case 978 -> "EUR"; case 840 -> "USD"; case 826 -> "GBP"; case 392 -> "JPY"; default -> "ISO 4217 numeric"; }; }
    private static String countryName(int code) { return switch (code) { case 724 -> "Spain"; case 250 -> "France"; case 276 -> "Germany"; case 380 -> "Italy"; case 826 -> "United Kingdom"; case 840 -> "United States"; default -> "ISO 3166 numeric"; }; }
    private static String transactionType(String value) { return switch (value == null ? "" : value.toUpperCase(java.util.Locale.ROOT)) { case "00" -> "purchase"; case "01" -> "cash advance"; case "09" -> "purchase with cashback"; case "20" -> "return/refund"; default -> "EMV code " + value; }; }
    private static String cryptogramType(String value) { if (value == null || value.length() != 2) return "invalid CID"; int cid = Integer.parseInt(value, 16); return switch (cid & 0xC0) { case 0x00 -> "AAC (decline)"; case 0x40 -> "TC (offline approval)"; case 0x80 -> "ARQC (online authorization request)"; default -> "RFU"; }; }

    /** Parses a CDOL/DDOL value: tag followed by its one-byte requested length. */
    public static List<Item> parseDol(String hex) {
        String value = hex == null ? "" : hex.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        if (!value.matches("[0-9A-F]*") || (value.length() & 1) != 0) throw new IllegalArgumentException("DOL must be even-length hexadecimal");
        byte[] data = new byte[value.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        java.util.ArrayList<Item> fields = new java.util.ArrayList<>(); int p = 0;
        while (p < data.length) {
            int start = p, first = data[p++] & 0xFF;
            if ((first & 0x1F) == 0x1F) { do { if (p >= data.length) throw new IllegalArgumentException("Truncated DOL tag"); } while ((data[p++] & 0x80) != 0); }
            if (p >= data.length) throw new IllegalArgumentException("Missing requested length in DOL");
            String tag = hex(data, start, p); int length = data[p++] & 0xFF;
            fields.add(new Item(tag, NAMES.getOrDefault(tag, "Unknown EMV tag"), length, "", false, List.of()));
        }
        return fields;
    }

    /** Builds the exact byte sequence requested by a CDOL/DDOL; missing values are zero-filled. */
    public static String buildDol(String dolHex, Map<String, String> valuesByTag) {
        return buildDolDetailed(dolHex, valuesByTag).data();
    }

    /** Builds a DOL while retaining exactly which values were supplied or zero-filled. */
    public static DolBuildResult buildDolDetailed(String dolHex, Map<String, String> valuesByTag) {
        StringBuilder out = new StringBuilder();
        Map<String, String> values = new LinkedHashMap<>();
        if (valuesByTag != null) valuesByTag.forEach((tag, value) -> values.put(tag == null ? "" : tag.trim().toUpperCase(java.util.Locale.ROOT), value));
        List<DolField> fields = new ArrayList<>(); List<String> warnings = new ArrayList<>(); java.util.Set<String> requested = new java.util.HashSet<>();
        for (Item field : parseDol(dolHex)) {
            requested.add(field.tag());
            String value = values.get(field.tag());
            if (value == null || value.isBlank()) {
                String zero = "00".repeat(field.length()); out.append(zero);
                fields.add(new DolField(field.tag(), field.name(), field.length(), zero, false));
                warnings.add(field.tag() + " was not supplied and was zero-filled (" + field.length() + " bytes)");
                continue;
            }
            String normalized = value.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
            if (!normalized.matches("[0-9A-F]*") || normalized.length() != field.length() * 2) {
                throw new IllegalArgumentException("DOL value for " + field.tag() + " must be exactly " + field.length() + " bytes of hexadecimal");
            }
            out.append(normalized);
            fields.add(new DolField(field.tag(), field.name(), field.length(), normalized, true));
        }
        for (String tag : values.keySet()) if (!requested.contains(tag)) warnings.add(tag + " was supplied but is not requested by this DOL");
        return new DolBuildResult(out.toString(), List.copyOf(fields), List.copyOf(warnings));
    }

    private static String bcd(String hex) {
        if (!hex.matches("[0-9A-F]+") || hex.matches(".*[A-CE].*")) return "(not valid BCD)";
        return hex;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    public static List<Item> parse(String hex) {
        String value = hex == null ? "" : hex.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        if (value.isEmpty() || !value.matches("[0-9A-F]+") || (value.length() & 1) != 0) throw new IllegalArgumentException("TLV must be non-empty even-length hexadecimal");
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return parse(bytes, 0, bytes.length).items;
    }
    private static Result parse(byte[] data, int start, int end) {
        java.util.ArrayList<Item> items = new java.util.ArrayList<>(); int p = start;
        while (p < end) {
            int tagStart = p; int first = data[p++] & 0xFF;
            if ((first & 0x1F) == 0x1F) { do { if (p >= end) throw new IllegalArgumentException("Truncated multi-byte TLV tag"); } while ((data[p++] & 0x80) != 0); }
            String tag = hex(data, tagStart, p); boolean constructed = (first & 0x20) != 0;
            if (p >= end) throw new IllegalArgumentException("Missing length for tag " + tag);
            int lengthByte = data[p++] & 0xFF, length;
            if ((lengthByte & 0x80) == 0) length = lengthByte;
            else { int count = lengthByte & 0x7F; if (count == 0 || count > 3 || p + count > end) throw new IllegalArgumentException("Invalid length for tag " + tag); length = 0; for (int i=0;i<count;i++) length=(length<<8)|(data[p++]&0xFF); }
            if (p + length > end) throw new IllegalArgumentException("Truncated value for tag " + tag);
            int valueStart = p; p += length;
            List<Item> children = constructed ? parse(data, valueStart, p).items : List.of();
            items.add(new Item(tag, NAMES.getOrDefault(tag, "Unknown EMV tag"), length, hex(data, valueStart, p), constructed, children));
        }
        return new Result(items);
    }
    private record Result(List<Item> items) { }
    private static String hex(byte[] data, int start, int end) { StringBuilder out = new StringBuilder((end-start)*2); for(int i=start;i<end;i++) out.append(String.format("%02X",data[i])); return out.toString(); }
}
