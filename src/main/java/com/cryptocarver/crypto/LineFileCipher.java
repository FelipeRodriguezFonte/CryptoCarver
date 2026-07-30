package com.cryptocarver.crypto;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import com.cryptocarver.util.ProgressMonitor;

/**
 * Encrypts UTF-8 text files as independently authenticated records, one per
 * input line. Each encrypted record carries its own fresh nonce and tag, so a
 * line can be handled independently without reusing an AEAD nonce.
 */
public final class LineFileCipher {

    private LineFileCipher() { }

    public record Result(long lines, long inputBytes, long outputBytes) { }

    public enum Encoding { BASE64URL, HEXADECIMAL }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad) throws Exception {
        return encrypt(source, destination, key, algorithm, aad, Encoding.BASE64URL);
    }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, Encoding encoding) throws Exception {
        return encrypt(source, destination, key, algorithm, aad, encoding, null);
    }

    /** CBC compatibility mode uses the caller-provided IV for every line; AEAD modes generate a fresh nonce per line. */
    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, Encoding encoding, byte[] iv) throws Exception {
        return encrypt(source, destination, key, algorithm, aad, encoding, iv, StandardCharsets.UTF_8, false);
    }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, Encoding encoding,
                                 byte[] iv, Charset textCharset, boolean compactRecords) throws Exception {
        return encrypt(source, destination, key, algorithm, aad, encoding, iv, textCharset, compactRecords, ProgressMonitor.NO_OP);
    }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, Encoding encoding,
                                 byte[] iv, Charset textCharset, boolean compactRecords, ProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        LineRecordCipher.validateAlgorithmAndKey(algorithm, key);
        LineRecordCipher.validateIvAndAad(algorithm, iv, aad);
        Path temporary = temporarySibling(destination);
        long lines = 0;
        long totalBytes = Files.exists(source) ? Files.size(source) : -1;
        long bytesProcessed = 0;
        try (BufferedReader input = Files.newBufferedReader(source, textCharset);
             BufferedWriter output = Files.newBufferedWriter(temporary, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = input.readLine()) != null) {
                if (monitor.isCancelled()) {
                    throw new java.util.concurrent.CancellationException("Line file cipher encryption cancelled");
                }
                bytesProcessed += line.getBytes(textCharset).length + 1;
                String record = LineRecordCipher.encryptRecord(line, key, algorithm, aad, encoding, iv, textCharset, compactRecords);
                if (lines++ > 0) output.newLine();
                output.write(record);
                monitor.updateProgress(bytesProcessed, totalBytes);
            }
        } catch (Exception e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        moveAtomically(temporary, destination);
        return new Result(lines, Files.size(source), Files.size(destination));
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad) throws Exception {
        return decrypt(source, destination, key, algorithm, aad, null);
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, byte[] iv) throws Exception {
        return decrypt(source, destination, key, algorithm, aad, iv, Encoding.BASE64URL);
    }

    /**
     * Decodes the self-describing record formats and, for CBC compatibility,
     * accepts compact legacy records containing only ciphertext per line.
     */
    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, byte[] iv,
                                 Encoding compactCbcEncoding) throws Exception {
        return decrypt(source, destination, key, algorithm, aad, iv, compactCbcEncoding, StandardCharsets.UTF_8);
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, byte[] iv,
                                 Encoding compactCbcEncoding, Charset textCharset) throws Exception {
        return decrypt(source, destination, key, algorithm, aad, iv, compactCbcEncoding, textCharset, ProgressMonitor.NO_OP);
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, byte[] aad, byte[] iv,
                                 Encoding compactCbcEncoding, Charset textCharset, ProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        LineRecordCipher.validateAlgorithmAndKey(algorithm, key);
        LineRecordCipher.validateIvAndAad(algorithm, iv, aad);
        Path temporary = temporarySibling(destination);
        long lines = 0;
        long totalBytes = Files.exists(source) ? Files.size(source) : -1;
        long bytesProcessed = 0;
        try (BufferedReader input = Files.newBufferedReader(source, StandardCharsets.US_ASCII);
             BufferedWriter output = Files.newBufferedWriter(temporary, textCharset)) {
            String record;
            while ((record = input.readLine()) != null) {
                if (monitor.isCancelled()) {
                    throw new java.util.concurrent.CancellationException("Line file cipher decryption cancelled");
                }
                bytesProcessed += record.getBytes(StandardCharsets.US_ASCII).length + 1;
                String plain = LineRecordCipher.decryptRecord(record, key, algorithm, aad, iv, compactCbcEncoding, textCharset, lines + 1);
                if (lines++ > 0) output.newLine();
                output.write(plain);
                monitor.updateProgress(bytesProcessed, totalBytes);
            }
        } catch (Exception e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        moveAtomically(temporary, destination);
        return new Result(lines, Files.size(source), Files.size(destination));
    }

    private static Path temporarySibling(Path destination) throws java.io.IOException {
        return Files.createTempFile(destination.toAbsolutePath().getParent(), ".cryptocarver-lines-", ".tmp");
    }

    private static void moveAtomically(Path source, Path target) throws java.io.IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
