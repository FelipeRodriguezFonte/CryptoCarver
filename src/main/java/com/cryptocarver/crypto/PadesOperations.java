package com.cryptocarver.crypto;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.PAdESTimestampParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.AbstractKeyStoreTokenConnection;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import eu.europa.esig.dss.token.KeyStoreSignatureTokenConnection;
import com.cryptocarver.service.RevocationValidationService;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;

/**
 * Small PAdES Baseline-B signer for local PKCS#12 laboratory certificates.
 *
 * <p>The PDF is signed incrementally by DSS/PDFBox. This class intentionally
 * does not claim certificate trust, revocation status, visible appearance, or
 * a timestamp: those are separate PAdES concerns and are not silently implied
 * by a Baseline-B signature.</p>
 */
public final class PadesOperations {

    private PadesOperations() {
    }

    /** Creates an incrementally signed PAdES Baseline-B PDF using the first PKCS#12 key. */
    public static byte[] signBaselineB(byte[] pdf, File pkcs12File, char[] password) throws Exception {
        return signBaselineB(pdf, pkcs12File, password, null);
    }

    /** Creates Baseline-B and optionally adds a visible text appearance. */
    public static byte[] signBaselineB(byte[] pdf, File pkcs12File, char[] password,
                                       VisibleSignatureOptions visibleSignature) throws Exception {
        return sign(pdf, pkcs12File, password, null, visibleSignature);
    }

    /**
     * Creates a PAdES Baseline-T PDF by obtaining a signature timestamp from
     * the supplied RFC 3161 TSA. The timestamp is embedded by DSS; its trust
     * and the surrounding certificate path are deliberately not asserted here.
     */
    public static byte[] signBaselineT(byte[] pdf, File pkcs12File, char[] password, String tsaUrl) throws Exception {
        return signBaselineT(pdf, pkcs12File, password, tsaUrl, null);
    }

    /** Creates Baseline-T and optionally adds a visible text appearance. */
    public static byte[] signBaselineT(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                                       VisibleSignatureOptions visibleSignature) throws Exception {
        validateTsaUrl(tsaUrl);
        return sign(pdf, pkcs12File, password, tsaUrl.trim(), visibleSignature);
    }

    /**
     * Creates a real PAdES Baseline-LT document. DSS first creates the signed
     * CMS/T profile and then extends the PDF with its DSS dictionary containing
     * the validated certificate and revocation objects. No LT claim is made
     * unless the resulting PDF contains that evidence.
     */
    public static byte[] signBaselineLT(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                                         List<File> localRevocationFiles, boolean onlineRevocation) throws Exception {
        return signBaselineLT(pdf, pkcs12File, password, tsaUrl, localRevocationFiles, onlineRevocation, null);
    }

    public static byte[] signBaselineLT(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                                         List<File> localRevocationFiles, boolean onlineRevocation,
                                         VisibleSignatureOptions visibleSignature) throws Exception {
        validateTsaUrl(tsaUrl);
        if ((localRevocationFiles == null || localRevocationFiles.isEmpty()) && !onlineRevocation) {
            throw new IllegalArgumentException("PAdES-LT requires local CRL/OCSP evidence or explicit online revocation");
        }
        return sign(pdf, pkcs12File, password, tsaUrl.trim(), visibleSignature,
                SignatureLevel.PAdES_BASELINE_LT, localRevocationFiles, onlineRevocation);
    }

    /** Creates a genuine PAdES Baseline-LTA PDF: LT validation data plus DSS's archive timestamp. */
    public static byte[] signBaselineLTA(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                                          List<File> localRevocationFiles, boolean onlineRevocation) throws Exception {
        return signBaselineLTA(pdf, pkcs12File, password, tsaUrl, localRevocationFiles, onlineRevocation, null);
    }

    public static byte[] signBaselineLTA(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                                          List<File> localRevocationFiles, boolean onlineRevocation,
                                          VisibleSignatureOptions visibleSignature) throws Exception {
        validateTsaUrl(tsaUrl);
        if ((localRevocationFiles == null || localRevocationFiles.isEmpty()) && !onlineRevocation) {
            throw new IllegalArgumentException("PAdES-LTA requires local CRL/OCSP evidence or explicit online revocation");
        }
        return sign(pdf, pkcs12File, password, tsaUrl.trim(), visibleSignature,
                SignatureLevel.PAdES_BASELINE_LTA, localRevocationFiles, onlineRevocation);
    }

