package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.util.DataConverter;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Brute-force analysis of an encrypted file against the symmetric algorithm, mode and padding
 * combinations that fit the key, without JavaFX. Writes {@code report.txt}, {@code report.html}
 * and {@code attempts.csv} to an {@code analysis_<file>_<timestamp>} folder next to the input.
 */
final class EncryptedFileAnalyzer {

    /** IV/nonce, AAD and tag as typed in the cipher screen, read once before the analysis starts. */
    record CipherInputs(String ivHex, String aadText, boolean aadEnabled, String tagHex,
            boolean allowRecoveredTextPreview) {
        /** Callers must explicitly authorize plaintext previews; omission protects generated files. */
        CipherInputs(String ivHex, String aadText, boolean aadEnabled, String tagHex) {
            this(ivHex, aadText, aadEnabled, tagHex, false);
        }

        CipherInputs {
            ivHex = ivHex == null ? "" : ivHex.trim();
            aadText = aadText == null ? "" : aadText;
            tagHex = tagHex == null ? "" : tagHex.trim();
        }
    }

    /**
     * What the cipher screen shows once the files are written. {@code inspectorDetails},
     * {@code inspectorOutput}, {@code historyInput} and {@code historyResult} are null when no candidate decrypted.
     */
    record Outcome(String reportText,
            String status,
            byte[] analysedBytes,
            byte[] inspectorOutput,
            Map<String, String> inspectorDetails,
            String historyInput,
            String historyResult) {

        boolean hasCandidate() {
            return inspectorDetails != null;
        }
    }

    private final CipherInputs inputs;

    EncryptedFileAnalyzer(CipherInputs inputs) {
        this.inputs = inputs;
    }

    /**
     * Runs the analysis and writes its report files. Returns null when the input file is empty;
     * the analysis folder has already been created by then, as before the extraction.
     */
    Outcome analyze(Path inputFile, byte[] key, FileAnalysisOptions options) throws Exception {
        FileAnalysisOptions effectiveOptions = normalizeFileAnalysisOptions(options);
        Path analysisDirectory = createAnalysisDirectory(inputFile);

        byte[] fileBytes = Files.readAllBytes(inputFile);
        if (fileBytes.length == 0) {
            return null;
        }

        byte[] analysisBytes = fileBytes;
        boolean sampled = false;
        if (fileBytes.length > effectiveOptions.getSampleSizeBytes()) {
            analysisBytes = Arrays.copyOf(fileBytes, effectiveOptions.getSampleSizeBytes());
            sampled = true;
        }

        List<FileDataEncoding> encodingsToTest = resolveInputEncodings(effectiveOptions.getForcedInputEncoding());
        List<String> algorithms = getAlgorithmCandidatesByKeyLength(key.length);
        List<CipherCombination> combinations = buildCipherCombinations(algorithms);

        AnalysisProgress progress = new AnalysisProgress();
        testInputEncodings(analysisBytes, key, effectiveOptions, encodingsToTest, combinations, progress);

        Path attemptsLogPath = analysisDirectory.resolve("attempts.csv");
        writeAttemptLog(attemptsLogPath, progress.attemptLog);
        if (progress.candidates.isEmpty()) {
            return writeNoCandidateOutcome(inputFile, analysisDirectory, fileBytes, analysisBytes,
                    sampled, attemptsLogPath, progress);
        }
        return writeCandidateOutcome(inputFile, analysisDirectory, fileBytes, analysisBytes,
                sampled, attemptsLogPath, effectiveOptions, progress);
    }

    /** Counters and ordered results shared by the sequential analysis stages. */
    private static final class AnalysisProgress {
        private int attempts = 0;
        private int successes = 0;
        private int attemptIndex = 0;
        private final List<AnalysisCandidate> candidates = new ArrayList<>();
        private final List<AnalysisAttempt> attemptLog = new ArrayList<>();
    }

    private void testInputEncodings(byte[] analysisBytes, byte[] key, FileAnalysisOptions effectiveOptions,
            List<FileDataEncoding> encodingsToTest, List<CipherCombination> combinations, AnalysisProgress progress) {
        for (FileDataEncoding inputEncoding : encodingsToTest) {
            byte[] decodedCiphertext;
            try {
                decodedCiphertext = decodeFileData(analysisBytes, inputEncoding);
            } catch (Exception decodeError) {
                continue;
            }

            if (decodedCiphertext == null || decodedCiphertext.length == 0) {
                continue;
            }

            if (effectiveOptions.isTestFullContent()) {
                testFullContent(decodedCiphertext, key, inputEncoding, combinations, progress);
            }

            if (effectiveOptions.isTestIndependentBlocks()) {
                testIndependentBlocks(decodedCiphertext, key, inputEncoding, effectiveOptions, combinations, progress);
            }

        }
    }

    private void testFullContent(byte[] decodedCiphertext, byte[] key, FileDataEncoding inputEncoding,
            List<CipherCombination> combinations, AnalysisProgress progress) {
        for (CipherCombination combo : combinations) {
            progress.attemptIndex++;
            progress.attempts++;
            try {
                byte[] plaintext = decryptSymmetricBytes(
                        decodedCiphertext,
                        key,
                        combo.algorithm,
                        combo.mode,
                        combo.padding);
                PaddingEvidence paddingEvidence = computePaddingEvidence(
                        combo,
                        "FULL_CONTENT",
                        0,
                        decodedCiphertext,
                        key);
                AnalysisCandidate candidate = buildAnalysisCandidate(
                        combo.algorithm,
                        combo.mode,
                        combo.padding,
                        "FULL_CONTENT",
                        0,
                        inputEncoding,
                        plaintext,
                        paddingEvidence);
                progress.candidates.add(candidate);
                progress.attemptLog.add(new AnalysisAttempt(
                        progress.attemptIndex,
                        combo.algorithm,
                        combo.mode,
                        combo.padding,
                        "FULL_CONTENT",
                        0,
                        inputEncoding,
                        true,
                        candidate.score,
                        candidate.inferredPlainEncoding,
                        candidate.preview,
                        ""));
                progress.successes++;
            } catch (Exception error) {
                progress.attemptLog.add(new AnalysisAttempt(
                        progress.attemptIndex,
                        combo.algorithm,
                        combo.mode,
                        combo.padding,
                        "FULL_CONTENT",
                        0,
                        inputEncoding,
                        false,
                        0,
                        "",
                        "",
                        safeErrorMessage(error)));
            }
        }
    }

