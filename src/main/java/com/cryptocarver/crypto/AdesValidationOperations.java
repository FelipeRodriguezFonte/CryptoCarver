package com.cryptocarver.crypto;

import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * One validator for every AdES flavour eIDAS names — XAdES, PAdES, CAdES and
 * ASiC — that says which baseline level a signature actually reached and emits
 * the <b>ETSI TS 119 102-2 validation report</b>.
 *
 * <p>The app has produced that report for a while without calling it that: DSS
 * builds it on every validation and the XAdES and PAdES panes were already
 * asking for it. What was missing was a way to get at it for the other two
 * formats, and a name for the thing. Both matter, because a TS 119 102-2 report
 * is the artefact a conformance exercise asks for, and "our tool said it was
 * fine" is not.</p>
 *
 * <h2>The level is the answer, not a detail</h2>
 * <p>A B-B signature proves who signed. A B-T adds a timestamp, so it proves
 * <em>when</em>. A B-LT carries the revocation evidence with it, so it can still
 * be checked after the certificate expires. A B-LTA keeps that provable as the
 * algorithms age. "Valid" without the level says almost nothing: a B-B that
 * verifies today is not evidence of anything next year, and
 * {@link Result#level()} is what tells them apart.</p>
 *
 * <h2>What this does not do</h2>
 * <p>It validates; it does not upgrade. Producing a B-LT or a B-LTA needs a
 * timestamp authority and fresh revocation data, which are network services and
 * therefore the signing panes' business, not this one's. Validation without a
 * trust anchor reaches INDETERMINATE and says so rather than reporting a pass:
 * a chain that ends nowhere is not a valid chain, it is an unfinished one.</p>
 */
public final class AdesValidationOperations {

    /** @param level the baseline reached, as DSS names it, e.g. {@code XAdES_BASELINE_LTA} */
    public record SignatureOutcome(String signatureId,
                                   String level,
                                   Indication indication,
                                   SubIndication subIndication,
                                   String signedBy,
                                   List<String> errors,
                                   List<String> warnings) {

        public boolean passed() {
            return Indication.TOTAL_PASSED.equals(indication);
        }

        /** Whether the signature carries its own evidence: LT and LTA embed the
         *  revocation data, so they remain checkable after the certificate is
         *  gone. B and T do not. */
        public boolean selfContained() {
            return level != null && (level.endsWith("_LT") || level.endsWith("_LTA"));
        }
    }

    /** @param etsiValidationReportXml the TS 119 102-2 report */
    public record Result(List<SignatureOutcome> signatures,
                         String simpleReportXml,
                         String etsiValidationReportXml,
                         boolean trustAnchorSupplied) {

        public boolean allPassed() {
            return !signatures.isEmpty() && signatures.stream().allMatch(SignatureOutcome::passed);
        }
    }

    private AdesValidationOperations() {
    }

    /**
     * Validates a signed document of any supported format.
     *
     * @param fileName      the name the document travels under; DSS uses the
     *                      extension to pick a validator, so an ASiC handed over
     *                      as "document" is read as a plain zip
     * @param trustStore    a keystore whose certificates are the trust anchors,
     *                      or {@code null} to validate without one — which can
     *                      only ever reach INDETERMINATE
     */
    public static Result validate(byte[] document,
                                  String fileName,
                                  File trustStore,
                                  char[] trustStorePassword) throws Exception {
        if (document == null || document.length == 0) {
            throw new IllegalArgumentException("A signed document is required");
        }
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        boolean trustSupplied = trustStore != null;
        if (trustSupplied) {
            verifier.setTrustedCertSources(trustedSource(trustStore, trustStorePassword));
        }

        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(
                new InMemoryDocument(document, fileName == null ? "document" : fileName));
        validator.setCertificateVerifier(verifier);

        Reports reports = validator.validateDocument();
        SimpleReport simple = reports.getSimpleReport();

        List<SignatureOutcome> outcomes = new ArrayList<>();
        for (String id : simple.getSignatureIdList()) {
            outcomes.add(new SignatureOutcome(
                    id,
                    simple.getSignatureFormat(id) == null ? null : simple.getSignatureFormat(id).toString(),
                    simple.getIndication(id),
                    simple.getSubIndication(id),
                    simple.getSignedBy(id),
                    simple.getAdESValidationErrors(id).stream().map(Object::toString).toList(),
                    simple.getAdESValidationWarnings(id).stream().map(Object::toString).toList()));
        }
        return new Result(List.copyOf(outcomes), reports.getXmlSimpleReport(),
                reports.getXmlValidationReport(), trustSupplied);
    }

    /** Every certificate in a keystore, as trust anchors. */
    private static eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource trustedSource(
            File keystore, char[] password) throws Exception {
        eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource source =
                new eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource();
        KeyStore store = KeyStore.getInstance(keystore.getName().toLowerCase(Locale.ROOT).endsWith(".jks")
                ? "JKS" : "PKCS12");
        try (java.io.InputStream input = new java.io.FileInputStream(keystore)) {
            store.load(input, password);
        }
        for (Enumeration<String> aliases = store.aliases(); aliases.hasMoreElements(); ) {
            java.security.cert.Certificate certificate = store.getCertificate(aliases.nextElement());
            if (certificate instanceof X509Certificate x509) {
                source.addCertificate(new eu.europa.esig.dss.model.x509.CertificateToken(x509));
            }
        }
        return source;
    }

    public static String describe(Result result, Locale locale) {
        boolean spanish = locale != null && "es".equals(locale.getLanguage());
        StringBuilder text = new StringBuilder();
        text.append(spanish ? "Validación AdES (ETSI EN 319 102-1)" : "AdES validation (ETSI EN 319 102-1)")
                .append('\n');
        text.append("  ").append(spanish ? "firmas" : "signatures").append(": ")
                .append(result.signatures().size()).append('\n');
        text.append("  ").append(spanish ? "ancla de confianza" : "trust anchor").append(": ")
                .append(result.trustAnchorSupplied()
                        ? (spanish ? "aportada" : "supplied")
                        : (spanish ? "ninguna — el resultado no puede pasar de INDETERMINATE"
                                   : "none — the result cannot get past INDETERMINATE"))
                .append('\n');

        for (SignatureOutcome outcome : result.signatures()) {
            text.append('\n').append("  ").append(outcome.signatureId()).append('\n');
            text.append("    ").append(spanish ? "nivel" : "level").append("     : ")
                    .append(outcome.level()).append(outcome.selfContained()
                            ? (spanish ? "  (lleva su propia evidencia)" : "  (carries its own evidence)")
                            : (spanish ? "  (no sobrevive a la caducidad del certificado)"
                                       : "  (does not survive the certificate expiring)"))
                    .append('\n');
            text.append("    ").append(spanish ? "resultado" : "indication").append(" : ")
                    .append(outcome.indication());
            if (outcome.subIndication() != null) {
                text.append(" / ").append(outcome.subIndication());
            }
            text.append('\n');
            text.append("    ").append(spanish ? "firmante" : "signed by").append("  : ")
                    .append(outcome.signedBy()).append('\n');
            for (String error : outcome.errors()) {
                text.append("    [ERROR] ").append(error).append('\n');
            }
            for (String warning : outcome.warnings()) {
                text.append("    [WARN ] ").append(warning).append('\n');
            }
        }

        text.append('\n').append(spanish
                ? "El informe TS 119 102-2 completo va aparte; esto es sólo el resumen."
                : "The full TS 119 102-2 report is separate; this is only the summary.").append('\n');
        return text.toString();
    }
}
