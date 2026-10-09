package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Assignment 80: records whichever outcome a Wallet handler produces, without judging it. */
abstract class WalletRemainingCharacterizationSupport extends WalletExtractionCharacterizationSupport {
    @FunctionalInterface
    protected interface Scenario { void run(List<String> transcript) throws Exception; }

    /** Runs the scenario once per language and visibility profile, labelling each block. */
    protected void forEachLanguageAndProfile(List<String> transcript, Scenario scenario) throws Exception {
        for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
            I18nService.getInstance().setPreference(language);
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                transcript.add(language + "|" + profile);
                scenario.run(transcript);
            }
        }
    }

    /**
     * Invokes the handler and returns one stable line: the field and localized remedy of a
     * validation, the field and status of a failure (never the exception text), or the published
     * operation, status and public details. A published result always equals the output area.
     */
    protected String observe(String handler, String output) throws Exception {
        showResult(output);
        int before = shell.getHistoryManager().getHistoryItems().size();
        recorder.status = null;
        invoke(handler);
        int published = shell.getHistoryManager().getHistoryItems().size() - before;
        if (recorder.error != null) {
            assertNull(recorder.result, handler + " must not publish after an error");
            assertEquals(0, published, handler);
            boolean failure = recorder.status != null;
            return handler + (failure ? "|failed|" : "|validation|") + recorder.error.fieldKey()
                    + "|" + (failure ? recorder.status : recorder.error.remedy());
        }
        assertNotNull(recorder.result, handler + " must publish or report an error");
        assertEquals(1, published, handler);
        assertEquals(text(output), new String(recorder.result.getOutput(), StandardCharsets.UTF_8), handler);
        return handler + "|" + recorder.result.getOperation() + "|" + recorder.status
                + "|" + recorder.result.getStatusMessage() + "|"
                + recorder.result.getDetails().stream()
                        .filter(detail -> detail.classification() == OperationDetail.Classification.PUBLIC).toList();
    }
}