    /** Signs with a token-backed DSS connection; the opaque key never leaves the caller's token boundary. */
    public static byte[] signWithTokenConnection(byte[] pdf, AbstractKeyStoreTokenConnection token,
                                                   String alias, String tsaUrl) throws Exception {
        return signWithTokenConnection(pdf, token, alias, tsaUrl, null);
    }

    /** Token-backed counterpart supporting the same optional visible appearance. */
    public static byte[] signWithTokenConnection(byte[] pdf, AbstractKeyStoreTokenConnection token,
                                                   String alias, String tsaUrl,
                                                   VisibleSignatureOptions visibleSignature) throws Exception {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        if (token == null) throw new IllegalArgumentException("PKCS#11 signing token is required");
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("PKCS#11 signing alias is required");
        String normalizedTsa = null;
        if (tsaUrl != null && !tsaUrl.isBlank()) {
            validateTsaUrl(tsaUrl);
            normalizedTsa = tsaUrl.trim();
        }
        DSSPrivateKeyEntry signingKey = token.getKey(alias);
        if (signingKey == null) throw new IllegalArgumentException("PKCS#11 signing alias was not found: " + alias);
        return signWithToken(pdf, token, signingKey, normalizedTsa, visibleSignature);
    }

    /** Token-backed PAdES-LT counterpart; evidence retrieval follows the shared policy. */
    public static byte[] signBaselineLTWithTokenConnection(byte[] pdf, AbstractKeyStoreTokenConnection token,
                                                            String alias, String tsaUrl,
                                                            List<File> localRevocationFiles,
                                                            boolean onlineRevocation,
                                                            VisibleSignatureOptions visibleSignature) throws Exception {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        if (token == null) throw new IllegalArgumentException("PKCS#11 signing token is required");
        validateTsaUrl(tsaUrl);
        if ((localRevocationFiles == null || localRevocationFiles.isEmpty()) && !onlineRevocation) {
            throw new IllegalArgumentException("PAdES-LT requires local CRL/OCSP evidence or explicit online revocation");
        }
        DSSPrivateKeyEntry signingKey = token.getKey(alias);
        if (signingKey == null) throw new IllegalArgumentException("PKCS#11 signing alias was not found: " + alias);
        return signWithToken(pdf, token, signingKey, tsaUrl.trim(), visibleSignature,
                SignatureLevel.PAdES_BASELINE_LT, localRevocationFiles, onlineRevocation);
    }

    /** Token-backed PAdES-LTA counterpart. The token remains the only holder of the private key. */
    public static byte[] signBaselineLTAWithTokenConnection(byte[] pdf, AbstractKeyStoreTokenConnection token,
                                                             String alias, String tsaUrl,
                                                             List<File> localRevocationFiles,
                                                             boolean onlineRevocation,
                                                             VisibleSignatureOptions visibleSignature) throws Exception {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        if (token == null) throw new IllegalArgumentException("PKCS#11 signing token is required");
        validateTsaUrl(tsaUrl);
        if ((localRevocationFiles == null || localRevocationFiles.isEmpty()) && !onlineRevocation) {
            throw new IllegalArgumentException("PAdES-LTA requires local CRL/OCSP evidence or explicit online revocation");
        }
        DSSPrivateKeyEntry signingKey = token.getKey(alias);
        if (signingKey == null) throw new IllegalArgumentException("PKCS#11 signing alias was not found: " + alias);
        return signWithToken(pdf, token, signingKey, tsaUrl.trim(), visibleSignature,
                SignatureLevel.PAdES_BASELINE_LTA, localRevocationFiles, onlineRevocation);
    }

    private static byte[] sign(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                               VisibleSignatureOptions visibleSignature) throws Exception {
        return sign(pdf, pkcs12File, password, tsaUrl, visibleSignature,
                tsaUrl == null ? SignatureLevel.PAdES_BASELINE_B : SignatureLevel.PAdES_BASELINE_T,
                List.of(), false);
    }

