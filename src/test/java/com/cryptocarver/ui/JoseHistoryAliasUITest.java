package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.HistoryCommandPolicy;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.ShelfPackage;
import javafx.scene.control.TextArea;
import javafx.scene.control.Label;
import javafx.stage.Window;
import java.nio.file.Files;
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

    @Test void privateAliasContentUsesCanonicalPolicyAcrossRestrictedProfiles() throws Exception {
        HistoryManager history = new HistoryManager(tempDir.resolve("history.json"));
        try {
            UiTestLifecycleExtension.onFx(() -> {
                try (var fixture = new JoseCharacterizationSupport()) {
                    assertTrue(com.nimbusds.jose.jwk.JWKSet.parse(PRIVATE_JWKS).getKeys().get(0).isPrivate());
                    TextArea area = fixture.area("jwksSecretArea");
                    ResultAreaTracker tracker = new ResultAreaTracker();
                    tracker.register(area); tracker.markUpdated(area); tracker.focus(area);
                    ResultCaptureCoordinator capture = new ResultCaptureCoordinator(() -> null, () -> null,
                            () -> null, () -> null, () -> tracker, () -> null, () -> "JOSE", () -> "JOSE",
                            () -> AppSettings.getInstance().getSecretVisibilityProfile(), message -> { },
                            (title, message) -> { }, (control, selected) -> { });
                    Label status = new Label();
                    List<String> clipboard = new ArrayList<>();
                    ResultViewerCoordinator viewerActions = new ResultViewerCoordinator(tracker, null,
                            a -> { }, a -> { }, a -> { }, a -> { }, () -> "JOSE", () -> false,
                            capture::resolveResultText, capture::classificationForResultArea,
                            () -> AppSettings.isFullLab(), status::setText, clipboard::add, entry -> { },
                            new ResultViewerCoordinator.ShelfServices() {
                                public String capture(TextArea a) { return capture.resolveShelfCaptureText(a); }
                                public boolean blockedByVisibility(TextArea a) { return capture.isShelfCaptureBlockedByVisibility(a); }
                                public boolean isCurrentSelection(TextArea a) { return tracker.isCurrentSelection(a, false); }
                                public OperationResult snapshot() { return null; }
                                public String activeOperation() { return "JOSE"; }
                                public ClipboardShelfManager manager() { return ClipboardShelfManager.getInstance(); }
                                public boolean isPrimaryCipherOutput(TextArea a) { return false; }
                                public ShelfPackage createCipherPackage() { return null; }
                            });
                    for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        for (String oldKey : List.of("jwksArea", "JOSEController.jwksArea")) {
                            area.setEditable(true);
                            // Session restores may contain inputs stored during FULL_LAB.
                            UiStateSnapshot.restore(fixture.controller, Map.of(oldKey, PRIVATE_JWKS));
                            assertEquals(PRIVATE_JWKS, area.getText());
                            assertEquals("jwksSecretArea", area.getId());
                            assertEquals(OperationDetail.Classification.SECRET, capture.classificationForResultArea(area));
                            assertTrue(capture.isShelfCaptureBlockedByVisibility(area));
                            var recipe = UiStateSnapshot.captureHistoryRecipe(fixture.controller);
                            assertEquals("[REDACTED_SECRET]", recipe.get("JOSEController.jwksSecretArea"));
                            assertFalse(recipe.containsKey("JOSEController.jwksArea"));
                            history.addHistoryItem(HistoryCommandPolicy.create("JWKS alias test", List.of(), recipe,
                                    "JSON", "JSON", "JOSE", navigation -> true));
                            assertFalse(Files.readString(tempDir.resolve("history.json")).contains(PRIVATE_VALUE));
                            assertTrue(history.getHistoryItems().stream()
                                    .noneMatch(item -> item.getParameters().toString().contains(PRIVATE_VALUE)));

                            // Exercise the shared output policy as well as the actual expanded viewer.
                            area.setEditable(false);
                            assertEquals(profile == SecretVisibilityProfile.MASKED ? "***MASKED***" : "",
                                    capture.renderResultArea(area));
                            assertFalse(capture.resolveShelfCaptureText(area).contains(PRIVATE_VALUE));
                            viewerActions.addToClipboardShelfSecure(area, null);
                            viewerActions.copySecure(area, null, false);
                            assertTrue(clipboard.isEmpty());
                            assertEquals(originalShelf, ClipboardShelfManager.getInstance().getEntries());
                            assertFalse(status.getText().contains(PRIVATE_VALUE));
                            ExpandedTextViewer expanded = new ExpandedTextViewer();
                            try {
                                expanded.show(null, "JWKS alias privacy 77", capture.resolveCurrentOutputText());
                                var window = Window.getWindows().stream().filter(w -> w instanceof javafx.stage.Stage stage
                                        && "JWKS alias privacy 77".equals(stage.getTitle())).findFirst().orElseThrow();
                                var content = window.getScene().getRoot().lookupAll(".text-area").stream()
                                        .map(TextArea.class::cast).map(TextArea::getText).toList();
                                assertFalse(content.isEmpty());
                                assertTrue(content.stream().noneMatch(text -> text.contains(PRIVATE_VALUE)));
                            } finally { expanded.dispose(); area.setEditable(true); }

                            // History reopen must clear the private input and identify it for re-entry,
                            // exactly like a recipe already using the canonical id.
                            var redacted = UiStateSnapshot.restoreHistoryRecipe(fixture.controller, Map.of(oldKey, PRIVATE_JWKS));
                            assertEquals("", area.getText()); assertTrue(redacted.contains(area));
                            area.setText(PRIVATE_JWKS);
                            var direct = UiStateSnapshot.restoreHistoryRecipe(fixture.controller,
                                    Map.of("JOSEController.jwksSecretArea", PRIVATE_JWKS));
                            assertEquals("", area.getText()); assertEquals(direct, redacted);
                            var marker = UiStateSnapshot.restoreHistoryRecipe(fixture.controller,
                                    Map.of(oldKey, "[REDACTED_SECRET]"));
                            assertEquals("", area.getText()); assertTrue(marker.contains(area));
                        }
                    }
                } catch (Exception error) { throw new RuntimeException(error); }
            });
        } finally { history.clearHistory(); }
    }

}
