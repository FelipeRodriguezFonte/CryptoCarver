package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Phase-zero privacy contract on the production shell, before any extraction. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class XMLSignaturePrivacyCharacterizationUITest extends EmvExtractionCharacterizationSupport {
    @ParameterizedTest
    @EnumSource(SecretVisibilityProfile.class)
    void tsaProfileMustNotPersistEndpointAuthenticationInRestrictedModes(SecretVisibilityProfile profile) throws Exception {
        onFx(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            shell.navigateTo("Sign XML (XAdES)");
            XMLSignatureController controller = (XMLSignatureController) get(shell, "xmlSecurityContainerController");
            assertNotNull(controller);
            // A valid URI with synthetic HTTP user-info. Saving a profile never needs network access.
            String password = "invented-xmlsig-81-" + UUID.randomUUID();
            String url = "https://invented-user:" + password + "@tsa.invalid/timestamp";
            @SuppressWarnings("unchecked") ComboBox<String> endpoint =
                    (ComboBox<String>) get(controller, "xmlSignTsaUrlText");
            TextInputControl name = (TextInputControl) get(controller, "xmlSignTsaProfileNameField");
            endpoint.setValue(url);
            endpoint.getEditor().setText(url);
            name.setText("Invented XML 81 TSA");
            controller.handleSaveTSASavedProfile();

            // Establish persistence independently of the in-memory AppSettings object.
            var settingsFile = tempDir.resolve("settings.json");
            assertTrue(Files.isRegularFile(settingsFile), "the real controller must save settings");
            String persisted = Files.readString(settingsFile);
            AppSettings reloaded = new AppSettings(settingsFile);
            assertEquals(1, reloaded.getTsaProfiles().size(), "successful profile save, not a validation-only path");
            assertEquals("Invented XML 81 TSA", reloaded.getTsaProfiles().get(0).name());
            assertTrue(persisted.contains("tsaProfiles"));
            String savedEndpoint = reloaded.getTsaProfiles().get(0).url();
            String customEndpoint = reloaded.getCustomTsaUrl();
            // Loading it again also puts the persisted credential in the real editable endpoint control.
            controller.handleLoadTSASavedProfile();
            root.applyCss(); root.layout();
            assertSame(stage.getScene(), endpoint.getScene());
            assertTrue(endpoint.isVisible());
            List<String> surfaces = List.of(savedEndpoint, customEndpoint, persisted, endpoint.getEditor().getText());
            if (profile == SecretVisibilityProfile.FULL_LAB) {
                for (String surface : surfaces) assertFalse(surface.contains(password), "FULL_LAB must also keep endpoint credentials out of persisted profiles");
            } else {
                assertAll(profile.name(),
                        () -> assertFalse(savedEndpoint.contains(password), "settings.json tsaProfiles[].url persists TSA password from fx:id=xmlSignTsaUrlText"),
                        () -> assertFalse(customEndpoint.contains(password), "settings.json customTsaUrl persists TSA password"),
                        () -> assertFalse(persisted.contains(password), "settings.json contains invented TSA password in plain text"),
                        () -> assertFalse(endpoint.getEditor().getText().contains(password), "fx:id=xmlSignTsaUrlText restores TSA password from persisted profile"));
            }
            // The shared fixture restores settings, language, Shelf, shell/history and test.mode;
            // @TempDir deletes all files even when this required privacy contract fails.
            // No signing/legacy OperationHistory calls, file dialogs, URL resolution or TSA requests.
        });
    }
}
