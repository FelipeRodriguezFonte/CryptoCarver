package com.cryptocarver.ui;

import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.net.URL;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.Group;

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

    // Accessed only on the FX thread. Weak tracking must not become a new GC root.
    private static final List<Fixture> fixtures = new ArrayList<>();
    private record Fixture(WeakReference<Parent> root, WeakReference<Object> controller) { }

    static int mark() { return fixtures.size(); }

    static void releaseFrom(int mark) {
        List<Fixture> owned = new ArrayList<>(fixtures.subList(mark, fixtures.size()));
        fixtures.subList(mark, fixtures.size()).clear();
        for (Fixture fixture : owned) {
            Object controller = fixture.controller().get();
            if (controller instanceof ModernMainController shell) shell.shutdown();
            if (controller instanceof ClipboardShelfController shelf) shelf.dispose();
            Parent root = fixture.root().get();
            if (root != null && root.getScene() != null && root.getScene().getRoot() == root) {
                root.getScene().setRoot(new Group());
            }
        }
    }

    /** Production loading without eager materialization, with the same fixture teardown. */
    static FXMLLoader productionLoader(String location) {
        return new MaterializingLoader(UiTestFxml.class.getResource(location), false);
    }

    static FXMLLoader productionLoader(URL location) {
        return new MaterializingLoader(location, false);
    }

    private UiTestFxml() {
    }

    static FXMLLoader loader(URL location) {
        return new MaterializingLoader(location);
    }

    static FXMLLoader loader(String classpathLocation) {
        return new MaterializingLoader(UiTestFxml.class.getResource(classpathLocation));
    }

    private static final class MaterializingLoader extends FXMLLoader {

        private final boolean materialize;

        private MaterializingLoader(URL location) { this(location, true); }

        private MaterializingLoader(URL location, boolean materialize) {
            super(location);
            this.materialize = materialize;
            setResources(Fxml.bundle());
        }

        @Override
        public <T> T load() throws IOException {
            T loaded = super.load();
            if (loaded instanceof Parent root) {
                fixtures.add(new Fixture(new WeakReference<>(root), new WeakReference<>(getController())));
            }
            if (materialize && getController() instanceof ModernMainController shell) {
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
