package com.cryptoforge.crypto;

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
                                  String signerSubject, String signerIssuer, String signerSha256, int responseBytes) { }

    public static Report test(String url) throws Exception {
        return timestamp(url, "CryptoCarver TSA diagnostic".getBytes(StandardCharsets.UTF_8)).report();
    }

    /** Requests an RFC 3161 token for the SHA-256 imprint of arbitrary data. */
    public static TokenResult timestamp(String url, byte[] data) throws Exception {
        return timestamp(url, data, "SHA-256");
    }

    /** Requests an RFC 3161 token using SHA-256, SHA-384 or SHA-512. */
    public static TokenResult timestamp(String url, byte[] data, String digestAlgorithm) throws Exception {
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
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/timestamp-query");
        connection.setRequestProperty("Accept", "application/timestamp-reply");
        try (var output = connection.getOutputStream()) { output.write(request.getEncoded()); }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        byte[] payload = stream == null ? new byte[0] : stream.readAllBytes();
        long latency = (System.nanoTime() - start) / 1_000_000;
        if (status < 200 || status >= 300) throw new IllegalArgumentException("TSA returned HTTP " + status + ": " + new String(payload, StandardCharsets.UTF_8));
        TimeStampResponse response = new TimeStampResponse(payload);
        response.validate(request);
        TimeStampToken token = response.getTimeStampToken();
        if (token == null) throw new IllegalArgumentException("TSA response has no timestamp token: " + response.getStatusString());
        var info = token.getTimeStampInfo();
        Report report = new Report(url, status, latency, info.getPolicy().getId(), info.getMessageImprintAlgOID().getId(),
                info.getGenTime().toInstant().toString(), payload.length);
        return new TokenResult(report, payload, hex(digest));
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
        var matches = token.getCertificates().getMatches(token.getSID());
        if (!matches.isEmpty()) {
            org.bouncycastle.cert.X509CertificateHolder holder = (org.bouncycastle.cert.X509CertificateHolder) matches.iterator().next();
            X509Certificate certificate = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                    .setProvider("BC").getCertificate(holder);
            subject = certificate.getSubjectX500Principal().getName();
            issuer = certificate.getIssuerX500Principal().getName();
            certificateHash = hex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        }
        return new TokenInspection(info.getPolicy().getId(), info.getMessageImprintAlgOID().getId(), hex(info.getMessageImprintDigest()),
                info.getGenTime().toInstant().toString(), info.getSerialNumber().toString(16).toUpperCase(java.util.Locale.ROOT),
                String.valueOf(token.getSID()), subject, issuer, certificateHash, responseBytes.length);
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
