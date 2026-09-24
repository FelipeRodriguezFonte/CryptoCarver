package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationExportChoiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void encryptedChoiceUsesTheEncryptedPathInSpanishAndEnglish() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService i18n = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());

        i18n.setPreference(LanguagePreference.ES);
        String encryptedOption = i18n.text("dialog.configuration.encryptedOption");
        String selectedOption = List.of(encryptedOption, i18n.text("dialog.configuration.plainJsonUnsafe")).get(0);
        assertEquals("Cifrado (.ccconfig)", selectedOption);
        assertTrue(ModernMainController.isEncryptedConfigurationOption(selectedOption, encryptedOption));
        assertFalse(ModernMainController.isEncryptedConfigurationOption(
                i18n.text("dialog.configuration.plainJsonUnsafe"), encryptedOption));

        i18n.setPreference(LanguagePreference.EN);
        encryptedOption = i18n.text("dialog.configuration.encryptedOption");
        assertEquals("Encrypted (.ccconfig)", encryptedOption);
        assertTrue(ModernMainController.isEncryptedConfigurationOption(encryptedOption, encryptedOption));
    }
}
