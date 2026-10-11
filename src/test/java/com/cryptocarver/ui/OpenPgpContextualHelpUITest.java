package com.cryptocarver.ui;

import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.OperationHelpRegistry;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.Parent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.layout.VBox;
import com.cryptocarver.service.I18nService;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class OpenPgpContextualHelpUITest {
    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test void openPgpHelpButtonsAreRouteScopedAndDoNotReadOrChangePayloads() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try {
                FXMLLoader loader = UiTestFxml.productionLoader("/fxml/openpgp.fxml");
                VBox root = loader.load();
                OpenPgpController controller = loader.getController();
                Stage stage = new Stage(); stage.setScene(new Scene(root, 1100, 800)); stage.show(); root.applyCss(); root.layout();
                HelpContextState state = new HelpContextState();
                controller.configureContextualHelp(state, ignored -> { });
                controller.setContextualHelpEnabled(true);
                controller.setContextualHelpRouteActive(true);
                root.applyCss(); root.layout();
                Node encryptHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_openpgp_encrypt_text_action");
                assertNotNull(encryptHelp);
                assertTrue(encryptHelp.isVisible() && encryptHelp.isManaged());
                TextArea input = (TextArea) root.lookup("#openPgpInputArea");
                TextArea secret = (TextArea) root.lookup("#openPgpSecretKeyArea");
                String sentinel = "OPENPGP_HELP_SENTINEL";
                input.setText(sentinel); secret.setText("SECRET_KEY_SENTINEL");
                ContextualHelpTestSupport.shiftF1(encryptHelp);
                assertEquals("encrypt_text", state.current().actionId());
                assertFalse(state.current().toString().contains(sentinel));
                assertFalse(state.current().toString().contains("SECRET_KEY_SENTINEL"));
                assertEquals(sentinel, input.getText());
                assertEquals("SECRET_KEY_SENTINEL", secret.getText());
                controller.setContextualHelpEnabled(false);
                HelpContext beforeFocus = state.current();
                encryptHelp.requestFocus();
                assertEquals(beforeFocus, state.current(), "Focus must not publish help once the mode is off");
                state.setAction("");
                {
                    HelpContext beforeShortcut = state.current();
                    ContextualHelpTestSupport.shiftF1(encryptHelp);
                    assertEquals("encrypt_text", state.current().actionId(), "Shift+F1 works with the mode off while the route is active");
                    assertNotEquals(beforeShortcut, state.current());
                }
                controller.closeContextualHelpBindings(); stage.close();
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    @Test void openPgpBindsFromRealShellBeforeCssAndEveryQueryUsesCatalogIds() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            AppSettings original = AppSettings.getInstance();
            Stage stage = null;
            try {
                AppSettings.setInstanceForTesting(new AppSettings(java.nio.file.Files.createTempDirectory("b04-pgp-shell").resolve("settings.json")));
                for (LanguagePreference language : java.util.List.of(LanguagePreference.ES, LanguagePreference.EN)) {
                    for (int width : java.util.List.of(1800, 900)) {
                        I18nService.getInstance().setPreference(language);
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load();
                        stage = new Stage(); stage.setScene(new Scene(root, width, 900)); stage.show();
                        ModernMainController shell = loader.getController();
                        shell.navigateTo("OpenPGP (GPG Compatible)");
                        CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                        mode.setSelected(true); shell.handleToggleContextualHelpMode();
                        Object cipher = field(shell, "cipherContainerController");
                        OpenPgpController pgp = field(cipher, "openPgpContainerController");
                        HelpContextState state = field(shell, "contextualHelpState");
                        Object binder = field(pgp, "contextualHelpButtons");
                        assertNotNull(binder);
                        assertTrue(((java.util.List<?>) field(binder, "bindings")).size() >= 35,
                                "buttons must bind during actual lazy shell loading without prior CSS");
                        int initialCount = ((java.util.List<?>) field(binder, "bindings")).size();
                        shell.navigateTo("Keys"); shell.navigateTo("OpenPGP (GPG Compatible)");
                        assertEquals(initialCount, ((java.util.List<?>) field(binder, "bindings")).size(), "no duplicate bindings on route revisit");
                        root.applyCss(); root.layout();
                        java.util.List<Node> buttons = ContextualHelpTestSupport.boundControls(root).stream()
                                .filter(node -> ContextualHelpTestSupport.bindingIds(node).stream()
                                        .anyMatch(id -> id.startsWith("contextHelp_op_openpgp_"))).toList();
                        assertTrue(buttons.size() >= 35);
                        assertTrue(ContextualHelpTestSupport.contextHelpButtons(root).isEmpty(), "no per-control ? buttons remain");
                        var definition = OperationHelpRegistry.getInstance().resolve("op_openpgp");
                        TextArea input = field(pgp, "openPgpInputArea");
                        TextArea secret = field(pgp, "openPgpSecretKeyArea");
                        input.setText("SHELL_OPENPGP_INPUT_SENTINEL"); secret.setText("SHELL_OPENPGP_SECRET_SENTINEL");
                        for (Node button : buttons) {
                            ContextualHelpTestSupport.shiftF1(button);
                            assertTrue(definition.actions().stream().anyMatch(action -> action.id().equals(state.current().actionId())),
                                    "action id must exist for " + ContextualHelpTestSupport.bindingIds(button));
                            String fieldId = state.current().fieldId();
                            if (!fieldId.isBlank()) assertTrue(definition.actions().stream().flatMap(a -> a.fields().stream())
                                    .anyMatch(field -> field.id().equals(fieldId)), "field id must exist: " + fieldId);
                        }
                        javafx.scene.Node userIdHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_openpgp_generate_key_pair_user_id");
                        assertNotNull(userIdHelp);
                        assertTrue(userIdHelp.getAccessibleText().toLowerCase(java.util.Locale.ROOT).contains(language == LanguagePreference.ES ? "usuario" : "user"));
                        javafx.scene.Node saveSecretHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_openpgp_save_secret_key_action");
                        javafx.scene.Node clearSecretHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_openpgp_clear_secret_material_action");
                        assertNotNull(saveSecretHelp); assertNotNull(clearSecretHelp);
                        assertNotEquals(saveSecretHelp.getAccessibleHelp(), clearSecretHelp.getAccessibleHelp());
                        javafx.scene.Node savePublicHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_openpgp_save_public_key_action");
                        assertNotNull(savePublicHelp); ContextualHelpTestSupport.shiftF1(savePublicHelp);
                        String expectedDescription = I18nService.getInstance().text("help.action.op_openpgp.save_public_key.description");
                        assertTrue(shownHelpLabels(shell).stream().anyMatch(text -> text.contains(expectedDescription)),
                                "localized help must render in the active inspector or compact view");
                        ContextualHelpWindowCoordinator compact = field(shell, "contextualHelpWindowCoordinator");
                        assertEquals(width < 1100, compact != null && compact.isShowing(), "selected help view follows shell width");
                        if (compact != null && compact.isShowing()) {
                            compact.windowForTesting().close();
                            assertFalse(compact.isShowing());
                            ContextualHelpTestSupport.shiftF1(savePublicHelp);
                            assertTrue(compact.isShowing(), "compact help can reopen for the current action");
                        }
                        assertEquals("SHELL_OPENPGP_INPUT_SENTINEL", input.getText());
                        assertEquals("SHELL_OPENPGP_SECRET_SENTINEL", secret.getText());
                        assertFalse(state.current().toString().contains("SHELL_OPENPGP_INPUT_SENTINEL"));
                        assertFalse(state.current().toString().contains("SHELL_OPENPGP_SECRET_SENTINEL"));
                        shell.navigateTo("Keys");
                        HelpContext afterRouteChange = state.current();
                        buttons.forEach(ContextualHelpTestSupport::shiftF1);
                        assertEquals(afterRouteChange, state.current(), "route change makes OpenPGP help controls inert");
                        shell.shutdown(); stage.close(); stage = null;
                    }
                }
            } catch (Throwable failure) { throw new RuntimeException(failure); }
            finally {
                if (stage != null) stage.close();
                AppSettings.setInstanceForTesting(original);
            }
        });
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
        java.lang.reflect.Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(owner);
    }

    private static java.util.List<String> shownHelpLabels(ModernMainController shell) throws Exception {
        ContextualHelpWindowCoordinator compact = field(shell, "contextualHelpWindowCoordinator");
        VBox content = compact != null && compact.isShowing()
                ? (VBox) ((ScrollPane) compact.windowForTesting().getScene().getRoot()).getContent()
                : field(shell, "contextualHelpContent");
        return content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .map(Label::getText).toList();
    }
}
