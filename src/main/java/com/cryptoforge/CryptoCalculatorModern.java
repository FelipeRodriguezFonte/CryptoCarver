package com.cryptoforge;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Modern launcher for the new Rail + SidePanel UI
 * This is a prototype to test the new navigation structure
 */
public class CryptoCalculatorModern extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load modern FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
            Parent root = loader.load();

            // Create scene
            Scene scene = new Scene(root, 1400, 900);

            // Load CSS
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            // Setup stage
            primaryStage.setTitle("CryptoCarver");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);

            // Configurar iconos (Window & Dock)
            try {
                // 1. Cargar imagen como recurso JavaFX para la ventana
                String iconPath = "/icons/app-icon.png";
                java.net.URL iconURL = getClass().getResource(iconPath);

                if (iconURL != null) {
                    // Set Window Icon (JavaFX)
                    javafx.scene.image.Image fxIcon = new javafx.scene.image.Image(iconURL.toExternalForm());
                    if (!fxIcon.isError()) {
                        primaryStage.getIcons().add(fxIcon);
                    }

                    // Set Dock Icon (macOS - AWT)
                    String osName = System.getProperty("os.name").toLowerCase();
                    if (osName.contains("mac")) {
                        try {
                            // Utilizar AWT Taskbar API (Java 9+)
                            java.awt.image.BufferedImage awtIcon = javax.imageio.ImageIO.read(iconURL);
                            java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                                taskbar.setIconImage(awtIcon);
                                System.out.println("✓ macOS Dock icon set");
                            }
                        } catch (UnsupportedOperationException e) {
                            System.err.println("Note: Taskbar API not supported on this platform");
                        } catch (Exception e) {
                            System.err.println("Note: Could not set macOS Dock icon (Taskbar API): " + e.getMessage());
                        }
                    }
                } else {
                    System.err.println("⚠️ Icon not found: " + iconPath);
                }
            } catch (Exception e) {
                System.err.println("Error loading application icon: " + e.getMessage());
            }

            primaryStage.show();

            System.out.println("✅ Modern UI launched successfully!");

        } catch (Exception e) {
            System.err.println("❌ Error launching modern UI:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("================================================");
        System.out.println("  CryptoCarver - Modern UI");
        System.out.println("================================================");
        System.out.println();

        launch(args);
    }
}
