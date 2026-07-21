package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyStoreInspectorTest {
    @TempDir Path directory;

    @Test
    void reportsJceksAliasesWithoutExportingSecretKey() throws Exception {
        char[] password = "test-password".toCharArray();
        KeyStore store = KeyStore.getInstance("JCEKS");
        store.load(null, password);
        store.setEntry("lab-aes", new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[16], "AES")),
                new KeyStore.PasswordProtection(password));
        Path file = directory.resolve("lab.jceks");
        try (var output = Files.newOutputStream(file)) { store.store(output, password); }
        KeyStoreInspector.Report report = KeyStoreInspector.inspect(file, password, "Auto");
        assertEquals("JCEKS", report.type());
        assertEquals(1, report.entries().size());
        assertEquals("lab-aes", report.entries().get(0).alias());
        assertEquals("Key entry", report.entries().get(0).kind());
        assertEquals("Not requested", report.entries().get(0).keyMaterial());
        KeyStoreInspector.Report extracted = KeyStoreInspector.inspect(file, password, "JCEKS", true);
        assertEquals("00000000000000000000000000000000", extracted.entries().get(0).keyMaterial());
    }
}
