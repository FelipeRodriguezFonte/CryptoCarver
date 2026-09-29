package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ScreenConfiguration;
import com.cryptocarver.model.ScreenConfigurationCodec;
import com.cryptocarver.model.ScreenConfigurationFiles;
import com.cryptocarver.model.ScreenConfigurationImportException;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Captures, validates, exports and imports portable screen configuration. */
public final class ScreenConfigurationCoordinator {
    public record ConfigurationTarget(Object controller, Parent root) { }

    private final Supplier<String> activeOperation;
    private final Supplier<ComboBox<String>> inputFormat;
    private final Supplier<ComboBox<String>> outputFormat;
    private final Supplier<SecretVisibilityProfile> visibilityProfile;
    private final Function<UiNavigationRegistry.Module, ConfigurationTarget> targetProvider;
    private final Consumer<String> navigate;
    private final Consumer<Map<String, Object>> restore;
    private final Supplier<Window> owner;
    private final DialogService dialogs;
    private final I18nService i18n;
    private final Consumer<String> status;
    private java.util.function.Supplier<File> importFileSupplier;
    private Function<Boolean, Optional<char[]>> passwordPrompt;
    private boolean passwordPromptInjectedForTesting;

    public ScreenConfigurationCoordinator(Supplier<String> activeOperation,
            Supplier<ComboBox<String>> inputFormat, Supplier<ComboBox<String>> outputFormat,
            Supplier<SecretVisibilityProfile> visibilityProfile,
            Function<UiNavigationRegistry.Module, ConfigurationTarget> targetProvider,
            Consumer<String> navigate, Consumer<Map<String, Object>> restore,
            Supplier<Window> owner, DialogService dialogs, I18nService i18n, Consumer<String> status) {
        this.activeOperation = activeOperation;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.visibilityProfile = visibilityProfile;
        this.targetProvider = targetProvider;
        this.navigate = navigate;
        this.restore = restore;
        this.owner = owner;
        this.dialogs = dialogs;
        this.i18n = i18n;
        this.status = status;
        this.importFileSupplier = this::chooseImportFile;
        this.passwordPrompt = this::showPasswordPrompt;
    }

    public ScreenConfiguration captureActiveScreenConfiguration() {
        String operation = activeOperation.get();
        UiNavigationRegistry.Route route = activeRoute(operation);
        ConfigurationTarget target = route == null ? null : targetProvider.apply(route.module());
        if (route == null || target == null || target.controller() == null || target.root() == null) {
            throw new IllegalStateException(i18n.text("dialog.configuration.unsupportedScreen"));
        }
        Map<String, Object> state = new LinkedHashMap<>(UiStateSnapshot.capturePortableConfiguration(
                target.controller(), target.root(), route.section()));
        ComboBox<String> input = inputFormat.get();
        if (input != null && input.getValue() != null) state.put("ModernMainController.inputFormatCombo", input.getValue());
        ComboBox<String> output = outputFormat.get();
        if (output != null && output.getValue() != null) state.put("ModernMainController.outputFormatCombo", output.getValue());
        return new ScreenConfiguration(operation, route.module().name(), state, visibilityProfile.get());
    }

    public void applyScreenConfiguration(ScreenConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException(i18n.text("dialog.configuration.required"));
        String operation = com.cryptocarver.model.OperationRegistry.getInstance()
                .resolveNavigation(configuration.operation())
                .map(com.cryptocarver.model.OperationDescriptor::getNavigationPath)
                .orElse(configuration.operation());
        UiNavigationRegistry.Route route = UiNavigationRegistry.resolve(operation)
                .orElseThrow(() -> new IllegalArgumentException(i18n.text("dialog.configuration.unsupportedOperation")));
        if (!route.module().name().equals(configuration.module())) {
            throw new IllegalArgumentException(i18n.text("dialog.configuration.moduleMismatch"));
        }

        ConfigurationTarget target = targetProvider.apply(route.module());
        if (target == null || target.controller() == null || target.root() == null) {
            throw new IllegalArgumentException(i18n.text("dialog.configuration.targetUnavailable"));
        }
        Set<String> allowed = new LinkedHashSet<>(UiStateSnapshot.capturePortableConfiguration(
                target.controller(), target.root(), route.section()).keySet());
        if (configuration.version() == 1) {
            allowed.addAll(UiStateSnapshot.capturePortableConfiguration(target.controller()).keySet());
        }
        allowed.add("ModernMainController.inputFormatCombo");
        allowed.add("ModernMainController.outputFormatCombo");
        Map<String, Object> state = configuration.toState();
        Set<String> unknown = new LinkedHashSet<>(state.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(i18n.text("dialog.configuration.fieldsOutsideScreen")
                    + String.join(", ", unknown.stream().limit(5).toList()));
        }

        navigate.accept(operation);
        restore.accept(state);
        status.accept(i18n.text("status.configuration.loaded", operation));
    }

