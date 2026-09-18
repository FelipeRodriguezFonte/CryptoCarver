package com.cryptocarver.ui;

import com.cryptocarver.model.ThemePreference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Resolves the persisted theme choice without making the UI depend on platform internals. */
public final class SystemAppearance {
    private SystemAppearance() {
    }

    public static ThemePreference resolve(ThemePreference preference) {
        if (preference != ThemePreference.SYSTEM) {
            return preference;
        }
        return isDarkMacAppearance() ? ThemePreference.DARK : ThemePreference.LIGHT;
    }

    private static boolean isDarkMacAppearance() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(250, TimeUnit.MILLISECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return false;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return output.trim().equalsIgnoreCase("dark");
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
