package com.cryptocarver;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import com.cryptocarver.service.I18nService;

/**
 * Modern launcher for the new Rail + SidePanel UI
 * This is a prototype to test the new navigation structure
 */
public class CryptoCalculatorModern extends Application {

    /** Size the Rail + SidePanel + Inspector layout was designed against. */
    private static final double DESIGN_WIDTH = 1400;
    private static final double DESIGN_HEIGHT = 900;
    /** Below this the three side panes start eating the form area. */
    private static final double DESIGN_MIN_WIDTH = 1200;
    private static final double DESIGN_MIN_HEIGHT = 700;

    @Override
    public void stop() {
        // A token session owns native PKCS#11 resources. It is intentionally
        // process-local and must never survive application shutdown.
        com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().disconnect();
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load modern FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
            loader.setResources(I18nService.getInstance().getBundle());
            Parent root = loader.load();

            // A 1400x900 window does not fit every desktop: a 1366x768 laptop, or any
            // display at 125-150% scaling (JavaFX reports the scaled, not the physical,
            // work area) leaves less room than the design size. Opening at the design
            // size there centres a window larger than the screen, which puts the title
            // bar above the top edge and the navigation rail past the left edge, so
            // neither the side panes nor the window controls are reachable.
            Rectangle2D workArea = Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(root,
                    Math.min(DESIGN_WIDTH, workArea.getWidth()),
                    Math.min(DESIGN_HEIGHT, workArea.getHeight()));

            // Load CSS
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            // Setup stage
            primaryStage.setTitle("CryptoCarver");
            primaryStage.setScene(scene);
            // Clamped as well: a minimum larger than the screen makes the window
            // impossible to resize back into view.
            primaryStage.setMinWidth(Math.min(DESIGN_MIN_WIDTH, workArea.getWidth()));
            primaryStage.setMinHeight(Math.min(DESIGN_MIN_HEIGHT, workArea.getHeight()));
            if (workArea.getWidth() < DESIGN_WIDTH || workArea.getHeight() < DESIGN_HEIGHT) {
                // Every pixel counts on a display that cannot show the design size.
                primaryStage.setMaximized(true);
            }

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
            // Window decorations are added on top of the scene size, so the frame can
            // still overflow the work area after show(). Pull it back into view.
            confineToWorkArea(primaryStage, workArea);

            System.out.println("✅ Modern UI launched successfully!");

        } catch (Exception e) {
            System.err.println("❌ Error launching modern UI:");
            e.printStackTrace();
        }
    }

    /** Keeps the whole window frame, title bar included, inside the screen's work area. */
    private static void confineToWorkArea(Stage stage, Rectangle2D workArea) {
        if (stage.isMaximized()) {
            return;
        }
        if (stage.getWidth() > workArea.getWidth()) {
            stage.setWidth(workArea.getWidth());
        }
        if (stage.getHeight() > workArea.getHeight()) {
            stage.setHeight(workArea.getHeight());
        }
        stage.setX(clamp(stage.getX(), workArea.getMinX(), workArea.getMaxX() - stage.getWidth()));
        stage.setY(clamp(stage.getY(), workArea.getMinY(), workArea.getMaxY() - stage.getHeight()));
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static void main(String[] args) {
        System.out.println("================================================");
        System.out.println("  CryptoCarver - Modern UI");
        System.out.println("================================================");
        System.out.println();

        launch(args);
    }
}
