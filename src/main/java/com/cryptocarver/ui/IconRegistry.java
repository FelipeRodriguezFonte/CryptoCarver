package com.cryptocarver.ui;

import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;

/**
 * The single UI icon vocabulary.  Icon names are stable semantic values, so
 * navigation/model code never needs to know about a font or a glyph.
 */
public final class IconRegistry {
    private IconRegistry() { }

    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("search", "mdi2m-magnify"),
            Map.entry("processDesigner", "mdi2s-sitemap"),
            Map.entry("generic", "mdi2w-wrench"),
            Map.entry("cipher", "mdi2l-lock"),
            Map.entry("authentication", "mdi2s-shield-check"),
            Map.entry("keys", "mdi2k-key-variant"),
            Map.entry("postQuantum", "mdi2a-atom"),
            Map.entry("xmlSecurity", "mdi2x-xml"),
            Map.entry("certificates", "mdi2c-certificate"),
            Map.entry("jose", "mdi2w-web"),
            Map.entry("cose", "mdi2p-package-variant-closed"),
            Map.entry("payments", "mdi2c-credit-card-outline"),
            Map.entry("asn1", "mdi2c-code-json"),
            Map.entry("history", "mdi2h-history"),
            Map.entry("warning", "mdi2a-alert-outline"),
            Map.entry("clipboard", "mdi2c-clipboard-text-outline"),
            Map.entry("close", "mdi2c-close"),
            Map.entry("favorite", "mdi2s-star-outline"),
            Map.entry("default", "mdi2c-cube-outline")
    );

    public static FontIcon icon(String semanticName) {
        String literal = ICONS.getOrDefault(normalize(semanticName), ICONS.get("default"));
        FontIcon icon = new FontIcon(literal);
        icon.getStyleClass().add("cc-icon");
        icon.setIconSize(16);
        return icon;
    }

    /** Maps legacy operation icon values to a stable semantic icon. */
    public static FontIcon operation(String operationId, String legacyValue) {
        String id = operationId == null ? "" : operationId.toLowerCase();
        String key = id.contains("hash") ? "generic" : id.contains("pin") || id.contains("payment") ? "payments"
                : id.contains("xml") || id.contains("wss") ? "xmlSecurity" : id.contains("cert") ? "certificates"
                : id.contains("key") || id.contains("kdf") || id.contains("tr31") ? "keys"
                : id.contains("mac") || id.contains("sig") ? "authentication" : normalize(legacyValue);
        return icon(key);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "default";
        return switch (value) {
            case "🔍", "search" -> "search";
            case "🧩", "processDesigner" -> "processDesigner";
            case "🔒", "cipher" -> "cipher";
            case "🛡", "authentication" -> "authentication";
            case "🔑", "keys" -> "keys";
            case "⚛", "postQuantum" -> "postQuantum";
            case "📝", "xmlSecurity" -> "xmlSecurity";
            case "📜", "certificates" -> "certificates";
            case "🌐", "jose" -> "jose";
            case "📦", "cose" -> "cose";
            case "💳", "payments" -> "payments";
            case "{}", "asn1" -> "asn1";
            case "⏱", "history" -> "history";
            default -> value;
        };
    }
}
