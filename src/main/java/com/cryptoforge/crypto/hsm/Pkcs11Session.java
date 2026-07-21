package com.cryptoforge.crypto.hsm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import eu.europa.esig.dss.token.AbstractKeyStoreTokenConnection;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.GCMParameterSpec;

/**
 * A short-lived connection to a real PKCS#11 token through the JDK provider.
 *
 * <p>The session never calls {@code Key.getEncoded()} for token keys. Private
 * and secret material stay in the provider: this class passes their opaque key
 * handles to JCA {@link Signature}, {@link Mac} and {@link Cipher} instances.
 * It is a laboratory integration, not a replacement for vendor PKCS#11 tools.
 * </p>
 */
public final class Pkcs11Session implements AutoCloseable {
    private final Pkcs11Configuration configuration;
    private final Provider provider;
    private final KeyStore keyStore;
    private final Path temporaryConfig;
    private boolean closed;

    private Pkcs11Session(Pkcs11Configuration configuration, Provider provider,
            KeyStore keyStore, Path temporaryConfig) {
        this.configuration = configuration;
        this.provider = provider;
        this.keyStore = keyStore;
        this.temporaryConfig = temporaryConfig;
    }

    /** Opens a session using a native PKCS#11 library and a transient PIN. */
    public static Pkcs11Session open(Pkcs11Configuration configuration, char[] pin)
            throws GeneralSecurityException, IOException {
        if (configuration == null) {
            throw new IllegalArgumentException("PKCS#11 configuration is required");
        }
        if (!Files.isRegularFile(configuration.library())) {
            throw new IllegalArgumentException("PKCS#11 library was not found: " + configuration.library());
        }
        Provider baseProvider = Security.getProvider("SunPKCS11");
        if (baseProvider == null) {
            throw new GeneralSecurityException("The current JVM does not provide SunPKCS11");
        }

        Path configFile = Files.createTempFile("cryptocarver-pkcs11-", ".cfg");
        char[] transientPin = pin == null ? new char[0] : pin.clone();
        try {
            Files.writeString(configFile, configuration.toSunPkcs11Configuration(), StandardCharsets.UTF_8);
            Provider configuredProvider = baseProvider.configure(configFile.toString());
            KeyStore tokenStore = KeyStore.getInstance("PKCS11", configuredProvider);
            tokenStore.load(null, transientPin);
            return new Pkcs11Session(configuration, configuredProvider, tokenStore, configFile);
        } catch (GeneralSecurityException | IOException failure) {
            Files.deleteIfExists(configFile);
            throw failure;
        } finally {
            Arrays.fill(transientPin, '\0');
        }
    }

    public Pkcs11Configuration configuration() {
        return configuration;
    }

    public String providerName() {
        return provider.getName();
    }

