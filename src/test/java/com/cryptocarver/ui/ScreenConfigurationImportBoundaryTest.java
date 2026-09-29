package com.cryptocarver.ui;

import com.cryptocarver.model.ScreenConfiguration;
import com.cryptocarver.model.ScreenConfigurationCodec;
import com.cryptocarver.model.ScreenConfigurationFiles;
import com.cryptocarver.model.ScreenConfigurationImportException;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenConfigurationImportBoundaryTest {
    private static final String PASSWORD = "SYNTHETIC-PASSWORD";
    @TempDir Path temp;

    @Test
    void importFromAcceptsPlainFileWithoutJavaFx() throws Exception {
        Path path = write("plain.json", ScreenConfigurationCodec.encodePlain(sample()));
        assertDoesNotThrow(() -> coordinator().importFrom(path, null));
    }

    @Test
    void importFromAcceptsEncryptedFileWithoutJavaFx() throws Exception {
        Path path = write("encrypted.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray()));
        assertDoesNotThrow(() -> coordinator().importFrom(path, PASSWORD.toCharArray()));
    }

    @Test
    void importFromRejectsWrongPassword() throws Exception {
        Path path = write("encrypted.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator().importFrom(path, "SYNTHETIC-WRONG".toCharArray()));
    }

    @Test
    void importFromRejectsModifiedCiphertext() throws Exception {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray());
        int start = encrypted.indexOf("\"ciphertext\": \"") + "\"ciphertext\": \"".length();
        char original = encrypted.charAt(start);
        String modified = encrypted.substring(0, start) + (original == 'A' ? 'B' : 'A') + encrypted.substring(start + 1);
        Path path = write("modified.ccconfig", modified);
        assertThrows(IllegalArgumentException.class,
                () -> coordinator().importFrom(path, PASSWORD.toCharArray()));
    }

    @Test
    void importFromRejectsNonJson() throws Exception {
        Path path = write("invalid.json", "not json");
        assertThrows(IllegalArgumentException.class, () -> coordinator().importFrom(path, null));
    }

    @Test
    void importFromRejectsJsonThatIsNotConfiguration() throws Exception {
        Path path = write("other.json", "{\"hello\":\"world\"}");
        assertThrows(IllegalArgumentException.class, () -> coordinator().importFrom(path, null));
    }

    @Test
    void importFromRejectsUnsupportedVersion() throws Exception {
        String json = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 99");
        Path path = write("future.json", json);
        assertThrows(IllegalArgumentException.class, () -> coordinator().importFrom(path, null));
    }

    @Test
    void importFromMapsMissingFileToUnreadable() {
        ScreenConfigurationImportException failure = assertThrows(ScreenConfigurationImportException.class,
                () -> coordinator().importFrom(temp.resolve("missing.json"), null));
        org.junit.jupiter.api.Assertions.assertEquals(ScreenConfigurationImportException.Reason.UNREADABLE, failure.reason());
    }

    @Test
    void importFromMapsOversizedFileToUnreadable() throws Exception {
        Path path = temp.resolve("oversized.json");
        Files.createFile(path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.position(ScreenConfigurationFiles.MAX_DOCUMENT_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        ScreenConfigurationImportException failure = assertThrows(ScreenConfigurationImportException.class,
                () -> coordinator().importFrom(path, null));
        org.junit.jupiter.api.Assertions.assertEquals(ScreenConfigurationImportException.Reason.UNREADABLE, failure.reason());
    }

    @Test
    void importFromRejectsEmptyFile() throws Exception {
        Path path = temp.resolve("empty.json");
        Files.writeString(path, "");
        assertThrows(IllegalArgumentException.class, () -> coordinator().importFrom(path, null));
    }

    private Path write(String name, String contents) throws Exception {
        Path path = temp.resolve(name);
        ScreenConfigurationFiles.writeAtomic(path, contents);
        return path;
    }

    private ScreenConfigurationCoordinator coordinator() {
        return new ScreenConfigurationCoordinator(() -> null, () -> null, () -> null, () -> null,
                module -> null, operation -> { }, state -> { }, () -> null,
                new DialogService(), I18nService.getInstance(), status -> { });
    }

    private ScreenConfiguration sample() {
        return new ScreenConfiguration("Symmetric Ciphers", "CIPHER", Map.of(), SecretVisibilityProfile.FULL_LAB);
    }
}
