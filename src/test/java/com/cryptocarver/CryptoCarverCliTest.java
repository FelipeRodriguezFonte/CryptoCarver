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

    @Test void tr31BatchRequiresAProtectionKeyEnvironmentVariable() {
        StringWriter error = new StringWriter();
        assertEquals(CryptoCarverCli.EXIT_INVALID_ARGS, CryptoCarverCli.run(
                new String[] {"tr31-batch", "unwrap", "-", "--kbpk-env", "CRYPTOCARVER_TEST_KBPK_MUST_NOT_EXIST"},
                new PrintWriter(new StringWriter()), new PrintWriter(error)));
        assertTrue(error.toString().toLowerCase(java.util.Locale.ROOT).contains("environment variable")
                && error.toString().toLowerCase(java.util.Locale.ROOT).contains("required"), error.toString());
    }

    @Test void tr31BatchContinuesAfterBadRowAndMasksKeyMaterial() throws Exception {
        Path csv = Files.createTempFile("cryptocarver-tr31-batch-", ".csv");
        try {
            String clearKey = "0123456789ABCDEFFEDCBA9876543210";
            Files.writeString(csv, "material,usage,version,algorithm,mode,exportability\n"
                    + clearKey + ",P0,B,T,E,N\nBADHEX,P0,B,T,E,N\n", StandardCharsets.UTF_8);
            ProcessBuilder process = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"),
                    "com.cryptocarver.Launcher", "--cli", "tr31-batch", "wrap", csv.toString(),
                    "--kbpk-env", "CRYPTOCARVER_TEST_KBPK", "--format", "csv", "--output", "jsonl");
            process.environment().put("CRYPTOCARVER_TEST_KBPK", "AB2E09DB3EF0BA71E0CE6CD755C23A3B");
            Process run = process.start();
            String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String error = new String(run.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(CryptoCarverCli.EXIT_OPERATION_FAILED, run.waitFor(), error);
            assertTrue(output.contains("\"row\":1,\"status\":\"ok\""), output);
            assertTrue(output.contains("\"row\":2,\"status\":\"error\""), output);
            assertTrue(output.contains("[REDACTED]"), output);
            assertFalse(output.contains(clearKey), output);
            assertFalse(output.contains("BADHEX"), output);
        } finally {
            Files.deleteIfExists(csv);
        }
    }

    @Test void batchUsesNewCatalogOperationsAndRejectsSecretOperation() throws Exception {
        Path csv = Files.createTempFile("cryptocarver-cli-catalog-", ".csv");
        Files.writeString(csv, "input\nabc\n", StandardCharsets.UTF_8);
        StringWriter output = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "batch", "sha-1", csv.toString(), "--format", "csv", "--output", "jsonl" },
                new PrintWriter(output), new PrintWriter(new StringWriter())));
        assertTrue(output.toString().contains("a9993e364706816aba3e25717850c26c9cd0d89d"));
        StringWriter encoded = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "batch", "utf8-to-base32", csv.toString(), "--format", "csv" },
                new PrintWriter(encoded), new PrintWriter(new StringWriter())));
        assertEquals("MFRGG===", com.google.gson.JsonParser.parseString(encoded.toString())
                .getAsJsonObject().getAsJsonObject("output").get("result").getAsString());
        StringWriter error = new StringWriter();
        assertEquals(CryptoCarverCli.EXIT_INVALID_ARGS, CryptoCarverCli.run(new String[] { "batch", "hmac-sha256", csv.toString() },
                new PrintWriter(new StringWriter()), new PrintWriter(error)));
        assertTrue(error.toString().contains("not available in batch"));
        Files.deleteIfExists(csv);
    }
}
