package com.cryptocarver.crypto;

import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

/** RFC 3161 connectivity and response diagnostics, without signing user content. */
public final class TsaDiagnostics {
    private TsaDiagnostics() { }
    public record Report(String url, int httpStatus, long latencyMs, String policyOid, String imprintAlgorithmOid,
                         String generationTime, int responseBytes) { }
    public record TokenResult(Report report, byte[] token, String dataSha256) { }
    public record TokenInspection(String policyOid, String imprintAlgorithmOid, String imprintHex,
                                  String generationTime, String serialNumber, String signerId,
                                  String signerSubject, String signerIssuer, String signerSha256, int responseBytes,
                                  String signatureAlgorithm, String certNotBefore, String certNotAfter,
                                  boolean hasTimeStampingEku, String certificateChainInfo) { }

    public static Report test(String url) throws Exception {
        return timestamp(url, "CryptoCarver TSA diagnostic".getBytes(StandardCharsets.UTF_8)).report();
    }

    /** Requests an RFC 3161 token for the SHA-256 imprint of arbitrary data. */
    public static TokenResult timestamp(String url, byte[] data) throws Exception {
        return timestamp(url, data, "SHA-256");
    }

    /** Requests an RFC 3161 token using SHA-256, SHA-384 or SHA-512. */
    public static TokenResult timestamp(String url, byte[] data, String digestAlgorithm) throws Exception {
        return timestamp(url, data, digestAlgorithm, 15_000, 20_000, 1024 * 1024, null); // 1MB default limit
    }

    public static TokenResult timestamp(String url, byte[] data, String digestAlgorithm,
                                        int connectTimeoutMs, int readTimeoutMs, int maxResponseBytes,
                                        com.cryptocarver.model.TsaAuthCredentials auth) throws Exception {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("TSA URL must start with http:// or https://");
        }
        if (data == null || data.length == 0) throw new IllegalArgumentException("Data to timestamp is required");
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        String algorithm = normalizeDigestAlgorithm(digestAlgorithm);
        byte[] digest = MessageDigest.getInstance(algorithm).digest(data);
        TimeStampRequest request = generator.generate(digestOid(algorithm), digest);
        long start = System.nanoTime();
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            if (auth != null) {
                String header = auth.getAuthorizationHeader();
                if (header != null) connection.setRequestProperty("Authorization", header);
            }
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/timestamp-query");
            connection.setRequestProperty("Accept", "application/timestamp-reply");
            try (var output = connection.getOutputStream()) { output.write(request.getEncoded()); }
            int status = connection.getResponseCode();
            byte[] payload;
            try (InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream()) {
                if (stream != null) {
                    payload = stream.readNBytes(maxResponseBytes);
                    if (stream.read() != -1) {
                        throw new IllegalArgumentException("TSA response exceeded maximum size limit of " + maxResponseBytes + " bytes");
                    }
                } else {
                    payload = new byte[0];
                }
            }
            long latency = (System.nanoTime() - start) / 1_000_000;
            if (status < 200 || status >= 300) throw new IllegalArgumentException("TSA returned HTTP " + status); // Do not include payload text in exception

