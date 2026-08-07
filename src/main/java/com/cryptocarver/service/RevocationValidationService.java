package com.cryptocarver.service;

import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.client.http.DataLoader;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.x509.revocation.RevocationSource;
import eu.europa.esig.dss.spi.x509.revocation.RevocationToken;
import eu.europa.esig.dss.model.x509.revocation.crl.CRL;
import eu.europa.esig.dss.model.x509.revocation.ocsp.OCSP;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.ExternalResourcesOCSPSource;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/** Shared, opt-in revocation source configuration for DSS validators. */
public final class RevocationValidationService {
    public static final int CONNECTION_TIMEOUT_MS = 5_000;
    public static final int READ_TIMEOUT_MS = 10_000;
    public static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    public enum Status { DISABLED, GOOD, REVOKED, UNKNOWN, INDETERMINATE }
    public enum Evidence { NONE, LOCAL, OCSP, ONLINE_CRL }

    public record Configuration(boolean onlineEnabled, List<DSSDocument> localCrls,
                                List<DSSDocument> localOcsps, Consumer<String> endpointObserver) {
        public Configuration {
            localCrls = localCrls == null ? List.of() : List.copyOf(localCrls);
            localOcsps = localOcsps == null ? List.of() : List.copyOf(localOcsps);
            endpointObserver = endpointObserver == null ? ignored -> { } : endpointObserver;
        }
        public Configuration(boolean onlineEnabled, List<DSSDocument> localCrls, Consumer<String> endpointObserver) {
            this(onlineEnabled, localCrls, List.of(), endpointObserver);
        }
        public Configuration(boolean onlineEnabled, List<DSSDocument> localCrls, List<DSSDocument> localOcsps) {
            this(onlineEnabled, localCrls, localOcsps, ignored -> { });
        }
        public Configuration(boolean onlineEnabled, List<DSSDocument> localCrls) { this(onlineEnabled, localCrls, ignored -> { }); }
        public static Configuration offline(List<DSSDocument> localCrls) { return new Configuration(false, localCrls); }
    }

    public record Result(Status status, Evidence evidence, List<String> endpoints, List<String> errors) {
        public Result {
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
            errors = errors == null ? List.of() : errors.stream().map(Result::safeError).toList();
        }
        public static Result disabled(boolean local) {
            return new Result(Status.DISABLED, local ? Evidence.LOCAL : Evidence.NONE, List.of(), List.of());
        }
    public static Result indeterminate(String error) {
            return new Result(Status.INDETERMINATE, Evidence.NONE, List.of(), List.of(safeError(error)));
        }
        private static String safeError(String value) {
            if (value == null || value.isBlank()) return "Revocation source failed";
            String normalized = value.replaceAll("(?i)(password|token|secret|authorization)\\s*[=:]\\s*[^\\s,;]+", "$1=[redacted]")
                    .replaceAll("[\\r\\n]+", " ");
            return normalized.substring(0, Math.min(240, normalized.length()));
        }
    }

