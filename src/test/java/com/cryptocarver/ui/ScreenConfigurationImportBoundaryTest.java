package com.cryptocarver.ui;

import com.cryptocarver.model.ScreenConfiguration;
import com.cryptocarver.model.ScreenConfigurationCodec;
import com.cryptocarver.model.ScreenConfigurationFiles;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenConfigurationImportBoundaryTest {
    private static final String PASSWORD = "SYNTHETIC-PASSWORD";
    @TempDir Path temp;

    @Test
    void importFromAcceptsPlainAndEncryptedFilesWithoutJavaFx() throws Exception {
        ScreenConfigurationCoordinator coordinator = coordinator();
        Path plain = temp.resolve("plain.json");
        ScreenConfigurationFiles.writeAtomic(plain, ScreenConfigurationCodec.encodePlain(sample()));
        Path encrypted = temp.resolve("encrypted.ccconfig");
        ScreenConfigurationFiles.writeAtomic(encrypted,
                ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray()));
        assertDoesNotThrow(() -> coordinator.importFrom(plain, null));
        assertDoesNotThrow(() -> coordinator.importFrom(encrypted, PASSWORD.toCharArray()));
    }

    @Test
    void importFromRejectsWrongPasswordAndModifiedCiphertext() throws Exception {
        ScreenConfigurationCoordinator coordinator = coordinator();
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray());
        Path path = temp.resolve("encrypted.ccconfig");
        ScreenConfigurationFiles.writeAtomic(path, encrypted);
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.importFrom(path, "SYNTHETIC-WRONG".toCharArray()));
        int index = encrypted.indexOf("\"ciphertext\": \"") + "\"ciphertext\": \"".length();
        char original = encrypted.charAt(index);
        String modified = encrypted.substring(0, index) + (original == 'A' ? 'B' : 'A') + encrypted.substring(index + 1);
        ScreenConfigurationFiles.writeAtomic(path, modified);
        assertThrows(IllegalArgumentException.class, () -> coordinator.importFrom(path, PASSWORD.toCharArray()));
    }

    @Test
    void importFromRejectsNonConfigurationJsonUnsupportedVersionAndEmptyFile() throws Exception {
        ScreenConfigurationCoordinator coordinator = coordinator();
        Path path = temp.resolve("bad.json");
        ScreenConfigurationFiles.writeAtomic(path, "not json");
        assertThrows(IllegalArgumentException.class, () -> coordinator.importFrom(path, null));
        ScreenConfigurationFiles.writeAtomic(path, "{\"hello\":\"world\"}");
        assertThrows(IllegalArgumentException.class, () -> coordinator.importFrom(path, null));
        String unsupported = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 99");
        ScreenConfigurationFiles.writeAtomic(path, unsupported);
        assertThrows(IllegalArgumentException.class, () -> coordinator.importFrom(path, null));
        Path empty = temp.resolve("empty.json");
        java.nio.file.Files.writeString(empty, "");
        assertThrows(IllegalArgumentException.class, () -> coordinator.importFrom(empty, null));
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
