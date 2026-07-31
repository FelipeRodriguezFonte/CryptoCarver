package com.cryptocarver.ui;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Accordion;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Captures and restores serializable JavaFX state across nested FXML controllers. */
public final class UiStateSnapshot {

    /**
     * Read-only controls that contain reusable key material rather than a derived report.
     * Explicit screen exports must preserve these artifacts so importing a key-generation
     * screen can recover the generated key pair/material exactly.
     */
    private static final Set<String> PORTABLE_READ_ONLY_ARTIFACTS = Set.of(
            "KeysController.generatedKeyField",
            "KeysController.rsaPublicKeyArea",
            "KeysController.rsaPrivateKeyArea",
            "KeysController.ecdsaPublicKeyArea",
            "KeysController.ecdsaPrivateKeyArea",
            "KeysController.dsaPublicKeyArea",
            "KeysController.dsaPrivateKeyArea",
            "KeysController.eddsaPublicKeyArea",
            "KeysController.eddsaPrivateKeyArea"
    );

    private UiStateSnapshot() {
    }

    public static Map<String, Object> capture(Object rootController) {
        return capture(rootController, CaptureMode.FULL);
    }

    public static Map<String, Object> capture(Object rootController, CaptureMode mode) {
        return capture(rootController, mode, null, null);
    }

    /** Captures only selectors and toggles, suitable for automatic history records. */
    static Map<String, Object> captureConfiguration(Object rootController) {
        return capture(rootController, CaptureMode.NON_TEXT);
    }

    public static Map<String, Object> capturePortableConfiguration(Object rootController) {
        return capture(rootController, CaptureMode.EDITABLE_INPUTS);
    }

    /** Captures editable inputs and selectors, redacting secret fields for safe history storage. */
    public static Map<String, Object> captureHistoryRecipe(Object rootController) {
        return capture(rootController, CaptureMode.HISTORY_RECIPE);
    }

    /** Captures the controls belonging to one visible operation pane. */
    static Map<String, Object> capturePortableConfiguration(
            Object rootController, Parent screenRoot, String section) {
        if (section == null || section.isBlank()) return capturePortableConfiguration(rootController);
        Node sectionRoot = findSectionRoot(screenRoot, section);
        if (sectionRoot == null) {
            throw new IllegalStateException("Unable to locate configuration section: " + section);
        }
        return capture(rootController, CaptureMode.EDITABLE_INPUTS, sectionRoot, screenRoot);
    }

    private static Map<String, Object> capture(
            Object rootController, CaptureMode mode, Node scopeRoot, Parent screenRoot) {
        Map<String, Object> state = new LinkedHashMap<>();
        visitControllers(rootController, (owner, field, value) -> {
            if (scopeRoot != null && value instanceof Node node
                    && !isDescendantOf(node, scopeRoot) && !isSharedScreenControl(node, screenRoot)) return;
            if (mode == CaptureMode.NON_TEXT && value instanceof TextInputControl) return;
            if (mode == CaptureMode.EDITABLE_INPUTS && value instanceof TextInputControl text
                    && !text.isEditable() && !PORTABLE_READ_ONLY_ARTIFACTS.contains(key(owner, field))) return;

            Object captured = readControlValue(value);
            if (captured != null) {
                if (mode == CaptureMode.HISTORY_RECIPE && isSecretField(field.getName(), value)) {
                    state.put(key(owner, field), "[REDACTED_SECRET]");
                } else {
                    state.put(key(owner, field), captured);
                }
            }
        });
        return state;
    }

    private static boolean isSecretField(String fieldName, Object control) {
        if (control instanceof javafx.scene.control.ComboBox || control instanceof javafx.scene.control.ChoiceBox || control instanceof javafx.scene.control.CheckBox) {
            return false;
        }
        String lower = fieldName.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("password") || lower.contains("key")
            || lower.contains("pin") || lower.contains("secret")
            || lower.contains("iv") || lower.contains("nonce")
            || lower.contains("aad") || lower.contains("payload")
            || lower.contains("token") || lower.contains("salt")
            || lower.contains("info") || lower.contains("verify")
            || lower.contains("signature") || lower.contains("mac")
            || lower.contains("cert") || lower.contains("input")
            || lower.contains("tag");
    }

    private static boolean isSharedScreenControl(Node node, Parent screenRoot) {
        return screenRoot != null && isDescendantOf(node, screenRoot)
                && !isInsideAnyTitledPane(node, screenRoot);
    }