    private void testIndependentBlocks(byte[] decodedCiphertext, byte[] key, FileDataEncoding inputEncoding,
            FileAnalysisOptions effectiveOptions, List<CipherCombination> combinations, AnalysisProgress progress) {
        int structuredBlockSize = extractStructuredBlockSize(decodedCiphertext);
        // 1) Structured independent-block format (native CryptoCarver expert mode).
        testStructuredBlocks(decodedCiphertext, key, inputEncoding, structuredBlockSize, combinations, progress);

        // 2) Heuristic independent-block mode on raw chunks with user-provided sizes.
        testGuessedBlocks(decodedCiphertext, key, inputEncoding, effectiveOptions, combinations, progress);
    }

    private void testStructuredBlocks(byte[] decodedCiphertext, byte[] key, FileDataEncoding inputEncoding,
            int structuredBlockSize, List<CipherCombination> combinations, AnalysisProgress progress) {
        for (CipherCombination combo : combinations) {
            progress.attemptIndex++;
            progress.attempts++;
            try {
                byte[] plaintext = decryptIndependentBlocks(
                        decodedCiphertext,
                        key,
                        combo.algorithm,
                        combo.mode,
                        combo.padding);
                PaddingEvidence paddingEvidence = computePaddingEvidence(
                        combo,
                        "INDEPENDENT_BLOCKS_STRUCTURED",
                        structuredBlockSize,
                        decodedCiphertext,
                        key);
                AnalysisCandidate candidate = buildAnalysisCandidate(
                        combo.algorithm,
                        combo.mode,
                        combo.padding,
                        "INDEPENDENT_BLOCKS_STRUCTURED",
                        structuredBlockSize,
                        inputEncoding,
                        plaintext,
                        paddingEvidence);
                progress.candidates.add(candidate);
                progress.attemptLog.add(new AnalysisAttempt(
                        progress.attemptIndex,
                        combo.algorithm,
                        combo.mode,
                        combo.padding,
                        "INDEPENDENT_BLOCKS_STRUCTURED",
                        structuredBlockSize,
                        inputEncoding,
                        true,
                        candidate.score,
                        candidate.inferredPlainEncoding,
                        candidate.preview,
                        ""));
                progress.successes++;
            } catch (Exception error) {
                progress.attemptLog.add(new AnalysisAttempt(
                        progress.attemptIndex,
                        combo.algorithm,
                        combo.mode,
                        combo.padding,
                        "INDEPENDENT_BLOCKS_STRUCTURED",
                        structuredBlockSize,
                        inputEncoding,
                        false,
                        0,
                        "",
                        "",
                        safeErrorMessage(error)));
            }
        }
    }

    private void testGuessedBlocks(byte[] decodedCiphertext, byte[] key, FileDataEncoding inputEncoding,
            FileAnalysisOptions effectiveOptions, List<CipherCombination> combinations, AnalysisProgress progress) {
        for (int blockSize : effectiveOptions.getCandidateBlockSizes()) {
            for (CipherCombination combo : combinations) {
                progress.attemptIndex++;
                progress.attempts++;
                try {
                    byte[] plaintext = decryptIndependentBlocksRawGuess(
                            decodedCiphertext,
                            key,
                            combo.algorithm,
                            combo.mode,
                            combo.padding,
                            blockSize);
                    PaddingEvidence paddingEvidence = computePaddingEvidence(
                            combo,
                            "INDEPENDENT_BLOCKS_GUESS",
                            blockSize,
                            decodedCiphertext,
                            key);
                    AnalysisCandidate candidate = buildAnalysisCandidate(
                            combo.algorithm,
                            combo.mode,
                            combo.padding,
                            "INDEPENDENT_BLOCKS_GUESS",
                            blockSize,
                            inputEncoding,
                            plaintext,
                            paddingEvidence);
                    progress.candidates.add(candidate);
                    progress.attemptLog.add(new AnalysisAttempt(
                            progress.attemptIndex,
                            combo.algorithm,
                            combo.mode,
                            combo.padding,
                            "INDEPENDENT_BLOCKS_GUESS",
                            blockSize,
                            inputEncoding,
                            true,
                            candidate.score,
                            candidate.inferredPlainEncoding,
                            candidate.preview,
                            ""));
                    progress.successes++;
                } catch (Exception error) {
                    progress.attemptLog.add(new AnalysisAttempt(
                            progress.attemptIndex,
                            combo.algorithm,
                            combo.mode,
                            combo.padding,
                            "INDEPENDENT_BLOCKS_GUESS",
                            blockSize,
                            inputEncoding,
                            false,
                            0,
                            "",
                            "",
                            safeErrorMessage(error)));
                }
            }
        }
    }

    private Outcome writeNoCandidateOutcome(Path inputFile, Path analysisDirectory, byte[] fileBytes,
            byte[] analysisBytes, boolean sampled, Path attemptsLogPath, AnalysisProgress progress) throws Exception {
        StringBuilder noResult = new StringBuilder();
        noResult.append("=== ENCRYPTED FILE ANALYSIS REPORT ===\n\n");
        noResult.append("No valid decryption candidates found.\n\n");
        noResult.append("File: ").append(inputFile).append("\n");
        noResult.append("File size: ").append(fileBytes.length).append(" bytes\n");
        noResult.append("Tested sample: ").append(analysisBytes.length).append(" bytes");
        if (sampled) {
            noResult.append(" (sampled)");
        }
        noResult.append("\n");
        noResult.append("Attempts: ").append(progress.attempts).append("\n");
        noResult.append("Successes: ").append(progress.successes).append("\n\n");
        noResult.append("Analysis Directory: ").append(analysisDirectory).append("\n");
        noResult.append("Attempt Log: ").append(attemptsLogPath).append("\n\n");
        noResult.append("Tips:\n");
        noResult.append("- Verify key and IV/Nonce.\n");
        noResult.append("- Provide GCM/Auth TAG if needed.\n");
        noResult.append("- Try enabling more input encodings and chunk sizes.\n");
        Files.writeString(analysisDirectory.resolve("report.txt"), noResult.toString(), StandardCharsets.UTF_8);
        EncryptedFileAnalysisReportWriter.writeHtmlReport(
                analysisDirectory.resolve("report.html"),
                inputFile,
                fileBytes.length,
                analysisBytes.length,
                sampled,
                progress.attempts,
                progress.successes,
                List.of(),
                List.of(),
                attemptsLogPath);
        return new Outcome(noResult.toString(), "Encrypted file analysis finished: no matches",
                analysisBytes, null, null, null, null);
    }

