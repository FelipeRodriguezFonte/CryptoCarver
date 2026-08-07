package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Headless contracts for the real CMS and JOSE UX-25 field routes. */
class Ux25HeadlessTest {
    @TempDir Path temporaryDirectory;

    @Test
    void cmsAndJoseValidationKeysSwitchBetweenEnglishAndSpanish() {
        I18nService service = new I18nService(new AppSettings(temporaryDirectory.resolve("settings.json")),
                I18nService.BUNDLE_BASE_NAME, Locale.ENGLISH, getClass().getClassLoader());
        List<String> keys = List.of(
                "module.cms.inputRequired", "module.cms.detachedContentRequired",
                "module.jose.feedback.tokenRequired", "module.jose.feedback.algorithmRequired",
                "module.jose.feedback.keyRequired", "preflight.title", "preflight.remedy.input",
                "preflight.remedy.algorithm");
        for (String key : keys) {
            service.setPreference(LanguagePreference.EN);
            String english = service.text(key);
            service.setPreference(LanguagePreference.ES);
            String spanish = service.text(key);
            assertNotEquals(key, english, key + " must exist in EN");
            assertNotEquals(key, spanish, key + " must exist in ES");
            assertNotEquals(english, spanish, key + " must change with locale");
        }
    }

    @Test
    void expectedFieldKeysArePresentInTheRealFxml() throws Exception {
        assertFxmlFields("/fxml/cms_inspector.fxml", List.of("cmsInputArea", "cmsContentArea"));
        assertFxmlFields("/fxml/jose.fxml", List.of(
                "jwtValidateTokenArea", "jwtValidateKeyArea", "detachedAlgoCombo",
                "detachedPayloadArea", "detachedSigningKeyArea", "detachedVerificationKeyArea"));
    }

    private static void assertFxmlFields(String resource, List<String> fieldKeys) throws Exception {
        String fxml;
        try (InputStream input = Ux25HeadlessTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, resource + " must be available");
            fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String fieldKey : fieldKeys) {
            assertTrue(fxml.contains("fx:id=\"" + fieldKey + "\""),
                    resource + " must expose " + fieldKey);
        }
    }
}
