package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsTsaPrivacyTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({
            "https://invented:password@tsa.invalid/tsr, https://tsa.invalid/tsr",
            "https://invented@tsa.invalid/tsr, https://tsa.invalid/tsr",
            "https://tsa.invalid/tsr, https://tsa.invalid/tsr",
            "HTTP://invented:password@tsa.invalid:8443/a%2Fb/tsr?q=a%20b&x=%2F#part, HTTP://tsa.invalid:8443/a%2Fb/tsr?q=a%20b&x=%2F#part",
            "https://invented:password@[2001:db8::1]:8443/tsr?q=%2F, https://[2001:db8::1]:8443/tsr?q=%2F",
            "https://invented%40user:pa%3Ass@tsa.invalid/tsr, https://tsa.invalid/tsr",
            "not an analysable URL @ text, not an analysable URL @ text"
    })
    void bothSettingsWritePathsPreserveEverythingExceptUserInfo(String input, String expected) throws Exception {
        for (SecretVisibilityProfile visibility : SecretVisibilityProfile.values()) {
            Path file = directory.resolve(visibility.name() + ".json");
            AppSettings settings = new AppSettings(file);
            settings.setSecretVisibilityProfile(visibility);
            assertDoesNotThrow(() -> settings.setCustomTsaUrl("  " + input + "  "));
            assertDoesNotThrow(() -> settings.saveTsaProfile("  Invented TSA  ", "  " + input + "  "));
            assertEquals(expected, settings.getCustomTsaUrl());
            assertEquals(new AppSettings.TsaProfile("Invented TSA", expected), settings.getTsaProfiles().get(0));
            AppSettings fromDisk = new AppSettings(file);
            assertEquals(expected, fromDisk.getCustomTsaUrl());
            assertEquals(settings.getTsaProfiles(), fromDisk.getTsaProfiles());
            var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertEquals(expected, json.get("customTsaUrl").getAsString());
            assertEquals(expected, json.getAsJsonArray("tsaProfiles").get(0).getAsJsonObject().get("url").getAsString());
        }
    }

    @Test
    void oldSettingsAreSanitizedInMemoryThenRewrittenOnTheNextSave() throws Exception {
        Path file = directory.resolve("legacy.json");
        String legacy = """
                {
                  "customTsaUrl":"https://invented:old-secret@[2001:db8::1]:8443/a%2Fb?q=%2F",
                  "tsaProfiles":[
                    {"name":"Legacy","url":"https://invented:old-secret@tsa.invalid:8443/tsr?q=x"},
                    {"name":"User only","url":"https://invented@tsa.invalid/tsr"},
                    {"name":"Public","url":"https://tsa.invalid/tsr"},
                    {"name":"Unparseable","url":"not a URL @ text"}
                  ]
                }
                """;
        Files.writeString(file, legacy);
        AppSettings loaded = assertDoesNotThrow(() -> new AppSettings(file));
        assertEquals("https://[2001:db8::1]:8443/a%2Fb?q=%2F", loaded.getCustomTsaUrl());
        assertEquals("https://tsa.invalid:8443/tsr?q=x", loaded.getTsaProfiles().get(0).url());
        assertEquals("https://tsa.invalid/tsr", loaded.getTsaProfiles().get(1).url());
        assertEquals("https://tsa.invalid/tsr", loaded.getTsaProfiles().get(2).url());
        assertEquals("not a URL @ text", loaded.getTsaProfiles().get(3).url());
        assertEquals(legacy, Files.readString(file), "loading alone leaves rewriting to the next save");
        loaded.setClipboardClearSeconds(45);
        assertFalse(Files.readString(file).contains("old-secret"));
        assertFalse(Files.readString(file).contains("invented@"));
        AppSettings reloaded = new AppSettings(file);
        assertEquals(loaded.getCustomTsaUrl(), reloaded.getCustomTsaUrl());
        assertEquals(loaded.getTsaProfiles(), reloaded.getTsaProfiles());
    }
}
