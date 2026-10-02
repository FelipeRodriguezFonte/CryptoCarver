package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.TR34Operations;
import com.cryptocarver.model.CryptoEnvelope;
import com.cryptocarver.model.CryptoEnvelopeCodec;
import com.cryptocarver.util.DataConverter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/** Input processing and report formatting without JavaFX or module controller references. */
final class Tr34Logic {
    private Tr34Logic() { }

    record DistributeResult(byte[] keyToDistribute, String keyHex, String keyId, String bindingNonceHex, boolean twoPass, String outputText, String report) { }

    static DistributeResult distribute(String senderPrivateText, String senderCertText, String receiverCertText, String keyText, String keyIdText, String bindingText, boolean asEnvelope) throws Exception {
        String privatePem = senderPrivateText.trim();
        String senderCertPem = senderCertText.trim();
        String receiverCertPem = receiverCertText.trim();
        String keyHex = keyText.trim().replaceAll("\\s+", "");

        if (privatePem.isEmpty() || senderCertPem.isEmpty() || receiverCertPem.isEmpty() || keyHex.isEmpty()) {
            throw new KeyDistributionValidation("module.keys.tr34.required", privatePem.isEmpty() ? "tr34SenderPrivateKeyArea"
                            : senderCertPem.isEmpty() ? "tr34SenderCertArea"
                            : receiverCertPem.isEmpty() ? "tr34ReceiverCertArea" : "tr34KeyToDistributeField");
        }
        if (!keyHex.matches("[0-9A-Fa-f]+")) {
            throw new KeyDistributionValidation("module.keys.tr34.keyInvalid", "tr34KeyToDistributeField");
        }

        byte[] keyToDistribute = DataConverter.hexToBytes(keyHex);
        PrivateKey senderPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privatePem);
        X509Certificate senderCert = parseCertificatePem(senderCertPem);
        X509Certificate receiverCert = parseCertificatePem(receiverCertPem);
        String keyId = keyIdText.trim();
        String bindingNonceHex = bindingText.trim().replaceAll("\\s+", "");
        if (!bindingNonceHex.isEmpty() && !bindingNonceHex.matches("[0-9A-Fa-f]+")) {
            throw new KeyDistributionValidation("module.keys.tr34.keyInvalid", "tr34BindingNonceField");
        }
        boolean twoPass = !bindingNonceHex.isEmpty();

        byte[] distributed = twoPass
                ? TR34Operations.distributeKeyTwoPass(keyToDistribute, senderCert, senderPrivateKey,
                        receiverCert, DataConverter.hexToBytes(bindingNonceHex), keyId.isEmpty() ? null : keyId)
                : TR34Operations.distributeKey(keyToDistribute, senderCert, senderPrivateKey,
                        receiverCert, keyId.isEmpty() ? null : keyId);

