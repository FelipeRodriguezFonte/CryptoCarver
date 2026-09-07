package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Several JavaFX containers keep their content out of {@code getChildrenUnmodifiable()} until a
 * skin exists, which has not happened when a controller binds in {@code initialize()}. The walk
 * has to reach through each of them by its own accessor, or every literal declared inside keeps
 * the English text from the FXML.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModuleI18nSkinDeferredContainersUITest {

    private static final String LABEL = "Save block settings";
    private static final String KEY = "module.process.saveBlock";

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX toolkit did not start");
    }

    private <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Object[] box = new Object[2];
        Platform.runLater(() -> {
            try { box[0] = action.call(); } catch (Throwable t) { box[1] = t; } finally { latch.countDown(); }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) fail("timed out on the FX thread");
        if (box[1] != null) throw new RuntimeException((Throwable) box[1]);
        @SuppressWarnings("unchecked") T result = (T) box[0];
        return result;
    }

    private static String expected() {
        return I18nService.getInstance().text(KEY);
    }

    @Test
    void aLabelInsideASplitPaneIsTranslated() throws Exception {
        String text = onFx(() -> {
            Button button = new Button(LABEL);
            SplitPane split = new SplitPane(new VBox(button), new VBox());
            ModuleI18n.bind(split, Map.of(LABEL, KEY));
            return button.getText();
        });
        assertEquals(expected(), text, "a SplitPane item must be reached through getItems()");
    }

    @Test
    void aLabelInsideAScrollPaneIsTranslated() throws Exception {
        String text = onFx(() -> {
            Label label = new Label(LABEL);
            ScrollPane scroll = new ScrollPane(new VBox(label));
            ModuleI18n.bind(scroll, Map.of(LABEL, KEY));
            return label.getText();
        });
        assertEquals(expected(), text, "ScrollPane content must be reached through getContent()");
    }

    @Test
    void aLabelInsideAToolBarIsTranslated() throws Exception {
        String text = onFx(() -> {
            Button button = new Button(LABEL);
            ToolBar bar = new ToolBar(button);
            ModuleI18n.bind(bar, Map.of(LABEL, KEY));
            return button.getText();
        });
        assertEquals(expected(), text, "ToolBar items must be reached through getItems()");
    }

    @Test
    void aMenuInsideAMenuBarIsTranslated() throws Exception {
        String text = onFx(() -> {
            MenuItem item = new MenuItem(LABEL);
            MenuBar bar = new MenuBar(new Menu("File", null, item));
            ModuleI18n.bind(bar, Map.of(LABEL, KEY));
            return item.getText();
        });
        assertEquals(expected(), text, "MenuBar menus must be reached through getMenus()");
    }

    @Test
    void nestingTheContainersStillReachesTheLeaf() throws Exception {
        String text = onFx(() -> {
            Button button = new Button(LABEL);
            TitledPane pane = new TitledPane("Pane", new ScrollPane(new VBox(button)));
            SplitPane split = new SplitPane(pane);
            ModuleI18n.bind(split, Map.of(LABEL, KEY));
            return button.getText();
        });
        assertEquals(expected(), text, "SplitPane -> TitledPane -> ScrollPane -> Button must resolve");
    }

    @Test
    void aNodeReachableTwiceIsRegisteredOnlyOnce() throws Exception {
        // The button is reachable through getContent() and, after layout, through the
        // children list. Indexing it twice would queue two writers for one control.
        int entries = onFx(() -> {
            Button button = new Button(LABEL);
            ScrollPane scroll = new ScrollPane(new VBox(button));
            new javafx.scene.Scene(scroll);
            scroll.applyCss();
            scroll.layout();
            ModuleI18n.Binding binding = ModuleI18n.bind(scroll, Map.of(LABEL, KEY));
            binding.refresh();
            return countEntries(binding);
        });
        assertEquals(1, entries, "one control must produce exactly one translation entry");
    }

    private static int countEntries(ModuleI18n.Binding binding) throws Exception {
        java.lang.reflect.Field field = ModuleI18n.Binding.class.getDeclaredField("entries");
        field.setAccessible(true);
        return ((java.util.List<?>) field.get(binding)).size();
    }
}
