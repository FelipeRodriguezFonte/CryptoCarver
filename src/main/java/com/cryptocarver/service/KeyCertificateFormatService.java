package com.cryptocarver.service;

import com.cryptocarver.model.SecretVisibilityProfile;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.Ed448PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed448PublicKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.bouncycastle.math.ec.rfc8032.Ed448;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;

public class KeyCertificateFormatService {

    public enum FormatType {
        PEM_CERTIFICATE,
        DER_CERTIFICATE,
        PEM_PUBLIC_KEY,
        DER_PUBLIC_KEY,
        PEM_PRIVATE_KEY,
        DER_PRIVATE_KEY,
        PKCS12,
        JKS,
        BKS,
        JWK_PUBLIC,
        JWK_PRIVATE,
        OPENSSH_PUBLIC_KEY,
        UNKNOWN
    }

    public static class KeystoreEntrySummary {
        private final String alias;
        private final String entryType; // TrustedCert, PrivateKey, SecretKey
        private final String algorithm;
        private final String subject;
        private final String issuer;
        private final String expiration;
        private final int chainLength;

        public KeystoreEntrySummary(String alias, String entryType, String algorithm, String subject, String issuer, String expiration, int chainLength) {
            this.alias = alias;
            this.entryType = entryType;
            this.algorithm = algorithm;
            this.subject = subject;
            this.issuer = issuer;
            this.expiration = expiration;
            this.chainLength = chainLength;
        }

        public String getAlias() { return alias; }
        public String getEntryType() { return entryType; }
        public String getAlgorithm() { return algorithm; }
        public String getSubject() { return subject; }
        public String getIssuer() { return issuer; }
        public String getExpiration() { return expiration; }
        public int getChainLength() { return chainLength; }
    }

    public static class DetectionResult {
        public FormatType type;
        public String algorithm;
        public int keySize;
        public String formatString;
        public String sha256Fingerprint;
        public String subject;
        public String issuer;
        public boolean hasPrivateKey;
        public boolean isEncrypted;
        public String notBefore;
        public String notAfter;
        public Object parsedObject;
        public List<KeystoreEntrySummary> keystoreEntries; // Null if not a keystore

        // Safe, non-material diagnostic for malformed JWK input. Never stores the input JSON.
        public String validationError;

        // Original raw bytes used to identify this (so we can pass it into convert easily)
        public byte[] rawBytes;
    }

