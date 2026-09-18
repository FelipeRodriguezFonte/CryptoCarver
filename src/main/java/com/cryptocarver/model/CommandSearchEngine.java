package com.cryptocarver.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Pure search engine for filtering and ranking Command Palette items.
 */
public final class CommandSearchEngine {

    private CommandSearchEngine() {}

    private static final class ScoredCommand {
        final CommandItem item;
        final int score;
        final int originalIndex;

        ScoredCommand(CommandItem item, int score, int originalIndex) {
            this.item = item;
            this.score = score;
            this.originalIndex = originalIndex;
        }
    }

    /**
     * Filters and ranks commands based on search query.
     */
    public static List<CommandItem> search(List<CommandItem> commands, String query) {
        Objects.requireNonNull(commands, "commands must not be null");
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(commands);
        }

        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<ScoredCommand> scoredList = new ArrayList<>();

        for (int i = 0; i < commands.size(); i++) {
            CommandItem cmd = commands.get(i);
            int score = computeScore(cmd, normalized);
            if (score > 0) {
                scoredList.add(new ScoredCommand(cmd, score, i));
            }
        }

        // Sort higher score first, tie-breaker: originalIndex (stable deterministic order)
        scoredList.sort(Comparator.comparingInt((ScoredCommand sc) -> -sc.score)
                .thenComparingInt(sc -> sc.originalIndex));

        return scoredList.stream().map(sc -> sc.item).collect(Collectors.toList());
    }

    private static int computeScore(CommandItem cmd, String q) {
        String title = cmd.getTitle().toLowerCase(Locale.ROOT);
        String category = cmd.getCategory().toLowerCase(Locale.ROOT);
        String description = cmd.getDescription().toLowerCase(Locale.ROOT);

        // Exact title match
        if (title.equals(q)) {
            return 1000;
        }

        // Title starts with query
        if (title.startsWith(q)) {
            return 900;
        }

        // Title contains word starting with query
        String[] titleWords = title.split("\\s+");
        for (String word : titleWords) {
            if (word.startsWith(q)) {
                return 800;
            }
        }

        // Title contains query anywhere
        if (title.contains(q)) {
            return 700;
        }

        // Keywords are the command's aliases. They rank below title matches,
        // but above prose in the description (e.g. "mac" matches HMAC/CMAC).
        for (String kw : cmd.getKeywords()) {
            String kwLower = kw.toLowerCase(Locale.ROOT);
            if (kwLower.equals(q)) {
                return 600;
            }
            if (kwLower.startsWith(q) || containsWordStartingWith(kwLower, q)) {
                return 550;
            }
            if (kwLower.contains(q)) {
                return 250;
            }
        }

        // Description is deliberately the lowest-priority searchable field.
        if (description.contains(q) || containsWordStartingWith(description, q)) {
            return 200;
        }

        // Keep category search as a backwards-compatible, lowest-priority
        // fallback for commands built by older callers.
        if (category.contains(q)) {
            return 100;
        }

        return 0;
    }

    private static boolean containsWordStartingWith(String value, String query) {
        for (String word : value.split("[^\\p{L}\\p{N}]+")) {
            if (word.startsWith(query)) return true;
        }
        return false;
    }
}
