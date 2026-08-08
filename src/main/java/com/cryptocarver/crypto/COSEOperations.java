package com.cryptocarver.crypto;

import COSE.AlgorithmID;
import COSE.Attribute;
import COSE.CoseException;
import COSE.Encrypt0Message;
import COSE.HeaderKeys;
import COSE.MAC0Message;
import COSE.Message;
import COSE.MessageTag;
import COSE.OneKey;
import COSE.Sign1Message;

import COSE.KeyKeys;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * COSE (RFC 9052/9053) — the CBOR sibling of JOSE. Built on the reference
 * {@code cose-java} library (Jim Schaad, an RFC 9052 co-author), itself built
 * on BouncyCastle — already a dependency of this app. Same crypto-agility
 * idea as JOSE (an algorithm identifier travels with the message), just with
 * binary CBOR framing instead of JSON/base64url; it's the format behind
 * WebAuthn/FIDO2, EAT, mDL (ISO 18013-5) and C2PA.
 *
 * <p><b>MVP profile only</b>, matching what JOSE Compact already covers in
 * this app: {@code COSE_Sign1} (single signer), {@code COSE_Mac0} (symmetric
 * MAC), {@code COSE_Encrypt0} (single-key encryption). Multi-recipient
 * {@code COSE_Sign}/{@code COSE_Encrypt} are out of scope, the same way
 * JWS/JWE JSON General are not implemented for JOSE today.</p>
 *
 * <p><b>EdDSA/Ed25519 support requires two workarounds, both verified against
 * the library's decompiled source and proven with a standalone reproduction
 * before being written here — not assumed.</b> {@code OneKey(PublicKey,
 * PrivateKey)} parses the key's own SPKI/PKCS8 ASN.1 and only recognizes EC
 * (P-256/P-384/P-521) and RSA — there is no code path for Ed25519/OKP keys,
 * so {@link #sign1}/{@link #verify1} build the OKP {@code OneKey} by hand
 * from the key's raw 32-byte material for {@link SignAlgorithm#EDDSA}
 * (extracted as the last 32 bytes of {@link java.security.Key#getEncoded()},
 * which is fixed-size and standardized for Ed25519 (RFC 8410) regardless of
 * which provider produced the key). Separately, the library's EdDSA
 * signing/verification and key-reconstruction paths hardcode a JCA provider
 * literally named {@code "EdDSA"} (from the old, unmaintained
 * {@code net.i2p.crypto.eddsa} project) that this app never registered —
 * {@link EdDsaProviderShim} registers a provider under that exact name that
 * delegates to the JDK's own built-in Ed25519 implementation (JEP 339, JDK
 * 15+; BouncyCastle 1.78.1 was checked and does not expose an Ed25519/EdDSA
 * {@code Signature} service, only {@code KeyFactory}, so BC alone would not
 * have been enough here).</p>
 */
public final class COSEOperations {

    private COSEOperations() {
    }

    /**
     * Raw 32-byte Ed25519 private key seed. <b>Not</b> "last 32 bytes of the PKCS8 encoding" —
     * confirmed empirically that this breaks for keys BouncyCastle generates itself (as opposed to
     * ones it merely re-parses): BC's own {@code KeyPairGenerator.getInstance("Ed25519", "BC")}
     * embeds the *public* key in an optional PKCS8 attribute trailing the private key, making the
     * encoding 83 bytes instead of the plain 48 — so its last 32 bytes are the public key, not the
     * private seed. Uses {@link EdECPrivateKey#getBytes()} instead, which every key object tested
     * here — the JDK's own and BC 1.78.1's {@code BC15EdDSAPrivateKey} alike — already implements
     * directly; a translate-through-SunEC fallback covers any provider that doesn't.
     */
    private static byte[] rawEd25519PrivateBytes(PrivateKey key) throws CoseException {
        try {
            java.security.interfaces.EdECPrivateKey edKey = key instanceof java.security.interfaces.EdECPrivateKey k
                    ? k
                    : (java.security.interfaces.EdECPrivateKey) java.security.KeyFactory.getInstance("Ed25519", "SunEC").translateKey(key);
            return edKey.getBytes().orElseThrow(
                    () -> new CoseException("This Ed25519 private key does not expose its raw byte material"));
        } catch (java.security.GeneralSecurityException | ClassCastException e) {
            throw new CoseException("Unable to extract raw Ed25519 private key material: " + e.getMessage(), e);
        }
    }

    /**
     * Raw 32-byte Ed25519 public key. Unlike the private-key case above, X.509/SPKI public-key
     * encoding for Ed25519 has no optional-attribute complication — it is a fixed 44-byte
     * structure (RFC 8410) whose last 32 bytes are the compressed point, confirmed across the
     * JDK-native, BC-native, and BC-re-parsed cases alike, so the simpler tail-extraction is fine
     * here (unlike for private keys).
     */
    private static byte[] rawEd25519PublicBytes(PublicKey key) {
        byte[] encoded = key.getEncoded();
        return Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
    }

    private static boolean isEd25519(java.security.Key key) {
        String alg = key == null ? null : key.getAlgorithm();
        return "EdDSA".equalsIgnoreCase(alg) || "Ed25519".equalsIgnoreCase(alg);
    }

    /**
     * Builds an OKP {@link OneKey} from raw Ed25519 key material, bypassing
     * {@code OneKey(PublicKey, PrivateKey)}'s missing OKP support. Round-tripping through
     * {@code OneKey(CBORObject)} (rather than just calling {@code add(...)} and stopping) is
     * required — it is the only path that runs the library's internal {@code CheckOkpKey()},
     * which is what actually populates the JCA key objects {@code sign1}/{@code verify1} need.
     */
    private static OneKey ed25519OneKey(byte[] rawPublic, byte[] rawPrivate) throws CoseException {
        EdDsaProviderShim.ensureRegistered();
        OneKey builder = new OneKey();
        builder.add(KeyKeys.KeyType, KeyKeys.KeyType_OKP);
        builder.add(KeyKeys.OKP_Curve, KeyKeys.OKP_Ed25519);
        if (rawPublic != null) {
            builder.add(KeyKeys.OKP_X, com.upokecenter.cbor.CBORObject.FromObject(rawPublic));
        }
        if (rawPrivate != null) {
            builder.add(KeyKeys.OKP_D, com.upokecenter.cbor.CBORObject.FromObject(rawPrivate));
        }
        return new OneKey(builder.AsCBOR());
    }

    /**
     * {@code Message.DecodeFromBytes} can throw the underlying CBOR library's own unchecked
     * {@code CBORException} for garbage input (confirmed empirically — malformed bytes fail
     * before COSE-level code gets a chance to wrap it), not just {@link CoseException}. This
     * normalizes both into {@link CoseException} so callers get one consistent exception type
     * to catch, regardless of which layer rejected the input.
     */
    private static Message decodeMessage(byte[] data, MessageTag expectedTag) throws CoseException {
        try {
            return Message.DecodeFromBytes(data, expectedTag);
        } catch (CoseException coseFailure) {
            throw coseFailure;
        } catch (RuntimeException malformedInput) {
            throw new CoseException("Malformed COSE message: " + malformedInput.getMessage(), malformedInput);
        }
    }

    /** COSE_Sign1 / COSE_Sign algorithms this class supports. */
    public enum SignAlgorithm {
        ES256(AlgorithmID.ECDSA_256), ES384(AlgorithmID.ECDSA_384), ES512(AlgorithmID.ECDSA_512),
        PS256(AlgorithmID.RSA_PSS_256), PS384(AlgorithmID.RSA_PSS_384), PS512(AlgorithmID.RSA_PSS_512),
        /** See the class javadoc — needs {@link EdDsaProviderShim} plus hand-built OKP keys. */
        EDDSA(AlgorithmID.EDDSA);

        final AlgorithmID id;

        SignAlgorithm(AlgorithmID id) {
            this.id = id;
        }
    }

    /** COSE_Mac0 algorithms this class supports. */
    public enum MacAlgorithm {
        HS256(AlgorithmID.HMAC_SHA_256), HS384(AlgorithmID.HMAC_SHA_384), HS512(AlgorithmID.HMAC_SHA_512);

        final AlgorithmID id;

        MacAlgorithm(AlgorithmID id) {
            this.id = id;
        }

        /** Exact key length this algorithm requires, in bytes. */
        public int requiredKeyBytes() {
            return id.getKeySize() / 8;
        }
    }

    /** COSE_Encrypt0 algorithms this class supports. */
    public enum EncryptAlgorithm {
        A128GCM(AlgorithmID.AES_GCM_128), A192GCM(AlgorithmID.AES_GCM_192), A256GCM(AlgorithmID.AES_GCM_256);

        final AlgorithmID id;

        EncryptAlgorithm(AlgorithmID id) {
            this.id = id;
        }

        /** Exact key length this algorithm requires, in bytes. */
        public int requiredKeyBytes() {
            return id.getKeySize() / 8;
        }
    }

    // ---------------------------------------------------------------- Sign1

    /** Signs {@code payload} as a COSE_Sign1 message. {@code publicKey} may be null if unavailable. */
    public static byte[] sign1(byte[] payload, PrivateKey privateKey, PublicKey publicKey, SignAlgorithm algorithm)
            throws CoseException {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is required");
        }
        if (privateKey == null) {
            throw new IllegalArgumentException("A private key is required to sign");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("A signing algorithm is required");
        }
        Sign1Message message = new Sign1Message();
        message.SetContent(payload);
        message.addAttribute(HeaderKeys.Algorithm, algorithm.id.AsCBOR(), Attribute.PROTECTED);
        OneKey key = (algorithm == SignAlgorithm.EDDSA || isEd25519(privateKey))
                ? ed25519OneKey(publicKey == null ? null : rawEd25519PublicBytes(publicKey), rawEd25519PrivateBytes(privateKey))
                : new OneKey(publicKey, privateKey);
        message.sign(key);
        return message.EncodeToBytes();
    }

    /** Result of verifying a COSE_Sign1 message: whether it checked out, and the signed payload. */
    public static final class Sign1Result {
        private final boolean verified;
        private final byte[] payload;

        Sign1Result(boolean verified, byte[] payload) {
            this.verified = verified;
            this.payload = payload;
        }

        public boolean isVerified() {
            return verified;
        }

        public byte[] getPayload() {
            return payload;
        }
    }

    /**
     * Verifies a COSE_Sign1 message against {@code publicKey}. A mismatched or tampered
     * signature yields a {@link Sign1Result} with {@code verified=false} rather than an
     * exception — same pattern as {@code CMSOperations#verifySignedData} and
     * {@code TR34Operations#receiveKey} elsewhere in this app. A structurally invalid message
     * (not decodable as COSE_Sign1 at all) still throws, since there is nothing to report on.
     */
    public static Sign1Result verify1(byte[] cose1Message, PublicKey publicKey) throws CoseException {
        if (cose1Message == null || cose1Message.length == 0) {
            throw new IllegalArgumentException("There is no COSE_Sign1 message to verify");
        }
        if (publicKey == null) {
            throw new IllegalArgumentException("A public key is required to verify");
        }
        Message decoded = decodeMessage(cose1Message, MessageTag.Sign1);
        if (!(decoded instanceof Sign1Message message)) {
            throw new CoseException("Not a COSE_Sign1 message");
        }
        OneKey key = isEd25519(publicKey) ? ed25519OneKey(rawEd25519PublicBytes(publicKey), null) : new OneKey(publicKey, null);
        boolean verified;
        try {
            verified = message.validate(key);
        } catch (CoseException signatureFailure) {
            verified = false;
        }
        return new Sign1Result(verified, message.GetContent());
    }

    // ---------------------------------------------------------------- Mac0

    /** MACs {@code payload} as a COSE_Mac0 message under a raw symmetric key. */
    public static byte[] mac0(byte[] payload, byte[] macKey, MacAlgorithm algorithm) throws CoseException {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is required");
        }
        if (macKey == null || macKey.length == 0) {
            throw new IllegalArgumentException("A MAC key is required");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("A MAC algorithm is required");
        }
        MAC0Message message = new MAC0Message();
        message.SetContent(payload);
        message.addAttribute(HeaderKeys.Algorithm, algorithm.id.AsCBOR(), Attribute.PROTECTED);
        message.Create(macKey);
        return message.EncodeToBytes();
    }

    /** Result of verifying a COSE_Mac0 message: whether the tag checked out, and the payload. */
    public static final class Mac0Result {
        private final boolean verified;
        private final byte[] payload;

        Mac0Result(boolean verified, byte[] payload) {
            this.verified = verified;
            this.payload = payload;
        }

        public boolean isVerified() {
            return verified;
        }

        public byte[] getPayload() {
            return payload;
        }
    }

    /** Verifies a COSE_Mac0 message's tag; a mismatch yields {@code verified=false}, not an exception. */
    public static Mac0Result verifyMac0(byte[] mac0Message, byte[] macKey) throws CoseException {
        if (mac0Message == null || mac0Message.length == 0) {
            throw new IllegalArgumentException("There is no COSE_Mac0 message to verify");
        }
        if (macKey == null || macKey.length == 0) {
            throw new IllegalArgumentException("A MAC key is required");
        }
        Message decoded = decodeMessage(mac0Message, MessageTag.MAC0);
        if (!(decoded instanceof MAC0Message message)) {
            throw new CoseException("Not a COSE_Mac0 message");
        }
        boolean verified;
        try {
            verified = message.Validate(macKey);
        } catch (CoseException macFailure) {
            verified = false;
        }
        return new Mac0Result(verified, message.GetContent());
    }

    // ---------------------------------------------------------------- Encrypt0

    /**
     * Encrypts {@code payload} as a COSE_Encrypt0 message under a raw symmetric key. The IV is
     * generated internally by the library (random, per-message) and travels in the message's
     * protected header — callers never need to supply or manage one.
     */
    public static byte[] encrypt0(byte[] payload, byte[] key, EncryptAlgorithm algorithm) throws CoseException {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is required");
        }
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("An encryption key is required");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("An encryption algorithm is required");
        }
        Encrypt0Message message = new Encrypt0Message();
        message.SetContent(payload);
        message.addAttribute(HeaderKeys.Algorithm, algorithm.id.AsCBOR(), Attribute.PROTECTED);
        message.encrypt(key);
        return message.EncodeToBytes();
    }

    /** Decrypts a COSE_Encrypt0 message. An authentication-tag failure throws (AEAD, cannot report a partial result). */
    public static byte[] decrypt0(byte[] encrypt0Message, byte[] key) throws CoseException {
        if (encrypt0Message == null || encrypt0Message.length == 0) {
            throw new IllegalArgumentException("There is no COSE_Encrypt0 message to decrypt");
        }
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("A decryption key is required");
        }
        Message decoded = decodeMessage(encrypt0Message, MessageTag.Encrypt0);
        if (!(decoded instanceof Encrypt0Message message)) {
            throw new CoseException("Not a COSE_Encrypt0 message");
        }
        return message.decrypt(key);
    }
}