    /** Lists public metadata only; token keys are never exported. */
    public List<Pkcs11ObjectInfo> listObjects() throws GeneralSecurityException {
        ensureOpen();
        try {
            List<Pkcs11ObjectInfo> objects = new ArrayList<>();
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                objects.add(describe(alias));
            }
            return Collections.unmodifiableList(objects);
        } catch (java.security.KeyStoreException error) {
            throw new GeneralSecurityException("Unable to enumerate PKCS#11 token objects", error);
        }
    }

    /**
     * Lists JCA services advertised by the configured SunPKCS11 provider for a
     * service type (for example {@code Signature}, {@code Cipher} or {@code Mac}).
     * This is a compatibility diagnostic, not a direct PKCS#11 C_GetMechanismList
     * call: a specific token object can still reject an advertised service.
     */
    public java.util.Set<String> getSupportedMechanisms(String serviceType) {
        ensureOpen();
        if (serviceType == null || serviceType.isBlank()) return java.util.Collections.emptySet();
        java.util.Set<String> algorithms = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Provider.Service service : provider.getServices()) {
            if (serviceType.equalsIgnoreCase(service.getType())) {
                algorithms.add(service.getAlgorithm());
            }
        }
        return java.util.Collections.unmodifiableSet(algorithms);
    }

    /** Lists aliases with a public certificate or certificate chain available from the token. */
        public List<String> listPrivateKeysWithCertificate() throws GeneralSecurityException {
        ensureOpen();
        try {
            List<String> aliases = new ArrayList<>();
            Enumeration<String> names = keyStore.aliases();
            while (names.hasMoreElements()) {
                String alias = names.nextElement();
                if (keyStore.isKeyEntry(alias) && keyStore.getCertificate(alias) != null) {
                    java.security.Key key = keyStore.getKey(alias, null);
                    if (key instanceof java.security.PrivateKey) {
                        aliases.add(alias);
                    }
                }
            }
            return Collections.unmodifiableList(aliases);
        } catch (Exception error) {
            throw new GeneralSecurityException("Unable to enumerate PKCS#11 private keys with certificates", error);
        }
    }

    public List<String> listCertificateAliases() throws GeneralSecurityException {
        ensureOpen();
        try {
            List<String> aliases = new ArrayList<>();
            Enumeration<String> names = keyStore.aliases();
            while (names.hasMoreElements()) {
                String alias = names.nextElement();
                if (keyStore.getCertificate(alias) != null) aliases.add(alias);
            }
            return Collections.unmodifiableList(aliases);
        } catch (java.security.KeyStoreException error) {
            throw new GeneralSecurityException("Unable to enumerate PKCS#11 certificates", error);
        }
    }

    /**
     * Updates the certificate chain associated with a PKCS#11 token alias.
     * The private key is resolved internally and never escapes the session.
     */
    public void updateCertificateChain(String alias, Certificate[] chain) throws GeneralSecurityException {
        ensureOpen();
        if (alias == null || chain == null || chain.length == 0) {
            throw new IllegalArgumentException("Alias and certificate chain are required");
        }

        try {
            if (!keyStore.isKeyEntry(alias)) {
                throw new GeneralSecurityException("Alias does not exist or is not a key entry: " + alias);
            }

            Key key = keyStore.getKey(alias, null);
            if (key == null) {
                throw new GeneralSecurityException("Private key is not accessible for alias: " + alias);
            }

            List<java.security.cert.X509Certificate> x509Chain = new ArrayList<>();
            java.util.Set<java.security.cert.X509Certificate> uniqueCerts = new java.util.HashSet<>();
            for (Certificate cert : chain) {
                if (!(cert instanceof java.security.cert.X509Certificate x509Cert)) {
                    throw new GeneralSecurityException("Chain contains non-X.509 certificate");
                }
                if (!uniqueCerts.add(x509Cert)) {
                    throw new GeneralSecurityException("Chain contains duplicate certificates");
                }
                x509Chain.add(x509Cert);
            }

            // Determine the unique leaf only from verified issuer relationships.
            java.security.cert.X509Certificate leaf = null;
            for (java.security.cert.X509Certificate cert : x509Chain) {
                boolean isIssuer = false;
                for (java.security.cert.X509Certificate other : x509Chain) {
                    if (cert != other && isVerifiedIssuer(cert, other)) {
                        isIssuer = true;
                        break;
                    }
                }
                if (!isIssuer) {
                    if (leaf != null) throw new GeneralSecurityException("Chain contains multiple leaves or is disconnected");
                    leaf = cert;
                }
            }
            if (leaf == null) {
                throw new GeneralSecurityException("Could not determine a unique leaf in the chain");
            }

            // Match keys cryptographically
            Certificate currentTokenCert = keyStore.getCertificate(alias);
            if (currentTokenCert == null) {
                throw new GeneralSecurityException("Token has no existing certificate for alias: " + alias);
            }
            java.security.PublicKey tokenPubKey = currentTokenCert.getPublicKey();
            java.security.PublicKey leafPubKey = leaf.getPublicKey();

            if (!java.util.Arrays.equals(tokenPubKey.getEncoded(), leafPubKey.getEncoded())) {
                throw new GeneralSecurityException("Public key in the provided leaf certificate does not match the token's public key");
            }

            // Validate PKIX offline
            com.cryptoforge.crypto.CertificateGenerator.ChainValidationResult validation = com.cryptoforge.crypto.CertificateGenerator.validateCertificateChain(x509Chain);
            if (!validation.isValid) {
                throw new GeneralSecurityException("PKIX validation failed for the new chain: " + validation.message);
            }

            // Build a complete leaf-to-root chain from verified issuer links. This
            // both normalizes caller order and rejects disconnected certificates.
            List<Certificate> finalChain = new ArrayList<>();
            java.security.cert.X509Certificate current = leaf;
            while (true) {
                finalChain.add(current);
                if (current.getSubjectX500Principal().equals(current.getIssuerX500Principal())) {
                    break;
                }

                java.security.cert.X509Certificate issuer = null;
                for (java.security.cert.X509Certificate candidate : x509Chain) {
                    if (candidate != current && isVerifiedIssuer(candidate, current)) {
                        if (issuer != null) {
                            throw new GeneralSecurityException("Chain contains multiple cryptographic issuers");
                        }
                        issuer = candidate;
                    }
                }
                if (issuer == null || finalChain.contains(issuer)) {
                    throw new GeneralSecurityException("Chain is incomplete or contains an issuer cycle");
                }
                current = issuer;
            }
            if (finalChain.size() != x509Chain.size()) {
                throw new GeneralSecurityException("Chain contains disconnected certificates");
            }

            // JCA KeyStore allows updating the chain by setting the key entry again
            keyStore.setKeyEntry(alias, key, null, finalChain.toArray(new Certificate[0]));
        } catch (GeneralSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to update certificate chain on token for alias: " + alias, e);
        }
    }

    private static boolean isVerifiedIssuer(X509Certificate issuer, X509Certificate certificate)
            throws GeneralSecurityException {
        if (!issuer.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
            return false;
        }
        try {
            certificate.verify(issuer.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }



    /**
     * Signs XAdES with an opaque token-resident private key. This is intentionally
     * the only XAdES entry point exposed by the session: neither the keystore nor
     * a raw private-key reference escapes the PKCS#11 boundary.
     */
    public String signXAdES(String xmlContent, String alias, String level, String tsaUrl, String packaging,
                            com.cryptoforge.model.TsaAuthCredentials auth) throws Exception {
        ensureOpen();
        try (AbstractKeyStoreTokenConnection token = createDssTokenConnection()) {
            return com.cryptoforge.crypto.XMLSignatureOperations.signXAdESWithTokenConnection(
                    xmlContent, token, alias, level, tsaUrl, packaging, auth);
        }
    }

    /**
     * Signs a PDF through DSS using an opaque PKCS#11 key. The alias is
     * resolved inside this session and the private key is never returned to a
     * controller or another application layer.
     */
    public byte[] signPades(String alias, byte[] pdf, String tsaUrl) throws Exception {
        return signPades(alias, pdf, tsaUrl, null);
    }

    /** Token-backed PAdES signature with an optional visible PDF appearance. */
    public byte[] signPades(String alias, byte[] pdf, String tsaUrl,
                             com.cryptoforge.crypto.PadesOperations.VisibleSignatureOptions visibleSignature) throws Exception {
        ensureOpen();
        try (AbstractKeyStoreTokenConnection token = createDssTokenConnection()) {
            return com.cryptoforge.crypto.PadesOperations.signWithTokenConnection(pdf, token, alias, tsaUrl, visibleSignature);
        }
    }

    /** Builds a DSS adapter while keeping the backing token keystore private. */
    private AbstractKeyStoreTokenConnection createDssTokenConnection() {
        return new AbstractKeyStoreTokenConnection() {
            @Override
            protected KeyStore getKeyStore() {
                return keyStore;
            }
            @Override
            protected KeyStore.PasswordProtection getKeyProtectionParameter() {
                return new KeyStore.PasswordProtection(new char[0]);
            }
            @Override
            public void close() { }
        };
    }

    public X509Certificate[] getCertificateChain(String alias) throws GeneralSecurityException {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Certificate alias is required");
        try {
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null || chain.length == 0) {
                Certificate certificate = keyStore.getCertificate(alias);
                if (certificate == null) throw new GeneralSecurityException("No certificate is available for token object " + alias);
                chain = new Certificate[]{certificate};
            }
            X509Certificate[] x509Chain = new X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (!(chain[i] instanceof X509Certificate)) {
                    throw new GeneralSecurityException("Certificate in chain is not X.509");
                }
                x509Chain[i] = (X509Certificate) chain[i];
            }
            return x509Chain;
        } catch (Exception error) {
            throw new GeneralSecurityException("Unable to retrieve PKCS#11 certificate chain", error);
        }
    }

    /** Exports only public X.509 certificates; it can never export a token private key. */
    public String certificateChainPem(String alias) throws GeneralSecurityException {
        ensureOpen();
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Certificate alias is required");
        try {
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null || chain.length == 0) {
                Certificate certificate = keyStore.getCertificate(alias);
                if (certificate == null) throw new GeneralSecurityException("No certificate is available for token object " + alias);
                chain = new Certificate[]{certificate};
            }
            StringBuilder pem = new StringBuilder();
            for (Certificate certificate : chain) {
                pem.append("-----BEGIN CERTIFICATE-----\n")
                        .append(Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                                .encodeToString(certificate.getEncoded()))
                        .append("\n-----END CERTIFICATE-----\n");
            }
            return pem.toString();
        } catch (java.security.KeyStoreException error) {
            throw new GeneralSecurityException("Unable to export PKCS#11 certificate chain", error);
        }
    }

    public byte[] sign(String alias, byte[] data, String algorithm) throws GeneralSecurityException {
        PrivateKey key = requireKey(alias, PrivateKey.class);
        Signature signature = Signature.getInstance(requireText(algorithm, "Signature algorithm"), provider);
        signature.initSign(key);
        signature.update(nonNullBytes(data, "Data"));
        return signature.sign();
    }

    public boolean verify(String alias, byte[] data, byte[] signatureBytes, String algorithm)
            throws GeneralSecurityException {
        PublicKey key = getPublicKey(alias);
        Signature signature = Signature.getInstance(requireText(algorithm, "Signature algorithm"), provider);
        signature.initVerify(key);
        signature.update(nonNullBytes(data, "Data"));
        return signature.verify(nonNullBytes(signatureBytes, "Signature"));
    }

    public byte[] mac(String alias, byte[] data, String algorithm) throws GeneralSecurityException {
        SecretKey key = requireKey(alias, SecretKey.class);
        Mac mac = Mac.getInstance(requireText(algorithm, "MAC algorithm"), provider);
        mac.init(key);
        return mac.doFinal(nonNullBytes(data, "Data"));
    }

    /** Creates CMS/PKCS#7 SignedData using the opaque private key in this token. */
    public byte[] signCms(String alias, byte[] data, boolean detached) throws GeneralSecurityException {
        ensureOpen();
        try {
            PrivateKey privateKey = requireKey(alias, PrivateKey.class);
            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new GeneralSecurityException("PKCS#11 signing object " + alias + " has no X.509 certificate");
            }
            return com.cryptoforge.crypto.CMSOperations.generateSignedDataWithProvider(
                    nonNullBytes(data, "Data"), x509Certificate, privateKey, provider, Collections.emptyMap(), detached).pkcs7;
        } catch (GeneralSecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new GeneralSecurityException("Unable to create CMS with PKCS#11 object " + alias, error);
        }
    }

    /** Creates CAdES-BES SignedData with the opaque token key and its certificate. */
    public byte[] signCadesBes(String alias, byte[] data, boolean detached) throws GeneralSecurityException {
        ensureOpen();
        try {
            PrivateKey privateKey = requireKey(alias, PrivateKey.class);
            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new GeneralSecurityException("PKCS#11 signing object " + alias + " has no X.509 certificate");
            }
            return com.cryptoforge.crypto.CMSOperations.generateCadesBesWithProvider(
                    nonNullBytes(data, "Data"), x509Certificate, privateKey, provider, Collections.emptyMap(), detached).pkcs7;
        } catch (GeneralSecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new GeneralSecurityException("Unable to create CAdES-BES with PKCS#11 object " + alias, error);
        }
    }

    /** Creates an ASiC-S container from a token-resident CAdES-BES signing key. */
    public byte[] createAsicS(String alias, byte[] payload, String payloadName) throws Exception {
        ensureOpen();
        byte[] signature = signCadesBes(alias, payload, true);
        return com.cryptoforge.crypto.AsicOperations.createAsicSWithCadesSignature(payload, payloadName, signature);
    }

    /** Creates ASiC-E by signing the canonical manifest with a token-resident CAdES-BES key. */
    public byte[] createAsicE(String alias, java.util.Map<String, byte[]> payloads) throws Exception {
        ensureOpen();
        com.cryptoforge.crypto.AsicOperations.AsicEManifest prepared =
                com.cryptoforge.crypto.AsicOperations.prepareAsicEManifest(payloads);
        byte[] signature = signCadesBes(alias, prepared.manifest(), true);
        return com.cryptoforge.crypto.AsicOperations.createAsicEWithCadesSignature(
                prepared.payloads(), prepared.manifest(), signature);
    }

    public byte[] encrypt(String alias, byte[] plaintext, String transformation, byte[] iv)
            throws GeneralSecurityException {
        return encrypt(alias, plaintext, transformation, iv, null);
    }

    public byte[] encrypt(String alias, byte[] plaintext, String transformation, byte[] iv, byte[] aad)
            throws GeneralSecurityException {
        return cipher(Cipher.ENCRYPT_MODE, alias, plaintext, transformation, iv, aad);
    }

    public byte[] decrypt(String alias, byte[] ciphertext, String transformation, byte[] iv)
            throws GeneralSecurityException {
        return decrypt(alias, ciphertext, transformation, iv, null);
    }

    public byte[] decrypt(String alias, byte[] ciphertext, String transformation, byte[] iv, byte[] aad)
            throws GeneralSecurityException {
        return cipher(Cipher.DECRYPT_MODE, alias, ciphertext, transformation, iv, aad);
    }

    private byte[] cipher(int mode, String alias, byte[] input, String transformation, byte[] iv, byte[] aad)
            throws GeneralSecurityException {
        SecretKey key = requireKey(alias, SecretKey.class);
        Cipher cipher = Cipher.getInstance(requireText(transformation, "Cipher transformation"), provider);
        if (iv == null || iv.length == 0) {
            cipher.init(mode, key);
        } else if (transformation.toUpperCase(java.util.Locale.ROOT).contains("/GCM/")) {
            cipher.init(mode, key, new GCMParameterSpec(128, iv));
        } else {
            cipher.init(mode, key, new IvParameterSpec(iv));
        }
        if (aad != null && aad.length > 0) cipher.updateAAD(aad);
        return cipher.doFinal(nonNullBytes(input, mode == Cipher.ENCRYPT_MODE ? "Plaintext" : "Ciphertext"));
    }

    private Pkcs11ObjectInfo describe(String alias) throws GeneralSecurityException {
        try {
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate != null && !keyStore.isKeyEntry(alias)) {
                return new Pkcs11ObjectInfo(alias, "Certificate", certificate.getType(), "X.509",
                        fingerprint(certificate.getEncoded()));
            }
            Key key = keyStore.getKey(alias, null);
            if (key == null) {
                return new Pkcs11ObjectInfo(alias, "Unknown", "Unknown", "Unknown", "Unavailable");
            }
            return new Pkcs11ObjectInfo(alias, keyType(key), key.getAlgorithm(),
                    key.getFormat() == null ? "Token-resident" : key.getFormat(), "Not exported");
        } catch (Exception error) {
            throw new GeneralSecurityException("Unable to inspect PKCS#11 object: " + alias, error);
        }
    }

    /**
     * Decrypts CMS EnvelopedData using the private key associated with the given alias.
     * The private key is not exposed; the decryption delegates to CMSOperations
     * while injecting the native PKCS#11 provider.
     *
     * @param alias    The alias of the private key
     * @param cmsBytes The PKCS#7 EnvelopedData bytes
     * @return The decrypted content
     * @throws GeneralSecurityException if decryption fails
     */
    public byte[] decryptCms(String alias, byte[] cmsBytes) throws GeneralSecurityException {
        ensureOpen();
        PrivateKey privateKey = requireKey(alias, PrivateKey.class);
        try {
            return com.cryptoforge.crypto.CMSOperations.decryptEnvelopedData(cmsBytes, privateKey, provider);
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to decrypt CMS EnvelopedData", e);
        }
    }

    public PublicKey getPublicKey(String alias) throws GeneralSecurityException {
        try {
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate != null) {
                return certificate.getPublicKey();
            }
            return requireKey(alias, PublicKey.class);
        } catch (java.security.KeyStoreException error) {
            throw new GeneralSecurityException("Unable to resolve PKCS#11 public key: " + alias, error);
        }
    }

    /**
     * Generate Certificate Signing Request (CSR) directly in the PKCS#11 token.
     * The private key is never extracted; signing happens on the token.
     */
    public String generateCsr(String alias, com.cryptoforge.crypto.CertificateGenerator.CertificateConfig config) throws Exception {
        ensureOpen();
        String dn = com.cryptoforge.crypto.CertificateGenerator.buildDistinguishedName(config);
        X500Name subject = new X500Name(dn);

        PublicKey publicKey = getPublicKey(alias);
        PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(subject, publicKey);

        if (config.addSubjectAlternativeNames && (!config.sanDnsNames.isEmpty() || !config.sanIpAddresses.isEmpty())) {
            ExtensionsGenerator extensions = new ExtensionsGenerator();
            extensions.addExtension(Extension.subjectAlternativeName, false, com.cryptoforge.crypto.CertificateGenerator.buildSubjectAlternativeNames(config));
            csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions.generate());
        }

        PrivateKey privateKey = requireKey(alias, PrivateKey.class);
        ContentSigner signer = new JcaContentSignerBuilder(config.signatureAlgorithm)
                .setProvider(provider)
                .build(privateKey);

        PKCS10CertificationRequest csr = csrBuilder.build(signer);
        byte[] encoded = csr.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE REQUEST-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64.substring(i, Math.min(i + 64, base64.length()))).append("\n");
        }
        pem.append("-----END CERTIFICATE REQUEST-----\n");
        return pem.toString();
    }

    private <T extends Key> T requireKey(String alias, Class<T> expectedType) throws GeneralSecurityException {
        ensureOpen();
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("PKCS#11 object alias is required");
        }
        try {
            Key key = keyStore.getKey(alias, null);
            if (!expectedType.isInstance(key)) {
                throw new GeneralSecurityException("PKCS#11 object " + alias + " is not a " + expectedType.getSimpleName());
            }
            return expectedType.cast(key);
        } catch (java.security.KeyStoreException error) {
            throw new GeneralSecurityException("Unable to obtain PKCS#11 key handle: " + alias, error);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PKCS#11 session is closed");
        }
    }

    private static String keyType(Key key) {
        if (key instanceof PrivateKey) return "Private key";
        if (key instanceof PublicKey) return "Public key";
        if (key instanceof SecretKey) return "Secret key";
        return "Key";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static byte[] nonNullBytes(byte[] value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String fingerprint(byte[] encoded) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02X", value));
        return result.toString();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            Files.deleteIfExists(temporaryConfig);
        } catch (IOException ignored) {
            // The temporary OS file contains no PIN or token secrets.
        }
    }
}
