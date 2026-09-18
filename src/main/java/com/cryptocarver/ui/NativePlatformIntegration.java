package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.stage.Stage;
import java.util.Locale;

/** Small platform boundary for behavior that is meaningful only on desktop hosts. */
public final class NativePlatformIntegration {
    private NativePlatformIntegration() { }

    public static void configure(Stage stage, Scene scene, Node root) {
        if (isMac()) {
            MenuBar menuBar = root.lookup(".main-menu-bar") instanceof MenuBar value ? value : null;
            if (menuBar != null) menuBar.setUseSystemMenuBar(true);
            stage.setUserData("native-menu-bar");
        }
        AccessibilitySupport.enrich(root);
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
