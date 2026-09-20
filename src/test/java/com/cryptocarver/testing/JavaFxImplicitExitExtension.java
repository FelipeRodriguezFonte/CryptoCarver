package com.cryptocarver.testing;

import javafx.application.Platform;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Keeps the JavaFX toolkit alive between test classes.
 *
 * <p>Each UI test class starts the toolkit itself, and JavaFX shuts it down when the last
 * window closes. A class that hides its stage therefore left the next one with no Application
 * Thread: its {@code runLater} calls never ran and it timed out, but only when the two happened
 * to run in that order. Turning implicit exit off before a class starts the toolkit makes the
 * setting hold for all of them.</p>
 */
public final class JavaFxImplicitExitExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        Platform.setImplicitExit(false);
    }
}
