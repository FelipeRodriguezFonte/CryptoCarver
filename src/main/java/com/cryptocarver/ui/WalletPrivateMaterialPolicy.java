package com.cryptocarver.ui;

import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.SdJwtOperations;
import com.cryptocarver.model.AppSettings;

/**
 * Wallet rule for reports derived from a pasted structure: outside FULL_LAB, a report whose
 * source or text carries private key material is replaced by a notice before it is shown or
 * published. Detection is delegated to {@link PrivateKeyMaterialDetector}.
 */
final class WalletPrivateMaterialPolicy {
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
