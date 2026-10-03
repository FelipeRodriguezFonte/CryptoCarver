package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProfileStatusSeverityUITest extends UiMinorFixesFixture {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    @ParameterizedTest @EnumSource(value = LanguagePreference.class, names = {"EN", "ES"})
    void validProfileLoadsIncludingNegativeScenarioNamesAreInformational(LanguagePreference locale) throws Exception {
        fx(() -> {
            I18nService.getInstance().setPreference(locale);
            for (String id : new String[]{"dukpt_tdes_pos_1", "dukpt_tdes_neg_1", "tr31_neg_2"}) {
                var profile = profile(id); loadProfile(profile);
                assertTrue(status().getText().contains(profile.getName()));
                assertFalse(status().getPseudoClassStates().contains(ERROR), "Valid profile load marked as error: " + status().getText());
                assertTrue(status().getText().contains("✓"));
            }
        });
    }

    @ParameterizedTest @EnumSource(value = LanguagePreference.class, names = {"EN", "ES"})
    void executingRealInvalidProfileStillReportsAnError(LanguagePreference locale) throws Exception {
        fx(() -> {
            I18nService.getInstance().setPreference(locale);
            loadProfile(profile("dukpt_tdes_neg_1"));
            PaymentsController payments = field(shell, "paymentsController");
            payments.handleInspectDukpt();
            HBox banner = field(shell, "errorBanner"); assertTrue(banner.isVisible());
            Label title = field(shell, "errorBannerTitle"); Label remedy = field(shell, "errorBannerRemedy");
            assertFalse(title.getText().isBlank());
            // Classify the actual error's user-facing text as well as checking its real banner.
            new StatusBarPresenter(status(), null, null, I18nService.getInstance()).showStatus(title.getText() + ": " + remedy.getText());
            assertTrue(status().getPseudoClassStates().contains(ERROR), status().getText());
            assertTrue(status().getText().contains("×"));
        });
    }
}