            TimeStampResponse response = new TimeStampResponse(payload);
            response.validate(request);
            TimeStampToken token = response.getTimeStampToken();
            if (token == null) throw new IllegalArgumentException("TSA response has no timestamp token: " + response.getStatusString());
            var info = token.getTimeStampInfo();
            Report report = new Report(url, status, latency, info.getPolicy().getId(), info.getMessageImprintAlgOID().getId(),
                    info.getGenTime().toInstant().toString(), payload.length);
            return new TokenResult(report, payload, hex(digest));
        } finally {
            connection.disconnect();
        }
    }

    private static String normalizeDigestAlgorithm(String algorithm) {
        if (algorithm == null) return "SHA-256";
        return switch (algorithm.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SHA-256", "SHA256" -> "SHA-256";
            case "SHA-384", "SHA384" -> "SHA-384";
            case "SHA-512", "SHA512" -> "SHA-512";
            default -> throw new IllegalArgumentException("Supported timestamp hashes: SHA-256, SHA-384, SHA-512");
        };
    }

    private static org.bouncycastle.asn1.ASN1ObjectIdentifier digestOid(String algorithm) {
        return switch (algorithm) {
            case "SHA-384" -> org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha384;
            case "SHA-512" -> org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha512;
            default -> org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256;
        };
    }

    /** Parses a saved RFC 3161 response locally; it does not establish TSA certificate trust. */
    public static TokenInspection inspectToken(byte[] responseBytes) throws Exception {
        TimeStampToken token = parseToken(responseBytes);
        var info = token.getTimeStampInfo();
        String subject = "(certificate not embedded)";
        String issuer = "(certificate not embedded)";
        String certificateHash = "(not available)";
        String signatureAlgorithm = "(not available)";
        String certNotBefore = "(not available)";
        String certNotAfter = "(not available)";
        boolean hasTimeStampingEku = false;
        StringBuilder chainInfo = new StringBuilder();

        var matches = token.getCertificates().getMatches(token.getSID());
        if (!matches.isEmpty()) {
            org.bouncycastle.cert.X509CertificateHolder holder = (org.bouncycastle.cert.X509CertificateHolder) matches.iterator().next();
            X509Certificate certificate = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                    .setProvider("BC").getCertificate(holder);
            subject = certificate.getSubjectX500Principal().getName();
            issuer = certificate.getIssuerX500Principal().getName();
            certificateHash = hex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));

            try {
                org.bouncycastle.cms.SignerInformation signerInfo = token.toCMSSignedData().getSignerInfos().getSigners().iterator().next();
                signatureAlgorithm = signerInfo.getEncryptionAlgOID();
            } catch(Exception ignored) { }
            certNotBefore = certificate.getNotBefore().toInstant().toString();
            certNotAfter = certificate.getNotAfter().toInstant().toString();
            try {
                java.util.List<String> eku = certificate.getExtendedKeyUsage();
                hasTimeStampingEku = eku != null && eku.contains("1.3.6.1.5.5.7.3.8");
            } catch(Exception ignored) {}
        }

        int i = 1;
        for (Object obj : token.getCertificates().getMatches(null)) {
            org.bouncycastle.cert.X509CertificateHolder h = (org.bouncycastle.cert.X509CertificateHolder) obj;
            chainInfo.append(String.format("Cert %d: %s (SHA-256: %s)\n", i++, h.getSubject().toString(),
                hex(MessageDigest.getInstance("SHA-256").digest(h.getEncoded()))));
        }

        return new TokenInspection(info.getPolicy().getId(), info.getMessageImprintAlgOID().getId(), hex(info.getMessageImprintDigest()),
                info.getGenTime().toInstant().toString(), info.getSerialNumber().toString(16).toUpperCase(java.util.Locale.ROOT),
                String.valueOf(token.getSID()), subject, issuer, certificateHash, responseBytes.length,
                signatureAlgorithm, certNotBefore, certNotAfter, hasTimeStampingEku, chainInfo.toString());
    }

    /** Checks that a token's message imprint matches supplied data; it makes no certificate trust decision. */
    public static boolean tokenMatchesData(byte[] responseBytes, byte[] data) throws Exception {
        TimeStampToken token = parseToken(responseBytes);
        var info = token.getTimeStampInfo();
        String algorithm = switch (info.getMessageImprintAlgOID().getId()) {
            case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
            case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
            case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
            default -> throw new IllegalArgumentException("Unsupported token imprint algorithm: " + info.getMessageImprintAlgOID().getId());
        };
        return MessageDigest.isEqual(info.getMessageImprintDigest(), MessageDigest.getInstance(algorithm).digest(data));
    }

    public static String validateToken(byte[] responseBytes, byte[] data, String trustStorePath, String trustStorePassword) throws Exception {
        StringBuilder report = new StringBuilder("--- TSA Token Validation Report ---\n");
        TimeStampToken token;
        try {
            token = parseToken(responseBytes);
            report.append("1. Token Parse: SUCCESS\n");
        } catch (Exception e) {
            report.append("1. Token Parse: FAILED - ").append(e.getMessage()).append("\n");
            return report.toString();
        }

        if (data == null) {
            report.append("2. Imprint Match: NOT EVALUATED\n");
        } else {
            try {
                boolean match = tokenMatchesData(responseBytes, data);
                report.append("2. Imprint Match: ").append(match ? "SUCCESS" : "FAILED").append("\n");
            } catch (Exception e) {
                report.append("2. Imprint Match: FAILED - ").append(e.getMessage()).append("\n");
            }
        }

        X509Certificate signerCert = null;
        boolean cmsAndEkuValid = false;
        try {
            var matches = token.getCertificates().getMatches(token.getSID());
            if (matches.isEmpty()) throw new IllegalArgumentException("No signer certificate found in token");
            org.bouncycastle.cert.X509CertificateHolder holder = (org.bouncycastle.cert.X509CertificateHolder) matches.iterator().next();
            signerCert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
            org.bouncycastle.cms.SignerInformationVerifier verifier = new org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerCert);
            token.validate(verifier);
            java.util.List<String> ekus = signerCert.getExtendedKeyUsage();
            if (ekus == null || !ekus.contains(org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_timeStamping.getId())) {
                throw new IllegalArgumentException("Signer certificate is missing the required timeStamping Extended Key Usage (EKU)");
            }
            cmsAndEkuValid = true;
            report.append("3. CMS Signature & EKU: SUCCESS\n");
        } catch (Exception e) {
            report.append("3. CMS Signature & EKU: FAILED - ").append(e.getMessage()).append("\n");
        }

        if (!cmsAndEkuValid) {
            report.append("4. Trust Chain: NOT TRUSTED (CMS signature or timeStamping EKU failed)\n");
            report.append("5. Revocation: NOT EVALUATED\n");
            return report.toString();
        }

        if (trustStorePath == null || trustStorePath.isBlank()) {
            report.append("4. Trust Chain: NOT EVALUATED (No truststore provided)\n");
            report.append("5. Revocation: NOT EVALUATED\n");
            return report.toString();
        }

        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance(new java.io.File(trustStorePath), trustStorePassword != null ? trustStorePassword.toCharArray() : new char[0]);

            java.util.List<java.security.cert.Certificate> chain = new java.util.ArrayList<>();
            for (Object obj : token.getCertificates().getMatches(null)) {
                org.bouncycastle.cert.X509CertificateHolder h = (org.bouncycastle.cert.X509CertificateHolder) obj;
                chain.add(new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC").getCertificate(h));
            }

            java.security.cert.X509CertSelector selector = new java.security.cert.X509CertSelector();
            selector.setCertificate(signerCert);
            java.security.cert.PKIXBuilderParameters params = new java.security.cert.PKIXBuilderParameters(ks, selector);
            params.setRevocationEnabled(false);

            java.security.cert.CertStore certStore = java.security.cert.CertStore.getInstance("Collection", new java.security.cert.CollectionCertStoreParameters(chain));
            params.addCertStore(certStore);

            java.security.cert.CertPathBuilder cpb = java.security.cert.CertPathBuilder.getInstance("PKIX");
            cpb.build(params);

            report.append("4. Trust Chain: SUCCESS\n");
            report.append("5. Revocation: NOT EVALUATED (Skipped in basic validation)\n");
        } catch (Exception e) {
            report.append("4. Trust Chain: FAILED - ").append(e.getMessage()).append("\n");
            report.append("5. Revocation: NOT EVALUATED\n");
        }
        return report.toString();
    }

    private static TimeStampToken parseToken(byte[] responseBytes) throws Exception {
        if (responseBytes == null || responseBytes.length == 0) throw new IllegalArgumentException("Timestamp response is required");
        TimeStampResponse response = new TimeStampResponse(responseBytes);
        TimeStampToken token = response.getTimeStampToken();
        if (token == null) throw new IllegalArgumentException("Response has no timestamp token: " + response.getStatusString());
        return token;
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format("%02X", b));
        return out.toString();
    }
}
