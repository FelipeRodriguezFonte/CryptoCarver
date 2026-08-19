package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real-FXML regression for the ASN.1 legacy validation route. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux24LegacyValidationLiveUITest {
    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @AfterAll
    static void stopFx() throws Exception {
        fx(() -> { });
    }

    @Test
    void realAsn1FxmlRouteUsesSharedErrorContractAndFieldKey() throws Exception {
        final RecordingReporter[] reporter = new RecordingReporter[1];
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/asn1.fxml"));
            try {
                Parent root = loader.load();
                ASN1Controller controller = loader.getController();
                reporter[0] = new RecordingReporter();
                controller.init(reporter[0]);
                TextArea input = field(controller, "asn1InputArea", TextArea.class);
                input.clear();
                Method parse = ASN1Controller.class.getDeclaredMethod("handleParseASN1");
                parse.setAccessible(true);
                parse.invoke(controller);
                assertNotNull(root);
            } catch (Exception error) {
                throw new AssertionError("Could not exercise real ASN.1 FXML route", error);
            }
        });
        assertNotNull(reporter[0].error);
        assertEquals("asn1InputArea", reporter[0].error.fieldKey());
        assertFalse(reporter[0].error.detail().equals("Error"));
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable error) { failure[0] = error; }
            finally { done.countDown(); }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private static final class RecordingReporter implements StatusReporter {
        private UserFacingError error;
        @Override public void updateStatus(String message) { }
        @Override public void updateInspector(String operation, byte[] input, byte[] output,
                                              List<com.cryptocarver.model.OperationDetail> details) { }
        @Override public void showError(String title, String message) { }
        @Override public void showError(UserFacingError error) { this.error = error; }
    }
}
