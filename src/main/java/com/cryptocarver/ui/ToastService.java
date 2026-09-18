package com.cryptocarver.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.util.Objects;

/** Non-blocking, short-lived feedback surface for reversible shell actions. */
public final class ToastService {
    private ToastService() { }

    public static ToastHandle show(Node owner, String message) { return show(owner, message, null); }

    public static ToastHandle show(Node owner, String message, Runnable undo) {
        Objects.requireNonNull(owner, "owner");
        if (!(owner.getScene() != null && owner.getScene().getRoot() instanceof Pane root)) {
            return ToastHandle.empty();
        }
        Label text = new Label(message == null ? "" : message);
        text.setWrapText(true);
        HBox box = new HBox(10, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("cc-toast");
        if (undo != null) {
            Button action = new Button("Deshacer");
            action.getStyleClass().add("cc-toast-action");
            action.setOnAction(event -> { undo.run(); remove(root, box); });
            box.getChildren().add(action);
        }
        StackPane.setAlignment(box, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(box, new javafx.geometry.Insets(0, 18, 18, 18));
        root.getChildren().add(box);
        FadeTransition in = new FadeTransition(Duration.millis(140), box);
        in.setFromValue(0); in.setToValue(1); in.play();
        PauseTransition pause = new PauseTransition(Duration.seconds(4));
        pause.setOnFinished(event -> remove(root, box));
        pause.play();
        return new ToastHandle(() -> { pause.stop(); remove(root, box); });
    }

    private static void remove(Pane root, Node node) {
        if (!root.getChildren().contains(node)) return;
        FadeTransition out = new FadeTransition(Duration.millis(120), node);
        out.setToValue(0);
        out.setOnFinished(event -> root.getChildren().remove(node));
        out.play();
    }

    public record ToastHandle(Runnable dismissAction) {
        static ToastHandle empty() { return new ToastHandle(() -> { }); }
        public void dismiss() { if (dismissAction != null) dismissAction.run(); }
    }
}
