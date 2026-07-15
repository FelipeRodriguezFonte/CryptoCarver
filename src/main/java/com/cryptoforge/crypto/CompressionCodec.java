package com.cryptoforge.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/** Compression helpers for diagnostic workflows; output is capped on decompression. */
public final class CompressionCodec {
    private static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;
    private CompressionCodec() { }

    public static byte[] compress(byte[] input, String format) throws IOException {
        if (input == null) throw new IllegalArgumentException("Input cannot be null");
        return switch (format) {
            case "gzip" -> gzip(input);
            case "zlib" -> deflate(input, false);
            case "deflate" -> deflate(input, true);
            default -> throw new IllegalArgumentException("Unsupported compression format: " + format);
        };
    }

    public static byte[] decompress(byte[] input, String format) throws IOException {
        if (input == null) throw new IllegalArgumentException("Input cannot be null");
        return switch (format) {
            case "gzip" -> readCapped(new GZIPInputStream(new ByteArrayInputStream(input)));
            case "zlib" -> readCapped(new InflaterInputStream(new ByteArrayInputStream(input)));
            case "deflate" -> readCapped(new InflaterInputStream(new ByteArrayInputStream(input), new java.util.zip.Inflater(true)));
            default -> throw new IllegalArgumentException("Unsupported compression format: " + format);
        };
    }

    private static byte[] gzip(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) { gzip.write(input); }
        return output.toByteArray();
    }

    private static byte[] deflate(byte[] input, boolean raw) throws IOException {
        try (DeflaterInputStream stream = new DeflaterInputStream(new ByteArrayInputStream(input), new Deflater(Deflater.DEFAULT_COMPRESSION, raw))) {
            return stream.readAllBytes();
        }
    }

    private static byte[] readCapped(java.io.InputStream input) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) {
                if (output.size() + read > MAX_DECOMPRESSED_BYTES) throw new IOException("Decompressed data exceeds 64 MiB safety limit");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
