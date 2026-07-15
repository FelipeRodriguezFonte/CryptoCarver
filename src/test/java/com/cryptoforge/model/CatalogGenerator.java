package com.cryptoforge.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class CatalogGenerator {
    public static void main(String[] args) {
        String markdown = OperationRegistry.getInstance().toMarkdownCatalog();
        Path catalogPath = Paths.get("docs", "OPERATIONS_CATALOG.md");
        
        try {
            Files.writeString(catalogPath, markdown, StandardCharsets.UTF_8);
            System.out.println("Successfully generated " + catalogPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write catalog: " + e.getMessage());
            System.exit(1);
        }
    }
}
