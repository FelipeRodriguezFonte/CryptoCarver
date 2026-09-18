package com.cryptocarver.crypto;

import eu.europa.esig.dss.enumerations.QCType;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.extension.PSD2QcType;
import eu.europa.esig.dss.model.x509.extension.PdsLocation;
import eu.europa.esig.dss.model.x509.extension.QCLimitValue;
import eu.europa.esig.dss.model.x509.extension.QcStatements;
import eu.europa.esig.dss.model.x509.extension.RoleOfPSP;
import eu.europa.esig.dss.spi.QcStatementUtils;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.PolicyQualifierId;
import org.bouncycastle.asn1.x509.PolicyQualifierInfo;
import org.bouncycastle.asn1.x509.qualified.QCStatement;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads an X.509 certificate against the eIDAS certificate profiles, including
 * the two that the European Digital Identity Wallet introduced.
 *
 * <p><b>It describes; it does not certify.</b> A certificate can satisfy every
 * syntactic requirement below and still not be qualified, because qualification
 * is decided by the Trusted List, not by the bytes. Nothing here is a conformity
 * assessment, and the app's existing boundary in {@code docs/LAB_VS_PRODUCTION.md}
 * is unchanged.</p>
 *
 * <h2>What it knows, and where each rule comes from</h2>
 * <ul>
 *   <li><b>EN 319 412-5 qcStatements</b> and <b>TS 119 495</b> (PSD2 roles and
 *       NCA) are decoded by DSS, which the app already depends on and which the
 *       Commission maintains. No OID table is copied here for those.</li>
 *   <li><b>TS 119 412-6 V1.2.1 (2026-04)</b> — certificate profiles for PID,
 *       Wallet, EAA, QEAA and PSBEAA providers. Its Annex A defines three
 *       identifiers under {@code 0.4.0.194126.1} that no released DSS knows yet,
 *       which is exactly why they are decoded here: a PID provider certificate
 *       otherwise reads as "unknown qcStatement".</li>
 *   <li><b>TS 119 411-8 V1.1.1 (2025-10)</b> — the access certificate policy for
 *       Wallet Relying Parties, with four policy identifiers under
 *       {@code 0.4.0.194118.1}.</li>
 * </ul>
 *
 * <p>Findings carry the requirement identifier from the specification
 * ({@code PID-4.5-01}, {@code GEN-6.6.1-05}) so that a finding can be taken back
 * to the clause it came from instead of being argued about.</p>
 */
public final class EidasCertificateInspector {

    // --- TS 119 412-6 V1.2.1, Annex A (normative) -------------------------
    /** {@code id-etsi-eidas2-qct-extensions ::= { itu-t(0) identified-organization(4) etsi(0) qct-extension(194126) 1 }} */
    public static final String EIDAS2_QCT_ARC = "0.4.0.194126.1";
    /** {@code id-etsi-qct-pid} — PID Provider sign/seal certificate. */
    public static final String QCT_PID = EIDAS2_QCT_ARC + ".1";
    /** {@code id-etsi-qct-wal} — Wallet Provider sign/seal certificate. */
    public static final String QCT_WALLET = EIDAS2_QCT_ARC + ".2";
    /** {@code id-etsi-qcs-QcPSB} — the public sector body statement, whose value
     *  is a SEQUENCE of country of legislation, authentic source and legislation. */
    public static final String QCS_PSB = EIDAS2_QCT_ARC + ".3";

    // --- TS 119 411-8 V1.1.1, clause 5.3 ---------------------------------
    /** {@code { itu-t(0) identified-organization(4) etsi(0) eudiwrp(194118) policy-identifiers(1) }} */
    public static final String WRPAC_POLICY_ARC = "0.4.0.194118.1";
    public static final String POLICY_NCP_N_EUDIWRP = WRPAC_POLICY_ARC + ".1";
    public static final String POLICY_NCP_L_EUDIWRP = WRPAC_POLICY_ARC + ".2";
    public static final String POLICY_QCP_N_EUDIWRP = WRPAC_POLICY_ARC + ".3";
    public static final String POLICY_QCP_L_EUDIWRP = WRPAC_POLICY_ARC + ".4";

