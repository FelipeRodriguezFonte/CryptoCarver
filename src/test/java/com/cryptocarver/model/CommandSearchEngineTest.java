package com.cryptocarver.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandSearchEngine Ranking & Filtering Tests")
class CommandSearchEngineTest {

    private CommandItem cmdAes;
    private CommandItem cmdHash;
    private CommandItem cmdJwt;
    private CommandItem cmdDisabled;
    private List<CommandItem> allCommands;

    @BeforeEach
    void setUp() {
        cmdAes = new CommandItem(
                "nav_cipher",
                "Symmetric Ciphers (AES / DES)",
                "Navigation",
                "AES encryption laboratory",
                Arrays.asList("aes", "cipher", "gcm", "cbc"),
                null,
                () -> true,
                () -> {}
        );

        cmdHash = new CommandItem(
                "nav_hash",
                "Hashing (SHA-256 / SHA-512)",
                "Navigation",
                "Cryptographic hash algorithms",
                Arrays.asList("hash", "sha256", "digest"),
                null,
                () -> true,
                () -> {}
        );

        cmdJwt = new CommandItem(
                "nav_jwt",
                "JWT (Signed Tokens)",
                "Navigation",
                "JSON Web Tokens and JWE",
                Arrays.asList("jwt", "jose", "token"),
                null,
                () -> true,
                () -> {}
        );

        cmdDisabled = new CommandItem(
                "action_copy",
                "Copy Output",
                "Actions",
                "Copy current output to clipboard",
                Arrays.asList("copy", "output"),
                "Ctrl+C",
                () -> false, // Disabled state
                () -> {}
        );

        allCommands = Arrays.asList(cmdAes, cmdHash, cmdJwt, cmdDisabled);
    }

    @Test
    @DisplayName("Empty query returns all commands in original order")
    void testEmptyQueryReturnsAll() {
        List<CommandItem> results = CommandSearchEngine.search(allCommands, "");
        assertEquals(4, results.size());
        assertEquals("nav_cipher", results.get(0).getId());
        assertEquals("nav_hash", results.get(1).getId());
    }

    @Test
    @DisplayName("Case-insensitive title start query ranks highest")
    void testTitleStartRanksHighest() {
        List<CommandItem> results = CommandSearchEngine.search(allCommands, "hash");
        assertFalse(results.isEmpty());
        assertEquals("nav_hash", results.get(0).getId());
    }

    @Test
    @DisplayName("Keyword match filters correctly")
    void testKeywordMatch() {
        List<CommandItem> results = CommandSearchEngine.search(allCommands, "sha256");
        assertEquals(1, results.size());
        assertEquals("nav_hash", results.get(0).getId());
    }

    @Test
    @DisplayName("Deterministic ordering preserved for ties")
    void testDeterministicOrder() {
        List<CommandItem> results1 = CommandSearchEngine.search(allCommands, "nav");
        List<CommandItem> results2 = CommandSearchEngine.search(allCommands, "nav");

        assertEquals(results1.size(), results2.size());
        for (int i = 0; i < results1.size(); i++) {
            assertEquals(results1.get(i).getId(), results2.get(i).getId());
        }
    }

    @Test
    @DisplayName("Disabled supplier evaluates isEnabled() correctly")
    void testDisabledSupplier() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        CommandItem cmd = new CommandItem(
                "dynamic",
                "Dynamic Action",
                "Actions",
                "Dynamic description",
                Arrays.asList("dynamic"),
                null,
                enabled::get,
                () -> {}
        );

        assertFalse(cmd.isEnabled());
        enabled.set(true);
        assertTrue(cmd.isEnabled());
    }
}
