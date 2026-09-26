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

class EmvVisaHceControllerTest {
    private static final List<String> FIELDS = List.of("hceUdkField", "hceYearField", "hceHoursField",
            "hceCounterField", "hceMsdLukField", "hceMsdAtcField", "hceDeviceTypeField",
            "hceQvsdcLukField", "hceAmountField", "hceOtherAmountField", "hceCountryField",
            "hceTvrField", "hceCurrencyField", "hceDateField", "hceTypeField", "hceUnField",
            "hceAipField", "hceQvsdcAtcField", "hceCvrField");

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException alreadyRunning) { }
    }

    @Test void exampleRunsAllThreeStepsAndPublishesSecrets() throws Exception {
        EMVController controller = wire();
        List<OperationResult> published = new ArrayList<>();
        controller.init(new Reporter(published));
        controller.handleHceLoadExample();
        assertEquals("94E3194C02105E3B153438D562D5A49D", field(controller, "hceUdkField").getText());
        assertEquals("26", field(controller, "hceYearField").getText());
        assertEquals("6431", field(controller, "hceHoursField").getText());
        assertEquals("01", field(controller, "hceCounterField").getText());

        controller.handleHceLuk();
        String luk = "D144CA8CBB4BD463C8EDD5761BF1770E";
        assertEquals(luk, field(controller, "hceMsdLukField").getText());
        assertEquals(luk, field(controller, "hceQvsdcLukField").getText());
        assertEquals(OperationDetail.Classification.SECRET, published.get(0).getOutputClassification());
        assertTrue(published.get(0).getDetails().stream()
                .anyMatch(d -> d.name().equals("UDK") && d.classification() == OperationDetail.Classification.SECRET));

        controller.handleHceMsd();
        assertTrue(result(controller).contains("634"), result(controller));
        assertEquals("634", new String(published.get(1).getOutput()));

        controller.handleHceQvsdc();
        assertTrue(result(controller).contains("42A0254F47679C5A"), result(controller));
        assertEquals("42A0254F47679C5A", new String(published.get(2).getOutput()));
    }

    @Test void malformedTerminalFieldIsTranslatedAndNotPublished() throws Exception {
        EMVController controller = wire();
        List<OperationResult> published = new ArrayList<>();
        controller.init(new Reporter(published));
        controller.handleHceLoadExample();
        controller.handleHceLuk();
        field(controller, "hceCountryField").setText("071");
        controller.handleHceQvsdc();
        assertTrue(result(controller).contains(I18nService.getInstance().text("module.emv.hce.hexLength",
                I18nService.getInstance().text("module.emv.hce.country"), 2)), result(controller));
        assertEquals(1, published.size());
    }

    @Test void fxmlIdsAndActionsExist() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/emv.fxml"));
        Matcher ids = Pattern.compile("fx:id=\\\"(hce[A-Za-z]+)\\\"").matcher(fxml);
        int count = 0;
        while (ids.find()) { EMVController.class.getDeclaredField(ids.group(1)); count++; }
        assertEquals(FIELDS.size() + 1, count);
        Matcher actions = Pattern.compile("onAction=\\\"#(handleHce[A-Za-z]+)\\\"").matcher(fxml);
        count = 0;
        while (actions.find()) { EMVController.class.getMethod(actions.group(1)); count++; }
        assertEquals(4, count);
    }

    private static EMVController wire() throws Exception {
        EMVController c = new EMVController();
        for (String name : FIELDS) set(c, name, new TextField());
        set(c, "hceResultArea", new TextArea());
        return c;
    }
    private static TextField field(EMVController c, String name) throws Exception {
        Field f = EMVController.class.getDeclaredField(name); f.setAccessible(true); return (TextField) f.get(c);
    }
    private static String result(EMVController c) throws Exception {
        Field f = EMVController.class.getDeclaredField("hceResultArea"); f.setAccessible(true);
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
