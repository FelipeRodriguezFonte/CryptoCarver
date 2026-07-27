package com.cryptocarver.ui;

import com.cryptocarver.model.FileCipherRecipe;
import java.nio.file.Path;

/**
 * Pure helper for separating recipe UI logic from JavaFX dependencies.
 */
public class RecipeUIHelper {

    public record RecipeUIState(
            String algorithm,
            boolean linesMode,
            String lineEncoding,
            boolean compactMode,
            String charset,
            String aadHex,
            String ivNonceHex,
            String tagRefOrPath
    ) {}

    /**
     * Builds a FileCipherRecipe from the given UI state, extracting just the relative tag name
     * and nullifying fields that shouldn't be included depending on the algorithm logic.
     */
    public static FileCipherRecipe buildRecipeForExport(RecipeUIState state) {
        boolean isAead = "AES-256-GCM".equals(state.algorithm()) || "ChaCha20-Poly1305".equals(state.algorithm());
        String ivNonceHex = null;
        if (state.ivNonceHex() != null && !state.ivNonceHex().trim().isEmpty()) {
            if (!(isAead && state.linesMode())) {
                ivNonceHex = state.ivNonceHex().trim();
            }
        }

        String tagRef = null;
        if (state.tagRefOrPath() != null && !state.tagRefOrPath().trim().isEmpty()) {
            Path tagPath = Path.of(state.tagRefOrPath().trim());
            tagRef = tagPath.getFileName().toString();
        } else if (isAead && !state.linesMode()) {
            throw new IllegalArgumentException("En modo fichero completo AEAD, el tagRef es obligatorio.");
        }

        String aadHex = (state.aadHex() != null && !state.aadHex().trim().isEmpty()) ? state.aadHex().trim() : null;

        return new FileCipherRecipe(
                state.algorithm(),
                state.linesMode(),
                state.lineEncoding(),
                state.compactMode(),
                state.charset(),
                aadHex,
                ivNonceHex,
                tagRef
        );
    }

    /**
     * Calculates the local tag path safely from a given relative tagRef and the current absolute path in UI.
     */
    public static String calculateLocalTagPath(String currentTagPath, String tagRef) {
        if (tagRef == null || tagRef.isEmpty()) {
            return null;
        }
        if (tagRef.contains("/") || tagRef.contains("\\") || tagRef.contains("..")) {
            throw new IllegalArgumentException("El tagRef debe ser un nombre de fichero simple.");
        }
        if (currentTagPath != null && !currentTagPath.isEmpty()) {
            Path currentDir = Path.of(currentTagPath).getParent();
            if (currentDir != null) {
                return currentDir.resolve(tagRef).toString();
            }
        }
        return tagRef;
    }

    /**
     * Determines if a security warning should be displayed when exporting.
     */
    public static boolean requiresSecurityWarning(FileCipherRecipe recipe) {
        return recipe.getIvNonceHex() != null || recipe.getAadHex() != null;
    }
}
