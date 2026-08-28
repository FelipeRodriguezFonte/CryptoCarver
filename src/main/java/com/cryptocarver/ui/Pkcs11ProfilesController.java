package com.cryptocarver.ui;

import com.cryptocarver.crypto.hsm.Pkcs11DiagnosticResult;
import com.cryptocarver.crypto.hsm.Pkcs11InventoryResult;
import com.cryptocarver.crypto.hsm.Pkcs11LibraryDiagnosticService;
import com.cryptocarver.crypto.hsm.Pkcs11LibraryInventoryService;
import com.cryptocarver.crypto.hsm.Pkcs11MechanismInventory;
import com.cryptocarver.crypto.hsm.Pkcs11SlotInventory;
import com.cryptocarver.crypto.hsm.Pkcs11TokenInventory;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.Pkcs11Profile;
import com.cryptocarver.service.I18nService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.util.List;
import java.util.Set;

/** JavaFX adapter for the safe PKCS#11 profiles and public inventory presenter. */
public final class Pkcs11ProfilesController implements Pkcs11ProfilesPresenter.View {
    @FXML private TitledPane pkcs11ProfilesPane;
    @FXML private VBox pkcs11ProfilesRoot;
    @FXML private TableView<Pkcs11Profile> pkcs11ProfilesTable;
    @FXML private TableColumn<Pkcs11Profile, String> pkcs11ProfileNameColumn;
    @FXML private TableColumn<Pkcs11Profile, String> pkcs11LibraryColumn;
    @FXML private TableColumn<Pkcs11Profile, Integer> pkcs11SlotColumn;
    @FXML private TextField pkcs11ProfileNameField;
    @FXML private TextField pkcs11LibraryPathField;
    @FXML private TextField pkcs11SlotListIndexField;
    @FXML private Button pkcs11SaveProfileButton;
    @FXML private Button pkcs11NewProfileButton;
    @FXML private Button pkcs11DeleteProfileButton;
    @FXML private Button pkcs11DiagnoseButton;
    @FXML private Button pkcs11InventoryButton;
    @FXML private Button pkcs11CancelButton;
    @FXML private Label pkcs11FeedbackLabel;
    @FXML private TextArea pkcs11InventoryArea;

    private final I18nService i18n = I18nService.getInstance();
    private ModuleI18n.Binding moduleI18n;
    private Pkcs11ProfilesPresenter presenter;
    private OperationExecutor operationExecutor;
    private StatusReporter statusReporter;

