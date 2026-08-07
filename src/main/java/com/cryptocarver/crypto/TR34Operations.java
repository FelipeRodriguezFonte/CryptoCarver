package com.cryptocarver.crypto;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Laboratory-grade RSA remote key distribution, inspired by ANSI X9 TR-34 — the RSA sibling of
 * TR-31 used to bootstrap the very first symmetric channel to a terminal/HSM, before any shared
 * key exists to protect a TR-31 key block. The sender signs the key (CMS SignedData) and then
 * envelopes the signed result for the receiver (CMS EnvelopedData, RSA key transport) — the same
 * sign-then-encrypt shape TR-34 uses, built entirely on {@link CMSOperations}, which already
 * handles both halves and is already tested.
 *
 * <p>Both the <b>one-pass</b> profile ({@link #distributeKey}/{@link #receiveKey}) and the
 * <b>two-pass, binding-nonce</b> profile ({@link #distributeKeyTwoPass}/{@link #receiveKeyTwoPass})
 * are available. Two-pass adds replay/freshness protection: the receiver (KRD) generates a
 * challenge nonce first, the sender (KDH) binds its response to that nonce as a signed attribute,
 * and the receiver checks the nonce matches before trusting the response is not a captured replay
 * of an older distribution.</p>
 *
 * <p><b>This is not a byte-for-byte ANSI X9 TR-34-2019 implementation.</b> The real standard
 * defines a specific ASN.1 key-block profile and a specific two-pass PDU exchange (KRD identity +
 * random-number tokens); neither wire format is implemented here — the nonce-binding concept above
 * is built on the same generic signed-attribute mechanism as {@link #KEY_ID_ATTRIBUTE_OID}, not a
 * standards-exact TR-34 handshake (no public TR-34 test vectors exist to verify a wire-exact
 * profile against, the same reason EMV Option B is not implemented — see
 * {@code docs/CRYPTOCARVER_ROADMAP_EVOLUCION.md}). This remains a laboratory tool for exploring the
 * mechanism, not something to point at a production KDH/KRD.</p>
 */
public final class TR34Operations {

    /**
     * Private, unregistered OID used to carry an optional key id as a signed (authenticated)
     * CMS attribute — {@link CMSOperations}'s associated-data map requires numeric-OID keys, so
     * this is a fixed placeholder rather than a friendly name like {@code "keyId"}. It is not a
     * real IANA-assigned enterprise OID; it only needs to round-trip within this app.
     */
    public static final String KEY_ID_ATTRIBUTE_OID = "1.3.6.1.4.1.99999.34.1";

    /**
     * Sibling OID to {@link #KEY_ID_ATTRIBUTE_OID}, same "unregistered enterprise OID" pattern —
     * carries the two-pass binding nonce (hex-encoded) as a signed attribute.
     */
    public static final String NONCE_ATTRIBUTE_OID = "1.3.6.1.4.1.99999.34.2";

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private TR34Operations() {
    }

    /**
     * KRD-side: generates a fresh 128-bit challenge nonce. Send this to the KDH out of band (in
     * this lab tool: copy it from the Receive tab into the Distribute tab's binding-nonce field)
     * before the KDH calls {@link #distributeKeyTwoPass}.
     */
    public static byte[] generateChallengeNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return nonce;
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

    /**
     * KDH-side, two-pass profile: like {@link #distributeKey(byte[], X509Certificate, PrivateKey,
     * X509Certificate, String)}, but also binds the response to the KRD's {@code receiverNonce}
     * (from {@link #generateChallengeNonce()}) as a signed attribute, so the receiver can detect a
     * captured, replayed old response via {@link #receiveKeyTwoPass}.
     */
    public static byte[] distributeKeyTwoPass(byte[] keyToDistribute, X509Certificate senderSigningCert,
            PrivateKey senderPrivateKey, X509Certificate receiverCert, byte[] receiverNonce, String keyId)
            throws Exception {
        if (receiverNonce == null || receiverNonce.length == 0) {
            throw new IllegalArgumentException("A binding nonce is required for two-pass distribution");
        }
        Map<String, String> associatedData = new HashMap<>();
        associatedData.put(NONCE_ATTRIBUTE_OID, toHex(receiverNonce));
        if (keyId != null && !keyId.isBlank()) {
            associatedData.put(KEY_ID_ATTRIBUTE_OID, keyId);
        }
        return distributeKey(keyToDistribute, senderSigningCert, senderPrivateKey, receiverCert, associatedData);
    }

    /** Result of receiving a distributed key: the recovered bytes and whether the signature checked out. */
    public static final class ReceivedKey {
        private final byte[] key;
        private final boolean signatureVerified;
        private final Map<String, String> associatedData;
        private final boolean nonceVerified;

        ReceivedKey(byte[] key, boolean signatureVerified, Map<String, String> associatedData) {
            this(key, signatureVerified, associatedData, false);
        }

        ReceivedKey(byte[] key, boolean signatureVerified, Map<String, String> associatedData, boolean nonceVerified) {
            this.key = key;
            this.signatureVerified = signatureVerified;
            this.associatedData = associatedData == null ? Collections.emptyMap() : associatedData;
            this.nonceVerified = nonceVerified;
        }

        public byte[] getKey() { return key; }
        /** False means the CMS decrypted fine but the signature did not verify against {@code expectedSenderCert}. */
        public boolean isSignatureVerified() { return signatureVerified; }
        public Map<String, String> getAssociatedData() { return associatedData; }
        /**
         * Only meaningful on a result from {@link #receiveKeyTwoPass}: {@code true} means the
         * response was bound to the expected challenge nonce (fresh, not a replay). Results from
         * the one-pass {@link #receiveKey} always report {@code false} here — one-pass has no
         * nonce concept, so this is never checked for them, not a claim of failure.
         */
        public boolean isNonceVerified() { return nonceVerified; }
        /** Convenience accessor for the {@link #KEY_ID_ATTRIBUTE_OID} attribute, or {@code null} if absent. */
        public String getKeyId() { return stripBrackets(associatedData.get(KEY_ID_ATTRIBUTE_OID)); }
        /** Convenience accessor for the {@link #NONCE_ATTRIBUTE_OID} attribute, or {@code null} if absent. */
        public String getNonceHex() { return stripBrackets(associatedData.get(NONCE_ATTRIBUTE_OID)); }

        /**
         * {@link CMSOperations}'s attribute extraction returns the raw ASN.1 set's {@code toString()}
         * (e.g. {@code "[kek-001]"}); this strips that bracket wrapping so callers get back exactly
         * what was signed.
         */
        private static String stripBrackets(String raw) {
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

    /**
     * KRD-side, two-pass profile: like {@link #receiveKey}, but additionally checks the response
     * is bound to {@code expectedNonce} (the challenge generated earlier via
     * {@link #generateChallengeNonce()}). A response with a valid signature but a missing or
     * mismatched nonce is exactly the case two-pass exists to catch: a captured, replayed old
     * distribution message — {@link ReceivedKey#isSignatureVerified()} stays {@code true} in that
     * case (the signature really is valid) while {@link ReceivedKey#isNonceVerified()} is
     * {@code false}, so callers must check both, not just the signature.
     */
    public static ReceivedKey receiveKeyTwoPass(byte[] distributed, PrivateKey receiverPrivateKey,
            X509Certificate expectedSenderCert, byte[] expectedNonce) throws Exception {
        if (expectedNonce == null || expectedNonce.length == 0) {
            throw new IllegalArgumentException("The expected challenge nonce is required for two-pass receive");
        }
        ReceivedKey received = receiveKey(distributed, receiverPrivateKey, expectedSenderCert);
        byte[] actualNonce = fromHex(received.getNonceHex());
        boolean nonceVerified = actualNonce != null && MessageDigest.isEqual(actualNonce, expectedNonce);
        return new ReceivedKey(received.getKey(), received.isSignatureVerified(), received.getAssociatedData(), nonceVerified);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
        }
        return sb.toString();
    }

    /** Returns {@code null} for anything that isn't a clean, even-length hex string (never throws). */
    private static byte[] fromHex(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() % 2) != 0) return null;
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }
}
