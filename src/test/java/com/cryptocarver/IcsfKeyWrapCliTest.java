package com.cryptocarver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The key wrapping verbs reachable without opening a window.
 *
 * <p>The point of having them on the command line is automation: exporting a run of keys
 * from a script, or piping a verdict into something else. So the tests here check what a
 * script depends on -- exit codes, machine-readable verdict codes, and a token that
 * survives a round trip through two separate invocations -- rather than the prose.</p>
 */
class IcsfKeyWrapCliTest {

    private static final String KEY_16 = "0123456789ABCDEFFEDCBA9876543210";
    private static final String KEK_16 = "404142434445464748494A4B4C4D4E4F";

    /** Runs the CLI and returns stdout, asserting the exit code. */
    private static String run(int expectedExit, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exit = CryptoCarverCli.run(args, new PrintWriter(out), new PrintWriter(err));
        assertEquals(expectedExit, exit, "exit code; stderr was: " + err);
        return out.toString();
    }

    private static JsonObject json(int expectedExit, String... args) {
        return JsonParser.parseString(run(expectedExit, args).trim()).getAsJsonObject();
    }

    @Test
    void exportProducesATokenAndImportGetsTheKeyBack() {
        JsonObject exported = json(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16,
                "--type", "EXPORTER", "--json");
        assertTrue(exported.get("ok").getAsBoolean(), "export should succeed");
        assertEquals("EXPORT", exported.get("operation").getAsString());

        String token = exported.getAsJsonObject("outputs").get("token").getAsString();
        assertEquals(128, token.length(), "an external token is 64 bytes");

        // A second, separate invocation: this is the flow a script actually performs.
        JsonObject imported = json(0, "icsf-import", "--token", token, "--kek", KEK_16, "--json");
        assertTrue(imported.get("ok").getAsBoolean(), "import should succeed");
        assertEquals(KEY_16, imported.getAsJsonObject("outputs").get("key").getAsString(),
                "the key must survive the round trip through the command line");
    }

    @Test
    void exportDefaultsToTheVersionByteRealHostsWrite() {
        // Comparing against a host token is the reason to run this, so the host form is the
        // default and the Table 616 form is the one that has to be asked for.
        String hostForm = json(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16, "--json")
                .getAsJsonObject("outputs").get("token").getAsString();
        String tableForm = json(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16,
                "--table616-version", "--json").getAsJsonObject("outputs").get("token").getAsString();

        assertEquals("00", hostForm.substring(8, 10), "byte 4 should be X'00' by default");
        assertEquals("01", tableForm.substring(8, 10), "--table616-version should give X'01'");

        // Only byte 4 and the TVV that sums it may move; the cryptogram is the same.
        assertEquals(hostForm.substring(32, 64), tableForm.substring(32, 64), "cryptogram");
    }

    @Test
    void resolveNamesTheSchemeInAMachineReadableCode() {
        String token = json(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16, "--json")
                .getAsJsonObject("outputs").get("token").getAsString();

        JsonObject resolved = json(0, "icsf-resolve", "--token", token, "--kek", KEK_16,
                "--expected-key", KEY_16, "--json");
        JsonObject best = resolved.getAsJsonArray("candidates").get(0).getAsJsonObject();

        assertEquals("MATCHES_KEY", best.get("verdict").getAsString(),
                "the verdict must be a code a script can branch on, not a sentence");
        assertEquals(KEY_16, best.get("key").getAsString());
        assertNotNull(best.get("schemeCode"), "the scheme needs a stable identifier too");
    }

    @Test
    void resolveFindsAKeyWrappedWithoutAVariant() {
        String token = json(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16,
                "--variant", "nocv", "--json").getAsJsonObject("outputs").get("token").getAsString();

        JsonObject resolved = json(0, "icsf-resolve", "--token", token, "--kek", KEK_16,
                "--expected-key", KEY_16, "--json");
        JsonObject best = resolved.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertEquals("MATCHES_KEY", best.get("verdict").getAsString());
    }

    @Test
    void inspectReadsATokenWithoutAKekAndYieldsNoKey() {
        String token = json(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16, "--json")
                .getAsJsonObject("outputs").get("token").getAsString();

        JsonObject inspected = json(0, "icsf-inspect", "--token", token, "--json");
        assertTrue(inspected.get("ok").getAsBoolean());
        assertFalse(inspected.getAsJsonObject("outputs").has("key"),
                "an enciphered token must not yield a key without a KEK");
    }

    @Test
    void aMissingKeyFailsWithANonZeroExitAndAReason() {
        JsonObject result = json(CryptoCarverCli.EXIT_OPERATION_FAILED,
                "icsf-export", "--kek", KEK_16, "--json");
        assertFalse(result.get("ok").getAsBoolean());
        assertTrue(result.get("error").getAsString().length() > 0, "the refusal must say why");
    }

    @Test
    void unknownFlagsAndUnknownSchemeNamesAreRejected() {
        assertEquals(CryptoCarverCli.EXIT_INVALID_ARGS,
                CryptoCarverCli.run(new String[] {"icsf-export", "--key", KEY_16, "--invented", "x"},
                        new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter())));
        assertEquals(CryptoCarverCli.EXIT_INVALID_ARGS,
                CryptoCarverCli.run(new String[] {"icsf-export", "--key", KEY_16, "--kek", KEK_16,
                        "--variant", "nonsense"},
                        new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter())));
        assertEquals(CryptoCarverCli.EXIT_INVALID_ARGS,
                CryptoCarverCli.run(new String[] {"icsf-export", "--key", KEY_16, "--kek", KEK_16,
                        "--mode", "nonsense"},
                        new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter())));
    }

    @Test
    void theReportReadsInEitherLanguage() {
        String spanish = run(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16, "--lang", "es");
        String english = run(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16, "--lang", "en");
        assertTrue(spanish.contains("Clave a exportar"), "Spanish report");
        assertTrue(english.contains("Key to export"), "English report");
        assertFalse(spanish.equals(english), "the two languages must actually differ");
    }

    @Test
    void outWritesTheSameReportItPrinted(@TempDir Path directory) throws Exception {
        Path destination = directory.resolve("report.txt");
        String printed = run(0, "icsf-export", "--key", KEY_16, "--kek", KEK_16,
                "--out", destination.toString());
        assertTrue(Files.exists(destination), "--out should have written the file");
        assertEquals(printed.trim(), Files.readString(destination).trim(),
                "the saved report must be what was printed");
    }

    @Test
    void helpListsTheNewCommands() {
        String help = run(0, "help", "--json");
        for (String command : new String[] {"icsf-export", "icsf-import", "icsf-inspect", "icsf-resolve"}) {
            assertTrue(help.contains(command), "help should list " + command);
        }
    }
}
