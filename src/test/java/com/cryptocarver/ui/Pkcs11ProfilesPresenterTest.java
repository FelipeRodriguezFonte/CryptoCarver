package com.cryptocarver.ui;

import com.cryptocarver.crypto.hsm.Pkcs11DiagnosticResult;
import com.cryptocarver.crypto.hsm.Pkcs11InventoryResult;
import com.cryptocarver.crypto.hsm.Pkcs11LibraryDiagnosticService;
import com.cryptocarver.crypto.hsm.Pkcs11LibraryInventoryService;
import com.cryptocarver.crypto.hsm.Pkcs11NativeBridge;
import com.cryptocarver.model.Pkcs11Profile;
import com.cryptocarver.model.Pkcs11ProfileRepository;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pkcs11ProfilesPresenterTest {
    @TempDir Path temporaryDirectory;

    @Test
    void loadsCrudAndRehydratesOnlyPublicProfileFields() throws IOException {
        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(
                temporaryDirectory.resolve("profiles.json"));
        FixtureView view = new FixtureView("Lab", temporaryDirectory.resolve("module.so").toString(), "2");
        PendingRunner runner = new PendingRunner();
        Pkcs11ProfilesPresenter presenter = presenter(repository, runner, view, new EmptyBridge());

        presenter.load();
        assertTrue(view.profiles.isEmpty());
        presenter.save();
        assertEquals(List.of(new Pkcs11Profile("Lab", temporaryDirectory.resolve("module.so").toString(), 2)),
                repository.list());

        presenter.select(repository.list().get(0));
        assertEquals("Lab", view.name);
        assertEquals(temporaryDirectory.resolve("module.so").toString(), view.library);
        assertEquals("2", view.slot);
        assertFalse(view.rehydratedText.contains("PIN"));
        assertFalse(view.rehydratedText.contains("password"));

        view.slot = "-1";
        presenter.save();
        assertEquals("pkcs11.validation.slot", view.validationKey);
    }

    @Test
    void validatesBeforeSavingAndRejectsDuplicateNames() throws IOException {
        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(
                temporaryDirectory.resolve("profiles.json"));
        FixtureView view = new FixtureView("First", temporaryDirectory.resolve("one.so").toString(), "0");
        Pkcs11ProfilesPresenter presenter = presenter(repository, new PendingRunner(), view, new EmptyBridge());
        presenter.save();

        presenter.newProfile();
        view.name = " first ";
        view.library = temporaryDirectory.resolve("two.so").toString();
        presenter.save();
        assertEquals("pkcs11.validation.duplicate", view.saveErrorKey);

        view.name = "";
        presenter.save();
        assertEquals("pkcs11.validation.name", view.validationKey);
    }

    @Test
    void diagnosisUsesAsyncRunnerAndRestoresControlsOnSuccessFailureAndCancellation() throws IOException {
        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(
                temporaryDirectory.resolve("profiles.json"));
        FixtureView view = new FixtureView("Lab", temporaryDirectory.resolve("module.so").toString(), "0");
        PendingRunner runner = new PendingRunner();
        Pkcs11ProfilesPresenter presenter = presenter(repository, runner, view, new EmptyBridge());

        presenter.diagnose(null);
        assertTrue(view.busy);
        assertEquals("PKCS#11 library diagnosis", runner.operationName);
        runner.complete(new Pkcs11DiagnosticResult(Pkcs11DiagnosticResult.Status.OK,
                Path.of(view.library), "safe", "test", "test", "64-bit",
                Set.of(), true, true, true, false));
        assertFalse(view.busy);
        assertNotNull(view.diagnostic);

        presenter.diagnose(null);
        runner.fail(new IllegalStateException("provider secret path and PIN must never surface"));
        assertFalse(view.busy);
        assertEquals("pkcs11.operation.queryError", view.operationErrorKey);

        presenter.diagnose(null);
        runner.cancel();
        assertFalse(view.busy);
        assertEquals("pkcs11.operation.cancelled", view.operationErrorKey);
    }

    @Test
    void inventoryDelegatesToPublicInventoryServiceAndNeverExposesSecrets() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        Path library = Files.createTempFile(temporaryDirectory, "module-", ".so");
        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(
                temporaryDirectory.resolve("profiles.json"));
        FixtureView view = new FixtureView("Lab", library.toString(), "0");
        PendingRunner runner = new PendingRunner();
        Pkcs11ProfilesPresenter presenter = presenter(repository, runner, view, bridge);

        presenter.diagnose(null);
        runner.complete(new Pkcs11DiagnosticResult(Pkcs11DiagnosticResult.Status.OK,
                Path.of(view.library), "diagnostic path must not be rendered", "test", "test", "64-bit",
                Set.of(), true, true, true, false));
        presenter.inventory(null);
        assertEquals("PKCS#11 public inventory", runner.operationName);
        Pkcs11InventoryResult inventory = runner.callTask();
        runner.complete(inventory);

        assertTrue(bridge.initialized);
        assertEquals(Pkcs11InventoryResult.Status.OK, view.inventory.status());
        String rendered = Pkcs11ProfilesController.formatInventory(view.inventory);
        assertTrue(rendered.contains("Slot ID: 7"));
        assertTrue(rendered.contains("TOKEN_INITIALIZED"));
        assertFalse(rendered.contains("diagnostic path"));
        assertFalse(rendered.contains("PIN"));
        assertFalse(rendered.contains("password"));
        assertFalse(rendered.contains("alias"));
        assertFalse(view.operationHistoryOrInspectorText.contains("diagnostic path"));
    }

    private Pkcs11ProfilesPresenter presenter(Pkcs11ProfileRepository repository,
                                              PendingRunner runner,
                                              FixtureView view,
                                              Pkcs11NativeBridge bridge) {
        return new Pkcs11ProfilesPresenter(repository, new Pkcs11LibraryDiagnosticService(),
                new Pkcs11LibraryInventoryService(bridge), runner, view);
    }

    private static final class FixtureView implements Pkcs11ProfilesPresenter.View {
        private String name;
        private String library;
        private String slot;
        private List<Pkcs11Profile> profiles = List.of();
        private boolean busy;
        private String validationKey;
        private String saveErrorKey;
        private String operationErrorKey;
        private String rehydratedText = "";
        private Pkcs11DiagnosticResult diagnostic;
        private Pkcs11InventoryResult inventory;
        private String operationHistoryOrInspectorText = "";

        private FixtureView(String name, String library, String slot) {
            this.name = name;
            this.library = library;
            this.slot = slot;
        }

        @Override public String profileName() { return name; }
        @Override public String libraryPath() { return library; }
        @Override public String slotListIndex() { return slot; }
        @Override public void setProfiles(List<Pkcs11Profile> profiles) { this.profiles = profiles; }
        @Override public void rehydrate(Pkcs11Profile profile) {
            name = profile.name(); library = profile.library(); slot = String.valueOf(profile.slotListIndex());
            rehydratedText = name + library + slot;
        }
        @Override public void clearEditor() { name = ""; library = ""; slot = "0"; }
        @Override public void setBusy(boolean busy) { this.busy = busy; }
        @Override public void showValidation(String fieldKey, String messageKey) { validationKey = messageKey; }
        @Override public void showSaveError(String messageKey) { saveErrorKey = messageKey; }
        @Override public void showDiagnostic(Pkcs11DiagnosticResult result) { diagnostic = result; }
        @Override public void showInventory(Pkcs11InventoryResult result) { inventory = result; }
        @Override public void showOperationError(String messageKey) { operationErrorKey = messageKey; }
        @Override public void showCancelled() { operationErrorKey = "pkcs11.operation.cancelled"; }
    }

    private static final class PendingRunner implements Pkcs11ProfilesPresenter.OperationRunner {
        private String operationName;
        private Callable<?> task;
        private Consumer<Object> success;
        private Consumer<Throwable> failure;
        private Runnable cancelled;

        @SuppressWarnings("unchecked")
        @Override
        public <T> void execute(String operationName, Button triggerButton, Callable<T> task,
                                Consumer<T> onSuccess, Consumer<Throwable> onFailure, Runnable onCancelled) {
            this.operationName = operationName;
            this.task = task;
            this.success = (Consumer<Object>) (Consumer<?>) onSuccess;
            this.failure = onFailure;
            this.cancelled = onCancelled;
        }

        private void complete(Object result) { success.accept(result); }
        private void fail(Throwable error) { failure.accept(error); }
        private void cancel() { cancelled.run(); }
        @SuppressWarnings("unchecked")
        private <T> T callTask() throws Exception { return (T) task.call(); }
    }

    private static class EmptyBridge implements Pkcs11NativeBridge {
        @Override public NativeSession initialize(Path library) { return new NativeSession() {
            @Override public List<NativeSlot> slots() { return List.of(); }
            @Override public NativeToken token(long slotId) { return null; }
            @Override public List<Long> mechanisms(long slotId) { return List.of(); }
            @Override public NativeMechanism mechanism(long slotId, long mechanismId) { return null; }
            @Override public void close() { }
        }; }
    }

    private static final class RecordingBridge extends EmptyBridge {
        private boolean initialized;

        @Override public NativeSession initialize(Path library) {
            initialized = true;
            return new NativeSession() {
                @Override public List<NativeSlot> slots() {
                    return List.of(new NativeSlot(0, 7, true));
                }
                @Override public NativeToken token(long slotId) {
                    return new NativeToken("Public token", "Public vendor", 0x00000400L);
                }
                @Override public List<Long> mechanisms(long slotId) { return List.of(1L); }
                @Override public NativeMechanism mechanism(long slotId, long mechanismId) {
                    return new NativeMechanism(16, 32, 0x00000100L);
                }
                @Override public void close() { }
            };
        }
    }
}
