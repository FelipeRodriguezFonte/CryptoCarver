package com.cryptocarver.ui;

import javafx.scene.control.TextInputControl;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Local tokens only; inherited lifecycle restores settings, shelf and history. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class XMLSignatureValidationStatusUITest extends WalletExtractionCharacterizationSupport {
    @Test void malformedTokenReportsInvalidWithoutChangingReport() throws Exception { validate(false, false); }
    @Test void mismatchedImprintReportsInvalidWithoutChangingReport() throws Exception { validate(true, true); }
    @Test void validTokenKeepsSuccessfulStatus() throws Exception { validate(true, false); }

    private void validate(boolean validEncoding, boolean mismatch) throws Exception {
        byte[] data = "invented timestamp data 82".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] token = validEncoding ? localTimestampToken(data) : new byte[]{1, 2, 3};
        byte[] selected = mismatch ? new byte[]{8, 2} : data;
        Path tokenFile = tempDir.resolve("local.tsr"); Files.write(tokenFile, token);
        Path dataFile = tempDir.resolve("data.bin"); Files.write(dataFile, selected);
        String expectedReport = com.cryptocarver.crypto.TsaDiagnostics.validateToken(token, selected, null, "");
        boolean invalid = !validEncoding || mismatch;
        assertEquals(invalid, expectedReport.contains(": FAILED"));
        onFx(() -> {
            shell.navigateTo("Sign XML (XAdES)");
            var controller = (XMLSignatureController) get(shell, "xmlSecurityContainerController");
            var reporter = new RecordingReporter(shell); controller.initModule(reporter);
            ((TextInputControl) get(controller, "xmlTimestampTokenField")).setText(tokenFile.toString());
            ((TextInputControl) get(controller, "xmlTimestampFileField")).setText(dataFile.toString());
            ((TextInputControl) get(controller, "xmlTimestampTrustStoreField")).clear();
            for (var language : List.of(com.cryptocarver.model.LanguagePreference.EN, com.cryptocarver.model.LanguagePreference.ES)) {
                com.cryptocarver.service.I18nService.getInstance().setPreference(language);
                controller.handleValidateTimestampToken();
                assertEquals(expectedReport, ((TextInputControl) get(controller, "xmlTimestampReportArea")).getText());
                String expected = language == com.cryptocarver.model.LanguagePreference.EN
                        ? (invalid ? "Timestamp token invalid." : "Timestamp token validated.")
                        : (invalid ? "Token de marca de tiempo no válido." : "Token de marca de tiempo validado.");
                assertEquals(expected, reporter.status);
                assertNull(reporter.error);
            }
        });
    }

    private byte[] localTimestampToken(byte[] data) throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyPair tsaKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=Invented Local TSA");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(81),
                new Date(System.currentTimeMillis() - 60_000), new Date(System.currentTimeMillis() + 3_600_000),
                name, tsaKey.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(tsaKey.getPublic()));
        X509CertificateHolder tsa = builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(tsaKey.getPrivate()));
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
        return new TimeStampResponseGenerator(generator, org.bouncycastle.tsp.TSPAlgorithms.ALLOWED)
                .generate(request, BigInteger.valueOf(81), new Date()).getEncoded();
    }

}
