package com.cryptocarver.model;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecRegistry;
import com.cryptocarver.codec.CodecException;

import java.security.MessageDigest;

/** Stateless transformations safe to expose through batch and local automation interfaces. */
public final class SafeTransformations {
    private SafeTransformations() { }

    public static String sha256(String value) throws Exception {
        byte[] input = CodecRegistry.getInstance().decode(value, ByteFormat.TEXT_UTF8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
        return CodecRegistry.getInstance().encode(digest, ByteFormat.HEX).toLowerCase();
    }

    public static String encodeBase64Url(String value) {
        byte[] input = CodecRegistry.getInstance().decode(value, ByteFormat.TEXT_UTF8);
        return CodecRegistry.getInstance().encode(input, ByteFormat.BASE64_URL);
    }

    public static String decodeBase64Url(String value) {
        try {
            byte[] decoded = CodecRegistry.getInstance().decode(value, ByteFormat.BASE64_URL);
            return CodecRegistry.getInstance().encode(decoded, ByteFormat.TEXT_UTF8);
        } catch (CodecException e) {
            throw new IllegalArgumentException("Invalid Base64URL string", e);
        }
    }

    public static String hmacSha256(String value, String keyBase64) throws Exception {
        if (keyBase64 == null || keyBase64.isBlank()) throw new IllegalArgumentException("HMAC key is required");
        byte[] input = CodecRegistry.getInstance().decode(value, ByteFormat.TEXT_UTF8);
        byte[] key = java.util.Base64.getDecoder().decode(keyBase64);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        return CodecRegistry.getInstance().encode(mac.doFinal(input), ByteFormat.HEX).toLowerCase();
    }

    public static String compressGzip(String value) throws Exception {
        byte[] input = CodecRegistry.getInstance().decode(value, ByteFormat.TEXT_UTF8);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(bos)) {
            gzip.write(input);
        }
        return CodecRegistry.getInstance().encode(bos.toByteArray(), ByteFormat.BASE64);
    }

    public static String decompressGzip(String value) throws Exception {
        byte[] input = CodecRegistry.getInstance().decode(value, ByteFormat.BASE64);
        try (java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(input))) {
            return CodecRegistry.getInstance().encode(gis.readAllBytes(), ByteFormat.TEXT_UTF8);
        }
    }

    public static String inspectAsn1(String hexOrBase64) throws Exception {
        byte[] input;
        try {
            input = CodecRegistry.getInstance().decode(hexOrBase64, ByteFormat.HEX);
        } catch (Exception e) {
            input = CodecRegistry.getInstance().decode(hexOrBase64, ByteFormat.BASE64);
        }
        com.cryptocarver.asn1.ASN1TreeNode root = com.cryptocarver.asn1.ASN1Parser.parse(input);
        return com.cryptocarver.asn1.ASN1TreeExporter.toJson(root);
    }

    public static String inspectTlv(String hex) {
        java.util.List<com.cryptocarver.crypto.EmvTlv.Item> items = com.cryptocarver.crypto.EmvTlv.parse(hex);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(items);
    }
}