    public void exportScreenConfiguration() {
        final ScreenConfiguration configuration;
        try {
            configuration = captureActiveScreenConfiguration();
        } catch (Exception failure) {
            dialogs.warning(owner.get(), i18n.text("dialog.configuration.exportTitle"), failure.getMessage());
            return;
        }

        String encryptedOption = i18n.text("dialog.configuration.encryptedOption");
        ChoiceDialog<String> mode = new ChoiceDialog<>(encryptedOption,
                encryptedOption, i18n.text("dialog.configuration.unencryptedJson"));
        dialogs.configure(mode, owner.get(), i18n.text("dialog.configuration.exportTitle"),
                i18n.text("dialog.configuration.exportHeader"));
        mode.setContentText(i18n.text("dialog.configuration.protectionPrompt"));
        Optional<String> selected = mode.showAndWait();
        if (selected.isEmpty()) return;

        boolean encrypted = isEncryptedConfigurationOption(selected.get(), encryptedOption);
        char[] password = null;
        try {
            if (encrypted) {
                Optional<char[]> selectedPassword = promptConfigurationPassword(true);
                if (selectedPassword.isEmpty()) return;
                password = selectedPassword.get();
            }
            FileChooser chooser = dialogs.createFileChooser("screen-configuration",
                    i18n.text("dialog.configuration.exportTitle"), new FileChooser.ExtensionFilter(
                            i18n.text(encrypted ? "dialog.configuration.encryptedFilter" : "dialog.configuration.plainFilter"),
                            encrypted ? "*.ccconfig" : "*.json"));
            chooser.setInitialFileName("cryptocarver-" + safeFileName(configuration.operation())
                    + (encrypted ? ".ccconfig" : ".json"));
            File chosen = chooser.showSaveDialog(owner.get());
            if (chosen == null) return;
            File file = withoutRepeatedExtension(chosen, encrypted ? ".ccconfig" : ".json");
            exportTo(file.toPath(), configuration, encrypted, password);
            status.accept(i18n.text("status.configuration.exported", file.getName()));
            dialogs.info(owner.get(), i18n.text("dialog.configuration.exportedTitle"), i18n.text(
                    encrypted ? "dialog.configuration.encryptedSaved" : "dialog.configuration.plainSavedRedacted"));
        } catch (Exception failure) {
            dialogs.error(owner.get(), i18n.text("dialog.configuration.exportFailureTitle"),
                    i18n.text("dialog.configuration.exportFailure"));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    public void importScreenConfiguration() {
        File file = importFileSupplier.get();
        if (file == null) return;
        try {
            String document = readDocument(file.toPath());
            ScreenConfiguration configuration = decodeImportDocument(document);
            if (configuration == null) return;
            Label summary = new Label(configurationSummary(configuration));
            summary.setWrapText(true);
            ButtonType cancel = new ButtonType(i18n.text("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType proceed = new ButtonType(i18n.text("dialog.configuration.importAction"), ButtonBar.ButtonData.OK_DONE);
            if (dialogs.showForImport(Alert.AlertType.CONFIRMATION, owner.get(),
                    i18n.text("dialog.configuration.importTitle"), i18n.text("dialog.configuration.reviewTitle"),
                    summary, cancel, proceed).filter(proceed::equals).isEmpty()) return;
            applyScreenConfiguration(configuration);
            if (isLegacyKeyGenerationConfiguration(configuration)) {
                dialogs.warning(owner.get(), i18n.text("dialog.configuration.legacyTitle"),
                        i18n.text("dialog.configuration.legacyMessage"));
            }
            status.accept(i18n.text("status.configuration.imported", configuration.operation()));
        } catch (ScreenConfigurationImportException failure) {
            showImportFailure(failure.reason(), false);
        } catch (IOException failure) {
            showImportFailure(ScreenConfigurationImportException.Reason.UNREADABLE, false);
        } catch (IllegalArgumentException failure) {
            showImportFailure(ScreenConfigurationImportException.Reason.NOT_A_CONFIGURATION, false);
        } catch (Exception failure) {
            dialogs.error(owner.get(), i18n.text("dialog.configuration.importFailureTitle"),
                    i18n.text("dialog.configuration.importFailure"));
        }
    }

    void setImportFileSupplierForTesting(java.util.function.Supplier<File> supplier) {
        importFileSupplier = supplier;
    }

    void setPasswordPromptForTesting(Function<Boolean, Optional<char[]>> prompt) {
        passwordPrompt = prompt;
        passwordPromptInjectedForTesting = true;
    }

    void setDialogObserverForTesting(java.util.function.Consumer<String> observer) {
        dialogs.setTestModeObserverForTesting(observer);
    }

    void setDialogSelectionForTesting(Function<ButtonType[], Optional<ButtonType>> selection) {
        dialogs.setTestModeSelectionForTesting(selection);
    }

    private File chooseImportFile() {
        FileChooser chooser = dialogs.createFileChooser("screen-configuration",
                i18n.text("dialog.configuration.importTitle"),
                new FileChooser.ExtensionFilter(i18n.text("dialog.configuration.filter"), "*.ccconfig", "*.json"),
                new FileChooser.ExtensionFilter(i18n.text("dialog.allFiles"), "*.*"));
        return chooser.showOpenDialog(owner.get());
    }

    private Optional<char[]> showPasswordPrompt(boolean confirmationRequired) {
        return createPasswordDialog(confirmationRequired).showAndWait();
    }

    /** Testable filesystem boundary; callers own the dialog/choice flow. */
    void exportTo(Path target, ScreenConfiguration configuration, boolean encrypted, char[] password) throws IOException {
        String document = encrypted ? ScreenConfigurationCodec.encodeEncrypted(configuration, password)
                : ScreenConfigurationCodec.encodePlain(configuration);
        ScreenConfigurationFiles.writeAtomic(target, document);
    }

    /** Testable filesystem boundary; decode completes before a caller can apply the result. */
    ScreenConfiguration importFrom(Path source, char[] password) throws IOException {
        return decodeDocument(readDocument(source), password);
    }

    String readDocument(Path source) throws IOException {
        try {
            return ScreenConfigurationFiles.read(source);
        } catch (IOException | IllegalArgumentException failure) {
            throw new ScreenConfigurationImportException(ScreenConfigurationImportException.Reason.UNREADABLE);
        }
    }

    private ScreenConfiguration decodeImportDocument(String document) {
        if (!ScreenConfigurationCodec.isEncrypted(document)) return decodeDocument(document, null);
        try {
            return decodeDocument(document, null);
        } catch (ScreenConfigurationImportException failure) {
            if (failure.reason() != ScreenConfigurationImportException.Reason.WRONG_PASSWORD_OR_TAMPERED) throw failure;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<char[]> selected = promptConfigurationPassword(false);
            if (selected.isEmpty()) return null;
            char[] password = selected.get();
            try {
                return decodeDocument(document, password);
            } catch (ScreenConfigurationImportException failure) {
                if (failure.reason() != ScreenConfigurationImportException.Reason.WRONG_PASSWORD_OR_TAMPERED) throw failure;
                if (!showImportFailure(failure.reason(), attempt < 2)) return null;
            } finally {
                Arrays.fill(password, '\0');
            }
        }
        return null;
    }

    private boolean showImportFailure(ScreenConfigurationImportException.Reason reason, boolean canRetry) {
        String key = "dialog.configuration.importFailure." + switch (reason) {
            case WRONG_PASSWORD_OR_TAMPERED -> "wrongPasswordOrTampered";
            case NOT_A_CONFIGURATION -> "notAConfiguration";
            case UNSUPPORTED_VERSION -> "unsupportedVersion";
            case UNREADABLE -> "unreadable";
            case EMPTY -> "empty";
        };
        String message = i18n.text(key);
        ButtonType cancel = new ButtonType(i18n.text("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType retry = new ButtonType(i18n.text("dialog.configuration.retryPassword"), ButtonBar.ButtonData.OK_DONE);
        ButtonType acknowledge = new ButtonType(i18n.text("dialog.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType[] buttons = canRetry ? new ButtonType[]{cancel, retry} : new ButtonType[]{acknowledge};
        Optional<ButtonType> selected = dialogs.showForImport(Alert.AlertType.ERROR, owner.get(),
                i18n.text("dialog.configuration.importFailureTitle"), message, null, buttons);
        return canRetry && selected.filter(retry::equals).isPresent();
    }

    ScreenConfiguration decodeDocument(String document, char[] password) {
        return ScreenConfigurationCodec.decode(document, password);
    }

    private String configurationSummary(ScreenConfiguration configuration) {
        return i18n.text("dialog.configuration.reviewOperation") + ": " + configuration.operation()
                + "\n" + i18n.text("dialog.configuration.reviewModule") + ": " + configuration.module()
                + "\n" + i18n.text("dialog.configuration.reviewFields") + ": " + configuration.values().size()
                + "\n" + i18n.text("dialog.configuration.reviewCreated") + ": " + configuration.createdAt()
                + "\n\n" + i18n.text("dialog.configuration.reviewWarning");
    }

    Dialog<char[]> createPasswordDialog(boolean confirmationRequired) {
        Dialog<char[]> dialog = new Dialog<>();
        ButtonType accept = new ButtonType(i18n.text(confirmationRequired
                ? "dialog.configuration.encrypt" : "dialog.configuration.unlock"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(i18n.text("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, accept);
        PasswordField password = new PasswordField();
        password.setPromptText(i18n.text("dialog.configuration.password"));
        VBox fields = new VBox(8, new Label(i18n.text("dialog.configuration.passwordLabel")), password);
        PasswordField confirmation = null;
        if (confirmationRequired) {
            confirmation = new PasswordField();
            confirmation.setPromptText(i18n.text("dialog.configuration.repeatPassword"));
            fields.getChildren().addAll(new Label(i18n.text("dialog.configuration.repeatPasswordLabel")), confirmation);
        }
        dialog.getDialogPane().setContent(fields);
        dialogs.configure(dialog, owner.get(), i18n.text(confirmationRequired
                ? "dialog.configuration.protectTitle" : "dialog.configuration.unlockTitle"),
                i18n.text(confirmationRequired ? "dialog.configuration.protectHeader" : "dialog.configuration.unlockHeader"));
        PasswordField confirmationField = confirmation;
        Node acceptButton = dialog.getDialogPane().lookupButton(accept);
        acceptButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> password.getText().length() < 8 || (confirmationField != null
                        && !password.getText().equals(confirmationField.getText())),
                confirmationField == null ? new javafx.beans.Observable[]{password.textProperty()}
                        : new javafx.beans.Observable[]{password.textProperty(), confirmationField.textProperty()}));
        dialog.setResultConverter(button -> {
            if (button != accept) return null;
            char[] result = password.getText().toCharArray();
            char[] repeated = confirmationField == null ? null : confirmationField.getText().toCharArray();
            password.clear();
            if (confirmationField != null) confirmationField.clear();
            if (repeated != null) Arrays.fill(repeated, '\0');
            return result;
        });
        Platform.runLater(password::requestFocus);
        return dialog;
    }

    private Optional<char[]> promptConfigurationPassword(boolean confirmationRequired) {
        if (Boolean.getBoolean("test.mode") && !passwordPromptInjectedForTesting) return Optional.empty();
        return passwordPrompt.apply(confirmationRequired);
    }

    static boolean isEncryptedConfigurationOption(String selectedOption, String encryptedOption) {
        return encryptedOption.equals(selectedOption);
    }

    /**
     * The macOS save panel appends the filter's extension again when it does not
     * recognise it, producing {@code name.ccconfig.ccconfig}.
     */
    static File withoutRepeatedExtension(File file, String extension) {
        String name = file.getName();
        String doubled = extension + extension;
        if (!name.toLowerCase(Locale.ROOT).endsWith(doubled)) return file;
        return new File(file.getParentFile(), name.substring(0, name.length() - extension.length()));
    }

    static boolean isLegacyKeyGenerationConfiguration(ScreenConfiguration configuration) {
        if (configuration == null || !"Key Generation".equals(configuration.operation())) return false;
        ScreenConfiguration.Value generated = configuration.values().get("KeysController.generatedKeyField");
        return generated == null || generated.value().isBlank();
    }

    private static UiNavigationRegistry.Route activeRoute(String operation) {
        if (operation != null && operation.startsWith("Hashing: ")) operation = "Hashing";
        return UiNavigationRegistry.resolve(operation).orElse(null);
    }

    private static String safeFileName(String value) {
        String safe = value == null ? "screen" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return safe.isBlank() ? "screen" : safe;
    }
}
