package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ResultPresentationPolicyTest {
    @Test void recognizesCompletePrivateMaterialAndPlaceholder() {
        String key = "-----BEGIN PRIVATE KEY-----\nSYNTHETIC_FIXTURE\n-----END PRIVATE KEY-----";
        assertTrue(ResultPresentationPolicy.isCompletePrivateKeyMaterial(key));
        assertFalse(ResultPresentationPolicy.isCompletePrivateKeyMaterial("-----BEGIN PRIVATE KEY-----"));
        assertTrue(ResultPresentationPolicy.isPrivateMaterialPlaceholder("*** PRIVATE KEY MATERIAL — NOT RECORDED ***"));
        assertFalse(ResultPresentationPolicy.isCompletePrivateKeyMaterial("*** PRIVATE KEY MATERIAL — NOT RECORDED ***"));
    }

    @Test void captureBlockingTracksVisibilityAndClassification() {
        for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
            assertEquals(profile != SecretVisibilityProfile.FULL_LAB,
                    ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(OperationDetail.Classification.SECRET, profile));
            assertFalse(ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(OperationDetail.Classification.PUBLIC, profile));
        }
    }

    @Test void rendersBytesAndBuildsSensitiveHistoryDetails() {
        assertEquals("hello", ResultPresentationPolicy.renderBytesForDisplay("hello".getBytes(StandardCharsets.UTF_8)));
        assertEquals("00FF", ResultPresentationPolicy.renderBytesForDisplay(new byte[]{0, (byte) 255}));
        OperationResult result = OperationResult.forOperation("test").input(new byte[]{65}).output(new byte[]{0}).build();
        var details = ResultPresentationPolicy.detailsForHistory(result);
        assertEquals(2, details.size());
        assertEquals(OperationDetail.Classification.SENSITIVE, details.get(0).classification());
        assertEquals("Input (1 bytes)", details.get(0).name());
    }
}
