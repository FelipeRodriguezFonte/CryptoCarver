package com.cryptocarver.crypto;

import com.cryptocarver.util.ProgressMonitor;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.concurrent.CancellationException;

/**
 * File encryption that keeps payloads out of memory. AEAD tags are stored in a
 * separate file and decryption writes to a temporary file until authentication
 * succeeds, so an invalid tag never leaves a completed plaintext output.
 */
public final class StreamingCipher {
    private static int bufferSize = 64 * 1024;
    private static final int AEAD_TAG_BYTES = 16;

    public static void setBufferSize(int size) {
        if (size <= 0) throw new IllegalArgumentException("Buffer size must be positive");
        bufferSize = size;
    }

    public static int getBufferSize() {
        return bufferSize;
    }

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private StreamingCipher() { }

    public record Result(long inputBytes, long outputBytes, boolean authenticated) { }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, String mode,
                                 byte[] iv, byte[] aad, Path tagDestination) throws Exception {
        return encrypt(source, destination, key, algorithm, mode, iv, aad, tagDestination, ProgressMonitor.NO_OP);
    }

    public static Result encrypt(Path source, Path destination, byte[] key, String algorithm, String mode,
                                 byte[] iv, byte[] aad, Path tagDestination, ProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, key, algorithm, mode, iv, aad);
        boolean aead = isAead(algorithm, mode);
        if (aead && tagDestination == null) throw new IllegalArgumentException("AEAD encryption requires a separate tag file");
        return process(source, destination, tagDestination, cipher, aead, true, monitor);
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, String mode,
                                 byte[] iv, byte[] aad, Path tagSource) throws Exception {
        return decrypt(source, destination, key, algorithm, mode, iv, aad, tagSource, ProgressMonitor.NO_OP);
    }

    public static Result decrypt(Path source, Path destination, byte[] key, String algorithm, String mode,
                                 byte[] iv, byte[] aad, Path tagSource, ProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = ProgressMonitor.NO_OP;
        boolean aead = isAead(algorithm, mode);
        if (aead && tagSource == null) throw new IllegalArgumentException("AEAD decryption requires the tag file");
        byte[] tag = aead ? Files.readAllBytes(tagSource) : null;
        if (aead && tag.length != AEAD_TAG_BYTES) throw new IllegalArgumentException("AEAD tag must be exactly 16 bytes");
        Cipher cipher = newCipher(Cipher.DECRYPT_MODE, key, algorithm, mode, iv, aad);
        return process(source, destination, tag, cipher, aead, monitor);
    }

    private static Result process(Path source, Path destination, Path tagDestination, Cipher cipher,
                                  boolean aead, boolean encrypt, ProgressMonitor monitor) throws Exception {
        Path outputTemp = temporarySibling(destination);
        Path tagTemp = aead ? temporarySibling(tagDestination) : null;
        long inputBytes = 0;
        long totalBytes = Files.size(source);
        try (var input = new BufferedInputStream(Files.newInputStream(source), bufferSize);
             var output = new BufferedOutputStream(Files.newOutputStream(outputTemp), bufferSize)) {
            byte[] buffer = new byte[bufferSize];
            for (int read; (read = input.read(buffer)) != -1;) {
                if (monitor.isCancelled()) throw new CancellationException("Encryption operation cancelled");
                inputBytes += read;
                write(output, cipher.update(buffer, 0, read));
                monitor.updateProgress(inputBytes, totalBytes);
            }
            byte[] finalBytes = cipher.doFinal();
            if (aead) {
                if (finalBytes.length < AEAD_TAG_BYTES) throw new IllegalStateException("Cipher did not return an AEAD tag");
                int cipherTailLength = finalBytes.length - AEAD_TAG_BYTES;
                output.write(finalBytes, 0, cipherTailLength);
                Files.write(tagTemp, java.util.Arrays.copyOfRange(finalBytes, cipherTailLength, finalBytes.length));
            } else {
                write(output, finalBytes);
            }
        } catch (Exception e) {
            Files.deleteIfExists(outputTemp);
            if (tagTemp != null) Files.deleteIfExists(tagTemp);
            throw e;
        }
        moveAtomically(outputTemp, destination);
        if (tagTemp != null) moveAtomically(tagTemp, tagDestination);
        return new Result(inputBytes, Files.size(destination), aead);
    }

    private static Result process(Path source, Path destination, byte[] tag, Cipher cipher, boolean aead, ProgressMonitor monitor) throws Exception {
        Path outputTemp = temporarySibling(destination);
        long inputBytes = 0;
        long totalBytes = Files.size(source);
        try (var input = new BufferedInputStream(Files.newInputStream(source), bufferSize);
             var output = new BufferedOutputStream(Files.newOutputStream(outputTemp), bufferSize)) {
            byte[] buffer = new byte[bufferSize];
            for (int read; (read = input.read(buffer)) != -1;) {
                if (monitor.isCancelled()) throw new CancellationException("Decryption operation cancelled");
                inputBytes += read;
                write(output, cipher.update(buffer, 0, read));
                monitor.updateProgress(inputBytes, totalBytes);
            }
            write(output, aead ? cipher.doFinal(tag) : cipher.doFinal());
        } catch (Exception e) {
            Files.deleteIfExists(outputTemp);
            throw e;
        }
        moveAtomically(outputTemp, destination);
        return new Result(inputBytes, Files.size(destination), aead);
    }

    private static Cipher newCipher(int operation, byte[] key, String algorithm, String mode, byte[] iv, byte[] aad)
            throws Exception {
        if (key == null || iv == null) throw new IllegalArgumentException("Key and IV/nonce are required");
        boolean chacha = "ChaCha20-Poly1305".equals(algorithm);
        if (chacha && key.length != 32) throw new IllegalArgumentException("ChaCha20-Poly1305 key must be 32 bytes");
        if (!chacha && (!algorithm.startsWith("AES") || (key.length != 16 && key.length != 24 && key.length != 32))) {
            throw new IllegalArgumentException("Supported file algorithms are AES-128/192/256 and ChaCha20-Poly1305");
        }
        if (iv.length < 12) throw new IllegalArgumentException("IV/nonce must be at least 12 bytes");
        String transformation = chacha ? "ChaCha20-Poly1305" : "AES/" + mode + "/" + ("CBC".equals(mode) ? "PKCS5Padding" : "NoPadding");
        if (!chacha && !("CBC".equals(mode) || "CTR".equals(mode) || "GCM".equals(mode))) {
            throw new IllegalArgumentException("File mode must be CBC, CTR, or GCM");
        }
        Cipher cipher = Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, chacha ? "ChaCha20" : "AES");
        if ("GCM".equals(mode)) cipher.init(operation, secretKey, new GCMParameterSpec(128, iv));
        else cipher.init(operation, secretKey, new IvParameterSpec(iv));
        if (aad != null && aad.length > 0 && isAead(algorithm, mode)) cipher.updateAAD(aad);
        return cipher;
    }

    private static boolean isAead(String algorithm, String mode) {
        return "GCM".equals(mode) || "ChaCha20-Poly1305".equals(algorithm);
    }

    private static void write(BufferedOutputStream output, byte[] data) throws IOException {
        if (data != null && data.length > 0) output.write(data);
    }

    private static Path temporarySibling(Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        return Files.createTempFile(parent, ".cryptocarver-", ".tmp");
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
