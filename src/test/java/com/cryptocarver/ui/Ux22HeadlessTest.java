package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationPreflightEngine;
import com.cryptocarver.model.ResultComparator;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Headless UX-22 contracts for localized, safe and actionable preflight feedback. */
class Ux22HeadlessTest {
    @TempDir Path temporaryDirectory;

    @Test
    void preflightKeysAreLocalizedInEnglishAndSpanish() {
        I18nService service = new I18nService(new AppSettings(temporaryDirectory.resolve("settings.json")),
                I18nService.BUNDLE_BASE_NAME, Locale.ENGLISH, getClass().getClassLoader());
        List<String> keys = List.of("preflight.title", "preflight.input.required", "preflight.key.invalid",
                "preflight.remedy.key", "preflight.summary.blocked", "error.detailsCopied");
        for (String key : keys) {
            service.setPreference(LanguagePreference.EN);
            String english = service.text(key, 2);
            service.setPreference(LanguagePreference.ES);
            String spanish = service.text(key, 2);
            assertNotEquals(key, english, key + " must exist in EN");
            assertNotEquals(key, spanish, key + " must exist in ES");
            assertNotEquals(english, spanish, key + " must change with locale");
        }
    }

    @Test
    void missingPreflightKeyFallsBackSafelyAndFeedbackRedactsSecrets() {
        I18nService missing = new I18nService(new AppSettings(temporaryDirectory.resolve("missing-settings.json")),
                "i18n.ux22-missing", Locale.ENGLISH, getClass().getClassLoader());
        assertEquals("ux22.missing", missing.text("ux22.missing"));

        String secret = "00112233445566778899AABBCCDDEEFF";
        UserFacingError error = new UserFacingError("Invalid key", "key=" + secret,
                "Check key=" + secret, "symmetricKeyField");
        String technical = InlineErrorPresenter.formatRedactedTechnicalDetails(error);
        assertFalse(technical.contains(secret));
        assertTrue(technical.contains("key=[REDACTED]"));
        assertTrue(technical.contains("symmetricKeyField"));
    }

    @Test
    void preflightPreservesTechnicalValuesAndDoesNotEchoSecretInputs() {
        String input = "B0096P0TE00E000000000000000000000000000";
        String key = "00112233445566778899AABBCCDDEEFF";
        var report = OperationPreflightEngine.checkSymmetricCipher(
                input, "Text (UTF-8)", "AES-256", "GCM", "NoPadding", "Manual", key,
                null, false, "00112233445566778899AABB", null, "", true);
        assertTrue(report.getChecks().stream().noneMatch(check -> check.getMessage().contains(key)));

        ClipboardEntry tr31 = new ClipboardEntry("TR-31", input, ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.PUBLIC, "TR-31");
        ClipboardEntry hash = new ClipboardEntry("Hash", "001122AABB", ClipboardEntry.Format.HEX,
                OperationDetail.Classification.PUBLIC, "Hashing");
        ClipboardEntry bytes = new ClipboardEntry("Bytes", "AQID", ClipboardEntry.Format.BASE64,
                OperationDetail.Classification.PUBLIC, "Bytes");
        assertEquals(ResultComparator.Status.EQUAL,
                ResultComparator.compare(tr31, tr31, SecretVisibilityProfile.FULL_LAB).status());
        assertEquals(ResultComparator.Status.EQUAL,
                ResultComparator.compare(hash, hash, SecretVisibilityProfile.FULL_LAB).status());
        assertEquals(ResultComparator.Status.EQUAL,
                ResultComparator.compare(bytes, bytes, SecretVisibilityProfile.FULL_LAB).status());
        assertEquals(input, tr31.getValue());
        assertEquals("001122AABB", hash.getValue());
        assertEquals("AQID", bytes.getValue());
    }

    @Test
    void isolatedTR31FallbackKeepsExportImportAndParseFeedbackSeparated() throws Exception {
        KeysController controller = new KeysController();
        setField(controller, "mainController", null);

        FeedbackAreaProbe exportArea = new FeedbackAreaProbe();
        FeedbackAreaProbe importArea = new FeedbackAreaProbe();
        Method validation = KeysController.class.getDeclaredMethod("showTR31Validation",
                String.class, String.class, KeysController.TR31FeedbackTarget.class);
        validation.setAccessible(true);

        validation.invoke(controller, "export secret=00112233445566778899AABBCCDDEEFF",
                "tr31KeyToWrapField", (KeysController.TR31FeedbackTarget) exportArea::present);
        assertEquals("export secret=[REDACTED]", exportArea.text);
        assertTrue(exportArea.visible);
        assertTrue(exportArea.managed);
        assertEquals("", importArea.text);
        assertFalse(importArea.visible);
        assertFalse(importArea.managed);

        validation.invoke(controller, "import secret=00112233445566778899AABBCCDDEEFF",
                "tr31KeyBlockField", (KeysController.TR31FeedbackTarget) importArea::present);
        validation.invoke(controller, "parse secret=00112233445566778899AABBCCDDEEFF",
                "tr31KeyBlockField", (KeysController.TR31FeedbackTarget) importArea::present);
        assertEquals("parse secret=[REDACTED]", importArea.text);
        assertTrue(importArea.visible);
        assertTrue(importArea.managed);
        assertEquals("export secret=[REDACTED]", exportArea.text);
        assertFalse(exportArea.text.contains("00112233445566778899AABBCCDDEEFF"));
        assertFalse(importArea.text.contains("00112233445566778899AABBCCDDEEFF"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FeedbackAreaProbe {
        private String text = "";
        private boolean visible;
        private boolean managed;

        private void present(String safeMessage) {
            text = safeMessage;
            visible = true;
            managed = true;
        }
    }
}
