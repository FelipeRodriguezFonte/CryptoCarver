package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ODA pane end to end, through the controller rather than the facade.
 *
 * <p>Every field is wired by name from the FXML, and a name that does not match
 * leaves a silent {@code null} that only shows up as a button doing nothing. The
 * test personalises a card with the pane's own button and then verifies all
 * three schemes with the pane's own buttons, so a broken {@code fx:id} fails
 * here instead of in front of someone.</p>
 */
class EmvOdaControllerTest {

    private static final List<String> FIELDS = List.of(
            "odaCaModulusArea", "odaCaExponentField", "odaIssuerCertificateArea", "odaIssuerRemainderField",
            "odaIssuerExponentField", "odaIccCertificateArea", "odaIccRemainderField", "odaIccExponentField",
            "odaStaticDataArea", "odaPanField", "odaSsadArea", "odaSdadArea", "odaTerminalDataField",
            "odaCidField", "odaTransactionDataArea", "odaResultArea");

    private static LanguagePreference preferenceOnEntry;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (Exception alreadyRunning) {
            // The toolkit is process-wide; another test class may have started it.
        }
        // The handlers under test read I18nService, which is a process-wide
        // singleton that other UI tests reconfigure. This class does not need a
        // particular language, but it should hand back whatever it was given.
        preferenceOnEntry = I18nService.getInstance().getPreference();
    }

    @AfterAll
    static void restorePreference() {
        if (preferenceOnEntry != null) {
            I18nService.getInstance().setPreference(preferenceOnEntry);
        }
    }

    @Test
    void theTestCardFillsEveryFieldAndAllThreeSchemesPass() throws Exception {
        EMVController controller = wire();

        controller.handleOdaIssueTestCard();

        for (String name : List.of("odaCaModulusArea", "odaIssuerCertificateArea", "odaIccCertificateArea",
                "odaSsadArea", "odaSdadArea", "odaStaticDataArea", "odaPanField")) {
            assertFalse(get(controller, name).getText().isBlank(), name + " was left empty");
        }

        controller.handleOdaRecoverKeys();
        String recovery = result(controller);
        assertTrue(recovery.contains("Issuer Public Key Certificate"), recovery);
        assertTrue(recovery.contains("ICC Public Key Certificate"), recovery);
        assertFalse(recovery.contains("FAILED"), recovery);

        controller.handleOdaVerifySda();
        assertTrue(result(controller).contains("PASSED"), result(controller));

        controller.handleOdaVerifyCda();
        assertTrue(result(controller).contains("PASSED"), result(controller));
    }

    @Test
    void aTamperedStaticDataFailsWhileRecoveringTheIccKey() throws Exception {
        // Book 2 clause 6.4 step 5 folds the static data into the ICC
        // certificate's hash, so this is where a bad AFL surfaces.
        EMVController controller = wire();
        controller.handleOdaIssueTestCard();
        TextInputControl staticData = get(controller, "odaStaticDataArea");
        staticData.setText(staticData.getText() + "00");

        controller.handleOdaRecoverKeys();

        String report = result(controller);
        assertTrue(report.contains("FAILED"), report);
        assertTrue(report.contains("ICC Public Key Certificate does not match"), report);
    }

    @Test
    void clearEmptiesEveryOdaField() throws Exception {
        EMVController controller = wire();
        controller.handleOdaIssueTestCard();

        controller.handleOdaClear();

        for (String name : FIELDS) {
            assertTrue(get(controller, name).getText().isEmpty(), name + " survived the clear");
        }
    }

    private static EMVController wire() throws Exception {
        EMVController controller = new EMVController();
        set(controller, "emvContainer", new VBox());
        for (String name : FIELDS) {
            set(controller, name, name.endsWith("Area") ? new TextArea() : new TextField());
        }
        return controller;
    }

    private static String result(EMVController controller) throws Exception {
        return get(controller, "odaResultArea").getText();
    }

    private static TextInputControl get(EMVController controller, String fieldName) throws Exception {
        Field field = EMVController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (TextInputControl) field.get(controller);
    }

    private static void set(EMVController controller, String fieldName, Object value) throws Exception {
        Field field = EMVController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
