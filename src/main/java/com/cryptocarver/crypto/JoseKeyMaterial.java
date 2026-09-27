package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import com.nimbusds.jose.jwk.AsymmetricJWK;
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
        if (!(jwk instanceof AsymmetricJWK asymmetric)) {
            throw new IllegalArgumentException("A symmetric (oct) JWK is not an RSA/EC key; use it as a shared secret instead.");
        }
        return jwk.isPrivate() ? asymmetric.toPrivateKey() : asymmetric.toPublicKey();
    }

    private static Object fromPem(String pem) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object object = parser.readObject();
            if (object == null) throw new IllegalArgumentException("No PEM object found in the supplied key material.");
            if (object instanceof PEMEncryptedKeyPair || object instanceof PKCS8EncryptedPrivateKeyInfo) {
                throw new IllegalArgumentException("Encrypted PEM keys are not supported; decrypt the key first.");
            }
            if (object instanceof PEMKeyPair pair) return fromTraditional(pair.getPrivateKeyInfo());
            if (object instanceof PrivateKeyInfo info) return converter.getPrivateKey(info);
            if (object instanceof SubjectPublicKeyInfo info) return converter.getPublicKey(info);
            if (object instanceof X509CertificateHolder certificate) {
                return converter.getPublicKey(certificate.getSubjectPublicKeyInfo());
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
        return new JcaPEMKeyConverter().getPrivateKey(info);
    }

    private static Object fromBareDer(byte[] der) {
        for (String algorithm : new String[] { "RSA", "EC" }) {
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
        throw new IllegalArgumentException("Cannot derive a public key from a " + key.getAlgorithm() + " private key.");
    }
}
