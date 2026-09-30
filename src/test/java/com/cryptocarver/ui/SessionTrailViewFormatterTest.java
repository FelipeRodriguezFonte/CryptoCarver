package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.OperationSessionLog;
import com.cryptocarver.model.SavedSession;
import com.cryptocarver.model.SavedSessionCodec;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.SessionOperationStep;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionTrailViewFormatterTest {
    @Test
    void previewsEmptyPlaintextAndEncryptedTrailsDifferently() {
        I18nService i18n = I18nService.getInstance();
        SavedSession empty = new SavedSession("Synthetic empty", "Synthetic", Map.of());
        assertTrue(SessionTrailViewFormatter.preview(empty, i18n).contains(i18n.text("sessionTrail.empty")));

        OperationSessionLog log = new OperationSessionLog();
        log.add(OperationResult.forOperation("Synthetic operation").build(), "Synthetic step", List.of(), Map.of());
        SavedSession clear = new SavedSession("Synthetic clear", "Synthetic", Map.of(), log);
        String clearPreview = SessionTrailViewFormatter.preview(clear, i18n);
        assertTrue(clearPreview.contains("Synthetic step"));
        assertFalse(clearPreview.contains(i18n.text("savedSessions.encryptedTrailPreview")));

        SavedSession encrypted = new SavedSession("Synthetic encrypted", "Synthetic", Map.of(), null);
        encrypted.setProtectedFields(new SavedSession.ProtectedFields("synthetic-kdf", 1, "salt", "nonce", "ciphertext"));
        String encryptedPreview = SessionTrailViewFormatter.preview(encrypted, i18n);
        assertTrue(encryptedPreview.contains(i18n.text("savedSessions.encryptedTrailPreview")));
        assertFalse(encryptedPreview.contains(i18n.text("sessionTrail.empty")));
        assertFalse(encryptedPreview.contains(i18n.text("savedSessions.passwordRequired")));
    }

    @Test
    void projectsSavedPayloadsAndParametersByVisibilityProfile() {
        OperationSessionLog log = new OperationSessionLog();
        SessionOperationStep step = log.add(OperationResult.forOperation("Calculate MAC")
                .input("INPUT-SECRET".getBytes(StandardCharsets.UTF_8))
                .output("OUTPUT-SECRET".getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET)
                .detail(OperationDetail.secretDetail("Key", "DETAIL-SECRET"))
                .build(), "First step", List.of("mac"), Map.of("password", "PARAMETER-SECRET"));
        I18nService i18n = I18nService.getInstance();

        String full = SessionTrailViewFormatter.step(step, SecretVisibilityProfile.FULL_LAB, i18n);
        assertTrue(full.contains("INPUT-SECRET"));
        assertTrue(full.contains("OUTPUT-SECRET"));
        assertTrue(full.contains("DETAIL-SECRET"));
        assertTrue(full.contains("PARAMETER-SECRET"));

        String masked = SessionTrailViewFormatter.step(step, SecretVisibilityProfile.MASKED, i18n);
        assertTrue(masked.contains("***MASKED***"));
        assertFalse(masked.contains("INPUT-SECRET"));
        assertFalse(masked.contains("OUTPUT-SECRET"));
        assertFalse(masked.contains("DETAIL-SECRET"));
        assertFalse(masked.contains("PARAMETER-SECRET"));

        String redacted = SessionTrailViewFormatter.step(step, SecretVisibilityProfile.REDACTED, i18n);
        assertTrue(redacted.contains("***REDACTED***"));
        assertFalse(redacted.contains("INPUT-SECRET"));
        assertFalse(redacted.contains("OUTPUT-SECRET"));

        SavedSession session = new SavedSession("Review session", "MAC", Map.of(), log);
        String preview = SessionTrailViewFormatter.preview(session, i18n);
        assertTrue(preview.contains("First step"));
        assertTrue(preview.contains("Calculate MAC"));
        assertFalse(preview.contains("INPUT-SECRET"));
        assertFalse(preview.contains("PARAMETER-SECRET"));
    }
    @Test
    void redactedPreviewAndStepExplainOmittedValuesEvenInFullLab() {
        I18nService i18n = I18nService.getInstance();
        SavedSession stored = new com.cryptocarver.model.SavedSessionCodec().prepareForStorage(
                new SavedSession("Redacted", "MAC", Map.of(), secretLog()), null);
        assertTrue(SessionTrailViewFormatter.preview(stored, i18n).contains(i18n.text("sessionTrail.redacted")));
        String step = SessionTrailViewFormatter.step(stored.getOperationLog().getSteps().get(0),
                SecretVisibilityProfile.FULL_LAB, i18n, stored.isTrailRedacted());
        assertTrue(step.contains(i18n.text("sessionTrail.redacted")));
        assertTrue(step.contains(i18n.text("sessionTrail.redactedValue")));
        assertFalse(step.contains("INPUT-SECRET"));
    }

    @Test
    void legacyAndAadEncryptedSessionsKeepTheSamePreviewContract() throws Exception {
        SavedSessionCodec codec = new SavedSessionCodec();
        I18nService i18n = I18nService.getInstance();
        String fixture;
        try (var input = getClass().getResourceAsStream("/com/cryptocarver/model/saved-session-no-aad.json")) {
            assertNotNull(input);
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        SavedSession legacy = codec.deserialize(fixture).get(0);
        SavedSession clear = codec.restore(legacy, "invented-password-42".toCharArray());
        SavedSession current = codec.prepareForStorage(clear, "invented-password-42".toCharArray());
        assertEquals(0, legacy.getProtectedFields().getAadVersion());
        assertEquals(1, current.getProtectedFields().getAadVersion());
        String preview = SessionTrailViewFormatter.preview(legacy, i18n);
        assertEquals(preview, SessionTrailViewFormatter.preview(current, i18n));
        assertTrue(preview.contains(i18n.text("savedSessions.encryptedTrailPreview")));
        assertFalse(preview.contains("invented-value-42"));
        assertFalse(preview.contains("invented-input-41"));
    }

    private OperationSessionLog secretLog() {
        OperationSessionLog log = new OperationSessionLog();
        log.add(OperationResult.forOperation("MAC").input("INPUT-SECRET".getBytes(StandardCharsets.UTF_8))
                .build(), "Synthetic step", List.of());
        return log;
    }
}
