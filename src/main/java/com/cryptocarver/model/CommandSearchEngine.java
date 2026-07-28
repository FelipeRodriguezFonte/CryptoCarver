package com.cryptocarver.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

        String normalized = query.trim().toLowerCase();
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
        String title = cmd.getTitle().toLowerCase();
        String category = cmd.getCategory().toLowerCase();
        String description = cmd.getDescription().toLowerCase();

        // Exact title match
        if (title.equals(q)) {
            return 1000;
        }

        // Title starts with query
        if (title.startsWith(q)) {
            return 800;
        }

        // Title contains word starting with query
        String[] titleWords = title.split("\\s+");
        for (String word : titleWords) {
            if (word.startsWith(q)) {
                return 600;
            }
        }

        // Title contains query anywhere
        if (title.contains(q)) {
            return 500;
        }

        // Category matches or starts with query
        if (category.equals(q) || category.startsWith(q)) {
            return 400;
        }

        // Keyword starts with query
        for (String kw : cmd.getKeywords()) {
            String kwLower = kw.toLowerCase();
            if (kwLower.equals(q)) {
                return 350;
            }
            if (kwLower.startsWith(q)) {
                return 300;
            }
        }

        // Keyword contains query
        for (String kw : cmd.getKeywords()) {
            if (kw.toLowerCase().contains(q)) {
                return 250;
            }
        }

        // Description contains word starting with query
        if (description.contains(q)) {
            return 100;
        }

        return 0;
    }
}
