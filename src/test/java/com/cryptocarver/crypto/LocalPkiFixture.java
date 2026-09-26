package com.cryptocarver.crypto;

import com.sun.net.httpserver.HttpServer;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.jcajce.*;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Ephemeral three-level PKI and loopback-only RFC 3161 responder for integration tests. */
final class LocalPkiFixture implements AutoCloseable {
    static final char[] PASSWORD = "local-fixture-password".toCharArray();
    static final Date NOT_BEFORE = new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime();
    static final Date NOT_AFTER = new GregorianCalendar(2045, Calendar.JANUARY, 1).getTime();

    final KeyPair rootKeys, intermediateKeys, signerKeys, tsaKeys;
    final X509Certificate root, intermediate, signer, tsa;
    final X509CRL goodCrl, revokedCrl, rootCrl;
    final byte[] goodOcsp, revokedOcsp;
    final Path directory, pkcs12, goodCrlFile, revokedCrlFile, rootCrlFile, goodOcspFile, revokedOcspFile;
    final HttpServer tsaServer;
    final String tsaUrl, revocationUrl;
    private final AtomicInteger revocationDownloadAttempts = new AtomicInteger();

    LocalPkiFixture(Path directory) throws Exception {
        this.directory = directory;
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        Files.createDirectories(directory);
        tsaServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tsaUrl = "http://127.0.0.1:" + tsaServer.getAddress().getPort() + "/tsa";
        revocationUrl = "http://127.0.0.1:" + tsaServer.getAddress().getPort();
        tsaServer.createContext("/tsa", exchange -> {
            try {
                byte[] requestBytes = exchange.getRequestBody().readAllBytes();
                TimeStampRequest request = new TimeStampRequest(requestBytes);
                byte[] encoded = LocalTimestampAuthority.response(request, timestampGenerator(),
                        BigInteger.valueOf(System.nanoTime() & Long.MAX_VALUE), new Date());
                exchange.getResponseHeaders().set("Content-Type", "application/timestamp-reply");
                exchange.sendResponseHeaders(200, encoded.length);
                try (OutputStream body = exchange.getResponseBody()) { body.write(encoded); }
            } catch (Exception failure) { exchange.close(); }
        });
        com.sun.net.httpserver.HttpHandler forbiddenRevocationFetch = exchange -> {
            revocationDownloadAttempts.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        };
        tsaServer.createContext("/crl", forbiddenRevocationFetch);
        tsaServer.createContext("/ocsp", forbiddenRevocationFetch);
        tsaServer.createContext("/", forbiddenRevocationFetch);
        tsaServer.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        rootKeys = generator.generateKeyPair(); intermediateKeys = generator.generateKeyPair();
        signerKeys = generator.generateKeyPair(); tsaKeys = generator.generateKeyPair();
        X500Name rootName = new X500Name("CN=CryptoCarver Offline Test Root");
        X500Name intermediateName = new X500Name("CN=CryptoCarver Offline Test Intermediate");
        X500Name signerName = new X500Name("CN=CryptoCarver Offline Test Signer");
        root = issue(rootName, BigInteger.ONE, rootName, rootKeys.getPublic(), rootKeys.getPrivate(), true, null, true);
        intermediate = issue(intermediateName, BigInteger.TWO, rootName, intermediateKeys.getPublic(), rootKeys.getPrivate(), true, root, true);
        signer = issue(signerName, BigInteger.valueOf(3), intermediateName, signerKeys.getPublic(), intermediateKeys.getPrivate(), false, intermediate, true);
        goodCrl = crl(false); revokedCrl = crl(true);
        rootCrl = rootCrl();
        goodOcsp = ocsp(false); revokedOcsp = ocsp(true);
        tsa = issue(new X500Name("CN=CryptoCarver Offline TSA"), BigInteger.valueOf(4), rootName,
                tsaKeys.getPublic(), rootKeys.getPrivate(), false, root, false, true);
        pkcs12 = directory.resolve("signer.p12");
        KeyStore store = KeyStore.getInstance("PKCS12"); store.load(null, PASSWORD);
        store.setKeyEntry("signer", signerKeys.getPrivate(), PASSWORD, new java.security.cert.Certificate[]{signer, intermediate, root});
        try (var output = Files.newOutputStream(pkcs12)) { store.store(output, PASSWORD); }
        goodCrlFile = write("good.crl", goodCrl.getEncoded()); revokedCrlFile = write("revoked.crl", revokedCrl.getEncoded());
        rootCrlFile = write("root.crl", rootCrl.getEncoded());
        goodOcspFile = write("good.ocsp", goodOcsp); revokedOcspFile = write("revoked.ocsp", revokedOcsp);
    }

