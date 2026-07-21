package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AppSettingsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void testPkcs11ProfilesManagement() throws Exception {
        Path settingsFile = temporaryDirectory.resolve("settings.json");
        AppSettings settings = new AppSettings(settingsFile);

        settings.savePkcs11Profile("TestProfile1", "/opt/hsm/lib1.so", 0);
        settings.savePkcs11Profile("TestProfile2", "/opt/hsm/lib2.so", 1);

        List<Pkcs11Profile> profiles = settings.getPkcs11Profiles();
        assertEquals(2, profiles.size());

        Pkcs11Profile p1 = profiles.stream().filter(p -> p.name().equals("TestProfile1")).findFirst().orElse(null);
        assertNotNull(p1);
        assertEquals("/opt/hsm/lib1.so", p1.library());
        assertEquals(0, p1.slot());

        // Update existing profile
        settings.savePkcs11Profile("TestProfile1", "/opt/hsm/lib1_updated.so", 2);
        profiles = settings.getPkcs11Profiles();
        assertEquals(2, profiles.size());

        p1 = profiles.stream().filter(p -> p.name().equals("TestProfile1")).findFirst().orElse(null);
        assertNotNull(p1);
        assertEquals("/opt/hsm/lib1_updated.so", p1.library());
        assertEquals(2, p1.slot());

        // Remove profile
        settings.removePkcs11Profile("TestProfile2");
        profiles = settings.getPkcs11Profiles();
        assertEquals(1, profiles.size());
        assertTrue(profiles.stream().noneMatch(p -> p.name().equals("TestProfile2")));

        AppSettings reloaded = new AppSettings(settingsFile);
        assertEquals(List.of(new Pkcs11Profile("TestProfile1", "/opt/hsm/lib1_updated.so", 2)),
                reloaded.getPkcs11Profiles());
        assertFalse(Files.readString(settingsFile).toLowerCase().contains("pin"));
    }

    @Test
    public void testPkcs11ProfileValidation() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        assertThrows(IllegalArgumentException.class, () -> settings.savePkcs11Profile("", "/lib.so", 0));
        assertThrows(IllegalArgumentException.class, () -> settings.savePkcs11Profile("Name", "", 0));
        assertThrows(IllegalArgumentException.class, () -> settings.savePkcs11Profile(null, "/lib.so", 0));
        assertThrows(IllegalArgumentException.class, () -> settings.savePkcs11Profile("Name", "/lib.so", -1));
    }
}
