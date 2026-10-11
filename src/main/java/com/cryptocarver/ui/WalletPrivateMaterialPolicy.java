package com.cryptocarver.ui;

import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.SdJwtOperations;
import com.cryptocarver.model.AppSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;

import java.math.BigDecimal;
import java.util.ArrayDeque;

/**
 * Wallet rule for reports derived from a pasted structure: outside FULL_LAB, a report whose
 * source or text carries private key material is replaced by a notice before it is shown or
 * published. JWK/PEM detection is delegated to {@link PrivateKeyMaterialDetector}; COSE
 * integer labels in CBOR-derived JSON are checked here, only for the CBOR handlers.
 */
final class WalletPrivateMaterialPolicy {
    private static final Gson STRICT_JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private WalletPrivateMaterialPolicy() { }

    /** Returns {@code report}, or the localized notice when it or any source carries private material. */
    static String forDisplay(String report, String... sources) {
        if (AppSettings.isFullLab()) return report;
        boolean privateMaterial = PrivateKeyMaterialDetector.containsPrivateMaterial(report, 0);
        for (int i = 0; !privateMaterial && i < sources.length; i++) {
            privateMaterial = PrivateKeyMaterialDetector.containsPrivateMaterial(sources[i], 0);
        }
        return privateMaterial
                ? com.cryptocarver.service.I18nService.getInstance().text("module.wallet.privateJwkHidden")
                : report;
    }

    /** Applies the Wallet COSE check as well as the existing JWK/PEM policy to a CBOR report. */
    static String forCborDisplay(String report, String json) {
        if (!AppSettings.isFullLab() && containsPrivateCoseKeyJson(json)) {
            return com.cryptocarver.service.I18nService.getInstance().text("module.wallet.privateJwkHidden");
        }
        return forDisplay(report, json);
    }

    /**
     * Searches objects and arrays without a traversal depth limit. Invalid JSON is not private.
     * Byte strings (including tag-24 embedded CBOR) remain base64url strings in this JSON and
     * are deliberately not decoded: this check cannot detect a COSE key inside them.
     */
    static boolean containsPrivateCoseKeyJson(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            JsonElement root = STRICT_JSON.fromJson(json, JsonElement.class);
            if (root == null) return false;
            ArrayDeque<JsonElement> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                JsonElement value = pending.pop();
                if (value.isJsonObject()) {
                    var object = value.getAsJsonObject();
                    if (object.has("1") && (object.has("-4")
                            || (object.has("-1") && isSymmetricKty(object.get("1"))))) return true;
                    for (var entry : object.entrySet()) pending.push(entry.getValue());
                } else if (value.isJsonArray()) {
                    for (JsonElement item : value.getAsJsonArray()) pending.push(item);
                }
            }
        } catch (RuntimeException invalidJson) {
            return false;
        }
        return false;
    }

    private static boolean isSymmetricKty(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try {
            return value.getAsBigDecimal().compareTo(BigDecimal.valueOf(4)) == 0;
        } catch (NumberFormatException notRepresentable) {
            return false;
        }
    }

    /** JSON rendering of a CBOR item, so that keys carried in CBOR maps are inspected like JSON ones. */
    static String cborAsJson(byte[] cbor) {
        try {
            return CborInspector.toJson(cbor);
        } catch (RuntimeException notRepresentable) {
            return null;
        }
    }

    /** Header, payload, disclosures and key binding claims of an SD-JWT presentation, as one JSON object. */
    static String sdJwtAsJson(String presentation) {
        try {
            SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(presentation);
            StringBuilder json = new StringBuilder("{\"parts\":[").append(parsed.header()).append(',').append(parsed.payload());
            if (parsed.keyBindingClaims() != null) json.append(',').append(parsed.keyBindingClaims());
            for (SdJwtOperations.Disclosure disclosure : parsed.disclosures()) {
                json.append(',').append(disclosure.value());
            }
            return json.append("]}").toString();
        } catch (Exception notAnSdJwt) {
            return null;
        }
    }
}
