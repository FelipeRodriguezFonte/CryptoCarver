package com.cryptocarver.crypto;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;

/**
 * Laboratory-grade RSA remote key distribution, inspired by ANSI X9 TR-34 — the RSA sibling of
 * TR-31 used to bootstrap the very first symmetric channel to a terminal/HSM, before any shared
 * key exists to protect a TR-31 key block. The sender signs the key (CMS SignedData) and then
 * envelopes the signed result for the receiver (CMS EnvelopedData, RSA key transport) — the same
 * sign-then-encrypt shape TR-34 uses, built entirely on {@link CMSOperations}, which already
 * handles both halves and is already tested.
 *
 * <p><b>This is not a byte-for-byte ANSI X9 TR-34-2019 implementation.</b> The real standard
 * defines a specific ASN.1 key-block profile and a two-pass binding-nonce handshake to prevent
 * replay; neither is implemented here (no public TR-34 test vectors exist to verify a wire-exact
 * profile against, the same reason EMV Option B is not implemented — see
 * {@code docs/CRYPTOCARVER_ROADMAP_EVOLUCION.md}). Only the one-pass shape is provided: it is a
 * laboratory tool for exploring the mechanism, not something to point at a production KDH/KRD.</p>
 */
public final class TR34Operations {

    /**
     * Private, unregistered OID used to carry an optional key id as a signed (authenticated)
     * CMS attribute — {@link CMSOperations}'s associated-data map requires numeric-OID keys, so
     * this is a fixed placeholder rather than a friendly name like {@code "keyId"}. It is not a
     * real IANA-assigned enterprise OID; it only needs to round-trip within this app.
     */
    public static final String KEY_ID_ATTRIBUTE_OID = "1.3.6.1.4.1.99999.34.1";

    private TR34Operations() {
    }

    /** Convenience overload: carries {@code keyId} as a signed attribute, or omits it if blank/null. */
    public static byte[] distributeKey(byte[] keyToDistribute, X509Certificate senderSigningCert,
            PrivateKey senderPrivateKey, X509Certificate receiverCert, String keyId) throws Exception {
        Map<String, String> associatedData = (keyId == null || keyId.isBlank())
                ? null : Map.of(KEY_ID_ATTRIBUTE_OID, keyId);
        return distributeKey(keyToDistribute, senderSigningCert, senderPrivateKey, receiverCert, associatedData);
    }

    /**
     * Signs {@code keyToDistribute} as the sender, then envelopes the result for the receiver.
     *
     * @param associatedData signed (authenticated) attributes, keyed by numeric OID string (see
     *                        {@link CMSOperations}) — may be {@code null}. Most callers want the
     *                        {@code String keyId} overload instead of building this map by hand.
     */
    public static byte[] distributeKey(byte[] keyToDistribute, X509Certificate senderSigningCert,
            PrivateKey senderPrivateKey, X509Certificate receiverCert,
            Map<String, String> associatedData) throws Exception {
        if (keyToDistribute == null || keyToDistribute.length == 0) {
            throw new IllegalArgumentException("There is no key material to distribute");
        }
        if (senderSigningCert == null || senderPrivateKey == null) {
            throw new IllegalArgumentException("The sender's signing certificate and private key are required");
        }
        if (receiverCert == null) {
            throw new IllegalArgumentException("The receiver's certificate is required");
        }
        byte[] signed = CMSOperations.generateSignedData(keyToDistribute, senderSigningCert, senderPrivateKey,
                associatedData, false);
        return CMSOperations.generateEnvelopedData(signed, receiverCert);
    }

    /** Result of receiving a distributed key: the recovered bytes and whether the signature checked out. */
    public static final class ReceivedKey {
        private final byte[] key;
        private final boolean signatureVerified;
        private final Map<String, String> associatedData;

        ReceivedKey(byte[] key, boolean signatureVerified, Map<String, String> associatedData) {
            this.key = key;
            this.signatureVerified = signatureVerified;
            this.associatedData = associatedData == null ? Collections.emptyMap() : associatedData;
        }

        public byte[] getKey() { return key; }
        /** False means the CMS decrypted fine but the signature did not verify against {@code expectedSenderCert}. */
        public boolean isSignatureVerified() { return signatureVerified; }
        public Map<String, String> getAssociatedData() { return associatedData; }
        /**
         * Convenience accessor for the {@link #KEY_ID_ATTRIBUTE_OID} attribute, or {@code null} if
         * absent. {@link CMSOperations}'s attribute extraction returns the raw ASN.1 set's
         * {@code toString()} (e.g. {@code "[kek-001]"}); this strips that bracket wrapping so
         * callers get back exactly what {@link #distributeKey(byte[], X509Certificate, PrivateKey,
         * X509Certificate, String)} was given.
         */
        public String getKeyId() {
            String raw = associatedData.get(KEY_ID_ATTRIBUTE_OID);
            if (raw == null) return null;
            if (raw.length() >= 2 && raw.charAt(0) == '[' && raw.charAt(raw.length() - 1) == ']') {
                return raw.substring(1, raw.length() - 1);
            }
            return raw;
        }
    }

    /**
     * Decrypts {@code distributed} as the receiver, then verifies the inner signature against
     * {@code expectedSenderCert} — a certificate the caller already trusts out of band, not one
     * extracted from the message itself. A mismatched or tampered signature yields a
     * {@link ReceivedKey} with {@code signatureVerified=false} rather than an exception, exactly
     * like {@link CMSOperations#verifySignedData}.
     */
    public static ReceivedKey receiveKey(byte[] distributed, PrivateKey receiverPrivateKey,
            X509Certificate expectedSenderCert) throws Exception {
        if (distributed == null || distributed.length == 0) {
            throw new IllegalArgumentException("There is no distributed data to receive");
        }
        if (receiverPrivateKey == null) {
            throw new IllegalArgumentException("The receiver's private key is required");
        }
        if (expectedSenderCert == null) {
            throw new IllegalArgumentException("The expected sender certificate is required to verify the signature");
        }
        byte[] signed = CMSOperations.decryptEnvelopedData(distributed, receiverPrivateKey);
        CMSOperations.VerificationResult verification = CMSOperations.verifySignedData(signed, expectedSenderCert);
        return new ReceivedKey(verification.content, verification.verified, verification.associatedData);
    }
}