    private static byte[] sign(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                               VisibleSignatureOptions visibleSignature, SignatureLevel level,
                               List<File> localRevocationFiles, boolean onlineRevocation) throws Exception {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        if (pkcs12File == null || !pkcs12File.isFile()) throw new IllegalArgumentException("PKCS#12 file is required");
        char[] transientPassword = password == null ? new char[0] : password.clone();
        try (KeyStoreSignatureTokenConnection token = new KeyStoreSignatureTokenConnection(
                pkcs12File, "PKCS12", new PasswordProtection(transientPassword))) {
            if (token.getKeys().isEmpty()) throw new IllegalArgumentException("PKCS#12 does not contain a signing key");
            DSSPrivateKeyEntry signingKey = token.getKeys().get(0);

            return signWithToken(pdf, token, signingKey, tsaUrl, visibleSignature, level,
                    localRevocationFiles, onlineRevocation);
        } finally {
            Arrays.fill(transientPassword, '\0');
        }
    }

    private static byte[] signWithToken(byte[] pdf, AbstractKeyStoreTokenConnection token,
                                        DSSPrivateKeyEntry signingKey, String tsaUrl,
                                        VisibleSignatureOptions visibleSignature) throws Exception {
        return signWithToken(pdf, token, signingKey, tsaUrl, visibleSignature,
                tsaUrl == null ? SignatureLevel.PAdES_BASELINE_B : SignatureLevel.PAdES_BASELINE_T,
                List.of(), false);
    }

    private static byte[] signWithToken(byte[] pdf, AbstractKeyStoreTokenConnection token,
                                        DSSPrivateKeyEntry signingKey, String tsaUrl,
                                        VisibleSignatureOptions visibleSignature, SignatureLevel level,
                                        List<File> localRevocationFiles, boolean onlineRevocation) throws Exception {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(level);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(signingKey.getCertificate());
        parameters.setCertificateChain(signingKey.getCertificateChain());
        if (visibleSignature != null) parameters.setImageParameters(visibleSignature.toImageParameters());
        if (level == SignatureLevel.PAdES_BASELINE_LTA) {
            // DSS 6.3's official LTA extension adds the LT DSS dictionary and then
            // a detached RFC 3161 archive timestamp over the resulting PDF.
            parameters.setArchiveTimestampParameters(new PAdESTimestampParameters(DigestAlgorithm.SHA256));
        }

        DSSDocument document = new InMemoryDocument(pdf, "document.pdf");
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        List<DSSDocument> localCrls = loadLocalCrlEvidence(localRevocationFiles);
        List<DSSDocument> localOcsps = loadLocalOcspEvidence(localRevocationFiles);
        if ((level == SignatureLevel.PAdES_BASELINE_LT || level == SignatureLevel.PAdES_BASELINE_LTA)
                && localCrls.isEmpty() && localOcsps.isEmpty() && !onlineRevocation) {
            throw new IllegalArgumentException("PAdES-LT could not obtain validated revocation evidence");
        }
        RevocationValidationService.configure(verifier,
                new RevocationValidationService.Configuration(onlineRevocation, localCrls, localOcsps));
        PAdESService service = new PAdESService(verifier);
        if (tsaUrl != null) service.setTspSource(new OnlineTSPSource(tsaUrl));
        ToBeSigned toBeSigned = service.getDataToSign(document, parameters);
        SignatureValue signature = token.sign(toBeSigned, parameters.getDigestAlgorithm(), signingKey);
        DSSDocument signed = service.signDocument(document, parameters, signature);
        if (level == SignatureLevel.PAdES_BASELINE_LT) {
            signed = service.extendDocument(signed, parameters);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            signed.writeTo(output);
            byte[] result = output.toByteArray();
            if (level == SignatureLevel.PAdES_BASELINE_LT || level == SignatureLevel.PAdES_BASELINE_LTA) {
                EmbeddedEvidence evidence = inspectEmbeddedEvidence(result);
                if (evidence.certificateCount() == 0 || evidence.crlCount() + evidence.ocspCount() == 0) {
                    throw new IllegalStateException("DSS did not embed sufficient PAdES-LT evidence (certificates="
                            + evidence.certificateCount() + ", CRLs=" + evidence.crlCount()
                            + ", OCSP=" + evidence.ocspCount() + ")");
                }
                if (level == SignatureLevel.PAdES_BASELINE_LTA) {
                    PadesValidationResult validation = validate(result, null, null, localRevocationFiles, onlineRevocation);
                    if (!validation.dssProfile().toUpperCase(java.util.Locale.ROOT).contains("BASELINE_LTA")
                            && !validation.dssProfile().toUpperCase(java.util.Locale.ROOT).contains("BASELINE-LTA")) {
                        throw new IllegalStateException("DSS did not produce PAdES Baseline-LTA: "
                                + validation.dssProfile() + "\n" + validation.summary());
                    }
                    if (!validation.archiveTimestampCryptographicIntegrity()) {
                        throw new IllegalStateException("DSS did not validate the RFC 3161 archive timestamp integrity:\n"
                                + validation.summary());
                    }
                }
            }
            return result;
        }
    }

