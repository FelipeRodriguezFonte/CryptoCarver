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
    @Test void jweReferencesArePackagedInBothLocales() {
        LocalHelpService service = new LocalHelpService();
        assertTrue(service.readReference("jose.jwe", Locale.ENGLISH).orElseThrow()
                .contains("https://www.rfc-editor.org/rfc/rfc7516.html"));
        assertTrue(service.readReference("jose.jwe", new Locale("es")).orElseThrow()
                .contains("https://www.rfc-editor.org/rfc/rfc7518.html"));
    }

    @Test void readsOnlyExplicitPackagedReferenceIds() throws Exception {
        Path root = Files.createTempDirectory("help");
        Path file = root.resolve("help/en/cipher.symmetric.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Exact reference.", StandardCharsets.UTF_8);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            LocalHelpService service = new LocalHelpService(loader);
            assertEquals("Exact reference.", service.readReference("cipher.symmetric", Locale.ENGLISH).orElseThrow());
            assertTrue(service.readReference("cipher", Locale.ENGLISH).isEmpty());
            assertTrue(service.readReference("../cipher.symmetric", Locale.ENGLISH).isEmpty());
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
            }
        }
    }
}
