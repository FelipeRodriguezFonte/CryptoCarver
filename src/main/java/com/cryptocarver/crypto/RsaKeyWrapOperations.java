package com.cryptocarver.crypto;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Wraps/unwraps a symmetric key under an RSA key pair — the RSA sibling of
 * {@link KeyWrapOperations} (which only handles AES Key Wrap KEKs) and {@link TR31Operations}
 * (symmetric key blocks). This is deliberately generic (not tied to the payments TR-34 standard):
 * it exposes plain RSA-OAEP-256 key transport in three interoperable serializations so the wrapped
 * key can be handed to whatever the receiving system expects.
 *
 * <p>All three profiles delegate to primitives that already exist and are already exercised
 * elsewhere in the app — this class does not implement any new cryptography, it only gives
 * "export/import a symmetric key with RSA" its own name and a uniform {@code byte[] -> byte[]}
 * shape:</p>
 * <ul>
 *   <li>{@link WrapProfile#RAW_OAEP} &mdash; {@link AsymmetricCipher#encrypt}/{@code decrypt} with
 *       {@code RSA/ECB/OAEPWithSHA-256AndMGF1Padding}, the same transformation the "Asymmetric
 *       Ciphers" module already offers.</li>
 *   <li>{@link WrapProfile#JWE_COMPACT} &mdash; a one-recipient JWE
 *       ({@code alg=RSA-OAEP-256}, {@code enc=A256GCM}) built directly with Nimbus JOSE+JWT, the
 *       same library {@code JOSEController} already uses for its JWE module.</li>
 *   <li>{@link WrapProfile#CMS_ENVELOPED} &mdash; {@link CMSOperations#generateEnvelopedData}/
 *       {@code decryptEnvelopedData}, i.e. PKCS#7/CMS {@code EnvelopedData} with an RSA
 *       {@code KeyTransRecipientInfo}. Requires an actual recipient certificate, not just a bare
 *       public key.</li>
 * </ul>
 */
public final class RsaKeyWrapOperations {

    /** Canonical algorithm name recorded in a {@link com.cryptocarver.model.CryptoEnvelope}. */
    public static final String ALGORITHM_NAME = "RSA-OAEP-256";

    private static final String RAW_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final EncryptionMethod JWE_CONTENT_ENCRYPTION = EncryptionMethod.A256GCM;

    public enum WrapProfile {
        /** Plain RSA-OAEP-256 ciphertext bytes — smallest output, no envelope structure of its own. */
        RAW_OAEP,
        /** A compact one-recipient JWE string, for systems that already speak JOSE. */
        JWE_COMPACT,
        /** PKCS#7/CMS EnvelopedData, for systems that already speak PKI/CMS tooling. Needs a certificate. */
        CMS_ENVELOPED
    }

    /** Result of a wrap: the wrapped bytes plus the metadata a {@link com.cryptocarver.model.CryptoEnvelope} wants. */
    public static final class WrapResult {
        private final byte[] wrapped;
        private final WrapProfile profile;
        private final String algorithm;
        private final String kcvHex;

        WrapResult(byte[] wrapped, WrapProfile profile, String algorithm, String kcvHex) {
            this.wrapped = wrapped;
            this.profile = profile;
            this.algorithm = algorithm;
            this.kcvHex = kcvHex;
        }

        public byte[] getWrapped() { return wrapped; }
        public WrapProfile getProfile() { return profile; }
        public String getAlgorithm() { return algorithm; }
        /** Key check value of the key that was wrapped (hex), or {@code null} if it isn't a KCV-eligible length. */
        public String getKcvHex() { return kcvHex; }
    }

    private RsaKeyWrapOperations() {
    }

    /**
     * Wraps {@code keyToWrap} under {@code recipientPublicKey} using the given profile.
     *
     * @param recipientCertificate required (non-null) only for {@link WrapProfile#CMS_ENVELOPED};
     *                              ignored for the other two profiles.
     */
    public static WrapResult wrap(byte[] keyToWrap, PublicKey recipientPublicKey,
            X509Certificate recipientCertificate, WrapProfile profile) throws Exception {
        if (keyToWrap == null || keyToWrap.length == 0) {
            throw new IllegalArgumentException("There is no key material to wrap");
        }
        if (recipientPublicKey == null) {
            throw new IllegalArgumentException("A recipient RSA public key is required");
        }
        if (profile == null) {
            throw new IllegalArgumentException("A wrap profile is required");
        }

        byte[] wrapped = switch (profile) {
            case RAW_OAEP -> AsymmetricCipher.encrypt(keyToWrap, recipientPublicKey, RAW_OAEP_TRANSFORMATION);
            case JWE_COMPACT -> wrapJwe(keyToWrap, recipientPublicKey);
            case CMS_ENVELOPED -> {
                if (recipientCertificate == null) {
                    throw new IllegalArgumentException(
                            "CMS EnvelopedData needs a recipient certificate, not just a bare public key. "
                                    + "Paste a certificate, or use Raw OAEP / JWE Compact instead.");
                }
                yield CMSOperations.generateEnvelopedData(keyToWrap, recipientCertificate);
            }
        };

        return new WrapResult(wrapped, profile, ALGORITHM_NAME, kcvHexIfEligible(keyToWrap));
    }

    /** Unwraps {@code wrapped} with {@code recipientPrivateKey}, returning the recovered key bytes. */
    public static byte[] unwrap(byte[] wrapped, PrivateKey recipientPrivateKey, WrapProfile profile) throws Exception {
        if (wrapped == null || wrapped.length == 0) {
            throw new IllegalArgumentException("There is no wrapped data to unwrap");
        }
        if (recipientPrivateKey == null) {
            throw new IllegalArgumentException("A recipient RSA private key is required");
        }
        if (profile == null) {
            throw new IllegalArgumentException("A wrap profile is required");
        }

        return switch (profile) {
            case RAW_OAEP -> AsymmetricCipher.decrypt(wrapped, recipientPrivateKey, RAW_OAEP_TRANSFORMATION);
            case JWE_COMPACT -> unwrapJwe(wrapped, recipientPrivateKey);
            case CMS_ENVELOPED -> CMSOperations.decryptEnvelopedData(wrapped, recipientPrivateKey);
        };
    }

    private static byte[] wrapJwe(byte[] keyToWrap, PublicKey recipientPublicKey) throws Exception {
        if (!(recipientPublicKey instanceof RSAPublicKey rsaPublicKey)) {
            throw new IllegalArgumentException("JWE Compact requires an RSA public key");
        }
        JWEHeader header = new JWEHeader(JWEAlgorithm.RSA_OAEP_256, JWE_CONTENT_ENCRYPTION);
        JWEObject jweObject = new JWEObject(header, new Payload(keyToWrap));
        jweObject.encrypt(new RSAEncrypter(rsaPublicKey));
        return jweObject.serialize().getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] unwrapJwe(byte[] wrapped, PrivateKey recipientPrivateKey) throws Exception {
        if (!(recipientPrivateKey instanceof RSAPrivateKey rsaPrivateKey)) {
            throw new IllegalArgumentException("JWE Compact requires an RSA private key");
        }
        String compact = new String(wrapped, StandardCharsets.US_ASCII);
        JWEObject jweObject = JWEObject.parse(compact);
        jweObject.decrypt(new RSADecrypter(rsaPrivateKey));
        return jweObject.getPayload().toBytes();
    }

    /** KCV is only defined here for AES-length key material (16/24/32 bytes); anything else is best-effort skipped. */
    private static String kcvHexIfEligible(byte[] keyMaterial) {
        if (keyMaterial.length != 16 && keyMaterial.length != 24 && keyMaterial.length != 32) {
            return null;
        }
        try {
            return com.cryptocarver.util.DataConverter.bytesToHex(KeyOperations.calculateKCV_AES(keyMaterial));
        } catch (Exception e) {
            return null;
        }
    }
}
