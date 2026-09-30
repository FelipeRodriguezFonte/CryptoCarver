package com.cryptocarver.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ClippedTextAuditToolTest {
    @Test void detectsSkinEllipsisWithoutFlaggingFittingCaption() throws Exception {
        ClippedTextAuditTool.onFx(() -> {
            String fxml = """
                    <?import javafx.scene.layout.VBox?>
                    <?import javafx.scene.control.Button?>
                    <?import javafx.scene.control.TitledPane?>
                    <?import javafx.scene.control.Label?>
                    <?import javafx.scene.control.MenuButton?>
                    <VBox xmlns:fx="http://javafx.com/fxml/1">
                      <Button fx:id="clipped" text="A very long interface caption that cannot fit" minWidth="0" prefWidth="70" maxWidth="70"/>
                      <Button fx:id="fits" text="OK" minWidth="100"/>
                      <MenuButton fx:id="clippedMenu" text="A very long menu caption that cannot fit" minWidth="0" prefWidth="70" maxWidth="70"/>
                      <Label fx:id="lblFingerprint" text="DATA_OUTPUT" minWidth="0" prefWidth="10" maxWidth="10"/>
                      <TitledPane text="Header" expanded="true">
                        <Label text="A different content caption"/>
                      </TitledPane>
                      <TitledPane text="Hidden content" expanded="false">
                        <Button text="Hidden and narrow interface caption" minWidth="0" prefWidth="20" maxWidth="20"/>
                      </TitledPane>
                    </VBox>
                    """;
            FXMLLoader loader = new FXMLLoader();
            Parent root = loader.load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(root, 300, 300)); stage.show();
                var result = ClippedTextAuditTool.inspect("synthetic", root);
                assertEquals(2, result.findings().size());
                assertTrue(result.findings().get(0).path().contains("#clipped"));
                assertTrue(result.findings().stream().anyMatch(f -> f.path().contains("#clippedMenu")));
                assertTrue(result.exclusions().stream().anyMatch(e -> e.contains("data output label")));
                assertNotEquals(result.findings().get(0).full(), result.findings().get(0).visible());
                assertTrue(result.exclusions().stream().anyMatch(e -> e.contains("collapsed TitledPane content")));
            } finally { stage.close(); stage.setScene(null); }
            return null;
        });
    }
}
