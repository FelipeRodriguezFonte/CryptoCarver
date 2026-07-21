package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StreamingFileToolsTest {
    @Test
    void hashesAndFindsFirstDifferenceWithoutMaterializingFileContent() throws Exception {
        Path left = Files.createTempFile("cryptocarver-left", ".bin");
        Path right = Files.createTempFile("cryptocarver-right", ".bin");
        try {
            Files.write(left, new byte[] {1, 2, 3, 4});
            Files.write(right, new byte[] {1, 2, 9, 4});
            assertEquals("9F64A747E1B97F131FABB6B447296C9B6F0201E79FB3C5356E6C77E89B6A806A", StreamingFileTools.hash(left, "SHA-256", com.cryptocarver.util.ProgressMonitor.NO_OP));
            assertEquals(2, StreamingFileTools.firstDifference(left, right, com.cryptocarver.util.ProgressMonitor.NO_OP));
            assertArrayEquals(new byte[] {1, 2}, StreamingFileTools.preview(left, 2, com.cryptocarver.util.ProgressMonitor.NO_OP));
        } finally {
            Files.deleteIfExists(left);
            Files.deleteIfExists(right);
        }
    }
}
