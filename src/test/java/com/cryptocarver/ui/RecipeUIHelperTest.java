package com.cryptocarver.ui;

import com.cryptocarver.model.FileCipherRecipe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RecipeUIHelperTest {

    @Test
    public void testBuildRecipeForExport_CbcMode() {
        RecipeUIHelper.RecipeUIState state = new RecipeUIHelper.RecipeUIState(
            "AES-256-CBC", false, "Hexadecimal", false, "UTF-8", null, "00112233445566778899AABBCCDDEEFF", null
        );
        FileCipherRecipe recipe = RecipeUIHelper.buildRecipeForExport(state);

        assertEquals("AES-256-CBC", recipe.getAlgorithm());
        assertEquals("00112233445566778899AABBCCDDEEFF", recipe.getIvNonceHex());
        assertFalse(recipe.isLinesMode());
        assertTrue(RecipeUIHelper.requiresSecurityWarning(recipe));
    }

    @Test
    public void testBuildRecipeForExport_GcmLinesMode() {
        // UI might have an old IV in the text field, but it should NOT be exported for GCM lines mode
        RecipeUIHelper.RecipeUIState state = new RecipeUIHelper.RecipeUIState(
            "AES-256-GCM", true, "Base64URL", false, "UTF-8", null, "123456789012", null
        );
        FileCipherRecipe recipe = RecipeUIHelper.buildRecipeForExport(state);

        assertEquals("AES-256-GCM", recipe.getAlgorithm());
        assertTrue(recipe.isLinesMode());
        assertNull(recipe.getIvNonceHex(), "Nonce should not be exported for GCM in lines mode");
        assertFalse(RecipeUIHelper.requiresSecurityWarning(recipe), "Should not warn if nonce was stripped");
    }

    @Test
    public void testBuildRecipeForExport_GcmFullMode() {
        RecipeUIHelper.RecipeUIState state = new RecipeUIHelper.RecipeUIState(
            "AES-256-GCM", false, null, false, null, "AABBCC", "112233445566778899001122", "/some/path/tag.bin"
        );
        FileCipherRecipe recipe = RecipeUIHelper.buildRecipeForExport(state);

        assertEquals("AES-256-GCM", recipe.getAlgorithm());
        assertEquals("AABBCC", recipe.getAadHex());
        assertEquals("112233445566778899001122", recipe.getIvNonceHex());
        assertEquals("tag.bin", recipe.getTagRef(), "Should extract only filename from path");
        assertTrue(RecipeUIHelper.requiresSecurityWarning(recipe));
    }

    @Test
    public void testBuildRecipeForExport_GcmFullMode_MissingTagRef() {
        RecipeUIHelper.RecipeUIState state = new RecipeUIHelper.RecipeUIState(
            "AES-256-GCM", false, null, false, null, "AABBCC", "112233445566778899001122", null
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> RecipeUIHelper.buildRecipeForExport(state));
        assertTrue(ex.getMessage().contains("el tagRef es obligatorio"));
    }

    @Test
    public void testCalculateLocalTagPath() {
        // No UI path previously
        assertEquals("my.tag", RecipeUIHelper.calculateLocalTagPath("", "my.tag"));
        assertEquals("my.tag", RecipeUIHelper.calculateLocalTagPath(null, "my.tag"));

        // With absolute UI path (OS-agnostic checking)
        String calculated = RecipeUIHelper.calculateLocalTagPath("/Users/test/dir/old.tag", "my.tag");

        assertTrue(calculated.endsWith("my.tag"));
        assertNotEquals("my.tag", calculated);

        // Security check for tagRef content
        assertThrows(IllegalArgumentException.class, () -> RecipeUIHelper.calculateLocalTagPath("/dir/a", "../my.tag"));
        assertThrows(IllegalArgumentException.class, () -> RecipeUIHelper.calculateLocalTagPath("/dir/a", "/my.tag"));
        assertThrows(IllegalArgumentException.class, () -> RecipeUIHelper.calculateLocalTagPath("/dir/a", "a\\b.tag"));
    }
}
