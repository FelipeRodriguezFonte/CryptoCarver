package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The result surface's actions and format selector do what they say.
 *
 * <p>All five actions and the format selector shipped unwired: no controller ever set a handler
 * and nothing read the selector, so four buttons and a dropdown sat there doing nothing at all.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ResultPanelWiringUITest {

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    private static <T> T fx(java.util.function.Supplier<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                value.set(action.get());
            } catch (RuntimeException error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "FX thread did not run the action");
        if (failure.get() != null) throw failure.get();
        return value.get();
    }

    private static OperationResult publicResult(byte[] output) {
        return OperationResult.forOperation("Cipher")
                .output(output, OperationDetail.Classification.PUBLIC)
                .build();
    }

    @Test
    void theFormatSelectorReEncodesTheSameResult() throws Exception {
        byte[] output = {(byte) 0x00, (byte) 0xAB, (byte) 0xFF};
        String hex = fx(() -> {
            ResultPanel panel = new ResultPanel();
            panel.setResult(publicResult(output), SecretVisibilityProfile.FULL_LAB,
                    ResultPanel.Status.SUCCESS, 1);
            selectFormat(panel, "Hex");
            return outputText(panel);
        });
        assertEquals("00ABFF", hex);

        String base64 = fx(() -> {
            ResultPanel panel = new ResultPanel();
            panel.setResult(publicResult(output), SecretVisibilityProfile.FULL_LAB,
                    ResultPanel.Status.SUCCESS, 1);
            selectFormat(panel, "Base64");
            return outputText(panel);
        });
        assertEquals("AKv/", base64);
    }

    @Test
    void theFormatSelectorCannotRevealARedactedResult() throws Exception {
        OperationResult secret = OperationResult.forOperation("Key Generation")
                .output("super-secret-key".getBytes(StandardCharsets.UTF_8),
                        OperationDetail.Classification.SECRET)
                .build();

        for (String format : List.of("Texto", "Hex", "Base64")) {
            String shown = fx(() -> {
                ResultPanel panel = new ResultPanel();
                panel.setResult(secret, SecretVisibilityProfile.REDACTED, ResultPanel.Status.SUCCESS, 1);
                selectFormat(panel, format);
                return outputText(panel);
            });
            assertFalse(shown.contains("super-secret-key"),
                    "Choosing " + format + " must not decode a redacted result");
            assertFalse(shown.contains("73757065"),
                    "Choosing " + format + " must not hex-encode a redacted result");
        }
    }

    @Test
    void anActionNobodyWiredIsNotOffered() throws Exception {
        com.cryptocarver.service.I18nService i18n = com.cryptocarver.service.I18nService.getInstance();
        List<String> offered = fx(() -> labels(new ResultPanel()));
        assertEquals(List.of(i18n.text("resultPanel.action.copy")), offered,
                "Only Copy works without a shell, so it is the only action a bare panel shows");

        List<String> wired = fx(() -> {
            ResultPanel panel = new ResultPanel();
            panel.connectTo(() -> null);
            panel.setChainHandler(value -> { });
            return labels(panel);
        });
        assertEquals(List.of(
                i18n.text("resultPanel.action.copy"),
                i18n.text("resultPanel.action.shelf"),
                i18n.text("resultPanel.action.expand"),
                i18n.text("resultPanel.action.saveStep"),
                i18n.text("resultPanel.action.chain")
        ), wired);
    }

    @Test
    void theShellPerformsTheActionsOnTheCurrentResult() throws Exception {
        List<String> performed = new ArrayList<>();
        StatusReporter shell = new StatusReporter() {
            @Override public void updateStatus(String message) { }
            @Override public void updateInspector(String operation, byte[] input, byte[] output,
                                                  List<OperationDetail> details) { }
            @Override public void showError(String title, String message) { }
            @Override public void copyCurrentResult() { performed.add("copy"); }
            @Override public void addCurrentResultToShelf() { performed.add("shelf"); }
            @Override public void expandCurrentResult() { performed.add("expand"); }
            @Override public void saveCurrentResultAsSessionStep() { performed.add("save"); }
        };
        AtomicReference<String> chained = new AtomicReference<>();

        fx(() -> {
            ResultPanel panel = new ResultPanel();
            panel.connectTo(() -> shell);
            panel.setChainHandler(chained::set);
            panel.setResult(publicResult("chain me".getBytes(StandardCharsets.UTF_8)),
                    SecretVisibilityProfile.FULL_LAB, ResultPanel.Status.SUCCESS, 1);
            for (Button button : buttons(panel)) button.fire();
            return null;
        });

        assertEquals(List.of("copy", "shelf", "expand", "save"), performed);
        assertEquals("chain me", chained.get());
    }

    private static void selectFormat(ResultPanel panel, String format) {
        for (Node node : panel.getChildren()) {
            if (node instanceof javafx.scene.layout.HBox row) {
                for (Node child : row.getChildren()) {
                    if (child instanceof ComboBox<?> combo) {
                        @SuppressWarnings("unchecked")
                        ComboBox<String> formats = (ComboBox<String>) combo;
                        formats.setValue(format);
                        return;
                    }
                }
            }
        }
        throw new AssertionError("The result panel has no format selector");
    }

    private static String outputText(ResultPanel panel) {
        for (Node row : panel.getOutputs().getChildren()) {
            if (row instanceof VBox stack) {
                for (Node child : stack.getChildren()) {
                    if (child instanceof TextArea area) return area.getText();
                }
            }
        }
        return "";
    }

    private static List<Button> buttons(ResultPanel panel) {
        List<Button> found = new ArrayList<>();
        for (Node node : panel.getChildren()) {
            if (node instanceof javafx.scene.layout.HBox row) {
                for (Node child : row.getChildren()) {
                    if (child instanceof Button button && button.isVisible()) found.add(button);
                }
            }
        }
        return found;
    }

    @Test
    void hotReloadUpdatesResultPanelLabelsDynamically() throws Exception {
        com.cryptocarver.service.I18nService service = com.cryptocarver.service.I18nService.getInstance();
        try {
            service.setPreference(com.cryptocarver.model.LanguagePreference.EN);
            ResultPanel panel = fx(() -> {
                ResultPanel p = new ResultPanel();
                p.connectTo(() -> null);
                p.setChainHandler(v -> { });
                p.setResult(publicResult("test".getBytes(StandardCharsets.UTF_8)),
                        SecretVisibilityProfile.FULL_LAB, ResultPanel.Status.SUCCESS, 1);
                return p;
            });

            assertEquals(List.of("Copy", "Shelf", "Expand", "Save", "Use as input"), fx(() -> labels(panel)));

            fx(() -> {
                service.setPreference(com.cryptocarver.model.LanguagePreference.ES);
                return null;
            });

            assertEquals(List.of("Copiar", "Shelf", "Ampliar", "Guardar", "Usar como entrada"), fx(() -> labels(panel)));

            fx(() -> {
                service.setPreference(com.cryptocarver.model.LanguagePreference.EN);
                return null;
            });

            assertEquals(List.of("Copy", "Shelf", "Expand", "Save", "Use as input"), fx(() -> labels(panel)));
        } finally {
            service.setPreference(com.cryptocarver.model.LanguagePreference.EN);
        }
    }

    private static List<String> labels(ResultPanel panel) {
        return buttons(panel).stream().map(Button::getText).toList();
    }

    /** Finds the selector whether or not it is currently on screen. */
    private static ComboBox<?> formatSelector(ResultPanel panel) {
        for (Node node : panel.getChildren()) {
            if (node instanceof javafx.scene.layout.HBox row) {
                for (Node child : row.getChildren()) {
                    if (child instanceof ComboBox<?> combo) return combo;
                }
            }
        }
        throw new AssertionError("The result panel has no format selector");
    }

    @Test
    void theFormatSelectorIsNotOfferedUntilThereIsSomethingToFormat() throws Exception {
        // It was on screen from the moment a module loaded, showing "Texto" with
        // no caption and no result behind it. Every option did the same nothing,
        // which is the one thing a control must never do.
        assertFalse(fx(() -> formatSelector(new ResultPanel()).isVisible()),
                "A panel with no result must not offer an output format");

        assertTrue(fx(() -> {
            ResultPanel panel = new ResultPanel();
            panel.setResult(publicResult("bytes".getBytes(StandardCharsets.UTF_8)),
                    SecretVisibilityProfile.FULL_LAB, ResultPanel.Status.SUCCESS, 1);
            return formatSelector(panel).isVisible();
        }), "A result with bytes can be re-encoded, so the format is offered");
    }

    @Test
    void aSummaryWithNoBytesIsNotOfferedAFormatEither() throws Exception {
        OperationResult summary = OperationResult.forOperation("Validation")
                .detail("Signatures", "1")
                .build();

        assertFalse(fx(() -> {
            ResultPanel panel = new ResultPanel();
            panel.setResult(summary, SecretVisibilityProfile.FULL_LAB, ResultPanel.Status.SUCCESS, 1);
            return formatSelector(panel).isVisible();
        }), "There is nothing to re-encode in a summary");
    }

    @Test
    void anEmptyPanelSaysSoRatherThanShowingABlankHeader() throws Exception {
        com.cryptocarver.service.I18nService service = com.cryptocarver.service.I18nService.getInstance();
        com.cryptocarver.model.LanguagePreference previous = service.getPreference();
        try {
            service.setPreference(com.cryptocarver.model.LanguagePreference.ES);
            assertEquals("Sin resultado", fx(() -> {
            for (Node node : new ResultPanel().getChildren()) {
                if (node instanceof javafx.scene.layout.HBox row && !row.getChildren().isEmpty()
                        && row.getChildren().get(0) instanceof javafx.scene.control.Label status) {
                    return status.getText();
                }
            }
            return "";
        }));
        } finally {
            service.setPreference(previous);
        }
    }
}