    private static boolean isInsideAnyTitledPane(Node node, Parent root) {
        if (root instanceof TitledPane pane) {
            Node content = pane.getContent();
            if (content != null && isDescendantOf(node, content)) return true;
            return content instanceof Parent parent && isInsideAnyTitledPane(node, parent);
        }
        if (root instanceof Accordion accordion) {
            for (TitledPane pane : accordion.getPanes()) {
                Node content = pane.getContent();
                if (content != null && isDescendantOf(node, content)) return true;
                if (content instanceof Parent parent && isInsideAnyTitledPane(node, parent)) return true;
            }
        }
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent parent && isInsideAnyTitledPane(node, parent)) return true;
        }
        return false;
    }

    private static Node findSectionRoot(Parent root, String section) {
        if (root == null) return null;
        String wanted = normalizeSection(section);
        if (root instanceof TitledPane pane && sectionMatches(normalizeSection(pane.getText()), wanted)) {
            return pane.getContent();
        }
        if (root instanceof Accordion accordion) {
            for (TitledPane pane : accordion.getPanes()) {
                if (sectionMatches(normalizeSection(pane.getText()), wanted)) return pane.getContent();
                if (pane.getContent() instanceof Parent content) {
                    Node nested = findSectionRoot(content, section);
                    if (nested != null) return nested;
                }
            }
        }
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof TitledPane pane
                    && sectionMatches(normalizeSection(pane.getText()), wanted)) {
                return pane.getContent();
            }
            if (child instanceof Parent parent) {
                Node match = findSectionRoot(parent, section);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static boolean sectionMatches(String pane, String wanted) {
        return pane.equals(wanted) || pane.contains(wanted) || wanted.contains(pane);
    }

    private static String normalizeSection(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isDescendantOf(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }

    static List<Node> restore(Object rootController, Map<String, Object> state) {
        List<Node> redactedNodes = new ArrayList<>();
        if (rootController == null || state == null || state.isEmpty()) return redactedNodes;

        List<RestorationTask> tasks = new ArrayList<>();
        visitControllers(rootController, (owner, field, value) -> {
            String qualifiedKey = key(owner, field);
            Object saved = state.containsKey(qualifiedKey) ? state.get(qualifiedKey) : state.get(field.getName());
            if (saved != null || state.containsKey(qualifiedKey) || state.containsKey(field.getName())) {
                tasks.add(new RestorationTask(owner, field, value, saved));
            }
        });

        // Sort tasks: ComboBox/ChoiceBox first
        tasks.sort((t1, t2) -> {
            boolean isCombo1 = t1.value instanceof ComboBox<?> || t1.value instanceof ChoiceBox<?>;
            boolean isCombo2 = t2.value instanceof ComboBox<?> || t2.value instanceof ChoiceBox<?>;
            if (isCombo1 && !isCombo2) return -1;
            if (!isCombo1 && isCombo2) return 1;
            return 0;
        });

        for (RestorationTask task : tasks) {
            Object value = task.value;
            Object saved = task.saved;
            if ("[REDACTED_SECRET]".equals(saved)) {
                writeControlValue(value, "");
                if (value instanceof Node node) {
                    redactedNodes.add(node);
                }
            } else {
                writeControlValue(value, saved);
            }
        }
        return redactedNodes;
    }

    private static class RestorationTask {
        final Object owner;
        final Field field;
        final Object value;
        final Object saved;
        RestorationTask(Object owner, Field field, Object value, Object saved) {
            this.owner = owner;
            this.field = field;
            this.value = value;
            this.saved = saved;
        }
    }

    private static void visitControllers(Object rootController, FieldVisitor visitor) {
        if (rootController == null) return;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visitController(rootController, visitor, visited);
    }

    private static void visitController(Object controller, FieldVisitor visitor, Set<Object> visited) {
        if (controller == null || !visited.add(controller)) return;
        for (Field field : allFields(controller.getClass())) {
            if (!field.isAnnotationPresent(FXML.class)) continue;
            if ("keyLabImportBytesField".equals(field.getName())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(controller);
                if (value == null) continue;
                if (isSupportedControl(value)) {
                    visitor.accept(controller, field, value);
                } else if (isUiController(value)) {
                    visitController(value, visitor, visited);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A missing or inaccessible optional field must not make a session unusable.
            }
        }
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Collections.addAll(fields, current.getDeclaredFields());
        }
        return fields;
    }

    private static boolean isUiController(Object value) {
        Package ownerPackage = value.getClass().getPackage();
        return ownerPackage != null
                && ownerPackage.getName().equals(UiStateSnapshot.class.getPackageName())
                && value.getClass().getSimpleName().endsWith("Controller");
    }

    private static boolean isSupportedControl(Object value) {
        return value instanceof TextInputControl
                || value instanceof ComboBox<?>
                || value instanceof ChoiceBox<?>
                || value instanceof Spinner<?>
                || value instanceof CheckBox
                || value instanceof ToggleButton;
    }

    private static Object readControlValue(Object control) {
        if (control instanceof TextInputControl text) return text.getText();
        if (control instanceof ComboBox<?> combo) return combo.getValue();
        if (control instanceof ChoiceBox<?> choice) return choice.getValue();
        if (control instanceof Spinner<?> spinner) return spinner.getEditor().getText();
        if (control instanceof CheckBox checkBox) return checkBox.isSelected();
        if (control instanceof ToggleButton toggle) return toggle.isSelected();
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void writeControlValue(Object control, Object saved) {
        if (control instanceof TextInputControl text) {
            text.setText(saved == null ? "" : String.valueOf(saved));
        } else if (control instanceof ComboBox combo) {
            combo.setValue(saved);
        } else if (control instanceof ChoiceBox choice) {
            choice.setValue(saved);
        } else if (control instanceof Spinner<?> spinner) {
            spinner.getEditor().setText(saved == null ? "" : String.valueOf(saved));
            if (spinner.isEditable()) spinner.commitValue();
        } else if (control instanceof CheckBox checkBox) {
            checkBox.setSelected(saved instanceof Boolean value
                    ? value : Boolean.parseBoolean(String.valueOf(saved)));
        } else if (control instanceof ToggleButton toggle) {
            toggle.setSelected(saved instanceof Boolean value
                    ? value : Boolean.parseBoolean(String.valueOf(saved)));
        }
    }

    private static String key(Object owner, Field field) {
        return owner.getClass().getSimpleName() + "." + field.getName();
    }

    @FunctionalInterface
    private interface FieldVisitor {
        void accept(Object owner, Field field, Object value);
    }

    public enum CaptureMode {
        FULL,
        NON_TEXT,
        EDITABLE_INPUTS,
        HISTORY_RECIPE
    }
}