    /** {@code id-at-telephoneNumber}, ITU-T X.520 clause 6.7.1 — the otherName
     *  type TS 119 411-8 GEN-6.6.1-07 accepts as contact information. */
    private static final String ID_AT_TELEPHONE_NUMBER = "2.5.4.20";
    private static final String EKU_SERVER_AUTH = "1.3.6.1.5.5.7.3.1";

    /** Which eIDAS role the certificate declares itself to be in. A certificate
     *  can be in several at once — a PID provider certificate is also a
     *  qualified seal certificate. */
    public enum Profile {
        PID_PROVIDER("PID Provider sign/seal (TS 119 412-6 clause 4)"),
        WALLET_PROVIDER("Wallet Provider sign/seal (TS 119 412-6 clause 5)"),
        PSBEAA_PROVIDER("PSBEAA Provider sign/seal (TS 119 412-6 clause 8)"),
        WALLET_RELYING_PARTY_ACCESS("Wallet Relying Party access (TS 119 411-8)"),
        QUALIFIED_ESIGN("Qualified certificate for electronic signature"),
        QUALIFIED_ESEAL("Qualified certificate for electronic seal"),
        QUALIFIED_WEB("Qualified certificate for website authentication"),
        PSD2("PSD2 payment service provider (TS 119 495)");

        private final String description;

        Profile(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    /** @param requirement the clause identifier from the specification, or {@code null} for general observations */
    public record Finding(String severity, String requirement, String message) {
    }

    /** {@code QcPSB ::= SEQUENCE { countryOfLegislation PrintableString(SIZE(2)), authSourceIdentification UTF8String, legislationIdentification UTF8String }} */
    public record QcPsb(String countryOfLegislation, String authSourceIdentification, String legislationIdentification) {
    }

    public record Report(List<Profile> profiles,
                         List<String> qcTypeOids,
                         List<String> certificatePolicies,
                         QcPsb qcPsb,
                         QcStatements qcStatements,
                         List<Finding> findings) {

        public boolean declaresAnyEidasProfile() {
            return !profiles.isEmpty();
        }
    }

    private EidasCertificateInspector() {
    }

    public static Report inspect(X509Certificate certificate) throws Exception {
        JcaX509CertificateHolder holder = new JcaX509CertificateHolder(certificate);
        QcStatements qcStatements = QcStatementUtils.getQcStatements(new CertificateToken(certificate));

        List<String> qcTypeOids = qcTypeOids(qcStatements);
        List<String> policies = certificatePolicyOids(holder);
        QcPsb qcPsb = readQcPsb(holder);

        List<Profile> profiles = new ArrayList<>();
        if (qcTypeOids.contains(QCT_PID)) {
            profiles.add(Profile.PID_PROVIDER);
        }
        if (qcTypeOids.contains(QCT_WALLET)) {
            profiles.add(Profile.WALLET_PROVIDER);
        }
        if (qcPsb != null) {
            profiles.add(Profile.PSBEAA_PROVIDER);
        }
        boolean isWrpac = policies.stream().anyMatch(oid -> oid.startsWith(WRPAC_POLICY_ARC + "."));
        if (isWrpac) {
            profiles.add(Profile.WALLET_RELYING_PARTY_ACCESS);
        }
        if (qcStatements != null) {
            for (QCType type : nullSafe(qcStatements.getQcTypes())) {
                switch (type.getOid()) {
                    case "0.4.0.1862.1.6.1" -> profiles.add(Profile.QUALIFIED_ESIGN);
                    case "0.4.0.1862.1.6.2" -> profiles.add(Profile.QUALIFIED_ESEAL);
                    case "0.4.0.1862.1.6.3" -> profiles.add(Profile.QUALIFIED_WEB);
                    default -> { }
                }
            }
            if (qcStatements.getPsd2QcType() != null) {
                profiles.add(Profile.PSD2);
            }
        }

        List<Finding> findings = new ArrayList<>();
        checkProviderProfiles(certificate, holder, qcStatements, qcPsb, profiles, findings);
        checkWalletRelyingPartyAccess(certificate, holder, qcStatements, policies, findings);

        if (findings.isEmpty()) {
            findings.add(new Finding("INFO", null, profiles.isEmpty()
                    ? "No eIDAS profile is declared by this certificate."
                    : "No findings against the declared profiles."));
        }
        return new Report(List.copyOf(profiles), qcTypeOids, policies, qcPsb, qcStatements, List.copyOf(findings));
    }

    // ------------------------------------------------- TS 119 412-6 checks

    private static void checkProviderProfiles(X509Certificate certificate,
                                              JcaX509CertificateHolder holder,
                                              QcStatements qcStatements,
                                              QcPsb qcPsb,
                                              List<Profile> profiles,
                                              List<Finding> findings) {
        boolean providerProfile = profiles.contains(Profile.PID_PROVIDER)
                || profiles.contains(Profile.WALLET_PROVIDER)
                || profiles.contains(Profile.PSBEAA_PROVIDER);
        if (!providerProfile) {
            return;
        }

        // PID-4.4.1-01. Only the first half is checkable here: the extension must
        // be present. Whether the bits form exactly one of the Type A/B/C/F
        // settings is decided by table 1 of EN 319 412-2, which this app does not
        // implement -- so the bits are reported and the comparison is not claimed.
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage == null) {
            findings.add(new Finding("ERROR", "PID-4.4.1-01",
                    "The key usage extension shall be present."));
        } else {
            findings.add(new Finding("INFO", "PID-4.4.1-01",
                    "Key usage is " + describeKeyUsage(keyUsage)
                            + ". Whether this is exactly one of the Type A/B/C/F settings is decided by"
                            + " table 1 of EN 319 412-2, which is not implemented here."));
        }

        // PID-4.4.2-01
        if (holder.getExtension(Extension.subjectKeyIdentifier) == null) {
            findings.add(new Finding("ERROR", "PID-4.4.2-01",
                    "The subject key identifier extension shall be present."));
        }

        // PID-4.4.3-01: required unless the certificate is self-signed.
        boolean selfIssued = certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());
        if (!selfIssued && holder.getExtension(Extension.authorityInfoAccess) == null) {
            findings.add(new Finding("ERROR", "PID-4.4.3-01",
                    "Authority Information Access shall be present when the certificate is not self-signed."));
        }