    private Outcome writeCandidateOutcome(Path inputFile, Path analysisDirectory, byte[] fileBytes,
            byte[] analysisBytes, boolean sampled, Path attemptsLogPath, FileAnalysisOptions effectiveOptions,
            AnalysisProgress progress) throws Exception {
        List<AnalysisCandidate> topCandidates = selectTopCandidates(progress.candidates, effectiveOptions.getMaxResults());
        assignConfidencePercentages(topCandidates);
        List<AnalysisCandidate> probableCandidates = selectProbableCandidates(topCandidates);
        AnalysisCandidate best = topCandidates.get(0);

        String reportText = EncryptedFileAnalysisReportWriter.formatAnalysisReport(
                inputFile,
                fileBytes.length,
                analysisBytes.length,
                sampled,
                progress.attempts,
                progress.successes,
                topCandidates,
                probableCandidates,
                analysisDirectory,
                attemptsLogPath);
        Files.writeString(analysisDirectory.resolve("report.txt"), reportText, StandardCharsets.UTF_8);
        EncryptedFileAnalysisReportWriter.writeHtmlReport(
                analysisDirectory.resolve("report.html"),
                inputFile,
                fileBytes.length,
                analysisBytes.length,
                sampled,
                progress.attempts,
                progress.successes,
                topCandidates,
                probableCandidates,
                attemptsLogPath);

        Map<String, String> details = new HashMap<>();
        details.put("File", inputFile.toString());
        details.put("Attempts", String.valueOf(progress.attempts));
        details.put("Successful Candidates", String.valueOf(progress.successes));
        details.put("Best Algorithm", best.algorithm);
        details.put("Best Mode", best.mode);
        details.put("Best Padding", best.padding);
        details.put("Best Processing", best.processing);
        details.put("Best Input Encoding", best.inputEncoding.name());
        details.put("Best Inferred Plain Encoding", best.inferredPlainEncoding);
        details.put("Best Score", String.valueOf(best.score));
        details.put("Best Confidence", formatPercent(best.confidencePercent));
        details.put("Analysis Directory", analysisDirectory.toString());
        details.put("Attempt Log", attemptsLogPath.toString());

        String historyResult = best.algorithm + "/" + best.mode + "/" + best.padding + " [" + best.processing + "]"
                + " -> " + analysisDirectory.getFileName();
        String status = "Encrypted file analysis finished: best candidate "
                + best.algorithm + "/" + best.mode + "/" + best.padding
                + " | report: " + analysisDirectory.resolve("report.html");
        byte[] inspectorOutput = best.plaintext.length > 4096 ? Arrays.copyOf(best.plaintext, 4096) : best.plaintext;
        String historyInput = inputFile.getFileName() + " (" + fileBytes.length + " bytes)";
        return new Outcome(reportText, status, analysisBytes, inspectorOutput, details, historyInput, historyResult);
    }



    private static final byte[] INDEPENDENT_BLOCK_MAGIC = "CFXBI1".getBytes(StandardCharsets.US_ASCII);
    private static final int[] DEFAULT_ANALYSIS_BLOCK_SIZES = new int[] { 64, 128, 256, 512, 1024, 2048, 4096 };
    private static final Charset CHARSET_EBCDIC_CP037 = Charset.forName("Cp037");
    private static final Charset CHARSET_EBCDIC_CP500 = Charset.forName("Cp500");

    public enum FileDataEncoding {
        RAW,
        HEX,
        BASE64,
        UTF8,
        EBCDIC_CP037,
        EBCDIC_CP500
    }

    public static class FileAnalysisOptions {
        private final int[] candidateBlockSizes;
        private final boolean testFullContent;
        private final boolean testIndependentBlocks;
        private final int maxResults;
        private final FileDataEncoding forcedInputEncoding;
        private final int sampleSizeBytes;

        public FileAnalysisOptions(int[] candidateBlockSizes,
                boolean testFullContent,
                boolean testIndependentBlocks,
                int maxResults,
                FileDataEncoding forcedInputEncoding,
                int sampleSizeBytes) {
            this.candidateBlockSizes = candidateBlockSizes;
            this.testFullContent = testFullContent;
            this.testIndependentBlocks = testIndependentBlocks;
            this.maxResults = maxResults;
            this.forcedInputEncoding = forcedInputEncoding;
            this.sampleSizeBytes = sampleSizeBytes;
        }

        public static FileAnalysisOptions defaults() {
            return new FileAnalysisOptions(
                    Arrays.copyOf(DEFAULT_ANALYSIS_BLOCK_SIZES, DEFAULT_ANALYSIS_BLOCK_SIZES.length),
                    true,
                    true,
                    8,
                    null,
                    262144);
        }

        public int[] getCandidateBlockSizes() {
            return candidateBlockSizes;
        }

        public boolean isTestFullContent() {
            return testFullContent;
        }

        public boolean isTestIndependentBlocks() {
            return testIndependentBlocks;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public FileDataEncoding getForcedInputEncoding() {
            return forcedInputEncoding;
        }

        public int getSampleSizeBytes() {
            return sampleSizeBytes;
        }
    }

