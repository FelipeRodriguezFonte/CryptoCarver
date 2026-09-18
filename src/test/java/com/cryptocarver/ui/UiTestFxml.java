package com.cryptocarver.ui;

import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.net.URL;

/**
 * Test-side FXML loading.
 *
 * <p>Delegates to the production {@link Fxml} factory so tests load views exactly as the app
 * does — same localization bundle, same failure on a missing one.
 *
 * <p>It adds one thing the app gets from the user instead: the shell loads its workspace
 * modules on demand, so a freshly loaded {@code main-view-modern.fxml} has null module
 * controllers until somebody navigates. Tests assert on those controllers directly, so the
 * shell is asked to materialize them as part of loading.
 */
final class UiTestFxml {

    private UiTestFxml() {
    }

    static FXMLLoader loader(URL location) {
        return new MaterializingLoader(location);
    }

    static FXMLLoader loader(String classpathLocation) {
        return new MaterializingLoader(UiTestFxml.class.getResource(classpathLocation));
    }

    private static final class MaterializingLoader extends FXMLLoader {

        private MaterializingLoader(URL location) {
            super(location);
            setResources(Fxml.bundle());
        }

        @Override
        public <T> T load() throws IOException {
            T loaded = super.load();
            if (getController() instanceof ModernMainController shell) {
                shell.materializeModulesForTesting();
            }
            return loaded;
        }
    }
}
