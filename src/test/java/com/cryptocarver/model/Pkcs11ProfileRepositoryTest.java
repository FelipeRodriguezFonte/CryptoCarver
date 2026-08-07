package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Pkcs11ProfileRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void performsCrudAndPersistsProfilesAcrossReopen() throws IOException {
        Path file = temporaryDirectory.resolve("pkcs11-profiles.json");
        Path firstLibrary = temporaryDirectory.resolve("drivers").resolve("..").resolve("libone.so");
        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(file);

        repository.create(new Pkcs11Profile("  Lab token  ", firstLibrary.toAbsolutePath().toString(), 0));
        repository.create(new Pkcs11Profile("Second", temporaryDirectory.resolve("libtwo.so").toString(), 1));

        assertEquals(List.of(
                new Pkcs11Profile("Lab token", firstLibrary.toAbsolutePath().normalize().toString(), 0),
                new Pkcs11Profile("Second", temporaryDirectory.resolve("libtwo.so").toString(), 1)),
                repository.list());
        assertTrue(repository.find("LAB TOKEN").isPresent());
        assertThrows(IllegalArgumentException.class, () -> repository.create(
                new Pkcs11Profile(" lab TOKEN ", temporaryDirectory.resolve("other.so").toString(), 2)));

        repository.update("lab TOKEN", new Pkcs11Profile("Renamed", temporaryDirectory.resolve("updated.so").toString(), 2));
        assertTrue(repository.delete("second"));
        assertFalse(repository.delete("missing"));

        Pkcs11ProfileRepository reopened = new Pkcs11ProfileRepository(file);
        assertEquals(List.of(new Pkcs11Profile("Renamed", temporaryDirectory.resolve("updated.so").toString(), 2)),
                reopened.list());
        assertTrue(Files.readString(file).contains("\"schemaVersion\": 1"));
        assertTrue(Files.readString(file).contains("\"slotListIndex\": 2"));
        assertFalse(Files.readString(file).contains("\"slot\""));
    }

    @Test
    void rejectsInvalidProfilesAndOversizedValues() {
        String longName = "n".repeat(Pkcs11Profile.MAX_NAME_LENGTH + 1);
        String longPath = "/" + "p".repeat(Pkcs11Profile.MAX_LIBRARY_LENGTH);

        assertThrows(IllegalArgumentException.class, () -> new Pkcs11Profile(null,
                temporaryDirectory.resolve("module.so").toString(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Pkcs11Profile(" ",
                temporaryDirectory.resolve("module.so").toString(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Pkcs11Profile(longName,
                temporaryDirectory.resolve("module.so").toString(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Pkcs11Profile("Name", "relative/module.so", 0));
        assertThrows(IllegalArgumentException.class, () -> new Pkcs11Profile("Name", longPath, 0));
        assertThrows(IllegalArgumentException.class, () -> new Pkcs11Profile("Name",
                temporaryDirectory.resolve("module.so").toString(), -1));
    }

    @Test
    void readsPreviousUnversionedSchemaAndReemitsOnlyCurrentFields() throws IOException {
        Path file = temporaryDirectory.resolve("pkcs11-profiles.json");
        Path library = temporaryDirectory.resolve("legacy.so").toAbsolutePath();
        Files.writeString(file, """
                {
                  "schemaVersion": 0,
                  "profiles": [
                    {"name":"Legacy", "library":"%s", "slot":3, "password":"do-not-copy"}
                  ],
                  "pin":"do-not-copy"
                }
                """.formatted(library), StandardCharsets.UTF_8);

        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(file);

        assertEquals(List.of(new Pkcs11Profile("Legacy", library.toString(), 3)), repository.list());
        repository.update("Legacy", new Pkcs11Profile("Legacy", library.toString(), 4));
        String persisted = Files.readString(file);
        assertTrue(persisted.contains("\"schemaVersion\": 1"));
        assertTrue(persisted.contains("\"slotListIndex\": 4"));
        assertFalse(persisted.contains("do-not-copy"));
        assertFalse(persisted.toLowerCase().contains("pin"));
        assertFalse(persisted.toLowerCase().contains("password"));
    }

    @Test
    void migratesProfilesFromTheOriginalEmbeddedAppSettingsFormat() throws IOException {
        Path settingsFile = temporaryDirectory.resolve("settings.json");
        Path library = temporaryDirectory.resolve("legacy-app.so").toAbsolutePath();
        Files.writeString(settingsFile, """
                {
                  "pkcs11Profiles": [
                    {"name":"Legacy app", "library":"%s", "slot":5}
                  ]
                }
                """.formatted(library), StandardCharsets.UTF_8);

        AppSettings settings = new AppSettings(settingsFile);

        assertEquals(List.of(new Pkcs11Profile("Legacy app", library.toString(), 5)),
                settings.getPkcs11Profiles());
        Path dedicatedFile = temporaryDirectory.resolve("pkcs11-profiles.json");
        assertTrue(Files.exists(dedicatedFile));
        assertTrue(Files.readString(dedicatedFile).contains("\"schemaVersion\": 1"));
        assertFalse(Files.readString(dedicatedFile).contains("\"slot\""));
    }

    @Test
    void isolatesInvalidEntriesAndKeepsValidProfiles() throws IOException {
        Path file = temporaryDirectory.resolve("pkcs11-profiles.json");
        Path validLibrary = temporaryDirectory.resolve("valid.so").toAbsolutePath();
        Files.writeString(file, """
                {
                  "schemaVersion": 1,
                  "profiles": [
                    {"name":"Valid", "library":"%s", "slotListIndex":0},
                    {"name":"Relative", "library":"relative.so", "slotListIndex":1},
                    {"name":"Bad slot", "library":"%s", "slotListIndex":-1},
                    {"name":"valid", "library":"%s", "slotListIndex":2},
                    {"name":"Unknown fields", "library":"%s", "slotListIndex":3,
                     "alias":"secret-alias", "certificate":"secret-cert", "privateKey":"secret-key"}
                  ]
                }
                """.formatted(validLibrary, validLibrary, validLibrary, validLibrary), StandardCharsets.UTF_8);

        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(file);

        assertEquals(2, repository.list().size());
        assertEquals("Valid", repository.list().get(0).name());
        assertEquals("Unknown fields", repository.list().get(1).name());
        assertTrue(repository.list().toString().contains("Unknown fields"));
        assertFalse(repository.list().toString().contains("secret-key"));
        assertFalse(repository.list().toString().contains("secret-alias"));

        repository.upsert(new Pkcs11Profile("New", temporaryDirectory.resolve("new.so").toString(), 4));
        String persisted = Files.readString(file);
        assertFalse(persisted.contains("secret-alias"));
        assertFalse(persisted.contains("secret-cert"));
        assertFalse(persisted.contains("secret-key"));
    }

    @Test
    void malformedFileDoesNotOverwriteExistingInMemoryProfiles() throws IOException {
        Path file = temporaryDirectory.resolve("pkcs11-profiles.json");
        Pkcs11Profile profile = new Pkcs11Profile("Kept", temporaryDirectory.resolve("kept.so").toString(), 0);
        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(file);
        repository.create(profile);

        Files.writeString(file, "{\"schemaVersion\":1,\"profiles\":[", StandardCharsets.UTF_8);
        repository.reload();

        assertEquals(List.of(profile), repository.list());
    }

    @Test
    void stagesCompleteDocumentBeforeSimulatedAtomicMove() throws IOException {
        Path file = temporaryDirectory.resolve("pkcs11-profiles.json");
        AtomicBoolean moveCalled = new AtomicBoolean();
        AtomicBoolean stagedDocumentWasComplete = new AtomicBoolean();
        Pkcs11ProfileRepository.AtomicMover mover = (source, target) -> {
            moveCalled.set(true);
            assertNotNull(source);
            assertFalse(source.equals(target));
            String staged = Files.readString(source, StandardCharsets.UTF_8);
            stagedDocumentWasComplete.set(staged.contains("schemaVersion")
                    && staged.contains("Atomic") && staged.contains("slotListIndex"));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        };
        Pkcs11ProfileRepository repository = new Pkcs11ProfileRepository(file, mover);

        repository.create(new Pkcs11Profile("Atomic", temporaryDirectory.resolve("atomic.so").toString(), 0));

        assertTrue(moveCalled.get());
        assertTrue(stagedDocumentWasComplete.get());
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