        // PID-4.1-02
        Set<String> critical = certificate.getCriticalExtensionOIDs();
        if (critical != null && !critical.isEmpty()) {
            findings.add(new Finding("INFO", "PID-4.1-02",
                    "Extensions marked critical: " + String.join(", ", critical)
                            + ". These shall not be critical unless RFC 5280 or the profile allows it."));
        }

        if (profiles.contains(Profile.PSBEAA_PROVIDER)) {
            // PSB-8.3-05
            String country = qcPsb.countryOfLegislation();
            if (!"EU".equals(country) && (country == null || country.length() != 2)) {
                findings.add(new Finding("ERROR", "PSB-8.3-05",
                        "QcPSB country of legislation shall be an ISO 3166 alpha-2 code, or 'EU' for Union law;"
                                + " found '" + country + "'."));
            }
            // PSB-8.3-04
            if (isBlank(qcPsb.authSourceIdentification())) {
                findings.add(new Finding("ERROR", "PSB-8.3-04",
                        "QcPSB shall contain an unambiguous identification of the authentic source."));
            }
            // PSB-8.3-02
            if (isBlank(qcPsb.legislationIdentification())) {
                findings.add(new Finding("ERROR", "PSB-8.3-02",
                        "QcPSB shall identify the law under which the PSBEAA provider is established."));
            }
        }

        if (qcStatements == null || !qcStatements.isQcCompliance()) {
            findings.add(new Finding("WARN", null,
                    "No QcCompliance statement: this certificate does not claim to be a qualified certificate."));
        }
    }

    // ------------------------------------------------- TS 119 411-8 checks

