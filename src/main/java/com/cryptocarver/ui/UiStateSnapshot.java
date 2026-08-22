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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;

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

    private static final Set<String> HISTORY_SENSITIVE_TOKENS = Set.of(
            "kbpk", "cvk", "pvk", "pin", "pan", "cvv",
            "key", "password", "secret", "private", "certificate", "cert",
            "iv", "nonce", "aad", "salt", "token", "mac", "signature",
            // Existing input controls whose contents are secret material in
            // the crypto/payment modules, even when their id is generic.
            "input", "payload", "info", "verify", "tag"
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

    /**
     * Captures editable inputs and selectors for history storage.
     *
     * <p>Whether secret fields are kept in the clear follows the Security menu's
     * {@link SecretVisibilityProfile}, the same switch that governs whether the rest of the
     * application shows secrets on screen. Under FULL_LAB, the profile a laboratory user
     * selects deliberately, a reopened history entry rebuilds the operation exactly as it
     * ran; MASKED and REDACTED keep replacing secrets with a placeholder.</p>
     *
     * <p>History is written to {@code ~/.cryptocarver/history.json} as plain text (owner-only
     * permissions, no encryption), so FULL_LAB does put that material on disk.</p>
     */
    public static Map<String, Object> captureHistoryRecipe(Object rootController) {
        return capture(rootController, CaptureMode.HISTORY_RECIPE);
    }

    /** True unless the user has selected the FULL_LAB profile in the Security menu. */
    private static boolean redactsHistorySecrets() {
        return AppSettings.getInstance().getSecretVisibilityProfile() != SecretVisibilityProfile.FULL_LAB;
    }

    /**
     * Restores a History recipe without rehydrating result panes or retaining
     * secrets that may have been left in the live screen.  History is a safe
     * configuration hand-off; it is not a result/session snapshot.
     */
    static List<Node> restoreHistoryRecipe(Object rootController, Map<String, Object> state) {
        clearHistorySensitiveControls(rootController);
        if (state == null || state.isEmpty()) return List.of();
        Map<String, Object> safe = new LinkedHashMap<>();
        state.forEach((key, value) -> {
            String field = key == null ? "" : key.substring(key.lastIndexOf('.') + 1);
            if (!isHistorySensitiveField(field) && !isResultField(field)) safe.put(key, value);
        });
        return restore(rootController, safe);
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
        // Resolved once so a single capture cannot straddle a profile change mid-traversal.
        boolean redactSecrets = mode == CaptureMode.HISTORY_RECIPE && redactsHistorySecrets();
        visitControllers(rootController, (owner, field, value) -> {
            if (scopeRoot != null && value instanceof Node node
                    && !isDescendantOf(node, scopeRoot) && !isSharedScreenControl(node, screenRoot)) return;
            if (mode == CaptureMode.NON_TEXT && value instanceof TextInputControl) return;
            // Result/report/read-only panes are execution output, never a
            // reusable History configuration. This also prevents a PEM, XML,
            // JWT or TR-31 block from being copied into a later reopen.
            if (mode == CaptureMode.HISTORY_RECIPE
                    && (isResultField(field.getName())
                    || (value instanceof TextInputControl && !((TextInputControl) value).isEditable()))) return;
            if (mode == CaptureMode.EDITABLE_INPUTS && value instanceof TextInputControl text
                    && !text.isEditable() && !PORTABLE_READ_ONLY_ARTIFACTS.contains(key(owner, field))) return;

            Object captured = readControlValue(value);
            if (captured != null) {
                if (redactSecrets && isHistorySensitiveField(field.getName(), value)) {
                    state.put(key(owner, field), "[REDACTED_SECRET]");
                } else {
                    state.put(key(owner, field), captured);
                }
            }
        });
        return state;
    }

    /** Single History policy shared by capture, restore filtering and clearing. */
    static boolean isHistorySensitiveField(String fieldName) {
        return isHistorySensitiveField(fieldName, null);
    }

    private static boolean isHistorySensitiveField(String fieldName, Object control) {
        if (isSafeHistorySelector(fieldName, control)) return false;
        String lower = fieldName == null ? "" : fieldName.toLowerCase(java.util.Locale.ROOT);
        // The certificate subject is public metadata, unlike certificate PEM
        // inputs and issuance key material.
        if ("certcnfield".equals(lower)) return false;
        return HISTORY_SENSITIVE_TOKENS.stream().anyMatch(lower::contains);
    }

    private static boolean isSafeHistorySelector(String fieldName, Object control) {
        String lower = fieldName == null ? "" : fieldName.toLowerCase(java.util.Locale.ROOT);
        boolean selectorControl = control instanceof ComboBox<?> || control instanceof ChoiceBox<?> || control instanceof CheckBox;
        boolean selectorName = lower.endsWith("combo") || lower.endsWith("choice") || lower.endsWith("check");
        if (!selectorControl && !selectorName) return false;
        return lower.contains("algorithm") || lower.contains("algo")
                || lower.contains("format") || lower.contains("mode")
                || lower.contains("size") || lower.contains("usage")
                || lower.contains("keytype") || lower.contains("keyalgorithm")
                || lower.contains("keyusage");
    }

    private static boolean isResultField(String fieldName) {
        String lower = fieldName == null ? "" : fieldName.toLowerCase(java.util.Locale.ROOT);
        // These are input settings despite containing "output" in their FXML
        // id. They are needed to reproduce a history recipe and contain no
        // derived data or secret material.
        if (lower.contains("outputlength") || lower.contains("outputpath")
                || lower.contains("outputcolumn") || lower.contains("outputformat")
                || lower.contains("outputfile")) {
            return false;
        }
        return lower.contains("output") || lower.contains("result") || lower.contains("report")
                || lower.contains("diagnostic") || lower.contains("fingerprint")
                || lower.contains("kcv") || lower.contains("summary");
    }

    private static void clearHistorySensitiveControls(Object rootController) {
        if (rootController == null) return;
        visitControllers(rootController, (owner, field, value) -> {
            if (value instanceof TextInputControl input
                    && (isHistorySensitiveField(field.getName(), value) || isResultField(field.getName()))) {
                input.clear();
            }
        });
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
        return findSectionRoot(root, acceptedSectionTexts(section));
    }

    private static Node findSectionRoot(Parent root, Set<String> accepted) {
        if (root == null) return null;
        if (root instanceof TitledPane pane && sectionMatches(normalizeSection(pane.getText()), accepted)) {
            return pane.getContent();
        }
        if (root instanceof Accordion accordion) {
            for (TitledPane pane : accordion.getPanes()) {
                if (sectionMatches(normalizeSection(pane.getText()), accepted)) return pane.getContent();
                if (pane.getContent() instanceof Parent content) {
                    Node nested = findSectionRoot(content, accepted);
                    if (nested != null) return nested;
                }
            }
        }
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof TitledPane pane
                    && sectionMatches(normalizeSection(pane.getText()), accepted)) {
                return pane.getContent();
            }
            if (child instanceof Parent parent) {
                Node match = findSectionRoot(parent, accepted);
                if (match != null) return match;
            }
        }
        return null;
    }

    /**
     * Routes address a section by its canonical English name, but the pane on screen shows
     * whatever the active language renders. Matching the canonical name alone therefore finds
     * nothing on a translated UI, which surfaced as "Unable to locate configuration section"
     * for every section that actually has a translation.
     */
    private static Set<String> acceptedSectionTexts(String section) {
        Set<String> accepted = new LinkedHashSet<>();
        String canonical = normalizeSection(section);
        if (canonical.isEmpty()) return accepted;
        accepted.add(canonical);
        for (Map<String, String> module : ModuleTextCatalog.allModules()) {
            module.forEach((source, bundleKey) -> {
                // Containment one way only: a pane title may carry more than the section
                // name ("Key Sharing" lives in "Key Sharing (XOR Split/Combine)"), but the
                // reverse would let a short generic entry such as "Inspect" drag in the
                // translation of an unrelated control.
                if (normalizeSection(source).contains(canonical)) {
                    accepted.add(normalizeSection(I18nService.getInstance().text(bundleKey)));
                }
            });
        }
        accepted.remove("");
        return accepted;
    }

    private static boolean sectionMatches(String pane, Set<String> accepted) {
        // An untitled pane normalizes to "", which every candidate trivially contains;
        // without this guard the first such pane would swallow the lookup.
        if (pane.isEmpty()) return false;
        for (String wanted : accepted) {
            if (pane.equals(wanted) || pane.contains(wanted) || wanted.contains(pane)) return true;
        }
        return false;
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
