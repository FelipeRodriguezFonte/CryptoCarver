package com.cryptocarver.ui;

import com.cryptocarver.model.PersonalTemplateStore;
import com.cryptocarver.model.SafeOperationTemplate;
import com.cryptocarver.model.SafeTemplateAllowlist;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reusable UI helper for managing and applying safe operation templates.
 */
public final class SafeTemplateUIHelper {

    private static final String MY_TEMPLATE_PREFIX = "[My Template] ";

    private SafeTemplateUIHelper() {
    }

    public static void populateTemplateCombo(ComboBox<String> combo, String module, List<String> builtInTemplates) {
        if (combo == null) return;
        String currentSelection = combo.getValue();

        List<String> items = new ArrayList<>();
        if (builtInTemplates != null) {
            items.addAll(builtInTemplates);
        }

        List<SafeOperationTemplate> userTemplates = PersonalTemplateStore.getInstance().getTemplatesForModule(module);
        for (SafeOperationTemplate template : userTemplates) {
            items.add(MY_TEMPLATE_PREFIX + template.getName());
        }

        combo.getItems().setAll(items);
        if (currentSelection != null && combo.getItems().contains(currentSelection)) {
            combo.setValue(currentSelection);
        }
    }

    public static void saveCurrentAsTemplate(Window owner, String module, Map<String, String> currentParameters, Runnable refreshCallback, StatusReporter statusReporter) {
        if (currentParameters == null || currentParameters.isEmpty()) {
            showAlert(owner, Alert.AlertType.WARNING, "No Settings Selected", "There are no valid configuration settings to save in this template.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Save Personal Template");
        dialog.setHeaderText("Save Safe Operation Template for " + module);
        dialog.setContentText("Template Name:");
        if (owner != null) dialog.initOwner(owner);

        Optional<String> nameResult = dialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().isBlank()) {
            return;
        }

        String name = nameResult.get().trim();
        try {
            SafeOperationTemplate template = new SafeOperationTemplate(name, module, "Personal user template", currentParameters);
            PersonalTemplateStore.getInstance().saveTemplate(template);

            if (refreshCallback != null) refreshCallback.run();
            if (statusReporter != null) {
                statusReporter.updateStatus("Saved personal template: '" + name + "'");
            }
            showAlert(owner, Alert.AlertType.INFORMATION, "Template Saved", "Personal template '" + name + "' saved successfully (without keys or sensitive data).");
        } catch (Exception e) {
            showAlert(owner, Alert.AlertType.ERROR, "Error Saving Template", e.getMessage());
        }
    }

    public static void exportSelectedTemplate(Window owner, String module, ComboBox<String> combo, StatusReporter statusReporter) {
        if (combo == null || combo.getValue() == null) {
            showAlert(owner, Alert.AlertType.WARNING, "No Template Selected", "Please select a template from the list to export.");
            return;
        }

        String selected = combo.getValue();
        SafeOperationTemplate template = findTemplateBySelection(module, selected);
        if (template == null) {
            showAlert(owner, Alert.AlertType.WARNING, "Cannot Export Built-in Template", "Please select a personal template [My Template] to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Safe Operation Template");
        fileChooser.setInitialFileName(sanitizeFileName(template.getName()) + ".json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Safe Operation Template (*.json)", "*.json"));

        File file = fileChooser.showSaveDialog(owner);
        if (file != null) {
            try {
                PersonalTemplateStore.getInstance().exportTemplate(template, file.toPath());
                if (statusReporter != null) {
                    statusReporter.updateStatus("Exported template '" + template.getName() + "' to " + file.getName());
                }
                showAlert(owner, Alert.AlertType.INFORMATION, "Template Exported", "Template exported to " + file.getName() + ".\nThis file contains only non-secret settings and can be safely shared.");
            } catch (Exception e) {
                showAlert(owner, Alert.AlertType.ERROR, "Export Failed", e.getMessage());
            }
        }
    }

