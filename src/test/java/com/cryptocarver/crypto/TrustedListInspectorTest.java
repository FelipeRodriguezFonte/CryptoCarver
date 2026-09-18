package com.cryptocarver.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fixtures are real TS 119 612 XML, signed with a real enveloped XMLDSig,
 * so what is asserted is our reading of the encoding rather than of a mock.
 */
class TrustedListInspectorTest {

    private static final String TSL_NS = "http://uri.etsi.org/02231/v2#";
    private static final String ECC_NS = "http://uri.etsi.org/TrstSvc/SvcInfoExt/eSigDir-1999-93-EC-TrustedList/#";
    private static final String GRANTED = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted";
    private static final String WITHDRAWN = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn";
    private static final String QC_FOR_ESIG = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCForESig";
    private static final String QC_WITH_QSCD = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCWithQSCD";
    private static final String CA_QC = "http://uri.etsi.org/TrstSvc/Svctype/CA/QC";

    @BeforeAll
    static void installBouncyCastleProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ------------------------------------------------------------------ parse

    @Test
    void readsSchemeInformationAndServices() throws Exception {
        X509Certificate serviceCertificate = certificate("CN=CA de Pruebas,C=ES");
        byte[] xml = trustedList(serviceCertificate, GRANTED, List.of(QC_FOR_ESIG, QC_WITH_QSCD));

        TrustedListInspector.TrustedList list = TrustedListInspector.parse(xml);
        assertEquals("ES", list.scheme().territory());
        assertEquals("Laboratorio de Pruebas", list.scheme().operatorName());
        assertEquals(BigInteger.valueOf(42), list.scheme().sequenceNumber());
        assertFalse(list.isListOfTrustedLists());

        assertEquals(1, list.services().size());
        TrustedListInspector.Service service = list.services().get(0);
        assertEquals("TSP de Pruebas", service.providerName());
        assertEquals("Servicio de certificados cualificados", service.serviceName());
        assertEquals(CA_QC, service.typeIdentifier());
        assertTrue(service.isGranted());
        assertFalse(service.isWithdrawn());
        assertEquals("granted", service.statusLabel());
        assertEquals(1, service.digitalIdentities().size());
    }

    /** The qualifiers are where "qualified for what?" is actually answered, and
     *  they live in an extension rather than in the service itself. */
    @Test
    void readsTheQualifiersFromTheServiceExtension() throws Exception {
        byte[] xml = trustedList(certificate("CN=CA,C=ES"), GRANTED, List.of(QC_FOR_ESIG, QC_WITH_QSCD));
        List<String> qualifiers = TrustedListInspector.parse(xml).services().get(0).qualifiers();

        assertEquals(2, qualifiers.size());
        assertTrue(qualifiers.contains(QC_FOR_ESIG));
        assertTrue(qualifiers.contains(QC_WITH_QSCD));

        // And the report renders them by the name DSS knows them by, not as URIs.
        String report = TrustedListInspector.describe(xml, java.util.Locale.ENGLISH);
        assertTrue(report.contains("QC_FOR_ESIG"), report);
        assertTrue(report.contains("QC_WITH_QSCD"), report);
    }

    @Test
    void withdrawnIsDistinguishedFromGranted() throws Exception {
        byte[] xml = trustedList(certificate("CN=CA,C=ES"), WITHDRAWN, List.of());
        TrustedListInspector.Service service = TrustedListInspector.parse(xml).services().get(0);

        assertFalse(service.isGranted());
        assertTrue(service.isWithdrawn());
        assertNotNull(service.statusStartingTime(),
                "A status without a starting time cannot be applied to a signature made in the past");
    }

    @Test
    void recognisesAListOfTrustedLists() throws Exception {
        byte[] xml = listOfTrustedLists();
        TrustedListInspector.TrustedList list = TrustedListInspector.parse(xml);

        assertTrue(list.isListOfTrustedLists());
        assertEquals(2, list.scheme().pointersToOtherLists().size());
        assertTrue(TrustedListInspector.describe(xml, java.util.Locale.forLanguageTag("es"))
                .contains("lista de listas"));
    }

    // -------------------------------------------------------------- signature

