package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Detached window host for Process Designer.
 * Preserves the exact same view and state without duplication.
 */
public final class ProcessDesignerWindow {

    private static Stage windowStage;
    private static Node detachedContent;
    private static Runnable onRestoreCallback;

    private ProcessDesignerWindow() {}

    public static boolean isShowing() {
        return windowStage != null && windowStage.isShowing();
    }

    public static Stage open(Node content, String title, Runnable onRestore) {
        return open(null, content, title, onRestore);
    }

    public static Stage open(Stage owner, Node content, String title, Runnable onRestore) {
        if (windowStage != null && windowStage.isShowing()) {
            windowStage.toFront();
            return windowStage;
        }

        detachedContent = content;
        onRestoreCallback = onRestore;

        windowStage = new Stage();
        Stage effectiveOwner = owner;
        if (effectiveOwner == null && content != null && content.getScene() != null && content.getScene().getWindow() instanceof Stage s) {
            effectiveOwner = s;
        }
        if (effectiveOwner != null) {
            windowStage.initOwner(effectiveOwner);
        }
        windowStage.setTitle(title != null ? title : I18nService.getInstance().text("module.process.title"));
        Scene scene = new Scene(new StackPane(content), 1200, 800);
        if (content != null && content.getScene() != null) {
            scene.getStylesheets().addAll(content.getScene().getStylesheets());
        }
        windowStage.setScene(scene);
        windowStage.setOnHidden(e -> {
            if (onRestoreCallback != null) {
                onRestoreCallback.run();
            }
            windowStage = null;
            detachedContent = null;
            onRestoreCallback = null;
        });

        windowStage.show();
        return windowStage;
    }

    public static Stage open() {
        if (windowStage != null && windowStage.isShowing()) {
            windowStage.toFront();
            return windowStage;
        }
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(ProcessDesignerWindow.class.getResource("/fxml/process_designer.fxml"));
            javafx.scene.Parent root = loader.load();
            return open(null, root, I18nService.getInstance().text("module.process.title"), null);
        } catch (Exception e) {
            return null;
        }
    }

    public static void close() {
        if (windowStage != null) {
            windowStage.close();
        }
    }

    public static Stage getStage() {
        return windowStage;
    }
}
