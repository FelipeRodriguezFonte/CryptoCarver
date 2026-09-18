package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.fxml.FXMLLoader;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Single entry point for building an {@link FXMLLoader}.
 *
 * <p>Views declare their user-visible strings as {@code %key} references, which the loader
 * only resolves when a resource bundle is attached; without one it fails the whole load with
 * {@code LoadException: No resources specified}. Attaching the bundle at every call site is a
 * step that is easy to forget — and did go missing across the UI test suite — so the bundle is
 * wired here instead of being each caller's responsibility.
 */
public final class Fxml {

    private Fxml() {
    }

    /** Loader for {@code location} with the active localization bundle already attached. */
    public static FXMLLoader loader(URL location) {
        FXMLLoader loader = new FXMLLoader(location);
        loader.setResources(bundle());
        return loader;
    }

    /** The bundle every view is loaded with. */
    public static ResourceBundle bundle() {
        return I18nService.getInstance().getBundle();
    }

    /** Loader for a classpath FXML path such as {@code "/fxml/main-view-modern.fxml"}. */
    public static FXMLLoader loader(String classpathLocation) {
        URL location = Fxml.class.getResource(classpathLocation);
        if (location == null) {
            throw new IllegalArgumentException("FXML not found on the classpath: " + classpathLocation);
        }
        return loader(location);
    }
}
