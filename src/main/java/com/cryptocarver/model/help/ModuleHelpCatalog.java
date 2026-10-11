package com.cryptocarver.model.help;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Help definitions for module screens, declared in {@code /help/modules.spec}.
 * Each operation lists its actions and fields together with the node ids of the controls
 * they belong to, so the shell can attach focus-following help without per-controller wiring.
 */
public final class ModuleHelpCatalog {
    /** A field of an operation and the ids of the controls that edit or show it. */
    public record Field(String id, List<String> controls) { }
    /** An action, the ids of the controls that trigger it and the fields it reads or writes. */
    public record Action(String id, List<String> controls, List<String> fieldIds) { }
    public record Spec(String id, String fxml, List<Action> actions, List<Field> fields) {
        /** The action a field is explained under: the preferred one when it uses the field, else the first that does. */
        public String actionFor(String fieldId, String preferredAction) {
            String first = "";
            for (Action action : actions) {
                if (!action.fieldIds().contains(fieldId)) continue;
                if (action.id().equals(preferredAction)) return preferredAction;
                if (first.isEmpty()) first = action.id();
            }
            return first;
        }
    }

    private static final Map<String, Spec> SPECS = load();
    private ModuleHelpCatalog() { }

    public static boolean supports(String operationId) { return SPECS.containsKey(operationId); }
    public static Spec spec(String operationId) { return SPECS.get(operationId); }
    public static List<Spec> specs() { return List.copyOf(SPECS.values()); }
    public static List<OperationHelpDefinition> definitions() { return SPECS.values().stream().map(ModuleHelpCatalog::build).toList(); }
    public static OperationHelpDefinition find(String operationId) {
        Spec spec = SPECS.get(operationId);
        return spec == null ? null : build(spec);
    }

    private static OperationHelpDefinition build(Spec spec) {
        Map<String, FieldHelp> fields = new LinkedHashMap<>();
        for (Field field : spec.fields()) {
            String prefix = "help.field." + spec.id() + "." + field.id();
            fields.put(field.id(), new FieldHelp(field.id(), prefix + ".label", prefix + ".description", ""));
        }
        List<ActionHelp> actions = new ArrayList<>();
        for (Action action : spec.actions()) {
            String prefix = "help.action." + spec.id() + "." + action.id();
            actions.add(new ActionHelp(action.id(), prefix + ".title", prefix + ".description",
                    action.fieldIds().stream().map(fields::get).toList(), List.of(), "", ""));
        }
        return new OperationHelpDefinition(spec.id(), "help.operation." + spec.id() + ".purpose", actions, "", true);
    }

    private static Map<String, Spec> load() {
        Map<String, Spec> specs = new LinkedHashMap<>();
        try (InputStream stream = ModuleHelpCatalog.class.getResourceAsStream("/help/modules.spec")) {
            if (stream == null) return specs;
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String id = null, fxml = "";
            List<Action> actions = new ArrayList<>();
            List<Field> fields = new ArrayList<>();
            int number = 0;
            for (String raw; (raw = reader.readLine()) != null; ) {
                number++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("@")) {
                    if (id != null) specs.put(id, validated(new Spec(id, fxml, List.copyOf(actions), List.copyOf(fields))));
                    String[] head = line.substring(1).split("\\s+");
                    if (head.length != 2) throw new IllegalStateException("modules.spec:" + number + " expects '@operation file.fxml'");
                    id = head[0]; fxml = head[1];
                    actions = new ArrayList<>(); fields = new ArrayList<>();
                    continue;
                }
                int colon = line.indexOf(':');
                if (id == null || colon < 1) throw new IllegalStateException("modules.spec:" + number + " is not a field or action");
                String name = line.substring(0, colon).strip(), rest = line.substring(colon + 1);
                if (name.startsWith(".")) {
                    fields.add(new Field(name.substring(1), list(rest)));
                } else {
                    String[] parts = rest.split("->", 2);
                    actions.add(new Action(name, list(parts[0]), parts.length == 2 ? list(parts[1]) : List.of()));
                }
            }
            if (id != null) specs.put(id, validated(new Spec(id, fxml, List.copyOf(actions), List.copyOf(fields))));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return specs;
    }

    private static Spec validated(Spec spec) {
        List<String> known = spec.fields().stream().map(Field::id).toList();
        for (Action action : spec.actions()) for (String field : action.fieldIds())
            if (!known.contains(field)) throw new IllegalStateException(spec.id() + "/" + action.id() + " uses undeclared field " + field);
        return spec;
    }

    private static List<String> list(String text) {
        List<String> values = new ArrayList<>();
        for (String value : text.split(",")) if (!value.isBlank()) values.add(value.strip());
        return List.copyOf(values);
    }
}
