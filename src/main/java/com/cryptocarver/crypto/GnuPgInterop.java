package com.cryptocarver.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Optional, process-isolated bridge to a locally installed GnuPG executable.
 *
 * <p>The bridge is intentionally limited to public-key import and detached-signature verification.
 * It neither configures GnuPG globally nor forwards private keys or passphrases to an external
 * process. When GnuPG is absent, callers receive a structured unavailable status.</p>
 */
public final class GnuPgInterop {
    private static final int MAX_ARTIFACT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PROCESS_OUTPUT_BYTES = 64 * 1024;
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(10);
    private static final String ENV_BINARY = "CRYPTOCARVER_GPG_BINARY";

    private GnuPgInterop() {
    }

    public record Availability(boolean available, String executable, String version, String message) {
    }

    public record Verification(boolean cryptographicallyValid, String message) {
    }

    /** Result of a bidirectional, isolated interoperability exercise. */
    public record InteroperabilityResult(boolean gnuPgVerifiedCryptoCarverSignature,
                                         boolean gnuPgDecryptedCryptoCarverMessage,
                                         boolean cryptoCarverDecryptedGnuPgMessage,
                                         boolean cryptoCarverVerifiedGnuPgSignature,
                                         String message) {
        public boolean successful() {
            return gnuPgVerifiedCryptoCarverSignature && gnuPgDecryptedCryptoCarverMessage
                    && cryptoCarverDecryptedGnuPgMessage && cryptoCarverVerifiedGnuPgSignature;
        }
    }

    /** Probes the locally configured GnuPG executable without modifying any keyring. */
    public static Availability probe() {
        return probe(resolveExecutable());
    }

    static Availability probe(String executable) {
        try {
            ProcessResult result = run(List.of(executable, "--version"), null);
            if (result.exitCode != 0) {
                return new Availability(false, executable, null, "GnuPG exited with code " + result.exitCode);
            }
            return new Availability(true, executable, parseVersion(result.output), "GnuPG is available");
        } catch (Exception error) {
            return new Availability(false, executable, null, "GnuPG is not available: " + safeMessage(error));
        }
    }