    private static void checkWalletRelyingPartyAccess(X509Certificate certificate,
                                                      JcaX509CertificateHolder holder,
                                                      QcStatements qcStatements,
                                                      List<String> policies,
                                                      List<Finding> findings) throws Exception {
        String policy = policies.stream()
                .filter(oid -> oid.startsWith(WRPAC_POLICY_ARC + "."))
                .findFirst().orElse(null);
        if (policy == null) {
            return;
        }
        boolean legalPerson = POLICY_NCP_L_EUDIWRP.equals(policy) || POLICY_QCP_L_EUDIWRP.equals(policy);
        boolean qualified = POLICY_QCP_N_EUDIWRP.equals(policy) || POLICY_QCP_L_EUDIWRP.equals(policy);

        findings.add(new Finding("INFO", "GEN-6.6.1-03",
                "Policy " + policy + " — " + describeWrpacPolicy(policy) + "."));

        // GEN-6.6.1-05. The requirement is stated for certificates issued to
        // legal persons, so it is only an error for the two legal-person policies.
        boolean hasOrganizationIdentifier = certificate.getSubjectX500Principal()
                .getName(javax.security.auth.x500.X500Principal.RFC2253)
                .contains("2.5.4.97=");
        if (legalPerson && !hasOrganizationIdentifier) {
            findings.add(new Finding("ERROR", "GEN-6.6.1-05",
                    "The organizationIdentifier attribute shall be present in the subject of a"
                            + " wallet-relying party access certificate issued to a legal person."));
        }

        // GEN-6.6.1-06
        if (!hasCpsUri(holder)) {
            findings.add(new Finding("ERROR", "GEN-6.6.1-06",
                    "A cpsURI qualifier shall be present under certificate policies."));
        }

        // GEN-6.6.1-07
        if (!hasContactInformation(holder)) {
            findings.add(new Finding("ERROR", "GEN-6.6.1-07",
                    "Contact information shall be present in the Subject Alternative Name: a URI, an"
                            + " rfc822Name, or an otherName with type-id id-at-telephoneNumber ("
                            + ID_AT_TELEPHONE_NUMBER + ")."));
        }

        // The note under GEN-6.6.1-01: "Neither website authentication
        // certificates nor short-term certificates are applicable to
        // wallet-relying party access certificates."
        List<String> eku = certificate.getExtendedKeyUsage();
        boolean serverAuth = eku != null && eku.contains(EKU_SERVER_AUTH);
        boolean webQcType = qcStatements != null && nullSafe(qcStatements.getQcTypes()).stream()
                .anyMatch(type -> "0.4.0.1862.1.6.3".equals(type.getOid()));
        if (serverAuth || webQcType) {
            findings.add(new Finding("WARN", "GEN-6.6.1-01",
                    "This looks like a website authentication certificate, which TS 119 411-8 states is"
                            + " not applicable to wallet-relying party access certificates."));
        }

        if (qualified && (qcStatements == null || !qcStatements.isQcCompliance())) {
            findings.add(new Finding("ERROR", "GEN-6.6.1-02",
                    "Policy " + policy + " is a qualified policy, so EN 319 411-2 applies and a"
                            + " QcCompliance statement is expected."));
        }
    }

    // ------------------------------------------------------------- decoding

    /** Reads every QcType OID, including ones DSS does not recognise — which is
     *  the whole point for {@link #QCT_PID} and {@link #QCT_WALLET}, defined in a
     *  specification newer than any released DSS. */
    private static List<String> qcTypeOids(QcStatements qcStatements) {
        List<String> oids = new ArrayList<>();
        if (qcStatements != null) {
            for (QCType type : nullSafe(qcStatements.getQcTypes())) {
                oids.add(type.getOid());
            }
        }
        return List.copyOf(oids);
    }

    /**
     * Decodes the QcPSB statement of TS 119 412-6 Annex A. DSS files an
     * unrecognised statement under "other OIDs" and discards its value, so the
     * extension is re-read here to get at the SEQUENCE inside.
     */
    private static QcPsb readQcPsb(JcaX509CertificateHolder holder) {
        Extension extension = holder.getExtension(Extension.qCStatements);
        if (extension == null) {
            return null;
        }
        ASN1Sequence statements = ASN1Sequence.getInstance(extension.getParsedValue());
        for (ASN1Encodable element : statements) {
            QCStatement statement = QCStatement.getInstance(element);
            if (!QCS_PSB.equals(statement.getStatementId().getId())) {
                continue;
            }
            ASN1Encodable info = statement.getStatementInfo();
            if (info == null) {
                return new QcPsb(null, null, null);
            }
            ASN1Sequence value = ASN1Sequence.getInstance(info);
            return new QcPsb(
                    asString(value, 0),
                    asString(value, 1),
                    asString(value, 2));
        }
        return null;
    }

