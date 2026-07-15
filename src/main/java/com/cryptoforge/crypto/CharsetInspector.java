package com.cryptoforge.crypto;

import com.cryptoforge.util.DataConverter;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Side-by-side charset interpretations for diagnostic use only. */
public final class CharsetInspector {
    private CharsetInspector() { }

    public record CharsetHeuristics(
            byte[] sampleBytes,
            boolean utf8Valid,
            double asciiScore,
            double latin1Score,
            double windows1252Score,
            double ebcdicScore,
            double controlScore,
            String conclusion
    ) {}

    public static CharsetHeuristics inspect(Path file, int maxBytes, String ebcdicCodePage) throws IOException {
        if (maxBytes <= 0) maxBytes = 256 * 1024; // Default to 256 KiB
        byte[] bytes;
        try (var input = new BufferedInputStream(Files.newInputStream(file))) {
            bytes = input.readNBytes(maxBytes);
        }
        return analyzeHeuristics(bytes, ebcdicCodePage);
    }

    public static CharsetHeuristics inspect(Path file, String ebcdicCodePage) throws IOException {
        return inspect(file, 256 * 1024, ebcdicCodePage);
    }

    public static String compare(byte[] bytes, String ebcdicCodePage) {
        StringBuilder result = new StringBuilder("Charset interpretations (diagnostic, not conclusive)\n\n");
        
        CharsetHeuristics heuristics = analyzeHeuristics(bytes, ebcdicCodePage);
        result.append("Heuristic Analysis:\n");
        result.append(String.format("- Valid UTF-8 Sequence: %s\n", heuristics.utf8Valid() ? "Yes" : "No"));
        result.append(String.format("- ASCII Printable: %.1f%%\n", heuristics.asciiScore()));
        result.append(String.format("- Extended Latin-1: %.1f%%\n", heuristics.latin1Score()));
        result.append(String.format("- Windows-1252: %.1f%%\n", heuristics.windows1252Score()));
        result.append(String.format("- Control Characters: %.1f%%\n", heuristics.controlScore()));
        if (heuristics.ebcdicScore() > 0) {
            result.append(String.format("- EBCDIC Score: %.1f%%\n", heuristics.ebcdicScore()));
        }
        result.append("\nConclusion (Heuristic only): ").append(heuristics.conclusion()).append("\n\n");
        
        result.append("UTF-8: ").append(safeUtf8(bytes)).append('\n');
        result.append("ISO-8859-1: ").append(DataConverter.visualizeBytes(new String(bytes, StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.ISO_8859_1))).append('\n');
        result.append("Windows-1252: ").append(new String(bytes, Charset.forName("windows-1252"))).append('\n');
        if (ebcdicCodePage != null && !ebcdicCodePage.isBlank()) {
            result.append("EBCDIC (").append(ebcdicCodePage).append("): ").append(EBCDICConverter.decode(bytes, ebcdicCodePage)).append('\n');
        }
        return result.toString();
    }

    private static String safeUtf8(byte[] bytes) {
        try { return DataConverter.utf8BytesToString(bytes); }
        catch (IllegalArgumentException e) { return "<invalid UTF-8>"; }
    }

    private static CharsetHeuristics analyzeHeuristics(byte[] bytes, String ebcdicCodePage) {
        if (bytes.length == 0) {
            return new CharsetHeuristics(bytes, true, 0, 0, 0, 0, 0, "Empty buffer. Inconclusive.");
        }
        
        int total = bytes.length;
        int asciiPrintable = 0;
        int extendedLatin = 0;
        int controls = 0;
        
        for (byte b : bytes) {
            int u = b & 0xFF;
            if (u == 0x09 || u == 0x0A || u == 0x0D || (u >= 0x20 && u <= 0x7E)) {
                asciiPrintable++;
            } else if (u >= 0xA0 && u <= 0xFF) {
                extendedLatin++;
            } else {
                controls++;
            }
        }

        boolean isValidUtf8 = true;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (Exception e) {
            isValidUtf8 = false;
        }

        double asciiPct = (asciiPrintable * 100.0) / total;
        double latinPct = (extendedLatin * 100.0) / total;
        double controlPct = (controls * 100.0) / total;
        
        // Compute Windows-1252 score
        double windows1252Pct = 0;
        try {
            String decodedWin = new String(bytes, Charset.forName("windows-1252"));
            int readable = 0;
            for (int i = 0; i < decodedWin.length(); i++) {
                char c = decodedWin.charAt(i);
                if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xFF) || isWin1252Specific(c)) {
                    readable++;
                }
            }
            windows1252Pct = (readable * 100.0) / decodedWin.length();
        } catch (Exception ignored) {}
        
        // Compute EBCDIC Score by attempting decode and counting valid readable EBCDIC chars
        double ebcdicPct = 0;
        if (ebcdicCodePage != null && !ebcdicCodePage.isBlank()) {
            try {
                String decoded = EBCDICConverter.decode(bytes, ebcdicCodePage);
                int readable = 0;
                for (int i = 0; i < decoded.length(); i++) {
                    char c = decoded.charAt(i);
                    if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0x7E)) {
                        readable++;
                    }
                }
                ebcdicPct = (readable * 100.0) / decoded.length();
            } catch (Exception ignored) {}
        }

        String conclusion;
        if (isValidUtf8 && asciiPct > 90.0) {
            conclusion = "Likely standard ASCII or UTF-8 text.";
        } else if (isValidUtf8) {
            conclusion = "Likely valid UTF-8.";
        } else if (ebcdicPct > 50.0 && asciiPct < 50.0) {
            conclusion = "Likely EBCDIC text.";
        } else if (windows1252Pct > latinPct && windows1252Pct > 95.0) {
            conclusion = "Likely Windows-1252.";
        } else if (asciiPct + latinPct > 95.0) {
            conclusion = "Likely ISO-8859-1 or Windows-1252.";
        } else if (controlPct > 30.0) {
            conclusion = "Likely Binary data.";
        } else {
            conclusion = "Inconclusive. Mixed binary/text.";
        }
        
        return new CharsetHeuristics(bytes, isValidUtf8, asciiPct, latinPct, windows1252Pct, ebcdicPct, controlPct, conclusion);
    }

    private static boolean isWin1252Specific(char c) {
        return c == 0x20AC || c == 0x201A || c == 0x0192 || c == 0x201E || c == 0x2026 ||
               c == 0x2020 || c == 0x2021 || c == 0x02C6 || c == 0x2030 || c == 0x0160 ||
               c == 0x2039 || c == 0x0152 || c == 0x017D || c == 0x2018 || c == 0x2019 ||
               c == 0x201C || c == 0x201D || c == 0x2022 || c == 0x2013 || c == 0x2014 ||
               c == 0x02DC || c == 0x2122 || c == 0x0161 || c == 0x203A || c == 0x0153 ||
               c == 0x017E || c == 0x0178;
    }
}
