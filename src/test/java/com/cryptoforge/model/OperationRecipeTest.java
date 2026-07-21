package com.cryptoforge.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OperationRecipeTest {

    @Test
    void testValidChain() {
        OperationRecipe.Step step1 = new OperationRecipe.Step("op1", "text", "bytes", Map.of());
        OperationRecipe.Step step2 = new OperationRecipe.Step("op2", "bytes", "text", Map.of());
        assertDoesNotThrow(() -> new OperationRecipe("test", List.of(step1, step2)));
    }

    @Test
    void testValidChainWithAny() {
        OperationRecipe.Step step1 = new OperationRecipe.Step("op1", "text", "bytes", Map.of());
        OperationRecipe.Step step2 = new OperationRecipe.Step("op2", "any", "text", Map.of());
        assertDoesNotThrow(() -> new OperationRecipe("test", List.of(step1, step2)));
    }

    @Test
    void testIncompatibleChain() {
        OperationRecipe.Step step1 = new OperationRecipe.Step("op1", "text", "bytes", Map.of());
        OperationRecipe.Step step2 = new OperationRecipe.Step("op2", "text", "bytes", Map.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new OperationRecipe("test", List.of(step1, step2)));

        assertTrue(exception.getMessage().contains("Incompatible chain: step 0 outputs bytes but step 1 expects text"));
    }

    @Test
    void testFromJsonV2() {
        String json = "{\n" +
                "  \"version\": 2,\n" +
                "  \"operation\": \"test\",\n" +
                "  \"steps\": [\n" +
                "    {\n" +
                "      \"operation\": \"op1\",\n" +
                "      \"inputType\": \"bytes\",\n" +
                "      \"outputType\": \"bytes\",\n" +
                "      \"parameters\": {}\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        OperationRecipe recipe = OperationRecipe.fromJson(json);
        assertEquals(2, recipe.version());
        assertEquals(1, recipe.steps().size());
        assertEquals("op1", recipe.steps().get(0).operation());
    }

    @Test
    void migratesV1RecipeWithoutLosingItsTimestamp() {
        OperationRecipe recipe = OperationRecipe.fromJson("""
                {"version":1,"operation":"base64url-encode","createdAt":"2026-01-02T03:04:05Z",
                 "parameters":{"input":"hello"}}
                """);

        assertEquals("2026-01-02T03:04:05Z", recipe.createdAt());
        assertEquals(1, recipe.steps().size());
        assertEquals("base64url-encode", recipe.steps().get(0).operation());
    }

    @Test
    void executesACompleteSafeRecipeChain() throws Exception {
        OperationRecipe recipe = new OperationRecipe("round-trip", List.of(
                new OperationRecipe.Step("base64url-encode", "text", "text", Map.of("input", "hello")),
                new OperationRecipe.Step("base64url-decode", "text", "text", Map.of())));

        assertEquals("hello", RecipeExecutor.execute(recipe, "", Map.of()));
    }

    @Test
    void rejectsLiteralHmacKeyInPortableRecipe() {
        OperationRecipe recipe = new OperationRecipe("unsafe", List.of(
                new OperationRecipe.Step("hmac-sha256", "text", "text", Map.of("key", "c2VjcmV0"))));

        assertThrows(IllegalArgumentException.class, () -> RecipeExecutor.validateSupport(recipe));
    }
}
