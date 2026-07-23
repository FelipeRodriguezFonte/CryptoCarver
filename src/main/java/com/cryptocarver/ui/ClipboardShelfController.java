package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibility;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class ClipboardShelfController {

    @FXML private TextField searchField;
    @FXML private ComboBox<ClipboardEntry.Format> formatFilterCombo;
    @FXML private ComboBox<OperationDetail.Classification> classFilterCombo;
    @FXML private Label itemCountLabel;

    @FXML private TableView<ClipboardEntry> shelfTable;
    @FXML private TableColumn<ClipboardEntry, String> dateCol;
    @FXML private TableColumn<ClipboardEntry, String> labelCol;
    @FXML private TableColumn<ClipboardEntry, String> sourceCol;
    @FXML private TableColumn<ClipboardEntry, String> formatCol;
    @FXML private TableColumn<ClipboardEntry, String> classCol;
    @FXML private TableColumn<ClipboardEntry, String> previewCol;

    @FXML private Label warningLabel;
    @FXML private MenuButton useInMenu;
    @FXML private Button copyBtn;
    @FXML private Button expandBtn;
    @FXML private Button renameBtn;
    @FXML private Button deleteBtn;
    @FXML private TextArea detailsArea;

    private ClipboardShelfManager manager;
    private final ObservableList<ClipboardEntry> tableData = FXCollections.observableArrayList();
    private OperationNavigator navigator;
    private ModernMainController mainController;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        manager = ClipboardShelfManager.getInstance();

        formatFilterCombo.getItems().add(null); // Any
        formatFilterCombo.getItems().addAll(ClipboardEntry.Format.values());

        classFilterCombo.getItems().add(null);
        classFilterCombo.getItems().addAll(OperationDetail.Classification.values());

        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt().format(TIME_FORMATTER)));
        labelCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLabel()));
        sourceCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSourceOperation()));
        formatCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormat().name()));
        classCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getClassification().name()));
        previewCol.setCellValueFactory(cellData -> new SimpleStringProperty(getMaskedValue(cellData.getValue(), true)));

        shelfTable.setItems(tableData);
        shelfTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> showDetails(newVal));

        searchField.textProperty().addListener((obs, oldVal, newVal) -> refresh());
        formatFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> refresh());
        classFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> refresh());

        populateUseInMenu();
        refresh();
    }

    public void setNavigator(OperationNavigator navigator, ModernMainController mainController) {
        this.navigator = navigator;
        this.mainController = mainController;
    }

    public void refresh() {
        String query = searchField.getText();
        ClipboardEntry.Format fmt = formatFilterCombo.getValue();
        OperationDetail.Classification cls = classFilterCombo.getValue();

        List<ClipboardEntry> filtered = manager.search(query, fmt, cls);
        tableData.setAll(filtered);
        itemCountLabel.setText(filtered.size() + " / 100 items");

        if (shelfTable.getSelectionModel().getSelectedItem() == null) {
            clearDetails();
        } else {
            showDetails(shelfTable.getSelectionModel().getSelectedItem()); // refresh masking
        }
    }

    private void clearDetails() {
        detailsArea.clear();
        warningLabel.setVisible(false);
        useInMenu.setDisable(true);
        copyBtn.setDisable(true);
        expandBtn.setDisable(true);
        renameBtn.setDisable(true);
        deleteBtn.setDisable(true);
    }

    private void showDetails(ClipboardEntry entry) {
        if (entry == null) {
            clearDetails();
            return;
        }

        boolean isSensitive = entry.getClassification() == OperationDetail.Classification.SECRET ||
                              entry.getClassification() == OperationDetail.Classification.SENSITIVE;

        String displayValue = getMaskedValue(entry, false);
        detailsArea.setText(displayValue);

        SecretVisibility visibility = AppSettings.getInstance().getSecretVisibility();
        boolean laboratoryGeneratedKey = isLaboratoryGeneratedKey(entry);
        boolean isRedacted = isSensitive && !laboratoryGeneratedKey && visibility == SecretVisibility.REDACTED;
        boolean isMasked = isSensitive && !laboratoryGeneratedKey && visibility == SecretVisibility.MASKED;

        boolean canCopy = !isRedacted && !isMasked;

        warningLabel.setVisible(isSensitive);
        if (isSensitive) {
            warningLabel.setText(visibility == SecretVisibility.FULL_LAB
                ? "⚠️ Sensitive data displayed (Unsafe Lab mode)"
                : "⚠️ Sensitive data (Masked/Redacted)");
        }

        useInMenu.setDisable(!canCopy); // If redacted or masked, can't use it
        copyBtn.setDisable(!canCopy);
        expandBtn.setDisable(!canCopy);
        renameBtn.setDisable(false);
        deleteBtn.setDisable(false);
    }

    private String getMaskedValue(ClipboardEntry entry, boolean truncate) {
        if (entry == null || entry.getValue() == null) return "";
        String val = entry.getValue();

        boolean isSensitive = entry.getClassification() == OperationDetail.Classification.SECRET ||
                              entry.getClassification() == OperationDetail.Classification.SENSITIVE;

        if (isSensitive && !isLaboratoryGeneratedKey(entry)) {
            SecretVisibility visibility = AppSettings.getInstance().getSecretVisibility();
            if (visibility == SecretVisibility.REDACTED) {
                return "[REDACTED]";
            } else if (visibility == SecretVisibility.MASKED) {
                if (val.length() <= 8) return "********";
                return val.substring(0, 4) + "...[MASKED]..." + val.substring(val.length() - 4);
            }
        }

        if (truncate && val.length() > 50) {
            return val.substring(0, 47) + "...";
        }
        return val;
    }

    private boolean isLaboratoryGeneratedKey(ClipboardEntry entry) {
        String source = entry == null ? null : entry.getSourceOperation();
        return source != null && source.startsWith("Generate ") && source.contains(" Key");
    }

    @FXML
    private void handleClearShelf() {
        manager.clear();
        refresh();
    }

    /** Opens a detached multi-selection workspace for manual copy/paste flows. */
    @FXML
    private void handleOpenClipboardWindow() {
        javafx.stage.Window owner = shelfTable.getScene() == null ? null : shelfTable.getScene().getWindow();
        new ClipboardShelfWindow(manager).show(owner);
    }

    @FXML
    private void handleCopy() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null) {
            String val = getMaskedValue(entry, false);
            if (!val.equals("[REDACTED]")) {
                ClipboardContent content = new ClipboardContent();
                content.putString(val);
                Clipboard.getSystemClipboard().setContent(content);
            }
        }
    }

    @FXML
    private void handleOpenExpanded() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null && mainController != null) {
            String val = getMaskedValue(entry, false);
            if (!val.equals("[REDACTED]")) {
                ExpandedTextViewer viewer = new ExpandedTextViewer();
                viewer.show(null, entry.getLabel(), val);
            }
        }
    }

    @FXML
    private void handleRename() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null) {
            TextInputDialog dialog = new TextInputDialog(entry.getLabel());
            dialog.setTitle("Rename Entry");
            dialog.setHeaderText("Enter new label:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(newLabel -> {
                manager.renameEntry(entry.getId(), newLabel);
                refresh();
            });
        }
    }

    @FXML
    private void handleDelete() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null) {
            manager.removeEntry(entry.getId());
            refresh();
        }
    }

    private void populateUseInMenu() {
        useInMenu.getItems().clear();

        MenuItem manualConv = new MenuItem("Manual Conversion Input");
        manualConv.setOnAction(e -> useInTarget("op_gen_manual", "MANUAL_CONVERSION"));

        MenuItem symCipher = new MenuItem("Symmetric Cipher Input");
        symCipher.setOnAction(e -> useInTarget("op_sym_ciphers", "SYMMETRIC_CIPHER"));

        MenuItem hashInput = new MenuItem("Hashing Input");
        hashInput.setOnAction(e -> useInTarget("op_gen_hash", "HASHING"));

        MenuItem josePayload = new MenuItem("JOSE Payload (JWT)");
        josePayload.setOnAction(e -> useInTarget("op_jose_jwt", "JOSE_JWT"));

        useInMenu.getItems().addAll(manualConv, symCipher, hashInput, josePayload);
    }

    private void useInTarget(String operationId, String targetType) {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry == null || navigator == null || mainController == null) return;

        ClipboardEntry.Format fmt = entry.getFormat();
        boolean valid = true;
        if (targetType.equals("MANUAL_CONVERSION") || targetType.equals("SYMMETRIC_CIPHER") || targetType.equals("HASHING")) {
            if (fmt != ClipboardEntry.Format.TEXT && fmt != ClipboardEntry.Format.HEX &&
                fmt != ClipboardEntry.Format.BASE64 && fmt != ClipboardEntry.Format.BASE64URL) {
                valid = false;
            }
        } else if (targetType.equals("JOSE_JWT")) {
            if (fmt != ClipboardEntry.Format.TEXT && fmt != ClipboardEntry.Format.JSON) {
                valid = false;
            }
        }

        if (!valid) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Format " + fmt.name() + " is not supported for " + targetType + ".");
            alert.showAndWait();
            return;
        }

        String val = getMaskedValue(entry, false);
        if (val.equals("[REDACTED]") || val.contains("[MASKED]")) return;

        navigator.navigateTo(operationId);

        // Use the main controller to inject the value
        mainController.fillClipboardTarget(targetType, val, entry.getFormat());
    }
}
