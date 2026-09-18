package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Clears sensitive controls and optionally expires copied clipboard content. */
public final class SecretCleanupService {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cryptocarver-sensitive-data"); t.setDaemon(true); return t;
    });
    private SecretCleanupService() { }

    public static int clearSensitiveFields(Node root) {
        if (root == null) return 0;
        int count = 0;
        if (root instanceof TextInputControl control && isSensitive(control)) { control.clear(); count++; }
        if (root instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) count += clearSensitiveFields(child);
        return count;
    }

    public static boolean isSensitive(TextInputControl control) {
        if (control == null) return false;
        String id = ((control.getId() == null ? "" : control.getId()) + " " + String.join(" ", control.getStyleClass())).toLowerCase(Locale.ROOT);
        return id.matches(".*(secret|password|passphrase|private|pin|token|key|credential|sensitive).*" );
    }

    /** Copies a value and returns a cancellable expiry task; delay <= 0 disables expiry. */
    public static ScheduledFuture<?> copyWithExpiry(String value, int seconds) {
        ClipboardContent content = new ClipboardContent(); content.putString(value == null ? "" : value);
        Clipboard.getSystemClipboard().setContent(content);
        if (seconds <= 0) return null;
        return TIMER.schedule(() -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString() && (value == null ? "" : value).equals(clipboard.getString())) clipboard.clear();
        }, seconds, TimeUnit.SECONDS);
    }
}
