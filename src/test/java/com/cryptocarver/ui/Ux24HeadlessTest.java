package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Headless contracts for UX-24's recoverable legacy validation paths. */
class Ux24HeadlessTest {
    @TempDir Path temporaryDirectory;

    @Test
    void migratedFeedbackKeysAreLocalizedAndActionable() {
        I18nService service = new I18nService(new AppSettings(temporaryDirectory.resolve("settings.json")),
                I18nService.BUNDLE_BASE_NAME, Locale.ENGLISH, getClass().getClassLoader());
        String[] keys = {
                "module.cms.detachedContentRequired", "module.cms.reportRequired",
                "module.jose.feedback.inputRequired", "module.jose.feedback.keyRequired",
                "module.jose.feedback.tokenRequired", "module.jose.feedback.detachedPayloadRequired",
                "module.jose.feedback.detachedTokenRequired", "module.jose.feedback.nestedTokenRequired",
                "preflight.title", "preflight.remedy.input", "preflight.remedy.algorithm"
        };
        for (String key : keys) {
            service.setPreference(com.cryptocarver.model.LanguagePreference.EN);
            String en = service.text(key);
            service.setPreference(com.cryptocarver.model.LanguagePreference.ES);
            String es = service.text(key);
            assertNotEquals(key, en, key + " must exist in English");
            assertNotEquals(key, es, key + " must exist in Spanish");
            assertNotEquals(en, es, key + " must switch language live");
            assertFalse(en.isBlank());
            assertFalse(es.isBlank());
        }
    }

    @Test
    void sharedAdapterCarriesFieldKeyAndRedactsSecretsBeforePresentation() {
        RecordingReporter reporter = new RecordingReporter();
        String secret = "00112233445566778899AABBCCDDEEFF";

        InlineValidationSupport.show(reporter, "Validation", "key=" + secret,
                "Correct key=" + secret, "asn1InputArea", null);

        assertNotNull(reporter.error);
        assertEquals("asn1InputArea", reporter.error.fieldKey());
        assertFalse(reporter.error.detail().contains(secret));
        assertFalse(reporter.error.remedy().contains(secret));
        assertTrue(reporter.error.detail().contains("key=[REDACTED]"));
        assertFalse(reporter.error.detail().equals("Error"), "migrated routes must keep specific causes");
    }

    @Test
    void migratedControllerFamiliesUseConcreteFieldKeys() {
        RecordingReporter reporter = new RecordingReporter();
        String[][] routes = {
                {"cmsInputArea", "Provide CMS input"},
                {"cmsContentArea", "Provide detached content"},
                {"jwtValidateTokenArea", "Provide JWT token"},
                {"asicPkcs11AliasCombo", "Select token key"},
                {"padesVisiblePageField", "Provide page"}
        };
        for (String[] route : routes) {
            InlineValidationSupport.show(reporter, "Validation", route[1],
                    "Correct the highlighted field.", route[0], null);
            assertEquals(route[0], reporter.error.fieldKey());
            assertFalse(reporter.error.detail().equals("Error"));
        }
    }

    @Test
    void legacyFileValidationExceptionsPointToRealFxmlControls() throws Exception {
        FieldValidationException asic = invokeValidation(AsicController.class, "requireFile", null, "ASiC payload");
        assertEquals("asicInputPathField", asic.fieldKey());
        assertTrue(asic.getMessage().toLowerCase(Locale.ROOT).contains("provide")
                || asic.getMessage().toLowerCase(Locale.ROOT).contains("indica"));

        FieldValidationException output = invokeValidation(AsicController.class, "requireNewFile", null, "ASiC output");
        assertEquals("asicOutputPathField", output.fieldKey());

        FieldValidationException pades = invokeValidation(PadesController.class, "requireFile", null, "PDF input");
        assertEquals("padesInputPathField", pades.fieldKey());
        FieldValidationException padesOutput = invokeValidation(PadesController.class, "requireNewFile", null, "PDF output");
        assertEquals("padesOutputPathField", padesOutput.fieldKey());
    }

    @Test
    void padesCoordinateValidationKeepsTheSpecificInvalidField() throws Exception {
        assertCoordinateField("parseInteger", "padesVisiblePageField");
        assertCoordinateField("parseFloat", "padesVisibleXField");
        assertCoordinateField("parseFloat", "padesVisibleYField");
        assertCoordinateField("parseFloat", "padesVisibleWidthField");
        assertCoordinateField("parseFloat", "padesVisibleHeightField");
    }

    @Test
    void technicalArtifactsRemainByteForByteUntouchedByFeedback() {
        String tr31 = "B0096P0TE00E000000000000000000000000000";
        String hash = "001122AABBCCDDEE";
        byte[] bytes = new byte[] { 0x00, 0x01, (byte) 0xff };
        assertEquals(tr31, InlineErrorPresenter.redactSecrets(tr31));
        assertEquals(hash, InlineErrorPresenter.redactSecrets(hash));
        assertArrayEquals(bytes, java.util.Base64.getDecoder().decode(
                java.util.Base64.getEncoder().encodeToString(bytes)));
    }

    private static FieldValidationException invokeValidation(Class<?> type, String methodName,
                                                              Object field, String label) throws Exception {
        Method method = type.getDeclaredMethod(methodName, javafx.scene.control.TextField.class, String.class);
        method.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, field, label));
        assertInstanceOf(FieldValidationException.class, thrown.getCause());
        return (FieldValidationException) thrown.getCause();
    }

    private static void assertCoordinateField(String methodName, String expectedFieldKey) throws Exception {
        Method method = PadesController.class.getDeclaredMethod(methodName, String.class, String.class);
        method.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, "not-a-number", expectedFieldKey));
        assertInstanceOf(FieldValidationException.class, thrown.getCause());
        assertEquals(expectedFieldKey, ((FieldValidationException) thrown.getCause()).fieldKey());
    }

    private static final class RecordingReporter implements StatusReporter {
        private UserFacingError error;

        @Override public void updateStatus(String message) { }
        @Override public void updateInspector(String operation, byte[] input, byte[] output,
                                              java.util.List<com.cryptocarver.model.OperationDetail> details) { }
        @Override public void showError(String title, String message) { }
        @Override public void showError(UserFacingError error) { this.error = error; }
    }
}
