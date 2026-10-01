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
 * <p>It adds module materialization that the app gets from the user instead: the shell loads its workspace
 * modules on demand, so a freshly loaded {@code main-view-modern.fxml} has null module
 * controllers until somebody navigates. Tests assert on those controllers directly, so the
 * shell is asked to materialize them as part of loading. It also installs the base
 * stylesheet when a test attaches the shell to a Scene, matching the app bootstrap.
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
                // Match CryptoCalculatorModern's bootstrap when a test attaches the
                // shell to a Scene. Unstyled scenes otherwise reuse cached component
                // rules without the root tokens that those rules look up.
                if (loaded instanceof javafx.scene.Parent root) {
                    String base = UiTestFxml.class.getResource("/css/styles.css").toExternalForm();
                    root.sceneProperty().addListener((observable, before, scene) -> {
                        if (scene != null && !scene.getStylesheets().contains(base)) {
                            scene.getStylesheets().add(base);
                        }
                    });
                }
            }
            return loaded;
        }
    }
}
