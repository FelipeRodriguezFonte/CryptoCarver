package com.cryptoforge.utils;

import com.cryptoforge.util.DataConverter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * File Converter Utility
 *
 * Supports conversion between:
 * - Binary files ↔ Hexadecimal
 * - Binary files ↔ Base64
 * - Text encodings (ASCII, UTF-8)
 *
 * @author Felipe
 */
public class FileConverter {

    /**
     * Read binary file and convert to hexadecimal string
     *
     * @param filePath Path to binary file
     * @return Hexadecimal representation of file contents
     */
    public static String binaryFileToHex(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        return DataConverter.bytesToHex(fileBytes);
    }

    /**
     * Read binary file and convert to Base64 string
     *
     * @param filePath Path to binary file
     * @return Base64 representation of file contents
     */
    public static String binaryFileToBase64(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        return Base64.getEncoder().encodeToString(fileBytes);
    }

    /**
     * Convert hexadecimal string to binary file
     *
     * @param hexData Hexadecimal string
     * @param outputPath Output file path
     */
    public static void hexToBinaryFile(String hexData, String outputPath) throws IOException {
        byte[] fileBytes = DataConverter.hexToBytes(hexData);
        Files.write(Paths.get(outputPath), fileBytes);
    }

    /**
     * Convert Base64 string to binary file
     *
     * @param base64Data Base64 string
     * @param outputPath Output file path
     */
    public static void base64ToBinaryFile(String base64Data, String outputPath) throws IOException {
        byte[] fileBytes = Base64.getDecoder().decode(base64Data);
        Files.write(Paths.get(outputPath), fileBytes);
    }

    /**
     * Read text file with specified encoding
     *
     * @param filePath Path to text file
     * @param encoding Character encoding (ASCII, UTF-8, etc.)
     * @return Text content
     */
    public static String readTextFile(String filePath, String encoding) throws IOException {
        Charset charset = getCharset(encoding);
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        return new String(fileBytes, charset);
    }

    /**
     * Write text to file with specified encoding
     *
     * @param text Text content
     * @param outputPath Output file path
     * @param encoding Character encoding (ASCII, UTF-8, etc.)
     */
    public static void writeTextFile(String text, String outputPath, String encoding) throws IOException {
        Charset charset = getCharset(encoding);
        byte[] textBytes = text.getBytes(charset);
        Files.write(Paths.get(outputPath), textBytes);
    }

    /**
     * Convert text file encoding (e.g., ASCII to UTF-8)
     *
     * @param inputPath Input file path
     * @param outputPath Output file path
     * @param inputEncoding Source encoding
     * @param outputEncoding Target encoding
     */
    public static void convertTextEncoding(
            String inputPath,
            String outputPath,
            String inputEncoding,
            String outputEncoding) throws IOException {

        String text = readTextFile(inputPath, inputEncoding);
        writeTextFile(text, outputPath, outputEncoding);
    }

    /**
     * Binary file to text (interpret as ASCII/UTF-8)
     *
     * @param filePath Path to binary file
     * @param encoding Encoding to interpret as (ASCII, UTF-8)
     * @return Text interpretation
     */
    public static String binaryFileToText(String filePath, String encoding) throws IOException {
        return readTextFile(filePath, encoding);
    }

    /**
     * Text to binary file
     *
     * @param text Text content
     * @param outputPath Output file path
     * @param encoding Encoding (ASCII, UTF-8)
     */
    public static void textToBinaryFile(String text, String outputPath, String encoding) throws IOException {
        writeTextFile(text, outputPath, encoding);
    }

    /**
     * Hex string to text file
     *
     * @param hexData Hexadecimal string
     * @param outputPath Output file path
     * @param encoding Text encoding for output
     */
    public static void hexToTextFile(String hexData, String outputPath, String encoding) throws IOException {
        byte[] bytes = DataConverter.hexToBytes(hexData);
        Charset charset = getCharset(encoding);
        String text = new String(bytes, charset);
        writeTextFile(text, outputPath, encoding);
    }

