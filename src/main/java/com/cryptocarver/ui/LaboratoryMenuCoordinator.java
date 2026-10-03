package com.cryptocarver.ui;

import com.cryptocarver.crypto.PaymentProfileVerifier;
import com.cryptocarver.model.payments.PaymentProfile;
import com.cryptocarver.model.payments.PaymentProfileManager;
import com.cryptocarver.service.I18nService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/** Builds the optional profile entries in the shell's Laboratory menu. */
public final class LaboratoryMenuCoordinator {
    private final MenuBar menuBar;
    private final Runnable showQuickStart;
    private final Consumer<String> navigate;
    private final Supplier<KeysController> keysController;
    private final Supplier<EMVController> emvController;
    private final Supplier<PaymentsController> paymentsController;

    public LaboratoryMenuCoordinator(MenuBar menuBar, Runnable showQuickStart, Consumer<String> navigate,
            Supplier<KeysController> keysController, Supplier<EMVController> emvController,
            Supplier<PaymentsController> paymentsController) {
        this.menuBar = menuBar;
        this.showQuickStart = showQuickStart;
        this.navigate = navigate;
        this.keysController = keysController;
        this.emvController = emvController;
        this.paymentsController = paymentsController;
    }

    /** Marks a Laboratory menu that already lists the profiles, so setup never adds them twice. */
    private static final String PROFILES_ADDED = "laboratory.profilesAdded";

    public void setup() {
        if (menuBar == null) {
            return;
        }
        Menu existing = menuBar.getMenus().stream()
                .filter(menu -> "laboratory".equals(menu.getUserData()))
                .findFirst().orElse(null);
        if (existing != null) {
            // The shell FXML declares the menu with Quick Start; the profiles still come from here.
            addProfiles(existing);
            return;
        }
        I18nService i18n = I18nService.getInstance();
        Menu labMenu = new Menu(i18n.text("menu.laboratory"));
        labMenu.setUserData("laboratory");
        labMenu.setStyle("-fx-text-fill: white;");
        MenuItem quickStartItem = new MenuItem(i18n.text("menu.quickStart"));
        quickStartItem.setOnAction(event -> showQuickStart.run());
        labMenu.getItems().add(quickStartItem);
        addProfiles(labMenu);
        menuBar.getMenus().add(labMenu);
    }

    private void addProfiles(Menu labMenu) {
        if (labMenu.getProperties().containsKey(PROFILES_ADDED)) {
            return;
        }
        labMenu.getProperties().put(PROFILES_ADDED, Boolean.TRUE);
        labMenu.getItems().add(new SeparatorMenuItem());
        for (PaymentProfile profile : PaymentProfileManager.getAllProfiles()) {
            labMenu.getItems().add(createProfileMenu(profile));
        }
    }

    private Menu createProfileMenu(PaymentProfile profile) {
        Menu profileMenu = new Menu(profile.getType().name() + " - " + profile.getName());
        MenuItem loadItem = new MenuItem(I18nService.getInstance().text("menu.loadData"));
        loadItem.setOnAction(event -> loadProfile(profile));
        MenuItem verifyItem = new MenuItem(I18nService.getInstance().text("menu.runVerify"));
        verifyItem.setOnAction(event -> verifyProfile(profile));
        profileMenu.getItems().addAll(loadItem, verifyItem);
        return profileMenu;
    }

    private void loadProfile(PaymentProfile profile) {
        if (profile.getType() == PaymentProfile.ProfileType.TR31) {
            navigate.accept("Symmetric Keys");
            KeysController controller = keysController.get();
            if (controller != null) {
                controller.loadProfile(profile);
            }
        } else if (profile.getType() == PaymentProfile.ProfileType.EMV) {
            navigate.accept("EMV Tool");
            EMVController controller = emvController.get();
            if (controller != null) {
                controller.loadProfile(profile);
            }
        } else {
            navigate.accept("Payments");
            PaymentsController controller = paymentsController.get();
            if (controller != null) {
                controller.loadProfile(profile);
            }
        }
        System.out.println("Loaded profile: " + profile.getName());
    }

    private void verifyProfile(PaymentProfile profile) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Laboratory Verification");
        alert.setHeaderText(profile.getName());
        com.cryptocarver.crypto.VerificationResult result = PaymentProfileVerifier.verify(profile);
        StringBuilder content = new StringBuilder();
        content.append(result.getMessage()).append("\n\n");
        content.append("--- Profile Details ---\n");
        content.append("Parameters: ").append(profile.getParameters()).append("\n");
        content.append("Inputs: ").append(profile.getInputs()).append("\n");
        content.append("Expected Outputs: ").append(profile.getOutputs()).append("\n");
        alert.setAlertType(result.isSuccess() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setContentText(content.toString());
        if (!System.getProperty("java.awt.headless", "false").equals("true") && !Boolean.getBoolean("test.mode")) {
            alert.showAndWait();
        } else {
            System.out.println("TEST MODE: Alert suppressed. Result: " + result.isSuccess() + ", Message: " + result.getMessage());
        }
    }
}
