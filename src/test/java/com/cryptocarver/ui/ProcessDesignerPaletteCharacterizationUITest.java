package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Pins category/empty filtering and live placement without IDs or volatile outputs. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerPaletteCharacterizationUITest {
    @TempDir Path tempDir;
    private AppSettings originalSettings;
    private ClipboardShelfManager shelf;
    private List<ClipboardEntry> originalShelf;
    private HistoryManager isolatedHistory;
    private Stage stage;

    @BeforeAll static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @BeforeEach void isolateState() {
        originalSettings = AppSettings.getInstance();
        AppSettings.setInstanceForTesting(new AppSettings(tempDir.resolve("settings.json")));
        AppSettings.getInstance().setLanguagePreference(LanguagePreference.EN);
        I18nService.getInstance().refreshFromSettings();
        shelf = ClipboardShelfManager.getInstance();
        originalShelf = new ArrayList<>(shelf.getEntries());
        isolatedHistory = new HistoryManager(tempDir.resolve("history.json"));
    }

    @AfterEach void restoreState() throws Exception {
        try { onFx(() -> { if (stage != null) stage.close(); return null; }); }
        finally {
            isolatedHistory.clearHistory();
            if (!originalShelf.equals(shelf.getEntries())) {
                shelf.clear();
                for (int i = originalShelf.size() - 1; i >= 0; i--) shelf.addEntry(originalShelf.get(i));
            }
            AppSettings.setInstanceForTesting(originalSettings);
            I18nService.getInstance().refreshFromSettings();
        }
    }

    @Test void filtersAndPlacementHavePortableTranscript() throws Exception {
        List<String> transcript = onFx(() -> {
            FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/process_designer.fxml"));
            TitledPane root = loader.load();
            ProcessDesignerController controller = loader.getController();
            Scene scene = new Scene(root, 1200, 800);
            stage = new Stage(); stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
            VBox palette = (VBox) scene.lookup("#paletteItemsContainer");
            TextField search = (TextField) scene.lookup("#paletteSearchField");
            List<String> lines = new ArrayList<>();
            List<String> all = cards(palette);
            assertEquals(NodeCatalog.descriptors().size(), all.size());
            lines.add("all=" + snapshot(palette));
            search.setText("  hAsH  ");
            List<String> hash = cards(palette);
            assertFalse(hash.isEmpty()); assertTrue(hash.size() < all.size());
            lines.add("text=" + snapshot(palette));
            search.setText("Conversions");
            List<String> categoryLabels = NodeCatalog.descriptorsByCategory("Conversions").stream()
                    .map(d -> I18nService.getInstance().text(d.labelKey())).sorted().toList();
            assertEquals(categoryLabels, cards(palette));
            lines.add("category=" + snapshot(palette));
            search.setText("no-such-palette-entry-74");
            assertTrue(palette.getChildren().isEmpty());
            lines.add("no-match=" + snapshot(palette));
            search.setText("  ");
            assertEquals(all, cards(palette));
            lines.add("blank=" + snapshot(palette));
            var filter = ProcessDesignerController.class.getDeclaredMethod("filterPalette", String.class);
            filter.setAccessible(true); filter.invoke(controller, (Object) null);
            assertEquals(all, cards(palette));
            lines.add("null=" + snapshot(palette));

            search.setText("Hash");
            HBox card = (HBox) palette.getChildren().stream().filter(HBox.class::isInstance).findFirst().orElseThrow();
            // Change model size after the card was built: placement must read current state.
            controller.handleAddConsoleOutput();
            for (int click = 0; click < 2; click++) {
                int count = controller.nodes.size();
                card.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 10, 10,
                        MouseButton.PRIMARY, 2, false, false, false, false,
                        true, false, false, false, false, false, null));
                assertEquals(count + 1, controller.nodes.size());
                ProcessDefinition.Node added = controller.nodes.get(count);
                assertEquals(60 + (count % 5) * 40, added.x);
                assertEquals(80 + (count % 6) * 35, added.y);
                assertTrue(controller.selectedNodeIds.contains(added.id));
                lines.add("add=" + added.type + "/" + added.label + "/" + added.x + "/" + added.y);
            }
            assertEquals(originalShelf, shelf.getEntries());
            assertTrue(isolatedHistory.getHistoryItems().isEmpty());
            return lines;
        });
        String joined = String.join("\n", transcript);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("e5919c4b74289c4943dec6ca56218164ddff40ffee4baeac5c957bfdaf4a6a76", digest, joined);
    }

    private static List<String> cards(VBox palette) {
        return palette.getChildren().stream().filter(HBox.class::isInstance).map(HBox.class::cast)
                .map(card -> (VBox) card.getChildren().get(1))
                .map(text -> ((Label) text.getChildren().get(0)).getText()).sorted().toList();
    }

    private static String snapshot(VBox palette) {
        // Preserve the actual UI order, while the independent membership assertion sorts labels.
        return palette.getChildren().stream().map(node -> {
            if (node instanceof Label header) return "category:" + header.getText();
            HBox card = (HBox) node;
            VBox text = (VBox) card.getChildren().get(1);
            return "card:" + ((Label) text.getChildren().get(0)).getText()
                    + ":" + ((Label) text.getChildren().get(1)).getText();
        }).toList().toString();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        Object[] result = new Object[1]; Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { result[0] = action.call(); }
            catch (Throwable error) { failure[0] = error; }
            finally { finished.countDown(); }
        });
        assertTrue(finished.await(15, TimeUnit.SECONDS), "timed out waiting for JavaFX");
        if (failure[0] instanceof Exception error) throw error;
        if (failure[0] instanceof Error error) throw error;
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }
}
