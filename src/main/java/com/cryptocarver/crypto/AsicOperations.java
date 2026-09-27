package com.cryptocarver.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import eu.europa.esig.dss.asic.xades.ASiCWithXAdESSignatureParameters;
import eu.europa.esig.dss.asic.xades.signature.ASiCWithXAdESService;
import eu.europa.esig.dss.asic.cades.ASiCWithCAdESSignatureParameters;
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESService;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.AbstractKeyStoreTokenConnection;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.KeyStoreSignatureTokenConnection;
import eu.europa.esig.dss.enumerations.Indication;
import com.cryptocarver.service.RevocationValidationService;
import java.net.URI;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** ASiC-S/ASiC-E signing helpers for CAdES and XAdES baseline profiles. */
public final class AsicOperations {
    public static final String MIME_TYPE = "application/vnd.etsi.asic-s+zip";
    public static final String ASIC_E_MIME_TYPE = "application/vnd.etsi.asic-e+zip";
    private static final String SIGNATURE_ENTRY = "META-INF/signatures.p7s";
    private static final String MANIFEST_ENTRY = "META-INF/ASiCManifest.xml";
    private static final String ASIC_MANIFEST_NS = "http://uri.etsi.org/02918/v1.2.1#";
    private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String SHA256_URI = "http://www.w3.org/2001/04/xmlenc#sha256";
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_EXPANDED_ARCHIVE_BYTES = MAX_ENTRY_BYTES + 4L * 1024L * 1024L;

    private AsicOperations() {
    }

    /** Signs an ASiC-S container at the requested CAdES or XAdES baseline level. */
    public static byte[] signAsicS(byte[] content, String fileName, File pkcs12File, char[] password,
                                   String format, String level, String tsaUrl,
                                   List<File> revocationFiles, boolean onlineRevocation) throws Exception {
        DSSDocument payload = asicSPayload(content, fileName);
        if ("XAdES".equalsIgnoreCase(format)) {
            return signXades(List.of(payload), ASiCContainerType.ASiC_S,
                    pkcs12File, password, null, level, tsaUrl, revocationFiles, onlineRevocation);
        }
        requireCades(format);
        return signCades(List.of(payload), ASiCContainerType.ASiC_S,
                pkcs12File, password, null, level, tsaUrl, revocationFiles, onlineRevocation);
    }

    /** Signs an ASiC-E container at the requested CAdES or XAdES baseline level. */
    public static byte[] signAsicE(Map<String, byte[]> payloads, File pkcs12File, char[] password,
                                   String format, String level, String tsaUrl,
                                   List<File> revocationFiles, boolean onlineRevocation) throws Exception {
        AsicEManifest manifest = prepareAsicEManifest(payloads);
        List<DSSDocument> documents = new java.util.ArrayList<>();
        manifest.payloads().forEach((name, value) -> documents.add(new InMemoryDocument(value, name)));
        if ("XAdES".equalsIgnoreCase(format)) {
            return signXades(documents, ASiCContainerType.ASiC_E, pkcs12File, password, null,
                    level, tsaUrl, revocationFiles, onlineRevocation);
        }
        requireCades(format);
        return signCades(documents, ASiCContainerType.ASiC_E, pkcs12File, password, null,
                level, tsaUrl, revocationFiles, onlineRevocation);
    }

    /** Token-backed ASiC-S signing. */
    public static byte[] signAsicS(byte[] content, String fileName, AbstractKeyStoreTokenConnection token,
                                   String alias, String format, String level, String tsaUrl,
                                   List<File> revocationFiles, boolean onlineRevocation) throws Exception {
        DSSDocument payload = asicSPayload(content, fileName);
        if ("XAdES".equalsIgnoreCase(format)) return signXades(List.of(payload),
                ASiCContainerType.ASiC_S, null, null, new TokenKey(token, alias), level, tsaUrl, revocationFiles, onlineRevocation);
        requireCades(format);
        return signCades(List.of(payload), ASiCContainerType.ASiC_S,
                null, null, new TokenKey(token, alias), level, tsaUrl, revocationFiles, onlineRevocation);
    }