    static class AnalysisCandidate {
        final String algorithm;
        final String mode;
        final String padding;
        final String processing;
        final int blockSize;
        final FileDataEncoding inputEncoding;
        private final byte[] plaintext;
        final int baseScore;
        final int paddingAdjustment;
        final int score;
        final String inferredPlainEncoding;
        final String qualitySummary;
        final String preview;
        private final String paddingEvidence;
        double confidencePercent;

        private AnalysisCandidate(String algorithm,
                String mode,
                String padding,
                String processing,
                int blockSize,
                FileDataEncoding inputEncoding,
                byte[] plaintext,
                int baseScore,
                int paddingAdjustment,
                int score,
                String inferredPlainEncoding,
                String qualitySummary,
                String preview,
                String paddingEvidence) {
            this.algorithm = algorithm;
            this.mode = mode;
            this.padding = padding;
            this.processing = processing;
            this.blockSize = blockSize;
            this.inputEncoding = inputEncoding;
            this.plaintext = plaintext;
            this.baseScore = baseScore;
            this.paddingAdjustment = paddingAdjustment;
            this.score = score;
            this.inferredPlainEncoding = inferredPlainEncoding;
            this.qualitySummary = qualitySummary;
            this.preview = preview;
            this.paddingEvidence = paddingEvidence;
            this.confidencePercent = 0.0;
        }
    }

    private static class CipherCombination {
        private final String algorithm;
        private final String mode;
        private final String padding;

        private CipherCombination(String algorithm, String mode, String padding) {
            this.algorithm = algorithm;
            this.mode = mode;
            this.padding = padding;
        }
    }

    private static class AnalysisAttempt {
        private final int index;
        private final String algorithm;
        private final String mode;
        private final String padding;
        private final String processing;
        private final int blockSize;
        private final FileDataEncoding inputEncoding;
        private final boolean success;
        private final int score;
        private final String inferredPlainEncoding;
        private final String preview;
        private final String error;

        private AnalysisAttempt(int index,
                String algorithm,
                String mode,
                String padding,
                String processing,
                int blockSize,
                FileDataEncoding inputEncoding,
                boolean success,
                int score,
                String inferredPlainEncoding,
                String preview,
                String error) {
            this.index = index;
            this.algorithm = algorithm;
            this.mode = mode;
            this.padding = padding;
            this.processing = processing;
            this.blockSize = blockSize;
            this.inputEncoding = inputEncoding;
            this.success = success;
            this.score = score;
            this.inferredPlainEncoding = inferredPlainEncoding;
            this.preview = preview;
            this.error = error;
        }
    }

    private static class PlaintextQuality {
        private final int score;
        private final String inferredEncoding;
        private final String summary;
        private final String preview;

        private PlaintextQuality(int score, String inferredEncoding, String summary, String preview) {
            this.score = score;
            this.inferredEncoding = inferredEncoding;
            this.summary = summary;
            this.preview = preview;
        }
    }

    private static class PaddingEvidence {
        private final int adjustment;
        private final String summary;

        private PaddingEvidence(int adjustment, String summary) {
            this.adjustment = adjustment;
            this.summary = summary;
        }
    }

    private byte[] decodeFileData(byte[] fileData, FileDataEncoding encoding) {
        switch (encoding) {
            case RAW:
                return fileData;
            case HEX: {
                String hex = new String(fileData, StandardCharsets.UTF_8).replaceAll("\\s+", "");
                return DataConverter.hexToBytes(hex);
            }
            case BASE64: {
                String b64 = new String(fileData, StandardCharsets.UTF_8).trim();
                return DataConverter.decodeBase64Flexible(b64);
            }
            case UTF8:
                return new String(fileData, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            case EBCDIC_CP037:
                return new String(fileData, CHARSET_EBCDIC_CP037).getBytes(StandardCharsets.UTF_8);
            case EBCDIC_CP500:
                return new String(fileData, CHARSET_EBCDIC_CP500).getBytes(StandardCharsets.UTF_8);
            default:
                throw new IllegalArgumentException("Unsupported input encoding: " + encoding);
        }
    }

    private byte[] decryptIndependentBlocks(byte[] data,
            byte[] key,
            String algorithm,
            String mode,
            String padding) throws Exception {
        int minHeader = INDEPENDENT_BLOCK_MAGIC.length + 8;
        if (data.length < minHeader) {
            throw new IllegalArgumentException("Input too short for independent-block encrypted format");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] magic = new byte[INDEPENDENT_BLOCK_MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(magic, INDEPENDENT_BLOCK_MAGIC)) {
            throw new IllegalArgumentException(
                    "Invalid independent-block format header. Use full-content mode for raw ciphertext files.");
        }

        buffer.getInt(); // Stored block size: informational, the chunk lengths drive decryption.
        int blockCount = buffer.getInt();
        if (blockCount < 0) {
            throw new IllegalArgumentException("Invalid block count in encrypted file");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < blockCount; i++) {
            if (buffer.remaining() < 4) {
                throw new IllegalArgumentException("Corrupted block stream: missing block length for block " + (i + 1));
            }

            int encryptedLength = buffer.getInt();
            if (encryptedLength < 0 || encryptedLength > buffer.remaining()) {
                throw new IllegalArgumentException("Corrupted block stream: invalid block length at block " + (i + 1));
            }

            byte[] encryptedChunk = new byte[encryptedLength];
            buffer.get(encryptedChunk);
            byte[] plainChunk = decryptSymmetricBytes(encryptedChunk, key, algorithm, mode, padding);
            output.write(plainChunk);
        }

        if (buffer.hasRemaining()) {
            throw new IllegalArgumentException("Corrupted block stream: trailing bytes detected after last block");
        }

        return output.toByteArray();
    }

    private byte[] decryptIndependentBlocksRawGuess(byte[] data,
            byte[] key,
            String algorithm,
            String mode,
            String padding,
            int blockSize) throws Exception {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Block size must be greater than 0");
        }
        if (data.length < blockSize) {
            throw new IllegalArgumentException("Not enough data for configured block size");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int offset = 0; offset < data.length; offset += blockSize) {
            int end = Math.min(offset + blockSize, data.length);
            byte[] encryptedChunk = Arrays.copyOfRange(data, offset, end);
            byte[] plainChunk = decryptSymmetricBytes(encryptedChunk, key, algorithm, mode, padding);
            output.write(plainChunk);
        }

        return output.toByteArray();
    }

