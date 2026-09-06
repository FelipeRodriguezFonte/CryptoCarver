package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ui")
class NodeInspectorRendererTest {

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    private static void runOnFxThread(Runnable work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX test timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void allDescriptorsRenderCorrectControlKindsAndSensitiveAsPasswordField() throws Exception {
        runOnFxThread(() -> {
            List<NodeDescriptor> descriptors = NodeCatalog.descriptors();
            assertFalse(descriptors.isEmpty());

            for (NodeDescriptor descriptor : descriptors) {
                ProcessDefinition.Node node = new ProcessDefinition.Node("n1", descriptor.type(), descriptor.type(), 0, 0);
                VBox container = new VBox();
                Map<String, char[]> secrets = new HashMap<>();

                NodeInspectorRenderer renderer = NodeInspectorRenderer.render(descriptor, node, container, secrets);
                assertEquals(descriptor.parameters().size(), renderer.getControls().size(),
                        "Control count mismatch for " + descriptor.type());

                for (NodeParameter param : descriptor.parameters()) {
                    Control control = renderer.getControl(param.key());
                    assertNotNull(control, "Missing control for param " + param.key() + " in " + descriptor.type());
                    VBox group = renderer.getGroup(param.key());
                    assertNotNull(group, "Missing group for param " + param.key() + " in " + descriptor.type());

                    if (param.sensitive()) {
                        assertTrue(control instanceof PasswordField,
                                "Sensitive param " + param.key() + " must be PasswordField in " + descriptor.type());
                    }

                    switch (param.kind()) {
                        case TEXT, NUMBER, HEX -> assertTrue(control instanceof TextField);
                        case MULTILINE -> assertTrue(control instanceof TextArea);
                        case COMBO -> {
                            assertTrue(control instanceof ComboBox);
                            ComboBox<?> cb = (ComboBox<?>) control;
                            assertEquals(param.options().size(), cb.getItems().size());
                        }
                        case CHECKBOX -> assertTrue(control instanceof CheckBox);
                        case PASSWORD -> assertTrue(control instanceof PasswordField);
                        case FILE_OPEN, FILE_SAVE -> assertTrue(control instanceof TextField);
                    }
                }
            }
        });
    }

    @Test
    void dynamicVisibilityAndSavingWorks() throws Exception {
        runOnFxThread(() -> {
            NodeDescriptor descriptor = NodeCatalog.descriptor("WSS_SIGN_BODY").orElseThrow();
            ProcessDefinition.Node node = new ProcessDefinition.Node("w1", "WSS_SIGN_BODY", "WSS Sign", 0, 0);
            node.configuration.put("timestampEnabled", "false");

            VBox container = new VBox();
            Map<String, char[]> secrets = new HashMap<>();

            NodeInspectorRenderer renderer = NodeInspectorRenderer.render(descriptor, node, container, secrets);

            VBox minutesGroup = renderer.getGroup("timestampMinutes");
            assertNotNull(minutesGroup);
            assertFalse(minutesGroup.isVisible(), "timestampMinutes should be hidden when timestampEnabled=false");
            assertFalse(minutesGroup.isManaged(), "timestampMinutes should not be managed when hidden");

            // Toggle timestampEnabled
            CheckBox enabledCheck = (CheckBox) renderer.getControl("timestampEnabled");
            enabledCheck.setSelected(true);

            assertTrue(minutesGroup.isVisible(), "timestampMinutes should be visible when timestampEnabled=true");
            assertTrue(minutesGroup.isManaged(), "timestampMinutes should be managed when visible");

            // Fill and save
            TextField minutesField = (TextField) renderer.getControl("timestampMinutes");
            minutesField.setText("15");
            PasswordField keyPassField = (PasswordField) renderer.getControl("keyPassword");
            keyPassField.setText("secretKeyPass123");

            renderer.save(node, secrets);

            assertEquals("15", node.configuration.get("timestampMinutes"));
            assertEquals("true", node.configuration.get("timestampEnabled"));
            assertNull(node.configuration.get("keyPassword"), "Secret keyPassword must not be in configuration");
            assertNotNull(secrets.get("keyPassword"), "Secret keyPassword must be in transient secrets map");
            assertEquals("secretKeyPass123", new String(secrets.get("keyPassword")));
        });
    }
}
