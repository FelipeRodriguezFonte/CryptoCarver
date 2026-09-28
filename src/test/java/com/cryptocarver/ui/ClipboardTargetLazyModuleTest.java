package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shelf targets must reach modules that were loaded after an earlier fill; production loading is lazy. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ClipboardTargetLazyModuleTest {

    @BeforeAll
    static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { Platform.setImplicitExit(false); }
    }

    @Test
    void joseTargetWorksWhenJoseLoadsAfterAnEarlierFill() throws Exception {
        runFx(() -> {
            try {
                FXMLLoader loader = Fxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ModernMainController controller = loader.getController();
                assertNull(field(controller, "joseController"), "JOSE starts unloaded");

                controller.fillClipboardTarget("UNKNOWN_TARGET", "x", null, null);

                Method showJose = ModernMainController.class.getDeclaredMethod("showJOSE");
                showJose.setAccessible(true);
                showJose.invoke(controller);
                JOSEController jose = field(controller, "joseController");
                controller.fillClipboardTarget("JOSE_JWT", "{\"sub\":\"shelf\"}", null, null);

                TextArea payload = field(jose, "jwtPayloadArea");
                assertEquals("{\"sub\":\"shelf\"}", payload.getText());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void runFx(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> { try { task.run(); } catch (Throwable t) { error.set(t); } finally { latch.countDown(); } });
        assertTrue(latch.await(20, TimeUnit.SECONDS), "JavaFX action timed out");
        if (error.get() != null) throw new AssertionError(error.get());
    }
}
