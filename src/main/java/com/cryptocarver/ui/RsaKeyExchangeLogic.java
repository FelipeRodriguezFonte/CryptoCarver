package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.RsaKeyWrapOperations;
import com.cryptocarver.model.CryptoEnvelope;
import com.cryptocarver.model.CryptoEnvelopeCodec;
import com.cryptocarver.util.DataConverter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/** Input processing and report formatting without JavaFX or module controller references. */
final class RsaKeyExchangeLogic {
    private RsaKeyExchangeLogic() { }

    record ExportResult(byte[] keyToWrap, String keyHex, RsaKeyWrapOperations.WrapProfile profile, RsaKeyWrapOperations.WrapResult wrapResult, String outputText, String report) { }

    static ExportResult export(String pemText, String keyText, String profileText, boolean asEnvelope, String kidText, String versionText) throws Exception {
        String pem = pemText.trim();
        String keyHex = keyText.trim().replaceAll("\\s+", "");

        if (pem.isEmpty() || keyHex.isEmpty()) {
            throw new KeyDistributionValidation("module.keys.rsaKex.required", pem.isEmpty() ? "rsaKexRecipientPemArea" : "rsaKexKeyToWrapField");
        }
        if (!keyHex.matches("[0-9A-Fa-f]+")) {
            throw new KeyDistributionValidation("module.keys.rsaKex.keyInvalid", "rsaKexKeyToWrapField");
        }

        byte[] keyToWrap = DataConverter.hexToBytes(keyHex);

        PublicKey publicKey;
        X509Certificate certificate = null;
        if (pem.contains("BEGIN CERTIFICATE")) {
            java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
            certificate = (X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            publicKey = certificate.getPublicKey();
        } else if (pem.contains("BEGIN PUBLIC KEY")) {
            publicKey = AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
        } else {
            throw new KeyDistributionValidation("module.keys.rsaKex.pemUnrecognized", "rsaKexRecipientPemArea");
        }

        RsaKeyWrapOperations.WrapProfile profile = rsaKexProfileFromCombo(profileText);
        RsaKeyWrapOperations.WrapResult wrapResult = RsaKeyWrapOperations.wrap(keyToWrap, publicKey, certificate, profile);

        String outputText;
        if (asEnvelope) {
            CryptoEnvelope.Builder builder = CryptoEnvelope.forAlgorithm(wrapResult.getAlgorithm())
                    .ciphertext(wrapResult.getWrapped())
                    .kcv(wrapResult.getKcvHex())
                    .extension("profile", profile.name());
            String kid = kidText.trim();
            if (!kid.isEmpty()) builder.kid(kid);
            String keyVersionText = versionText.trim();
            if (!keyVersionText.isEmpty()) {
                try {
                    builder.keyVersion(Integer.parseInt(keyVersionText));
                } catch (NumberFormatException e) {
                    throw new KeyDistributionValidation("module.keys.rsaKex.keyVersionInvalid", "rsaKexKeyVersionField");
                }
            }
            outputText = CryptoEnvelopeCodec.serializeCompact(builder.build());
        } else if (profile == RsaKeyWrapOperations.WrapProfile.JWE_COMPACT) {
            outputText = new String(wrapResult.getWrapped(), StandardCharsets.US_ASCII);
        } else {
            outputText = java.util.Base64.getEncoder().encodeToString(wrapResult.getWrapped());
        }

        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("RSA KEY EXCHANGE — EXPORT\n");
        result.append("========================================\n\n");
        result.append("Profile:        ").append(profile).append("\n");
        result.append("Algorithm:      ").append(wrapResult.getAlgorithm()).append("\n");
        if (wrapResult.getKcvHex() != null) {
            result.append("Key KCV:        ").append(wrapResult.getKcvHex()).append("\n");
        }
        result.append("Envelope:       ").append(asEnvelope ? "yes (compact)" : "no").append("\n\n");
        result.append("OUTPUT:\n");
        result.append("------------------\n");
        result.append(outputText).append("\n");
        result.append("\n========================================\n");
        return new ExportResult(keyToWrap, keyHex, profile, wrapResult, outputText, result.toString());
    }

    record ImportResult(byte[] wrapped, byte[] recovered, String recoveredHex, RsaKeyWrapOperations.WrapProfile profile, String report) { }

    static ImportResult importKey(String privateText, String wrappedInput, String profileText) throws Exception {
        String privatePem = privateText.trim();
        String wrappedText = wrappedInput.trim();

        if (privatePem.isEmpty() || wrappedText.isEmpty()) {
            throw new KeyDistributionValidation("module.keys.rsaKex.importRequired", privatePem.isEmpty() ? "rsaKexPrivateKeyArea" : "rsaKexWrappedDataArea");
        }

        PrivateKey privateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privatePem);

        byte[] wrapped;
        RsaKeyWrapOperations.WrapProfile profile;
        CryptoEnvelope envelope = null;
        if (CryptoEnvelopeCodec.looksLikeEnvelope(wrappedText)) {
            envelope = CryptoEnvelopeCodec.deserializeAuto(wrappedText);
            wrapped = java.util.Base64.getDecoder().decode(envelope.getCiphertextB64());
            String profileExt = envelope.getExtensions().get("profile");
            profile = profileExt != null
                    ? RsaKeyWrapOperations.WrapProfile.valueOf(profileExt)
                    : rsaKexProfileFromCombo(profileText);
        } else {
            profile = rsaKexProfileFromCombo(profileText);
            wrapped = profile == RsaKeyWrapOperations.WrapProfile.JWE_COMPACT
                    ? wrappedText.getBytes(StandardCharsets.US_ASCII)
                    : java.util.Base64.getDecoder().decode(wrappedText);
        }

        byte[] recovered = RsaKeyWrapOperations.unwrap(wrapped, privateKey, profile);
        String recoveredHex = DataConverter.bytesToHex(recovered);
        String recoveredKcv = null;
        if (recovered.length == 16 || recovered.length == 24 || recovered.length == 32) {
            try {
                recoveredKcv = DataConverter.bytesToHex(KeyOperations.calculateKCV_AES(recovered));
            } catch (Exception ignored) {
                // Best-effort only — KCV is a convenience cross-check, not required.
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("RSA KEY EXCHANGE — IMPORT\n");
        result.append("========================================\n\n");
        result.append("Profile:        ").append(profile).append("\n");
        if (envelope != null) {
            result.append("Envelope:       yes\n");
            result.append("Algorithm:      ").append(envelope.getAlg()).append("\n");
            if (envelope.getKid() != null) result.append("Key ID:         ").append(envelope.getKid()).append("\n");
            if (envelope.getKeyVersion() != null) result.append("Key Version:    ").append(envelope.getKeyVersion()).append("\n");
            if (envelope.getKcv() != null) {
                boolean matches = recoveredKcv != null && recoveredKcv.equalsIgnoreCase(envelope.getKcv());
                result.append("Envelope KCV:   ").append(envelope.getKcv())
                        .append(matches ? "  (matches recovered key)" : "  (!) does not match the recovered key's KCV")
                        .append("\n");
            }
        } else {
            result.append("Envelope:       no\n");
        }
        result.append("\nUNWRAPPED KEY:\n");
        result.append("------------------\n");
        result.append(recoveredHex.toUpperCase()).append("\n");
        if (recoveredKcv != null) result.append("Recovered KCV:  ").append(recoveredKcv).append("\n");
        result.append("Key Length:     ").append(recovered.length).append(" bytes\n");
        result.append("\n========================================\n");
        return new ImportResult(wrapped, recovered, recoveredHex, profile, result.toString());
    }

    private static RsaKeyWrapOperations.WrapProfile rsaKexProfileFromCombo(String value) {
        if (value == null) return RsaKeyWrapOperations.WrapProfile.RAW_OAEP;
        return switch (value) {
            case "JWE Compact" -> RsaKeyWrapOperations.WrapProfile.JWE_COMPACT;
            case "CMS EnvelopedData" -> RsaKeyWrapOperations.WrapProfile.CMS_ENVELOPED;
            default -> RsaKeyWrapOperations.WrapProfile.RAW_OAEP;
        };
    }
}
