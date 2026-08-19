package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Headless contract test for Payments validation feedback. */
class PaymentsValidationHeadlessTest {
    @TempDir Path temporaryDirectory;

    @Test
    void everyPaymentsValidationIsSpecificActionableAndLocalized() throws Exception {
        Map<String, String> expectedTechnicalAnchor = new LinkedHashMap<>();
        expectedTechnicalAnchor.put("module.payments.error.pinPanRequired", "PIN");
        expectedTechnicalAnchor.put("module.payments.error.pinLength", "PIN");
        expectedTechnicalAnchor.put("module.payments.error.panInvalid", "PAN");
        expectedTechnicalAnchor.put("module.payments.error.pinBlockInvalid", "PIN block");
        expectedTechnicalAnchor.put("module.payments.error.cvkAInvalid", "CVK A");
        expectedTechnicalAnchor.put("module.payments.error.cvkBInvalid", "CVK B");
        expectedTechnicalAnchor.put("module.payments.error.expiryInvalid", "YYMM");
        expectedTechnicalAnchor.put("module.payments.error.serviceCodeInvalid", "service");
        expectedTechnicalAnchor.put("module.payments.error.atcRequired", "ATC");
        expectedTechnicalAnchor.put("module.payments.error.atcInvalid", "ATC");
        expectedTechnicalAnchor.put("module.payments.error.macRequired", "MAC");
        expectedTechnicalAnchor.put("module.payments.error.macKeyInvalid", "MAC");
        expectedTechnicalAnchor.put("module.payments.error.macDataHex", "MAC");
        expectedTechnicalAnchor.put("module.payments.error.pinBlockPanRequired", "PIN");
        expectedTechnicalAnchor.put("module.payments.error.pvvRequired", "PVV");
        expectedTechnicalAnchor.put("module.payments.error.pvvFormatInvalid", "PVV");
        expectedTechnicalAnchor.put("module.payments.error.trackRequired", "Track");
        expectedTechnicalAnchor.put("module.payments.error.trackFormatInvalid", "Track");

        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());
        String generic = service.text("module.payments.error.required");
        String source = Files.readString(Path.of("src/main/java/com/cryptocarver/ui/PaymentsController.java"));

        for (Map.Entry<String, String> entry : expectedTechnicalAnchor.entrySet()) {
            String key = entry.getKey();
            String english = service.text(key);
            assertNotEquals(generic, english, key + " must not fall back to generic validation text");
            assertTrue(english.contains(entry.getValue()), key + " must retain its technical anchor");
            assertTrue(source.contains("t(\"" + key + "\""), key + " must be used by Payments");

            service.setPreference(LanguagePreference.ES);
            String spanish = service.text(key);
            assertNotEquals(english, spanish, key + " must have an ES translation");
            assertTrue(spanish.startsWith("Error:"), key + " must remain actionable in ES");
            service.setPreference(LanguagePreference.EN);
        }
    }

    @Test
    void advancedPaymentsFeedbackUsesDistinctLocalizedKeys() throws Exception {
        String[] keys = {
                "module.payments.error.aesDukptSelection",
                "module.payments.error.aesDukptBdkRequired",
                "module.payments.error.samePinFormats",
                "module.payments.error.controlsNotInitialized",
                "module.payments.error.invalidStartPosition",
                "module.payments.error.invalidLength",
                "module.payments.error.enterValue",
                "module.payments.error.enterPanForFormat",
                "module.payments.error.pvkOffsetPanRequired",
                "module.payments.error.pvkPanPinRequired",
                "module.payments.error.pvvTargetRequired",
                "module.payments.error.decimalizationTable",
                "module.payments.error.dukptTitle",
                "module.payments.error.operation",
                "module.payments.operation.aesPinBlock",
                "module.payments.operation.encryptedPinBlock",
                "module.payments.dialog.verifyCvvTitle",
                "module.payments.dialog.verifyCvvHeader",
                "module.payments.status.cvvValid",
                "module.payments.status.cvvInvalid",
                "module.payments.status.macVerificationComingSoon",
                "module.payments.status.dukptInspected",
                "module.payments.status.aesDukptDerived",
                "module.payments.status.aesPinBlockProcessed",
                "module.payments.status.profileLoaded",
                "module.payments.result.pinBlockEncodingTitle",
                "module.payments.result.pinBlockDecodingTitle",
                "module.payments.result.cvvGenerationTitle",
                "module.payments.result.cvvVerificationTitle",
                "module.payments.result.pvvVerificationTitle",
                "module.payments.result.macGenerationTitle",
                "module.payments.result.track1EncodedTitle",
                "module.payments.result.track2EncodedTitle",
                "module.payments.result.aesDukptNote"
        };

        AppSettings settings = new AppSettings(temporaryDirectory.resolve("advanced-settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());
        String source = Files.readString(Path.of("src/main/java/com/cryptocarver/ui/PaymentsController.java"));

        for (String key : keys) {
            String english = service.text(key, "sample");
            service.setPreference(LanguagePreference.ES);
            String spanish = service.text(key, "sample");
            service.setPreference(LanguagePreference.EN);

            assertFalse(english.isBlank(), key + " must exist in EN");
            assertFalse(spanish.isBlank(), key + " must exist in ES");
            assertNotEquals(english, spanish, key + " must differ between EN and ES");
            assertTrue(source.contains(key), key + " must be referenced by PaymentsController");
        }

        assertTrue(source.contains("module.payments.error.cvkAInvalid"));
        assertTrue(source.contains("module.payments.error.cvkBInvalid"));
        assertTrue(source.contains("module.payments.error.atcInvalid"));
        assertTrue(source.contains("module.payments.error.trackFormatInvalid"));
    }
}
