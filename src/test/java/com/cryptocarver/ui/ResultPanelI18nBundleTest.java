package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultPanelI18nBundleTest {

    @TempDir
    Path tempDir;

    private static final List<String> RESULT_PANEL_KEYS = List.of(
            "resultPanel.status.success",
            "resultPanel.status.error",
            "resultPanel.status.warning",
            "resultPanel.status.empty",
            "resultPanel.action.copy",
            "resultPanel.action.copy.accessibleText",
            "resultPanel.action.shelf",
            "resultPanel.action.shelf.accessibleText",
            "resultPanel.action.expand",
            "resultPanel.action.expand.accessibleText",
            "resultPanel.action.saveStep",
            "resultPanel.action.saveStep.accessibleText",
            "resultPanel.action.chain",
            "resultPanel.action.chain.accessibleText",
            "resultPanel.format.accessibleText",
            "resultPanel.format.text",
            "resultPanel.format.hex",
            "resultPanel.format.base64",
            "resultPanel.output.output",
            "resultPanel.output.summary",
            "resultPanel.output.defaultOperation",
            "resultPanel.output.accessibleText",
            "resultPanel.feedback.copied"
    );

    @Test
    void allResultPanelKeysExistInBaseBundleAndSpecificBundles() {
        ClassLoader cl = getClass().getClassLoader();
        ResourceBundle baseBundle = ResourceBundle.getBundle("i18n.messages", Locale.ROOT, cl);
        ResourceBundle enBundle = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH, cl);
        ResourceBundle esBundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("es"), cl);

        for (String key : RESULT_PANEL_KEYS) {
            assertTrue(baseBundle.containsKey(key), "Base bundle missing key: " + key);
            assertTrue(enBundle.containsKey(key), "English bundle missing key: " + key);
            assertTrue(esBundle.containsKey(key), "Spanish bundle missing key: " + key);

            assertFalse(baseBundle.getString(key).isBlank(), "Base bundle value blank for key: " + key);
            assertFalse(enBundle.getString(key).isBlank(), "English bundle value blank for key: " + key);
            assertFalse(esBundle.getString(key).isBlank(), "Spanish bundle value blank for key: " + key);
        }
    }

    @Test
    void i18nServiceResolvesResultPanelTranslations() {
        AppSettings settings = new AppSettings(tempDir.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());

        service.setPreference(LanguagePreference.EN);
        org.junit.jupiter.api.Assertions.assertEquals("Copy", service.text("resultPanel.action.copy"));
        org.junit.jupiter.api.Assertions.assertEquals("✓ Success", service.text("resultPanel.status.success"));

        service.setPreference(LanguagePreference.ES);
        org.junit.jupiter.api.Assertions.assertEquals("Copiar", service.text("resultPanel.action.copy"));
        org.junit.jupiter.api.Assertions.assertEquals("✓ Éxito", service.text("resultPanel.status.success"));
    }
}
