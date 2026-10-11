package com.cryptocarver.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/** Reads an explicitly indexed local help reference from the packaged classpath. */
public final class LocalHelpService {
    private final ClassLoader loader;

    public LocalHelpService() { this(LocalHelpService.class.getClassLoader()); }
    public LocalHelpService(ClassLoader loader) {
        this.loader = loader == null ? getClass().getClassLoader() : loader;
    }

    /** Loads {@code help/{language}/{referenceId}.md}; IDs are never interpreted as headings or paths. */
    public Optional<String> readReference(String referenceId, Locale locale) {
        if (referenceId == null || !referenceId.matches("[A-Za-z0-9._-]+") || referenceId.contains("..")) {
            return Optional.empty();
        }
        String language = locale != null && "es".equalsIgnoreCase(locale.getLanguage()) ? "es" : "en";
        String path = "help/" + language + "/" + referenceId + ".md";
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) return Optional.empty();
            return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
