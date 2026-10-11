package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.B05HelpCatalog;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class B05ContextualHelpUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void genericAndNodeHelpRunInTheShellAtBothLocalesAndWidthsWithoutReadingFormValues() throws Exception {
        for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
            for (double width : List.of(1800.0, 900.0)) {
                final Object[] refs = new Object[3];
                UiTestLifecycleExtension.onFx(() -> {
                    try {
                        I18nService.getInstance().setPreference(language);
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load();
                        ModernMainController shell = loader.getController();
                        Stage owner = new Stage(); owner.setScene(new Scene(root, width, 900)); owner.show();
                        root.applyCss(); root.layout();
                        shell.navigateTo("Hashing");
                        CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                        mode.setSelected(true); shell.handleToggleContextualHelpMode();
                        root.applyCss(); root.layout();
                        GenericController generic = field(shell, "genericContainerController");
                        generic.fillHashInput("B05_PRIVATE_INPUT_SENTINEL");
                        var input = (javafx.scene.control.TextArea) field(generic, "hashInputArea");
                        var output = (javafx.scene.control.TextArea) field(generic, "hashOutputArea");
                        output.setText("B05_RESULT_SENTINEL");
                        javafx.scene.Node hashHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_hash_calculate_input");
                        assertNotNull(hashHelp, "Hashing ? button at " + width);
                        assertTrue(hashHelp.isVisible() && hashHelp.isManaged());
                        assertNotNull(root.lookup("#help_handleCalculateHash"), "Hash action target at " + width);
                        javafx.scene.Node calculateHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_hash_calculate_action");
                        javafx.scene.Node templateHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_hash_apply_template_template");
                        assertNotNull(calculateHelp, "Hash action ? button at " + width);
                        assertNotNull(templateHelp, "Hash template ? button at " + width);
                        assertTrue(calculateHelp.isVisible() && calculateHelp.isManaged());
                        assertTrue(templateHelp.isVisible() && templateHelp.isManaged());
                        ContextualHelpTestSupport.shiftF1(hashHelp);
                        HelpContextState state = field(shell, "contextualHelpState");
                        assertEquals("op_gen_hash", state.current().operationId());
                        assertEquals("calculate", state.current().actionId());
                        assertEquals("input", state.current().fieldId());
                        assertTrue(state.current().selections().isEmpty() || state.current().selections().values().stream()
                                .noneMatch(value -> value.code().contains("PRIVATE_INPUT_SENTINEL")));
                        VBox help = field(shell, "contextualHelpContent");
                        assertTrue(containsText(help, I18nService.getInstance().text("help.action.op_gen_hash.calculate.description")));
                        assertFalse(containsHelpKey(help));
                        assertEquals("B05_PRIVATE_INPUT_SENTINEL", input.getText());
                        assertEquals("B05_RESULT_SENTINEL", output.getText());

                        shell.navigateTo("Batch Runner");
                        generic = field(shell, "genericContainerController");
                        var batchInput = (javafx.scene.control.TextArea) field(generic, "batchInputArea");
                        var batchResult = (javafx.scene.control.TextArea) field(generic, "batchResultArea");
                        var batchOperation = (javafx.scene.control.ComboBox<String>) field(generic, "batchOperationCombo");
                        batchInput.setText("B05_BATCH_PRIVATE_SENTINEL");
                        batchResult.setText("B05_BATCH_RESULT_SENTINEL");
                        batchOperation.setValue(com.cryptocarver.model.batch.BatchOperationCatalog.getAvailableOperations().get(0));
                        javafx.scene.Node batchHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_batch_select_operation_operation");
                        assertNotNull(batchHelp, "Batch operation ? button at " + width);
                        assertTrue(batchHelp.isVisible() && batchHelp.isManaged());
                        ContextualHelpTestSupport.shiftF1(batchHelp);
                        assertEquals("op_gen_batch", state.current().operationId());
                        assertEquals("select_operation", state.current().actionId());
                        assertEquals("operation", state.current().fieldId());
                        assertEquals(com.cryptocarver.model.batch.BatchOperationCatalog.getAvailableOperations().get(0)
                                        .trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]+", "_"),
                                state.current().selections().get(HelpContext.Selection.BATCH_OPERATION).code());
                        assertFalse(state.current().selections().values().stream()
                                .anyMatch(value -> value.code().contains("PRIVATE_SENTINEL")));
                        assertEquals("B05_BATCH_PRIVATE_SENTINEL", batchInput.getText());
                        assertEquals("B05_BATCH_RESULT_SENTINEL", batchResult.getText());

                        shell.navigateTo("Process Designer");
                        ProcessDesignerController designer = field(shell, "processDesignerContainerController");
                        assertNotNull(designer);
                        assertTrue(state.current().actionId().startsWith("node_"), state.current().actionId());
                        var type = state.current().selections().get(HelpContext.Selection.PROCESS_NODE_TYPE);
                        assertNotNull(type, "Selected node type must have priority over general Process Designer help");
                        var nodeHelp = B05HelpCatalog.find("op_gen_process_designer").actions().stream()
                                .filter(action -> action.id().equals(state.current().actionId())).findFirst().orElseThrow();
                        assertFalse(I18nService.getInstance().text(nodeHelp.descriptionKey()).equals(nodeHelp.descriptionKey()));
                        @SuppressWarnings("unchecked") var nodes = (List<com.cryptocarver.model.process.ProcessDefinition.Node>) field(designer, "nodes");
                        assertTrue(nodes.size() >= 2);
                        designer.select(nodes.get(1));
                        assertEquals(B05HelpCatalog.nodeActionId(nodes.get(1).type), state.current().actionId());
                        assertEquals(nodes.get(1).type, state.current().selections()
                                .get(HelpContext.Selection.PROCESS_NODE_TYPE).code());
                        designer.select(nodes.get(0));
                        assertEquals(B05HelpCatalog.nodeActionId(nodes.get(0).type), state.current().actionId());
                        designer.select(nodes.get(1));
                        assertEquals(B05HelpCatalog.nodeActionId(nodes.get(1).type), state.current().actionId(),
                                "Repeated selection must keep the current node help context");

                        mode.setSelected(false); shell.handleToggleContextualHelpMode();
                        HelpContext beforeModeOffFocus = state.current();
                        hashHelp.requestFocus();
                        assertEquals(beforeModeOffFocus, state.current(), "Focus must not publish help once the mode is off");
                        refs[0] = shell; refs[1] = owner; refs[2] = state;
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                UiTestLifecycleExtension.onFx(() -> {
                    ((ModernMainController) refs[0]).shutdown();
                    ((Stage) refs[1]).close(); ((Stage) refs[1]).setScene(null);
                });
            }
        }
    }

    @Test void batchControlsHaveDirectKeyboardHelpAndCompactWindowLifecyclePreservesWork() throws Exception {
        for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
            for (double width : List.of(1800.0, 900.0)) {
                final Object[] refs = new Object[4];
                UiTestLifecycleExtension.onFx(() -> {
                    try {
                        I18nService.getInstance().setPreference(language);
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load();
                        ModernMainController shell = loader.getController();
                        Stage owner = new Stage(); owner.setScene(new Scene(root, width, 900)); owner.show();
                        root.applyCss(); root.layout();
                        shell.navigateTo("Batch Runner");
                        CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                        mode.setSelected(true); shell.handleToggleContextualHelpMode();
                        root.applyCss(); root.layout();
                        GenericController generic = field(shell, "genericContainerController");
                        HelpContextState state = field(shell, "contextualHelpState");
                        var input = (javafx.scene.control.TextArea) field(generic, "batchInputArea");
                        var result = (javafx.scene.control.TextArea) field(generic, "batchResultArea");
                        var operation = (javafx.scene.control.ComboBox<String>) field(generic, "batchOperationCombo");
                        input.setText("B052A_PRIVATE_INPUT\n\"quoted, text\"");
                        result.setText("B052A_RESULT_SENTINEL");
                        String selectedOperation = "Base64 → UTF-8";
                        operation.setValue(selectedOperation);
                        javafx.scene.Node selectorHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_batch_select_operation_operation");
                        assertNotNull(selectorHelp, "operation selector help at " + width);
                        ContextualHelpTestSupport.shiftF1(selectorHelp);
                        assertEquals("select_operation", state.current().actionId());
                        assertEquals("operation", state.current().fieldId());
                        assertFalse(state.current().selections().values().stream().anyMatch(v -> v.code().contains("PRIVATE_INPUT")));
                        assertEquals("B052A_PRIVATE_INPUT\n\"quoted, text\"", input.getText());
                        assertEquals("B052A_RESULT_SENTINEL", result.getText());

                        VBox help = helpContent(shell, width);
                        String slug = com.cryptocarver.model.batch.BatchOperationCatalog.slug(selectedOperation);
                        String optionKey = "help.option.op_gen_batch.operation." + slug;
                        assertTrue(containsText(help, I18nService.getInstance().text(optionKey + ".example")), selectedOperation);
                        assertTrue(containsText(help, I18nService.getInstance().text(optionKey + ".error")), selectedOperation);
                        assertFalse(containsAny(help, "B052A_PRIVATE_INPUT", "B052A_RESULT_SENTINEL"));

                        for (String[] action : new String[][]{
                                {"load_input", "help_handleBrowseBatchInput", "action"},
                                {"run", "help_handleRunBatch", "action"},
                                {"dry_run", "help_handleDryRunBatch", "action"},
                                {"cancel", "help_handleCancelBatch", "progress"},
                                {"reset", "help_handleResetBatch", "input"},
                                {"export", "help_handleExportBatchResults", "results"}}) {
                            Button target = (Button) root.lookup("#" + action[1]);
                            javafx.scene.Node helpButton = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_batch_" + action[0] + "_" + action[2]);
                            assertNotNull(target, action[1]); assertNotNull(helpButton, action[0] + " contextual button");
                            assertTrue(helpButton.isVisible() && helpButton.isManaged(), action[0]);
                            ContextualHelpTestSupport.shiftF1(helpButton);
                            assertEquals(action[0], state.current().actionId(), action[0]);
                            assertFalse(containsAny(helpContent(shell, width), "B052A_PRIVATE_INPUT", "B052A_RESULT_SENTINEL"));
                            assertEquals("B052A_PRIVATE_INPUT\n\"quoted, text\"", input.getText());
                            assertEquals("B052A_RESULT_SENTINEL", result.getText());
                        }

                        for (String[] fieldHelp : new String[][]{
                                {"select_operation", "operation"}, {"load_input", "input_format"},
                                {"load_input", "input"}, {"run", "source_column"},
                                {"run", "destination_column"}, {"run", "stop_on_error"},
                                {"run", "progress"}, {"run", "status"}, {"export", "results"},
                                {"export", "export_format"}, {"cancel", "progress"}}) {
                            String id = "contextHelp_op_gen_batch_" + fieldHelp[0] + "_" + fieldHelp[1];
                            javafx.scene.Node fieldButton = ContextualHelpTestSupport.control(root, id);
                            assertNotNull(fieldButton, id);
                            ContextualHelpTestSupport.shiftF1(fieldButton);
                            assertEquals(fieldHelp[0], state.current().actionId(), id);
                            assertEquals(fieldHelp[1], state.current().fieldId(), id);
                            assertEquals("B052A_PRIVATE_INPUT\n\"quoted, text\"", input.getText());
                            assertEquals("B052A_RESULT_SENTINEL", result.getText());
                        }

                        if (width < 1100) {
                            ContextualHelpWindowCoordinator window = field(shell, "contextualHelpWindowCoordinator");
                            assertNotNull(window); assertTrue(window.isShowing());
                            window.windowForTesting().close();
                            shell.handleShowOperationHelp();
                            assertTrue(window.isShowing(), "compact help reopens on the current Batch context");
                            shell.navigateTo("Hashing"); shell.navigateTo("Batch Runner");
                            assertTrue(window.isShowing(), "compact help survives navigation and return");
                        }
                        refs[0] = shell; refs[1] = owner; refs[2] = mode; refs[3] = generic;
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                GenericController generic = (GenericController) refs[3];
                UiTestLifecycleExtension.onFx(() -> {
                    try {
                        var batchInput = (javafx.scene.control.TextArea) field(generic, "batchInputArea");
                        var batchFormat = (javafx.scene.control.ComboBox<String>) field(generic, "batchInputFormatCombo");
                        var batchOperation = (javafx.scene.control.ComboBox<String>) field(generic, "batchOperationCombo");
                        batchInput.setText("input\n4869\n"); batchFormat.setValue("CSV");
                        batchOperation.setValue("Hexadecimal → UTF-8");
                        generic.handleRunBatch();
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                awaitBatch(generic);
                UiTestLifecycleExtension.onFx(() -> {
                    try {
                        String completed = ((javafx.scene.control.TextArea) field(generic, "batchResultArea")).getText();
                        assertTrue(completed.contains("Hi"), completed);
                        var report = generic.lastBatchReport();
                        assertNotNull(report); assertEquals(1, report.succeeded());
                        javafx.scene.Node resultHelp = ContextualHelpTestSupport.control(((Stage) refs[1]).getScene().getRoot(),
                                "contextHelp_op_gen_batch_export_results");
                        assertNotNull(resultHelp); ContextualHelpTestSupport.shiftF1(resultHelp);
                        assertSame(report, generic.lastBatchReport(), "opening result help must not rerun Batch");
                        assertEquals(completed, ((javafx.scene.control.TextArea) field(generic, "batchResultArea")).getText());
                        var input = (javafx.scene.control.TextArea) field(generic, "batchInputArea");
                        assertEquals("input\n4869\n", input.getText());
                        assertEquals("export", ((HelpContextState) field(refs[0], "contextualHelpState")).current().actionId());
                        javafx.scene.Node inputHelp = ContextualHelpTestSupport.control(((Stage) refs[1]).getScene().getRoot(),
                                "contextHelp_op_gen_batch_load_input_input");
                        assertNotNull(inputHelp); ContextualHelpTestSupport.shiftF1(inputHelp);
                        assertSame(report, generic.lastBatchReport(), "opening input help after execution must not rerun Batch");
                        assertEquals(completed, ((javafx.scene.control.TextArea) field(generic, "batchResultArea")).getText());
                        assertEquals("input\n4869\n", input.getText());
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                UiTestLifecycleExtension.onFx(() -> {
                    ModernMainController shell = (ModernMainController) refs[0];
                    CheckMenuItem mode = (CheckMenuItem) refs[2];
                    mode.setSelected(false); shell.handleToggleContextualHelpMode();
                    javafx.scene.Node runHelp = ContextualHelpTestSupport.control(((Stage) refs[1]).getScene().getRoot(),
                            "contextHelp_op_gen_batch_run_action");
                    assertNotNull(runHelp);
                    HelpContextState modeOffState;
                    try { modeOffState = field(shell, "contextualHelpState"); }
                    catch (Exception error) { throw new AssertionError(error); }
                    HelpContext beforeFocus = modeOffState.current();
                    runHelp.requestFocus();
                    assertEquals(beforeFocus, modeOffState.current(), "Focus must not publish help once the mode is off");
                    shell.shutdown(); ((Stage) refs[1]).close(); ((Stage) refs[1]).setScene(null);
                });
            }
        }
    }

    private static boolean containsText(VBox root, String expected) {
        return root.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> expected.equals(label.getText()));
    }
    private static boolean containsHelpKey(VBox root) {
        return root.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> label.getText() != null && label.getText().startsWith("help."));
    }
    private static boolean containsAny(VBox root, String... needles) {
        return root.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .map(Label::getText).filter(java.util.Objects::nonNull)
                .anyMatch(value -> java.util.Arrays.stream(needles).anyMatch(value::contains));
    }
    @SuppressWarnings("unchecked") private static VBox helpContent(ModernMainController shell, double width) throws Exception {
        if (width >= 1100) return field(shell, "contextualHelpContent");
        ContextualHelpWindowCoordinator window = field(shell, "contextualHelpWindowCoordinator");
        javafx.scene.control.ScrollPane scroll = (javafx.scene.control.ScrollPane) window.windowForTesting().getScene().getRoot();
        return (VBox) scroll.getContent();
    }
    private static void fireKey(Button button, KeyCode code, boolean pressed) {
        KeyEvent event = new KeyEvent(pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                "", "", code, false, false, false, false);
        Event.fireEvent(button, event);
    }
    private static void awaitBatch(GenericController generic) throws Exception {
        for (int index = 0; index < 200; index++) {
            final javafx.concurrent.Task<?>[] task = new javafx.concurrent.Task<?>[1];
            UiTestLifecycleExtension.onFx(() -> task[0] = generic.activeBatchTask());
            if (task[0] == null) return;
            try { task[0].get(50, TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.TimeoutException ignored) { }
            catch (java.util.concurrent.ExecutionException error) { throw new AssertionError(error.getCause()); }
        }
        throw new AssertionError("Batch did not finish");
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
}
