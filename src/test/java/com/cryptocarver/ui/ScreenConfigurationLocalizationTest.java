package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenConfigurationLocalizationTest {
    private static final List<String> KEYS = List.of(
            "dialog.configuration.importTitle", "dialog.configuration.reviewTitle",
            "dialog.configuration.reviewOperation", "dialog.configuration.reviewModule",
            "dialog.configuration.reviewFields", "dialog.configuration.reviewCreated",
            "dialog.configuration.reviewWarning", "dialog.configuration.importAction",
            "dialog.configuration.protectTitle", "dialog.configuration.unlockTitle",
            "dialog.configuration.protectHeader", "dialog.configuration.unlockHeader",
            "dialog.configuration.encrypt", "dialog.configuration.unlock",
            "dialog.configuration.password", "dialog.configuration.passwordLabel",
            "dialog.configuration.repeatPassword", "dialog.configuration.repeatPasswordLabel",
            "dialog.configuration.legacyTitle", "dialog.configuration.legacyMessage",
            "dialog.configuration.exportFailureTitle", "dialog.configuration.exportFailure",
            "dialog.configuration.importFailureTitle", "dialog.configuration.importFailure",
            "dialog.configuration.encryptedFilter", "dialog.configuration.plainFilter",
            "dialog.allFiles", "status.configuration.loaded", "status.configuration.exported");

    @Test
    void configurationDialogAndStatusKeysExistInEnglishAndSpanishBundles() {
        ResourceBundle english = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        ResourceBundle spanish = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("es"));
        for (String key : KEYS) {
            assertTrue(english.containsKey(key), "English bundle is missing " + key);
            assertTrue(spanish.containsKey(key), "Spanish bundle is missing " + key);
        }
    }
}
