package com.cryptocarver.crypto;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.PolicyQualifierInfo;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The certificates here are built with the extensions the specifications call
 * for, so the assertions are about our reading of real encodings rather than
 * about a mock.
 *
 * <p>OIDs come from the specifications themselves: TS 119 412-6 V1.2.1 Annex A
 * for the {@code 0.4.0.194126.1} arc, TS 119 411-8 V1.1.1 clause 5.3 for the
 * {@code 0.4.0.194118.1} arc, and DSS's own enumerations for EN 319 412-5.</p>
 */
class EidasCertificateInspectorTest {

    private static final String QC_COMPLIANCE = "0.4.0.1862.1.1";
    private static final String QC_TYPE = "0.4.0.1862.1.6";
    private static final String QCT_ESEAL = "0.4.0.1862.1.6.2";
    private static final String QCT_WEB = "0.4.0.1862.1.6.3";

    @BeforeAll
    static void installBouncyCastleProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ------------------------------------------------------- TS 119 412-6

    /**
     * A PID provider certificate is identified by a QcType nobody's released DSS
     * knows yet — the specification is dated April 2026. This asserts both that
     * we recognise it and that DSS carries the unknown OID through rather than
     * dropping it, which is the assumption the implementation rests on.
     */
    @Test
    void recognisesAPidProviderCertificate() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .qcStatements(qcCompliance(), qcTypes(EidasCertificateInspector.QCT_PID))
                .keyUsage(KeyUsage.nonRepudiation)
                .subjectKeyIdentifier(true));

        EidasCertificateInspector.Report report = EidasCertificateInspector.inspect(certificate);
        assertTrue(report.profiles().contains(EidasCertificateInspector.Profile.PID_PROVIDER));
        assertTrue(report.qcTypeOids().contains(EidasCertificateInspector.QCT_PID),
                "DSS must carry the unrecognised QcType OID through: " + report.qcTypeOids());
    }

    @Test
    void recognisesAWalletProviderCertificate() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .qcStatements(qcCompliance(), qcTypes(EidasCertificateInspector.QCT_WALLET))
                .keyUsage(KeyUsage.nonRepudiation)
                .subjectKeyIdentifier(true));

        assertTrue(EidasCertificateInspector.inspect(certificate).profiles()
                .contains(EidasCertificateInspector.Profile.WALLET_PROVIDER));
    }

    /** PID-4.4.1-01, PID-4.4.2-01 and PID-4.4.3-01: key usage and subject key
     *  identifier are required, and AIA is required unless self-signed. The
     *  certificates here are self-signed, so AIA is not demanded. */
    @Test
    void reportsTheProviderProfileExtensionsThatAreMissing() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .qcStatements(qcCompliance(), qcTypes(EidasCertificateInspector.QCT_PID)));

        List<String> requirements = requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR");
        assertTrue(requirements.contains("PID-4.4.1-01"), requirements.toString());
        assertTrue(requirements.contains("PID-4.4.2-01"), requirements.toString());
        assertFalse(requirements.contains("PID-4.4.3-01"),
                "A self-signed certificate is exempt from Authority Information Access");
    }

    /** PSB-8.3: the QcPSB statement of Annex A, decoded field by field. DSS files
     *  it under "other OIDs" and discards the value, so this is our own parsing. */
    @Test
    void decodesTheQcPsbStatement() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .qcStatements(qcCompliance(), qcPsb("EU", "urn:es:aeat:padron",
                        "Reglamento (UE) 2024/1183"))
                .keyUsage(KeyUsage.nonRepudiation)
                .subjectKeyIdentifier(true));

        EidasCertificateInspector.Report report = EidasCertificateInspector.inspect(certificate);
        assertTrue(report.profiles().contains(EidasCertificateInspector.Profile.PSBEAA_PROVIDER));
        assertEquals("EU", report.qcPsb().countryOfLegislation());
        assertEquals("urn:es:aeat:padron", report.qcPsb().authSourceIdentification());
        assertEquals("Reglamento (UE) 2024/1183", report.qcPsb().legislationIdentification());
        assertTrue(requirementsOf(report, "ERROR").isEmpty(),
                "A well-formed QcPSB should raise nothing: " + report.findings());
    }

    /** PSB-8.3-05: an ISO 3166 alpha-2 code, or 'EU' for Union law. "ESP" is
     *  neither, and a country nobody can resolve is not a detail. */
    @Test
    void rejectsAQcPsbCountryThatIsNotAlpha2OrEu() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .qcStatements(qcCompliance(), qcPsb("ESP", "source", "law"))
                .keyUsage(KeyUsage.nonRepudiation)
                .subjectKeyIdentifier(true));

        assertTrue(requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR")
                .contains("PSB-8.3-05"));
    }

    @Test
    void reportsAnEmptyQcPsbFieldByField() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .qcStatements(qcCompliance(), qcPsb("ES", "", ""))
                .keyUsage(KeyUsage.nonRepudiation)
                .subjectKeyIdentifier(true));

        List<String> requirements = requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR");
        assertTrue(requirements.contains("PSB-8.3-04"), requirements.toString());
        assertTrue(requirements.contains("PSB-8.3-02"), requirements.toString());
    }

    // ------------------------------------------------------- TS 119 411-8

    @Test
    void acceptsAWellFormedRelyingPartyAccessCertificate() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .subject("CN=Banco de Pruebas,organizationIdentifier=VATES-B12345678,O=Banco,C=ES")
                .policy(EidasCertificateInspector.POLICY_QCP_L_EUDIWRP, "https://tsp.lab.invalid/cps")
                .subjectAlternativeUri("https://banco.lab.invalid/soporte")
                .qcStatements(qcCompliance(), qcTypes(QCT_ESEAL))
                .keyUsage(KeyUsage.nonRepudiation)
                .subjectKeyIdentifier(true));

        EidasCertificateInspector.Report report = EidasCertificateInspector.inspect(certificate);
        assertTrue(report.profiles().contains(
                EidasCertificateInspector.Profile.WALLET_RELYING_PARTY_ACCESS));
        assertTrue(requirementsOf(report, "ERROR").isEmpty(), report.findings().toString());
    }

    /** GEN-6.6.1-05: for the legal-person policies the organizationIdentifier is
     *  what ties the certificate to the entry in the relying party register. */
    @Test
    void demandsAnOrganizationIdentifierForLegalPersonPolicies() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .subject("CN=Banco de Pruebas,O=Banco,C=ES")
                .policy(EidasCertificateInspector.POLICY_NCP_L_EUDIWRP, "https://tsp.lab.invalid/cps")
                .subjectAlternativeUri("https://banco.lab.invalid/soporte")
                .keyUsage(KeyUsage.digitalSignature)
                .subjectKeyIdentifier(true));

        assertTrue(requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR")
                .contains("GEN-6.6.1-05"));
    }

    /** The same certificate under a natural-person policy is fine without it. */
    @Test
    void doesNotDemandAnOrganizationIdentifierForNaturalPersonPolicies() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .subject("CN=Ana Perez,C=ES")
                .policy(EidasCertificateInspector.POLICY_NCP_N_EUDIWRP, "https://tsp.lab.invalid/cps")
                .subjectAlternativeUri("https://ana.lab.invalid/contacto")
                .keyUsage(KeyUsage.digitalSignature)
                .subjectKeyIdentifier(true));

        assertFalse(requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR")
                .contains("GEN-6.6.1-05"));
    }

    /** GEN-6.6.1-06 and GEN-6.6.1-07. */
    @Test
    void demandsACpsUriAndContactInformation() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .subject("CN=Ana Perez,C=ES")
                .policy(EidasCertificateInspector.POLICY_NCP_N_EUDIWRP, null)
                .keyUsage(KeyUsage.digitalSignature)
                .subjectKeyIdentifier(true));

        List<String> requirements = requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR");
        assertTrue(requirements.contains("GEN-6.6.1-06"), requirements.toString());
        assertTrue(requirements.contains("GEN-6.6.1-07"), requirements.toString());
    }

    /** A telephone number in an otherName satisfies GEN-6.6.1-07 just as a URI does. */
    @Test
    void acceptsATelephoneNumberAsContactInformation() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .subject("CN=Ana Perez,C=ES")
                .policy(EidasCertificateInspector.POLICY_NCP_N_EUDIWRP, "https://tsp.lab.invalid/cps")
                .subjectAlternativeTelephone("+34910000000")
                .keyUsage(KeyUsage.digitalSignature)
                .subjectKeyIdentifier(true));

        assertFalse(requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR")
                .contains("GEN-6.6.1-07"));
    }

    /** TS 119 411-8 says outright that website authentication certificates are
     *  not applicable to wallet relying parties. */
    @Test
    void warnsWhenAnAccessCertificateLooksLikeAWebsiteCertificate() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .subject("CN=Banco,organizationIdentifier=VATES-B12345678,C=ES")
                .policy(EidasCertificateInspector.POLICY_QCP_L_EUDIWRP, "https://tsp.lab.invalid/cps")
                .subjectAlternativeUri("https://banco.lab.invalid")
                .qcStatements(qcCompliance(), qcTypes(QCT_WEB))
                .keyUsage(KeyUsage.digitalSignature)
                .subjectKeyIdentifier(true));

        assertTrue(requirementsOf(EidasCertificateInspector.inspect(certificate), "WARN")
                .contains("GEN-6.6.1-01"));
    }

    /** GEN-6.6.1-02: the QCP-* policies pull in EN 319 411-2, so a qualified
     *  policy with no QcCompliance claims a qualification it does not assert. */
    @Test
    void demandsQcComplianceUnderAQualifiedPolicy() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .subject("CN=Banco,organizationIdentifier=VATES-B12345678,C=ES")
                .policy(EidasCertificateInspector.POLICY_QCP_L_EUDIWRP, "https://tsp.lab.invalid/cps")
                .subjectAlternativeUri("https://banco.lab.invalid/soporte")
                .keyUsage(KeyUsage.digitalSignature)
                .subjectKeyIdentifier(true));

        assertTrue(requirementsOf(EidasCertificateInspector.inspect(certificate), "ERROR")
                .contains("GEN-6.6.1-02"));
    }

    // ------------------------------------------------------------- general

    @Test
    void anOrdinaryCertificateDeclaresNoEidasProfile() throws Exception {
        X509Certificate certificate = certificate(builder -> builder.subject("CN=plain,C=ES"));
        EidasCertificateInspector.Report report = EidasCertificateInspector.inspect(certificate);

        assertFalse(report.declaresAnyEidasProfile());
        assertTrue(report.findings().stream().allMatch(f -> "INFO".equals(f.severity())));
    }

    @Test
    void theReportSaysWhoDecidesQualification() throws Exception {
        X509Certificate certificate = certificate(builder -> builder
                .qcStatements(qcCompliance(), qcTypes(EidasCertificateInspector.QCT_PID))
                .keyUsage(KeyUsage.nonRepudiation)
                .subjectKeyIdentifier(true));

        String english = EidasCertificateInspector.describe(certificate, java.util.Locale.ENGLISH);
        assertTrue(english.contains("trusted list"), english);
        assertTrue(english.contains(EidasCertificateInspector.QCT_PID), english);

        String spanish = EidasCertificateInspector.describe(certificate,
                java.util.Locale.forLanguageTag("es"));
        assertTrue(spanish.contains("lista de confianza"), spanish);
    }

    // ------------------------------------------------ certificate building

    private static List<String> requirementsOf(EidasCertificateInspector.Report report, String severity) {
        List<String> requirements = new ArrayList<>();
        for (EidasCertificateInspector.Finding finding : report.findings()) {
            if (severity.equals(finding.severity()) && finding.requirement() != null) {
                requirements.add(finding.requirement());
            }
        }
        return requirements;
    }

    private static ASN1Encodable qcCompliance() {
        return new QCStatement(new ASN1ObjectIdentifier(QC_COMPLIANCE));
    }

    private static ASN1Encodable qcTypes(String... typeOids) {
        ASN1EncodableVector types = new ASN1EncodableVector();
        for (String oid : typeOids) {
            types.add(new ASN1ObjectIdentifier(oid));
        }
        return new QCStatement(new ASN1ObjectIdentifier(QC_TYPE), new DERSequence(types));
    }

    /** {@code QcPSB ::= SEQUENCE { PrintableString(2), UTF8String, UTF8String }}, TS 119 412-6 Annex A. */
    private static ASN1Encodable qcPsb(String country, String authSource, String legislation) {
        ASN1EncodableVector value = new ASN1EncodableVector();
        value.add(new DERPrintableString(country));
        value.add(new DERUTF8String(authSource));
        value.add(new DERUTF8String(legislation));
        return new QCStatement(new ASN1ObjectIdentifier(EidasCertificateInspector.QCS_PSB),
                new DERSequence(value));
    }

    private static X509Certificate certificate(java.util.function.Consumer<Builder> configure) throws Exception {
        Builder builder = new Builder();
        configure.accept(builder);
        return builder.build();
    }

    /** Builds a self-signed certificate carrying whatever extensions a test needs. */
    private static final class Builder {
        private String subject = "CN=lab,C=ES";
        private final List<ASN1Encodable> qcStatements = new ArrayList<>();
        private String policyOid;
        private String cpsUri;
        private String sanUri;
        private String sanTelephone;
        private Integer keyUsage;
        private boolean subjectKeyIdentifier;

        Builder subject(String value) {
            this.subject = value;
            return this;
        }

        Builder qcStatements(ASN1Encodable... statements) {
            this.qcStatements.addAll(List.of(statements));
            return this;
        }

        Builder policy(String oid, String cps) {
            this.policyOid = oid;
            this.cpsUri = cps;
            return this;
        }

        Builder subjectAlternativeUri(String uri) {
            this.sanUri = uri;
            return this;
        }

        Builder subjectAlternativeTelephone(String number) {
            this.sanTelephone = number;
            return this;
        }

        Builder keyUsage(int usage) {
            this.keyUsage = usage;
            return this;
        }

        Builder subjectKeyIdentifier(boolean present) {
            this.subjectKeyIdentifier = present;
            return this;
        }

        X509Certificate build() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();

            X500Name name = new X500Name(subject);
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder certificate = new JcaX509v3CertificateBuilder(
                    name, BigInteger.valueOf(System.nanoTime()),
                    Date.from(now.minus(1, ChronoUnit.DAYS)),
                    Date.from(now.plus(365, ChronoUnit.DAYS)),
                    name, pair.getPublic());

            if (!qcStatements.isEmpty()) {
                ASN1EncodableVector statements = new ASN1EncodableVector();
                qcStatements.forEach(statements::add);
                certificate.addExtension(Extension.qCStatements, false, new DERSequence(statements));
            }
            if (policyOid != null) {
                PolicyInformation information = cpsUri == null
                        ? new PolicyInformation(new ASN1ObjectIdentifier(policyOid))
                        // The single-argument constructor is the id-qt-cps one.
                        : new PolicyInformation(new ASN1ObjectIdentifier(policyOid),
                                new DERSequence(new PolicyQualifierInfo(cpsUri)));
                certificate.addExtension(Extension.certificatePolicies, false,
                        new DERSequence(information));
            }
            List<GeneralName> names = new ArrayList<>();
            if (sanUri != null) {
                names.add(new GeneralName(GeneralName.uniformResourceIdentifier, sanUri));
            }
            if (sanTelephone != null) {
                ASN1EncodableVector otherName = new ASN1EncodableVector();
                otherName.add(new ASN1ObjectIdentifier("2.5.4.20"));
                otherName.add(new org.bouncycastle.asn1.DERTaggedObject(true, 0,
                        new DERUTF8String(sanTelephone)));
                names.add(new GeneralName(GeneralName.otherName, new DERSequence(otherName)));
            }
            if (!names.isEmpty()) {
                certificate.addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(names.toArray(new GeneralName[0])));
            }
            if (keyUsage != null) {
                certificate.addExtension(Extension.keyUsage, false, new KeyUsage(keyUsage));
            }
            if (subjectKeyIdentifier) {
                certificate.addExtension(Extension.subjectKeyIdentifier, false,
                        new org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils()
                                .createSubjectKeyIdentifier(pair.getPublic()));
            }
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(
                    certificate.build(new JcaContentSignerBuilder("SHA256withECDSA")
                            .setProvider("BC").build(pair.getPrivate())));
        }
    }
}
