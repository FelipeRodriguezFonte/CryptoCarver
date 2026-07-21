package com.cryptocarver;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.security.Security;

/**
 * CryptoCarver - Advanced Cryptographic Test Tool
 * Multi-platform cryptographic calculator with GUI
 */
public class CryptoCalculatorApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Registrar BouncyCastle como proveedor de seguridad
        Security.addProvider(new BouncyCastleProvider());

        FXMLLoader fxmlLoader = new FXMLLoader(
                CryptoCalculatorApp.class.getResource("/fxml/main-view.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

        // Aplicar stylesheet
        scene.getStylesheets().add(
                CryptoCalculatorApp.class.getResource("/css/styles.css").toExternalForm());

        stage.setTitle("CryptoCarver - Advanced Cryptographic Test Tool");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);

        // Configurar iconos (Window & Dock)
        try {
            // 1. Cargar imagen como recurso JavaFX para la ventana
            String iconPath = "/icons/app-icon.png";
            java.net.URL iconURL = CryptoCalculatorApp.class.getResource(iconPath);

            if (iconURL != null) {
                // Set Window Icon (JavaFX)
                Image fxIcon = new Image(iconURL.toExternalForm());
                if (!fxIcon.isError()) {
                    stage.getIcons().add(fxIcon);
                }

                // Set Dock Icon (macOS - AWT)
                // Esto es necesario para que el icono aparezca en el Dock en macOS
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

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
