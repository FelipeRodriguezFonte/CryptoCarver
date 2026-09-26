package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class EmvMastercardDataStorageControllerTest {
    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException alreadyRunning) { }
    }

    @Test void exampleCalculatesPartialKeyAndDigest() throws Exception {
        EMVController controller = wire();
        List<OperationResult> published = new ArrayList<>();
        controller.init(new Reporter(published));
        controller.handleDsLoadExample();
        assertEquals("5168624300900697", field(controller, "dsIdField").getText());
        assertEquals("8199829983998499", field(controller, "dsOperatorIdField").getText());
        assertEquals("1223344556677889", field(controller, "dsInputField").getText());

        controller.handleDsDspk();
        assertTrue(result(controller).contains("66887C5600B47C5600B40CC2"), result(controller));
        assertEquals(OperationDetail.Classification.SECRET, published.get(0).getOutputClassification());
        controller.handleDsDigest();
        assertTrue(result(controller).contains("659C8EBFAA816DB5"), result(controller));
        assertEquals("659C8EBFAA816DB5", new String(published.get(1).getOutput()));
        controller.handleDsSummary();
        assertTrue(result(controller).contains("FC96571A6E95FFA4"), result(controller));
    }

    @Test void invalidOperatorIdIsTranslatedAndNotPublished() throws Exception {
        EMVController controller = wire();
        List<OperationResult> published = new ArrayList<>();
        controller.init(new Reporter(published));
        controller.handleDsLoadExample();
        field(controller, "dsOperatorIdField").setText("123");
        controller.handleDsDigest();
        assertTrue(result(controller).contains(I18nService.getInstance().text("module.emv.hce.hexLength",
                I18nService.getInstance().text("module.emv.ds.operatorId"), 8)), result(controller));
        assertTrue(published.isEmpty());
    }

    @Test void fxmlIdsAndActionsExist() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/emv.fxml"));
        Matcher ids = Pattern.compile("fx:id=\\\"(ds[A-Za-z0-9]+)\\\"").matcher(fxml);
        int count = 0;
        while (ids.find()) { EMVController.class.getDeclaredField(ids.group(1)); count++; }
        assertEquals(11, count);
        Matcher actions = Pattern.compile("onAction=\\\"#(handleDs[A-Za-z]+)\\\"").matcher(fxml);
        count = 0;
        while (actions.find()) { EMVController.class.getMethod(actions.group(1)); count++; }
        assertEquals(4, count);
    }

    private static EMVController wire() throws Exception {
        EMVController c = new EMVController();
        for (String name : List.of("dsIdField", "dsOperatorIdField", "dsInputField", "dsSummary1Field", "dsAmountField",
                "dsCurrencyField", "dsRcpField", "dsGacField", "dsDsUnField", "dsUnField")) set(c, name, new TextField());
        set(c, "dsResultArea", new TextArea());
        return c;
    }
    private static TextField field(EMVController c, String name) throws Exception {
        Field f = EMVController.class.getDeclaredField(name); f.setAccessible(true); return (TextField) f.get(c);
    }
    private static String result(EMVController c) throws Exception {
        Field f = EMVController.class.getDeclaredField("dsResultArea"); f.setAccessible(true);
        return ((TextArea) f.get(c)).getText();
    }
    private static void set(EMVController c, String name, Object value) throws Exception {
        Field f = EMVController.class.getDeclaredField(name); f.setAccessible(true); f.set(c, value);
    }
    private record Reporter(List<OperationResult> results) implements StatusReporter {
        @Override public void publish(OperationResult result) { results.add(result); }
        @Override public void updateStatus(String message) { }
        @Override public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) { }
        @Override public void showError(String title, String message) { }
    }
}
