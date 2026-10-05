package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import com.nimbusds.jose.jwk.AsymmetricJWK;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

/**
 * Parses the key material a user pastes into the JOSE module.
 *
 * <p>Asymmetric input may be PEM (SubjectPublicKeyInfo, PKCS#1 RSA, PKCS#8,
 * SEC1 EC, X.509 certificate), bare Base64 DER, a JWK or a single-key JWKS.
 * A public key is derived from a private key when only the private half was
 * supplied, so a signer can verify with the same field it signed with.
 * Symmetric input is decoded with an explicit {@link SecretEncoding}.</p>
 */
public final class JoseKeyMaterial {

    /** How a shared secret or password typed as text becomes bytes. */
    public enum SecretEncoding {
        UTF8("UTF-8"), HEX("Hex"), BASE64("Base64 / Base64URL");

        private final String label;

        SecretEncoding(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Unknown or missing labels fall back to UTF-8, the historical behaviour. */
        public static SecretEncoding fromLabel(String label) {
            for (SecretEncoding encoding : values()) {
                if (encoding.label.equals(label)) return encoding;
            }
            return UTF8;
        }
    }

    private JoseKeyMaterial() {
    }

    public static byte[] secret(String text, SecretEncoding encoding) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("A shared secret is required.");
        if (text.trim().startsWith("-----BEGIN")) {
            throw new IllegalArgumentException("A symmetric algorithm needs a shared secret, not a PEM key or certificate.");
        }
        SecretEncoding selected = encoding == null ? SecretEncoding.UTF8 : encoding;
        return switch (selected) {
            case UTF8 -> text.getBytes(StandardCharsets.UTF_8);
            case HEX -> {
                String hex = text.replaceAll("\\s", "");
                if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
                if (!DataConverter.isValidHex(hex)) throw new IllegalArgumentException("The secret is not valid hexadecimal.");
                yield DataConverter.hexToBytes(hex);
            }
            case BASE64 -> DataConverter.decodeBase64Flexible(text.replaceAll("\\s", ""));
        };
    }

    public static PublicKey publicKey(String input) throws Exception {
        Object parsed = parse(input);
        if (parsed instanceof PublicKey key) return key;
        if (parsed instanceof PrivateKey key) return derivePublic(key);
        throw new IllegalArgumentException("No public key found in the supplied key material.");
    }

    public static PrivateKey privateKey(String input) throws Exception {
        Object parsed = parse(input);
        if (parsed instanceof PrivateKey key) return key;
        throw new IllegalArgumentException("A private key is required; the supplied key material only holds a public key.");
    }

    public static RSAPublicKey rsaPublicKey(String input) throws Exception {
        if (publicKey(input) instanceof RSAPublicKey key) return key;
        throw new IllegalArgumentException("The supplied key is not an RSA key.");
    }

    public static RSAPrivateKey rsaPrivateKey(String input) throws Exception {
        if (privateKey(input) instanceof RSAPrivateKey key) return key;
        throw new IllegalArgumentException("The supplied private key is not an RSA key.");
    }

    public static ECPublicKey ecPublicKey(String input) throws Exception {
        if (publicKey(input) instanceof ECPublicKey key) return key;
        throw new IllegalArgumentException("The supplied key is not an EC key.");
    }

    public static ECPrivateKey ecPrivateKey(String input) throws Exception {
        if (privateKey(input) instanceof ECPrivateKey key) return key;
        throw new IllegalArgumentException("The supplied private key is not an EC key.");
    }

    /** Returns a {@link PublicKey} or {@link PrivateKey}; private wins when both are present. */
    private static Object parse(String input) throws Exception {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Key material is required.");
        String text = input.trim();
        if (text.startsWith("{")) return fromJwk(text);
        if (text.contains("-----BEGIN")) return fromPem(text);
        return fromBareDer(DataConverter.decodeBase64Flexible(text.replaceAll("\\s", "")));
    }

