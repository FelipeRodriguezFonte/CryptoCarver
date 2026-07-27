package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibility;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OperationResultRendererTest {

    @Test
    void rendersPrintableUtf8AndBinaryDeterministically() {
        assertEquals("á\nOK", OperationResultRenderer.renderBytes("á\nOK".getBytes(StandardCharsets.UTF_8)));
        assertEquals("00FF80", OperationResultRenderer.renderBytes(new byte[]{0, (byte) 0xFF, (byte) 0x80}));
        assertFalse(OperationResultRenderer.isPrintableUtf8(new byte[]{0}));
        assertFalse(OperationResultRenderer.isPrintableUtf8(new byte[]{(byte) 0xC3, 0x28}));
    }

    @Test
    void appliesExplicitVisibilityToEnrichedAndByteOutputs() {
        OperationResult secret = OperationResult.forOperation("Secret export")
                .enrichedOutput("PRIVATE", OperationDetail.Classification.SECRET)
                .build();
        assertEquals("PRIVATE", OperationResultRenderer.render(secret, SecretVisibility.FULL_LAB));
        assertEquals("***MASKED***", OperationResultRenderer.render(secret, SecretVisibility.MASKED));
        assertEquals("", OperationResultRenderer.render(secret, SecretVisibility.REDACTED));
        assertEquals("", OperationResultRenderer.render(secret, null), "Null policy must fail closed");

        OperationResult sensitive = OperationResult.forOperation("Ciphertext")
                .output("DATA".getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SENSITIVE)
                .build();
        assertEquals("***MASKED***", OperationResultRenderer.render(sensitive, SecretVisibility.MASKED));
        assertEquals("DATA", OperationResultRenderer.render(sensitive, SecretVisibility.FULL_LAB));
    }

    @Test
    void aProtectedDetailProtectsTheWholePublishedReport() {
        OperationResult result = OperationResult.forOperation("Certificate report")
                .enrichedOutput("PUBLIC LOOKING REPORT", OperationDetail.Classification.PUBLIC)
                .detail(OperationDetail.secretDetail("Private key", "hidden"))
                .build();

        assertEquals(OperationDetail.Classification.SECRET, OperationResultRenderer.classification(result));
        assertEquals("***MASKED***", OperationResultRenderer.render(result, SecretVisibility.MASKED));
        assertEquals("", OperationResultRenderer.render(result, SecretVisibility.REDACTED));
    }

    @Test
    void noPayloadProducesAStablePublicSummary() {
        OperationResult result = OperationResult.forOperation("Validate Certificate")
                .status("Valid")
                .detail(OperationDetail.publicDetail("Subject", "CN=Test"))
                .build();

        assertEquals("Operation: Validate Certificate\nStatus: Valid\nSubject: CN=Test",
                OperationResultRenderer.render(result, SecretVisibility.REDACTED));
    }
}
