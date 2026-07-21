package com.cryptoforge.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build metadata filtered by Maven from the single project version. */
public final class BuildInfo {
    private static final String RESOURCE = "/cryptocarver-build.properties";
    private static final Properties PROPERTIES = load();

    private BuildInfo() { }

    public static String name() {
        return PROPERTIES.getProperty("name", "CryptoCarver");
    }

    public static String version() {
        return PROPERTIES.getProperty("version", "development");
    }

    public static String javaRelease() {
        return PROPERTIES.getProperty("java.release", "Not reported");
    }

    public static String channel() {
        return PROPERTIES.getProperty("channel", "laboratory");
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // A source-tree launch remains usable even if a resource is absent.
        }
        return properties;
    }
}
