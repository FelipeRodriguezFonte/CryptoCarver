package com.cryptocarver.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Reads contextual help from bundled markdown only; it never performs network I/O. */
public final class LocalHelpService {
    private final ClassLoader loader;
    public LocalHelpService() { this(LocalHelpService.class.getClassLoader()); }
    public LocalHelpService(ClassLoader loader) { this.loader = loader == null ? getClass().getClassLoader() : loader; }

    public String readGuide(Locale locale) {
        String language = locale != null && "es".equalsIgnoreCase(locale.getLanguage()) ? "es" : "en";
        String path = "docs/guide_" + language + "_extended.md";
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) return "";
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) { return ""; }
    }

    /** Returns the markdown section whose heading contains the requested topic. */
    public String section(String topic, Locale locale) {
        if (topic == null || topic.isBlank()) return "";
        String[] lines = readGuide(locale).split("\\R", -1);
        String needle = topic.trim().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(); boolean collecting = false; int level = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.startsWith("#")) {
                int current = 0; while (current < line.length() && line.charAt(current) == '#') current++;
                String heading = line.substring(current).trim().toLowerCase(Locale.ROOT);
                if (!collecting && heading.contains(needle)) { collecting = true; level = current; }
                else if (collecting && current <= level) break;
            }
            if (collecting) result.append(line).append('\n');
        }
        return result.toString().trim();
    }
}
