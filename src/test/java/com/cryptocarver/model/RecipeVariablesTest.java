package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RecipeVariablesTest {
    @Test void resolvesNamedVariablesWithoutRegexSideEffects() {
        assertEquals("https://tsa.example/file.xml", RecipeVariables.resolve("${url}/${file}", Map.of("url", "https://tsa.example", "file", "file.xml")));
    }
    @Test void rejectsUnknownOrMalformedVariables() {
        assertThrows(IllegalArgumentException.class, () -> RecipeVariables.resolve("${missing}", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> RecipeVariables.resolve("${bad name}", Map.of()));
    }
    @Test void listsDistinctReferencedVariables() {
        assertEquals(java.util.Set.of("host", "file"), RecipeVariables.referencedVariables("${host}/${file}/${host}"));
    }
}
