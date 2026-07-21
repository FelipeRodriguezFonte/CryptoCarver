package com.cryptoforge.model;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/** Executes chained operation recipes. */
public final class RecipeExecutor {
    private RecipeExecutor() { }

    /** Validates that all steps in the recipe are supported by SafeTransformations. */
    public static void validateSupport(OperationRecipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe is required");
        }
        for (OperationRecipe.Step step : recipe.steps()) {
            if (!isSupported(step.operation())) {
                throw new UnsupportedOperationException("Unsupported operation in recipe: " + step.operation());
            }
            if ("hmac-sha256".equals(step.operation())) {
                requireSecretVariable(step.parameters().get("key"));
            }
        }
    }

    /** Executes the recipe sequentially. */
    public static String execute(OperationRecipe recipe, String initialInput, Map<String, String> variables) throws Exception {
        validateSupport(recipe);
        String currentOutput = initialInput;

        for (OperationRecipe.Step step : recipe.steps()) {
            Map<String, String> resolved = new LinkedHashMap<>();
            step.parameters().forEach((k, v) -> resolved.put(k, RecipeVariables.resolve(v, variables)));

            currentOutput = executeStep(step.operation(), currentOutput, resolved);
        }

        return currentOutput;
    }

    /**
     * HMAC is opt-in for automation.  A recipe can name a temporary variable,
     * but it must never serialize the actual key material.
     */
    private static void requireSecretVariable(String template) {
        Set<String> names = RecipeVariables.referencedVariables(template);
        if (names.size() != 1) {
            throw new IllegalArgumentException("HMAC recipe key must be one explicit ${variable} reference");
        }
        String name = names.iterator().next();
        if (!("${" + name + "}").equals(template)) {
            throw new IllegalArgumentException("HMAC recipe key must not contain literal key material");
        }
    }

    private static boolean isSupported(String operation) {
        return switch (operation) {
            case "sha256", "base64url-encode", "base64url-decode", "compress-gzip", "decompress-gzip", "inspect-asn1", "inspect-tlv", "hmac-sha256" -> true;
            default -> false;
        };
    }

    private static String executeStep(String operation, String input, Map<String, String> params) throws Exception {
        String actualInput = input;
        if (actualInput == null || actualInput.isEmpty()) {
            actualInput = params.getOrDefault("input", params.get("value"));
        }
        if (actualInput == null) actualInput = "";

        return switch (operation) {
            case "sha256" -> SafeTransformations.sha256(actualInput);
            case "base64url-encode" -> SafeTransformations.encodeBase64Url(actualInput);
            case "base64url-decode" -> SafeTransformations.decodeBase64Url(actualInput);
            case "compress-gzip" -> SafeTransformations.compressGzip(actualInput);
            case "decompress-gzip" -> SafeTransformations.decompressGzip(actualInput);
            case "inspect-asn1" -> SafeTransformations.inspectAsn1(actualInput);
            case "inspect-tlv" -> SafeTransformations.inspectTlv(actualInput);
            case "hmac-sha256" -> {
                String key = params.get("key");
                if (key == null || key.isBlank()) throw new IllegalArgumentException("HMAC key is required");
                yield SafeTransformations.hmacSha256(actualInput, key);
            }
            default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
        };
    }
}
