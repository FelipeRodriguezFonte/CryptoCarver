package com.cryptocarver.crypto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts explicitly written hexadecimal payload bytes from common terminal
 * and tcpdump-style dumps.  It deliberately recognises only compact runs of
 * at least four bytes or runs written as byte pairs, and strips a conventional
 * hexadecimal line offset before scanning.  It does not claim to decode a
 * packet capture format or infer protocol framing.
 */
public final class TraceHexExtractor {
    private static final Pattern OFFSET = Pattern.compile("^\\s*(?:0x)?[0-9A-Fa-f]{1,8}\\s*:\\s*");
    private static final Pattern COMPACT = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{8,}(?![0-9a-f])");
    private static final Pattern PAIR = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{2}(?![0-9a-f])");

    private TraceHexExtractor() { }

    public record Extraction(String hex, int byteCount, List<String> warnings) {
        public Extraction { warnings = List.copyOf(warnings); }
    }

    public static Extraction extract(String trace) {
        if (trace == null || trace.isBlank()) throw new IllegalArgumentException("Trace is empty");
        List<String> bytes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String rawLine : trace.split("\\R")) {
            String line = OFFSET.matcher(rawLine).replaceFirst("");
            Matcher compact = COMPACT.matcher(line);
            boolean foundCompact = false;
            while (compact.find()) {
                foundCompact = true;
                String token = compact.group();
                for (int i = 0; i < token.length(); i += 2) bytes.add(token.substring(i, i + 2));
            }
            if (!foundCompact) {
                Matcher pairs = PAIR.matcher(line);
                List<String> linePairs = new ArrayList<>();
                while (pairs.find()) linePairs.add(pairs.group());
                if (linePairs.size() >= 4) bytes.addAll(linePairs);
                else if (!linePairs.isEmpty()) warnings.add("Ignored a short hex-like run: " + rawLine.trim());
            }
        }
        if (bytes.isEmpty()) throw new IllegalArgumentException("No explicit hexadecimal payload bytes found");
        return new Extraction(String.join("", bytes).toUpperCase(Locale.ROOT), bytes.size(), warnings);
    }
}