        String outputText;
        if (asEnvelope) {
            CryptoEnvelope.Builder builder = CryptoEnvelope.forAlgorithm("TR34-CMS")
                    .ciphertext(distributed)
                    .kcv(tr34KcvIfEligible(keyToDistribute));
            if (!keyId.isEmpty()) builder.kid(keyId);
            outputText = CryptoEnvelopeCodec.serializeCompact(builder.build());
        } else {
            outputText = java.util.Base64.getEncoder().encodeToString(distributed);
        }

        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("TR-34 KEY DISTRIBUTION — DISTRIBUTE\n");
        result.append("========================================\n\n");
        if (!keyId.isEmpty()) result.append("Key ID:         ").append(keyId).append("\n");
        result.append("Profile:        ").append(twoPass ? "two-pass (bound to nonce " + bindingNonceHex.toUpperCase() + ")" : "one-pass").append("\n");
        result.append("Envelope:       ").append(asEnvelope ? "yes (compact)" : "no").append("\n\n");
        result.append("OUTPUT:\n");
        result.append("------------------\n");
        result.append(outputText).append("\n");
        result.append("\n========================================\n");
        return new DistributeResult(keyToDistribute, keyHex, keyId, bindingNonceHex, twoPass, outputText, result.toString());
    }

    record ReceiveResult(byte[] distributed, TR34Operations.ReceivedKey received, String recoveredHex, boolean twoPass, String report) { }

    static ReceiveResult receive(String privateText, String senderCertText, String distributedInput, String challengeText) throws Exception {
        String privatePem = privateText.trim();
        String expectedSenderCertPem = senderCertText.trim();
        String distributedText = distributedInput.trim();

        if (privatePem.isEmpty() || expectedSenderCertPem.isEmpty() || distributedText.isEmpty()) {
            throw new KeyDistributionValidation("module.keys.tr34.receiveRequired", privatePem.isEmpty() ? "tr34ReceiverPrivateKeyArea"
                            : expectedSenderCertPem.isEmpty() ? "tr34ExpectedSenderCertArea" : "tr34DistributedDataArea");
        }

        PrivateKey receiverPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privatePem);
        X509Certificate expectedSenderCert = parseCertificatePem(expectedSenderCertPem);

        byte[] distributed;
        CryptoEnvelope envelope = null;
        if (CryptoEnvelopeCodec.looksLikeEnvelope(distributedText)) {
            envelope = CryptoEnvelopeCodec.deserializeAuto(distributedText);
            distributed = java.util.Base64.getDecoder().decode(envelope.getCiphertextB64());
        } else {
            distributed = java.util.Base64.getDecoder().decode(distributedText);
        }

        String challengeNonceHex = challengeText.trim().replaceAll("\\s+", "");
        if (!challengeNonceHex.isEmpty() && !challengeNonceHex.matches("[0-9A-Fa-f]+")) {
            throw new KeyDistributionValidation("module.keys.tr34.keyInvalid", "tr34ChallengeNonceField");
        }
        boolean twoPass = !challengeNonceHex.isEmpty();

        TR34Operations.ReceivedKey received = twoPass
                ? TR34Operations.receiveKeyTwoPass(distributed, receiverPrivateKey, expectedSenderCert,
                        DataConverter.hexToBytes(challengeNonceHex))
                : TR34Operations.receiveKey(distributed, receiverPrivateKey, expectedSenderCert);
        String recoveredHex = DataConverter.bytesToHex(received.getKey());

        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("TR-34 KEY DISTRIBUTION — RECEIVE\n");
        result.append("========================================\n\n");
        result.append("Signature Verified: ").append(received.isSignatureVerified() ? "YES" : "NO — do not trust this key").append("\n");
        if (twoPass) {
            result.append("Nonce Verified:      ").append(received.isNonceVerified()
                    ? "YES" : "NO — possible replay of an old distribution, or wrong challenge").append("\n");
        }
        String keyId = received.getKeyId();
        if (keyId != null) result.append("Key ID (authenticated): ").append(keyId).append("\n");
        if (envelope != null) {
            result.append("Envelope KCV:        ").append(envelope.getKcv() == null ? "-" : envelope.getKcv()).append("\n");
        }
        result.append("\nRECOVERED KEY:\n");
        result.append("------------------\n");
        result.append(recoveredHex.toUpperCase()).append("\n");
        result.append("Key Length:     ").append(received.getKey().length).append(" bytes\n");
        result.append("\n========================================\n");
        return new ReceiveResult(distributed, received, recoveredHex, twoPass, result.toString());
    }

    private static X509Certificate parseCertificatePem(String pem) throws Exception {
        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String tr34KcvIfEligible(byte[] keyMaterial) {
        if (keyMaterial.length != 16 && keyMaterial.length != 24 && keyMaterial.length != 32) {
            return null;
        }
        try {
            return DataConverter.bytesToHex(KeyOperations.calculateKCV_AES(keyMaterial));
        } catch (Exception e) {
            return null;
        }
    }
}
