package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import java.nio.file.Path;
import java.util.Map;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleTextCatalogTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void eachUx15aModuleResolvesEnglishAndSpanish() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());
        assertModule(service, ModuleTextCatalog.cipher(), "Data Encryption & Decryption", "Cifrado y descifrado de datos");
        assertModule(service, ModuleTextCatalog.authentication(), "1. Data to authenticate", "1. Datos que autenticar");
        assertModule(service, ModuleTextCatalog.keys(), "🎛 Key Lab", "🎛 Laboratorio de claves");
        assertModule(service, ModuleTextCatalog.certificates(), "📜 Generate Certificate", "📜 Generar certificado");
        assertModule(service, ModuleTextCatalog.generic(), "🔐 Hashing", "🔐 Hashing");
    }

    @Test
    void technicalValueRemainsIdenticalAcrossLanguageChange() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());
        String technical = "SHA-256|A1B2C3|-----BEGIN PUBLIC KEY-----";

        service.setPreference(LanguagePreference.EN);
        String english = service.text("technical", technical);
        service.setPreference(LanguagePreference.ES);
        String spanish = service.text("technical", technical);

        assertTrue(english.endsWith(technical));
        assertTrue(spanish.endsWith(technical));
        assertEquals(technical, english.substring(english.indexOf(": ") + 2));
        assertEquals(technical, spanish.substring(spanish.indexOf(": ") + 2));
    }

    private void assertModule(I18nService service, Map<String, String> catalog,
                              String sourceText, String spanishText) {
        String key = catalog.get(sourceText);
        assertTrue(key != null && !key.isBlank(), "Catalog key missing for " + sourceText);
        service.setPreference(LanguagePreference.EN);
        String english = service.text(key);
        service.setPreference(LanguagePreference.ES);
        String spanish = service.text(key);
        assertNotEquals(sourceText, key);
        assertEquals(sourceText, english);
        assertEquals(spanishText, spanish);
    }
}
