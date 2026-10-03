package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.List;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ToolbarOutputFormatsUITest extends UiMinorFixesFixture {
    @ParameterizedTest @EnumSource(value = LanguagePreference.class, names = {"EN", "ES"})
    void base94KeepsSelectionAfterModuleWasAlreadyLoaded(LanguagePreference locale) throws Exception {
        fx(() -> {
            I18nService.getInstance().setPreference(locale);
            shell.navigateToModule("Hashing"); shell.navigateToModule("Manual Conversion");
            GenericController generic = field(shell, "genericContainerController");
            ComboBox<String> local = field(generic, "manualOutputFormatCombo"); local.setValue("Base94");
            ComboBox<String> toolbar = field(shell, "outputFormatCombo");
            assertNotNull(toolbar.getValue(), "Base94 cleared the toolbar selection");
            assertEquals("Base94", toolbar.getSelectionModel().getSelectedItem());
        });
    }

    @ParameterizedTest @EnumSource(value = LanguagePreference.class, names = {"EN", "ES"})
    void everyManualFormatKeepsBothRealSelectorsSelected(LanguagePreference locale) throws Exception {
        fx(() -> {
            I18nService.getInstance().setPreference(locale);
            shell.navigateToModule("Manual Conversion");
            ComboBox<String> toolbar = field(shell, "outputFormatCombo");
            GenericController generic = field(shell, "genericContainerController");
            ComboBox<String> local = field(generic, "manualOutputFormatCombo");
            List<String> formats = List.copyOf(local.getItems());
            assertTrue(formats.contains("Base94"));
            for (String format : formats) {
                local.getSelectionModel().select(format);
                assertNotNull(toolbar.getValue(), "Toolbar selection cleared for " + format);
                assertEquals(format, toolbar.getValue());
                assertEquals(format, toolbar.getSelectionModel().getSelectedItem());
                toolbar.getSelectionModel().select(format);
                assertEquals(format, local.getValue());
                assertNotNull(toolbar.getValue());
            }
        });
    }

    @ParameterizedTest @EnumSource(value = LanguagePreference.class, names = {"EN", "ES"})
    void outputChoicesPersistInAppSettingsAcrossShellRecreation(LanguagePreference locale) throws Exception {
        fx(() -> {
            I18nService.getInstance().setPreference(locale);
            shell.navigateToModule("Manual Conversion");
            GenericController generic = field(shell, "genericContainerController");
            ComboBox<String> local = field(generic, "manualOutputFormatCombo");
            local.setValue("Base94");
            var json = JsonParser.parseString(Files.readString(settingsFile)).getAsJsonObject();
            assertTrue(json.has("outputFormats"), "AppSettings does not persist toolbar formats");
            assertEquals("Base94", json.getAsJsonObject("outputFormats").get("Manual Conversion").getAsString());
            shell.navigateToModule("Hashing");
            ComboBox<String> toolbar = field(shell, "outputFormatCombo"); toolbar.setValue("Binary");
            closeShell(); AppSettings.setInstanceForTesting(new AppSettings(settingsFile)); openShell();
            toolbar = field(shell, "outputFormatCombo"); assertEquals("Binary", toolbar.getValue());
            shell.navigateToModule("Manual Conversion"); assertEquals("Base94", toolbar.getValue());
            generic = field(shell, "genericContainerController"); local = field(generic, "manualOutputFormatCombo");
            assertEquals("Base94", local.getValue());
        });
        fx(() -> {});
    }
}