    private FileAnalysisOptions normalizeFileAnalysisOptions(FileAnalysisOptions options) {
        if (options == null) {
            return FileAnalysisOptions.defaults();
        }

        int[] blockSizes = options.getCandidateBlockSizes();
        if (blockSizes == null || blockSizes.length == 0) {
            blockSizes = new int[] { 512, 1024, 2048, 4096 };
        }

        List<Integer> normalizedSizes = new ArrayList<>();
        for (int size : blockSizes) {
            if (size > 0) {
                normalizedSizes.add(size);
            }
        }
        if (normalizedSizes.isEmpty()) {
            normalizedSizes.add(4096);
        }

        int[] cleanSizes = normalizedSizes.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
        int maxResults = options.getMaxResults() > 0 ? options.getMaxResults() : 8;
        int sampleSize = options.getSampleSizeBytes() > 0 ? options.getSampleSizeBytes() : 262144;
        boolean testFull = options.isTestFullContent();
        boolean testBlocks = options.isTestIndependentBlocks();

        if (!testFull && !testBlocks) {
            testFull = true;
        }

        return new FileAnalysisOptions(
                cleanSizes,
                testFull,
                testBlocks,
                maxResults,
                options.getForcedInputEncoding(),
                sampleSize);
    }

    private List<FileDataEncoding> resolveInputEncodings(FileDataEncoding forcedInputEncoding) {
        if (forcedInputEncoding != null) {
            return List.of(forcedInputEncoding);
        }
        return Arrays.asList(FileDataEncoding.values());
    }

    private List<String> getAlgorithmCandidatesByKeyLength(int keyLength) {
        List<String> candidates = new ArrayList<>();

        for (String algorithm : SymmetricCipher.SUPPORTED_ALGORITHMS) {
            if (algorithm.equals("DES") && keyLength == 8) {
                candidates.add(algorithm);
            } else if (algorithm.contains("3DES") && (keyLength == 16 || keyLength == 24)) {
                candidates.add(algorithm);
            } else if (algorithm.equals("AES-128") && keyLength == 16) {
                candidates.add(algorithm);
            } else if (algorithm.equals("AES-192") && keyLength == 24) {
                candidates.add(algorithm);
            } else if (algorithm.equals("AES-256") && keyLength == 32) {
                candidates.add(algorithm);
            } else if (SymmetricCipher.isStreamCipher(algorithm) && keyLength == 32) {
                candidates.add(algorithm);
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(SymmetricCipher.SUPPORTED_ALGORITHMS);
        }

        return candidates;
    }

    private List<CipherCombination> buildCipherCombinations(List<String> algorithms) {
        List<CipherCombination> combinations = new ArrayList<>();
        for (String algorithm : algorithms) {
            if (SymmetricCipher.isStreamCipher(algorithm)) {
                combinations.add(new CipherCombination(algorithm, "STREAM", "NoPadding"));
                continue;
            }

            for (String mode : SymmetricCipher.SUPPORTED_MODES) {
                if (SymmetricCipher.supportsPadding(mode)) {
                    for (String padding : SymmetricCipher.SUPPORTED_PADDINGS) {
                        // PKCS7 is normalized to PKCS5 in this app/provider stack, avoid duplicate attempts.
                        if ("PKCS7Padding".equals(padding)) {
                            continue;
                        }
                        combinations.add(new CipherCombination(algorithm, mode, padding));
                    }
                } else {
                    combinations.add(new CipherCombination(algorithm, mode, "NoPadding"));
                }
            }
        }
        return combinations;
    }

    private AnalysisCandidate buildAnalysisCandidate(String algorithm,
            String mode,
            String padding,
            String processing,
            int blockSize,
            FileDataEncoding inputEncoding,
            byte[] plaintext,
            PaddingEvidence paddingEvidence) {
        PlaintextQuality quality = evaluatePlaintextQuality(plaintext);
        int padAdj = paddingEvidence != null ? paddingEvidence.adjustment : 0;
        int totalScore = quality.score + padAdj;
        String qualitySummary = quality.summary;
        if (paddingEvidence != null && paddingEvidence.summary != null && !paddingEvidence.summary.isBlank()) {
            qualitySummary = qualitySummary + ", " + paddingEvidence.summary;
        }
        return new AnalysisCandidate(
                algorithm,
                mode,
                padding,
                processing,
                blockSize,
                inputEncoding,
                plaintext,
                quality.score,
                padAdj,
                totalScore,
                quality.inferredEncoding,
                qualitySummary,
                inputs.allowRecoveredTextPreview() ? quality.preview : "[REDACTED_SECRET]",
                paddingEvidence != null ? paddingEvidence.summary : "");
    }

    private PaddingEvidence computePaddingEvidence(CipherCombination combo,
            String processing,
            int blockSize,
            byte[] decodedCiphertext,
            byte[] key) {
        try {
            if (combo == null || decodedCiphertext == null || decodedCiphertext.length == 0) {
                return new PaddingEvidence(0, "padAdj=0");
            }

            if (SymmetricCipher.isStreamCipher(combo.algorithm) || !SymmetricCipher.supportsPadding(combo.mode)) {
                return new PaddingEvidence(0, "padAdj=0 (mode without padding semantics)");
            }

            if (!"CBC".equalsIgnoreCase(combo.mode) && !"ECB".equalsIgnoreCase(combo.mode)) {
                return new PaddingEvidence(0, "padAdj=0 (padding evidence not applicable)");
            }

            List<byte[]> chunks = switch (processing) {
                case "FULL_CONTENT" -> List.of(decodedCiphertext);
                case "INDEPENDENT_BLOCKS_STRUCTURED" -> extractStructuredCipherChunks(decodedCiphertext);
                case "INDEPENDENT_BLOCKS_GUESS" -> splitCipherChunks(decodedCiphertext, blockSize);
                default -> List.of();
            };

            if (chunks.isEmpty()) {
                return new PaddingEvidence(-8, "padAdj=-8 (no chunks to validate)");
            }

            int blockCipherSize = getBlockCipherSize(combo.algorithm);
            int totalAdj = 0;
            int pkcsPatternCount = 0;
            int checked = 0;

            for (byte[] chunk : chunks) {
                if (chunk.length == 0 || chunk.length % blockCipherSize != 0) {
                    totalAdj -= 12;
                    continue;
                }

                byte[] rawPadded = decryptBlockChunkNoPadding(chunk, key, combo.algorithm, combo.mode);
                checked++;

                boolean pkcsPattern = isPkcsPaddingPattern(rawPadded, blockCipherSize);
                boolean iso7816Pattern = isIso7816PaddingPattern(rawPadded, blockCipherSize);
                int trailingZeros = countTrailingZeroBytes(rawPadded);
                if (pkcsPattern) {
                    pkcsPatternCount++;
                }

                switch (combo.padding) {
                    case "PKCS5Padding":
                    case "PKCS7Padding":
                        totalAdj += pkcsPattern ? 18 : -35;
                        break;
                    case "ISO10126Padding":
                        if (!hasValidPadLength(rawPadded, blockCipherSize)) {
                            totalAdj -= 30;
                        } else if (pkcsPattern) {
                            totalAdj -= 18;
                        } else {
                            totalAdj += 8;
                        }
                        break;
                    case "ISO7816-4Padding":
                        totalAdj += iso7816Pattern ? 16 : -24;
                        break;
                    case "ZeroBytePadding":
                        totalAdj += trailingZeros > 0 ? Math.min(8, trailingZeros) : -14;
                        break;
                    case "NoPadding":
                        totalAdj += pkcsPattern ? -10 : 2;
                        break;
                    default:
                        break;
                }
            }

            int adjustment = checked > 0 ? Math.round((float) totalAdj / (float) checked) : totalAdj;
            String summary = "padAdj=" + adjustment + " (" + pkcsPatternCount + "/" + Math.max(checked, 1)
                    + " pkcs-like blocks)";
            return new PaddingEvidence(adjustment, summary);

        } catch (Exception e) {
            return new PaddingEvidence(-6, "padAdj=-6 (" + safeErrorMessage(e) + ")");
        }
    }

    private List<byte[]> extractStructuredCipherChunks(byte[] data) {
        try {
            int minHeader = INDEPENDENT_BLOCK_MAGIC.length + 8;
            if (data.length < minHeader) {
                return List.of();
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] magic = new byte[INDEPENDENT_BLOCK_MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, INDEPENDENT_BLOCK_MAGIC)) {
                return List.of();
            }

            buffer.getInt(); // stored block size
            int blockCount = buffer.getInt();
            if (blockCount < 0) {
                return List.of();
            }

            List<byte[]> chunks = new ArrayList<>();
            for (int i = 0; i < blockCount; i++) {
                if (buffer.remaining() < 4) {
                    return List.of();
                }
                int len = buffer.getInt();
                if (len <= 0 || len > buffer.remaining()) {
                    return List.of();
                }
                byte[] chunk = new byte[len];
                buffer.get(chunk);
                chunks.add(chunk);
            }
            return chunks;
        } catch (Exception e) {
            return List.of();
        }
    }