    /**
     * Classifies only explicit revocation evidence from a DSS detailed report.
     * Signature/chain VALID indications are deliberately not accepted here.
     */
    public static Result classifyDssReport(boolean online, boolean localEvidence,
                                           String detailedReport, List<String> endpoints,
                                           List<String> errors) {
        if (!online && !localEvidence) return Result.disabled(false);
        String report = detailedReport == null ? "" : detailedReport.toUpperCase(java.util.Locale.ROOT);
        Evidence source = evidenceFor(online, localEvidence, report);
        if (report.contains("BBB_XCV_ISCR_ANS") || report.contains("REVOKED_CERTIFICATE")) {
            return new Result(Status.REVOKED, source, endpoints, errors);
        }
        if (report.matches("(?s).*<(?:[^:>]+:)?(?:REVOCATIONSTATUS|CERTIFICATESTATUS)[^>]*>\\s*(REVOKED|REVOKED_CERTIFICATE)\\s*<.*")) {
            return new Result(Status.REVOKED, source, endpoints, errors);
        }
        // DSS versions differ in XML casing/namespaces. Keep this deliberately
        // narrow: only a revocation-labelled status can establish GOOD.
        if (report.matches("(?s).*<(?:[^:>]+:)?(?:REVOCATIONSTATUS|CERTIFICATESTATUS)[^>]*>\\s*(GOOD|VALID|NOT_REVOKED)\\s*<.*")) {
            return new Result(Status.GOOD, source, endpoints, errors);
        }
        // DSS 6.3 may expose a locally checked CRL only through the passed
        // validation result, without serialising a REVOCATIONSTATUS element.
        // A TOTAL_PASSED result is accepted here only when local/online
        // revocation evidence was actually configured, and revoked markers
        // were checked first above.
        if ((localEvidence || online) && report.contains("TOTAL_PASSED")) {
            return new Result(Status.GOOD, source, endpoints, errors);
        }
        List<String> allErrors = errors == null ? List.of() : errors;
        if (online && allErrors.isEmpty()) allErrors = List.of("No sufficient positive revocation evidence");
        return new Result(Status.UNKNOWN, source, endpoints, allErrors);
    }

    /** Applies the same local-CRL policy when DSS only reports REVOKED_NO_POE. */
    public static Result classifyLocalCrls(List<java.security.cert.X509Certificate> certificates,
                                           List<java.io.File> crlFiles, List<String> endpoints) {
        if (certificates == null || certificates.isEmpty() || crlFiles == null || crlFiles.isEmpty()) return null;
        try {
            java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
            for (java.io.File file : crlFiles) {
                if (file == null || !file.isFile()) continue;
                java.security.cert.X509CRL crl;
                try (java.io.InputStream input = new java.io.FileInputStream(file)) {
                    crl = (java.security.cert.X509CRL) factory.generateCRL(input);
                }
                for (java.security.cert.X509Certificate certificate : certificates) {
                    if (crl.getRevokedCertificate(certificate) != null) {
                        return new Result(Status.REVOKED, Evidence.LOCAL, endpoints, List.of());
                    }
                }
            }
        } catch (Exception ignored) {
            // DSS remains authoritative when a local file cannot be parsed.
        }
        return null;
    }

    private static Evidence evidenceFor(boolean online, boolean localEvidence, String report) {
        if (!online) return localEvidence ? Evidence.LOCAL : Evidence.NONE;
        if (report.contains("OCSP")) return Evidence.OCSP;
        if (report.contains("CRL")) return Evidence.ONLINE_CRL;
        return Evidence.OCSP;
    }

    private RevocationValidationService() { }

    /** Configures DSS. Network sources are never installed unless explicitly enabled. */
    public static void configure(CommonCertificateVerifier verifier, Configuration configuration) {
        if (configuration == null) configuration = Configuration.offline(List.of());
        if (!configuration.localCrls().isEmpty()) {
            RevocationSource<CRL> local = new eu.europa.esig.dss.spi.x509.revocation.crl.ExternalResourcesCRLSource(
                    configuration.localCrls().toArray(DSSDocument[]::new));
            if (configuration.onlineEnabled()) {
                verifier.setCrlSource(new LocalThenOnlineCrlSource(local, null));
            } else {
                verifier.setCrlSource(local);
            }
            verifier.setCheckRevocationForUntrustedChains(true);
        }
        if (!configuration.localOcsps().isEmpty()) {
            RevocationSource<OCSP> local = new ExternalResourcesOCSPSource(
                    configuration.localOcsps().toArray(DSSDocument[]::new));
            if (configuration.onlineEnabled()) {
                verifier.setOcspSource(new LocalThenOnlineOcspSource(local, null));
            } else {
                verifier.setOcspSource(local);
            }
            verifier.setCheckRevocationForUntrustedChains(true);
        }
        if (configuration.onlineEnabled()) {
            DataLoader loader = safeLoader(configuration.endpointObserver());
            RevocationSource<OCSP> onlineOcsp = new OnlineOCSPSource(loader);
            RevocationSource<OCSP> currentOcsp = verifier.getOcspSource();
            verifier.setOcspSource(currentOcsp instanceof LocalThenOnlineOcspSource
                    ? new LocalThenOnlineOcspSource(((LocalThenOnlineOcspSource) currentOcsp).local, onlineOcsp)
                    : onlineOcsp);
            RevocationSource<CRL> online = new OnlineCRLSource(loader);
            RevocationSource<CRL> current = verifier.getCrlSource();
            verifier.setCrlSource(current instanceof LocalThenOnlineCrlSource
                    ? new LocalThenOnlineCrlSource(((LocalThenOnlineCrlSource) current).local, online)
                    : online);
            verifier.setCheckRevocationForUntrustedChains(true);
        }
    }

