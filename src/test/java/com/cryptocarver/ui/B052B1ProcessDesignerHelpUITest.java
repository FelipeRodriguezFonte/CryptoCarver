package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
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
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class B052B1ProcessDesignerHelpUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void generalControlsAndRepresentativeNodesExposeSafeHelpInBothLocalesAndWidths() throws Exception {
        for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
            for (double width : List.of(1800.0, 900.0)) {
                UiTestLifecycleExtension.onFx(() -> {
                    AppSettings original = AppSettings.getInstance();
                    Stage stage = null;
                    try {
                        AppSettings.setInstanceForTesting(new AppSettings(Files.createTempDirectory("b052b1-ui")));
                        I18nService.getInstance().setPreference(language);
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load();
                        ModernMainController shell = loader.getController();
                        stage = new Stage(); stage.setScene(new Scene(root, width, 900)); stage.show();
                        root.applyCss(); root.layout();
                        shell.navigateTo("Process Designer");
                        CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                        mode.setSelected(true); shell.handleToggleContextualHelpMode();
                        root.applyCss(); root.layout();
                        ProcessDesignerController designer = field(shell, "processDesignerContainerController");
                        HelpContextState state = field(shell, "contextualHelpState");
                        var processName = (javafx.scene.control.TextField) field(designer, "processNameField");
                        var executionTrace = (javafx.scene.control.TextArea) field(designer, "executionOutputArea");
                        processName.setText("B052B1_PROCESS_NAME_PRIVATE");
                        executionTrace.setText("B052B1_RESULT_PRIVATE");
                        @SuppressWarnings("unchecked") var existingNodes = (List<com.cryptocarver.model.process.ProcessDefinition.Node>) field(designer, "nodes");
                        @SuppressWarnings("unchecked") var existingConnections = (List<com.cryptocarver.model.process.ProcessDefinition.Connection>) field(designer, "connections");
                        int initialNodeCount = existingNodes.size(), initialConnectionCount = existingConnections.size();
                        javafx.scene.control.TableView<?> executionStatusTable = field(designer, "executionStatusTable");
                        int initialStatusRows = executionStatusTable.getItems().size();

                        for (String actionField : List.of("run_run_mode", "run_progress", "run_status_summary", "dry_run_validation", "cancel_progress",
                                "load_process_file", "save_process_file", "save_process_name", "edit_menu_canvas", "presets_canvas",
                                "connect_ports", "reverse_connection_ports",
                                "delete_selection", "clear_canvas", "toggle_inspector_inspector", "focus_mode_focus_mode",
                                "zoom_in_zoom", "zoom_fit_zoom", "snap_to_grid_snap_to_grid", "expand_results_result",
                                "open_window_canvas", "save_node_settings_parameters", "search_palette_palette")) {
                            javafx.scene.Node helpButton = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_" + actionField);
                            assertNotNull(helpButton, actionField + " help button at width=" + width);
                            assertTrue(helpButton.isManaged() && helpButton.isVisible(), actionField);
                            assertNotNull(helpButton.getAccessibleHelp());
                            assertFalse(helpButton.getAccessibleHelp().contains("help.action."), actionField);
                        }
                        ContextualHelpTestSupport.shiftF1(ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_edit_menu_canvas"));
                        assertEquals("edit_menu", state.current().actionId());
                        assertRenderedContains(helpContent(shell, width), language == LanguagePreference.ES ? "Delete/Backspace" : "Delete/Backspace");
                        assertRenderedContains(helpContent(shell, width), language == LanguagePreference.ES ? "atajo+Z" : "Shortcut+Z");
                        ContextualHelpTestSupport.shiftF1(ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_presets_canvas"));
                        assertEquals("presets", state.current().actionId());
                        javafx.scene.Node paletteButton = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_node_file_input_node_purpose");
                        assertNotNull(paletteButton, "Node palette entries need direct, keyboard-accessible help");
                        ContextualHelpTestSupport.shiftF1(paletteButton);
                        assertEquals("node_file_input", state.current().actionId());
                        assertEquals("FILE_INPUT", state.current().selections().get(HelpContext.Selection.PROCESS_NODE_TYPE).code());
                        assertTrue(state.current().selections().values().stream().noneMatch(value ->
                                value.code().contains("/Users/") || value.code().contains("PRIVATE")));
                        assertRenderedContains(helpContent(shell, width), language == LanguagePreference.ES ? "ruta inexistente" : "missing path");

                        var add = ProcessDesignerController.class.getDeclaredMethod("addNode", String.class, String.class, double.class, double.class);
                        add.setAccessible(true);
                        for (String type : List.of("FILE_INPUT", "BASE58CHECK_DECODE", "ASSERT_EQUALS", "CHECK_DIGIT_VERIFY", "BYTE_STATISTICS")) {
                            var node = (com.cryptocarver.model.process.ProcessDefinition.Node) add.invoke(designer, type, type, 20.0, 20.0);
                            designer.select(node);
                            if (type.equals("FILE_INPUT")) {
                                ((javafx.scene.control.TextField) designer.getInspectorControl("path")).setText("B052B1_PATH_PRIVATE");
                                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> readMode =
                                        (javafx.scene.control.ComboBox<String>) designer.getInspectorControl("readMode");
                                readMode.setValue("TEXT");
                                assertTrue(designer.getInspectorGroup("charset").isVisible()
                                        && designer.getInspectorGroup("charset").isManaged(), "charset applies in TEXT mode");
                                readMode.setValue("BINARY");
                                assertFalse(designer.getInspectorGroup("charset").isVisible()
                                        || designer.getInspectorGroup("charset").isManaged(), "charset is hidden in BINARY mode");
                            }
                            root.applyCss(); root.layout();
                            assertEquals(com.cryptocarver.model.help.B05HelpCatalog.nodeActionId(type), state.current().actionId());
                            assertEquals(type, state.current().selections().get(HelpContext.Selection.PROCESS_NODE_TYPE).code());
                            javafx.scene.Node paletteTypeHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_node_"
                                    + type.toLowerCase(java.util.Locale.ROOT) + "_node_purpose");
                            assertNotNull(paletteTypeHelp, type + " should expose its own rendered example");
                            ContextualHelpTestSupport.shiftF1(paletteTypeHelp);
                            String[] nodeFacts = switch (type) {
                                case "FILE_INPUT" -> language == LanguagePreference.ES
                                        ? new String[]{"BINARY", "ruta inexistente"} : new String[]{"BINARY", "missing path"};
                                case "BASE58CHECK_DECODE" -> new String[]{"checksum", language == LanguagePreference.ES ? "rechaza" : "rejects"};
                                case "ASSERT_EQUALS" -> new String[]{"CAFE", language == LanguagePreference.ES ? "falla" : "fails"};
                                case "CHECK_DIGIT_VERIFY" -> new String[]{"79927398714", "false"};
                                default -> new String[]{language == LanguagePreference.ES ? "tres bytes" : "three bytes", "FF"};
                            };
                            assertRenderedContains(helpContent(shell, width), nodeFacts);
                            var descriptor = com.cryptocarver.model.process.NodeCatalog.getDescriptor(type).orElseThrow();
                            if (!descriptor.parameters().isEmpty()) {
                                String parameter = descriptor.parameters().get(0).key();
                                javafx.scene.Node fieldHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_"
                                        + com.cryptocarver.model.help.B05HelpCatalog.nodeActionId(type) + "_" + parameter);
                                assertNotNull(fieldHelp, type + "." + parameter);
                                ContextualHelpTestSupport.shiftF1(fieldHelp);
                                assertEquals(parameter, state.current().fieldId());
                                assertEquals(type, state.current().selections().get(HelpContext.Selection.PROCESS_NODE_TYPE).code());
                                assertEquals(parameter, state.current().selections().get(HelpContext.Selection.PROCESS_NODE_PARAMETER).code());
                                if (type.equals("FILE_INPUT")) {
                                    assertRenderedContains(helpContent(shell, width), language == LanguagePreference.ES
                                            ? new String[]{"Ruta del archivo de origen", "predeterminada vacía"}
                                            : new String[]{"Source file path", "default is empty"});
                                }
                            }
                        }
                        var pad = (com.cryptocarver.model.process.ProcessDefinition.Node) add.invoke(designer, "PAD", "PAD", 40.0, 40.0);
                        designer.select(pad);
                        @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> padding =
                                (javafx.scene.control.ComboBox<String>) designer.getInspectorControl("paddingType");
                        padding.setValue("ZERO");
                        assertEquals("paddingType", state.current().selections().get(HelpContext.Selection.PROCESS_NODE_PARAMETER).code());
                        assertEquals("zero", state.current().selections().get(HelpContext.Selection.PROCESS_NODE_OPTION).code());
                        String graphBeforePadHelp = com.cryptocarver.model.process.ProcessDefinitionCodec.serialize(designer.toDefinition());
                        javafx.scene.Node padTypeHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_node_pad_paddingType");
                        assertNotNull(padTypeHelp); ContextualHelpTestSupport.shiftF1(padTypeHelp);
                        assertRenderedContains(helpContent(shell, width), language == LanguagePreference.ES
                                ? new String[]{"PKCS5 fija", "bloque completo", "ceros finales", "ZERO"}
                                : new String[]{"PKCS5 fixes", "full block", "trailing zeros", "ZERO"});

                        HelpContext beforeLocaleSwitch = state.current();
                        LanguagePreference otherLanguage = language == LanguagePreference.ES ? LanguagePreference.EN : LanguagePreference.ES;
                        I18nService.getInstance().setPreference(otherLanguage);
                        assertEquals("node_pad", state.current().actionId());
                        assertEquals("paddingType", state.current().fieldId());
                        assertEquals("zero", state.current().selections().get(HelpContext.Selection.PROCESS_NODE_OPTION).code());
                        assertEquals("PAD", state.current().selections().get(HelpContext.Selection.PROCESS_NODE_TYPE).code());
                        assertEquals(I18nService.getInstance().getLocale(), state.current().locale());
                        assertRenderedContains(helpContent(shell, width), otherLanguage == LanguagePreference.ES
                                ? new String[]{"PKCS5 fija", "ceros finales", "Rellena con ceros"}
                                : new String[]{"PKCS5 fixes", "trailing zeros", "Pads with zero"});
                        I18nService.getInstance().setPreference(language);
                        assertEquals(beforeLocaleSwitch.actionId(), state.current().actionId());
                        assertEquals(beforeLocaleSwitch.fieldId(), state.current().fieldId());
                        assertEquals(beforeLocaleSwitch.selections(), state.current().selections());

                        javafx.scene.Node deleteHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_delete_selection");
                        assertNotNull(deleteHelp, "Delete selected needs direct contextual help");
                        ContextualHelpTestSupport.shiftF1(deleteHelp);
                        assertEquals("delete", state.current().actionId(), "Shift+F1 on the focused delete control shows its help without deleting");
                        assertRenderedContains(helpContent(shell, width), language == LanguagePreference.ES
                                ? new String[]{"conexión seleccionada", "conexiones incidentes", "Undo"}
                                : new String[]{"selected connection", "incident connection", "Undo"});
                        assertEquals("B052B1_PROCESS_NAME_PRIVATE", processName.getText());
                        assertEquals("B052B1_RESULT_PRIVATE", executionTrace.getText());
                        assertEquals(initialNodeCount + 6, existingNodes.size());
                        assertEquals(initialConnectionCount, existingConnections.size());
                        assertEquals(initialStatusRows, executionStatusTable.getItems().size(),
                                "Contextual queries must not execute any node");
                        assertEquals(graphBeforePadHelp,
                                com.cryptocarver.model.process.ProcessDefinitionCodec.serialize(designer.toDefinition()),
                                "Opening contextual help must preserve node settings, graph and connections");
                        assertFalse(state.current().selections().values().stream().anyMatch(value ->
                                value.code().contains("PRIVATE") || value.code().contains("B052B1")));

                        var source = (com.cryptocarver.model.process.ProcessDefinition.Node)
                                add.invoke(designer, "CONSOLE_INPUT", "CONSOLE_INPUT", 20.0, 20.0);
                        var target = (com.cryptocarver.model.process.ProcessDefinition.Node)
                                add.invoke(designer, "MODULAR_ARITHMETIC", "MODULAR_ARITHMETIC", 40.0, 40.0);
                        designer.selectedNodeIds.clear();
                        designer.selectedNodeIds.add(source.id);
                        designer.selectedNodeIds.add(target.id);
                        var updateSelection = ProcessDesignerController.class.getDeclaredMethod("updateSelectionUi");
                        updateSelection.setAccessible(true); updateSelection.invoke(designer);
                        root.applyCss(); root.layout();
                        String graphBeforePortHelp = com.cryptocarver.model.process.ProcessDefinitionCodec.serialize(designer.toDefinition());
                        javafx.scene.Node targetPortHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_connect_target_port");
                        assertNotNull(targetPortHelp, "dynamic multi-port menu help");
                        assertTrue(targetPortHelp.isVisible() && targetPortHelp.isManaged());
                        ContextualHelpTestSupport.shiftF1(targetPortHelp);
                        assertEquals("connect", state.current().actionId());
                        assertEquals("target_port", state.current().fieldId());
                        assertEquals(graphBeforePortHelp,
                                com.cryptocarver.model.process.ProcessDefinitionCodec.serialize(designer.toDefinition()),
                                "Consulting target-port help must not create a connection");

                        shell.navigateTo("Hashing");
                        shell.navigateTo("Process Designer");
                        root.applyCss(); root.layout();
                        assertEquals("PAD", state.current().selections().get(HelpContext.Selection.PROCESS_NODE_TYPE).code());
                        assertEquals(1, ContextualHelpTestSupport.controls(root, "contextHelp_op_gen_process_designer_run_run_mode").size(),
                                "Returning to the route must not duplicate action help listeners/buttons");
                        assertRenderedContains(helpContent(shell, width), language == LanguagePreference.ES
                                ? new String[]{"Tipo de relleno", "PKCS7"} : new String[]{"Padding type", "PKCS7"});
                        assertFalse(com.cryptocarver.model.help.B05HelpCatalog.find("op_gen_process_designer").complete());
                        if (width < 1100) {
                            ContextualHelpWindowCoordinator window = field(shell, "contextualHelpWindowCoordinator");
                            assertNotNull(window); assertTrue(window.isShowing(), "compact help opens on first consultation");
                            window.windowForTesting().close();
                            shell.handleShowOperationHelp();
                            assertTrue(window.isShowing(), "compact help reopens with the current node/option");
                            shell.navigateTo("Hashing"); shell.navigateTo("Process Designer");
                            assertTrue(window.isShowing(), "compact help survives navigation away and back");
                        }
                        mode.setSelected(false); shell.handleToggleContextualHelpMode();
                        javafx.scene.Node currentPaletteButton = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_process_designer_node_file_input_node_purpose");
                        assertNull(currentPaletteButton, "Palette entries stop being help targets once the mode is off");
                        HelpContext inactiveContext = state.current();
                        deleteHelp.requestFocus();
                        assertEquals(inactiveContext, state.current(), "Focus cannot publish help while contextual mode is off");
                        assertEquals(initialNodeCount + 8, existingNodes.size());
                        assertEquals(initialConnectionCount, existingConnections.size());
                        assertEquals("B052B1_PROCESS_NAME_PRIVATE", processName.getText());
                        assertEquals("B052B1_RESULT_PRIVATE", executionTrace.getText());
                        shell.shutdown();
                    } catch (Exception error) { throw new AssertionError(error); }
                    finally {
                        try { if (stage != null) { stage.close(); stage.setScene(null); } }
                        finally { AppSettings.setInstanceForTesting(original); }
                    }
                }, 90);
            }
        }
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(instance);
    }

    private static VBox helpContent(ModernMainController shell, double width) throws Exception {
        if (width >= 1100) return field(shell, "contextualHelpContent");
        ContextualHelpWindowCoordinator window = field(shell, "contextualHelpWindowCoordinator");
        javafx.scene.control.ScrollPane scroll = (javafx.scene.control.ScrollPane) window.windowForTesting().getScene().getRoot();
        return (VBox) scroll.getContent();
    }

    private static void assertRenderedContains(VBox content, String... fragments) {
        String rendered = visibleLabels(content).stream().reduce((left, right) -> left + "\n" + right).orElse("");
        for (String fragment : fragments)
            assertTrue(rendered.contains(fragment), "Expected visible help fragment `" + fragment + "`; rendered text was:\n" + rendered);
    }

    private static List<String> visibleLabels(Parent parent) {
        List<String> labels = new java.util.ArrayList<>();
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Label label && label.getText() != null) labels.add(label.getText());
            if (child instanceof Parent nested) labels.addAll(visibleLabels(nested));
        }
        return labels;
    }

    private static void fireKey(Button button, KeyCode code, boolean pressed) {
        Event.fireEvent(button, new KeyEvent(pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                "", "", code, false, false, false, false));
    }
}
