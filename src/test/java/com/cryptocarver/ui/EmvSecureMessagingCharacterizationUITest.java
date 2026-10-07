package com.cryptocarver.ui;

import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Invented input keys; fixes scheme workflows, validation and real privacy surfaces. */
@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class EmvSecureMessagingCharacterizationUITest extends EmvExtractionCharacterizationSupport {
    private static final String KEY = "0123456789ABCDEFFEDCBA9876543210";
    @Test void schemesAndPrivacyKeepTheirTranscript() throws Exception {
        List<String> lines = new ArrayList<>(), violations = new ArrayList<>();
        onFx(() -> {
            EMVController c = controller();
            for (String scheme : List.of("Mastercard", "Visa")) {
                ((ComboBox<String>)get(c,"smSchemeCombo")).setValue(scheme);
                c.handleSmLoadExample();
                lines.add(scheme + " example=" + ((TextInputControl)get(c,"smResultArea")).getText());
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppProfile(profile); resetShared();
                    // Replace shipped example keys with a fixed invented fixture before any publication.
                    for (String field : List.of("smMkSmiField","smMkSmcField","smUdkSmiField","smUdkSmcField","smUdkAField"))
                        ((TextInputControl)get(c,field)).setText(KEY);
                    c.handleSmDeriveSessionKeys();
                    String macKey = ((TextInputControl)get(c,"smSkMacField")).getText();
                    String encKey = ((TextInputControl)get(c,"smSkEncField")).getText();
                    assertFalse(macKey.isBlank()); assertFalse(encKey.isBlank());
                    if (profile == SecretVisibilityProfile.FULL_LAB)
                        lines.add(scheme + " keys=" + ((TextInputControl)get(c,"smResultArea")).getText());
                    inspectSurfaces(profile, scheme + " keys", List.of(KEY,macKey,encKey),shell,root,lines,violations);
                    c.handleSmEncipherPin();
                    String encrypted = ((TextInputControl)get(c,"smDataField")).getText();
                    assertFalse(encrypted.isBlank());
                    if(profile == SecretVisibilityProfile.FULL_LAB) lines.add(scheme + " pin="+encrypted);
                    c.handleSmGenerateMac();
                    if(profile == SecretVisibilityProfile.FULL_LAB)
                        lines.add(scheme + " mac="+((TextInputControl)get(c,"smResultArea")).getText());
                    // PIN/MAC output is public ciphertext; raw keys must never occur in any surface.
                    inspectSurfaces(profile, scheme + " command", List.of(KEY,macKey,encKey),shell,root,lines,violations);
                }
                ((TextInputControl)get(c,"smPinField")).setText("12");
                c.handleSmEncipherPin();
                String error=((TextInputControl)get(c,"smResultArea")).getText();
                assertTrue(error.contains(I18nService.getInstance().text("module.emv.sm.pinInvalid")));
                lines.add(scheme + " validation="+error);
            }
            assertTrue(violations.isEmpty(),String.join("\n",violations));
        });
        pinTranscript("emv-sm-75", "1fddbe0e74ac769692e229cc638067924e109be447fa9648c34987d82bc92d25", lines);
    }
    private static void AppProfile(SecretVisibilityProfile p) {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(p);
    }
}
