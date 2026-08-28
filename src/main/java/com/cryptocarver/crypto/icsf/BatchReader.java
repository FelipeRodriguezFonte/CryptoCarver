package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cuts a pasted block of text into tokens.
 *
 * <p>Blocks are separated by blank lines; lines whose first non-blank character is
 * {@code #} are dropped without breaking the block. Any line may carry a label
 * ahead of the hex.</p>
 */
public final class BatchReader {

    private BatchReader() { }

    /**
     * Separators that ALWAYS mark a label ahead of the hex. Comma and colon are not
     * unconditional, because they are legitimate hex separators too ("0x01,0xAF",
     * "01:AF"): with those, the left-hand side has to be non-hexadecimal.
     */
    private static final char[] STRONG_LABEL_SEPARATORS = {'\t', '|', ';'};
    private static final char[] WEAK_LABEL_SEPARATORS = {',', ':'};

    private static final Pattern HEXISH = Pattern.compile("^(?:0x|0X)?[0-9A-Fa-f]+$");

    /** One line of a block: its input line number and its content. */
    private record Line(int number, String content) { }

    /** A line already split into label and hex. */
    private record Split(int number, String label, String hex) { }

    /** One candidate reading of a block. */
    private record Candidate(BatchInputFormat.Resolved format, List<BatchEntry> entries) { }

    /**
     * Splits "LABEL&lt;sep&gt;hex" into label and hex. No label yields {@code ("", line)}.
     *
     * <p>With a tab, {@code |} or {@code ;} the label is always accepted. With a
     * comma, a colon or a space, the left-hand side has to be non-hexadecimal,
     * because those three are also valid separators INSIDE the hex.</p>
     *
     * <p>Consequence worth knowing: a label made only of hex digits (say
     * {@code ABCDEF}) needs a tab, {@code |} or {@code ;}.</p>
     */
    public static String[] splitLabel(String line) {
        for (char separator : STRONG_LABEL_SEPARATORS) {
            int at = line.indexOf(separator);
            if (at >= 0) {
                return new String[]{line.substring(0, at).strip(), line.substring(at + 1).strip()};
            }
        }
        for (char separator : WEAK_LABEL_SEPARATORS) {
            int at = line.indexOf(separator);
            if (at >= 0) {
                String left = line.substring(0, at).strip();
                if (!left.isEmpty() && !isHexish(left)) {
                    return new String[]{left, line.substring(at + 1).strip()};
                }
            }
        }
        String[] parts = line.strip().split("\\s+", 2);
        if (parts.length == 2 && !isHexish(parts[0]) && isHexish(parts[1])) {
            return new String[]{parts[0].strip(), parts[1].strip()};
        }
        return new String[]{"", line.strip()};
    }

    /** True when the text is nothing but hex digits, allowing the usual separators. */
    static boolean isHexish(String text) {
        String stripped = text.strip();
        for (String junk : new String[]{"0x", "0X", ",", ":", "-", " ", "\t"}) {
            stripped = stripped.replace(junk, "");
        }
        return !stripped.isEmpty() && HEXISH.matcher(stripped).matches();
    }

    /**
     * Reads the input into tokens.
     *
     * @param text   the pasted input
     * @param format {@link BatchInputFormat#AUTO} decides block by block; the other
     *               two force a reading so an analyst can override a bad guess
     */
    public static List<BatchEntry> read(String text, BatchInputFormat format) {
        List<BatchEntry> entries = new ArrayList<>();
        int index = 0;

        for (List<Line> block : blocks(text)) {
            List<Candidate> candidates = candidates(block);
            Candidate chosen;

            if (format == BatchInputFormat.LINE) {
                chosen = candidates.get(0);
            } else if (format == BatchInputFormat.TWO_ROW) {
                chosen = null;
                for (Candidate candidate : candidates) {
                    if (candidate.format() == BatchInputFormat.Resolved.TWO_ROW) {
                        chosen = candidate;
                        break;
                    }
                }
                if (chosen == null) {
                    // An odd number of lines cannot be paired up.
                    for (Line line : block) {
                        index++;
                        entries.add(new BatchEntry(index, splitLabel(line.content())[0], line.number(), 1,
                                BatchInputFormat.Resolved.TWO_ROW, new byte[0],
                                "Two-row mode needs the lines in pairs (high row + low row) and this "
                                        + "block has " + block.size() + " line(s)."));
                    }
                    continue;
                }
            } else {
                chosen = best(candidates);
            }

            for (BatchEntry entry : chosen.entries()) {
                index++;
                entries.add(new BatchEntry(index, entry.label(), entry.line(), entry.lineCount(),
                        chosen.format(), entry.data(), entry.error()));
            }
        }
        return entries;
    }

    /**
     * Picks the candidate reading that produces real tokens.
     *
     * <p>Ties go to the earliest candidate, which is the order they are generated in
     * (line, then two-row, then whole-block): when nothing parses at all, a report
     * that accounts for the failure line by line is more useful than one that
     * reports a single unreadable blob.</p>
     */
    private static Candidate best(List<Candidate> candidates) {
        Candidate chosen = candidates.get(0);
        int bestScore = score(chosen);
        for (int position = 1; position < candidates.size(); position++) {
            int candidateScore = score(candidates.get(position));
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                chosen = candidates.get(position);
            }
        }
        return chosen;
    }

    /**
     * Scores a candidate reading.
     *
     * <p>A token that analyses and has a recognisable family is worth 2; one that
     * only passes as a NULL token is worth 1, because any string starting with X'00'
     * passes as null and must never beat a real reading; one that does not analyse
     * is worth 0. A candidate with an unreadable entry scores below everything.</p>
     */
    private static int score(Candidate candidate) {
        int total = 0;
        for (BatchEntry entry : candidate.entries()) {
            if (entry.data().length == 0) return -1;
            ParseResult result = IcsfTokenParser.parse(entry.data(), Origin.INFER);
            if (result.isOk()) total += result.tokenFamily().isNullToken() ? 1 : 2;
        }
        return total;
    }

    /** The possible readings of a block, in preference order. */
    private static List<Candidate> candidates(List<Line> block) {
        List<Split> splits = new ArrayList<>(block.size());
        for (Line line : block) {
            String[] parts = splitLabel(line.content());
            splits.add(new Split(line.number(), parts[0], parts[1]));
        }
        List<Candidate> candidates = new ArrayList<>(3);

        // (a) one line = one token
        List<BatchEntry> linear = new ArrayList<>(splits.size());
        for (Split split : splits) {
            linear.add(decodeLinear(split.label(), split.hex(), split.number(), 1,
                    BatchInputFormat.Resolved.LINE));
        }
        candidates.add(new Candidate(BatchInputFormat.Resolved.LINE, linear));

        // (b) two lines = one token (high row + low row)
        if (block.size() >= 2 && block.size() % 2 == 0) {
            List<BatchEntry> paired = new ArrayList<>(splits.size() / 2);
            for (int position = 0; position < splits.size(); position += 2) {
                Split high = splits.get(position);
                Split low = splits.get(position + 1);
                String label = high.label().isEmpty() ? low.label() : high.label();
                byte[] data = new byte[0];
                String error = "";
                try {
                    data = IcsfHex.deinterleaveTwoRows(high.hex() + "\n" + low.hex());
                } catch (IllegalArgumentException exception) {
                    error = exception.getMessage();
                }
                paired.add(new BatchEntry(0, label, high.number(), 2,
                        BatchInputFormat.Resolved.TWO_ROW, data, error));
            }
            candidates.add(new Candidate(BatchInputFormat.Resolved.TWO_ROW, paired));
        }

        // (c) the whole block = one token (hex stacked one byte per line)
        if (block.size() >= 2) {
            String label = "";
            StringBuilder joined = new StringBuilder();
            for (Split split : splits) {
                if (label.isEmpty() && !split.label().isEmpty()) label = split.label();
                joined.append(split.hex());
            }
            BatchEntry entry = decodeLinear(label, joined.toString(), block.get(0).number(),
                    block.size(), BatchInputFormat.Resolved.BLOCK);
            candidates.add(new Candidate(BatchInputFormat.Resolved.BLOCK, List.of(entry)));
        }

        return candidates;
    }

    private static BatchEntry decodeLinear(String label, String hex, int line, int lineCount,
                                           BatchInputFormat.Resolved format) {
        try {
            return new BatchEntry(0, label, line, lineCount, format, IcsfHex.clean(hex), "");
        } catch (IllegalArgumentException exception) {
            return new BatchEntry(0, label, line, lineCount, format, new byte[0], exception.getMessage());
        }
    }

    /**
     * Groups lines with content into blocks separated by blank lines.
     *
     * <p>Comments are dropped, and dropping one does not end the block it sits in.</p>
     */
    private static List<List<Line>> blocks(String text) {
        List<List<Line>> blocks = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        if (text == null) return blocks;

        int number = 0;
        for (String raw : text.split("\\R", -1)) {
            number++;
            String line = raw.strip();
            if (line.isEmpty()) {
                if (!current.isEmpty()) {
                    blocks.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            if (line.startsWith("#")) continue;
            current.add(new Line(number, line));
        }
        if (!current.isEmpty()) blocks.add(current);
        return blocks;
    }
}