    private int extractStructuredBlockSize(byte[] data) {
        try {
            int minHeader = INDEPENDENT_BLOCK_MAGIC.length + 8;
            if (data == null || data.length < minHeader) {
                return 0;
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] magic = new byte[INDEPENDENT_BLOCK_MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, INDEPENDENT_BLOCK_MAGIC)) {
                return 0;
            }

            int storedBlockSize = buffer.getInt();
            int blockCount = buffer.getInt();
            if (storedBlockSize <= 0 || blockCount < 0) {
                return 0;
            }
            return storedBlockSize;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<byte[]> splitCipherChunks(byte[] data, int blockSize) {
        if (data == null || data.length == 0 || blockSize <= 0) {
            return List.of();
        }
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += blockSize) {
            int end = Math.min(offset + blockSize, data.length);
            chunks.add(Arrays.copyOfRange(data, offset, end));
        }
        return chunks;
    }

    private int getBlockCipherSize(String algorithm) {
        if (algorithm != null && (algorithm.equals("DES") || algorithm.contains("3DES") || algorithm.contains("Triple"))) {
            return 8;
        }
        return 16;
    }

    private byte[] decryptBlockChunkNoPadding(byte[] chunk,
            byte[] key,
            String algorithm,
            String mode) throws Exception {
        byte[] iv = getIVForMode(mode);
        return SymmetricCipher.decrypt(chunk, key, algorithm, mode, "NoPadding", iv, null);
    }

    private boolean hasValidPadLength(byte[] rawPadded, int blockSize) {
        if (rawPadded == null || rawPadded.length == 0) {
            return false;
        }
        int padLen = rawPadded[rawPadded.length - 1] & 0xFF;
        return padLen >= 1 && padLen <= blockSize && padLen <= rawPadded.length;
    }

    private boolean isPkcsPaddingPattern(byte[] rawPadded, int blockSize) {
        if (!hasValidPadLength(rawPadded, blockSize)) {
            return false;
        }
        int padLen = rawPadded[rawPadded.length - 1] & 0xFF;
        for (int i = rawPadded.length - padLen; i < rawPadded.length; i++) {
            if ((rawPadded[i] & 0xFF) != padLen) {
                return false;
            }
        }
        return true;
    }

    private boolean isIso7816PaddingPattern(byte[] rawPadded, int blockSize) {
        if (!hasValidPadLength(rawPadded, blockSize)) {
            return false;
        }
        int markerIndex = -1;
        for (int i = rawPadded.length - 1; i >= Math.max(0, rawPadded.length - blockSize); i--) {
            int v = rawPadded[i] & 0xFF;
            if (v == 0x80) {
                markerIndex = i;
                break;
            }
            if (v != 0x00) {
                return false;
            }
        }
        return markerIndex >= 0;
    }

