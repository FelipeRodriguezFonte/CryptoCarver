package com.cryptoforge.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OperationRecipeTest {
    private SecretVisibility originalVisibility;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        originalVisibility = AppSettings.getInstance().getSecretVisibility();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AppSettings.getInstance().setSecretVisibility(originalVisibility);
    }

    @Test void serializesPortableNonSecretParameters() {
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.REDACTED);
        java.util.List<OperationDetail> details = java.util.List.of(
            OperationDetail.publicDetail("input", "9F0206"),
            OperationDetail.secretDetail("fileCipherKeyField", "nope"),
            OperationDetail.publicDetail("tsaUrl", "https://tsa.example")
        );
        OperationRecipe recipe = new OperationRecipe("EMV TLV Inspector", details, SecretVisibility.REDACTED);
        OperationRecipe loaded = OperationRecipe.fromJson(recipe.toJson());
        assertEquals("EMV TLV Inspector", loaded.operation());
        assertFalse(loaded.parameters().containsKey("fileCipherKeyField"));
        assertEquals("https://tsa.example", loaded.parameters().get("tsaUrl"));
    }

    @Test void testsMaskedVisibility() {
        java.util.List<OperationDetail> details = java.util.List.of(
            OperationDetail.secretDetail("fileCipherKeyField", "super-secret"),
            OperationDetail.sensitiveDetail("email", "test@test.com")
        );
        OperationRecipe recipe = new OperationRecipe("Op", details, SecretVisibility.MASKED);
        OperationRecipe loaded = OperationRecipe.fromJson(recipe.toJson());
        assertEquals("***MASKED***", loaded.parameters().get("fileCipherKeyField"));
        assertEquals("***MASKED***", loaded.parameters().get("email"));
    }

    @Test void testsFullLabVisibility() {
        java.util.List<OperationDetail> details = java.util.List.of(
            OperationDetail.secretDetail("fileCipherKeyField", "super-secret"),
            OperationDetail.sensitiveDetail("email", "test@test.com")
        );
        OperationRecipe recipe = new OperationRecipe("Op", details, SecretVisibility.FULL_LAB);
        OperationRecipe loaded = OperationRecipe.fromJson(recipe.toJson());
        assertEquals("super-secret", loaded.parameters().get("fileCipherKeyField"));
        assertEquals("test@test.com", loaded.parameters().get("email"));
    }

    @Test void rejectsUnknownVersion() {
        assertThrows(IllegalArgumentException.class, () -> OperationRecipe.fromJson("{\"version\":99,\"operation\":\"x\"}"));
    }
    @Test void rejectsOversizedParameter() {
        assertThrows(IllegalArgumentException.class, () -> new OperationRecipe("x", java.util.List.of(OperationDetail.publicDetail("input", "x".repeat(1_000_001))), SecretVisibility.FULL_LAB));
    }


    @Test void legacyMapExportIsBlocked() {
        assertThrows(UnsupportedOperationException.class, () -> new OperationRecipe("x", Map.of("fileCipherKeyField", "super-secret")));
    }

    @Test void testsJoseJweExport() {
        java.util.List<OperationDetail> details = java.util.List.of(
            OperationDetail.publicDetail("Algorithm", "RSA-OAEP"),
            OperationDetail.publicDetail("Content Algorithm", "A128GCM"),
            OperationDetail.secretDetail("Key Material", "PEM-PRIVATE-KEY")
        );

        OperationRecipe redacted = new OperationRecipe("JWE Encryption", details, SecretVisibility.REDACTED);
        assertFalse(redacted.parameters().containsKey("Key Material"));

        OperationRecipe masked = new OperationRecipe("JWE Encryption", details, SecretVisibility.MASKED);
        assertEquals("***MASKED***", masked.parameters().get("Key Material"));

        OperationRecipe full = new OperationRecipe("JWE Encryption", details, SecretVisibility.FULL_LAB);
        assertEquals("PEM-PRIVATE-KEY", full.parameters().get("Key Material"));
    }

    @Test void nullVisibilityIsFailClosed() {
        java.util.List<OperationDetail> details = java.util.List.of(
            OperationDetail.secretDetail("fileCipherKeyField", "super-secret"),
            OperationDetail.publicDetail("publicField", "value")
        );

        OperationRecipe recipe = new OperationRecipe("Op", details, null);
        assertFalse(recipe.parameters().containsKey("fileCipherKeyField"));
        assertEquals("value", recipe.parameters().get("publicField"));
    }
}