    private static Object fromJwk(String json) throws Exception {
        JWK jwk;
        if (json.contains("\"keys\"")) {
            List<JWK> keys = JWKSet.parse(json).getKeys();
            if (keys.size() != 1) {
                throw new IllegalArgumentException("The JWKS holds " + keys.size() + " keys; paste the single JWK to use.");
            }
            jwk = keys.get(0);
        } else {
            jwk = JWK.parse(json);
        }
        if (jwk instanceof OctetKeyPair okp) return fromOkp(okp);
        if (jwk instanceof com.nimbusds.jose.jwk.ECKey ecKey
                && Curve.SECP256K1.equals(ecKey.getCurve())) return fromSecp256k1Jwk(ecKey);
        if (!(jwk instanceof AsymmetricJWK asymmetric)) {
            throw new IllegalArgumentException("A symmetric (oct) JWK is not an RSA/EC key; use it as a shared secret instead.");
        }
        return jwk.isPrivate() ? asymmetric.toPrivateKey() : asymmetric.toPublicKey();
    }

    private static Object fromPem(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object object = parser.readObject();
            if (object == null) throw new IllegalArgumentException("No PEM object found in the supplied key material.");
            if (object instanceof PEMEncryptedKeyPair || object instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new IllegalArgumentException("Encrypted PEM keys are not supported; decrypt the key first.");
            }
            if (object instanceof PEMKeyPair pair) return fromTraditional(pair.getPrivateKeyInfo());
            if (object instanceof PrivateKeyInfo info) return convertPrivateKey(info);
            if (object instanceof SubjectPublicKeyInfo info) return convertPublicKey(info);
            if (object instanceof X509CertificateHolder certificate) {
                return convertPublicKey(certificate.getSubjectPublicKeyInfo());
            }
            throw new IllegalArgumentException("Unsupported PEM object: " + object.getClass().getSimpleName());
        }
    }

    /**
     * SEC1 keys carry the curve inside the key rather than in the algorithm
     * identifier the JDK factory reads, so the identifier is rebuilt first.
     */
    private static PrivateKey fromTraditional(PrivateKeyInfo info) throws Exception {
        AlgorithmIdentifier algorithm = info.getPrivateKeyAlgorithm();
        if (X9ObjectIdentifiers.id_ecPublicKey.equals(algorithm.getAlgorithm()) && algorithm.getParameters() == null) {
            org.bouncycastle.asn1.sec.ECPrivateKey sec1 = org.bouncycastle.asn1.sec.ECPrivateKey.getInstance(info.parsePrivateKey());
            if (sec1.getParametersObject() == null) {
                throw new IllegalArgumentException("The EC private key does not name its curve.");
            }
            info = new PrivateKeyInfo(new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey,
                    sec1.getParametersObject()), sec1);
        }
        return convertPrivateKey(info);
    }

    private static PrivateKey convertPrivateKey(PrivateKeyInfo info) throws Exception {
        try { return new JcaPEMKeyConverter().getPrivateKey(info); }
        catch (Exception platformFailure) {
            try { return new JcaPEMKeyConverter().setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider()).getPrivateKey(info); }
            catch (Exception bcFailure) { bcFailure.addSuppressed(platformFailure); throw bcFailure; }
        }
    }

    private static PublicKey convertPublicKey(SubjectPublicKeyInfo info) throws Exception {
        try { return new JcaPEMKeyConverter().getPublicKey(info); }
        catch (Exception platformFailure) {
            try { return new JcaPEMKeyConverter().setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider()).getPublicKey(info); }
            catch (Exception bcFailure) { bcFailure.addSuppressed(platformFailure); throw bcFailure; }
        }
    }

    private static Object fromSecp256k1Jwk(com.nimbusds.jose.jwk.ECKey jwk) throws Exception {
        org.bouncycastle.asn1.x9.X9ECParameters named = org.bouncycastle.asn1.x9.ECNamedCurveTable
                .getByName("secp256k1");
        if (named == null) throw new IllegalStateException("The bundled EC provider has no secp256k1 parameters.");
        java.security.spec.ECParameterSpec params = EC5Util.convertToSpec(named);
        KeyFactory factory = KeyFactory.getInstance("EC", new org.bouncycastle.jce.provider.BouncyCastleProvider());
        if (jwk.isPrivate()) {
            return factory.generatePrivate(new java.security.spec.ECPrivateKeySpec(
                    new java.math.BigInteger(1, jwk.getD().decode()), params));
        }
        return factory.generatePublic(new ECPublicKeySpec(new ECPoint(
                new java.math.BigInteger(1, jwk.getX().decode()), new java.math.BigInteger(1, jwk.getY().decode())), params));
    }

    /** DER prefixes that wrap a raw RFC 8037 key into SubjectPublicKeyInfo / PKCS#8. */
    private static final byte[] ED25519_SPKI = DataConverter.hexToBytes("302a300506032b6570032100");
    private static final byte[] ED448_SPKI = DataConverter.hexToBytes("3043300506032b6571033a00");
    private static final byte[] ED25519_PKCS8 = DataConverter.hexToBytes("302e020100300506032b657004220420");
    private static final byte[] ED448_PKCS8 = DataConverter.hexToBytes("3047020100300506032b6571043b0439");
    private static final byte[] X25519_SPKI = DataConverter.hexToBytes("302a300506032b656e032100");
    private static final byte[] X448_SPKI = DataConverter.hexToBytes("3042300506032b656f033900");
    private static final byte[] X25519_PKCS8 = DataConverter.hexToBytes("302e020100300506032b656e04220420");
    private static final byte[] X448_PKCS8 = DataConverter.hexToBytes("3046020100300506032b656f043a0438");

    /** RFC 8037 OKP JWK to JDK keys. Nimbus's XDH conversion needs optional Tink. */
    private static Object fromOkp(OctetKeyPair okp) throws Exception {
        boolean ed25519 = Curve.Ed25519.equals(okp.getCurve());
        boolean ed448 = Curve.Ed448.equals(okp.getCurve());
        boolean x25519 = Curve.X25519.equals(okp.getCurve());
        boolean x448 = Curve.X448.equals(okp.getCurve());
        if (!ed25519 && !ed448 && !x25519 && !x448) {
            throw new IllegalArgumentException("Only Ed25519, Ed448, X25519 and X448 OKP keys are supported (got " + okp.getCurve() + ").");
        }
        String algorithm = ed25519 ? "Ed25519" : ed448 ? "Ed448" : x25519 ? "X25519" : "X448";
        KeyFactory factory = KeyFactory.getInstance(algorithm);
        if (okp.isPrivate()) {
            return factory.generatePrivate(new PKCS8EncodedKeySpec(
                    concat(ed25519 ? ED25519_PKCS8 : ed448 ? ED448_PKCS8
                            : x25519 ? X25519_PKCS8 : X448_PKCS8, okp.getDecodedD())));
        }
        return factory.generatePublic(new X509EncodedKeySpec(
                concat(ed25519 ? ED25519_SPKI : ed448 ? ED448_SPKI
                        : x25519 ? X25519_SPKI : X448_SPKI, okp.getDecodedX())));
    }

    /** Raw RFC 8037 public key bytes of an Ed25519 / Ed448 key. */
    public static byte[] rawEdPublicKey(PublicKey key) {
        byte[] der = key.getEncoded();
        int length = key.getAlgorithm().equals("Ed448") || der.length == ED448_SPKI.length + 57 ? 57 : 32;
        return java.util.Arrays.copyOfRange(der, der.length - length, der.length);
    }

    /** Raw RFC 8037 private key bytes (the seed) of an Ed25519 / Ed448 key. */
    public static byte[] rawEdPrivateKey(PrivateKey key) throws Exception {
        PrivateKeyInfo info = PrivateKeyInfo.getInstance(key.getEncoded());
        return org.bouncycastle.asn1.ASN1OctetString.getInstance(info.parsePrivateKey()).getOctets();
    }

    /** Raw RFC 8037 public coordinate for an X25519 / X448 key. */
    public static byte[] rawXPublicKey(PublicKey key) {
        byte[] der = key.getEncoded();
        int length = "X448".equalsIgnoreCase(xdhCurveName(key)) ? 56 : 32;
        return java.util.Arrays.copyOfRange(der, der.length - length, der.length);
    }

    /** Raw RFC 8037 private scalar for an X25519 / X448 key. */
    public static byte[] rawXPrivateKey(PrivateKey key) {
        if (key instanceof java.security.interfaces.XECPrivateKey xec) {
            return xec.getScalar().orElseThrow(() -> new IllegalArgumentException("The XDH private scalar is unavailable."));
        }
        throw new IllegalArgumentException("The supplied key is not an X25519 or X448 private key.");
    }

    public static String xdhCurveName(java.security.Key key) {
        if (key instanceof java.security.interfaces.XECKey xec
                && xec.getParams() instanceof java.security.spec.NamedParameterSpec named) {
            return named.getName();
        }
        return key.getAlgorithm();
    }

    private static byte[] concat(byte[] prefix, byte[] raw) {
        byte[] out = java.util.Arrays.copyOf(prefix, prefix.length + raw.length);
        System.arraycopy(raw, 0, out, prefix.length, raw.length);
        return out;
    }

    private static Object fromBareDer(byte[] der) {
        for (String algorithm : new String[] { "RSA", "EC", "Ed25519", "Ed448", "X25519", "X448" }) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (Exception ignored) {
                // try the next encoding
            }
            try {
                return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(der));
            } catch (Exception ignored) {
                // try the next algorithm
            }
        }
        throw new IllegalArgumentException("Could not read the key as PEM, DER (PKCS#8 / SubjectPublicKeyInfo) or JWK.");
    }

    private static PublicKey derivePublic(PrivateKey key) throws Exception {
        if (key instanceof RSAPrivateCrtKey rsa) {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
        }
        if (key instanceof ECPrivateKey ec) {
            org.bouncycastle.math.ec.ECPoint q = EC5Util.convertSpec(ec.getParams()).getG()
                    .multiply(ec.getS()).normalize();
            ECPoint w = new ECPoint(q.getAffineXCoord().toBigInteger(), q.getAffineYCoord().toBigInteger());
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, ec.getParams()));
        }
        if (key instanceof java.security.interfaces.EdECPrivateKey) {
            byte[] seed = rawEdPrivateKey(key);
            boolean ed25519 = seed.length == 32;
            byte[] raw = ed25519
                    ? new org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seed).generatePublicKey().getEncoded()
                    : new org.bouncycastle.crypto.params.Ed448PrivateKeyParameters(seed).generatePublicKey().getEncoded();
            return KeyFactory.getInstance(ed25519 ? "Ed25519" : "Ed448")
                    .generatePublic(new X509EncodedKeySpec(concat(ed25519 ? ED25519_SPKI : ED448_SPKI, raw)));
        }
        if (key instanceof java.security.interfaces.XECPrivateKey xec) {
            String curve = xdhCurveName(key);
            boolean x25519 = "X25519".equalsIgnoreCase(curve);
            byte[] scalar = xec.getScalar().orElseThrow(() -> new IllegalArgumentException("The XDH private scalar is unavailable."));
            byte[] raw;
            if (x25519) {
                raw = new org.bouncycastle.crypto.params.X25519PrivateKeyParameters(scalar, 0).generatePublicKey().getEncoded();
            } else if ("X448".equalsIgnoreCase(curve)) {
                raw = new org.bouncycastle.crypto.params.X448PrivateKeyParameters(scalar, 0).generatePublicKey().getEncoded();
            } else {
                throw new IllegalArgumentException("Unsupported XDH curve: " + curve);
            }
            return KeyFactory.getInstance(x25519 ? "X25519" : "X448")
                    .generatePublic(new X509EncodedKeySpec(concat(x25519 ? X25519_SPKI : X448_SPKI, raw)));
        }
        throw new IllegalArgumentException("Cannot derive a public key from a " + key.getAlgorithm() + " private key.");
    }
}
