package com.cryptoforge;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CryptoCarverCliTest {
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
    @Test void runsCsvBatchThroughTheSameSafeOperationEngine() throws Exception {
        Path csv = Files.createTempFile("cryptocarver-cli-", ".csv");
        Files.writeString(csv, "input\nabc\n", StandardCharsets.UTF_8);
        StringWriter output = new StringWriter();
        assertEquals(0, CryptoCarverCli.run(new String[] { "batch", "sha256", csv.toString(), "--format", "csv", "--output", "jsonl" },
                new PrintWriter(output), new PrintWriter(new StringWriter())));
        assertTrue(output.toString().contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        Files.deleteIfExists(csv);
    }
}
