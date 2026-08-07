package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Headless UX-23 contracts for localized and redacted accessible error text. */
class Ux23HeadlessTest {
    @TempDir Path temporaryDirectory;

    @Test
    void errorBannerAccessibilityKeysExistInEnglishAndSpanish() {
        I18nService service = new I18nService(new AppSettings(temporaryDirectory.resolve("settings.json")),
                I18nService.BUNDLE_BASE_NAME, Locale.ENGLISH, getClass().getClassLoader());
        List<String> keys = List.of("a11y.errorTitle", "a11y.errorRemedy", "a11y.errorGoToField",
                "a11y.errorGoToFieldHelp", "a11y.errorCopyDetails", "a11y.errorCopyDetailsHelp",
                "a11y.errorClose", "a11y.errorCloseHelp");

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
    void accessibleTextUsesTheSharedSecretRedactionBoundary() {
        String secret = "00112233445566778899AABBCCDDEEFF";
        String accessible = InlineErrorPresenter.safeAccessibleText(
                "title key=" + secret + " remedy privateKey=" + secret + " technical=" + secret);

        assertFalse(accessible.contains(secret));
        assertTrue(accessible.contains("[REDACTED]"));
    }
}
