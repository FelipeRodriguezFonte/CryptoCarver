package com.cryptocarver.service;

import org.junit.jupiter.api.Test;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class LocalHelpServiceTest {
    @Test void selectsAHeadingWithoutNetwork() throws Exception {
        Path root = Files.createTempDirectory("help");
        Path file = root.resolve("docs/guide_en_extended.md"); Files.createDirectories(file.getParent());
        Files.writeString(file, "# Guide\n## Cipher\nUse AES.\n## Keys\nUse RSA.\n", StandardCharsets.UTF_8);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()})) {
            LocalHelpService service = new LocalHelpService(loader);
            assertTrue(service.section("cipher", Locale.ENGLISH).contains("Use AES"));
            assertFalse(service.section("cipher", Locale.ENGLISH).contains("Use RSA"));
        } finally { Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } }); }
    }
}
