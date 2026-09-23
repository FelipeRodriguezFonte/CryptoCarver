package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.OperationSessionLog;
import com.cryptocarver.model.SavedSession;
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
}
