package com.cryptoforge.crypto;

import com.cryptoforge.util.ProgressMonitor;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.CancellationException;

/** Streaming file diagnostics suitable for files larger than available memory. */
public final class StreamingFileTools {
    private static int bufferSize = 64 * 1024;

    public static void setBufferSize(int size) {
        if (size <= 0) throw new IllegalArgumentException("Buffer size must be positive");
        bufferSize = size;
    }

    public static int getBufferSize() {
        return bufferSize;
    }
    private StreamingFileTools() { }

    public static String hash(Path file, String algorithm) throws Exception {
        return hash(file, algorithm, ProgressMonitor.NO_OP);
    }

    public static String hash(Path file, String algorithm, ProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        long bytesProcessed = 0;
        long totalBytes = Files.size(file);
        try (var input = new BufferedInputStream(Files.newInputStream(file), bufferSize)) {
            byte[] buffer = new byte[bufferSize];
            for (int read; (read = input.read(buffer)) != -1;) {
                if (monitor.isCancelled()) throw new CancellationException("Hash operation cancelled");
                digest.update(buffer, 0, read);
                bytesProcessed += read;
                monitor.updateProgress(bytesProcessed, totalBytes);
            }
        }
        return com.cryptoforge.util.DataConverter.bytesToHex(digest.digest());
    }

    public static long firstDifference(Path left, Path right) throws IOException {
        return firstDifference(left, right, ProgressMonitor.NO_OP);
    }

    /** Returns -1 when files are identical, otherwise the first different byte offset. */
    public static long firstDifference(Path left, Path right, ProgressMonitor monitor) throws IOException {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        long totalBytes = Math.max(Files.size(left), Files.size(right));
        try (var leftInput = new BufferedInputStream(Files.newInputStream(left), bufferSize);
             var rightInput = new BufferedInputStream(Files.newInputStream(right), bufferSize)) {
            long offset = 0;
            byte[] leftBuf = new byte[bufferSize];
            byte[] rightBuf = new byte[bufferSize];

            while (true) {
                if (monitor.isCancelled()) throw new CancellationException("File comparison cancelled");
                monitor.updateProgress(offset, totalBytes);

                int readLeft = readFully(leftInput, leftBuf);
                int readRight = readFully(rightInput, rightBuf);

                if (readLeft == 0 && readRight == 0) return -1;

                int compareLen = Math.min(readLeft, readRight);
                for (int i = 0; i < compareLen; i++) {
                    if (leftBuf[i] != rightBuf[i]) return offset + i;
                }

                if (readLeft != readRight) return offset + compareLen; // One file is shorter

                offset += compareLen;
            }
        }
    }