    @Test
    void verifiesTheListSignatureAndSaysWhatThatDoesNotProve() throws Exception {
        KeyPair signer = p256();
        X509Certificate signerCertificate = certificate(signer, "CN=Operador del esquema,C=ES");
        byte[] xml = sign(trustedList(certificate("CN=CA,C=ES"), GRANTED, List.of()),
                signer, signerCertificate);

        TrustedListInspector.SignatureResult result = TrustedListInspector.verifySignature(xml);
        assertTrue(result.signatureValid());
        assertEquals(signerCertificate, result.signingCertificate());
        assertTrue(result.trustNote().contains("List of Trusted Lists"),
                "A valid signature must not be presented as trust: " + result.trustNote());
    }

    @Test
    void refusesAListWhoseBytesChangedAfterSigning() throws Exception {
        KeyPair signer = p256();
        byte[] xml = sign(trustedList(certificate("CN=CA,C=ES"), GRANTED, List.of()),
                signer, certificate(signer, "CN=Operador,C=ES"));

        String tampered = new String(xml, StandardCharsets.UTF_8)
                .replace("Servicio de certificados cualificados", "Servicio manipulado          ");
        TrustedListInspector.SignatureResult result =
                TrustedListInspector.verifySignature(tampered.getBytes(StandardCharsets.UTF_8));

        assertFalse(result.signatureValid());
        assertTrue(result.trustNote().contains("not the ones that were signed"), result.trustNote());
    }

    @Test
    void saysSoWhenThereIsNoSignatureAtAll() throws Exception {
        byte[] xml = trustedList(certificate("CN=CA,C=ES"), GRANTED, List.of());
        TrustedListInspector.SignatureResult result = TrustedListInspector.verifySignature(xml);

        assertFalse(result.signatureValid());
        assertNull(result.signingCertificate());
        assertTrue(result.trustNote().contains("no XML signature"), result.trustNote());
    }

    // ----------------------------------------------------------------- lookup

    @Test
    void findsACertificateListedAsAServiceIdentity() throws Exception {
        X509Certificate listed = certificate("CN=CA de Pruebas,C=ES");
        TrustedListInspector.TrustedList list = TrustedListInspector.parse(
                trustedList(listed, GRANTED, List.of(QC_FOR_ESIG)));

        List<TrustedListInspector.Match> matches =
                TrustedListInspector.findCertificate(list, listed);
        assertEquals(1, matches.size());
        assertEquals("exact certificate", matches.get(0).matchedBy());
        assertTrue(matches.get(0).service().isGranted());
    }

    @Test
    void doesNotMatchAStrangerToTheList() throws Exception {
        TrustedListInspector.TrustedList list = TrustedListInspector.parse(
                trustedList(certificate("CN=CA de Pruebas,C=ES"), GRANTED, List.of()));

        assertTrue(TrustedListInspector.findCertificate(list, certificate("CN=Otra CA,C=PT")).isEmpty());
    }

    /** A subject name is not an identity: two unrelated issuers can use the same
     *  distinguished name, so matching never falls back to it. */
    @Test
    void doesNotMatchOnSubjectNameAlone() throws Exception {
        String sameName = "CN=CA de Pruebas,C=ES";
        TrustedListInspector.TrustedList list = TrustedListInspector.parse(
                trustedList(certificate(sameName), GRANTED, List.of()));

        assertTrue(TrustedListInspector.findCertificate(list, certificate(sameName)).isEmpty(),
                "A different key under the same name must not match");
    }

    // ------------------------------------------------------- fixture building

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(String subject) throws Exception {
        return certificate(p256(), subject);
    }