    public DetectionResult detect(byte[] input, char[] password) {
        DetectionResult res = new DetectionResult();
        res.type = FormatType.UNKNOWN;
        res.rawBytes = input;

        // Try PEM
        try {
            String str = new String(input, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (str.contains("BEGIN")) {
                try (PEMParser parser = new PEMParser(new StringReader(str))) {
                    Object obj = parser.readObject();
                    if (obj instanceof X509CertificateHolder) {
                        res.type = FormatType.PEM_CERTIFICATE;
                        res.parsedObject = obj;
                        res.formatString = "PEM Certificate";
                        populateCertInfo(res, (X509CertificateHolder) obj);
                        return res;
                    } else if (obj instanceof SubjectPublicKeyInfo) {
                        res.type = FormatType.PEM_PUBLIC_KEY;
                        res.parsedObject = obj;
                        res.formatString = "PEM SPKI Public Key";
                        populatePublicKeyInfo(res, (SubjectPublicKeyInfo) obj);
                        return res;
                    } else if (obj instanceof PEMKeyPair) {
                        res.type = FormatType.PEM_PRIVATE_KEY;
                        res.parsedObject = obj;
                        res.hasPrivateKey = true;
                        res.formatString = "PEM PKCS#1 Private Key";
                        populatePrivateKeyInfo(res, ((PEMKeyPair) obj).getPrivateKeyInfo());
                        return res;
                    } else if (obj instanceof PrivateKeyInfo) {
                        res.type = FormatType.PEM_PRIVATE_KEY;
                        res.parsedObject = obj;
                        res.hasPrivateKey = true;
                        res.formatString = "PEM PKCS#8 Private Key";
                        populatePrivateKeyInfo(res, (PrivateKeyInfo) obj);
                        return res;
                    } else if (obj instanceof PKCS8EncryptedPrivateKeyInfo) {
                        res.type = FormatType.PEM_PRIVATE_KEY;
                        res.parsedObject = obj;
                        res.hasPrivateKey = true;
                        res.isEncrypted = true;
                        res.formatString = "PEM PKCS#8 Encrypted Private Key";
                        res.algorithm = ((PKCS8EncryptedPrivateKeyInfo) obj).getEncryptionAlgorithm().getAlgorithm().getId();
                        return res;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try JWK
        try {
            String str = new String(input, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (str.startsWith("{")) {
                try {
                    JWK jwk = JWK.parse(str);
                    res.type = jwk.isPrivate() ? FormatType.JWK_PRIVATE : FormatType.JWK_PUBLIC;
                    res.parsedObject = jwk;
                    res.formatString = "JSON Web Key (JWK)";
                    res.hasPrivateKey = jwk.isPrivate();
                    res.algorithm = jwk.getKeyType().getValue();
                    res.keySize = jwk.size();
                    return res;
                } catch (Exception e) {
                    // Keep parse diagnostics structural and never expose the input or Nimbus' exception text.
                    res.validationError = describeMalformedJwk(str);
                    return res;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try OpenSSH Public Key
        try {
            String str = new String(input, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (str.startsWith("ssh-rsa") || str.startsWith("ecdsa-sha2-") || str.startsWith("ssh-ed25519") || str.startsWith("ssh-dss")) {
                res.type = FormatType.OPENSSH_PUBLIC_KEY;
                res.parsedObject = str;
                res.formatString = "OpenSSH Public Key";
                res.algorithm = str.split(" ")[0];
                return res;
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try DER X509
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(input));
            res.type = FormatType.DER_CERTIFICATE;
            res.parsedObject = cert;
            res.formatString = "DER X.509 Certificate";
            res.algorithm = cert.getPublicKey().getAlgorithm();
            res.subject = cert.getSubjectX500Principal().getName();
            res.issuer = cert.getIssuerX500Principal().getName();
            return res;
        } catch (Exception e) {
            // Ignore
        }

        // Try DER SPKI (Public Key)
        try {
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(input);
            if (spki != null) {
                res.type = FormatType.DER_PUBLIC_KEY;
                res.parsedObject = spki;
                res.formatString = "DER SPKI Public Key";
                populatePublicKeyInfo(res, spki);
                return res;
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try DER PKCS#8 (Private Key)
        try {
            PrivateKeyInfo pki = PrivateKeyInfo.getInstance(input);
            if (pki != null) {
                res.type = FormatType.DER_PRIVATE_KEY;
                res.parsedObject = pki;
                res.hasPrivateKey = true;
                res.formatString = "DER PKCS#8 Private Key";
                populatePrivateKeyInfo(res, pki);
                return res;
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try PKCS12
        try {
            if (input.length > 2 && input[0] == 0x30) {
                try {
                    org.bouncycastle.asn1.pkcs.Pfx pfx = org.bouncycastle.asn1.pkcs.Pfx.getInstance(input);
                    if (pfx != null && pfx.getMacData() != null) {
                        res.type = FormatType.PKCS12;
                        res.formatString = "PKCS#12 Keystore";
                        res.parsedObject = input;

                        char[] pwd = (password != null) ? password : new char[0];
                        KeyStore ks = KeyStore.getInstance("PKCS12");
                        try {
                            ks.load(new ByteArrayInputStream(input), pwd);
                            res.isEncrypted = (pwd.length > 0);

                            boolean hasPriv = false;
                            Enumeration<String> aliases = ks.aliases();
                            while (aliases.hasMoreElements()) {
                                String alias = aliases.nextElement();
                                if (ks.isKeyEntry(alias)) {
                                    hasPriv = true;
                                    break;
                                }
                            }
                            res.hasPrivateKey = hasPriv;
                        } catch (Exception ex) {
                            res.formatString = "PKCS#12 Keystore (Requires correct Password)";
                            res.isEncrypted = true;
                            res.hasPrivateKey = true; // Assume it might have private key if protected
                        }
                        return res;
                    }
                } catch (Exception e) {
                    // Not a valid PFX
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return res;
    }

    public String convert(DetectionResult source, String targetFormat, SecretVisibilityProfile policy) throws Exception {
        if (source == null) {
            throw new Exception("No source material was supplied.");
        }
        if (source.validationError != null) {
            throw new Exception(source.validationError);
        }
        if (source.isEncrypted && source.type != FormatType.PKCS12) {
            throw new Exception("Cannot convert encrypted key without decryption logic.");
        }

        if (source.hasPrivateKey && policy != SecretVisibilityProfile.FULL_LAB) {
            throw new Exception("Policy prevents exporting private keys. Required: FULL_LAB");
        }

        if (source.type == FormatType.PEM_CERTIFICATE || source.type == FormatType.DER_CERTIFICATE) {
            X509Certificate cert = null;
            if (source.type == FormatType.PEM_CERTIFICATE) {
                cert = new JcaX509CertificateConverter().getCertificate((X509CertificateHolder) source.parsedObject);
            } else {
                cert = (X509Certificate) source.parsedObject;
            }

            if ("PEM".equalsIgnoreCase(targetFormat)) {
                return toPEM("CERTIFICATE", cert.getEncoded());
            } else if ("DER (Base64)".equalsIgnoreCase(targetFormat)) {
                return Base64.getEncoder().encodeToString(cert.getEncoded());
            } else if ("DER (Hex)".equalsIgnoreCase(targetFormat)) {
                return com.cryptocarver.util.DataConverter.bytesToHex(cert.getEncoded());
            }
            throw new Exception("Unsupported conversion for Certificate to " + targetFormat);
        }

        if (source.type == FormatType.PEM_PUBLIC_KEY || source.type == FormatType.DER_PUBLIC_KEY || source.type == FormatType.JWK_PUBLIC || source.type == FormatType.OPENSSH_PUBLIC_KEY) {
            PublicKey pub = getPublicKey(source);

            if ("PEM".equalsIgnoreCase(targetFormat)) {
                return toPEM("PUBLIC KEY", pub.getEncoded());
            } else if ("DER (Base64)".equalsIgnoreCase(targetFormat)) {
                return Base64.getEncoder().encodeToString(pub.getEncoded());
            } else if ("DER (Hex)".equalsIgnoreCase(targetFormat)) {
                return com.cryptocarver.util.DataConverter.bytesToHex(pub.getEncoded());
            } else if ("JWK".equalsIgnoreCase(targetFormat)) {
                return toJWK(pub, null).toJSONString();
            } else if ("OpenSSH Public Key".equalsIgnoreCase(targetFormat)) {
                AsymmetricKeyParameter pubParam = PublicKeyFactory.createKey(pub.getEncoded());
                byte[] openssh = OpenSSHPublicKeyUtil.encodePublicKey(pubParam);
                return new String(openssh, java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            throw new Exception("Unsupported conversion for Public Key to " + targetFormat);
        }

        if (source.type == FormatType.PEM_PRIVATE_KEY || source.type == FormatType.DER_PRIVATE_KEY || source.type == FormatType.JWK_PRIVATE) {
            PrivateKey priv = getPrivateKey(source);

            if ("PEM".equalsIgnoreCase(targetFormat)) {
                return toPEM("PRIVATE KEY", priv.getEncoded()); // Outputs PKCS#8
            } else if ("DER (Base64)".equalsIgnoreCase(targetFormat)) {
                return Base64.getEncoder().encodeToString(priv.getEncoded());
            } else if ("DER (Hex)".equalsIgnoreCase(targetFormat)) {
                return com.cryptocarver.util.DataConverter.bytesToHex(priv.getEncoded());
            } else if ("JWK".equalsIgnoreCase(targetFormat)) {
                PublicKey pub = null;
                try {
                    // Try to derive public key to build full JWK
                    if (priv instanceof RSAPrivateKey) {
                        RSAPrivateKey rsa = (RSAPrivateKey) priv;
                        // For RSA we need Public Exponent which is usually in the private key info
                        java.security.spec.RSAPublicKeySpec pubSpec = new java.security.spec.RSAPublicKeySpec(rsa.getModulus(), ((java.security.interfaces.RSAPrivateCrtKey)rsa).getPublicExponent());
                        pub = KeyFactory.getInstance("RSA").generatePublic(pubSpec);
                    }
                } catch (Exception e) {}

                return toJWK(pub, priv).toJSONString();
            }
            throw new Exception("Unsupported conversion for Private Key to " + targetFormat);
        }

        throw new Exception("Unsupported source type for conversion.");
    }

    public String getChainSummary(byte[] pkcs12Bytes, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(pkcs12Bytes), password);

        StringBuilder sb = new StringBuilder();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            sb.append("Alias: ").append(alias).append("\n");

            if (ks.isKeyEntry(alias)) {
                sb.append("  [Key Entry]\n");
            }

            java.security.cert.Certificate[] chain = ks.getCertificateChain(alias);
            if (chain != null) {
                sb.append("  Certificate Chain:\n");
                for (int i = 0; i < chain.length; i++) {
                    X509Certificate c = (X509Certificate) chain[i];
                    sb.append(String.format("    %d: Subject: %s\n", i, c.getSubjectX500Principal().getName()));
                    sb.append(String.format("       Issuer:  %s\n", c.getIssuerX500Principal().getName()));
                }
            } else if (ks.isCertificateEntry(alias)) {
                X509Certificate c = (X509Certificate) ks.getCertificate(alias);
                sb.append("  [Trusted Certificate]\n");
                sb.append(String.format("    Subject: %s\n", c.getSubjectX500Principal().getName()));
                sb.append(String.format("    Issuer:  %s\n", c.getIssuerX500Principal().getName()));
            }
        }
        return sb.toString();
    }

    public DetectionResult inspectKeystore(byte[] raw, String storeType, char[] password) throws Exception {
        DetectionResult res = new DetectionResult();
        res.rawBytes = raw;
        if ("PKCS12".equalsIgnoreCase(storeType)) res.type = FormatType.PKCS12;
        else if ("JKS".equalsIgnoreCase(storeType)) res.type = FormatType.JKS;
        else if ("BKS".equalsIgnoreCase(storeType)) res.type = FormatType.BKS;
        else res.type = FormatType.UNKNOWN;

        res.formatString = storeType + " Keystore";
        res.keystoreEntries = new ArrayList<>();

        KeyStore ks;
        if ("BKS".equalsIgnoreCase(storeType)) {
            ks = KeyStore.getInstance("BKS", "BC");
        } else {
            ks = KeyStore.getInstance(storeType);
        }

        try {
            ks.load(new ByteArrayInputStream(raw), password);
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                String entryType = "Unknown";
                String algorithm = "N/A";
                String subject = "N/A";
                String issuer = "N/A";
                String expiration = "N/A";
                int chainLength = 0;

                if (ks.isKeyEntry(alias)) {
                    java.security.cert.Certificate[] chain = ks.getCertificateChain(alias);
                    if (chain != null && chain.length > 0) {
                        entryType = "PrivateKey";
                        chainLength = chain.length;
                        if (chain[0] instanceof X509Certificate) {
                            X509Certificate c = (X509Certificate) chain[0];
                            subject = c.getSubjectX500Principal().getName();
                            issuer = c.getIssuerX500Principal().getName();
                            expiration = c.getNotAfter().toString();
                            algorithm = c.getPublicKey().getAlgorithm();
                        }
                    } else {
                        // Without extracting the key, we cannot easily know its algorithm for SecretKeys in JKS/PKCS12
                        entryType = "SecretKey";
                        algorithm = "N/A";
                    }
                } else if (ks.isCertificateEntry(alias)) {
                    entryType = "TrustedCert";
                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        X509Certificate c = (X509Certificate) cert;
                        subject = c.getSubjectX500Principal().getName();
                        issuer = c.getIssuerX500Principal().getName();
                        expiration = c.getNotAfter().toString();
                        algorithm = c.getPublicKey().getAlgorithm();
                    }
                }
                res.keystoreEntries.add(new KeystoreEntrySummary(alias, entryType, algorithm, subject, issuer, expiration, chainLength));
            }
        } finally {
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
        }
        return res;
    }

    public String extractFromKeystore(byte[] raw, String storeType, char[] password, String alias, String targetFormat, SecretVisibilityProfile visibility) throws Exception {
        KeyStore ks;
        if ("BKS".equalsIgnoreCase(storeType)) {
            ks = KeyStore.getInstance("BKS", "BC");
        } else {
            ks = KeyStore.getInstance(storeType);
        }

        try {
            ks.load(new ByteArrayInputStream(raw), password);

            if (ks.isCertificateEntry(alias)) {
                if (targetFormat.contains("Public")) {
                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                    if (cert == null) throw new Exception("No public key / cert found for alias");
                    DetectionResult tempRes = new DetectionResult();
                    tempRes.type = FormatType.DER_PUBLIC_KEY;
                    tempRes.parsedObject = SubjectPublicKeyInfo.getInstance(cert.getPublicKey().getEncoded());
                    return convert(tempRes, targetFormat.replace(" Public", ""), visibility);
                } else {
                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                    DetectionResult tempRes = detect(cert.getEncoded(), null); // re-detect to use convert
                    return convert(tempRes, targetFormat.replace(" Cert", ""), visibility);
                }
            } else if (ks.isKeyEntry(alias)) {
                if (targetFormat.contains("Chain")) {
                    java.security.cert.Certificate[] chain = ks.getCertificateChain(alias);
                    if (chain == null || chain.length == 0) throw new Exception("No certificate chain found for alias");
                    StringBuilder sb = new StringBuilder();
                    for (java.security.cert.Certificate c : chain) {
                        DetectionResult tempRes = detect(c.getEncoded(), null);
                        sb.append(convert(tempRes, targetFormat.replace("Chain ", ""), visibility)).append("\n");
                    }
                    return sb.toString().trim();
                } else if (targetFormat.contains("Public")) {
                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                    if (cert == null) throw new Exception("No public key / cert found for alias");
                    DetectionResult tempRes = new DetectionResult();
                    tempRes.type = FormatType.DER_PUBLIC_KEY;
                    tempRes.parsedObject = SubjectPublicKeyInfo.getInstance(cert.getPublicKey().getEncoded());
                    return convert(tempRes, targetFormat.replace(" Public", ""), visibility);
                } else if (targetFormat.contains("Private")) {
                    if (visibility != SecretVisibilityProfile.FULL_LAB) {
                        throw new Exception("Private key export is blocked by current SecretVisibilityProfile policy.");
                    }
                    java.security.Key key = ks.getKey(alias, password);
                    if (!(key instanceof PrivateKey)) throw new Exception("Not a private key entry");
                    DetectionResult tempRes = new DetectionResult();
                    tempRes.type = FormatType.DER_PRIVATE_KEY;
                    tempRes.parsedObject = PrivateKeyInfo.getInstance(key.getEncoded());
                    return convert(tempRes, targetFormat.replace(" Private", ""), visibility);
                } else if (targetFormat.contains("Cert")) {
                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                    if (cert == null) throw new Exception("No certificate found for alias");
                    DetectionResult tempRes = detect(cert.getEncoded(), null);
                    return convert(tempRes, targetFormat.replace(" Cert", ""), visibility);
                }
            }
            throw new Exception("Alias not found or unsupported target format");
        } finally {
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
        }
    }

    public boolean validatePair(byte[] primary, byte[] secondary) {
        try {
            DetectionResult r1 = detect(primary, null);
            DetectionResult r2 = detect(secondary, null);

            PublicKey pub = null;
            PrivateKey priv = null;

            if (r1.hasPrivateKey) priv = getPrivateKey(r1);
            else pub = getPublicKey(r1);

            if (r2.hasPrivateKey) priv = getPrivateKey(r2);
            else pub = getPublicKey(r2);

            if (pub == null || priv == null) {
                throw new Exception("Need one public key (or certificate) and one private key.");
            }

            // Check if pub and priv match using signature
            String sigAlgo = getSigAlgo(priv.getAlgorithm());
            if (sigAlgo == null) {
                throw new Exception("Unsupported key algorithm for signature validation: " + priv.getAlgorithm());
            }
            Signature sig = Signature.getInstance(sigAlgo);
            sig.initSign(priv);
            byte[] data = "CryptoForgeChallenge".getBytes();
            sig.update(data);
            byte[] signature = sig.sign();

            sig.initVerify(pub);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private String getSigAlgo(String keyAlg) {
        if ("RSA".equalsIgnoreCase(keyAlg)) return "SHA256withRSA";
        if ("EC".equalsIgnoreCase(keyAlg) || "ECDSA".equalsIgnoreCase(keyAlg)) return "SHA256withECDSA";
        if ("EdDSA".equalsIgnoreCase(keyAlg)) return "EdDSA";
        if ("Ed25519".equalsIgnoreCase(keyAlg)) return "Ed25519";
        if ("Ed448".equalsIgnoreCase(keyAlg)) return "Ed448";
        return null;
    }

    private PublicKey getPublicKey(DetectionResult res) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        if (res.type == FormatType.PEM_PUBLIC_KEY || res.type == FormatType.DER_PUBLIC_KEY) {
            SubjectPublicKeyInfo spki = (SubjectPublicKeyInfo) res.parsedObject;
            return converter.getPublicKey(spki);
        }
        if (res.type == FormatType.PEM_CERTIFICATE) {
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate((X509CertificateHolder) res.parsedObject);
            return cert.getPublicKey();
        }
        if (res.type == FormatType.DER_CERTIFICATE) {
            return ((X509Certificate) res.parsedObject).getPublicKey();
        }
        if (res.type == FormatType.JWK_PUBLIC || res.type == FormatType.JWK_PRIVATE) {
            JWK jwk = (JWK) res.parsedObject;
            if (jwk instanceof RSAKey) return ((RSAKey) jwk).toPublicKey();
            if (jwk instanceof ECKey) return ((ECKey) jwk).toECPublicKey();
            if (jwk instanceof OctetKeyPair) {
                return toEdDsaPublicKey((OctetKeyPair) jwk);
            }
        }
        if (res.type == FormatType.OPENSSH_PUBLIC_KEY) {
            String str = (String) res.parsedObject;
            AsymmetricKeyParameter param = OpenSSHPublicKeyUtil.parsePublicKey(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(param);
            return converter.getPublicKey(spki);
        }
        throw new Exception("Not a supported public key format.");
    }

    private PrivateKey getPrivateKey(DetectionResult res) throws Exception {
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        if (res.type == FormatType.PEM_PRIVATE_KEY || res.type == FormatType.DER_PRIVATE_KEY) {
            if (res.parsedObject instanceof PEMKeyPair) {
                return converter.getPrivateKey(((PEMKeyPair) res.parsedObject).getPrivateKeyInfo());
            }
            if (res.parsedObject instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) res.parsedObject);
            }
        }
        if (res.type == FormatType.JWK_PRIVATE) {
            JWK jwk = (JWK) res.parsedObject;
            if (jwk instanceof RSAKey) return ((RSAKey) jwk).toPrivateKey();
            if (jwk instanceof ECKey) return ((ECKey) jwk).toECPrivateKey();
            if (jwk instanceof OctetKeyPair) {
                return toEdDsaPrivateKey((OctetKeyPair) jwk);
            }
        }
        throw new Exception("Not a supported private key format or it is encrypted.");
    }

    private JWK toJWK(PublicKey pub, PrivateKey priv) throws Exception {
        if (pub instanceof RSAPublicKey) {
            RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) pub);
            if (priv instanceof RSAPrivateKey) {
                builder.privateKey((RSAPrivateKey) priv);
            }
            return builder.build();
        } else if (pub instanceof ECPublicKey) {
            ECPublicKey ecPub = (ECPublicKey) pub;
            com.nimbusds.jose.jwk.Curve curve = com.nimbusds.jose.jwk.Curve.forECParameterSpec(ecPub.getParams());
            if (curve == null) throw new Exception("Unknown EC curve for JWK.");
            ECKey.Builder builder = new ECKey.Builder(curve, ecPub);
            if (priv instanceof ECPrivateKey) {
                builder.privateKey((ECPrivateKey) priv);
            }
            return builder.build();
        }
        if (isEdDsaKey(pub) || isEdDsaKey(priv)) {
            return toEdDsaJwk(pub, priv);
        }
        if (pub == null && priv instanceof ECPrivateKey) {
            // Cannot reliably derive ECPublicKey from ECPrivateKey in standard Java without BouncyCastle internals
            throw new Exception("Converting EC Private Key to JWK requires Public Key components. Not available or not implemented.");
        }
        if (pub == null && priv instanceof RSAPrivateKey) {
            throw new Exception("Converting RSA Private Key to JWK requires Public Key components (Modulus, Public Exponent). Not available or not implemented.");
        }
        throw new Exception("Unsupported key algorithm for JWK conversion.");
    }

    /**
     * The only OKP curves accepted by this service. X25519/X448 are deliberately
     * excluded because they are key-agreement curves, not EdDSA signing keys.
     */
    private static final class OkpJwkMaterial {
        private final String curveName;
        private final byte[] x;
        private final byte[] d;

        private OkpJwkMaterial(String curveName, byte[] x, byte[] d) {
            this.curveName = curveName;
            this.x = x;
            this.d = d;
        }
    }

    private static final class EdDsaKeyMaterial {
        private final String curveName;
        private final byte[] publicBytes;
        private final byte[] privateBytes;

        private EdDsaKeyMaterial(String curveName, byte[] publicBytes, byte[] privateBytes) {
            this.curveName = curveName;
            this.publicBytes = publicBytes;
            this.privateBytes = privateBytes;
        }
    }

    private OkpJwkMaterial validateOkpJwk(JWK jwk) throws Exception {
        if (!(jwk instanceof OctetKeyPair)) {
            throw new Exception("Unsupported JWK key type; only OKP Ed25519 and Ed448 are supported here.");
        }

        OctetKeyPair okp = (OctetKeyPair) jwk;
        String curveName = supportedOkpCurve(okp.getCurve());
        int expectedLength = "Ed25519".equals(curveName) ? Ed25519.PUBLIC_KEY_SIZE : Ed448.PUBLIC_KEY_SIZE;
        byte[] x = decodeOkpField(okp.getX(), "x", curveName, expectedLength);
        byte[] d = okp.getD() == null
                ? null
                : decodeOkpField(okp.getD(), "d", curveName, expectedLength);

        validateEdDsaPublicMaterial(curveName, x);
        if (d != null) {
            byte[] derived = deriveEdDsaPublic(curveName, d);
            if (!java.security.MessageDigest.isEqual(x, derived)) {
                throw invalidEdDsaMaterial(curveName);
            }
        }
        return new OkpJwkMaterial(curveName, x, d);
    }

    private String supportedOkpCurve(Curve curve) throws Exception {
        if (Curve.Ed25519.equals(curve)) return "Ed25519";
        if (Curve.Ed448.equals(curve)) return "Ed448";
        String curveName = curve == null ? "missing" : safeCurveLabel(curve.getName());
        throw new Exception("Unsupported JWK OKP curve: " + curveName + "; only Ed25519 and Ed448 are supported.");
    }

    private byte[] decodeOkpField(Base64URL value, String field, String curveName, int expectedLength) throws Exception {
        if (value == null) {
            throw new Exception("Invalid JWK OKP " + curveName + " structure: " + field + " is missing.");
        }
        final byte[] decoded;
        try {
            decoded = value.decode();
        } catch (Exception e) {
            throw new Exception("Invalid JWK OKP " + curveName + " structure: " + field + " is invalid.");
        }
        if (decoded == null || decoded.length != expectedLength) {
            throw new Exception("Invalid JWK OKP " + curveName + " structure: " + field
                    + " has an invalid length.");
        }
        return decoded;
    }

    private void validateEdDsaPublicMaterial(String curveName, byte[] publicBytes) throws Exception {
        boolean valid = "Ed25519".equals(curveName)
                ? Ed25519.validatePublicKeyFull(publicBytes, 0)
                : Ed448.validatePublicKeyFull(publicBytes, 0);
        if (!valid) {
            throw invalidEdDsaMaterial(curveName);
        }
    }

    private byte[] deriveEdDsaPublic(String curveName, byte[] privateBytes) throws Exception {
        try {
            if ("Ed25519".equals(curveName)) {
                return new Ed25519PrivateKeyParameters(privateBytes, 0).generatePublicKey().getEncoded();
            }
            return new Ed448PrivateKeyParameters(privateBytes, 0).generatePublicKey().getEncoded();
        } catch (Exception e) {
            throw invalidEdDsaMaterial(curveName);
        }
    }

    private Exception invalidEdDsaMaterial(String curveName) {
        return new Exception("Invalid JWK OKP " + curveName + " key material.");
    }

    private PublicKey toEdDsaPublicKey(OctetKeyPair jwk) throws Exception {
        OkpJwkMaterial material = validateOkpJwk(jwk);
        try {
            AsymmetricKeyParameter parameters = edDsaPublicParameters(material.curveName, material.x);
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(parameters);
            return new JcaPEMKeyConverter().getPublicKey(spki);
        } catch (Exception e) {
            throw invalidEdDsaMaterial(material.curveName);
        }
    }

    private PrivateKey toEdDsaPrivateKey(OctetKeyPair jwk) throws Exception {
        OkpJwkMaterial material = validateOkpJwk(jwk);
        if (material.d == null) {
            throw new Exception("Invalid JWK OKP " + material.curveName + " private structure: d is missing.");
        }
        try {
            AsymmetricKeyParameter parameters = edDsaPrivateParameters(material.curveName, material.d);
            PrivateKeyInfo pkcs8 = PrivateKeyInfoFactory.createPrivateKeyInfo(parameters);
            return new JcaPEMKeyConverter().getPrivateKey(pkcs8);
        } catch (Exception e) {
            throw invalidEdDsaMaterial(material.curveName);
        }
    }

    private AsymmetricKeyParameter edDsaPublicParameters(String curveName, byte[] publicBytes) {
        if ("Ed25519".equals(curveName)) return new Ed25519PublicKeyParameters(publicBytes, 0);
        return new Ed448PublicKeyParameters(publicBytes, 0);
    }

    private AsymmetricKeyParameter edDsaPrivateParameters(String curveName, byte[] privateBytes) {
        if ("Ed25519".equals(curveName)) return new Ed25519PrivateKeyParameters(privateBytes, 0);
        return new Ed448PrivateKeyParameters(privateBytes, 0);
    }

    private boolean isEdDsaKey(java.security.Key key) {
        if (key == null || key.getAlgorithm() == null) return false;
        String algorithm = key.getAlgorithm();
        return "EdDSA".equalsIgnoreCase(algorithm)
                || "Ed25519".equalsIgnoreCase(algorithm)
                || "Ed448".equalsIgnoreCase(algorithm);
    }

    private JWK toEdDsaJwk(PublicKey pub, PrivateKey priv) throws Exception {
        EdDsaKeyMaterial publicMaterial = pub == null ? null : extractEdDsaPublicMaterial(pub);
        EdDsaKeyMaterial privateMaterial = priv == null ? null : extractEdDsaPrivateMaterial(priv);

        String curveName;
        byte[] publicBytes;
        byte[] privateBytes = null;
        if (publicMaterial != null && privateMaterial != null) {
            if (!publicMaterial.curveName.equals(privateMaterial.curveName)) {
                throw new Exception("EdDSA public/private key curves do not match.");
            }
            if (!java.security.MessageDigest.isEqual(publicMaterial.publicBytes, privateMaterial.publicBytes)) {
                throw new Exception("EdDSA public/private key material does not match.");
            }
            curveName = publicMaterial.curveName;
            publicBytes = publicMaterial.publicBytes;
            privateBytes = privateMaterial.privateBytes;
        } else if (publicMaterial != null) {
            curveName = publicMaterial.curveName;
            publicBytes = publicMaterial.publicBytes;
        } else if (privateMaterial != null) {
            curveName = privateMaterial.curveName;
            publicBytes = privateMaterial.publicBytes;
            privateBytes = privateMaterial.privateBytes;
        } else {
            throw new Exception("No EdDSA key material was supplied.");
        }

        Curve curve = "Ed25519".equals(curveName) ? Curve.Ed25519 : Curve.Ed448;
        OctetKeyPair.Builder builder = new OctetKeyPair.Builder(curve, Base64URL.encode(publicBytes));
        if (privateBytes != null) {
            builder.d(Base64URL.encode(privateBytes));
        }
        return builder.build();
    }

    private EdDsaKeyMaterial extractEdDsaPublicMaterial(PublicKey publicKey) throws Exception {
        try {
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
            AsymmetricKeyParameter parameters = PublicKeyFactory.createKey(spki);
            if (parameters instanceof Ed25519PublicKeyParameters) {
                byte[] publicBytes = ((Ed25519PublicKeyParameters) parameters).getEncoded();
                validateEdDsaPublicMaterial("Ed25519", publicBytes);
                return new EdDsaKeyMaterial("Ed25519", publicBytes, null);
            }
            if (parameters instanceof Ed448PublicKeyParameters) {
                byte[] publicBytes = ((Ed448PublicKeyParameters) parameters).getEncoded();
                validateEdDsaPublicMaterial("Ed448", publicBytes);
                return new EdDsaKeyMaterial("Ed448", publicBytes, null);
            }
        } catch (Exception e) {
            // Deliberately replace provider/parser details with a structural diagnostic.
        }
        throw new Exception("Invalid EdDSA public key material.");
    }

    private EdDsaKeyMaterial extractEdDsaPrivateMaterial(PrivateKey privateKey) throws Exception {
        try {
            PrivateKeyInfo pkcs8 = PrivateKeyInfo.getInstance(privateKey.getEncoded());
            AsymmetricKeyParameter parameters = PrivateKeyFactory.createKey(pkcs8);
            if (parameters instanceof Ed25519PrivateKeyParameters) {
                byte[] privateBytes = ((Ed25519PrivateKeyParameters) parameters).getEncoded();
                return new EdDsaKeyMaterial("Ed25519", deriveEdDsaPublic("Ed25519", privateBytes), privateBytes);
            }
            if (parameters instanceof Ed448PrivateKeyParameters) {
                byte[] privateBytes = ((Ed448PrivateKeyParameters) parameters).getEncoded();
                return new EdDsaKeyMaterial("Ed448", deriveEdDsaPublic("Ed448", privateBytes), privateBytes);
            }
        } catch (Exception e) {
            // Deliberately replace provider/parser details with a structural diagnostic.
        }
        throw new Exception("Invalid EdDSA private key material.");
    }

    private String describeMalformedJwk(String json) {
        try {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(json);
            if (root.isJsonObject()) {
                com.google.gson.JsonObject object = root.getAsJsonObject();
                String kty = jsonStringField(object, "kty");
                if ("OKP".equals(kty)) {
                    String curve = jsonStringField(object, "crv");
                    if (curve == null) {
                        return "Invalid JWK OKP structure: curve is missing or invalid.";
                    }
                    if (!"Ed25519".equals(curve) && !"Ed448".equals(curve)) {
                        return "Unsupported JWK OKP curve: " + safeCurveLabel(curve)
                                + "; only Ed25519 and Ed448 are supported.";
                    }
                    return "Invalid JWK OKP " + curve + " structure: x/d material is missing or invalid.";
                }
            }
        } catch (Exception ignored) {
            // Fall through to a generic structural diagnostic.
        }
        return "Invalid JWK structure.";
    }

    private String jsonStringField(com.google.gson.JsonObject object, String name) {
        com.google.gson.JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private String safeCurveLabel(String curve) {
        if (curve == null || curve.isEmpty()) return "missing";
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < curve.length() && safe.length() < 32; i++) {
            char c = curve.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                safe.append(c);
            } else {
                safe.append('?');
            }
        }
        return safe.toString();
    }

    private String toPEM(String type, byte[] data) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(new PemObject(type, data));
        }
        return sw.toString().trim();
    }

    private void populateCertInfo(DetectionResult res, X509CertificateHolder holder) {
        res.subject = holder.getSubject().toString();
        res.issuer = holder.getIssuer().toString();
        res.algorithm = resolveOid(holder.getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm().getId());
        res.notBefore = holder.getNotBefore().toString();
        res.notAfter = holder.getNotAfter().toString();
        try {
            res.sha256Fingerprint = calculateFingerprint(holder.getEncoded());
            PublicKey pub = new JcaX509CertificateConverter().getCertificate(holder).getPublicKey();
            res.keySize = getKeySize(pub);
        } catch (Exception e) {}
    }

    private void populatePublicKeyInfo(DetectionResult res, SubjectPublicKeyInfo spki) {
        res.algorithm = resolveOid(spki.getAlgorithm().getAlgorithm().getId());
        try {
            res.sha256Fingerprint = calculateFingerprint(spki.getEncoded());
            PublicKey pub = new JcaPEMKeyConverter().getPublicKey(spki);
            res.keySize = getKeySize(pub);
        } catch (Exception e) {}
    }

    private void populatePrivateKeyInfo(DetectionResult res, PrivateKeyInfo pki) {
        res.algorithm = resolveOid(pki.getPrivateKeyAlgorithm().getAlgorithm().getId());
        try {
            res.sha256Fingerprint = calculateFingerprint(pki.getEncoded());
            PrivateKey priv = new JcaPEMKeyConverter().getPrivateKey(pki);
            res.keySize = getKeySize(priv);
        } catch (Exception e) {}
    }

    private int getKeySize(java.security.Key key) {
        if (key instanceof java.security.interfaces.RSAKey) {
            return ((java.security.interfaces.RSAKey) key).getModulus().bitLength();
        }
        if (key instanceof java.security.interfaces.ECKey) {
            return ((java.security.interfaces.ECKey) key).getParams().getCurve().getField().getFieldSize();
        }
        return 0;
    }

    private String calculateFingerprint(byte[] derBytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(derBytes);
            return com.cryptocarver.util.DataConverter.bytesToHex(hash).toUpperCase().replaceAll("..", "$0:").replaceAll(":$", "");
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveOid(String oid) {
        if ("1.2.840.113549.1.1.1".equals(oid)) return "RSA";
        if ("1.2.840.10045.2.1".equals(oid)) return "EC";
        if ("1.2.840.10040.4.1".equals(oid)) return "DSA";
        if ("1.3.101.112".equals(oid)) return "Ed25519";
        if ("1.3.101.113".equals(oid)) return "Ed448";
        return oid;
    }
}
