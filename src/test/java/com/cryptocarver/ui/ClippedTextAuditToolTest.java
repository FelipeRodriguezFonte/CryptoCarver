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
                    <VBox xmlns:fx="http://javafx.com/fxml/1">
                      <Button fx:id="clipped" text="A very long interface caption that cannot fit" minWidth="0" prefWidth="70" maxWidth="70"/>
                      <Button fx:id="fits" text="OK" minWidth="100"/>
                    </VBox>
                    """;
            FXMLLoader loader = new FXMLLoader();
            Parent root = loader.load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(root, 300, 120)); stage.show();
                var result = ClippedTextAuditTool.inspect("synthetic", root);
                assertEquals(1, result.findings().size());
                assertTrue(result.findings().get(0).path().contains("#clipped"));
                assertNotEquals(result.findings().get(0).full(), result.findings().get(0).visible());
            } finally { stage.close(); stage.setScene(null); }
            return null;
        });
    }
}