    private static X509Certificate certificate(KeyPair pair, String subject) throws Exception {
        X500Name name = new X500Name(subject);
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                name, pair.getPublic());
        builder.addExtension(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(pair.getPublic()));
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA")
                        .setProvider("BC").build(pair.getPrivate())));
    }

    private static byte[] trustedList(X509Certificate serviceCertificate,
                                      String status,
                                      List<String> qualifiers) throws Exception {
        StringBuilder qualifiersXml = new StringBuilder();
        if (!qualifiers.isEmpty()) {
            qualifiersXml.append("<ServiceInformationExtensions>")
                    .append("<Extension Critical=\"true\">")
                    .append("<Qualifications xmlns=\"").append(ECC_NS).append("\">")
                    .append("<QualificationElement><Qualifiers>");
            for (String qualifier : qualifiers) {
                qualifiersXml.append("<Qualifier uri=\"").append(qualifier).append("\"/>");
            }
            qualifiersXml.append("</Qualifiers>")
                    .append("<CriteriaList assert=\"all\"/>")
                    .append("</QualificationElement></Qualifications>")
                    .append("</Extension></ServiceInformationExtensions>");
        }

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TrustServiceStatusList xmlns="%s" Id="TSL-lab" TSLTag="http://uri.etsi.org/19612/TSLTag">
                  <SchemeInformation>
                    <TSLVersionIdentifier>5</TSLVersionIdentifier>
                    <TSLSequenceNumber>42</TSLSequenceNumber>
                    <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</TSLType>
                    <SchemeOperatorName><Name xml:lang="en">Laboratorio de Pruebas</Name></SchemeOperatorName>
                    <SchemeName><Name xml:lang="en">Lista de pruebas</Name></SchemeName>
                    <StatusDeterminationApproach>http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate</StatusDeterminationApproach>
                    <SchemeTerritory>ES</SchemeTerritory>
                    <ListIssueDateTime>2026-09-01T00:00:00Z</ListIssueDateTime>
                    <NextUpdate><dateTime>2027-03-01T00:00:00Z</dateTime></NextUpdate>
                  </SchemeInformation>
                  <TrustServiceProviderList>
                    <TrustServiceProvider>
                      <TSPInformation>
                        <TSPName><Name xml:lang="en">TSP de Pruebas</Name></TSPName>
                      </TSPInformation>
                      <TSPServices>
                        <TSPService>
                          <ServiceInformation>
                            <ServiceTypeIdentifier>%s</ServiceTypeIdentifier>
                            <ServiceName><Name xml:lang="en">Servicio de certificados cualificados</Name></ServiceName>
                            <ServiceDigitalIdentity>
                              <DigitalId><X509Certificate>%s</X509Certificate></DigitalId>
                            </ServiceDigitalIdentity>
                            <ServiceStatus>%s</ServiceStatus>
                            <StatusStartingTime>2024-05-20T00:00:00Z</StatusStartingTime>
                            %s
                          </ServiceInformation>
                        </TSPService>
                      </TSPServices>
                    </TrustServiceProvider>
                  </TrustServiceProviderList>
                </TrustServiceStatusList>
                """.formatted(TSL_NS, CA_QC,
                Base64.getEncoder().encodeToString(serviceCertificate.getEncoded()),
                status, qualifiersXml);
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] listOfTrustedLists() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TrustServiceStatusList xmlns="%s" Id="LOTL-lab" TSLTag="http://uri.etsi.org/19612/TSLTag">
                  <SchemeInformation>
                    <TSLSequenceNumber>7</TSLSequenceNumber>
                    <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists</TSLType>
                    <SchemeOperatorName><Name xml:lang="en">Comision de Pruebas</Name></SchemeOperatorName>
                    <SchemeTerritory>EU</SchemeTerritory>
                    <ListIssueDateTime>2026-09-01T00:00:00Z</ListIssueDateTime>
                    <PointersToOtherTSL>
                      <OtherTSLPointer><TSLLocation>https://tl.lab.invalid/ES.xml</TSLLocation></OtherTSLPointer>
                      <OtherTSLPointer><TSLLocation>https://tl.lab.invalid/PT.xml</TSLLocation></OtherTSLPointer>
                    </PointersToOtherTSL>
                  </SchemeInformation>
                </TrustServiceStatusList>
                """.formatted(TSL_NS).getBytes(StandardCharsets.UTF_8);
    }

    /** An enveloped XMLDSig over the whole document, which is how a Trusted List
     *  is signed. */
    private static byte[] sign(byte[] xml, KeyPair signer, X509Certificate certificate) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

        XMLSignatureFactory signatures = XMLSignatureFactory.getInstance("DOM");
        Reference reference = signatures.newReference("", signatures.newDigestMethod(DigestMethod.SHA256, null),
                Collections.singletonList(signatures.newTransform(
                        Transform.ENVELOPED, (TransformParameterSpec) null)),
                null, null);
        SignedInfo signedInfo = signatures.newSignedInfo(
                signatures.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
                        (C14NMethodParameterSpec) null),
                signatures.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256", null),
                Collections.singletonList(reference));

        KeyInfoFactory keyInfoFactory = signatures.getKeyInfoFactory();
        X509Data x509Data = keyInfoFactory.newX509Data(Collections.singletonList(certificate));
        KeyInfo keyInfo = keyInfoFactory.newKeyInfo(Collections.singletonList(x509Data));

        DOMSignContext context = new DOMSignContext(signer.getPrivate(), document.getDocumentElement());
        signatures.newXMLSignature(signedInfo, keyInfo).sign(context);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toByteArray();
    }
}
