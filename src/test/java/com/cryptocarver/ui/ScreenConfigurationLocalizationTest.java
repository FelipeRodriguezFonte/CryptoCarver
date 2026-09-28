package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenConfigurationLocalizationTest {
    private static final Path SOURCE =
            Path.of("src/main/java/com/cryptocarver/ui/ScreenConfigurationCoordinator.java");

    /** Every literal key the coordinator passes to i18n.text, read from its source. */
    private static Set<String> keysUsedByCoordinator() throws Exception {
        Matcher matcher = Pattern.compile("i18n\\.text\\(\"([^\"]+)\"").matcher(Files.readString(SOURCE));
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) keys.add(matcher.group(1));
        return keys;
    }

    @Test
    void configurationDialogAndStatusKeysExistInEnglishAndSpanishBundles() throws Exception {
        Set<String> keys = keysUsedByCoordinator();
        assertFalse(keys.isEmpty(), "No i18n keys found in " + SOURCE);
        ResourceBundle english = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        ResourceBundle spanish = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("es"));
        for (String key : keys) {
            assertTrue(english.containsKey(key), "English bundle is missing " + key);
            assertTrue(spanish.containsKey(key), "Spanish bundle is missing " + key);
        }
    }
}
