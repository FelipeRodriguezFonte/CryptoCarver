package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests use an invented private symmetric JWK, never a real credential. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class JoseHistoryAliasUITest {
    private static final String PRIVATE_VALUE = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("invented-private-jwk-for-77-only!".getBytes(StandardCharsets.UTF_8));
    private static final String PRIVATE_JWKS = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\"invented-77\",\"k\":\""
            + PRIVATE_VALUE + "\"}]}";
    @TempDir Path tempDir;
    private AppSettings originalSettings;
    private List<ClipboardEntry> originalShelf;

    @BeforeAll static void startFx() throws Exception { JoseCharacterizationSupport.startFx(); }

    @BeforeEach void isolateSettingsAndShelf() {
        originalSettings = AppSettings.getInstance();
        AppSettings.setInstanceForTesting(new AppSettings(tempDir.resolve("settings.json")));
        AppSettings.getInstance().setLanguagePreference(LanguagePreference.EN);
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        I18nService.getInstance().refreshFromSettings();
        originalShelf = new ArrayList<>(ClipboardShelfManager.getInstance().getEntries());
    }

    @AfterEach void restoreSettingsAndShelf() throws Exception {
        try {
            UiTestLifecycleExtension.onFx(() -> {
                var shelf = ClipboardShelfManager.getInstance();
                if (!originalShelf.equals(shelf.getEntries())) {
                    shelf.clear();
                    for (int i = originalShelf.size() - 1; i >= 0; i--) shelf.addEntry(originalShelf.get(i));
                }
            });
        } finally {
            AppSettings.setInstanceForTesting(originalSettings);
            I18nService.getInstance().refreshFromSettings();
        }
    }

    @Test void oldQualifiedJwksRecipeRestoresContent() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var fixture = new JoseCharacterizationSupport()) {
                Map<String, Object> recipe = Map.of("JOSEController.jwksArea", PRIVATE_JWKS);
                UiStateSnapshot.restoreHistoryRecipe(fixture.controller, recipe);
                assertEquals(PRIVATE_JWKS, fixture.area("jwksSecretArea").getText(),
                        "legacy JWKS recipe must restore the current input");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
    }
    @Test void aliasesAreReadOnlyAndCurrentKeysWinEvenWhenEmptyOrNull() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var fixture = new JoseCharacterizationSupport()) {
                for (String oldKey : List.of("jwksArea", "JOSEController.jwksArea")) {
                    Map<String, Object> legacy = Map.of(oldKey, PRIVATE_JWKS);
                    UiStateSnapshot.restoreHistoryRecipe(fixture.controller, legacy);
                    assertEquals(PRIVATE_JWKS, fixture.area("jwksSecretArea").getText());
                    assertEquals(Map.of(oldKey, PRIVATE_JWKS), legacy, "restore must not rewrite the stored recipe");
                    for (UiStateSnapshot.CaptureMode mode : UiStateSnapshot.CaptureMode.values()) {
                        var captured = UiStateSnapshot.capture(fixture.controller, mode);
                        assertFalse(captured.containsKey("JOSEController.jwksArea"));
                        if (mode != UiStateSnapshot.CaptureMode.NON_TEXT) {
                            assertEquals(PRIVATE_JWKS, captured.get("JOSEController.jwksSecretArea"));
                        }
                    }
                    for (String currentKey : List.of("jwksSecretArea", "JOSEController.jwksSecretArea")) {
                        for (String currentValue : java.util.Arrays.asList("{\"keys\":[]}", "", null)) {
                            var both = new java.util.LinkedHashMap<String, Object>();
                            both.put(oldKey, PRIVATE_JWKS); both.put(currentKey, currentValue);
                            UiStateSnapshot.restoreHistoryRecipe(fixture.controller, both);
                            assertEquals(currentValue == null ? "" : currentValue, fixture.area("jwksSecretArea").getText());
                        }
                    }
                }
                // The same read aliases also support full saved-session restoration.
                UiStateSnapshot.restore(fixture.controller, Map.of("JOSEController.jwksArea", PRIVATE_JWKS));
                assertEquals(PRIVATE_JWKS, fixture.area("jwksSecretArea").getText());
            } catch (Exception error) { throw new RuntimeException(error); }
        });
    }

}
