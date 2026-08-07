package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectEncrypter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JOSEControllerJweSecurityTest {

    @Test
    void directCekAndSecretNeverEnterOperationResultOrItsHistoryInspectorSurfaces() throws Exception {
        String directSecret = "0123456789ABCDEF0123456789ABCDEF";
        String payload = "authenticated payload";
        JWEObject jwe = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).build(),
                new Payload(payload));
        jwe.encrypt(new DirectEncrypter(directSecret.getBytes(StandardCharsets.UTF_8)));

        OperationResult result = new JOSEController().buildJweDecryptionResult(
                jwe.serialize(), payload, jwe);
        RecordingReporter reporter = new RecordingReporter();
        reporter.publish(result);

        String resultSurface = resultText(result);
        assertFalse(resultSurface.contains(directSecret));
        assertFalse(resultSurface.contains("Key Material"));
        assertNotNull(reporter.inspectorText);
        assertNotNull(reporter.historyText);
        assertFalse(reporter.inspectorText.contains(directSecret));
        assertFalse(reporter.historyText.contains(directSecret));
        assertEquals("Direct encryption: the CEK is the supplied direct key and is not displayed automatically.",
                JOSEController.directCekPreviewMessage());
    }

    private static String resultText(OperationResult result) {
        StringBuilder text = new StringBuilder();
        text.append(new String(result.getInput(), StandardCharsets.US_ASCII));
        text.append(new String(result.getOutput(), StandardCharsets.UTF_8));
        text.append(result.getStatusMessage());
        for (OperationDetail detail : result.getDetails()) {
            text.append(detail.name()).append(detail.value());
        }
        return text.toString();
    }

    private static final class RecordingReporter implements StatusReporter {
        private String inspectorText;
        private String historyText;

        @Override public void updateStatus(String message) { }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
            inspectorText = operation + new String(input, StandardCharsets.US_ASCII)
                    + new String(output, StandardCharsets.UTF_8) + details;
        }

        @Override public void showError(String title, String message) { }

        @Override
        public void addToHistory(String operation, List<OperationDetail> details) {
            historyText = operation + details;
        }
    }
}