    /** Counts the ETSI DSS dictionary objects embedded in the PDF. */
    public static EmbeddedEvidence inspectEmbeddedEvidence(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSBase base = document.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.getPDFName("DSS"));
            if (!(base instanceof COSDictionary dss)) return new EmbeddedEvidence(0, 0, 0);
            return new EmbeddedEvidence(arraySize(dss, "Certs"), arraySize(dss, "CRLs"), arraySize(dss, "OCSPs"));
        }
    }

    private static int arraySize(COSDictionary dictionary, String key) {
        COSBase value = dictionary.getDictionaryObject(COSName.getPDFName(key));
        return value instanceof COSArray array ? array.size() : 0;
    }

    private static void validateTsaUrl(String tsaUrl) {
        if (tsaUrl == null || tsaUrl.isBlank()) throw new IllegalArgumentException("A TSA URL is required for PAdES-T");
        try {
            URI uri = URI.create(tsaUrl.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("PAdES-T TSA URL must use http:// or https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("PAdES-T TSA URL must include a host");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid PAdES-T TSA URL: " + invalid.getMessage(), invalid);
        }
    }

    /**
     * Reads PDF signature dictionaries without making any trust assertion. This
     * is useful as a fast local sanity check before a full DSS validation flow.
     */
    public static PdfSignatureInspection inspectSignatures(byte[] pdf) throws Exception {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<PDSignature> signatures = document.getSignatureDictionaries();
            List<String> summaries = new java.util.ArrayList<>();
            int archiveTimestampCount = 0;
            for (PDSignature signature : signatures) {
                int[] byteRange = signature.getByteRange();
                boolean byteRangePresent = byteRange != null && byteRange.length == 4;
                boolean byteRangeCoversDocument = hasDocumentCoveringByteRange(byteRange, pdf.length);
                boolean contentsPresent = signature.getContents() != null && signature.getContents().length > 0;
                boolean archiveTimestamp = "DocTimeStamp".equals(signature.getCOSObject().getNameAsString(COSName.TYPE))
                        || "ETSI.RFC3161".equalsIgnoreCase(signature.getSubFilter());
                if (archiveTimestamp) archiveTimestampCount++;
                summaries.add("Filter=" + nullSafe(signature.getFilter()) + "; SubFilter="
                        + nullSafe(signature.getSubFilter()) + "; ByteRange=" + byteRangePresent
                        + "; ByteRangeCoversDocument=" + byteRangeCoversDocument
                        + "; Contents=" + contentsPresent + "; ArchiveTimestamp=" + archiveTimestamp
                        + "; Name=" + nullSafe(signature.getName()));
            }
            return new PdfSignatureInspection(signatures.size(), List.copyOf(summaries), archiveTimestampCount);
        }
    }

    /**
     * Validates a signed PDF with DSS. Without a truststore the result is a
     * signature/format report only; it is intentionally not described as a
     * trusted or revocation-checked signature. Supplying a truststore enables
     * PKIX trust evaluation, while revocation remains offline unless the user
     * explicitly uses a dedicated validation workflow.
     */
    public static PadesValidationResult validate(byte[] pdf, File trustStoreFile, char[] password) throws Exception {
        return validate(pdf, trustStoreFile, password, List.of());
    }

    /**
     * Same validation flow with optional CRL files supplied locally by the
     * user. No CRL/OCSP URL is fetched: evidence is parsed before use and
     * passed to DSS as an offline source.
     */
    public static PadesValidationResult validate(byte[] pdf, File trustStoreFile, char[] password,
                                                  List<File> localCrlFiles) throws Exception {
        return validate(pdf, trustStoreFile, password, localCrlFiles, false);
    }

    /** PAdES validation with explicit, opt-in online OCSP/CRL retrieval. */
    public static PadesValidationResult validate(byte[] pdf, File trustStoreFile, char[] password,
                                                  List<File> localCrlFiles, boolean onlineRevocation) throws Exception {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        boolean trustConfigured = trustStoreFile != null;
        if (trustConfigured && !trustStoreFile.isFile()) throw new IllegalArgumentException("Truststore is not a file");
        char[] transientPassword = password == null ? new char[0] : password.clone();
        try {
            CommonCertificateVerifier verifier = new CommonCertificateVerifier();
            if (trustConfigured) verifier.setTrustedCertSources(loadTrustedCertificates(trustStoreFile, transientPassword));
            List<DSSDocument> localCrls = loadLocalCrlEvidence(localCrlFiles);
            if (localCrlFiles != null && !localCrlFiles.isEmpty() && localCrls.isEmpty()) {
                throw new IllegalArgumentException("No valid X.509 CRL evidence was provided");
            }
            java.util.List<String> endpoints = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            RevocationValidationService.Configuration revocationConfiguration =
                    new RevocationValidationService.Configuration(onlineRevocation, localCrls, endpoints::add);
            RevocationValidationService.configure(verifier, revocationConfiguration);

            SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(new InMemoryDocument(pdf, "document.pdf"));
            validator.setCertificateVerifier(verifier);
            Reports reports;
            try {
                reports = validator.validateDocument();
            } catch (Exception networkOrDssFailure) {
                String message = networkOrDssFailure.getMessage() == null ? "DSS/online revocation source failed" : networkOrDssFailure.getMessage();
                boolean revoked = message.toUpperCase(java.util.Locale.ROOT).contains("REVOKED")
                        || message.toUpperCase(java.util.Locale.ROOT).contains("SUSPENDED");
                if (!onlineRevocation && !revoked) throw networkOrDssFailure;
                RevocationValidationService.Status failureStatus = revoked
                        ? RevocationValidationService.Status.REVOKED
                        : RevocationValidationService.Status.INDETERMINATE;
                RevocationValidationService.Result failure = new RevocationValidationService.Result(
                        failureStatus, localCrls.isEmpty() ? RevocationValidationService.Evidence.NONE
                                : RevocationValidationService.Evidence.LOCAL,
                        endpoints, List.of(message));
                return new PadesValidationResult("--- PAdES Validation Report ---\n"
                        + "Signature: " + (revoked ? "REVOKED (DSS validation stopped on revocation)" : "INDETERMINATE (DSS validation did not complete)") + "\n"
                        + "Chain trust: " + (trustConfigured ? "not established" : "not evaluated (no truststore)") + "\n"
                        + "Revocation: " + failure.status() + "\nEvidence source: " + failure.evidence()
                        + "\nRevocation errors: " + String.join("; ", failure.errors()) + "\n",
                        trustConfigured, localCrls.size(), onlineRevocation, failure, 0, false, false, false,
                        "not established", "not established", "", "", "");
            }
            String signatureId = reports.getSimpleReport().getFirstSignatureId();
            RevocationValidationService.Result revocation = RevocationValidationService.classifyLocalCrls(
                    embeddedCertificates(pdf), localCrlFiles, endpoints);
            if (revocation == null) {
                revocation = revocationResult(onlineRevocation, localCrls, reports, endpoints);
            }
            String dssSignatureFormat = signatureId == null ? "not established"
                    : String.valueOf(reports.getSimpleReport().getSignatureFormat(signatureId));
            String normalizedDssFormat = dssSignatureFormat.toUpperCase(java.util.Locale.ROOT);
            boolean dssLta = normalizedDssFormat.contains("BASELINE_LTA")
                    || normalizedDssFormat.contains("BASELINE-LTA");
            boolean dssLt = dssLta || normalizedDssFormat.contains("BASELINE_LT")
                    || normalizedDssFormat.contains("BASELINE-LT");
            StringBuilder summary = new StringBuilder("--- PAdES Validation Report ---\n")
                    .append("Trust Policy: ").append(trustConfigured
                            ? "Configured local truststore (chain trust is evaluated separately)"
                            : "No truststore (signature/format evidence only)").append("\n")
                    .append("Signature: ");
            if (signatureId == null) {
                summary.append("Signatures: none recognised by DSS\n");
            } else {
                summary.append("Indication: ").append(reports.getSimpleReport().getIndication(signatureId)).append("\n")
                        .append("SubIndication: ").append(reports.getSimpleReport().getSubIndication(signatureId)).append("\n")
                        .append("Signature Format: ").append(reports.getSimpleReport().getSignatureFormat(signatureId)).append("\n")
                        .append("Signed By: ").append(reports.getSimpleReport().getSignedBy(signatureId)).append("\n");
            }
            String indication = signatureId == null ? "NOT ESTABLISHED"
                    : String.valueOf(reports.getSimpleReport().getIndication(signatureId));
            summary.append("Cryptographic signature: ").append(indication).append("\n")
                    .append("Chain trust: ").append(trustConfigured ? "evaluated by configured truststore" : "not evaluated (no truststore)").append("\n")
                    .append("Revocation: ").append(revocation.status() == RevocationValidationService.Status.DISABLED
                            ? "NOT EVALUATED" : revocation.status()).append("\n")
                    .append("Evidence source: ").append(revocation.evidence()).append("\n")
                    .append("Online endpoints: ").append(revocation.endpoints().isEmpty() ? "none reported by DSS" : String.join(", ", revocation.endpoints())).append("\n");
            EmbeddedEvidence embedded = inspectEmbeddedEvidence(pdf);
            int archiveTimestampCount = inspectSignatures(pdf).archiveTimestampCount();
            ArchiveTimestampAssessment archiveTimestamp = assessArchiveTimestamp(reports);
            boolean archiveTimestampDssValid = archiveTimestamp.dssPassed();
            boolean tsaChainTrusted = trustConfigured && archiveTimestamp.tsaChainTrusted();
            String effectiveProfile = dssLta && embedded.certificateCount() > 0
                    && embedded.crlCount() + embedded.ocspCount() > 0 && archiveTimestampCount > 0
                    && archiveTimestampDssValid && tsaChainTrusted
                    ? "PAdES Baseline-LTA"
                    : dssLt && !dssLta && embedded.certificateCount() > 0
                    && embedded.crlCount() + embedded.ocspCount() > 0
                    ? "PAdES Baseline-LT" : "not established";
            summary.append("Embedded evidence: certificates=").append(embedded.certificateCount())
                    .append(", CRLs=").append(embedded.crlCount())
                    .append(", OCSP=").append(embedded.ocspCount()).append("\n")
                    .append("Archive timestamp: ").append(archiveTimestampCount > 0 ? "present" : "absent")
                    .append(" (count=").append(archiveTimestampCount)
                    .append(", RFC3161 integrity=").append(archiveTimestamp.cryptographicIntegrity() ? "VALID" : "INVALID")
                    .append(", DSS validation=").append(archiveTimestampDssValid ? "PASSED" : "NOT PASSED")
                    .append(")\n")
                    .append("Signer DSS indication: ").append(signatureId == null ? "not established" : indication)
                    .append("; chain trust=").append(signerChainTrusted(reports, signatureId, trustConfigured) ? "ESTABLISHED" : "NOT ESTABLISHED").append("\n")
                    .append("TSA DSS indication: ").append(archiveTimestamp.dssIndication())
                    .append("; chain trust=").append(tsaChainTrusted ? "ESTABLISHED" : "NOT ESTABLISHED").append("\n")
                    .append("DSS archive timestamp reports: ").append(String.join(", ", archiveTimestamp.reports())).append("\n")
                    .append("DSS profile: ").append(dssSignatureFormat).append("\n")
                    .append("Effective profile: ").append(effectiveProfile).append("\n");
            if (!revocation.errors().isEmpty()) summary.append("Revocation errors: ").append(String.join("; ", revocation.errors())).append("\n");
            return new PadesValidationResult(summary.toString(), trustConfigured, localCrls.size(), onlineRevocation,
                    revocation, archiveTimestampCount, archiveTimestamp.cryptographicIntegrity(), archiveTimestampDssValid,
                    tsaChainTrusted, dssSignatureFormat, effectiveProfile,
                    reports.getXmlSimpleReport(), reports.getXmlDetailedReport(), reports.getXmlValidationReport());
        } finally {
            Arrays.fill(transientPassword, '\0');
        }
    }

    private static RevocationValidationService.Result revocationResult(boolean online, List<DSSDocument> localCrls, Reports reports, List<String> endpoints) {
        return RevocationValidationService.classifyDssReport(online, !localCrls.isEmpty(),
                reports.getXmlDetailedReport(), endpoints, List.of());
    }

    private static ArchiveTimestampAssessment assessArchiveTimestamp(Reports reports) {
        if (reports == null || reports.getSimpleReport() == null) {
            return new ArchiveTimestampAssessment(false, false, false, false, "not established", List.of());
        }
        java.util.LinkedHashMap<String, String> indications = new java.util.LinkedHashMap<>();
        boolean cryptographicIntegrity = false;
        boolean dssPassed = false;
        boolean tsaChainTrusted = false;
        for (String timestampId : reports.getSimpleReport().getTimestampIdList()) {
            var indication = reports.getSimpleReport().getIndication(timestampId);
            var subIndication = reports.getSimpleReport().getSubIndication(timestampId);
            indications.put(timestampId, indication + "/" + subIndication);
            cryptographicIntegrity |= timestampCryptographicallyUsable(indication, subIndication);
            dssPassed |= dssPassed(indication);
            tsaChainTrusted |= dssPassed(indication);
        }
        // SimpleReport.timestampIdList is DSS's document-timestamp section.
        // Signature timestamps are not archive timestamps and must not satisfy
        // the LTA archive-seal requirement.
        String first = indications.values().stream().findFirst().orElse("not established");
        return new ArchiveTimestampAssessment(!indications.isEmpty(), cryptographicIntegrity, dssPassed,
                tsaChainTrusted, first, List.copyOf(indications.entrySet().stream()
                        .map(entry -> entry.getKey() + ":" + entry.getValue()).toList()));
    }

    private static boolean dssPassed(eu.europa.esig.dss.enumerations.Indication indication) {
        return indication == eu.europa.esig.dss.enumerations.Indication.PASSED
                || indication == eu.europa.esig.dss.enumerations.Indication.TOTAL_PASSED;
    }

    private static boolean timestampCryptographicallyUsable(
            eu.europa.esig.dss.enumerations.Indication indication,
            eu.europa.esig.dss.enumerations.SubIndication subIndication) {
        if (dssPassed(indication)) return true;
        // DSS can still expose a cryptographically checked RFC 3161 token when
        // its TSA path is not trusted. This is deliberately reported only as
        // integrity; it is never sufficient for the effective LTA profile.
        return indication == eu.europa.esig.dss.enumerations.Indication.INDETERMINATE
                && subIndication == eu.europa.esig.dss.enumerations.SubIndication.NO_CERTIFICATE_CHAIN_FOUND;
    }

    private static boolean signerChainTrusted(Reports reports, String signatureId, boolean trustConfigured) {
        if (!trustConfigured || signatureId == null || !dssPassed(reports.getSimpleReport().getIndication(signatureId))) {
            return false;
        }
        var chain = reports.getSimpleReport().getCertificateChain(signatureId);
        return chain != null && chain.getCertificate() != null && !chain.getCertificate().isEmpty();
    }

    private record ArchiveTimestampAssessment(boolean present, boolean cryptographicIntegrity, boolean dssPassed,
                                              boolean tsaChainTrusted, String dssIndication, List<String> reports) { }

    private static List<X509Certificate> embeddedCertificates(byte[] pdf) throws Exception {
        List<X509Certificate> result = new java.util.ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSBase base = document.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.getPDFName("DSS"));
            if (!(base instanceof COSDictionary dss)) return result;
            COSBase value = dss.getDictionaryObject(COSName.getPDFName("Certs"));
            if (!(value instanceof COSArray certificates)) return result;
            java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
            for (int index = 0; index < certificates.size(); index++) {
                COSBase entry = certificates.getObject(index);
                if (entry instanceof org.apache.pdfbox.cos.COSString encoded) {
                    java.security.cert.Certificate certificate = factory.generateCertificate(
                            new java.io.ByteArrayInputStream(encoded.getBytes()));
                    if (certificate instanceof X509Certificate x509) result.add(x509);
                }
            }
        }
        return result;
    }

    /**
     * Performs only the inexpensive structural ByteRange checks that can be
     * established from the PDF bytes. It deliberately does not verify the CMS
     * signature stored in /Contents and therefore is not a trust assertion.
     */
    private static boolean hasDocumentCoveringByteRange(int[] byteRange, int documentLength) {
        if (byteRange == null || byteRange.length != 4 || documentLength < 0) return false;
        for (int value : byteRange) {
            if (value < 0) return false;
        }
        long firstEnd = (long) byteRange[0] + byteRange[1];
        long secondEnd = (long) byteRange[2] + byteRange[3];
        return byteRange[0] == 0 && firstEnd <= byteRange[2] && secondEnd == documentLength;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "(not specified)" : value;
    }

    private static eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource loadTrustedCertificates(
            File trustStoreFile, char[] password) throws Exception {
        Exception lastFailure = null;
        for (String type : new String[] { "PKCS12", "JKS" }) {
            try (var input = new java.io.FileInputStream(trustStoreFile)) {
                KeyStore keyStore = KeyStore.getInstance(type);
                keyStore.load(input, password);
                eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource source =
                        new eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource();
                java.util.Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    java.security.cert.Certificate certificate = keyStore.getCertificate(aliases.nextElement());
                    if (certificate instanceof X509Certificate x509) source.addCertificate(new CertificateToken(x509));
                }
                return source;
            } catch (Exception error) {
                lastFailure = error;
            }
        }
        throw new java.io.IOException("Unable to load truststore as PKCS12 or JKS", lastFailure);
    }

    private static List<DSSDocument> loadLocalCrlEvidence(List<File> localCrlFiles) throws Exception {
        if (localCrlFiles == null || localCrlFiles.isEmpty()) return List.of();
        List<DSSDocument> documents = new java.util.ArrayList<>();
        for (File crlFile : localCrlFiles) {
            if (crlFile == null || !crlFile.isFile()) throw new IllegalArgumentException("CRL evidence is not a file");
            if (crlFile.length() > 4L * 1024L * 1024L) throw new IllegalArgumentException("CRL evidence exceeds the 4 MiB limit");
            byte[] encoded = java.nio.file.Files.readAllBytes(crlFile.toPath());
            try {
                CMSOperations.parseX509Crl(encoded); // fail closed before DSS consumes it
                documents.add(new InMemoryDocument(encoded, crlFile.getName()));
            } catch (Exception ignored) {
                // OCSP fixtures are loaded by loadLocalOcspEvidence below.
            }
        }
        return List.copyOf(documents);
    }

    private static List<DSSDocument> loadLocalOcspEvidence(List<File> files) throws Exception {
        if (files == null || files.isEmpty()) return List.of();
        List<DSSDocument> documents = new java.util.ArrayList<>();
        for (File file : files) {
            if (file == null || !file.isFile()) throw new IllegalArgumentException("Revocation evidence is not a file");
            byte[] encoded = java.nio.file.Files.readAllBytes(file.toPath());
            try {
                new org.bouncycastle.cert.ocsp.OCSPResp(encoded);
                documents.add(new InMemoryDocument(encoded, file.getName()));
            } catch (Exception ignored) { }
        }
        return List.copyOf(documents);
    }

    /** Structural metadata only; it is not a cryptographic or trust validation result. */
    public record PdfSignatureInspection(int signatureCount, List<String> signatures, int archiveTimestampCount) { }

    public record EmbeddedEvidence(int certificateCount, int crlCount, int ocspCount) { }

    /** Coordinates are PDF points from the lower-left corner; page numbering starts at one. */
    public record VisibleSignatureOptions(int page, float originX, float originY, float width, float height, String text) {
        public VisibleSignatureOptions {
            if (page < 1) throw new IllegalArgumentException("Visible signature page must be at least 1");
            if (originX < 0 || originY < 0 || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Visible signature position and size must be positive");
            }
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Visible signature text is required");
            if (text.length() > 500) throw new IllegalArgumentException("Visible signature text exceeds 500 characters");
        }

        private SignatureImageParameters toImageParameters() {
            SignatureFieldParameters field = new SignatureFieldParameters();
            field.setPage(page);
            field.setOriginX(originX);
            field.setOriginY(originY);
            field.setWidth(width);
            field.setHeight(height);
            SignatureImageTextParameters textParameters = new SignatureImageTextParameters();
            textParameters.setText(text);
            SignatureImageParameters image = new SignatureImageParameters();
            image.setFieldParameters(field);
            image.setTextParameters(textParameters);
            return image;
        }
    }

    /** DSS reports are exported separately because they can contain certificate PII. */
    public record PadesValidationResult(String summary, boolean trustConfigured, int localCrlCount, boolean onlineRevocation,
                                        RevocationValidationService.Result revocation,
                                        int archiveTimestampCount, boolean archiveTimestampCryptographicIntegrity,
                                        boolean archiveTimestampDssValid, boolean tsaChainTrusted, String dssProfile,
                                        String effectiveProfile,
                                        String xmlSimpleReport, String xmlDetailedReport, String xmlEtsiReport) { }
}