    /**
     * Verifies a detached OpenPGP signature using an isolated temporary GnuPG home directory.
     * The result is cryptographic only; it does not claim Web-of-Trust validity.
     */
    public static Verification verifyDetached(String publicKeyArmored, byte[] data,
                                               String signatureArmored) throws Exception {
        requireArtifact(publicKeyArmored, "Public key");
        if (data == null || data.length == 0) throw new IllegalArgumentException("Signed data is required");
        if (data.length > MAX_ARTIFACT_BYTES) throw new IllegalArgumentException("Signed data exceeds 16 MiB laboratory limit");
        requireArtifact(signatureArmored, "Detached signature");

        Availability availability = probe();
        if (!availability.available()) throw new IllegalStateException(availability.message());

        Path home = Files.createTempDirectory("cryptocarver-gpg-");
        try {
            Path publicKey = home.resolve("public.asc");
            Path content = home.resolve("content.bin");
            Path signature = home.resolve("signature.asc");
            Files.writeString(publicKey, publicKeyArmored, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            Files.write(content, data, StandardOpenOption.CREATE_NEW);
            Files.writeString(signature, signatureArmored, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);

            ProcessResult imported = run(command(availability.executable(), home, "--import", publicKey.toString()), home);
            if (imported.exitCode != 0) {
                return new Verification(false, "GnuPG could not import the supplied public key");
            }
            ProcessResult verified = run(command(availability.executable(), home, "--status-fd", "1", "--verify",
                    signature.toString(), content.toString()), home);
            boolean valid = verified.exitCode == 0
                    && (verified.output.contains("[GNUPG:] GOODSIG") || verified.output.contains("[GNUPG:] VALIDSIG"));
            return new Verification(valid, valid
                    ? "GnuPG confirms a cryptographically valid detached signature (trust is not evaluated)."
                    : "GnuPG could not validate the detached signature.");
        } finally {
            deleteRecursively(home);
        }
    }

    /**
     * Exercises encryption/decryption and detached-signature interoperability in
     * both directions. It is intentionally an explicit laboratory helper: the
     * secret key and passphrase only enter an isolated temporary GnuPG home and
     * are never written to a command line, a persistent keyring or application
     * settings. Callers should clear the passphrase when this returns.
     */
    public static InteroperabilityResult exerciseBidirectional(String publicKeyArmored, String secretKeyArmored,
                                                                 char[] passphrase, byte[] data) throws Exception {
        requireArtifact(publicKeyArmored, "Public key");
        requireArtifact(secretKeyArmored, "Secret key");
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Secret key passphrase is required");
        }
        if (data == null || data.length == 0) throw new IllegalArgumentException("Interop data is required");
        if (data.length > MAX_ARTIFACT_BYTES) throw new IllegalArgumentException("Interop data exceeds 16 MiB laboratory limit");

        Availability availability = probe();
        if (!availability.available()) throw new IllegalStateException(availability.message());
        String fingerprint = OpenPgpOperations.inspectKey(publicKeyArmored).fingerprint();
        Path home = Files.createTempDirectory("cryptocarver-gpg-interop-");
        try {
            Path publicKey = home.resolve("public.asc");
            Path secretKey = home.resolve("secret.asc");
            Path content = home.resolve("content.bin");
            Path cryptoCarverEncrypted = home.resolve("cryptocarver.pgp");
            Path gnuPgDecrypted = home.resolve("gnupg-decrypted.bin");
            Path gnuPgEncrypted = home.resolve("gnupg.pgp");
            Path gnuPgSignature = home.resolve("gnupg-signature.asc");
            Path cryptoCarverSignature = home.resolve("cryptocarver-signature.asc");
            Files.writeString(publicKey, publicKeyArmored, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Files.writeString(secretKey, secretKeyArmored, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Files.write(content, data, StandardOpenOption.CREATE_NEW);

            if (run(command(availability.executable(), home, "--import", publicKey.toString()), home, null).exitCode != 0
                    || run(command(availability.executable(), home, "--import", secretKey.toString()), home, null).exitCode != 0) {
                return new InteroperabilityResult(false, false, false, false,
                        "GnuPG could not import the temporary CryptoCarver key material.");
            }

            String cryptoCarverSignatureText = OpenPgpOperations.signDetached(data, secretKeyArmored, passphrase);
            Files.writeString(cryptoCarverSignature, cryptoCarverSignatureText, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            ProcessResult gnuPgVerify = run(command(availability.executable(), home, "--status-fd", "1", "--verify",
                    cryptoCarverSignature.toString(), content.toString()), home, null);
            boolean gnuPgVerified = gnuPgVerify.exitCode == 0
                    && (gnuPgVerify.output.contains("[GNUPG:] GOODSIG") || gnuPgVerify.output.contains("[GNUPG:] VALIDSIG"));

            String cryptoCarverMessage = OpenPgpOperations.encrypt(data, publicKeyArmored);
            Files.writeString(cryptoCarverEncrypted, cryptoCarverMessage, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            byte[] decryptPassphrase = passphraseBytes(passphrase);
            ProcessResult gnuPgDecrypt;
            try {
                gnuPgDecrypt = run(command(availability.executable(), home, "--pinentry-mode", "loopback",
                        "--passphrase-fd", "0", "--output", gnuPgDecrypted.toString(), "--decrypt",
                        cryptoCarverEncrypted.toString()), home, decryptPassphrase);
            } finally {
                Arrays.fill(decryptPassphrase, (byte) 0);
            }
            boolean gnuPgDecryptedSuccessfully = gnuPgDecrypt.exitCode == 0 && Files.isRegularFile(gnuPgDecrypted)
                    && java.security.MessageDigest.isEqual(data, Files.readAllBytes(gnuPgDecrypted));

            ProcessResult gnuPgEncrypt = run(command(availability.executable(), home, "--trust-model", "always",
                    "--armor", "--output", gnuPgEncrypted.toString(), "--encrypt", "--recipient", fingerprint,
                    content.toString()), home, null);
            boolean cryptoCarverDecrypted = false;
            if (gnuPgEncrypt.exitCode == 0 && Files.isRegularFile(gnuPgEncrypted)) {
                OpenPgpOperations.DecryptionResult decrypted = OpenPgpOperations.decrypt(
                        Files.readString(gnuPgEncrypted, StandardCharsets.UTF_8), secretKeyArmored, passphrase);
                cryptoCarverDecrypted = java.security.MessageDigest.isEqual(data, decrypted.plaintext());
            }

            byte[] signingPassphrase = passphraseBytes(passphrase);
            ProcessResult gnuPgSign;
            try {
                gnuPgSign = run(command(availability.executable(), home, "--pinentry-mode", "loopback",
                        "--passphrase-fd", "0", "--armor", "--local-user", fingerprint, "--output",
                        gnuPgSignature.toString(), "--detach-sign", content.toString()), home, signingPassphrase);
            } finally {
                Arrays.fill(signingPassphrase, (byte) 0);
            }
            boolean cryptoCarverVerified = false;
            if (gnuPgSign.exitCode == 0 && Files.isRegularFile(gnuPgSignature)) {
                cryptoCarverVerified = OpenPgpOperations.verifyDetached(data,
                        Files.readString(gnuPgSignature, StandardCharsets.UTF_8), publicKeyArmored).valid();
            }

            String message = "GnuPG verification=" + gnuPgVerified + "; GnuPG decryption=" + gnuPgDecryptedSuccessfully
                    + "; CryptoCarver decryption=" + cryptoCarverDecrypted + "; CryptoCarver verification="
                    + cryptoCarverVerified + ". Trust is not evaluated.";
            return new InteroperabilityResult(gnuPgVerified, gnuPgDecryptedSuccessfully, cryptoCarverDecrypted,
                    cryptoCarverVerified, message);
        } finally {
            deleteRecursively(home);
        }
    }

    static String parseVersion(String output) {
        if (output == null) return null;
        for (String line : output.split("\\R")) {
            if (line.startsWith("gpg (GnuPG)") || line.startsWith("gpg2 (GnuPG)")) return line.trim();
        }
        return output.lines().findFirst().map(String::trim).orElse(null);
    }

    private static String resolveExecutable() {
        String configured = System.getenv(ENV_BINARY);
        return configured == null || configured.isBlank() ? "gpg" : configured.trim();
    }

    private static List<String> command(String executable, Path home, String... args) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--no-options");
        command.add("--batch");
        command.add("--homedir");
        command.add(home.toString());
        command.addAll(List.of(args));
        return command;
    }

    private static ProcessResult run(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        return run(command, workingDirectory, null);
    }

    private static ProcessResult run(List<String> command, Path workingDirectory, byte[] standardInput)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDirectory != null) builder.directory(workingDirectory.toFile());
        Process process = builder.start();
        try (var input = process.getOutputStream()) {
            if (standardInput != null) input.write(standardInput);
        }
        byte[] output;
        try (InputStream stream = process.getInputStream()) {
            output = readBounded(stream, MAX_PROCESS_OUTPUT_BYTES);
        }
        if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException("GnuPG process timed out");
        }
        return new ProcessResult(process.exitValue(), new String(output, StandardCharsets.UTF_8));
    }

    private static byte[] passphraseBytes(char[] passphrase) {
        java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(passphrase));
        byte[] result = new byte[encoded.remaining() + 1];
        encoded.get(result, 0, result.length - 1);
        result[result.length - 1] = '\n';
        return result;
    }

    private static byte[] readBounded(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            if (output.size() + read > limit) throw new IOException("GnuPG output exceeded safety limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void requireArtifact(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException(label + " exceeds 16 MiB laboratory limit");
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort cleanup of a short-lived GnuPG laboratory home.
                }
            });
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
