package com.cryptocarver.crypto;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
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
import eu.europa.esig.dss.spi.x509.revocation.crl.ExternalResourcesCRLSource;
import eu.europa.esig.dss.token.KeyStoreSignatureTokenConnection;

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

    private static byte[] sign(byte[] pdf, File pkcs12File, char[] password, String tsaUrl,
                               VisibleSignatureOptions visibleSignature) throws Exception {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        if (pkcs12File == null || !pkcs12File.isFile()) throw new IllegalArgumentException("PKCS#12 file is required");
        char[] transientPassword = password == null ? new char[0] : password.clone();
        try (KeyStoreSignatureTokenConnection token = new KeyStoreSignatureTokenConnection(
                pkcs12File, "PKCS12", new PasswordProtection(transientPassword))) {
            if (token.getKeys().isEmpty()) throw new IllegalArgumentException("PKCS#12 does not contain a signing key");
            DSSPrivateKeyEntry signingKey = token.getKeys().get(0);

            return signWithToken(pdf, token, signingKey, tsaUrl, visibleSignature);
        } finally {
            Arrays.fill(transientPassword, '\0');
        }
    }

    private static byte[] signWithToken(byte[] pdf, AbstractKeyStoreTokenConnection token,
                                        DSSPrivateKeyEntry signingKey, String tsaUrl,
                                        VisibleSignatureOptions visibleSignature) throws Exception {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(tsaUrl == null ? SignatureLevel.PAdES_BASELINE_B : SignatureLevel.PAdES_BASELINE_T);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(signingKey.getCertificate());
        parameters.setCertificateChain(signingKey.getCertificateChain());
        if (visibleSignature != null) parameters.setImageParameters(visibleSignature.toImageParameters());

        DSSDocument document = new InMemoryDocument(pdf, "document.pdf");
        PAdESService service = new PAdESService(new CommonCertificateVerifier());
        if (tsaUrl != null) service.setTspSource(new OnlineTSPSource(tsaUrl));
        ToBeSigned toBeSigned = service.getDataToSign(document, parameters);
        SignatureValue signature = token.sign(toBeSigned, parameters.getDigestAlgorithm(), signingKey);
        DSSDocument signed = service.signDocument(document, parameters, signature);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            signed.writeTo(output);
            return output.toByteArray();
        }
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
            for (PDSignature signature : signatures) {
                int[] byteRange = signature.getByteRange();
                boolean byteRangePresent = byteRange != null && byteRange.length == 4;
                boolean byteRangeCoversDocument = hasDocumentCoveringByteRange(byteRange, pdf.length);
                boolean contentsPresent = signature.getContents() != null && signature.getContents().length > 0;
                summaries.add("Filter=" + nullSafe(signature.getFilter()) + "; SubFilter="
                        + nullSafe(signature.getSubFilter()) + "; ByteRange=" + byteRangePresent
                        + "; ByteRangeCoversDocument=" + byteRangeCoversDocument
                        + "; Contents=" + contentsPresent + "; Name=" + nullSafe(signature.getName()));
            }
            return new PdfSignatureInspection(signatures.size(), List.copyOf(summaries));
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
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF input is required");
        boolean trustConfigured = trustStoreFile != null;
        if (trustConfigured && !trustStoreFile.isFile()) throw new IllegalArgumentException("Truststore is not a file");
        char[] transientPassword = password == null ? new char[0] : password.clone();
        try {
            CommonCertificateVerifier verifier = new CommonCertificateVerifier();
            if (trustConfigured) verifier.setTrustedCertSources(loadTrustedCertificates(trustStoreFile, transientPassword));
            List<DSSDocument> localCrls = loadLocalCrlEvidence(localCrlFiles);
            if (!localCrls.isEmpty()) {
                verifier.setCrlSource(new ExternalResourcesCRLSource(localCrls.toArray(DSSDocument[]::new)));
                verifier.setCheckRevocationForUntrustedChains(true);
            }

            SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(new InMemoryDocument(pdf, "document.pdf"));
            validator.setCertificateVerifier(verifier);
            Reports reports = validator.validateDocument();
            String signatureId = reports.getSimpleReport().getFirstSignatureId();
            StringBuilder summary = new StringBuilder("--- PAdES Validation Report ---\n")
                    .append("Trust Policy: ").append(trustConfigured
                            ? "Configured local truststore (revocation is not fetched online)"
                            : "No truststore (signature/format evidence only)").append("\n")
                    .append("Revocation: ").append(localCrls.isEmpty()
                            ? "NOT EVALUATED (no local CRL evidence)"
                            : "LOCAL CRL EVIDENCE SUPPLIED (" + localCrls.size() + "; see DSS indication)")
                    .append("\n");
            if (signatureId == null) {
                summary.append("Signatures: none recognised by DSS\n");
            } else {
                summary.append("Indication: ").append(reports.getSimpleReport().getIndication(signatureId)).append("\n")
                        .append("SubIndication: ").append(reports.getSimpleReport().getSubIndication(signatureId)).append("\n")
                        .append("Signature Format: ").append(reports.getSimpleReport().getSignatureFormat(signatureId)).append("\n")
                        .append("Signed By: ").append(reports.getSimpleReport().getSignedBy(signatureId)).append("\n");
            }
            return new PadesValidationResult(summary.toString(), trustConfigured, localCrls.size(),
                    reports.getXmlSimpleReport(), reports.getXmlDetailedReport(), reports.getXmlValidationReport());
        } finally {
            Arrays.fill(transientPassword, '\0');
        }
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
            CMSOperations.parseX509Crl(encoded); // fail closed before DSS consumes it
            documents.add(new InMemoryDocument(encoded, crlFile.getName()));
        }
        return List.copyOf(documents);
    }

    /** Structural metadata only; it is not a cryptographic or trust validation result. */
    public record PdfSignatureInspection(int signatureCount, List<String> signatures) { }

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
    public record PadesValidationResult(String summary, boolean trustConfigured, int localCrlCount,
                                        String xmlSimpleReport, String xmlDetailedReport, String xmlEtsiReport) { }
}
