package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.TrustedListInspector;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletTrustValidationStatusUITest extends WalletExtractionCharacterizationSupport {
    @Test void unsignedListReportsInvalidAndPreservesReport() throws Exception { verify(false); }
    @Test void signedListKeepsVerifiedStatusAndPreservesReport() throws Exception { verify(true); }

    private void verify(boolean signed) throws Exception {
        byte[] xml = "<TrustServiceStatusList xmlns=\"http://uri.etsi.org/02231/v2#\"/>".getBytes(StandardCharsets.UTF_8);
        if (signed) xml = sign(xml);
        var result = TrustedListInspector.verifySignature(xml);
        assertEquals(signed, result.signatureValid());
        String expectedReport = "signature: " + (signed ? "valid" : "INVALID") + "\n"
                + (result.signingCertificate() == null ? "" : "signed by: " + result.signingCertificate().getSubjectX500Principal() + "\n")
                + "\n" + result.trustNote() + "\n";
        String input = new String(xml, StandardCharsets.UTF_8);
        withWallet(() -> {
            shell.navigateTo("Trusted List"); replaceReporter();
            put("trustedListXmlArea", input);
            for (var language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                I18nService.getInstance().setPreference(language);
                invoke("handleTrustedListVerify");
                assertNull(recorder.error); assertNotNull(recorder.result);
                assertEquals(expectedReport, text("trustedListOutputArea"));
                assertEquals(expectedReport, new String(recorder.result.getOutput(), StandardCharsets.UTF_8));
                String expected = language == LanguagePreference.EN
                        ? (signed ? "Verified" : "Signature invalid")
                        : (signed ? "Verificado" : "Firma no válida");
                assertEquals(expected, recorder.status);
            }
        });
    }

    private static byte[] sign(byte[] xml) throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig(); config.commonName = "Invented TSL signer 82";
        var certificate = CertificateGenerator.generateSelfSignedCertificate(keys, config);
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        var signatures = XMLSignatureFactory.getInstance("DOM");
        var reference = signatures.newReference("", signatures.newDigestMethod(DigestMethod.SHA256, null),
                List.of(signatures.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);
        var info = signatures.newSignedInfo(signatures.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
                (C14NMethodParameterSpec) null), signatures.newSignatureMethod(SignatureMethod.RSA_SHA256, null), List.of(reference));
        var keyInfoFactory = signatures.getKeyInfoFactory();
        var keyInfo = keyInfoFactory.newKeyInfo(List.of(keyInfoFactory.newX509Data(List.of(certificate))));
        signatures.newXMLSignature(info, keyInfo).sign(new DOMSignContext(keys.getPrivate(), document.getDocumentElement()));
        var output = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(output));
        return output.toByteArray();
    }
}
