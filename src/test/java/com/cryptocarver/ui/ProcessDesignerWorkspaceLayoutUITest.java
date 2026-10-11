package com.cryptocarver.ui;

import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerWorkspaceLayoutUITest {
    @Test void designerTakesTheWidthOfTheHiddenShellPanelsAndOtherRoutesKeepTheirColumns() throws Exception {
        ClippedTextAuditTool.onFx(() -> {
            ClippedTextAuditTool.withScreen("Process Designer", "light", root -> {
                SplitPane split = (SplitPane) root.lookup("#workspaceSplitPane");
                double rail = ((Region) split.getItems().get(0)).getWidth();
                double content = ((Region) split.getItems().get(1)).getWidth();
                assertTrue(rail < 120, "only the icon rail stays on the left: " + rail);
                assertTrue(content > split.getWidth() - rail - 20, "the designer fills the workspace: " + content);
                Region canvas = (Region) root.lookup("#designerWorkspace");
                assertTrue(canvas.getWidth() > split.getWidth() * 0.45, "the canvas is the widest column: " + canvas.getWidth());
                // Font metrics differ between platforms, so the height is checked against the viewport, not in pixels.
                javafx.scene.control.ScrollPane page = (javafx.scene.control.ScrollPane) root.lookup("#mainScrollPane");
                Region workArea = (Region) root.lookup("#designerVerticalSplit");
                double viewport = page.getViewportBounds().getHeight();
                assertTrue(workArea.getHeight() > viewport * 0.6, "the designer fills the viewport height: "
                        + workArea.getHeight() + " of " + viewport);
                assertTrue(canvas.getHeight() > workArea.getHeight() * 0.4, "the canvas takes most of the work area: "
                        + canvas.getHeight() + " of " + workArea.getHeight());
            });
            ClippedTextAuditTool.withScreen("Key & Certificate Format Workbench", "light", root -> {
                SplitPane split = (SplitPane) root.lookup("#workspaceSplitPane");
                assertEquals(1400.0, split.getWidth(), 1.0, "the workspace never grows past the window");
                assertTrue(((Region) split.getItems().get(0)).getWidth() > 250, "the section tree keeps its column");
                assertTrue(((Region) split.getItems().get(2)).getWidth() > 250, "the inspector keeps its column");
            });
            return null;
        });
    }
}
