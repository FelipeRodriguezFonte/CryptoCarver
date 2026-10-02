package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.extension.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Releases UI fixtures even after failed assertions, without stopping the shared toolkit.
 * Class fixtures survive between methods; method fixtures are released after each method.
 * Settings use a disposable class store and the previous singleton is restored on exit.
 */
public final class UiTestLifecycleExtension implements BeforeAllCallback, BeforeEachCallback,
        AfterEachCallback, AfterAllCallback {
    private static final ExtensionContext.Namespace KEY = ExtensionContext.Namespace.create(UiTestLifecycleExtension.class);
    private record MethodFixtures(int mark, Set<Window> windows, AppSettings settings) { }
    private record ClassSettings(AppSettings previous, Path directory) { }

    private static boolean applies(ExtensionContext context) {
        return context.getRequiredTestClass().getPackageName().equals("com.cryptocarver.ui");
    }

    @Override public void beforeAll(ExtensionContext context) throws Exception {
        if (!applies(context)) return;
        Path directory = Files.createTempDirectory(Path.of("target"), "ui-fixture-");
        context.getStore(KEY).put("settings", new ClassSettings(AppSettings.getInstance(), directory));
        AppSettings.setInstanceForTesting(new AppSettings(directory.resolve("settings.json")));
    }

    @Override public void beforeEach(ExtensionContext context) throws Exception {
        if (!applies(context)) return;
        AppSettings settings = AppSettings.getInstance();
        context.getStore(KEY).put("fixtures", new MethodFixtures(0, Set.of(), settings));
        onFx(() -> context.getStore(KEY).put("fixtures",
                new MethodFixtures(UiTestFxml.mark(), new HashSet<>(Window.getWindows()), settings)));
    }

    @Override public void afterEach(ExtensionContext context) throws Exception {
        if (!applies(context)) return;
        MethodFixtures before = context.getStore(KEY).remove("fixtures", MethodFixtures.class);
        if (before == null) return;
        try {
            onFx(() -> {
                closeWindowsExcept(before.windows());
                UiTestFxml.releaseFrom(before.mark());
            });
        } finally {
            AppSettings.setInstanceForTesting(before.settings());
            I18nService.getInstance().refreshFromSettings();
        }
    }

    @Override public void afterAll(ExtensionContext context) throws Exception {
        if (!applies(context)) return;
        try {
            onFx(() -> {
                closeWindowsExcept(Set.of());
                UiTestFxml.releaseFrom(0);
            });
        } finally {
            ClassSettings settings = context.getStore(KEY).remove("settings", ClassSettings.class);
            if (settings != null) {
                AppSettings.setInstanceForTesting(settings.previous());
                I18nService.getInstance().refreshFromSettings();
                try (var paths = Files.walk(settings.directory())) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void closeWindowsExcept(Set<Window> keep) {
        // Copy: hiding a window mutates Window.getWindows() and can close owned windows.
        for (Window window : List.copyOf(Window.getWindows())) {
            if (keep.contains(window)) continue;
            if (window instanceof Stage stage) {
                stage.close();
                stage.setScene(null);
            } else window.hide();
        }
    }

    static void onFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) { action.run(); return; }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            Platform.runLater(() -> {
                try { action.run(); } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
            });
        } catch (IllegalStateException toolkitNotStarted) { return; }
        if (!done.await(30, TimeUnit.SECONDS)) throw new AssertionError("JavaFX fixture teardown timed out");
        if (failure.get() != null) throw new AssertionError("JavaFX fixture teardown failed", failure.get());
    }
}
