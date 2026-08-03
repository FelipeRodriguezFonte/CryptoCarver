package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
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

/** Opt-in real-FXML coverage for the UX-24 CMS and JOSE legacy migrations. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux25CmsJoseValidationLiveUITest {
    private static Stage stage;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    }

    @AfterAll
    static void stopStage() throws Exception {
        fx(() -> { if (stage != null) stage.hide(); });
    }

    @Test
    void cmsEmptyInputAndDetachedContentUseRealFieldsAndBanner() throws Exception {
        I18nService.getInstance().setPreference(com.cryptocarver.model.LanguagePreference.EN);
        final CmsFixture[] fixture = new CmsFixture[1];
        fx(() -> fixture[0] = CmsFixture.load());
        fx(() -> {
            fixture[0].controller.init(fixture[0].reporter);
            fixture[0].root.setExpanded(true);
            fixture[0].input.clear();
            invoke(fixture[0].controller, "doInspection", "Inspect CMS");
            assertFeedback(fixture[0].reporter, "cmsInputArea",
                    I18nService.getInstance().text("module.cms.inputRequired"), fixture[0].input, fixture[0].scene);

            fixture[0].presenter.hideBanner();
            String secret = "key=00112233445566778899AABBCCDDEEFF";
            fixture[0].input.setText(secret);
            fixture[0].detached.setSelected(true);
            fixture[0].content.setManaged(true);
            fixture[0].content.setVisible(true);
            fixture[0].content.clear();
            invoke(fixture[0].controller, "doInspection", "Validate SignedData");
            assertFeedback(fixture[0].reporter, "cmsContentArea",
                    I18nService.getInstance().text("module.cms.detachedContentRequired"),
                    fixture[0].content, fixture[0].scene);
            assertFalse(fixture[0].reporter.bannerText().contains(secret));
            assertNull(fixture[0].reporter.genericMessage, "validation must not use generic legacy feedback");
        });
    }

    @Test
    void joseJwtDetachedAlgorithmAndKeyValidationUseRealFieldsAndBanner() throws Exception {
        I18nService.getInstance().setPreference(com.cryptocarver.model.LanguagePreference.EN);
        final JoseFixture[] fixture = new JoseFixture[1];
        fx(() -> fixture[0] = JoseFixture.load());
        fx(() -> {
            fixture[0].controller.showSection("JWT");

            expandAncestors(fixture[0].jwtToken);
            fixture[0].jwtToken.clear();
            fixture[0].jwtKey.clear();
            invoke(fixture[0].controller, "handleValidateJWT");
            assertFeedback(fixture[0].reporter, "jwtValidateTokenArea",
                    I18nService.getInstance().text("module.jose.feedback.tokenRequired"),
                    fixture[0].jwtToken, fixture[0].scene);

            fixture[0].presenter.hideBanner();
            expandAncestors(fixture[0].detachedAlgorithm);
            fixture[0].detachedPayload.setText("payload");
            fixture[0].detachedSigningKey.setText("secret=00112233445566778899AABBCCDDEEFF");
            fixture[0].detachedAlgorithm.setValue(null);
            invoke(fixture[0].controller, "handleGenerateDetachedJWS");
            assertFeedback(fixture[0].reporter, "detachedAlgoCombo",
                    I18nService.getInstance().text("module.jose.feedback.algorithmRequired"),
                    fixture[0].detachedAlgorithm, fixture[0].scene);
            assertFalse(fixture[0].reporter.bannerText().contains("00112233445566778899AABBCCDDEEFF"));

            fixture[0].presenter.hideBanner();
            fixture[0].detachedToken.setText("header.payload.signature");
            fixture[0].detachedPayload.setText("payload");
            fixture[0].detachedAlgorithm.getItems().add("HS256");
            fixture[0].detachedAlgorithm.setValue("HS256");
            fixture[0].detachedVerificationKey.clear();
            expandAncestors(fixture[0].detachedVerificationKey);
            invoke(fixture[0].controller, "handleVerifyDetachedJWS");
            assertFeedback(fixture[0].reporter, "detachedVerificationKeyArea",
                    I18nService.getInstance().text("module.jose.feedback.keyRequired"),
                    fixture[0].detachedVerificationKey, fixture[0].scene);
            assertNull(fixture[0].reporter.genericMessage, "validation must not use generic legacy feedback");
        });
    }

    private static void assertFeedback(RecordingReporter reporter, String fieldKey, String detail,
                                       Node target, Scene scene) {
        assertNotNull(reporter.error, "expected a UserFacingError");
        assertEquals(fieldKey, reporter.error.fieldKey());
        assertEquals(detail, reporter.error.detail());
        assertFalse(reporter.error.detail().equals("Error"));
        assertTrue(reporter.presenter.isVisible(), "shared banner must be visible");
        assertTrue(target.getStyleClass().contains("field-error"), fieldKey + " should be highlighted");
        assertSame(target, scene.getFocusOwner(), fieldKey + " should receive focus");
        assertTrue(reporter.bannerText().contains(detail));
    }

    private static void expandAncestors(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof TitledPane pane) pane.setExpanded(true);
            current.setVisible(true);
            current.setManaged(true);
            current = current.getParent();
        }
    }

    private static void invoke(Object target, String methodName, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = args[i].getClass();
            Method method = findMethod(target.getClass(), methodName, types);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not invoke " + methodName, error);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] types) throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != types.length) continue;
            boolean compatible = true;
            for (int i = 0; i < types.length; i++) {
                if (!method.getParameterTypes()[i].isAssignableFrom(types[i])) compatible = false;
            }
            if (compatible) return method;
        }
        throw new NoSuchMethodException(name);
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Missing field " + name, error);
        }
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable error) { failure[0] = error; }
            finally { done.countDown(); }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure[0] != null) throw new AssertionError("UX-25 UI check failed", failure[0]);
    }

    private static HBox banner() {
        HBox banner = new HBox();
        banner.getChildren().addAll(new Label(), new Label(), new Button(), new Button(), new Button());
        return banner;
    }

    private static final class RecordingReporter implements StatusReporter {
        private final InlineErrorPresenter presenter;
        private final Parent sceneRoot;
        private final HBox banner;
        private UserFacingError error;
        private String genericMessage;

        private RecordingReporter(InlineErrorPresenter presenter, Parent sceneRoot, HBox banner) {
            this.presenter = presenter;
            this.sceneRoot = sceneRoot;
            this.banner = banner;
        }

        @Override public void updateStatus(String message) { }
        @Override public void updateInspector(String operation, byte[] input, byte[] output,
                                              List<OperationDetail> details) { }
        @Override public void showError(String title, String message) { genericMessage = message; }
        @Override public void showError(UserFacingError error) {
            this.error = error;
            presenter.showError(error, sceneRoot);
            presenter.goToField(sceneRoot);
        }

        private String bannerText() {
            StringBuilder text = new StringBuilder();
            for (Node node : banner.getChildren()) {
                if (node instanceof Label label) text.append(label.getText()).append('\n');
            }
            return text.toString();
        }
    }

    private static final class CmsFixture {
        private final CmsInspectorController controller;
        private final TitledPane root;
        private final TextArea input;
        private final CheckBoxProxy detached;
        private final TextArea content;
        private final Scene scene;
        private final InlineErrorPresenter presenter;
        private final RecordingReporter reporter;

        private CmsFixture(CmsInspectorController controller, TitledPane root, TextArea input,
                           CheckBoxProxy detached, TextArea content, Scene scene,
                           InlineErrorPresenter presenter, RecordingReporter reporter) {
            this.controller = controller; this.root = root; this.input = input; this.detached = detached;
            this.content = content; this.scene = scene; this.presenter = presenter; this.reporter = reporter;
        }

        private static CmsFixture load() {
            try {
                FXMLLoader loader = new FXMLLoader(Ux25CmsJoseValidationLiveUITest.class
                        .getResource("/fxml/cms_inspector.fxml"));
                TitledPane root = loader.load();
                CmsInspectorController controller = loader.getController();
                TextArea input = field(controller, "cmsInputArea", TextArea.class);
                TextArea content = field(controller, "cmsContentArea", TextArea.class);
                CheckBoxProxy detached = new CheckBoxProxy(field(controller, "cmsDetachedCheck", javafx.scene.control.CheckBox.class));
                VBox sceneRoot = new VBox(banner(), root);
                HBox errorBanner = (HBox) sceneRoot.getChildren().get(0);
                InlineErrorPresenter presenter = presenter(errorBanner, sceneRoot);
                RecordingReporter reporter = new RecordingReporter(presenter, sceneRoot, errorBanner);
                stage = new Stage(); stage.setScene(new Scene(sceneRoot, 1000, 700)); stage.show();
                return new CmsFixture(controller, root, input, detached, content, stage.getScene(), presenter, reporter);
            } catch (Exception error) {
                throw new AssertionError("Could not load cms_inspector.fxml", error);
            }
        }
    }

    private static final class JoseFixture {
        private final JOSEController controller;
        private final TextArea jwtToken;
        private final TextArea jwtKey;
        private final ComboBox<String> detachedAlgorithm;
        private final TextArea detachedPayload;
        private final TextArea detachedSigningKey;
        private final TextArea detachedVerificationKey;
        private final TextArea detachedToken;
        private final Scene scene;
        private final InlineErrorPresenter presenter;
        private final RecordingReporter reporter;

        private JoseFixture(JOSEController controller, TextArea jwtToken, TextArea jwtKey,
                            ComboBox<String> detachedAlgorithm, TextArea detachedPayload,
                            TextArea detachedSigningKey, TextArea detachedVerificationKey,
                            TextArea detachedToken, Scene scene, InlineErrorPresenter presenter,
                            RecordingReporter reporter) {
            this.controller = controller; this.jwtToken = jwtToken; this.jwtKey = jwtKey;
            this.detachedAlgorithm = detachedAlgorithm; this.detachedPayload = detachedPayload;
            this.detachedSigningKey = detachedSigningKey; this.detachedVerificationKey = detachedVerificationKey;
            this.detachedToken = detachedToken; this.scene = scene; this.presenter = presenter; this.reporter = reporter;
        }

        private static JoseFixture load() {
            try {
                FXMLLoader loader = new FXMLLoader(Ux25CmsJoseValidationLiveUITest.class
                        .getResource("/fxml/jose.fxml"));
                Parent root = loader.load();
                JOSEController controller = loader.getController();
                TextArea jwtToken = field(controller, "jwtValidateTokenArea", TextArea.class);
                TextArea jwtKey = field(controller, "jwtValidateKeyArea", TextArea.class);
                ComboBox<String> detachedAlgorithm = field(controller, "detachedAlgoCombo", ComboBox.class);
                TextArea detachedPayload = field(controller, "detachedPayloadArea", TextArea.class);
                TextArea detachedSigningKey = field(controller, "detachedSigningKeyArea", TextArea.class);
                TextArea detachedVerificationKey = field(controller, "detachedVerificationKeyArea", TextArea.class);
                TextArea detachedToken = field(controller, "detachedTokenArea", TextArea.class);
                VBox sceneRoot = new VBox(banner(), root);
                HBox errorBanner = (HBox) sceneRoot.getChildren().get(0);
                InlineErrorPresenter presenter = presenter(errorBanner, sceneRoot);
                RecordingReporter reporter = new RecordingReporter(presenter, sceneRoot, errorBanner);
                controller.setReporter(reporter);
                stage = new Stage(); stage.setScene(new Scene(sceneRoot, 1100, 800)); stage.show();
                return new JoseFixture(controller, jwtToken, jwtKey, detachedAlgorithm, detachedPayload,
                        detachedSigningKey, detachedVerificationKey, detachedToken,
                        stage.getScene(), presenter, reporter);
            } catch (Exception error) {
                throw new AssertionError("Could not load jose.fxml", error);
            }
        }
    }

    private static InlineErrorPresenter presenter(HBox banner, Parent root) {
        return new InlineErrorPresenter(banner,
                (Label) banner.getChildren().get(0), (Label) banner.getChildren().get(1),
                (Button) banner.getChildren().get(2), (Button) banner.getChildren().get(3),
                (Button) banner.getChildren().get(4));
    }

    private static final class CheckBoxProxy {
        private final javafx.scene.control.CheckBox delegate;
        private CheckBoxProxy(javafx.scene.control.CheckBox delegate) { this.delegate = delegate; }
        private void setSelected(boolean value) { delegate.setSelected(value); }
    }
}
