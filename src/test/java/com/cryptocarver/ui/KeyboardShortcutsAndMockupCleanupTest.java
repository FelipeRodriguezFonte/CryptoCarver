package com.cryptocarver.ui;

import com.cryptocarver.model.CommandItem;
import com.cryptocarver.model.CommandRegistry;
import com.cryptocarver.model.KeyboardShortcutEntry;
import com.cryptocarver.model.KeyboardShortcutRegistry;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class KeyboardShortcutsAndMockupCleanupTest {

    @BeforeAll
    public static void initJfx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        latch.await(5, TimeUnit.SECONDS);
    }

    private void runAndWait(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("JavaFX execution timed out");
            }
        } catch (InterruptedException e) {
            fail("Interrupted waiting for JavaFX thread");
        }
    }

    @Test
    void testNoMenuItemHijacksNativeClipboardAccelerators() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputStream is = getClass().getResourceAsStream("/fxml/main-view-modern.fxml");
        assertNotNull(is, "main-view-modern.fxml not found");

        Document doc = db.parse(is);
        NodeList menuItems = doc.getElementsByTagName("MenuItem");

        for (int i = 0; i < menuItems.getLength(); i++) {
            Element item = (Element) menuItems.item(i);
            String accelerator = item.getAttribute("accelerator");
            String text = item.getAttribute("text");

            assertNotEquals("Ctrl+C", accelerator, "MenuItem '" + text + "' must not hijack native Ctrl+C");
            assertNotEquals("Ctrl+V", accelerator, "MenuItem '" + text + "' must not hijack native Ctrl+V");
            assertNotEquals("Ctrl+X", accelerator, "MenuItem '" + text + "' must not hijack native Ctrl+X");
            assertNotEquals("Ctrl+A", accelerator, "MenuItem '" + text + "' must not hijack native Ctrl+A");
            assertNotEquals("Ctrl+Z", accelerator, "MenuItem '" + text + "' must not hijack native Ctrl+Z");

            if ("Copy Output".equals(text)) {
                assertEquals("Shortcut+Shift+C", accelerator, "Copy Output must use Shortcut+Shift+C");
            }
        }
    }

    @Test
    void testFxmlInitialHistoryContainerIsEmpty() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputStream is = getClass().getResourceAsStream("/fxml/main-view-modern.fxml");
        assertNotNull(is, "main-view-modern.fxml not found");

        Document doc = db.parse(is);
        NodeList vboxes = doc.getElementsByTagName("VBox");

        Element historyBoxEl = null;
        for (int i = 0; i < vboxes.getLength(); i++) {
            Element vbox = (Element) vboxes.item(i);
            if ("historyContainer".equals(vbox.getAttribute("fx:id"))) {
                historyBoxEl = vbox;
                break;
            }
        }

        assertNotNull(historyBoxEl, "historyContainer node must exist in main-view-modern.fxml");

        int elementChildCount = 0;
        NodeList childNodes = historyBoxEl.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
            if (childNodes.item(j).getNodeType() == Node.ELEMENT_NODE) {
                elementChildCount++;
            }
        }

        assertEquals(0, elementChildCount, "historyContainer in main-view-modern.fxml must start completely empty of static elements");
    }

    @Test
    void testToolbarContainsCommandPaletteTriggerButton() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputStream is = getClass().getResourceAsStream("/fxml/main-view-modern.fxml");
        assertNotNull(is, "main-view-modern.fxml not found");

        Document doc = db.parse(is);
        NodeList buttons = doc.getElementsByTagName("Button");

        boolean hasCommandPaletteButton = false;
        for (int i = 0; i < buttons.getLength(); i++) {
            Element btn = (Element) buttons.item(i);
            if ("#handleOpenCommandPalette".equals(btn.getAttribute("onAction"))) {
                hasCommandPaletteButton = true;
                break;
            }
        }
        assertTrue(hasCommandPaletteButton, "Main toolbar must contain a button opening Command Palette");
    }

    @Test
    void testSecurityTipBoxRevealsDynamicallyOnTip() {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                assertNotNull(resource, "main-view-modern.fxml not found");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();

                ModernMainController controller = loader.getController();
                assertNotNull(controller);

                Field tipLabelField = ModernMainController.class.getDeclaredField("securityTipLabel");
                tipLabelField.setAccessible(true);
                Label tipLabel = (Label) tipLabelField.get(controller);
                assertNotNull(tipLabel);

                Field tipBoxField = ModernMainController.class.getDeclaredField("securityTipBox");
                tipBoxField.setAccessible(true);
                VBox securityTipBox = (VBox) tipBoxField.get(controller);
                assertNotNull(securityTipBox);

                // Initial state must be hidden
                assertFalse(securityTipBox.isVisible());
                assertFalse(securityTipBox.isManaged());

                // Set a security warning
                tipLabel.setText("No salt provided! HKDF is less secure.");
                assertTrue(securityTipBox.isVisible(), "Security tip box must become visible when a tip text is set");
                assertTrue(securityTipBox.isManaged(), "Security tip box must become managed when a tip text is set");

                // Clear security warning
                tipLabel.setText("");
                assertFalse(securityTipBox.isVisible(), "Security tip box must hide when tip text is cleared");
                assertFalse(securityTipBox.isManaged(), "Security tip box must unmanage when tip text is cleared");
            } catch (Exception e) {
                fail("Failed to test security tip dynamic reveal: " + e.getMessage());
            }
        });
    }

    @Test
    void testKeyboardShortcutRegistryContainsF1AndCoreShortcuts() {
        List<KeyboardShortcutEntry> shortcuts = KeyboardShortcutRegistry.getShortcuts();
        assertFalse(shortcuts.isEmpty());

        boolean hasF1Help = shortcuts.stream().anyMatch(s -> "Keyboard Shortcuts".equals(s.getActionName()) && "F1".equals(s.getKeyCombination()));
        boolean hasSaveSession = shortcuts.stream().anyMatch(s -> "Save Session".equals(s.getActionName()));
        boolean hasCopyOutput = shortcuts.stream().anyMatch(s -> "Copy Output".equals(s.getActionName()) && "Shortcut+Shift+C".equals(s.getKeyCombination()));
        boolean hasCommandPalette = shortcuts.stream().anyMatch(s -> "Command Palette".equals(s.getActionName()) && "Shortcut+K".equals(s.getKeyCombination()));

        assertTrue(hasF1Help, "Registry must contain F1 for Keyboard Shortcuts");
        assertTrue(hasSaveSession, "Registry must contain Save Session");
        assertTrue(hasCopyOutput, "Registry must contain Copy Output with Shortcut+Shift+C");
        assertTrue(hasCommandPalette, "Registry must contain Command Palette with Shortcut+K");
    }

    @Test
    void testCommandRegistryUsesShortcutShiftCForCopyOutput() {
        ModernMainController controller = new ModernMainController();
        List<CommandItem> commands = CommandRegistry.buildCommands(controller);
        CommandItem copyCmd = commands.stream()
                .filter(c -> "Copy Output".equals(c.getTitle()))
                .findFirst()
                .orElse(null);

        assertNotNull(copyCmd, "CommandRegistry must contain Copy Output item");
        assertEquals("Shortcut+Shift+C", copyCmd.getShortcut(), "Command Palette Copy Output item must display Shortcut+Shift+C instead of Ctrl+C");
    }

    @Test
    void testOperationInspectorPresenterClearsTipOnInitialNavigation() {
        runAndWait(() -> {
            Label opLabel = new Label();
            Label inputLabel = new Label();
            Label outputLabel = new Label();
            Label tipLabel = new Label();
            VBox detailsContainer = new VBox();

            OperationInspectorPresenter presenter = new OperationInspectorPresenter(opLabel, inputLabel, outputLabel, tipLabel, detailsContainer);

            // Initial navigation (details == null)
            presenter.present("HKDF-SHA256", null, null, null);
            assertEquals("", tipLabel.getText(), "On initial navigation, security tip label must be empty so warning box stays hidden");

            // Operation execution with results (details != null)
            presenter.present("HKDF-SHA256", new byte[10], new byte[32], java.util.List.of());
            assertFalse(tipLabel.getText().isBlank(), "On real operation execution, security tip label must be populated");
        });
    }
}
