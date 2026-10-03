package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EmvProfileStatusUITest extends UiMinorFixesFixture {
    @ParameterizedTest @EnumSource(value = LanguagePreference.class, names = {"EN", "ES"})
    void laboratoryLoadNamesEachEmvProfileLikeOtherModules(LanguagePreference locale) throws Exception {
        fx(() -> {
            I18nService.getInstance().setPreference(locale);
            for (String id : new String[]{"emv_pos_1", "emv_pos_2", "emv_neg_1"}) {
                var profile = profile(id); loadProfile(profile);
                String expected = I18nService.getInstance().text("module.payments.status.profileLoaded", profile.getName());
                assertTrue(status().getText().contains(expected), "Expected profile status: " + expected + "; actual: " + status().getText());
                assertTrue(status().getAccessibleText().startsWith(expected + ", "));
                assertFalse(status().getText().contains("Loaded: EMV Tool"));
            }
        });
    }
}
