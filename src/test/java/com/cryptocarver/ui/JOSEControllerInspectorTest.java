package com.cryptocarver.ui;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.cryptocarver.crypto.JweComposer;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.SignerConfig;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JOSEControllerInspectorTest {

    @Test
    void breaksDownGeneralJsonJwsWithEverySignature() throws Exception {
        String json = JOSEService.generateSignedJWT("{\"sub\":\"json\"}", List.of(
                new SignerConfig("HS256", "one"), new SignerConfig("HS512", "two")), "General JSON", false);
        String report = inspect(json);
        assertTrue(report.contains("[JWS JSON Detected — General]"), report);
        assertTrue(report.contains("SIGNATURE 2 PROTECTED HEADER"), report);
        assertTrue(report.contains("\"alg\": \"HS512\""), report);
        assertTrue(report.contains("\"sub\""), report);
    }

    @Test
    void breaksDownFlattenedJsonJweIncludingAad() throws Exception {
        String json = JweComposer.encrypt("secret", "A128KW", "A128GCM", false, JweComposer.HeaderOptions.none(),
                "00112233445566778899aabbccddeeff", SecretEncoding.HEX, 1000,
                JweComposer.Serialization.FLATTENED, "context");
        String report = inspect(json);
        assertTrue(report.contains("[JWE JSON Detected — Flattened]"), report);
        assertTrue(report.contains("\"alg\": \"A128KW\""), report);
        assertTrue(report.contains("=== AAD ==="), report);
        assertTrue(report.contains("context"), report);
        assertTrue(report.contains("=== ENCRYPTED KEY ==="), report);
    }

    @Test
    void compactJweHeaderIsPrettyPrinted() throws Exception {
        String compact = JweComposer.encrypt("secret", "dir", "A128GCM", false, JweComposer.HeaderOptions.none(),
                "00112233445566778899aabbccddeeff", SecretEncoding.HEX, 1000);
        String report = inspect(compact);
        assertTrue(report.contains("[JWE Detected]"), report);
        assertTrue(report.contains("\"enc\": \"A128GCM\""), report);
    }

    private static String inspect(String token) {
        TextFlow flow = new TextFlow();
        new JOSEController().inspectToken(token, flow);
        return flow.getChildren().stream().map(node -> ((Text) node).getText()).collect(Collectors.joining());
    }
}
