package com.cryptocarver.service;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.ThemePreference;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PreferencesServiceTest {
    @Test void preferencesRoundTripAndClampValues() throws Exception {
        Path file = Files.createTempFile("cryptocarver-preferences", ".json");
        try {
            AppSettings settings = new AppSettings(file);
            PreferencesService service = new PreferencesService(settings);
            service.update(new PreferencesService.Preferences(null, ThemePreference.DARK, "cipher", true,
                    3, true, SecretVisibilityProfile.REDACTED, 99999, false, 99999, true, " https://tsa ", 999, " http://proxy "));
            var value = service.read();
            assertEquals(ThemePreference.DARK, value.theme());
            assertEquals(1.5, value.textScale());
            assertEquals(3650, value.historyRetentionDays());
            assertEquals(3600, value.clipboardClearSeconds());
            assertEquals(300, value.networkTimeoutSeconds());
            assertEquals("https://tsa", value.tsaUrl());
        } finally { Files.deleteIfExists(file); }
    }
}
