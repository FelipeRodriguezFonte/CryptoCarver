package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.TsaDiagnostics;
import com.cryptocarver.crypto.XMLSignatureOperations;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.scene.control.TextInputControl;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.SignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.bouncycastle.util.CollectionStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Remaining phase-zero privacy audit using local XML and RFC 3161 fixtures only. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class XMLSignatureExtendedPrivacyAuditUITest extends EmvExtractionCharacterizationSupport {
    @Test
    void verificationExportsInspectorsTruststoresAndLocalTimestampTokenAvoidCredentials() throws Exception {
        String keyPassword = "invented-audit-keystore-" + UUID.randomUUID();
        String trustPassword = "invented-audit-trust-" + UUID.randomUUID();
        String authPassword = "invented-audit-basic-" + UUID.randomUUID();
        KeyPair signer = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var signingCertificate = CertificateGenerator.generateSelfSignedCertificate(signer,
                config("Invented Audit Signer"));
        PathBundle bundle = createStoresAndXml(signer, signingCertificate, keyPassword, trustPassword);
        String signed = XMLSignatureOperations.signXAdES(Files.readString(bundle.xml()),
                bundle.signingStore().toString(), keyPassword);
        XMLSignatureOperations.VerificationResult verification = XMLSignatureOperations.verifyXAdES(
                signed, bundle.trustStore().toString(), trustPassword);
        String structural = XMLSignatureOperations.inspectSignedXml(signed);
        String[] exports = {verification.xmlSimpleReport(), verification.xmlDetailedReport(), verification.xmlEtsiReport()};
        for (String report : exports) {
            assertNotNull(report);
            assertFalse(report.contains(keyPassword));
            assertFalse(report.contains(trustPassword));
            assertFalse(report.contains(bundle.trustStore().toString()));
        }
        for (String report : exports) {
            Path export = tempDir.resolve("audit-export-" + UUID.randomUUID() + ".xml");
            Files.writeString(export, report);
            String roundTrip = Files.readString(export);
            assertFalse(roundTrip.contains(keyPassword));
            assertFalse(roundTrip.contains(trustPassword));
            assertFalse(roundTrip.contains(bundle.trustStore().toString()));
        }

        byte[] source = Files.readAllBytes(bundle.xml());
        TokenFixture tokenFixture = localTimestampToken(source);
        byte[] token = tokenFixture.token();
        Path tokenFile = tempDir.resolve("invented-local.tsr");
        Files.write(tokenFile, token);
        KeyStore tsaTrust = KeyStore.getInstance("PKCS12");
        tsaTrust.load(null, trustPassword.toCharArray());
        var tsaCertificate = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(tokenFixture.certificate());
        tsaTrust.setCertificateEntry("invented-tsa", tsaCertificate);
        Path tsaTrustFile = tempDir.resolve("invented-tsa-trust.p12");
        try (var out = Files.newOutputStream(tsaTrustFile)) { tsaTrust.store(out, trustPassword.toCharArray()); }

        List<String> secrets = List.of(keyPassword, trustPassword, authPassword,
                java.util.Base64.getEncoder().encodeToString(signer.getPrivate().getEncoded()),
                java.util.HexFormat.of().formatHex(signer.getPrivate().getEncoded()));
        onFx(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
            shell.navigateTo("Sign XML (XAdES)");
            var controller = (XMLSignatureController) get(shell, "xmlSecurityContainerController");
            var reporter = new AuditReporter();
            controller.initModule(reporter);

            put(controller, "xmlInspectInputArea", signed);
            controller.handleInspectSignedXML();
            assertTrue(((TextInputControl) get(controller, "xmlInspectReportArea")).getText().contains("XMLDSig signatures: 1"));

            put(controller, "xmlTimestampFileField", bundle.xml().toString());
            put(controller, "xmlTimestampTokenField", tokenFile.toString());
            put(controller, "xmlTimestampTrustStoreField", tsaTrustFile.toString());
            put(controller, "xmlTimestampTrustStorePasswordField", trustPassword);
            controller.handleInspectTimestampToken();
            String inspectedToken = ((TextInputControl) get(controller, "xmlTimestampReportArea")).getText();
            assertTrue(inspectedToken.contains("Embedded Certificate Chain"));
            controller.handleValidateTimestampToken();
            String validation = ((TextInputControl) get(controller, "xmlTimestampReportArea")).getText();
            assertTrue(validation.contains("Trust Chain: SUCCESS"));
            assertFalse(validation.contains(trustPassword));
            assertFalse(validation.contains(tsaTrustFile.toString()));

            OperationResult published = OperationResult.forOperation("XAdES Verify")
                    .input(signed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(verification.summary().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(List.of(OperationDetail.publicDetail("Trust Policy", "Truststore configured")))
                    .status("XML verification: valid").build();
            reporter.publish(published);
            reporter.updateStatus("XML verification: valid");
            var violations = new java.util.ArrayList<String>();
            inspectSurfaces(SecretVisibilityProfile.REDACTED, "XML verify/inspect/token audit", secrets,
                    shell, root, new java.util.ArrayList<>(), violations);
            assertTrue(violations.isEmpty(), String.join("\n", violations));
            assertFalse(structural.contains(keyPassword));
            assertFalse(structural.contains(trustPassword));
            assertFalse(verification.summary().contains(bundle.trustStore().toString()));
            for (String secret : secrets) {
                assertFalse(inspectedToken.contains(secret));
                assertFalse(validation.contains(secret));
            }
        });
    }

    private TokenFixture localTimestampToken(byte[] data) throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyPair tsaKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        X509CertificateHolder tsa = buildTsaCertificate(tsaKey);
        byte[] imprint = MessageDigest.getInstance("SHA-256").digest(data);
        TimeStampRequestGenerator requests = new TimeStampRequestGenerator();
        requests.setCertReq(true);
        TimeStampRequest request = requests.generate(NISTObjectIdentifiers.id_sha256, imprint);
        DigestCalculatorProvider calculators = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        var contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(tsaKey.getPrivate());
        var signerInfo = new JcaSignerInfoGeneratorBuilder(calculators).build(contentSigner, tsa);
        var digestCalculator = calculators.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
        var generator = new TimeStampTokenGenerator(signerInfo, digestCalculator, new ASN1ObjectIdentifier("1.2.3.4.81"));
        generator.addCertificates(new CollectionStore<>(List.of(tsa)));
        var response = new TimeStampResponseGenerator(generator, org.bouncycastle.tsp.TSPAlgorithms.ALLOWED)
                .generate(request, BigInteger.valueOf(81), new Date());
        return new TokenFixture(response.getEncoded(), tsa);
    }

    private X509CertificateHolder buildTsaCertificate(KeyPair pair) throws Exception {
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=Invented Local TSA");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(81),
                new Date(System.currentTimeMillis() - 60_000), new Date(System.currentTimeMillis() + 3_600_000),
                name, pair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(pair.getPublic()));
        return builder.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.getPrivate()));
    }

    private PathBundle createStoresAndXml(KeyPair key, java.security.cert.X509Certificate cert,
                                          String keyPassword, String trustPassword) throws Exception {
        Path keyStore = tempDir.resolve("invented-signing-audit.p12");
        KeyStore signing = KeyStore.getInstance("PKCS12"); signing.load(null, keyPassword.toCharArray());
        signing.setKeyEntry("signer", key.getPrivate(), keyPassword.toCharArray(), new java.security.cert.Certificate[]{cert});
        try (var out = Files.newOutputStream(keyStore)) { signing.store(out, keyPassword.toCharArray()); }
        Path trustStore = tempDir.resolve("invented-trust-audit.p12");
        KeyStore trust = KeyStore.getInstance("PKCS12"); trust.load(null, trustPassword.toCharArray());
        trust.setCertificateEntry("invented-root", cert);
        try (var out = Files.newOutputStream(trustStore)) { trust.store(out, trustPassword.toCharArray()); }
        Path xml = tempDir.resolve("audit.xml"); Files.writeString(xml, "<invoice><amount>81</amount></invoice>");
        return new PathBundle(keyStore, trustStore, xml);
    }

    private static CertificateGenerator.CertificateConfig config(String cn) {
        var result = new CertificateGenerator.CertificateConfig(); result.commonName = cn; return result;
    }

    private static void put(XMLSignatureController controller, String field, String value) throws Exception {
        ((TextInputControl) get(controller, field)).setText(value);
    }

    private record PathBundle(java.nio.file.Path signingStore, java.nio.file.Path trustStore, java.nio.file.Path xml) { }
    private record TokenFixture(byte[] token, X509CertificateHolder certificate) { }

    private final class AuditReporter implements StatusReporter {
        @Override public void updateStatus(String message) { shell.updateStatus(message); }
        @Override public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
            shell.updateInspector(operation, input, output, details);
        }
        @Override public void showError(String title, String message) { throw new AssertionError(title + ": " + message); }
        @Override public void showError(UserFacingError error) { throw new AssertionError(error.title() + ": " + error.detail()); }
        @Override public void showInfo(String title, String message) { }
        @Override public void publish(OperationResult result) { shell.publish(result); }
    }
}
