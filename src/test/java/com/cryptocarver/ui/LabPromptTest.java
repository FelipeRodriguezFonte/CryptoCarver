package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabPromptTest {
    @TempDir Path temporaryDirectory;

    @AfterEach
    void resetSettings() {
        AppSettings.resetInstanceForTesting();
    }

    @ParameterizedTest(name = "{0} follows the laboratory visibility profile")
    @EnumSource(LabPrompt.class)
    void everyPromptIsHiddenInFullLabAndShownInMasked(LabPrompt prompt) {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        AppSettings.setInstanceForTesting(settings);

        settings.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        assertTrue(AppSettings.isFullLab());
        assertFalse(prompt.shouldShow(), prompt.name());

        settings.setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);
        assertFalse(AppSettings.isFullLab());
        assertTrue(prompt.shouldShow(), prompt.name());
    }

    @Test
    void plainConfigurationOptionHasTheRequestedTranslations() throws Exception {
        assertEquals("Unencrypted JSON", value("messages.properties"));
        assertEquals("Unencrypted JSON", value("messages_en.properties"));
        assertEquals("JSON sin cifrar", value("messages_es.properties"));
    }

    private String value(String bundle) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/i18n/" + bundle)) {
            assertTrue(input != null, bundle);
            properties.load(input);
        }
        return properties.getProperty("dialog.configuration.unencryptedJson");
    }
}