    private int countTrailingZeroBytes(byte[] data) {
        int count = 0;
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == 0x00) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private PlaintextQuality evaluatePlaintextQuality(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            return new PlaintextQuality(-100, "EMPTY", "Empty plaintext", "");
        }

        boolean utf8Valid = isValidUtf8(plaintext);
        String utf8Text = utf8Valid ? new String(plaintext, StandardCharsets.UTF_8) : "";
        double utf8Printable = utf8Valid ? computePrintableRatio(utf8Text) : 0.0;

        String cp037Text = new String(plaintext, CHARSET_EBCDIC_CP037);
        String cp500Text = new String(plaintext, CHARSET_EBCDIC_CP500);
        double cp037Printable = computePrintableRatio(cp037Text);
        double cp500Printable = computePrintableRatio(cp500Text);
        double bestEbcdic = Math.max(cp037Printable, cp500Printable);
        String bestEbcdicName = cp037Printable >= cp500Printable ? "EBCDIC_CP037_TEXT" : "EBCDIC_CP500_TEXT";

        boolean likelyHex = false;
        boolean likelyBase64 = false;
        if (utf8Valid) {
            String compact = utf8Text.replaceAll("\\s+", "");
            likelyHex = compact.length() >= 16 && compact.length() % 2 == 0
                    && compact.matches("[0-9A-Fa-f]+");
            likelyBase64 = compact.length() >= 16 && compact.length() % 4 == 0
                    && compact.matches("[A-Za-z0-9+/=]+");
            if (likelyBase64) {
                try {
                    Base64.getDecoder().decode(compact);
                } catch (Exception e) {
                    likelyBase64 = false;
                }
            }
        }

        double controlRatio = computeControlByteRatio(plaintext);
        int score = 0;
        if (utf8Valid) {
            score += (int) Math.round(utf8Printable * 60.0);
        }
        if (likelyHex) {
            score += 35;
        }
        if (likelyBase64) {
            score += 28;
        }
        if (bestEbcdic > 0.65) {
            score += (int) Math.round((bestEbcdic - 0.65) * 80.0);
        }
        score -= (int) Math.round(controlRatio * 35.0);

        String inferredEncoding;
        String preview;
        if (likelyHex) {
            inferredEncoding = "HEX_TEXT";
            preview = safePreview(utf8Text);
        } else if (likelyBase64) {
            inferredEncoding = "BASE64_TEXT";
            preview = safePreview(utf8Text);
        } else if (utf8Valid && utf8Printable >= 0.72) {
            inferredEncoding = "UTF8_TEXT";
            preview = safePreview(utf8Text);
        } else if (bestEbcdic >= 0.72) {
            inferredEncoding = bestEbcdicName;
            preview = safePreview(cp037Printable >= cp500Printable ? cp037Text : cp500Text);
        } else {
            inferredEncoding = "BINARY_OR_UNKNOWN";
            preview = DataConverter.bytesToHex(Arrays.copyOf(plaintext, Math.min(64, plaintext.length)));
        }

        String summary = String.format(
                Locale.ROOT,
                "utf8Printable=%.2f, ebcdicPrintable=%.2f, controlRatio=%.2f",
                utf8Printable,
                bestEbcdic,
                controlRatio);

        return new PlaintextQuality(score, inferredEncoding, summary, preview);
    }

