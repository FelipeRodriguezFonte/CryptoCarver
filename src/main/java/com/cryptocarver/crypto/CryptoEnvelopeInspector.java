package com.cryptocarver.crypto;

import com.cryptocarver.model.CryptoEnvelope;
import com.cryptocarver.model.CryptoEnvelopeCodec;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Read-only decoding of a {@link CryptoEnvelope} for the Crypto Envelope Inspector screen —
 * the envelope counterpart to {@link CmsInspector}. Deliberately does not attempt to decrypt;
 * unwrapping the ciphertext is a separate, explicit action that needs the recipient's private key
 * (see {@link RsaKeyWrapOperations#unwrap}).
 */
public final class CryptoEnvelopeInspector {

    private CryptoEnvelopeInspector() {
    }

    public static final class InspectionResult {
        private final CryptoEnvelope envelope;
        private final int ciphertextLengthBytes;
        private final Duration age;

        InspectionResult(CryptoEnvelope envelope, int ciphertextLengthBytes, Duration age) {
            this.envelope = envelope;
            this.ciphertextLengthBytes = ciphertextLengthBytes;
            this.age = age;
        }

        public CryptoEnvelope getEnvelope() { return envelope; }
        public int getCiphertextLengthBytes() { return ciphertextLengthBytes; }
        /** Time elapsed since {@code createdAt}, or {@code null} if the envelope has no timestamp. */
        public Duration getAge() { return age; }
    }

    /**
     * Parses pasted text (compact or JSON profile, auto-detected) into an {@link InspectionResult}.
     *
     * @throws IllegalArgumentException if the text is not a recognizable, valid envelope
     */
    public static InspectionResult inspect(String pastedText) {
        CryptoEnvelope envelope = CryptoEnvelopeCodec.deserializeAuto(pastedText);
        int ciphertextLength = Base64.getDecoder().decode(envelope.getCiphertextB64()).length;
        Duration age = null;
        if (envelope.getCreatedAt() != null) {
            try {
                age = Duration.between(Instant.parse(envelope.getCreatedAt()), Instant.now());
            } catch (DateTimeParseException ignored) {
                // Already validated by the codec at deserialization time; defensive only.
            }
        }
        return new InspectionResult(envelope, ciphertextLength, age);
    }
}