    public static void importTemplate(Window owner, String module, Runnable refreshCallback, StatusReporter statusReporter) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Safe Operation Template");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Safe Operation Template (*.json)", "*.json"));

        File file = fileChooser.showOpenDialog(owner);
        if (file != null) {
            try {
                SafeOperationTemplate imported = PersonalTemplateStore.getInstance().importTemplate(file.toPath());
                if (refreshCallback != null) refreshCallback.run();
                if (statusReporter != null) {
                    statusReporter.updateStatus("Imported safe template: '" + imported.getName() + "'");
                }
                showAlert(owner, Alert.AlertType.INFORMATION, "Template Imported", "Successfully imported template '" + imported.getName() + "' for module '" + imported.getModule() + "'.");
            } catch (Exception e) {
                showAlert(owner, Alert.AlertType.ERROR, "Import Rejected (Security/Validation)", "Failed to import template:\n" + e.getMessage() + "\n\nSafe templates must not contain secret keys, IVs, nonces, inputs, or forbidden fields.");
            }
        }
    }

    public static void deleteSelectedTemplate(Window owner, String module, ComboBox<String> combo, Runnable refreshCallback, StatusReporter statusReporter) {
        if (combo == null || combo.getValue() == null) {
            showAlert(owner, Alert.AlertType.WARNING, "No Template Selected", "Please select a personal template to delete.");
            return;
        }

        String selected = combo.getValue();
        SafeOperationTemplate template = findTemplateBySelection(module, selected);
        if (template == null) {
            showAlert(owner, Alert.AlertType.WARNING, "Cannot Delete Built-in Template", "Built-in default templates cannot be deleted.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete personal template '" + template.getName() + "'?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Personal Template");
        confirm.setHeaderText("Confirm Deletion");
        if (owner != null) confirm.initOwner(owner);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            boolean deleted = PersonalTemplateStore.getInstance().deleteTemplate(template.getId());
            if (deleted) {
                if (refreshCallback != null) refreshCallback.run();
                if (statusReporter != null) {
                    statusReporter.updateStatus("Deleted personal template: '" + template.getName() + "'");
                }
            }
        }
    }

    public static boolean applySelectedTemplate(String selected, String module, Runnable applyBuiltIn, Map<String, Consumer<String>> controlSetters, StatusReporter statusReporter) {
        if (selected == null || selected.isBlank()) return false;

        SafeOperationTemplate personalTemplate = findTemplateBySelection(module, selected);
        if (personalTemplate != null) {
            SafeTemplateAllowlist.validateTemplate(personalTemplate);
            Map<String, String> params = personalTemplate.getParameters();

            int appliedCount = 0;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String simpleKey = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
                String val = entry.getValue();

                if (controlSetters.containsKey(simpleKey)) {
                    controlSetters.get(simpleKey).accept(val);
                    appliedCount++;
                } else if (controlSetters.containsKey(key)) {
                    controlSetters.get(key).accept(val);
                    appliedCount++;
                }
            }

            if (statusReporter != null) {
                statusReporter.updateStatus("Applied Personal Template: " + personalTemplate.getName() + " (" + appliedCount + " settings updated)");
            }
            return true;
        } else if (applyBuiltIn != null) {
            applyBuiltIn.run();
            return true;
        }
        return false;
    }

    private static SafeOperationTemplate findTemplateBySelection(String module, String selected) {
        if (selected == null || !selected.startsWith(MY_TEMPLATE_PREFIX)) return null;
        String rawName = selected.substring(MY_TEMPLATE_PREFIX.length()).trim();

        List<SafeOperationTemplate> userTemplates = PersonalTemplateStore.getInstance().getTemplatesForModule(module);
        for (SafeOperationTemplate t : userTemplates) {
            if (t.getName().equalsIgnoreCase(rawName)) {
                return t;
            }
        }
        return null;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void showAlert(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
