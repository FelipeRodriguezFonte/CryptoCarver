package com.cryptocarver.model.help;

import com.cryptocarver.model.PreflightCheck;
import com.cryptocarver.model.PreflightStatus;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelpContextTest {
    @Test void routeChangesClearOperationSpecificStateButActionChangesPreserveIt() {
        HelpContext context = HelpContext.empty()
                .withOperation("op_sym_ciphers")
                .withAction("decrypt")
                .withSelection(HelpContext.Selection.ALGORITHM, HelpContext.PublicSelection.of("AES-256"));

        HelpContext sameRoute = context.withOperation("op_sym_ciphers");
        assertEquals("decrypt", sameRoute.actionId());
        assertEquals("AES-256", sameRoute.selections().get(HelpContext.Selection.ALGORITHM).code());

        HelpContext nextRoute = context.withOperation("op_gen_hash");
        assertEquals("", nextRoute.actionId());
        assertTrue(nextRoute.selections().isEmpty());
    }

    @Test void selectionCodesRejectFreeformValuesAndPreflightRuleIdsIgnoreMessageText() {
        assertThrows(IllegalArgumentException.class,
                () -> HelpContext.PublicSelection.of("user entered secret text"));
        PreflightCheck first = new PreflightCheck("Input Format", PreflightStatus.BLOCKED,
                "payload contains private data A", "cipherInputArea");
        PreflightCheck translated = new PreflightCheck("Input Format", PreflightStatus.BLOCKED,
                "another localized diagnosis", "cipherInputArea");
        assertEquals("preflight.input_format.blocked", first.getRuleId());
        assertEquals(first.getRuleId(), translated.getRuleId());
    }

    @Test void localeAndSafeSelectionsArePartOfObservableContext() {
        HelpContext context = HelpContext.empty().withLocale(Locale.forLanguageTag("es"))
                .withSelection(HelpContext.Selection.PREFLIGHT_STATUS,
                        HelpContext.PublicSelection.of(PreflightStatus.WARNING.name()));
        assertEquals("es", context.locale().getLanguage());
        assertEquals("WARNING", context.selections().get(HelpContext.Selection.PREFLIGHT_STATUS).code());
    }
}