    /**
     * Base64 string to text file
     *
     * @param base64Data Base64 string
     * @param outputPath Output file path
     * @param encoding Text encoding for output
     */
    public static void base64ToTextFile(String base64Data, String outputPath, String encoding) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64Data);
        Charset charset = getCharset(encoding);
        String text = new String(bytes, charset);
        writeTextFile(text, outputPath, encoding);
    }

    /**
     * Get file size information
     *
     * @param filePath File path
     * @return Formatted string with file size in bytes, KB, MB
     */
    public static String getFileSizeInfo(String filePath) throws IOException {
        File file = new File(filePath);
        long sizeBytes = file.length();
        double sizeKB = sizeBytes / 1024.0;
        double sizeMB = sizeKB / 1024.0;

        StringBuilder info = new StringBuilder();
        info.append("File: ").append(file.getName()).append("\n");
        info.append("Size: ").append(sizeBytes).append(" bytes\n");
        info.append("      ").append(String.format("%.2f", sizeKB)).append(" KB\n");
        info.append("      ").append(String.format("%.2f", sizeMB)).append(" MB\n");

        return info.toString();
    }

    /**
     * Hex dump of file (like hexdump -C)
     *
     * @param filePath File path
     * @param maxBytes Maximum bytes to display (0 = all)
     * @return Formatted hex dump
     */
    public static String hexDump(String filePath, int maxBytes) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));

        int bytesToShow = (maxBytes > 0 && maxBytes < fileBytes.length) ? maxBytes : fileBytes.length;

        StringBuilder dump = new StringBuilder();
        dump.append("Hex Dump of: ").append(Paths.get(filePath).getFileName()).append("\n");
        dump.append("Size: ").append(fileBytes.length).append(" bytes");
        if (maxBytes > 0 && maxBytes < fileBytes.length) {
            dump.append(" (showing first ").append(maxBytes).append(" bytes)");
        }
        dump.append("\n\n");

        dump.append("Offset    Hex                                              ASCII\n");
        dump.append("--------  -----------------------------------------------  ----------------\n");

        for (int i = 0; i < bytesToShow; i += 16) {
            // Offset
            dump.append(String.format("%08X  ", i));

            // Hex bytes
            for (int j = 0; j < 16; j++) {
                if (i + j < bytesToShow) {
                    dump.append(String.format("%02X ", fileBytes[i + j]));
                } else {
                    dump.append("   ");
                }
                if (j == 7) dump.append(" ");
            }

            dump.append(" ");

            // ASCII representation
            for (int j = 0; j < 16 && i + j < bytesToShow; j++) {
                byte b = fileBytes[i + j];
                if (b >= 32 && b < 127) {
                    dump.append((char) b);
                } else {
                    dump.append(".");
                }
            }

            dump.append("\n");
        }

        if (maxBytes > 0 && fileBytes.length > maxBytes) {
            dump.append("\n... ").append(fileBytes.length - maxBytes).append(" more bytes ...\n");
        }

        return dump.toString();
    }

    /**
     * Analyze file and detect if it's text or binary
     *
     * @param filePath File path
     * @return Analysis result
     */
    public static String analyzeFile(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));

        StringBuilder analysis = new StringBuilder();
        analysis.append("File Analysis\n");
        analysis.append("=============\n\n");

        File file = new File(filePath);
        analysis.append("Name: ").append(file.getName()).append("\n");
        analysis.append("Size: ").append(fileBytes.length).append(" bytes\n\n");

        // Detect if likely text or binary
        int textChars = 0;
        int binaryChars = 0;

        int sampleSize = Math.min(1024, fileBytes.length); // Sample first 1KB
        for (int i = 0; i < sampleSize; i++) {
            byte b = fileBytes[i];
            if ((b >= 32 && b < 127) || b == '\n' || b == '\r' || b == '\t') {
                textChars++;
            } else if (b == 0) {
                binaryChars += 2; // Null bytes strongly indicate binary
            } else {
                binaryChars++;
            }
        }

        double textRatio = (double) textChars / sampleSize;

        analysis.append("Type Detection (first 1KB):\n");
        analysis.append("  Text characters: ").append(textChars).append(" (").append(String.format("%.1f%%", textRatio * 100)).append(")\n");
        analysis.append("  Binary/Control: ").append(binaryChars).append("\n");

        if (textRatio > 0.85) {
            analysis.append("  Verdict: LIKELY TEXT FILE\n\n");

            // Try to detect encoding
            try {
                String asUtf8 = new String(fileBytes, StandardCharsets.UTF_8);
                String asAscii = new String(fileBytes, StandardCharsets.US_ASCII);

                if (asUtf8.equals(asAscii)) {
                    analysis.append("  Encoding: Likely ASCII\n");
                } else {
                    analysis.append("  Encoding: Likely UTF-8 or extended ASCII\n");
                }
            } catch (Exception e) {
                analysis.append("  Encoding: Unknown\n");
            }
        } else {
            analysis.append("  Verdict: LIKELY BINARY FILE\n");
        }

        return analysis.toString();
    }

    /**
     * Get Charset from string name
     */
    private static Charset getCharset(String encoding) {
        switch (encoding.toUpperCase()) {
            case "ASCII":
            case "US-ASCII":
                return StandardCharsets.US_ASCII;
            case "UTF-8":
            case "UTF8":
                return StandardCharsets.UTF_8;
            case "UTF-16":
            case "UTF16":
                return StandardCharsets.UTF_16;
            case "ISO-8859-1":
            case "LATIN1":
                return StandardCharsets.ISO_8859_1;
            default:
                return Charset.forName(encoding);
        }
    }

    /**
     * Batch convert multiple files
     *
     * @param inputFiles Array of input file paths
     * @param outputDir Output directory
     * @param operation Operation: "TO_HEX", "TO_BASE64", "TO_BINARY_FROM_HEX", "TO_BINARY_FROM_BASE64"
     * @return Summary of conversions
     */
    public static String batchConvert(String[] inputFiles, String outputDir, String operation) throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("Batch Conversion Results\n");
        summary.append("========================\n\n");
        summary.append("Operation: ").append(operation).append("\n");
        summary.append("Output Directory: ").append(outputDir).append("\n");
        summary.append("Files processed: ").append(inputFiles.length).append("\n\n");

        int successful = 0;
        int failed = 0;

        for (String inputFile : inputFiles) {
            try {
                File file = new File(inputFile);
                String fileName = file.getName();
                String outputPath = outputDir + File.separator + fileName;

                switch (operation) {
                    case "TO_HEX":
                        String hex = binaryFileToHex(inputFile);
                        Files.writeString(Paths.get(outputPath + ".hex"), hex);
                        break;
                    case "TO_BASE64":
                        String base64 = binaryFileToBase64(inputFile);
                        Files.writeString(Paths.get(outputPath + ".b64"), base64);
                        break;
                    case "TO_BINARY_FROM_HEX":
                        String hexInput = Files.readString(Paths.get(inputFile));
                        hexToBinaryFile(hexInput, outputPath + ".bin");
                        break;
                    case "TO_BINARY_FROM_BASE64":
                        String base64Input = Files.readString(Paths.get(inputFile));
                        base64ToBinaryFile(base64Input, outputPath + ".bin");
                        break;
                }

                successful++;
                summary.append("✓ ").append(fileName).append("\n");

            } catch (Exception e) {
                failed++;
                summary.append("✗ ").append(inputFiles[successful + failed - 1]).append(" - ").append(e.getMessage()).append("\n");
            }
        }

        summary.append("\nSummary: ").append(successful).append(" successful, ").append(failed).append(" failed\n");

        return summary.toString();
    }
}
