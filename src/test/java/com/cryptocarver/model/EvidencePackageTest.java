package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidencePackageTest {
    private static final String SECRET_HEX = "00112233445566778899aabbccddeeff";
    private final EvidencePackageCodec codec = new EvidencePackageCodec();

    private static OperationSessionLog log() {
        OperationSessionLog log = new OperationSessionLog();
        OperationResult result = OperationResult.forOperation("AES Encrypt")
                .input("hola".getBytes(StandardCharsets.UTF_8))
                .output(new byte[] {1, 2, 3}, OperationDetail.Classification.PUBLIC)
                .status("done")
                .build();
        log.add(result, "Step one", List.of(), Map.of("key", SECRET_HEX));
        log.add(result, "Step two", List.of(), Map.of());
        return log;
    }

    private static EvidencePackage evidence(boolean clearText) {
        return EvidencePackage.create(new ExerciseSession("Ana Pérez", "LAB-1", "2026-11-02 10:00:00"), log(),
                "2026-11-02 11:00:00", "2.4.0", clearText);
    }

    @Test
    void publicExportOmitsScreenParametersAndStaysVerifiable() throws IOException {
        String json = codec.serialize(evidence(false));
        assertFalse(json.contains(SECRET_HEX));
        EvidencePackage read = codec.deserialize(json);
        assertEquals(EvidencePackage.Integrity.VALID, read.verify());
        assertEquals("Ana Pérez", read.getStudent());
        assertEquals(2, read.getSteps().size());
        assertFalse(read.isClearTextValues());
    }

    @Test
    void clearTextIsOptIn() throws IOException {
        String json = codec.serialize(evidence(true));
        assertTrue(json.contains(SECRET_HEX));
        assertEquals(EvidencePackage.Integrity.VALID, codec.deserialize(json).verify());
    }

    @Test
    void editingAStepBreaksTheChain() throws IOException {
        String json = codec.serialize(evidence(false)).replace("Step one", "Step uno");
        assertEquals(EvidencePackage.Integrity.CHAIN_BROKEN, codec.deserialize(json).verify());
    }

    @Test
    void editingTheHeaderBreaksTheSeal() throws IOException {
        String json = codec.serialize(evidence(false)).replace("Ana Pérez", "Otra Persona");
        assertEquals(EvidencePackage.Integrity.SEAL_MISMATCH, codec.deserialize(json).verify());
    }

    @Test
    void foreignFilesAreRejected() {
        assertThrows(IOException.class, () -> codec.deserialize("{\"format\":\"other\"}"));
        assertThrows(IOException.class, () -> codec.deserialize("not json"));
        assertThrows(IOException.class, () -> codec.deserialize(
                "{\"format\":\"cryptocarver-evidence\",\"formatVersion\":99}"));
    }

    @Test
    void folderReviewKeepsUnreadableFilesVisible(@TempDir Path folder) throws IOException {
        codec.write(evidence(false), folder.resolve("a.ccevidence"));
        Files.writeString(folder.resolve("b.ccevidence"), "broken");
        Files.writeString(folder.resolve("ignored.txt"), "x");
        List<EvidencePackageCodec.Entry> entries = codec.loadFolder(folder);
        assertEquals(2, entries.size());
        assertEquals(EvidencePackage.Integrity.VALID, entries.get(0).integrity());
        assertFalse(entries.get(1).readable());
    }

    @Test
    void writeAndReadUseTheSameUtf8ByteLimit(@TempDir Path folder) throws IOException {
        EvidencePackage value = evidence(false);
        byte[] serialized = codec.serialize(value).getBytes(StandardCharsets.UTF_8);
        Path file = folder.resolve("limited.ccevidence");

        new EvidencePackageCodec(serialized.length).write(value, file);
        assertEquals(EvidencePackage.Integrity.VALID, new EvidencePackageCodec(serialized.length).load(file).integrity());

        Files.write(file, serialized);
        EvidencePackageCodec tooSmall = new EvidencePackageCodec(serialized.length - 1);
        assertFalse(tooSmall.load(file).readable());
        Files.writeString(file, "original destination", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> tooSmall.write(value, file));
        assertEquals("original destination", Files.readString(file, StandardCharsets.UTF_8));
        try (var files = Files.list(folder)) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void writeLimitCountsUtf8BytesRatherThanCharacters(@TempDir Path folder) throws IOException {
        String json = codec.serialize(evidence(false));
        assertTrue(json.chars().anyMatch(character -> character > 127));
        int utf8Bytes = json.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(utf8Bytes > json.length());
        Path target = folder.resolve("utf8.ccevidence");
        assertThrows(IOException.class, () -> new EvidencePackageCodec(utf8Bytes - 1).write(evidence(false), target));
        assertFalse(Files.exists(target));
    }

    @Test
    void boundedReadRejectsFilesBeyondTheLimit(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("growing.ccevidence");
        Files.writeString(file, codec.serialize(evidence(false)), StandardCharsets.UTF_8);
        assertFalse(new EvidencePackageCodec(8).load(file).readable());
    }

    @Test
    void malformedUtf8IsRejected(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("malformed.ccevidence");
        Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});
        assertFalse(codec.load(file).readable());
    }

    @Test
    void oversizedStreamingWriteReportsAnIoErrorAndPreservesTheDestination(@TempDir Path folder) throws IOException {
        OperationSessionLog large = new OperationSessionLog();
        large.add(OperationResult.forOperation("Large result")
                .enrichedOutput("x".repeat(32_768), OperationDetail.Classification.PUBLIC).build(),
                "Large", List.of());
        EvidencePackage value = EvidencePackage.create(new ExerciseSession("Ana", "LAB", "start"),
                large, "end", "test", false);
        Path target = folder.resolve("large.ccevidence");
        Files.writeString(target, "original destination");
        assertThrows(IOException.class, () -> new EvidencePackageCodec(8_192).write(value, target));
        assertEquals("original destination", Files.readString(target));
        try (var files = Files.list(folder)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void suggestedFileNameIsSafe() {
        String name = new ExerciseSession("../Ana Pérez", "LAB 1", "t").suggestedFileName();
        assertFalse(name.contains("/"));
        assertTrue(name.endsWith(EvidencePackageCodec.EXTENSION));
    }
}