    private static DSSDocument asicSPayload(byte[] content, String fileName) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("ASiC payload is required");
        if (content.length > MAX_ENTRY_BYTES) throw new IllegalArgumentException("ASiC payload exceeds the 64 MiB limit");
        return new InMemoryDocument(content, payloadName(fileName));
    }

    /** Token-backed ASiC-E signing. */
    public static byte[] signAsicE(Map<String, byte[]> payloads, AbstractKeyStoreTokenConnection token,
                                   String alias, String format, String level, String tsaUrl,
                                   List<File> revocationFiles, boolean onlineRevocation) throws Exception {
        AsicEManifest manifest = prepareAsicEManifest(payloads);
        List<DSSDocument> documents = new java.util.ArrayList<>();
        manifest.payloads().forEach((name, value) -> documents.add(new InMemoryDocument(value, name)));
        if ("XAdES".equalsIgnoreCase(format)) {
            return signXades(documents, ASiCContainerType.ASiC_E, null, null, new TokenKey(token, alias),
                    level, tsaUrl, revocationFiles, onlineRevocation);
        }
        requireCades(format);
        return signCades(documents, ASiCContainerType.ASiC_E, null, null, new TokenKey(token, alias),
                level, tsaUrl, revocationFiles, onlineRevocation);
    }

    private record TokenKey(AbstractKeyStoreTokenConnection token, String alias) { }

    private static byte[] signCades(List<DSSDocument> documents, ASiCContainerType type, File file, char[] password,
                                    TokenKey tokenKey, String levelName, String tsa, List<File> evidence,
                                    boolean online) throws Exception {
        SignatureLevel level = cadesLevel(levelName);
        requireLevel(level, tsa, evidence, online);
        char[] pass = password == null ? new char[0] : password.clone();
        KeyStoreSignatureTokenConnection local = null;
        try {
            AbstractKeyStoreTokenConnection token;
            DSSPrivateKeyEntry key;
            if (tokenKey != null) { token = tokenKey.token(); key = token.getKey(tokenKey.alias()); }
            else {
                local = new KeyStoreSignatureTokenConnection(file, "PKCS12", new KeyStore.PasswordProtection(pass));
                token = local; key = token.getKeys().isEmpty() ? null : token.getKeys().get(0);
            }
            if (key == null) throw new IllegalArgumentException("Signing key was not found");
            ASiCWithCAdESSignatureParameters parameters = new ASiCWithCAdESSignatureParameters();
            parameters.setSignatureLevel(level);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(key.getCertificate());
            parameters.setCertificateChain(key.getCertificateChain());
            parameters.aSiC().setContainerType(type);
            ASiCWithCAdESService service = new ASiCWithCAdESService(verifier(evidence, online));
            if (tsa != null && !tsa.isBlank()) service.setTspSource(new OnlineTSPSource(validateTsa(tsa)));
            ToBeSigned toSign = service.getDataToSign(documents, parameters);
            SignatureValue value = token.sign(toSign, parameters.getDigestAlgorithm(), key);
            DSSDocument signed = service.signDocument(documents, parameters, value);
            if (level == SignatureLevel.CAdES_BASELINE_LT) signed = service.extendDocument(signed, parameters);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) { signed.writeTo(out); return out.toByteArray(); }
        } finally { if (local != null) local.close(); Arrays.fill(pass, '\0'); }
    }

    private static byte[] signXades(List<DSSDocument> documents, ASiCContainerType type, File file, char[] password,
                                    TokenKey tokenKey, String levelName, String tsa, List<File> evidence,
                                    boolean online) throws Exception {
        SignatureLevel level = xadesLevel(levelName);
        requireLevel(level, tsa, evidence, online);
        char[] pass = password == null ? new char[0] : password.clone();
        KeyStoreSignatureTokenConnection local = null;
        try {
            AbstractKeyStoreTokenConnection token;
            DSSPrivateKeyEntry key;
            if (tokenKey != null) { token = tokenKey.token(); key = token.getKey(tokenKey.alias()); }
            else {
                local = new KeyStoreSignatureTokenConnection(file, "PKCS12", new KeyStore.PasswordProtection(pass));
                token = local; key = token.getKeys().isEmpty() ? null : token.getKeys().get(0);
            }
            if (key == null) throw new IllegalArgumentException("Signing key was not found");
            ASiCWithXAdESSignatureParameters parameters = new ASiCWithXAdESSignatureParameters();
            parameters.setSignatureLevel(level); parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(key.getCertificate()); parameters.setCertificateChain(key.getCertificateChain());
            parameters.aSiC().setContainerType(type);
            CommonCertificateVerifier verifier = verifier(evidence, online);
            ASiCWithXAdESService service = new ASiCWithXAdESService(verifier);
            if (tsa != null && !tsa.isBlank()) service.setTspSource(new OnlineTSPSource(validateTsa(tsa)));
            ToBeSigned toSign = service.getDataToSign(documents, parameters);
            SignatureValue value = token.sign(toSign, parameters.getDigestAlgorithm(), key);
            DSSDocument signed = service.signDocument(documents, parameters, value);
            if (level == SignatureLevel.XAdES_BASELINE_LT) signed = service.extendDocument(signed, parameters);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) { signed.writeTo(out); return out.toByteArray(); }
        } finally { if (local != null) local.close(); Arrays.fill(pass, '\0'); }
    }

    private static void requireCades(String format) {
        if (!"CAdES".equalsIgnoreCase(format)) throw new IllegalArgumentException("Unsupported ASiC signature format: " + format);
    }
    private static SignatureLevel cadesLevel(String level) { return switch(level.toUpperCase(java.util.Locale.ROOT)) {
        case "B" -> SignatureLevel.CAdES_BASELINE_B; case "T" -> SignatureLevel.CAdES_BASELINE_T;
        case "LT" -> SignatureLevel.CAdES_BASELINE_LT; case "LTA" -> SignatureLevel.CAdES_BASELINE_LTA;
        default -> throw new IllegalArgumentException("Unsupported CAdES level: " + level); }; }
    private static SignatureLevel xadesLevel(String level) { return switch(level.toUpperCase(java.util.Locale.ROOT)) {
        case "B" -> SignatureLevel.XAdES_BASELINE_B; case "T" -> SignatureLevel.XAdES_BASELINE_T;
        case "LT" -> SignatureLevel.XAdES_BASELINE_LT; case "LTA" -> SignatureLevel.XAdES_BASELINE_LTA;
        default -> throw new IllegalArgumentException("Unsupported XAdES level: " + level); }; }
    private static void requireLevel(SignatureLevel level, String tsa, List<File> evidence, boolean online) {
        String n = level.name();
        if ((n.endsWith("_T") || n.endsWith("_LT") || n.endsWith("_LTA"))) validateTsa(tsa);
        if ((n.endsWith("_LT") || n.endsWith("_LTA")) && (evidence == null || evidence.isEmpty()) && !online)
            throw new IllegalArgumentException("ASiC-LT/LTA requires local CRL/OCSP evidence or explicit online revocation");
    }
    private static String validateTsa(String tsa) {
        if (tsa == null || tsa.isBlank()) throw new IllegalArgumentException("A TSA URL is required for T/LT/LTA");
        URI uri=URI.create(tsa.trim()); if (!List.of("http","https").contains(uri.getScheme()) || uri.getHost()==null)
            throw new IllegalArgumentException("Invalid TSA URL"); return tsa.trim(); }
    private static CommonCertificateVerifier verifier(List<File> files, boolean online) throws Exception {
        List<DSSDocument> crls=PadesOperations.loadLocalCrlEvidence(files), ocsps=PadesOperations.loadLocalOcspEvidence(files);
        if (files!=null && !files.isEmpty() && crls.isEmpty() && ocsps.isEmpty()) throw new IllegalArgumentException("No valid X.509 CRL or OCSP evidence was provided");
        CommonCertificateVerifier v=new CommonCertificateVerifier();
        RevocationValidationService.configure(v,new RevocationValidationService.Configuration(online,crls,ocsps)); return v;
    }

    /** Creates an ASiC-S ZIP with a first, uncompressed mimetype entry and detached CAdES-BES signature. */
    public static byte[] createAsicS(byte[] content, String fileName, File pkcs12File, char[] password) throws Exception {
        if (content == null || content.length == 0) throw new IllegalArgumentException("ASiC payload is required");
        if (content.length > MAX_ENTRY_BYTES) throw new IllegalArgumentException("ASiC payload exceeds the 64 MiB limit");
        String safeName = payloadName(fileName);
        if (pkcs12File == null || !pkcs12File.isFile()) throw new IllegalArgumentException("PKCS#12 file is required");
        char[] transientPassword = password == null ? new char[0] : password.clone();
        try {
            KeyStore keyStore = loadPkcs12(pkcs12File, transientPassword);
            String alias = firstPrivateKeyAlias(keyStore);
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, transientPassword);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            byte[] signature = CMSOperations.generateCadesBes(content, certificate, privateKey, null, true);
            return createAsicSWithCadesSignature(content, safeName, signature);
        } finally {
            Arrays.fill(transientPassword, '\0');
        }
    }

    /**
     * Packages a pre-built detached CAdES-BES signature, for example one
     * produced by a PKCS#11 session. The signature is verified against the
     * supplied content and its signingCertificateV2 binding before it enters
     * the ZIP container.
     */
    public static byte[] createAsicSWithCadesSignature(byte[] content, String fileName, byte[] detachedCades)
            throws Exception {
        if (content == null || content.length == 0) throw new IllegalArgumentException("ASiC payload is required");
        if (content.length > MAX_ENTRY_BYTES) throw new IllegalArgumentException("ASiC payload exceeds the 64 MiB limit");
        if (detachedCades == null || detachedCades.length == 0) throw new IllegalArgumentException("Detached CAdES signature is required");
        String safeName = payloadName(fileName);
        CMSOperations.VerificationResult verification = CMSOperations.verifySignedData(detachedCades, null, content);
        CMSOperations.CadesProfile profile = CMSOperations.inspectCadesProfile(detachedCades);
        if (!verification.verified || !"CAdES-BES".equals(profile.profile()) || !profile.certificateBindingValid()) {
            throw new IllegalArgumentException("ASiC-S requires a valid detached CAdES-BES signature bound to the payload");
        }
        return packageAsic(safeName, content, detachedCades);
    }

    /** Returns the declared first-entry mimetype without claiming that the archive is valid. */
    public static String detectDeclaredMimeType(byte[] asicBytes) throws Exception {
        if (asicBytes == null || asicBytes.length == 0) throw new IllegalArgumentException("ASiC input is required");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(asicBytes))) {
            ZipEntry first = zip.getNextEntry();
            if (first == null || !"mimetype".equals(first.getName()) || first.getMethod() != ZipEntry.STORED) {
                throw new IllegalArgumentException("ASiC first STORED mimetype entry is missing");
            }
            return readEntry(zip, first);
        }
    }

    /**
     * Inspects a bounded ASiC-S archive. Legacy CAdES-BES is checked locally;
     * DSS-produced CAdES and XAdES are checked by the common AdES validator.
     * Certificate trust is not evaluated by this convenience overload.
     */
    public static AsicInspection inspectAndVerify(byte[] asicBytes) throws Exception {
        return inspectAndVerify(asicBytes, null);
    }

    /**
     * Inspects an ASiC-S archive and optionally validates a legacy CAdES-BES
     * signer against a local truststore. Use AdesValidationOperations for a
     * trusted DSS level and revocation result on advanced CAdES or XAdES.
     */
    public static AsicInspection inspectAndVerify(byte[] asicBytes, KeyStore trustStore) throws Exception {
        if (asicBytes == null || asicBytes.length == 0) throw new IllegalArgumentException("ASiC input is required");
        if (asicBytes.length > MAX_ENTRY_BYTES + 2L * 1024L * 1024L) throw new IllegalArgumentException("ASiC container exceeds the laboratory limit");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(asicBytes))) {
            ZipEntry first = zip.getNextEntry();
            boolean mimeTypeValid = first != null && "mimetype".equals(first.getName())
                    && first.getMethod() == ZipEntry.STORED && MIME_TYPE.equals(readEntry(zip, first));
            if (!mimeTypeValid) throw new IllegalArgumentException("Not a valid ASiC-S container: first STORED mimetype entry is missing");

            String payloadName = null;
            byte[] payload = null;
            byte[] signature = null;
            int entryCount = 1;
            long expandedBytes = first.getSize() > 0 ? first.getSize() : MIME_TYPE.length();
            java.util.Set<String> entryNames = new java.util.HashSet<>();
            entryNames.add("mimetype");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                String name = entry.getName();
                if (name == null || name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new IllegalArgumentException("Unsafe ASiC ZIP entry name");
                }
                if (!entryNames.add(name)) throw new IllegalArgumentException("Duplicate ASiC ZIP entry: " + name);
                byte[] value = readEntryBytes(zip, entry);
                expandedBytes = boundedExpandedSize(expandedBytes, value.length);
                if (SIGNATURE_ENTRY.equals(name)) {
                    if (signature != null) throw new IllegalArgumentException("ASiC-S must contain one signature file");
                    signature = value;
                } else if (!name.startsWith("META-INF/")) {
                    if (payload != null) throw new IllegalArgumentException("ASiC-S must contain one payload file");
                    payloadName = name;
                    payload = value;
                }
            }
            if (payload == null) throw new IllegalArgumentException("ASiC-S payload is missing");
            if (signature == null) {
                AdesValidationOperations.SignatureOutcome outcome = inspectDssSignature(asicBytes, "signed.asics");
                return new AsicInspection(payloadName, entryCount, mimeTypeValid, true,
                        outcome.level(), true, CmsInspectionReport.ValidationState.NOT_EVALUATED,
                        "Use AdesValidationOperations with a truststore and revocation evidence for PKIX validation");
            }
            CMSOperations.VerificationResult verification = CMSOperations.verifySignedData(signature, null, payload);
            CMSOperations.CadesProfile profile = CMSOperations.inspectCadesProfile(signature);
            CmsInspectionReport cmsReport = new CmsInspector().inspect(signature, payload, trustStore);
            CmsInspectionReport.ValidationStep trustStep = cmsReport.getValidationSteps().stream()
                    .filter(step -> "Trust Chain".equals(step.getStepName()))
                    .findFirst()
                    .orElse(new CmsInspectionReport.ValidationStep("Trust Chain",
                            CmsInspectionReport.ValidationState.NOT_EVALUATED,
                            "No trust decision was produced"));
            return new AsicInspection(payloadName, entryCount, mimeTypeValid, verification.verified,
                    profile.profile(), profile.certificateBindingValid(), trustStep.getState(), trustStep.getDetails());
        }
    }

    /** Creates a legacy ASiC-E/CAdES-BES container with a local SHA-256 manifest. */
    public static byte[] createAsicE(Map<String, byte[]> payloads, File pkcs12File, char[] password) throws Exception {
        AsicEManifest prepared = prepareAsicEManifest(payloads);
        if (pkcs12File == null || !pkcs12File.isFile()) throw new IllegalArgumentException("PKCS#12 file is required");
        char[] transientPassword = password == null ? new char[0] : password.clone();
        try {
            KeyStore keyStore = loadPkcs12(pkcs12File, transientPassword);
            String alias = firstPrivateKeyAlias(keyStore);
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, transientPassword);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            byte[] signature = CMSOperations.generateCadesBes(prepared.manifest(), certificate, privateKey, null, true);
            return createAsicEWithCadesSignature(prepared.payloads(), prepared.manifest(), signature);
        } finally {
            Arrays.fill(transientPassword, '\0');
        }
    }

    /** Prepares the canonical local manifest to be signed by a local key or a PKCS#11 session. */
    public static AsicEManifest prepareAsicEManifest(Map<String, byte[]> payloads) throws Exception {
        Map<String, byte[]> safePayloads = normalizePayloads(payloads);
        return new AsicEManifest(safePayloads, buildAsicEManifest(safePayloads));
    }

    /** Fail-closed packaging API for a pre-built ASiC-E CAdES signature, including token-produced signatures. */
    public static byte[] createAsicEWithCadesSignature(Map<String, byte[]> payloads, byte[] manifest,
                                                         byte[] detachedCades) throws Exception {
        Map<String, byte[]> safePayloads = normalizePayloads(payloads);
        ManifestCheck manifestCheck = verifyAsicEManifest(manifest, safePayloads);
        if (!manifestCheck.digestValid() || !manifestCheck.signatureReferenceValid()) {
            throw new IllegalArgumentException("ASiC-E manifest does not match the supplied payloads");
        }
        CMSOperations.VerificationResult verification = CMSOperations.verifySignedData(detachedCades, null, manifest);
        CMSOperations.CadesProfile profile = CMSOperations.inspectCadesProfile(detachedCades);
        if (!verification.verified || !"CAdES-BES".equals(profile.profile()) || !profile.certificateBindingValid()) {
            throw new IllegalArgumentException("ASiC-E requires a valid detached CAdES-BES signature of its manifest");
        }
        return packageAsicE(safePayloads, manifest, detachedCades);
    }

    /**
     * Inspects a legacy ASiC-E manifest locally, or delegates DSS-produced
     * CAdES and XAdES containers to the common AdES validator.
     */
    public static AsicEInspection inspectAndVerifyE(byte[] asicBytes) throws Exception {
        return inspectAndVerifyE(asicBytes, null);
    }

    /**
     * Validates a legacy ASiC-E CAdES-BES signer against a supplied local
     * truststore. Advanced containers use the common AdES validator; call it
     * directly to evaluate trust, revocation and the achieved DSS level.
     */
    public static AsicEInspection inspectAndVerifyE(byte[] asicBytes, KeyStore trustStore) throws Exception {
        if (asicBytes == null || asicBytes.length == 0) throw new IllegalArgumentException("ASiC-E input is required");
        if (asicBytes.length > MAX_ENTRY_BYTES + 4L * 1024L * 1024L) throw new IllegalArgumentException("ASiC-E container exceeds the laboratory limit");
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(asicBytes))) {
            ZipEntry first = zip.getNextEntry();
            boolean mimeTypeValid = first != null && "mimetype".equals(first.getName())
                    && first.getMethod() == ZipEntry.STORED && ASIC_E_MIME_TYPE.equals(readEntry(zip, first));
            if (!mimeTypeValid) throw new IllegalArgumentException("Not a valid ASiC-E container: first STORED mimetype entry is missing");
            Map<String, byte[]> payloads = new LinkedHashMap<>();
            byte[] manifest = null;
            byte[] signature = null;
            int entryCount = 1;
            long expandedBytes = first.getSize() > 0 ? first.getSize() : ASIC_E_MIME_TYPE.length();
            java.util.Set<String> entryNames = new java.util.HashSet<>();
            entryNames.add("mimetype");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                String name = entry.getName();
                validateArchiveName(name);
                if (!entryNames.add(name)) throw new IllegalArgumentException("Duplicate ASiC ZIP entry: " + name);
                byte[] value = readEntryBytes(zip, entry);
                expandedBytes = boundedExpandedSize(expandedBytes, value.length);
                if (MANIFEST_ENTRY.equals(name)) {
                    if (manifest != null) throw new IllegalArgumentException("ASiC-E must contain one ASiCManifest");
                    manifest = value;
                } else if (SIGNATURE_ENTRY.equals(name)) {
                    if (signature != null) throw new IllegalArgumentException("ASiC-E must contain one signature file");
                    signature = value;
                } else if (!name.startsWith("META-INF/")) {
                    payloads.put(name, value);
                }
            }
            if (payloads.isEmpty()) throw new IllegalArgumentException("ASiC-E payload is missing");
            if (manifest == null || signature == null) {
                AdesValidationOperations.SignatureOutcome outcome = inspectDssSignature(asicBytes, "signed.asice");
                return new AsicEInspection(payloads.size(), entryCount, mimeTypeValid, true,
                        true, true, outcome.level(), true,
                        CmsInspectionReport.ValidationState.NOT_EVALUATED,
                        "Use AdesValidationOperations with a truststore and revocation evidence for PKIX validation");
            }
            ManifestCheck manifestCheck = verifyAsicEManifest(manifest, payloads);
            CMSOperations.VerificationResult verification = CMSOperations.verifySignedData(signature, null, manifest);
            CMSOperations.CadesProfile profile = CMSOperations.inspectCadesProfile(signature);
            CmsInspectionReport cmsReport = new CmsInspector().inspect(signature, manifest, trustStore);
            CmsInspectionReport.ValidationStep trustStep = cmsReport.getValidationSteps().stream()
                    .filter(step -> "Trust Chain".equals(step.getStepName()))
                    .findFirst()
                    .orElse(new CmsInspectionReport.ValidationStep("Trust Chain",
                            CmsInspectionReport.ValidationState.NOT_EVALUATED,
                            "No trust decision was produced"));
            return new AsicEInspection(payloads.size(), entryCount, mimeTypeValid, manifestCheck.digestValid(),
                    manifestCheck.signatureReferenceValid(), verification.verified, profile.profile(), profile.certificateBindingValid(),
                    trustStep.getState(), trustStep.getDetails());
        }
    }

    private static AdesValidationOperations.SignatureOutcome inspectDssSignature(byte[] container,
                                                                                  String fileName) throws Exception {
        AdesValidationOperations.Result result = AdesValidationOperations.validate(container, fileName, null, null);
        if (result.signatures().size() != 1) throw new IllegalArgumentException("ASiC must contain one recognizable AdES signature");
        AdesValidationOperations.SignatureOutcome outcome = result.signatures().get(0);
        if (outcome.level() == null || outcome.indication() == Indication.TOTAL_FAILED
                || outcome.errors().stream().anyMatch(error -> error.contains("cryptographic") || error.contains("digest"))) {
            throw new IllegalArgumentException("ASiC signature failed DSS verification: " + outcome.errors());
        }
        return outcome;
    }

    private static byte[] packageAsic(String fileName, byte[] content, byte[] signature) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            byte[] mime = MIME_TYPE.getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            mimeEntry.setSize(mime.length);
            CRC32 crc = new CRC32();
            crc.update(mime);
            mimeEntry.setCrc(crc.getValue());
            zip.putNextEntry(mimeEntry);
            zip.write(mime);
            zip.closeEntry();
            writeDeflated(zip, fileName, content);
            writeDeflated(zip, SIGNATURE_ENTRY, signature);
            zip.finish();
            return output.toByteArray();
        }
    }

    private static byte[] packageAsicE(Map<String, byte[]> payloads, byte[] manifest, byte[] signature) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            writeStoredMimeType(zip, ASIC_E_MIME_TYPE);
            for (Map.Entry<String, byte[]> payload : payloads.entrySet()) {
                writeDeflated(zip, payload.getKey(), payload.getValue());
            }
            writeDeflated(zip, MANIFEST_ENTRY, manifest);
            writeDeflated(zip, SIGNATURE_ENTRY, signature);
            zip.finish();
            return output.toByteArray();
        }
    }

    private static void writeStoredMimeType(ZipOutputStream zip, String mimeType) throws Exception {
        byte[] mime = mimeType.getBytes(StandardCharsets.US_ASCII);
        ZipEntry mimeEntry = new ZipEntry("mimetype");
        mimeEntry.setMethod(ZipEntry.STORED);
        mimeEntry.setSize(mime.length);
        CRC32 crc = new CRC32();
        crc.update(mime);
        mimeEntry.setCrc(crc.getValue());
        zip.putNextEntry(mimeEntry);
        zip.write(mime);
        zip.closeEntry();
    }

    private static Map<String, byte[]> normalizePayloads(Map<String, byte[]> payloads) {
        if (payloads == null || payloads.isEmpty()) throw new IllegalArgumentException("ASiC-E requires at least one payload");
        Map<String, byte[]> normalized = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, byte[]> entry : payloads.entrySet()) {
            String name = payloadNameE(entry.getKey());
            byte[] content = entry.getValue();
            if (content == null || content.length == 0) throw new IllegalArgumentException("ASiC-E payload '" + name + "' is empty");
            if (content.length > MAX_ENTRY_BYTES) throw new IllegalArgumentException("ASiC-E payload '" + name + "' exceeds the 64 MiB limit");
            if (normalized.put(name, content.clone()) != null) throw new IllegalArgumentException("Duplicate ASiC-E payload name: " + name);
            total += content.length;
            if (total > MAX_ENTRY_BYTES) throw new IllegalArgumentException("ASiC-E combined payloads exceed the 64 MiB limit");
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }

    private static byte[] buildAsicEManifest(Map<String, byte[]> payloads) throws Exception {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<ASiCManifest xmlns=\"").append(ASIC_MANIFEST_NS).append("\" xmlns:ds=\"")
                .append(XMLDSIG_NS).append("\">")
                .append("<SigReference URI=\"").append(SIGNATURE_ENTRY).append("\"/>");
        for (Map.Entry<String, byte[]> payload : payloads.entrySet()) {
            xml.append("<DataObjectReference URI=\"").append(xmlEscape(payload.getKey())).append("\">")
                    .append("<ds:DigestMethod Algorithm=\"").append(SHA256_URI).append("\"/>")
                    .append("<ds:DigestValue>").append(java.util.Base64.getEncoder().encodeToString(sha256(payload.getValue())))
                    .append("</ds:DigestValue></DataObjectReference>");
        }
        return xml.append("</ASiCManifest>").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static ManifestCheck verifyAsicEManifest(byte[] manifest, Map<String, byte[]> payloads) throws Exception {
        if (manifest == null || manifest.length == 0) throw new IllegalArgumentException("ASiC-E manifest is required");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(manifest)).getDocumentElement();
        if (!"ASiCManifest".equals(root.getLocalName()) || !ASIC_MANIFEST_NS.equals(root.getNamespaceURI())) {
            throw new IllegalArgumentException("ASiC-E manifest namespace or root element is invalid");
        }
        NodeList signatureRefs = root.getElementsByTagNameNS(ASIC_MANIFEST_NS, "SigReference");
        boolean signatureReferenceValid = signatureRefs.getLength() == 1
                && SIGNATURE_ENTRY.equals(((Element) signatureRefs.item(0)).getAttribute("URI"));
        NodeList references = root.getElementsByTagNameNS(ASIC_MANIFEST_NS, "DataObjectReference");
        if (references.getLength() != payloads.size()) return new ManifestCheck(false, signatureReferenceValid);
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean digestsValid = true;
        for (int i = 0; i < references.getLength(); i++) {
            Element reference = (Element) references.item(i);
            String name = payloadNameE(reference.getAttribute("URI"));
            byte[] content = payloads.get(name);
            Element digestMethod = (Element) reference.getElementsByTagNameNS(XMLDSIG_NS, "DigestMethod").item(0);
            Element digestValue = (Element) reference.getElementsByTagNameNS(XMLDSIG_NS, "DigestValue").item(0);
            if (content == null || digestMethod == null || digestValue == null || !SHA256_URI.equals(digestMethod.getAttribute("Algorithm"))) {
                digestsValid = false;
                continue;
            }
            byte[] expected;
            try {
                expected = java.util.Base64.getDecoder().decode(digestValue.getTextContent().replaceAll("\\s+", ""));
            } catch (IllegalArgumentException invalid) {
                digestsValid = false;
                continue;
            }
            if (!MessageDigest.isEqual(expected, sha256(content)) || !seen.add(name)) digestsValid = false;
        }
        return new ManifestCheck(digestsValid && seen.size() == payloads.size(), signatureReferenceValid);
    }

    private static byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    private static long boundedExpandedSize(long current, long entryBytes) {
        if (entryBytes < 0 || current > MAX_EXPANDED_ARCHIVE_BYTES - entryBytes) {
            throw new IllegalArgumentException("ASiC expanded archive exceeds the 68 MiB laboratory limit");
        }
        return current + entryBytes;
    }

    private static String payloadNameE(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("ASiC-E payload name is required");
        String value = fileName.trim();
        validateArchiveName(value);
        if ("mimetype".equals(value) || value.startsWith("META-INF/")) {
            throw new IllegalArgumentException("ASiC-E payload name cannot use mimetype or META-INF");
        }
        return value;
    }

    private static void validateArchiveName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.contains("..") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unsafe ASiC ZIP entry name");
        }
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    private static void writeDeflated(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String readEntry(ZipInputStream zip, ZipEntry entry) throws Exception {
        return new String(readEntryBytes(zip, entry), StandardCharsets.US_ASCII);
    }

    private static byte[] readEntryBytes(ZipInputStream zip, ZipEntry entry) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = zip.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_ENTRY_BYTES) throw new IllegalArgumentException("ASiC ZIP entry exceeds the 64 MiB limit");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String payloadName(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("ASiC payload file name is required");
        String value = fileName.trim();
        if (value.contains("/") || value.contains("\\") || value.equals("mimetype") || value.startsWith("META-INF/")) {
            throw new IllegalArgumentException("ASiC payload name must be a single non-META-INF file name");
        }
        return value;
    }

    private static KeyStore loadPkcs12(File file, char[] password) throws Exception {
        try (var input = new java.io.FileInputStream(file)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, password);
            return keyStore;
        }
    }

    private static String firstPrivateKeyAlias(KeyStore keyStore) throws Exception {
        var aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) return alias;
        }
        throw new IllegalArgumentException("PKCS#12 does not contain a private signing key");
    }

    public record AsicInspection(String payloadName, int entryCount, boolean mimeTypeValid,
                                 boolean signatureValid, String cadesProfile,
                                 boolean certificateBindingValid,
                                 CmsInspectionReport.ValidationState trustState,
                                 String trustDetails) { }

    /** ASiC-E result: local structure, manifest integrity, CAdES integrity and optional offline trust. */
    public record AsicEInspection(int payloadCount, int entryCount, boolean mimeTypeValid,
                                  boolean manifestDigestsValid, boolean signatureReferenceValid,
                                  boolean signatureValid, String cadesProfile,
                                  boolean certificateBindingValid,
                                  CmsInspectionReport.ValidationState trustState,
                                  String trustDetails) { }

    /** Immutable prepared input for a detached CAdES signature over an ASiC-E manifest. */
    public record AsicEManifest(Map<String, byte[]> payloads, byte[] manifest) {
        public AsicEManifest {
            Map<String, byte[]> copies = new LinkedHashMap<>();
            payloads.forEach((name, content) -> copies.put(name, content.clone()));
            payloads = java.util.Collections.unmodifiableMap(copies);
            manifest = manifest.clone();
        }

        @Override public Map<String, byte[]> payloads() {
            Map<String, byte[]> copies = new LinkedHashMap<>();
            payloads.forEach((name, content) -> copies.put(name, content.clone()));
            return java.util.Collections.unmodifiableMap(copies);
        }

        @Override public byte[] manifest() { return manifest.clone(); }
    }

    private record ManifestCheck(boolean digestValid, boolean signatureReferenceValid) { }
}
