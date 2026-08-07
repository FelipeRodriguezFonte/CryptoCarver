package com.cryptocarver.ui;

import com.cryptocarver.crypto.hsm.Pkcs11DiagnosticResult;
import com.cryptocarver.crypto.hsm.Pkcs11InventoryResult;
import com.cryptocarver.crypto.hsm.Pkcs11LibraryDiagnosticService;
import com.cryptocarver.crypto.hsm.Pkcs11LibraryInventoryService;
import com.cryptocarver.model.Pkcs11Profile;
import com.cryptocarver.model.Pkcs11ProfileRepository;
import javafx.scene.control.Button;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Small orchestration layer for the safe PKCS#11 profile/inventory panel.
 * It deliberately knows nothing about authentication data or token objects.
 */
public final class Pkcs11ProfilesPresenter {
    public interface View {
        String profileName();

        String libraryPath();

        String slotListIndex();

        void setProfiles(List<Pkcs11Profile> profiles);

        void rehydrate(Pkcs11Profile profile);

        void clearEditor();

        void setBusy(boolean busy);

        void showValidation(String fieldKey, String messageKey);

        void showSaveError(String messageKey);

        void showDiagnostic(Pkcs11DiagnosticResult result);

        void showInventory(Pkcs11InventoryResult result);

        void showOperationError(String messageKey);

        void showCancelled();
    }

    @FunctionalInterface
    public interface OperationRunner {
        <T> void execute(String operationName, Button triggerButton, Callable<T> task,
                         Consumer<T> onSuccess, Consumer<Throwable> onFailure, Runnable onCancelled);
    }

    public static OperationRunner from(OperationExecutor executor) {
        Objects.requireNonNull(executor, "Operation executor is required");
        return executor::execute;
    }

    private final Pkcs11ProfileRepository repository;
    private final Pkcs11LibraryDiagnosticService diagnosticService;
    private final Pkcs11LibraryInventoryService inventoryService;
    private final OperationRunner operationRunner;
    private final View view;
    private Pkcs11Profile editingProfile;
    private Pkcs11DiagnosticResult lastDiagnostic;

    public Pkcs11ProfilesPresenter(Pkcs11ProfileRepository repository,
                                   Pkcs11LibraryDiagnosticService diagnosticService,
                                   Pkcs11LibraryInventoryService inventoryService,
                                   OperationRunner operationRunner,
                                   View view) {
        this.repository = Objects.requireNonNull(repository, "Profile repository is required");
        this.diagnosticService = Objects.requireNonNull(diagnosticService, "Diagnostic service is required");
        this.inventoryService = Objects.requireNonNull(inventoryService, "Inventory service is required");
        this.operationRunner = Objects.requireNonNull(operationRunner, "Operation runner is required");
        this.view = Objects.requireNonNull(view, "PKCS#11 profile view is required");
    }

    public void load() {
        view.setProfiles(repository.list());
    }

    public void select(Pkcs11Profile profile) {
        if (profile == null) return;
        editingProfile = profile;
        lastDiagnostic = null;
        view.rehydrate(profile);
    }

    public void newProfile() {
        editingProfile = null;
        lastDiagnostic = null;
        view.clearEditor();
    }

    /** Invalidates a previous diagnosis when the user edits the selected profile. */
    public void profileInputChanged() {
        lastDiagnostic = null;
    }

    public void save() {
        Pkcs11Profile profile = readProfile();
        if (profile == null) return;
        try {
            if (editingProfile == null) repository.create(profile);
            else repository.update(editingProfile.name(), profile);
            editingProfile = profile;
            lastDiagnostic = null;
            view.setProfiles(repository.list());
            view.rehydrate(profile);
        } catch (IllegalArgumentException duplicateOrInvalid) {
            view.showSaveError(duplicateOrInvalid.getMessage() != null
                    && duplicateOrInvalid.getMessage().toLowerCase(java.util.Locale.ROOT).contains("already")
                    ? "pkcs11.validation.duplicate" : "pkcs11.validation.save");
        } catch (RuntimeException persistenceFailure) {
            view.showSaveError("pkcs11.validation.save");
        }
    }

    public void delete() {
        if (editingProfile == null) return;
        try {
            repository.delete(editingProfile.name());
            editingProfile = null;
            lastDiagnostic = null;
            view.setProfiles(repository.list());
            view.clearEditor();
        } catch (RuntimeException persistenceFailure) {
            view.showSaveError("pkcs11.validation.delete");
        }
    }

    public void diagnose(Button triggerButton) {
        Pkcs11Profile profile = readProfile();
        if (profile == null) return;
        lastDiagnostic = null;
        view.setBusy(true);
        operationRunner.execute("PKCS#11 library diagnosis", triggerButton,
                () -> diagnosticService.diagnose(Path.of(profile.library())),
                result -> {
                    lastDiagnostic = result;
                    view.setBusy(false);
                    view.showDiagnostic(result);
                },
                failure -> {
                    lastDiagnostic = null;
                    view.setBusy(false);
                    view.showOperationError("pkcs11.operation.queryError");
                },
                () -> {
                    lastDiagnostic = null;
                    view.setBusy(false);
                    view.showCancelled();
                });
    }

    public void inventory(Button triggerButton) {
        view.setBusy(true);
        Pkcs11DiagnosticResult diagnostic = lastDiagnostic;
        operationRunner.execute("PKCS#11 public inventory", triggerButton,
                () -> inventoryService.inventory(diagnostic),
                result -> {
                    view.setBusy(false);
                    view.showInventory(result);
                },
                failure -> {
                    view.setBusy(false);
                    view.showOperationError("pkcs11.operation.queryError");
                },
                () -> {
                    view.setBusy(false);
                    view.showCancelled();
                });
    }

    private Pkcs11Profile readProfile() {
        String slotText = view.slotListIndex() == null ? "" : view.slotListIndex().trim();
        final int slot;
        try {
            slot = Integer.parseInt(slotText);
        } catch (NumberFormatException invalidSlot) {
            view.showValidation("pkcs11SlotListIndexField", "pkcs11.validation.slot");
            return null;
        }
        try {
            return new Pkcs11Profile(view.profileName(), view.libraryPath(), slot);
        } catch (IllegalArgumentException invalidProfile) {
            String message = invalidProfile.getMessage() == null ? "" : invalidProfile.getMessage().toLowerCase(java.util.Locale.ROOT);
            String field = message.contains("name") ? "pkcs11ProfileNameField"
                    : message.contains("library") || message.contains("path") ? "pkcs11LibraryPathField"
                    : "pkcs11SlotListIndexField";
            String key = field.equals("pkcs11ProfileNameField") ? "pkcs11.validation.name"
                    : field.equals("pkcs11LibraryPathField") ? "pkcs11.validation.library"
                    : "pkcs11.validation.slot";
            view.showValidation(field, key);
            return null;
        }
    }
}
