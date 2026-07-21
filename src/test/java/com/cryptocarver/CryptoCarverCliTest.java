package com.cryptocarver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.cryptocarver.model.BuildInfo;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CryptoCarverCliTest {
    @Test void launcherRoutesVersionAndExplicitCliWithoutStartingTheUi() {
        assertArrayEquals(new String[] { "--version" }, Launcher.cliArguments(new String[] { "--version" }));
        assertArrayEquals(new String[] { "sha256", "abc" }, Launcher.cliArguments(new String[] { "--cli", "sha256", "abc" }));
        assertNull(Launcher.cliArguments(new String[] { "sha256", "abc" }));
    }

    @Test void versionUsesTheCentralBuildMetadata() {
        StringWriter output = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "--version" }, new PrintWriter(output), new PrintWriter(new StringWriter())));
        assertEquals("CryptoCarver CLI version " + BuildInfo.version(), output.toString().trim());
    }

    @Test void producesKnownSha256InHumanAndJsonForms() {
        StringWriter plain = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "sha256", "abc" }, new PrintWriter(plain), new PrintWriter(new StringWriter())));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", plain.toString().trim());
        StringWriter json = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "sha256", "abc", "--json" }, new PrintWriter(json), new PrintWriter(new StringWriter())));
        assertTrue(json.toString().contains("\"operation\":\"sha256\""));
    }
    @Test void rejectsUnknownCommandsWithUsageExitCode() {
        assertEquals(2, CryptoCarverCli.run(new String[] { "nope" }, new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter())));
    }
    @Test void rejectsExtraArgumentsAndKeepsErrorsOnStderr() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        assertEquals(CryptoCarverCli.EXIT_INVALID_ARGS,
                CryptoCarverCli.run(new String[] { "sha256", "abc", "--json", "extra" }, new PrintWriter(output), new PrintWriter(error)));
        assertTrue(output.toString().isBlank());
        assertTrue(error.toString().contains("Unexpected positional argument"));
    }
    @Test void runsCsvBatchThroughTheSameSafeOperationEngine() throws Exception {
        Path csv = Files.createTempFile("cryptocarver-cli-", ".csv");
        Files.writeString(csv, "input\nabc\n", StandardCharsets.UTF_8);
        StringWriter output = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "batch", "sha256", csv.toString(), "--format", "csv", "--output", "jsonl" },
                new PrintWriter(output), new PrintWriter(new StringWriter())));
        assertTrue(output.toString().contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        Files.deleteIfExists(csv);
    }
    @Test void batchJsonUsesOneJsonDocument() throws Exception {
        Path csv = Files.createTempFile("cryptocarver-cli-", ".csv");
        Files.writeString(csv, "input\nabc\n", StandardCharsets.UTF_8);
        StringWriter output = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "batch", "sha256", csv.toString(), "--json" },
                new PrintWriter(output), new PrintWriter(new StringWriter())));
        assertTrue(output.toString().contains("\"operation\":\"batch\""));
        Files.deleteIfExists(csv);
    }
}
