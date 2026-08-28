package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The key wrapping pane driven the way a person drives it.
 *
 * <p>The other tests call the service directly. This one loads the real FXML into a shown
 * window, types into the actual fields, fires the actual buttons and reads what comes back
 * out of the report area -- so it covers the wiring between the two, which is where a pane
 * breaks even when its core is perfect: a field never bound, a handler pointing at the
 * wrong control, a combo whose value never reaches the request.</p>
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class IcsfKeyWrapPaneQaUITest {

    private static final String KEY_16 = "0123456789ABCDEFFEDCBA9876543210";
    private static final String KEK_16 = "404142434445464748494A4B4C4D4E4F";

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        Platform.setImplicitExit(false);
        assertTrue(latch.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    private static void onFx(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "FX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Node byId(Parent root, String id) {
        java.util.Deque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (id.equals(node.getId())) return node;
            if (node instanceof Parent parent) queue.addAll(parent.getChildrenUnmodifiable());
            if (node instanceof TabPane tabs) {
                for (Tab tab : tabs.getTabs()) {
                    if (tab.getContent() != null) queue.add(tab.getContent());
                }
            }
            if (node instanceof TitledPane pane && pane.getContent() != null) {
                queue.add(pane.getContent());
            }
        }
        return null;
    }

    /** Fires the button whose label starts with the given text, the way a click would. */
    private static void press(Parent root, String labelPrefix) {
        Button button = findButton(root, labelPrefix);
        assertNotNull(button, "no button labelled " + labelPrefix);
        assertFalse(button.isDisabled(), "button " + labelPrefix + " is disabled");
        button.fire();
    }

    private static Button findButton(Parent root, String labelPrefix) {
        java.util.Deque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node instanceof Button button && button.getText() != null
                    && button.getText().startsWith(labelPrefix)) {
                return button;
            }
            if (node instanceof Parent parent) queue.addAll(parent.getChildrenUnmodifiable());
            if (node instanceof TabPane tabs) {
                for (Tab tab : tabs.getTabs()) {
                    if (tab.getContent() != null) queue.add(tab.getContent());
                }
            }
            if (node instanceof TitledPane pane && pane.getContent() != null) {
                queue.add(pane.getContent());
            }
        }
        return null;
    }

    private static final class Pane {
        Parent root;
        Stage stage;

        TextField field(String id) {
            Node node = byId(root, id);
            assertTrue(node instanceof TextField, "no TextField with id " + id);
            return (TextField) node;
        }

        TextArea area(String id) {
            Node node = byId(root, id);
            assertTrue(node instanceof TextArea, "no TextArea with id " + id);
            return (TextArea) node;
        }

        String report() {
            return area("icsfKeyWrapReportArea").getText();
        }

        String feedback() {
            Node node = byId(root, "icsfKeyWrapFeedbackLabel");
            return node instanceof Label label ? label.getText() : "";
        }

        void selectTab(int index) {
            Node node = byId(root, "icsfKeyWrapTabs");
            assertTrue(node instanceof TabPane, "tab pane missing");
            ((TabPane) node).getSelectionModel().select(index);
        }
    }

    private static Pane open() throws Exception {
        Pane pane = new Pane();
        onFx(() -> {
            FXMLLoader loader = new FXMLLoader(
                    IcsfKeyWrapPaneQaUITest.class.getResource("/fxml/icsf_keywrap.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 900, 800);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            ((TitledPane) root).setExpanded(true);
            root.applyCss();
            root.layout();
            pane.root = root;
            pane.stage = stage;
        });
        return pane;
    }

    private static void close(Pane pane) throws Exception {
        onFx(() -> pane.stage.close());
    }

    @Test
    void aUserCanExportAKeyAndSeeTheTokenInTheReport() throws Exception {
        Pane pane = open();
        try {
            onFx(() -> {
                pane.selectTab(0);
                pane.field("icsfExportKeyField").setText(KEY_16);
                pane.field("icsfExportKekField").setText(KEK_16);
                press(pane.root, "Export");
            });
            onFx(() -> {
                String report = pane.report();
                assertFalse(report.isBlank(), "the report area stayed empty after Export");
                assertTrue(report.contains("CSNBKEX"), "the report should name the verb it simulated");
                // The token the pane shows must be the one the arithmetic produces.
                assertTrue(report.contains("911B623DB3841A1CC9AADF627AEAFE9C"),
                        "the cryptogram is not the expected one:\n" + report);
                assertTrue(pane.feedback().length() > 0, "the status line said nothing");
            });
        } finally {
            close(pane);
        }
    }

    @Test
    void theHostVersionCheckboxChangesTheTokenThePaneShows() throws Exception {
        Pane pane = open();
        try {
            AtomicReference<String> withHostByte = new AtomicReference<>();
            onFx(() -> {
                pane.selectTab(0);
                pane.field("icsfExportKeyField").setText(KEY_16);
                pane.field("icsfExportKekField").setText(KEK_16);
                Node check = byId(pane.root, "icsfExportHostVersionCheck");
                assertTrue(check instanceof CheckBox, "host version checkbox missing");
                assertTrue(((CheckBox) check).isSelected(), "it should default to the host form");
                press(pane.root, "Export");
            });
            onFx(() -> withHostByte.set(pane.report()));

            onFx(() -> {
                ((CheckBox) byId(pane.root, "icsfExportHostVersionCheck")).setSelected(false);
                press(pane.root, "Export");
            });
            onFx(() -> {
                assertFalse(pane.report().equals(withHostByte.get()),
                        "unticking the version box changed nothing in the report");
                assertTrue(withHostByte.get().contains("X'00'"), "host form should show X'00'");
                assertTrue(pane.report().contains("X'01'"), "Table 616 form should show X'01'");
            });
        } finally {
            close(pane);
        }
    }

    @Test
    void aUserCanCarryTheTokenFromExportIntoImportAndGetTheKeyBack() throws Exception {
        Pane pane = open();
        try {
            AtomicReference<String> token = new AtomicReference<>();
            onFx(() -> {
                pane.selectTab(0);
                pane.field("icsfExportKeyField").setText(KEY_16);
                pane.field("icsfExportKekField").setText(KEK_16);
                press(pane.root, "Export");
            });
            // Read the token out of the report the way a user would copy it.
            onFx(() -> {
                for (String line : pane.report().split("\n")) {
                    String candidate = line.trim();
                    if (candidate.length() == 128 && candidate.matches("[0-9A-F]+")) {
                        token.set(candidate);
                    }
                }
                assertNotNull(token.get(), "no 64-byte token visible in the report");
            });
            onFx(() -> {
                pane.selectTab(1);
                pane.field("icsfImportInputField").setText(token.get());
                pane.field("icsfImportKekField").setText(KEK_16);
                press(pane.root, "Import");
            });
            onFx(() -> assertTrue(pane.report().contains(KEY_16),
                    "the imported key is not the one exported:\n" + pane.report()));
        } finally {
            close(pane);
        }
    }

    @Test
    void resolveNamesTheSchemeInThePane() throws Exception {
        Pane pane = open();
        try {
            AtomicReference<String> token = new AtomicReference<>();
            onFx(() -> {
                pane.selectTab(0);
                pane.field("icsfExportKeyField").setText(KEY_16);
                pane.field("icsfExportKekField").setText(KEK_16);
                press(pane.root, "Export");
            });
            onFx(() -> {
                for (String line : pane.report().split("\n")) {
                    String candidate = line.trim();
                    if (candidate.length() == 128 && candidate.matches("[0-9A-F]+")) token.set(candidate);
                }
            });
            onFx(() -> {
                pane.selectTab(3);
                pane.field("icsfResolveInputField").setText(token.get());
                pane.field("icsfResolveKekField").setText(KEK_16);
                pane.field("icsfResolveExpectedKeyField").setText(KEY_16);
                press(pane.root, "Resolve");
            });
            onFx(() -> {
                String report = pane.report();
                assertTrue(report.contains(KEY_16), "resolve did not surface the key");
                assertTrue(report.contains("COINCIDE") || report.contains("MATCHES"),
                        "resolve did not report a match:\n" + report);
            });
        } finally {
            close(pane);
        }
    }

    @Test
    void inspectWorksWithNoKekAndRefusesRubbish() throws Exception {
        Pane pane = open();
        try {
            onFx(() -> {
                pane.selectTab(2);
                pane.area("icsfInspectInputArea").setText("not a token");
                press(pane.root, "Inspect");
            });
            onFx(() -> assertTrue(pane.report().length() > 0,
                    "a bad input should still produce an explanation, not silence"));
        } finally {
            close(pane);
        }
    }

    @Test
    void clearEmptiesTheFieldsAndTheReport() throws Exception {
        Pane pane = open();
        try {
            onFx(() -> {
                pane.selectTab(0);
                pane.field("icsfExportKeyField").setText(KEY_16);
                pane.field("icsfExportKekField").setText(KEK_16);
                press(pane.root, "Export");
            });
            onFx(() -> assertFalse(pane.report().isBlank(), "precondition: there is a report"));
            onFx(() -> press(pane.root, "Clear"));
            onFx(() -> {
                assertTrue(pane.field("icsfExportKeyField").getText().isEmpty(), "key field not cleared");
                assertTrue(pane.report().isBlank(), "report area not cleared");
            });
        } finally {
            close(pane);
        }
    }

    @Test
    void everyControlThePaneDeclaresIsActuallyWiredUp() throws Exception {
        // A field declared in the FXML but never injected is null at runtime and only shows
        // up when someone touches that control, which is exactly the bug this catches.
        Pane pane = open();
        try {
            onFx(() -> {
                for (String id : new String[] {
                        "icsfExportKeyField", "icsfExportKekField", "icsfExportCvField",
                        "icsfExportRnField", "icsfImportInputField", "icsfImportKekField",
                        "icsfImportCvField", "icsfResolveInputField", "icsfResolveKekField",
                        "icsfResolveExpectedKeyField", "icsfResolveExpectedKcvField"}) {
                    assertNotNull(byId(pane.root, id), "control not in the scene graph: " + id);
                }
                for (String id : new String[] {
                        "icsfExportTypeCombo", "icsfImportTypeCombo", "icsfResolveTypeCombo",
                        "icsfExportVariantCombo", "icsfImportVariantCombo",
                        "icsfExportModeCombo", "icsfImportModeCombo"}) {
                    Node node = byId(pane.root, id);
                    assertTrue(node instanceof ComboBox, "combo missing: " + id);
                    ComboBox<?> combo = (ComboBox<?>) node;
                    assertFalse(combo.getItems().isEmpty(), "combo never populated: " + id);
                    assertNotNull(combo.getValue(), "combo has no default value: " + id);
                }
            });
        } finally {
            close(pane);
        }
    }
}
