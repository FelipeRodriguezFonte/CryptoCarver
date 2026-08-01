package com.cryptocarver.service;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesSpanishAndEnglishWithoutChangingTechnicalArguments() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());

        assertEquals("File", service.text("menu.file"));
        service.setPreference(LanguagePreference.ES);
        assertEquals(Locale.forLanguageTag("es"), service.getLocale());
        assertEquals("Archivo", service.text("menu.file"));
        assertEquals("Algoritmo: SHA-256", service.text("technical", "SHA-256"));
        String pem = "-----BEGIN PUBLIC KEY-----";
        assertTrue(service.text("technical", pem).endsWith(pem),
                "PEM and other technical values remain caller-owned data");
        service.setPreference(LanguagePreference.EN);
        assertEquals("File", service.text("menu.file"));
    }

    @Test
    void missingKeyFallsBackToEnglishThenToKey() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, "i18n.testmessages",
                Locale.ENGLISH, getClass().getClassLoader());

        service.setPreference(LanguagePreference.ES);
        assertEquals("English fallback", service.text("known"));
        assertEquals("missing.key", service.text("missing.key"));

        I18nService missingBundle = new I18nService(settings, "i18n.bundle-that-does-not-exist",
                Locale.ENGLISH, getClass().getClassLoader());
        assertEquals("still.safe", missingBundle.text("still.safe"));
    }

    @Test
    void systemPreferenceResolvesSystemLocaleAndNotifiesListeners() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.forLanguageTag("es-ES"), getClass().getClassLoader());
        AtomicReference<Locale> notified = new AtomicReference<>();
        service.addLocaleChangeListener(notified::set);

        assertEquals(LanguagePreference.SYSTEM, service.getPreference());
        assertEquals(Locale.forLanguageTag("es"), service.getLocale());
        service.setPreference(LanguagePreference.EN);
        assertEquals(Locale.ENGLISH, notified.get());
        service.setPreference(LanguagePreference.SYSTEM);
        assertEquals(Locale.forLanguageTag("es"), service.getLocale());
        assertEquals(Locale.forLanguageTag("es"), notified.get());
    }

    @Test
    void persistsAndRestoresPreference() {
        Path file = temporaryDirectory.resolve("settings.json");
        AppSettings settings = new AppSettings(file);
        settings.setLanguagePreference(LanguagePreference.ES);

        AppSettings restored = new AppSettings(file);
        assertEquals(LanguagePreference.ES, restored.getLanguagePreference());
        assertTrue(java.nio.file.Files.exists(file));
    }
}
