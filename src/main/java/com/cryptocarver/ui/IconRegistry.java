package com.cryptocarver.ui;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignK;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;
import org.kordamp.ikonli.materialdesign2.MaterialDesignX;

import java.util.Map;

/**
 * The single UI icon vocabulary.  Icon names are stable semantic values, so
 * navigation/model code never needs to know about a font or a glyph.
 */
public final class IconRegistry {
    private IconRegistry() { }

    private static final Map<String, Ikon> ICONS = Map.ofEntries(
            Map.entry("search", MaterialDesignM.MAGNIFY),
            Map.entry("processDesigner", MaterialDesignS.SITEMAP),
            Map.entry("generic", MaterialDesignW.WRENCH),
            Map.entry("cipher", MaterialDesignL.LOCK),
            Map.entry("authentication", MaterialDesignS.SHIELD_CHECK),
            Map.entry("keys", MaterialDesignK.KEY_VARIANT),
            Map.entry("postQuantum", MaterialDesignA.ATOM),
            Map.entry("xmlSecurity", MaterialDesignX.XML),
            Map.entry("certificates", MaterialDesignC.CERTIFICATE),
            Map.entry("jose", MaterialDesignW.WEB),
            Map.entry("cose", MaterialDesignP.PACKAGE_VARIANT_CLOSED),
            Map.entry("payments", MaterialDesignC.CREDIT_CARD_OUTLINE),
            Map.entry("asn1", MaterialDesignC.CODE_JSON),
            Map.entry("history", MaterialDesignH.HISTORY),
            Map.entry("warning", MaterialDesignA.ALERT_OUTLINE),
            Map.entry("clipboard", MaterialDesignC.CLIPBOARD_TEXT_OUTLINE),
            Map.entry("close", MaterialDesignC.CLOSE),
            Map.entry("favorite", MaterialDesignS.STAR_OUTLINE),
            Map.entry("default", MaterialDesignC.CUBE_OUTLINE)
    );

    public static FontIcon icon(String semanticName) {
        Ikon ikon = ICONS.getOrDefault(normalize(semanticName), ICONS.get("default"));
        FontIcon icon = new FontIcon(ikon);
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