    @FXML
    private void initialize() {
        // Bind the pane, not the inner box: binding the box left this pane's own title
        // in English after a language change, and the title is what the user reads while
        // the pane is collapsed.
        moduleI18n = ModuleI18n.bind(pkcs11ProfilesPane, ModuleTextCatalog.pkcs11Profiles());
        pkcs11ProfileNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().name()));
        pkcs11LibraryColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().library()));
        pkcs11SlotColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().slotListIndex()));
        pkcs11ProfilesTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldProfile, newProfile) -> { if (newProfile != null) presenter.select(newProfile); });
        pkcs11ProfileNameField.textProperty().addListener((obs, oldValue, newValue) -> presenter.profileInputChanged());
        pkcs11LibraryPathField.textProperty().addListener((obs, oldValue, newValue) -> presenter.profileInputChanged());
        pkcs11SlotListIndexField.textProperty().addListener((obs, oldValue, newValue) -> presenter.profileInputChanged());
        operationExecutor = new OperationExecutor();
        rebuildPresenter();
        presenter.load();
        presenter.newProfile();
        pkcs11CancelButton.setDisable(true);
    }

    public void setOperationExecutor(OperationExecutor executor) {
        if (executor == null) return;
        operationExecutor = executor;
        rebuildPresenter();
        presenter.load();
    }

    public void setStatusReporter(StatusReporter reporter) {
        statusReporter = reporter;
    }

    public void expand() {
        if (pkcs11ProfilesPane != null) pkcs11ProfilesPane.setExpanded(true);
    }

    @FXML
    private void handleNewProfile() { presenter.newProfile(); }

    @FXML
    private void handleSaveProfile() { presenter.save(); }

    @FXML
    private void handleDeleteProfile() { presenter.delete(); }

    @FXML
    private void handleDiagnose() { presenter.diagnose(pkcs11DiagnoseButton); }

    @FXML
    private void handleInventory() { presenter.inventory(pkcs11InventoryButton); }

    @FXML
    private void handleCancel() {
        if (operationExecutor != null) operationExecutor.cancelCurrentOperation();
    }

    private void rebuildPresenter() {
        presenter = new Pkcs11ProfilesPresenter(
                AppSettings.getInstance().getPkcs11ProfileRepository(),
                new Pkcs11LibraryDiagnosticService(),
                new Pkcs11LibraryInventoryService(),
                Pkcs11ProfilesPresenter.from(operationExecutor), this);
    }

    @Override public String profileName() { return pkcs11ProfileNameField.getText(); }
    @Override public String libraryPath() { return pkcs11LibraryPathField.getText(); }
    @Override public String slotListIndex() { return pkcs11SlotListIndexField.getText(); }

    @Override
    public void setProfiles(List<Pkcs11Profile> profiles) {
        pkcs11ProfilesTable.getItems().setAll(profiles == null ? List.of() : profiles);
    }

    @Override
    public void rehydrate(Pkcs11Profile profile) {
        pkcs11ProfileNameField.setText(profile.name());
        pkcs11LibraryPathField.setText(profile.library());
        pkcs11SlotListIndexField.setText(String.valueOf(profile.slotListIndex()));
    }

    @Override
    public void clearEditor() {
        pkcs11ProfilesTable.getSelectionModel().clearSelection();
        pkcs11ProfileNameField.clear();
        pkcs11LibraryPathField.clear();
        pkcs11SlotListIndexField.setText("0");
    }

    @Override
    public void setBusy(boolean busy) {
        pkcs11ProfileNameField.setDisable(busy);
        pkcs11LibraryPathField.setDisable(busy);
        pkcs11SlotListIndexField.setDisable(busy);
        pkcs11ProfilesTable.setDisable(busy);
        pkcs11SaveProfileButton.setDisable(busy);
        pkcs11NewProfileButton.setDisable(busy);
        pkcs11DeleteProfileButton.setDisable(busy);
        pkcs11DiagnoseButton.setDisable(busy);
        pkcs11InventoryButton.setDisable(busy);
        pkcs11CancelButton.setDisable(!busy);
    }

    @Override
    public void showValidation(String fieldKey, String messageKey) {
        showFeedback(i18n.text(messageKey), true);
        showShellError(i18n.text("pkcs11.error.title"), i18n.text(messageKey), i18n.text("pkcs11.error.remedy"), fieldKey);
    }

    @Override
    public void showSaveError(String messageKey) {
        showFeedback(i18n.text(messageKey), true);
        showShellError(i18n.text("pkcs11.error.title"), i18n.text(messageKey), i18n.text("pkcs11.error.remedy"), "pkcs11ProfileNameField");
    }

    @Override
    public void showDiagnostic(Pkcs11DiagnosticResult result) {
        String status = diagnosticStatus(result == null ? null : result.status());
        showFeedback(i18n.text(status), result == null || !result.isSuccessful());
        if (statusReporter != null) statusReporter.updateStatus(i18n.text(status));
    }

    @Override
    public void showInventory(Pkcs11InventoryResult result) {
        if (result == null) {
            showOperationError("pkcs11.operation.queryError");
            return;
        }
        pkcs11InventoryArea.setText(formatInventory(result));
        String status = "pkcs11.inventory.status." + result.status().name();
        showFeedback(i18n.text(status), result.status() == Pkcs11InventoryResult.Status.QUERY_ERROR
                || result.status() == Pkcs11InventoryResult.Status.LIBRARY_NOT_LOADABLE);
        if (statusReporter != null) statusReporter.updateStatus(i18n.text(status));
    }

    @Override
    public void showOperationError(String messageKey) {
        showFeedback(i18n.text(messageKey), true);
        showShellError(i18n.text("pkcs11.error.title"), i18n.text(messageKey), i18n.text("pkcs11.error.remedy"), null);
    }

    @Override
    public void showCancelled() {
        showFeedback(i18n.text("pkcs11.operation.cancelled"), false);
        if (statusReporter != null) statusReporter.updateStatus(i18n.text("pkcs11.operation.cancelled"));
    }

    private void showFeedback(String text, boolean error) {
        pkcs11FeedbackLabel.setText(InlineErrorPresenter.safeAccessibleText(text));
        pkcs11FeedbackLabel.getStyleClass().removeAll("pkcs11-feedback-error", "pkcs11-feedback-ok");
        pkcs11FeedbackLabel.getStyleClass().add(error ? "pkcs11-feedback-error" : "pkcs11-feedback-ok");
    }

    private void showShellError(String title, String detail, String remedy, String field) {
        if (statusReporter != null) statusReporter.showError(new UserFacingError(title, detail, remedy, field));
    }

    private String diagnosticStatus(Pkcs11DiagnosticResult.Status status) {
        if (status == null) return "pkcs11.diagnostic.status.ERROR";
        return switch (status) {
            case OK -> "pkcs11.diagnostic.status.OK";
            case INVALID_PATH -> "pkcs11.diagnostic.status.INVALID_PATH";
            case FILE_NOT_FOUND -> "pkcs11.diagnostic.status.LIBRARY_NOT_LOADABLE";
            case NOT_REGULAR_FILE -> "pkcs11.diagnostic.status.LIBRARY_NOT_LOADABLE";
            case ACCESS_DENIED -> "pkcs11.diagnostic.status.LIBRARY_NOT_LOADABLE";
            case NATIVE_LIBRARY_INCOMPATIBLE, SUNPKCS11_CONFIGURATION_REJECTED,
                    SUNPKCS11_UNAVAILABLE, TEMPORARY_CONFIGURATION_FAILED -> "pkcs11.diagnostic.status.LIBRARY_NOT_LOADABLE";
        };
    }

    static String formatInventory(Pkcs11InventoryResult result) {
        StringBuilder output = new StringBuilder();
        output.append(I18nService.getInstance().text("pkcs11.result.status")).append(": ")
                .append(I18nService.getInstance().text("pkcs11.inventory.status." + result.status().name())).append('\n');
        for (Pkcs11SlotInventory slot : result.slots()) {
            output.append('\n').append(I18nService.getInstance().text("pkcs11.result.slotIndex"))
                    .append(": ").append(slot.slotListIndex())
                    .append('\n').append(I18nService.getInstance().text("pkcs11.result.slotId"))
                    .append(": ").append(slot.slotId())
                    .append('\n').append(I18nService.getInstance().text("pkcs11.result.tokenPresent"))
                    .append(": ").append(slot.tokenPresent());
            Pkcs11TokenInventory token = slot.token();
            if (token != null) {
                output.append('\n').append(I18nService.getInstance().text("pkcs11.result.label"))
                        .append(": ").append(publicValue(token.label()))
                        .append('\n').append(I18nService.getInstance().text("pkcs11.result.manufacturer"))
                        .append(": ").append(publicValue(token.manufacturer()))
                        .append('\n').append(I18nService.getInstance().text("pkcs11.result.tokenFlags"))
                        .append(": ").append(publicFlags(token.flags()));
            }
            output.append('\n').append(I18nService.getInstance().text("pkcs11.result.mechanisms")).append(':');
            if (slot.mechanisms().isEmpty()) output.append(' ').append(I18nService.getInstance().text("pkcs11.result.none"));
            for (Pkcs11MechanismInventory mechanism : slot.mechanisms()) {
                output.append("\n- ").append(publicValue(mechanism.name()))
                        .append(" (").append(I18nService.getInstance().text("pkcs11.result.id"))
                        .append(' ').append(mechanism.mechanismId()).append(") ")
                        .append(I18nService.getInstance().text("pkcs11.result.flags")).append(' ')
                        .append(publicFlags(mechanism.flags()))
                        .append(' ').append(I18nService.getInstance().text("pkcs11.result.keySizes")).append(' ')
                        .append(mechanism.minKeySize())
                        .append("-").append(mechanism.maxKeySize());
            }
            output.append('\n');
        }
        return output.toString();
    }

    private static String publicValue(String value) {
        if (value == null || value.isBlank()) return "—";
        return value.replaceAll("[\\p{Cntrl}]", "").trim();
    }

    private static String publicFlags(Set<String> flags) {
        if (flags == null || flags.isEmpty()) return "—";
        return flags.stream().map(Pkcs11ProfilesController::publicValue).sorted().toList().toString();
    }
}
