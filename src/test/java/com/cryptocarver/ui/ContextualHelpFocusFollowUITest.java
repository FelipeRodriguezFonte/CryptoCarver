package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ContextualHelpFocusFollowUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void focusPublishesWithoutOpeningAndShiftF1OpensEvenWithTheModeOff() throws Exception {
        TextField first = new TextField(), second = new TextField();
        javafx.scene.control.ComboBox<String> combo = new javafx.scene.control.ComboBox<>();
        combo.getItems().addAll("a", "b");
        VBox box = new VBox(first, second, combo);
        Stage[] holder = new Stage[1];
        UiTestLifecycleExtension.onFx(() -> {
            first.setAccessibleText("Own label");
            holder[0] = new Stage(); holder[0].setScene(new Scene(box, 300, 200)); holder[0].show(); holder[0].requestFocus();
        });
        assumeWindowFocused(holder[0]);
        UiTestLifecycleExtension.onFx(() -> {
            Stage stage = holder[0];
            HelpContextState state = new HelpContextState();
            List<HelpContext> opened = new ArrayList<>();
            ContextualHelpButtonBinder binder = new ContextualHelpButtonBinder(state, opened::add);
            binder.bind("op_gen_hash", "op_gen_hash", "calculate", "input", first);
            binder.bind("op_gen_hash", "op_gen_hash", "select_algorithm", "algorithm", second);
            binder.bind("op_gen_hash", "op_gen_hash", "apply_template", "template", combo);
            assertEquals("Own label", first.getAccessibleText(), "existing accessible text must not be replaced");
            assertFalse(second.getAccessibleText() == null || second.getAccessibleText().isBlank(),
                    "controls without their own accessible text get the help label");

            // Inactive route: nothing happens, even with the mode on.
            binder.setEnabled(true);
            first.requestFocus();
            assertEquals("unknown", state.current().operationId());
            ContextualHelpTestSupport.shiftF1(first);
            assertTrue(opened.isEmpty());

            // Cells inside a combo box report a focused state while it lays out; that is not user focus.
            binder.setActiveRoute("op_gen_hash");
            HelpContext untouched = state.current();
            combo.getSelectionModel().select(1);
            box.applyCss(); box.layout();
            combo.getSelectionModel().select(0);
            box.applyCss(); box.layout();
            assertEquals(untouched, state.current(), "combo-box cell focus must not publish help");
            binder.setActiveRoute("");

            // Mode on + route active: focus follows, nothing opens, focus is left where the user put it.
            binder.setActiveRoute("op_gen_hash");
            second.requestFocus();
            assertSame(second, stage.getScene().getFocusOwner());
            assertEquals("select_algorithm", state.current().actionId());
            assertEquals("algorithm", state.current().fieldId());
            first.requestFocus();
            assertEquals("calculate", state.current().actionId());
            assertEquals("input", state.current().fieldId());
            assertSame(first, stage.getScene().getFocusOwner(), "following focus must not move it");
            assertTrue(opened.isEmpty(), "following focus must never open a help surface");

            // Mode off: focus is ignored, Shift+F1 still publishes and opens (and is consumed).
            binder.setEnabled(false);
            second.requestFocus();
            assertEquals("calculate", state.current().actionId());
            javafx.scene.input.KeyEvent shiftF1 = new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.F1, true, false, false, false);
            AtomicInteger bubbled = new AtomicInteger();
            box.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> bubbled.incrementAndGet());
            javafx.event.Event.fireEvent(second, shiftF1);
            assertEquals(0, bubbled.get(), "Shift+F1 is consumed by the control");
            assertEquals(1, opened.size());
            assertEquals("select_algorithm", opened.get(0).actionId());
            assertEquals("select_algorithm", state.current().actionId());
            assertSame(second, stage.getScene().getFocusOwner());

            // Plain F1 and other combinations are not ours.
            javafx.scene.input.KeyEvent plainF1 = new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.F1, false, false, false, false);
            javafx.event.Event.fireEvent(second, plainF1);
            assertEquals(1, bubbled.get(), "plain F1 bubbles up untouched");
            assertEquals(1, opened.size());

            // close() removes every listener, handler, id and accessible text it added.
            binder.close();
            binder.setActiveRoute("op_gen_hash"); binder.setEnabled(true);
            HelpContext afterClose = state.current();
            first.requestFocus();
            ContextualHelpTestSupport.shiftF1(first);
            assertEquals(afterClose, state.current());
            assertEquals(1, opened.size());
            assertTrue(ContextualHelpTestSupport.bindingIds(first).isEmpty());
            assertEquals("Own label", first.getAccessibleText());
            assertNull(second.getAccessibleText());
            stage.close();
        });
    }

    @Test void modeOnLeavesNoPerControlHelpButtonsAndFocusKeepsTheFocusOwner() throws Exception {
        for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
            Object[] refs = new Object[3];
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    I18nService.getInstance().setPreference(language);
                    var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                    Parent root = loader.load();
                    ModernMainController shell = loader.getController();
                    Stage owner = new Stage(); owner.setScene(new Scene(root, 1800, 900)); owner.show(); owner.requestFocus();
                    root.applyCss(); root.layout();
                    refs[0] = root; refs[1] = shell; refs[2] = owner;
                } catch (Exception error) { throw new AssertionError(error); }
            });
            assumeWindowFocused((Stage) refs[2]);
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    Parent root = (Parent) refs[0]; ModernMainController shell = (ModernMainController) refs[1]; Stage owner = (Stage) refs[2];
                    CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                    mode.setSelected(true); shell.handleToggleContextualHelpMode();
                    HelpContextState state = field(shell, "contextualHelpState");
                    for (String route : List.of("Hashing", "Symmetric Ciphers", "File Cipher (Streaming)", "JWE (Encrypted)",
                            "Encrypted PIN Blocks", "OpenPGP (GPG Compatible)", "Process Designer", "Batch Runner", "Clipboard Shelf")) {
                        shell.navigateTo(route);
                        root.applyCss(); root.layout();
                        assertTrue(ContextualHelpTestSupport.contextHelpButtons(root).isEmpty(), "context-help-button nodes on " + route);
                        assertTrue(ContextualHelpTestSupport.boundControls(root).stream()
                                .noneMatch(node -> node instanceof Button button && "?".equals(button.getText())),
                                "no ? button may be bound on " + route);
                    }

                    shell.navigateTo("Hashing");
                    root.applyCss(); root.layout();
                    shell.handleShowOperationHelp();
                    Node input = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_hash_calculate_input");
                    Node algorithm = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_hash_select_algorithm_algorithm");
                    assertNotNull(input); assertNotNull(algorithm);
                    VBox help = field(shell, "contextualHelpContent");
                    String algorithmLabel = I18nService.getInstance().text("help.field.op_gen_hash.algorithm.label");
                    algorithm.requestFocus();
                    assertSame(algorithm, owner.getScene().getFocusOwner());
                    assertEquals("algorithm", state.current().fieldId());
                    assertTrue(labels(help).stream().anyMatch(text -> text.contains(algorithmLabel)),
                            "an already open help panel refreshes by itself: " + labels(help));
                    input.requestFocus();
                    assertSame(input, owner.getScene().getFocusOwner(), "following focus must not move it");
                    assertEquals("input", state.current().fieldId());
                    assertNull(field(shell, "contextualHelpWindowCoordinator"), "following focus must not open a help window");
                    mode.setSelected(false); shell.handleToggleContextualHelpMode();
                    shell.shutdown(); owner.close(); owner.setScene(null);
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }

    /** Focus-following needs a focused window; on a display that never grants it the check is skipped, not failed. */
    private static void assumeWindowFocused(Stage stage) throws Exception {
        boolean[] focused = new boolean[1];
        for (int attempt = 0; attempt < 40 && !focused[0]; attempt++) {
            UiTestLifecycleExtension.onFx(() -> focused[0] = stage.isFocused());
            if (!focused[0]) Thread.sleep(50);
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(focused[0], "the test window never became focused on this display");
    }

    private static List<String> labels(VBox content) {
        return content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .map(Label::getText).filter(java.util.Objects::nonNull).toList();
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
}