    private boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private double computePrintableRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        int printable = 0;
        int total = text.length();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean isPrintable = ch == '\n' || ch == '\r' || ch == '\t'
                    || (ch >= 32 && ch <= 126)
                    || Character.isLetterOrDigit(ch)
                    || Character.isSpaceChar(ch);
            if (isPrintable) {
                printable++;
            }
        }
        return total == 0 ? 0.0 : (double) printable / (double) total;
    }

    private double computeControlByteRatio(byte[] data) {
        if (data == null || data.length == 0) {
            return 1.0;
        }
        int controls = 0;
        for (byte b : data) {
            int value = b & 0xFF;
            if (value < 9 || (value > 13 && value < 32) || value == 127) {
                controls++;
            }
        }
        return (double) controls / (double) data.length;
    }

    private String safePreview(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        if (compact.length() > 140) {
            return compact.substring(0, 140) + "...";
        }
        return compact;
    }

    private List<AnalysisCandidate> selectTopCandidates(List<AnalysisCandidate> candidates, int maxResults) {
        int limit = maxResults > 0 ? maxResults : 8;
        candidates.sort(Comparator.comparingInt((AnalysisCandidate c) -> c.score).reversed());

        Map<String, AnalysisCandidate> unique = new java.util.LinkedHashMap<>();
        List<AnalysisCandidate> ordered = new ArrayList<>();
        for (AnalysisCandidate candidate : candidates) {
            String signature = candidate.algorithm + "|" + candidate.mode + "|" + canonicalPaddingName(candidate.padding) + "|"
                    + candidate.processing + "|" + candidate.blockSize + "|" + candidate.inputEncoding + "|"
                    + candidate.inferredPlainEncoding;
            if (!unique.containsKey(signature)) {
                unique.put(signature, candidate);
                ordered.add(candidate);
                if (ordered.size() >= limit) {
                    break;
                }
            }
        }
        ordered.sort(Comparator.comparingInt((AnalysisCandidate c) -> c.score).reversed());
        return ordered;
    }

    private String canonicalPaddingName(String padding) {
        if ("PKCS5Padding".equals(padding) || "PKCS7Padding".equals(padding)) {
            return "PKCS5_OR_PKCS7";
        }
        return padding;
    }

    private void assignConfidencePercentages(List<AnalysisCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        int maxScore = candidates.stream().mapToInt(c -> c.score).max().orElse(0);
        final double temperature = 6.0;
        double[] weights = new double[candidates.size()];
        double sum = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            double exponent = ((double) candidates.get(i).score - (double) maxScore) / temperature;
            exponent = Math.max(-60.0, Math.min(60.0, exponent));
            double weight = Math.exp(exponent);
            weights[i] = weight;
            sum += weight;
        }

        if (sum <= 0.0 || Double.isNaN(sum) || Double.isInfinite(sum)) {
            double fallback = 100.0 / candidates.size();
            for (AnalysisCandidate candidate : candidates) {
                candidate.confidencePercent = fallback;
            }
            return;
        }

        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).confidencePercent = (weights[i] * 100.0) / sum;
        }
    }

    static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private List<AnalysisCandidate> selectProbableCandidates(List<AnalysisCandidate> topCandidates) {
        if (topCandidates == null || topCandidates.isEmpty()) {
            return List.of();
        }

        AnalysisCandidate best = topCandidates.get(0);
        int threshold = Math.max(best.score - 5, 40);
        List<AnalysisCandidate> probable = new ArrayList<>();
        for (AnalysisCandidate candidate : topCandidates) {
            if (candidate.score >= threshold) {
                probable.add(candidate);
            }
            if (probable.size() >= 3) {
                break;
            }
        }
        return probable;
    }

    private Path createAnalysisDirectory(Path inputFile) throws Exception {
        Path parent = inputFile.toAbsolutePath().getParent();
        if (parent == null) {
            parent = Path.of(".");
        }

        String rawName = inputFile.getFileName() != null ? inputFile.getFileName().toString() : "encrypted_file";
        String safeName = rawName.replaceAll("[^A-Za-z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path analysisDir = parent.resolve("analysis_" + safeName + "_" + timestamp);
        Files.createDirectories(analysisDir);
        return analysisDir;
    }

    private String safeErrorMessage(Exception error) {
        if (error == null || error.getMessage() == null) {
            return "Unknown error";
        }
        return error.getClass().getSimpleName() + ": " + error.getMessage().replace("\n", " ").replace("\r", " ");
    }

    private void writeAttemptLog(Path csvPath, List<AnalysisAttempt> attempts) throws Exception {
        StringBuilder csv = new StringBuilder();
        csv.append("index,algorithm,mode,padding,processing,block_size,input_encoding,success,score,inferred_plain_encoding,preview,error\n");
        for (AnalysisAttempt attempt : attempts) {
            csv.append(attempt.index).append(",");
            csv.append(csvEscape(attempt.algorithm)).append(",");
            csv.append(csvEscape(attempt.mode)).append(",");
            csv.append(csvEscape(attempt.padding)).append(",");
            csv.append(csvEscape(attempt.processing)).append(",");
            csv.append(attempt.blockSize).append(",");
            csv.append(csvEscape(attempt.inputEncoding != null ? attempt.inputEncoding.name() : "")).append(",");
            csv.append(attempt.success).append(",");
            csv.append(attempt.score).append(",");
            csv.append(csvEscape(attempt.inferredPlainEncoding)).append(",");
            csv.append(csvEscape(attempt.preview)).append(",");
            csv.append(csvEscape(attempt.error)).append("\n");
        }
        Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private byte[] getNonceBytes(String algorithm) {
        String ivHex = inputs.ivHex();
        if (ivHex.isEmpty()) {
            throw new IllegalArgumentException(algorithm + " requires an IV/Nonce");
        }
        return DataConverter.hexToBytes(ivHex);
    }

    private byte[] getIVForMode(String mode) {
        if (!SymmetricCipher.requiresIV(mode)) {
            return null;
        }

        String ivHex = inputs.ivHex();
        if (ivHex.isEmpty()) {
            throw new IllegalArgumentException(mode + " mode requires an Initialization Vector (IV)");
        }

        return DataConverter.hexToBytes(ivHex);
    }

    private byte[] getAADBytes() {
        if (!inputs.aadEnabled() || inputs.aadText().isEmpty()) {
            return null;
        }

        String aadText = inputs.aadText().trim();
        try {
            return DataConverter.hexToBytes(aadText);
        } catch (Exception e) {
            return aadText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    private byte[] getTagBytes(boolean required, String algorithmLabel) {
        String tagHex = inputs.tagHex();
        if (tagHex.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(algorithmLabel + " requires an Auth Tag for decryption");
            }
            return null;
        }

        byte[] tag = DataConverter.hexToBytes(tagHex);
        if (tag.length != 16) {
            throw new IllegalArgumentException("Auth Tag must be 16 bytes (32 hex chars)");
        }
        return tag;
    }

    private byte[] appendTag(byte[] ciphertext, byte[] tag) {
        if (tag == null) {
            return ciphertext;
        }
        byte[] combined = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
        System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
        return combined;
    }

    private byte[] decryptSymmetricBytes(byte[] ciphertext, byte[] key, String algorithm, String mode, String padding)
            throws Exception {
        if ("Salsa20".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.decrypt(ciphertext, key, algorithm, "None", "NoPadding", nonce);
        }
        if ("ChaCha20".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.decryptChaCha20(ciphertext, key, nonce);
        }
        if ("ChaCha20-Poly1305".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            byte[] tag = getTagBytes(false, "ChaCha20-Poly1305");
            byte[] combined = tag != null ? SymmetricCipher.combineChaCha20CiphertextAndTag(ciphertext, tag) : ciphertext;
            return SymmetricCipher.decryptChaCha20Poly1305(combined, key, nonce);
        }
        if ("XChaCha20-Poly1305".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            byte[] tag = getTagBytes(false, "XChaCha20-Poly1305");
            byte[] combined = tag != null ? SymmetricCipher.combineChaCha20CiphertextAndTag(ciphertext, tag) : ciphertext;
            return SymmetricCipher.decryptXChaCha20Poly1305(combined, key, nonce);
        }

        byte[] iv = getIVForMode(mode);
        byte[] aadBytes = getAADBytes();
        if ("GCM".equalsIgnoreCase(mode)) {
            byte[] optionalTag = getTagBytes(false, "GCM");
            ciphertext = appendTag(ciphertext, optionalTag);
        }
        return SymmetricCipher.decrypt(ciphertext, key, algorithm, mode, padding, iv, aadBytes);
    }
}