    private static final class LocalThenOnlineOcspSource implements RevocationSource<OCSP> {
        private final RevocationSource<OCSP> local;
        private final RevocationSource<OCSP> online;
        private LocalThenOnlineOcspSource(RevocationSource<OCSP> local, RevocationSource<OCSP> online) {
            this.local = local;
            this.online = online;
        }
        @Override public RevocationToken<OCSP> getRevocationToken(CertificateToken certificate, CertificateToken issuer) {
            RevocationToken<OCSP> token = local == null ? null : local.getRevocationToken(certificate, issuer);
            return token != null ? token : (online == null ? null : online.getRevocationToken(certificate, issuer));
        }
    }

    /** DSS exposes one CRLSource slot; preserve explicit local evidence first. */
    private static final class LocalThenOnlineCrlSource implements RevocationSource<CRL> {
        private final RevocationSource<CRL> local;
        private final RevocationSource<CRL> online;
        private LocalThenOnlineCrlSource(RevocationSource<CRL> local, RevocationSource<CRL> online) {
            this.local = local;
            this.online = online;
        }
        @Override public RevocationToken<CRL> getRevocationToken(CertificateToken certificate, CertificateToken issuer) {
            RevocationToken<CRL> token = local == null ? null : local.getRevocationToken(certificate, issuer);
            return token != null ? token : (online == null ? null : online.getRevocationToken(certificate, issuer));
        }
    }

    public static void validateWebUri(String endpoint) {
        URI uri;
        try { uri = URI.create(endpoint); } catch (Exception e) { throw new IllegalArgumentException("Invalid revocation URL"); }
        String scheme = uri.getScheme();
        if ((scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                || uri.getHost() == null) throw new IllegalArgumentException("Only HTTP/HTTPS revocation URLs are allowed");
    }

    static DataLoader safeLoader(Consumer<String> endpointObserver) {
        CommonsDataLoader delegate = new CommonsDataLoader();
        delegate.setTimeoutConnection(CONNECTION_TIMEOUT_MS);
        delegate.setTimeoutConnectionRequest(CONNECTION_TIMEOUT_MS);
        delegate.setTimeoutResponse(READ_TIMEOUT_MS);
        delegate.setTimeoutSocket(READ_TIMEOUT_MS);
        delegate.setRedirectsEnabled(false);
        delegate.setUseSystemProperties(false);
        return new DataLoader() {
            @Override public byte[] get(String url) { validateWebUri(url); endpointObserver.accept(url); return bounded(delegate.get(url)); }
            @Override public DataAndUrl get(List<String> urls) {
                if (urls == null || urls.isEmpty()) throw new IllegalArgumentException("No revocation URL");
                urls.forEach(RevocationValidationService::validateWebUri);
                urls.forEach(endpointObserver);
                DataAndUrl result = delegate.get(urls);
                return new DataAndUrl(result.getUrlString(), bounded(result.getData()));
            }
            @Override public byte[] post(String url, byte[] data) { validateWebUri(url); endpointObserver.accept(url); return bounded(delegate.post(url, data)); }
            @Override public void setContentType(String contentType) { delegate.setContentType(contentType); }
            private byte[] bounded(byte[] bytes) {
                if (bytes == null || bytes.length > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Revocation response exceeds size limit");
                return bytes;
            }
        };
    }
}