    private Path write(String name, byte[] bytes) throws Exception { Path p = directory.resolve(name); Files.write(p, bytes); return p; }
    void assertNoRevocationDownloads() {
        if (revocationDownloadAttempts.get() != 0) throw new AssertionError(
                "DSS attempted " + revocationDownloadAttempts.get() + " revocation HTTP request(s); tests must stay offline");
    }
    DSSDocument crlDocument(boolean revoked) throws Exception { return new InMemoryDocument((revoked ? revokedCrl : goodCrl).getEncoded(), "fixture.crl"); }

    private X509Certificate issue(X500Name subject, BigInteger serial, X500Name issuer, PublicKey publicKey,
                                  PrivateKey issuerKey, boolean ca, X509Certificate issuerCert, boolean endpoints) throws Exception {
        return issue(subject, serial, issuer, publicKey, issuerKey, ca, issuerCert, endpoints, false);
    }
    private X509Certificate issue(X500Name subject, BigInteger serial, X500Name issuer, PublicKey publicKey,
                                  PrivateKey issuerKey, boolean ca, X509Certificate issuerCert, boolean endpoints, boolean timestamp) throws Exception {
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE, NOT_AFTER, subject, publicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(ca ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature));
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(publicKey));
        if (issuerCert != null) builder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(issuerCert));
        if (endpoints) {
            builder.addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[]{
                    new DistributionPoint(new DistributionPointName(new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, revocationUrl + "/crl"))), null, null)}));
            builder.addExtension(Extension.authorityInfoAccess, false, new AuthorityInformationAccess(
                    new AccessDescription(AccessDescription.id_ad_ocsp, new GeneralName(GeneralName.uniformResourceIdentifier, revocationUrl + "/ocsp"))));
        }
        if (timestamp) builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer(issuerKey)));
    }
    private ContentSigner signer(PrivateKey key) throws Exception { return new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(key); }
    private X509CRL crl(boolean revoke) throws Exception {
        X509v2CRLBuilder builder = new X509v2CRLBuilder(new X500Name(intermediate.getSubjectX500Principal().getName()), NOT_BEFORE);
        builder.setNextUpdate(NOT_AFTER);
        builder.addExtension(Extension.authorityKeyIdentifier, false, new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(intermediate));
        if (revoke) builder.addCRLEntry(signer.getSerialNumber(), new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime(), CRLReason.privilegeWithdrawn);
        return new JcaX509CRLConverter().setProvider("BC").getCRL(builder.build(signer(intermediateKeys.getPrivate())));
    }
    private X509CRL rootCrl() throws Exception {
        X509v2CRLBuilder builder = new X509v2CRLBuilder(new X500Name(root.getSubjectX500Principal().getName()), NOT_BEFORE);
        builder.setNextUpdate(NOT_AFTER);
        builder.addExtension(Extension.authorityKeyIdentifier, false, new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(root));
        return new JcaX509CRLConverter().setProvider("BC").getCRL(builder.build(signer(rootKeys.getPrivate())));
    }
    private byte[] ocsp(boolean revoke) throws Exception {
        DigestCalculator digest = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build().get(CertificateID.HASH_SHA1);
        CertificateID id = new CertificateID(digest, new JcaX509CertificateHolder(intermediate), signer.getSerialNumber());
        BasicOCSPRespBuilder builder = new JcaBasicOCSPRespBuilder(intermediateKeys.getPublic(), digest);
        CertificateStatus status = revoke ? new RevokedStatus(new Date(System.currentTimeMillis() - 60_000L), CRLReason.privilegeWithdrawn) : CertificateStatus.GOOD;
        builder.addResponse(id, status, NOT_BEFORE, NOT_AFTER, null);
        BasicOCSPResp basic = builder.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(intermediateKeys.getPrivate()),
                new X509CertificateHolder[]{new JcaX509CertificateHolder(intermediate)}, new Date());
        return new OCSPRespBuilder().build(OCSPResp.SUCCESSFUL, basic).getEncoded();
    }
    private TimeStampTokenGenerator timestampGenerator() throws Exception {
        X509CertificateHolder tsaHolder = new JcaX509CertificateHolder(tsa);
        return LocalTimestampAuthority.tokenGenerator(tsaKeys, tsaHolder,
                List.of(tsaHolder, new JcaX509CertificateHolder(root)));
    }
    @Override public void close() { tsaServer.stop(0); Arrays.fill(PASSWORD, '\0'); }
}
