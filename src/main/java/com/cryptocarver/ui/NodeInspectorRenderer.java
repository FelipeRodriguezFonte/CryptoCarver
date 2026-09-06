package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.service.I18nService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * Descriptor-driven inspector renderer for process nodes.
 * Dynamically builds form controls based on {@link NodeDescriptor} and {@link NodeParameter}.
 */
public final class NodeInspectorRenderer {

    private final NodeDescriptor descriptor;
    private final ProcessDefinition.Node node;
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private final Map<String, VBox> groups = new LinkedHashMap<>();
    private final Map<String, char[]> nodeSecrets;
    private Consumer<String> changeListener;

    private NodeInspectorRenderer(NodeDescriptor descriptor, ProcessDefinition.Node node, Map<String, char[]> nodeSecrets) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor cannot be null");
        this.node = Objects.requireNonNull(node, "node cannot be null");
        this.nodeSecrets = nodeSecrets;
    }

    /**
     * Renders inspector controls into target container.
     */
    public static NodeInspectorRenderer render(NodeDescriptor descriptor,
                                              ProcessDefinition.Node node,
                                              Pane targetContainer,
                                              Map<String, char[]> nodeSecrets) {
        return render(descriptor, node, targetContainer, nodeSecrets, null);
    }

    /**
     * Renders inspector controls into target container with change notification.
     */
    public static NodeInspectorRenderer render(NodeDescriptor descriptor,
                                              ProcessDefinition.Node node,
                                              Pane targetContainer,
                                              Map<String, char[]> nodeSecrets,
                                              Consumer<String> changeListener) {
        NodeInspectorRenderer renderer = new NodeInspectorRenderer(descriptor, node, nodeSecrets);
        renderer.changeListener = changeListener;
        renderer.buildUi(targetContainer);
        return renderer;
    }

    private void buildUi(Pane targetContainer) {
        targetContainer.getChildren().clear();

        for (NodeParameter param : descriptor.parameters()) {
            VBox group = new VBox(4);
            group.setPadding(new Insets(3, 0, 3, 0));
            group.setId("param-group-" + param.key());

            Label label = new Label(t(param.labelKey()));
            label.getStyleClass().add("field-label");

            Control inputControl = createControl(param);
            controls.put(param.key(), inputControl);
            groups.put(param.key(), group);

            javafx.scene.Node controlNode = inputControl;
            if (inputControl.getProperties().containsKey("fileChooserBox")) {
                controlNode = (javafx.scene.Node) inputControl.getProperties().get("fileChooserBox");
            }

            group.getChildren().addAll(label, controlNode);

            if (param.helpKey() != null && !param.helpKey().isBlank()) {
                Label helpLabel = new Label(t(param.helpKey()));
                helpLabel.getStyleClass().add("text-muted");
                helpLabel.setWrapText(true);
                group.getChildren().add(helpLabel);
            }

            if (param.sensitive()) {
                Label secretWarn = new Label(t("module.process.secretsNotStored"));
                secretWarn.getStyleClass().add("text-warning");
                secretWarn.setWrapText(true);
                group.getChildren().add(secretWarn);
            }

            targetContainer.getChildren().add(group);
        }

        updateVisibility();
    }

    private Control createControl(NodeParameter param) {
        String currentVal = node.configuration.getOrDefault(param.key(), param.defaultValue());

        switch (param.kind()) {
            case MULTILINE -> {
                TextArea ta = new TextArea(currentVal);
                ta.setPrefRowCount(3);
                ta.setWrapText(true);
                ta.textProperty().addListener((obs, oldV, newV) -> onValueChanged(param.key()));
                return ta;
            }
            case COMBO -> {
                ComboBox<String> cb = new ComboBox<>();
                cb.setMaxWidth(Double.MAX_VALUE);
                cb.getItems().setAll(param.options());
                if (currentVal != null && cb.getItems().contains(currentVal)) {
                    cb.setValue(currentVal);
                } else if (!param.options().isEmpty()) {
                    cb.setValue(param.options().get(0));
                }
                cb.valueProperty().addListener((obs, oldV, newV) -> onValueChanged(param.key()));
                return cb;
            }
            case CHECKBOX -> {
                CheckBox chk = new CheckBox();
                chk.setSelected(Boolean.parseBoolean(currentVal));
                chk.selectedProperty().addListener((obs, oldV, newV) -> onValueChanged(param.key()));
                return chk;
            }
            case PASSWORD -> {
                PasswordField pf = new PasswordField();
                if (nodeSecrets != null && nodeSecrets.containsKey(param.key())) {
                    pf.setText(new String(nodeSecrets.get(param.key())));
                } else if (currentVal != null && !currentVal.isBlank()) {
                    pf.setText(currentVal);
                }
                pf.textProperty().addListener((obs, oldV, newV) -> onValueChanged(param.key()));
                return pf;
            }
            case FILE_OPEN, FILE_SAVE -> {
                TextField tf = new TextField(currentVal);
                tf.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(tf, Priority.ALWAYS);
                tf.textProperty().addListener((obs, oldV, newV) -> onValueChanged(param.key()));

                Button browseBtn = new Button(t("module.common.browse"));
                browseBtn.setOnAction(e -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle(t(param.labelKey()));
                    File file = (param.kind() == ParameterKind.FILE_SAVE)
                            ? chooser.showSaveDialog(tf.getScene().getWindow())
                            : chooser.showOpenDialog(tf.getScene().getWindow());
                    if (file != null) {
                        tf.setText(file.getAbsolutePath());
                    }
                });

                HBox box = new HBox(6, tf, browseBtn);
                box.setMaxWidth(Double.MAX_VALUE);
                // Return tf as primary control for registration
                tf.getProperties().put("fileChooserBox", box);
                return tf;
            }
            case NUMBER, HEX, TEXT -> {
                TextField tf = new TextField(currentVal);
                tf.textProperty().addListener((obs, oldV, newV) -> onValueChanged(param.key()));
                return tf;
            }
        }
        return new TextField(currentVal);
    }

    private void onValueChanged(String key) {
        updateVisibility();
        if (changeListener != null) {
            changeListener.accept(key);
        }
    }

    public void updateVisibility() {
        for (NodeParameter param : descriptor.parameters()) {
            VBox group = groups.get(param.key());
            if (group == null) continue;

            if (param.visibleWhen() == null || param.visibleWhen().isBlank()) {
                group.setVisible(true);
                group.setManaged(true);
                continue;
            }

            boolean visible = evaluateCondition(param.visibleWhen());
            group.setVisible(visible);
            group.setManaged(visible);
        }
    }

    private boolean evaluateCondition(String condition) {
        int eq = condition.indexOf('=');
        if (eq < 0) return true;

        String condKey = condition.substring(0, eq).trim();
        String expectedVal = condition.substring(eq + 1).trim();

        String currentVal = getParamCurrentValue(condKey);
        return Objects.equals(expectedVal, currentVal);
    }

    private String getParamCurrentValue(String key) {
        Control control = controls.get(key);
        if (control == null) {
            return node.configuration.get(key);
        }
        if (control instanceof CheckBox chk) {
            return String.valueOf(chk.isSelected());
        }
        if (control instanceof ComboBox<?> cb) {
            return cb.getValue() == null ? "" : cb.getValue().toString();
        }
        if (control instanceof TextInputControl tic) {
            return tic.getText();
        }
        return node.configuration.get(key);
    }

    /**
     * Saves form values into node configuration and transient secrets.
     */
    public void save(ProcessDefinition.Node targetNode, Map<String, char[]> secrets) {
        for (NodeParameter param : descriptor.parameters()) {
            Control control = controls.get(param.key());
            if (control == null) continue;

            if (param.sensitive()) {
                if (control instanceof PasswordField pf) {
                    String val = pf.getText();
                    if (secrets != null) {
                        secrets.put(param.key(), val != null ? val.toCharArray() : new char[0]);
                    }
                    targetNode.configuration.remove(param.key());
                }
            } else {
                String value = readControlValue(control);
                if (value != null) {
                    targetNode.configuration.put(param.key(), value);
                }
            }
        }
    }

    private String readControlValue(Control control) {
        if (control instanceof CheckBox chk) {
            return String.valueOf(chk.isSelected());
        }
        if (control instanceof ComboBox<?> cb) {
            return cb.getValue() == null ? "" : cb.getValue().toString();
        }
        if (control instanceof TextInputControl tic) {
            return tic.getText();
        }
        return "";
    }

    public Control getControl(String key) {
        return controls.get(key);
    }

    public VBox getGroup(String key) {
        return groups.get(key);
    }

    public Map<String, Control> getControls() {
        return Collections.unmodifiableMap(controls);
    }

    public Map<String, VBox> getGroups() {
        return Collections.unmodifiableMap(groups);
    }

    private static String t(String key) {
        try {
            return I18nService.getInstance().text(key);
        } catch (Exception ignored) {
            return key;
        }
    }
}