    private static String asString(ASN1Sequence sequence, int index) {
        if (index >= sequence.size()) {
            return null;
        }
        ASN1Primitive item = sequence.getObjectAt(index).toASN1Primitive();
        return item instanceof ASN1String text ? text.getString() : item.toString();
    }

    private static List<String> certificatePolicyOids(JcaX509CertificateHolder holder) {
        Extension extension = holder.getExtension(Extension.certificatePolicies);
        if (extension == null) {
            return List.of();
        }
        Set<String> oids = new LinkedHashSet<>();
        for (ASN1Encodable element : ASN1Sequence.getInstance(extension.getParsedValue())) {
            oids.add(PolicyInformation.getInstance(element).getPolicyIdentifier().getId());
        }
        return List.copyOf(oids);
    }

    private static boolean hasCpsUri(JcaX509CertificateHolder holder) {
        Extension extension = holder.getExtension(Extension.certificatePolicies);
        if (extension == null) {
            return false;
        }
        for (ASN1Encodable element : ASN1Sequence.getInstance(extension.getParsedValue())) {
            ASN1Sequence qualifiers = PolicyInformation.getInstance(element).getPolicyQualifiers();
            if (qualifiers == null) {
                continue;
            }
            for (ASN1Encodable qualifier : qualifiers) {
                PolicyQualifierInfo info = PolicyQualifierInfo.getInstance(qualifier);
                if (PolicyQualifierId.id_qt_cps.equals(info.getPolicyQualifierId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasContactInformation(JcaX509CertificateHolder holder) {
        Extension extension = holder.getExtension(Extension.subjectAlternativeName);
        if (extension == null) {
            return false;
        }
        for (GeneralName name : GeneralNames.getInstance(extension.getParsedValue()).getNames()) {
            switch (name.getTagNo()) {
                case GeneralName.uniformResourceIdentifier, GeneralName.rfc822Name -> {
                    return true;
                }
                case GeneralName.otherName -> {
                    ASN1Sequence other = ASN1Sequence.getInstance(name.getName());
                    if (other.size() > 0 && ASN1ObjectIdentifier.getInstance(other.getObjectAt(0))
                            .getId().equals(ID_AT_TELEPHONE_NUMBER)) {
                        return true;
                    }
                }
                default -> { }
            }
        }
        return false;
    }

    // -------------------------------------------------------------- report

    /** A human-readable report, in Spanish or English. */
    public static String describe(X509Certificate certificate, Locale locale) throws Exception {
        boolean spanish = locale != null && "es".equals(locale.getLanguage());
        Report report = inspect(certificate);
        StringBuilder text = new StringBuilder();

        text.append(spanish ? "Perfiles eIDAS declarados" : "Declared eIDAS profiles").append('\n');
        if (report.profiles().isEmpty()) {
            text.append("  ").append(spanish ? "ninguno" : "none").append('\n');
        } else {
            for (Profile profile : report.profiles()) {
                text.append("  - ").append(profile.description()).append('\n');
            }
        }

        QcStatements qc = report.qcStatements();
        text.append('\n').append(spanish ? "qcStatements" : "qcStatements").append('\n');
        if (qc == null) {
            text.append("  ").append(spanish ? "extensión ausente" : "extension absent").append('\n');
        } else {
            text.append("  QcCompliance : ").append(qc.isQcCompliance()).append('\n');
            text.append("  QcSSCD/QSCD  : ").append(qc.isQcQSCD()).append('\n');
            if (!report.qcTypeOids().isEmpty()) {
                text.append("  QcType       : ").append(String.join(", ", report.qcTypeOids())).append('\n');
            }
            if (qc.getQcSemanticsIdentifier() != null) {
                text.append("  Semantics    : ").append(qc.getQcSemanticsIdentifier().getDescription()).append('\n');
            }
            QCLimitValue limit = qc.getQcLimitValue();
            if (limit != null) {
                text.append("  QcLimitValue : ").append(limit.getAmount())
                        .append(" x10^").append(limit.getExponent())
                        .append(' ').append(limit.getCurrency()).append('\n');
            }
            if (qc.getQcEuRetentionPeriod() != null) {
                text.append("  Retention    : ").append(qc.getQcEuRetentionPeriod())
                        .append(spanish ? " años" : " years").append('\n');
            }
            for (PdsLocation pds : nullSafe(qc.getQcEuPDS())) {
                text.append("  PDS          : ").append(pds.getUrl())
                        .append(" (").append(pds.getLanguage()).append(")\n");
            }
            if (!nullSafe(qc.getQcLegislationCountryCodes()).isEmpty()) {
                text.append("  Legislation  : ")
                        .append(String.join(", ", qc.getQcLegislationCountryCodes())).append('\n');
            }
            PSD2QcType psd2 = qc.getPsd2QcType();
            if (psd2 != null) {
                text.append("  PSD2 NCA     : ").append(psd2.getNcaName())
                        .append(" / ").append(psd2.getNcaId()).append('\n');
                for (RoleOfPSP role : nullSafe(psd2.getRolesOfPSP())) {
                    text.append("  PSD2 role    : ").append(role.getPspOid() == null
                            ? String.valueOf(role.getPspName())
                            : role.getPspOid().getDescription()).append('\n');
                }
            }
            if (!nullSafe(qc.getOtherOids()).isEmpty()) {
                text.append("  Other        : ").append(String.join(", ", qc.getOtherOids())).append('\n');
            }
        }

        if (report.qcPsb() != null) {
            text.append('\n').append("QcPSB (TS 119 412-6)").append('\n');
            text.append("  ").append(spanish ? "legislación" : "legislation")
                    .append(" : ").append(report.qcPsb().countryOfLegislation()).append('\n');
            text.append("  ").append(spanish ? "fuente auténtica" : "authentic source")
                    .append(" : ").append(report.qcPsb().authSourceIdentification()).append('\n');
            text.append("  ").append(spanish ? "norma" : "law")
                    .append(" : ").append(report.qcPsb().legislationIdentification()).append('\n');
        }

        if (!report.certificatePolicies().isEmpty()) {
            text.append('\n').append(spanish ? "Políticas de certificado" : "Certificate policies").append('\n');
            for (String oid : report.certificatePolicies()) {
                text.append("  - ").append(oid);
                String named = describeWrpacPolicy(oid);
                if (named != null) {
                    text.append("  ").append(named);
                }
                text.append('\n');
            }
        }

        text.append('\n').append(spanish ? "Hallazgos" : "Findings").append('\n');
        for (Finding finding : report.findings()) {
            text.append("  [").append(finding.severity()).append(']');
            if (finding.requirement() != null) {
                text.append(' ').append(finding.requirement());
            }
            text.append(' ').append(finding.message()).append('\n');
        }

        text.append('\n').append(spanish
                ? "Este informe describe el contenido del certificado. La cualificación la decide"
                  + " la lista de confianza, no los bytes."
                : "This report describes the certificate's content. Qualification is decided by the"
                  + " trusted list, not by the bytes.").append('\n');
        return text.toString();
    }

    private static String describeWrpacPolicy(String oid) {
        return switch (oid) {
            case POLICY_NCP_N_EUDIWRP -> "NCP-n-eudiwrp, normalized, natural person";
            case POLICY_NCP_L_EUDIWRP -> "NCP-l-eudiwrp, normalized, legal person";
            case POLICY_QCP_N_EUDIWRP -> "QCP-n-eudiwrp, qualified, natural person";
            case POLICY_QCP_L_EUDIWRP -> "QCP-l-eudiwrp, qualified, legal person";
            default -> null;
        };
    }

    private static String describeKeyUsage(boolean[] keyUsage) {
        String[] names = {"digitalSignature", "nonRepudiation", "keyEncipherment", "dataEncipherment",
                "keyAgreement", "keyCertSign", "cRLSign", "encipherOnly", "decipherOnly"};
        List<String> set = new ArrayList<>();
        for (int i = 0; i < names.length && i < keyUsage.length; i++) {
            if (keyUsage[i]) {
                set.add(names[i]);
            }
        }
        return set.isEmpty() ? "empty" : String.join(" + ", set);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