    private static int readFully(java.io.InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read == -1) break;
            total += read;
        }
        return total;
    }

    public static byte[] preview(Path file, int maxBytes) throws IOException {
        return preview(file, maxBytes, ProgressMonitor.NO_OP);
    }

    public static byte[] preview(Path file, int maxBytes, ProgressMonitor monitor) throws IOException {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        if (maxBytes <= 0) throw new IllegalArgumentException("Preview size must be positive");
        long totalBytes = Math.min(Files.size(file), maxBytes);
        try (var input = new BufferedInputStream(Files.newInputStream(file), bufferSize)) {
            byte[] result = new byte[maxBytes];
            int totalRead = 0;
            byte[] buffer = new byte[bufferSize];
            while (totalRead < maxBytes) {
                if (monitor.isCancelled()) throw new CancellationException("Preview operation cancelled");
                int toRead = Math.min(bufferSize, maxBytes - totalRead);
                int read = input.read(buffer, 0, toRead);
                if (read == -1) break;
                System.arraycopy(buffer, 0, result, totalRead, read);
                totalRead += read;
                monitor.updateProgress(totalRead, totalBytes);
            }
            if (totalRead < maxBytes) {
                byte[] trimmed = new byte[totalRead];
                System.arraycopy(result, 0, trimmed, 0, totalRead);
                return trimmed;
            }
            return result;
        }
    }

    public static void convertCharset(java.nio.file.Path source, java.nio.file.Path destination, java.nio.charset.Charset from, java.nio.charset.Charset to) throws IOException {
        convertCharset(source, destination, from, to, ProgressMonitor.NO_OP);
    }

    public static void convertCharset(java.nio.file.Path source, java.nio.file.Path destination, java.nio.charset.Charset from, java.nio.charset.Charset to, ProgressMonitor monitor) throws IOException {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        long totalBytes = Files.size(source);
        long bytesProcessed = 0;

        java.nio.charset.CharsetDecoder decoder = from.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);

        java.nio.charset.CharsetEncoder encoder = to.newEncoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);

        java.nio.file.Path tempDest = temporarySibling(destination);
        try (var input = new java.io.BufferedInputStream(Files.newInputStream(source), bufferSize);
             var output = new java.io.BufferedOutputStream(Files.newOutputStream(tempDest), bufferSize)) {

            byte[] inArray = new byte[bufferSize];
            java.nio.ByteBuffer inBuf = java.nio.ByteBuffer.allocate(bufferSize);
            java.nio.CharBuffer charBuf = java.nio.CharBuffer.allocate(bufferSize);
            java.nio.ByteBuffer outBuf = java.nio.ByteBuffer.allocate((int) (bufferSize * encoder.maxBytesPerChar()));

            boolean endOfInput = false;
            while (!endOfInput) {
                if (monitor.isCancelled()) throw new CancellationException("Conversion operation cancelled");

                int toRead = Math.min(inBuf.remaining(), inArray.length);
                int read = input.read(inArray, 0, toRead);

                if (read == -1) {
                    endOfInput = true;
                } else {
                    inBuf.put(inArray, 0, read);
                    bytesProcessed += read;
                }

                inBuf.flip();

                while (true) {
                    java.nio.charset.CoderResult decodeResult = decoder.decode(inBuf, charBuf, endOfInput);
                    if (decodeResult.isError()) decodeResult.throwException();

                    charBuf.flip();
                    while (true) {
                        java.nio.charset.CoderResult encodeResult = encoder.encode(charBuf, outBuf, endOfInput);
                        if (encodeResult.isError()) encodeResult.throwException();

                        outBuf.flip();
                        if (outBuf.hasRemaining()) {
                            byte[] outArray = new byte[outBuf.remaining()];
                            outBuf.get(outArray);
                            output.write(outArray);
                        }
                        outBuf.clear();

                        if (encodeResult.isOverflow()) {
                            continue;
                        } else {
                            break;
                        }
                    }
                    charBuf.compact();

                    if (decodeResult.isOverflow()) {
                        continue;
                    } else {
                        break;
                    }
                }
                inBuf.compact();
                monitor.updateProgress(bytesProcessed, totalBytes);
            }

            // Flush decoder
            while (true) {
                java.nio.charset.CoderResult decodeFlush = decoder.flush(charBuf);
                if (decodeFlush.isError()) decodeFlush.throwException();

                charBuf.flip();
                while (true) {
                    java.nio.charset.CoderResult encodeResult = encoder.encode(charBuf, outBuf, true);
                    if (encodeResult.isError()) encodeResult.throwException();

                    outBuf.flip();
                    if (outBuf.hasRemaining()) {
                        byte[] outArray = new byte[outBuf.remaining()];
                        outBuf.get(outArray);
                        output.write(outArray);
                    }
                    outBuf.clear();

                    if (encodeResult.isOverflow()) {
                        continue;
                    } else {
                        break;
                    }
                }
                charBuf.compact();

                if (decodeFlush.isOverflow()) {
                    continue;
                } else {
                    break;
                }
            }

            // Flush encoder
            while (true) {
                java.nio.charset.CoderResult encodeFlush = encoder.flush(outBuf);
                if (encodeFlush.isError()) encodeFlush.throwException();

                outBuf.flip();
                if (outBuf.hasRemaining()) {
                    byte[] outArray = new byte[outBuf.remaining()];
                    outBuf.get(outArray);
                    output.write(outArray);
                }
                outBuf.clear();

                if (encodeFlush.isOverflow()) {
                    continue;
                } else {
                    break;
                }
            }

        } catch (Exception e) {
            Files.deleteIfExists(tempDest);
            throw e;
        }

        moveAtomically(tempDest, destination);
    }

    private static void moveAtomically(java.nio.file.Path source, java.nio.file.Path destination) throws IOException {
        try {
            Files.move(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(source);
            throw e;
        }
    }

    private static java.nio.file.Path temporarySibling(java.nio.file.Path target) {
        return target.resolveSibling(target.getFileName() + ".cryptocarver-" + System.nanoTime() + ".tmp");
    }
}
