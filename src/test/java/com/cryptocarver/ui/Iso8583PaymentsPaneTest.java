package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Contracts for the ISO 8583 section of the existing Payments module. */
class Iso8583PaymentsPaneTest {
    @Test
    void iso8583ButtonsAreBoundToControllerActions() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/payments.fxml"));
        assertTrue(fxml.contains("text=\"Parse message\" onAction=\"#handleParseIso8583\""));
        assertTrue(fxml.contains("text=\"Build message\" onAction=\"#handleBuildIso8583\""));
        assertNotNull(PaymentsController.class.getMethod("handleParseIso8583"));
        assertNotNull(PaymentsController.class.getMethod("handleBuildIso8583"));
        assertTrue(ModifierHelper.isPublic(PaymentsController.class.getMethod("handleParseIso8583")));
    }

    @Test
    void iso8583PaneTextsHaveEnglishAndSpanishTranslations() {
        I18nService service = I18nService.getInstance();
        try {
            service.setPreference(LanguagePreference.EN);
            assertEquals("ISO 8583 Message Inspector", service.text("module.payments.iso8583.title"));
            assertEquals("Parse message", service.text("module.payments.iso8583.parse"));
            service.setPreference(LanguagePreference.ES);
            assertEquals("Inspector de mensajes ISO 8583", service.text("module.payments.iso8583.title"));
            assertEquals("Analizar mensaje", service.text("module.payments.iso8583.parse"));
        } finally {
            service.setPreference(LanguagePreference.EN);
        }
    }

    @Test
    void paymentsPanelExposesReviewedPaymentAlgorithms() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/cryptocarver/ui/PaymentsController.java"));
        assertTrue(com.cryptocarver.crypto.PinBlockFormat.displayNames().contains("VISA-2"));
        assertTrue(com.cryptocarver.crypto.PinBlockFormat.displayNames().contains("ECI-2 (no PAN binding)"));
        assertTrue(controller.contains("ISO-9797-1-ALG2"));
        assertTrue(controller.contains("ISO-9797-1-ALG4"));
        assertTrue(controller.contains("ISO-9797-1-ALG6"));
        String generic = Files.readString(Path.of("src/main/resources/fxml/generic.fxml"));
        assertTrue(generic.contains("onAction=\"#handleExtractTraceHex\""));
        assertTrue(generic.contains("onAction=\"#handleShiftLeft\""));
    }

    private static final class ModifierHelper {
        static boolean isPublic(Method method) {
            return java.lang.reflect.Modifier.isPublic(method.getModifiers());
        }
    }
}
