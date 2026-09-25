package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The secure messaging pane of the EMV screen, through the controller. Loading each
 * scheme's example and running the three steps must give the values of
 * EmvSecureMessagingTest; a broken fx:id or handler name fails here.
 */
class EmvSecureMessagingControllerTest {

    private static final List<String> TEXT_FIELDS = List.of(
            "smMkSmiField", "smMkSmcField", "smPanSeqField", "smUdkSmiField", "smUdkSmcField", "smAcField",
            "smCommandNumberField", "smAtcField", "smSkMacField", "smSkEncField", "smUdkAField",
            "smHeaderField", "smDataField");

    private static LanguagePreference preferenceOnEntry;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (Exception alreadyRunning) {
            // Process-wide toolkit; another class may have started it.
        }
        preferenceOnEntry = I18nService.getInstance().getPreference();
    }

    @AfterAll
    static void restorePreference() {
        if (preferenceOnEntry != null) I18nService.getInstance().setPreference(preferenceOnEntry);
    }

    @Test
    void theMastercardExampleRunsEndToEnd() throws Exception {
        EMVController controller = wire("Mastercard");
        controller.handleSmLoadExample();

        controller.handleSmDeriveSessionKeys();
        assertEquals("AEB0F198A498E067C4E63D94A770A80E", get(controller, "smUdkSmiField").getText());
        assertEquals("E46C87DD5AC1177FCCE8F7A1C56A40C6", get(controller, "smSkMacField").getText());
        assertEquals("EA4F899B89521FC70B9A6E6DC44AD2A8", get(controller, "smSkEncField").getText());

        controller.handleSmEncipherPin();
        assertEquals("2EC06BD5D6AEEBBC", get(controller, "smDataField").getText());

        controller.handleSmGenerateMac();
        assertTrue(result(controller).contains("AC4E7EB35196E310"), result(controller));
    }

    @Test
    void theVisaExampleRunsEndToEnd() throws Exception {
        EMVController controller = wire("Visa");
        controller.handleSmLoadExample();

        controller.handleSmDeriveSessionKeys();
        assertEquals("94E3194C02105E38153438D562D55B61", get(controller, "smSkEncField").getText());

        controller.handleSmEncipherPin();
        assertEquals("B3511E3333BF9DC56E1EDF6458BB52B6", get(controller, "smDataField").getText());

        controller.handleSmGenerateMac();
        assertTrue(result(controller).contains("E36046E6E5C110A2"), result(controller));
    }

    @Test
    void aBadPinIsReportedAndNothingIsEnciphered() throws Exception {
        EMVController controller = wire("Mastercard");
        controller.handleSmLoadExample();
        controller.handleSmDeriveSessionKeys();
        get(controller, "smPinField").setText("12");

        controller.handleSmEncipherPin();

        assertTrue(get(controller, "smDataField").getText().isEmpty());
        assertTrue(result(controller).contains(I18nService.getInstance().text("module.emv.sm.pinInvalid")),
                result(controller));
    }

    @Test
    void everyFxIdAndHandlerOfThePaneExistsInTheController() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/emv.fxml"), StandardCharsets.UTF_8);
        Matcher ids = Pattern.compile("fx:id=\"(sm[A-Za-z]+)\"").matcher(fxml);
        int count = 0;
        while (ids.find()) {
            EMVController.class.getDeclaredField(ids.group(1));
            count++;
        }
        assertEquals(TEXT_FIELDS.size() + 3, count, "text fields, the PIN, the scheme and the result area");
        Matcher handlers = Pattern.compile("onAction=\"#(handleSm[A-Za-z]+)\"").matcher(fxml);
        while (handlers.find()) {
            Method method = EMVController.class.getMethod(handlers.group(1));
            assertTrue(method != null);
        }
    }

    private static EMVController wire(String scheme) throws Exception {
        EMVController controller = new EMVController();
        set(controller, "emvContainer", new VBox());
        for (String name : TEXT_FIELDS) set(controller, name, new TextField());
        set(controller, "smPinField", new PasswordField());
        set(controller, "smResultArea", new TextArea());
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll("Mastercard", "Visa");
        combo.setValue(scheme);
        set(controller, "smSchemeCombo", combo);
        return controller;
    }

    private static String result(EMVController controller) throws Exception {
        return get(controller, "smResultArea").getText();
    }

    private static TextInputControl get(EMVController controller, String name) throws Exception {
        Field field = EMVController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (TextInputControl) field.get(controller);
    }

    private static void set(EMVController controller, String name, Object value) throws Exception {
        Field field = EMVController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
