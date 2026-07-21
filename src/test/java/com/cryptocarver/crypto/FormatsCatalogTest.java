package com.cryptocarver.crypto;

import com.cryptocarver.codec.ByteFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FormatsCatalogTest {
    @Test
    void catalogListsEveryRegisteredByteFormatAndEbcdicCodePage() throws Exception {
        String catalog = Files.readString(Path.of("docs", "FORMATS_AND_CHARSETS.md"));
        for (ByteFormat format : ByteFormat.values()) {
            assertTrue(catalog.contains(format.getDisplayName()), "Missing byte format: " + format.getDisplayName());
        }
        for (String codePage : EBCDICConverter.supportedCodePages().keySet()) {
            assertTrue(catalog.contains(codePage), "Missing EBCDIC code page: " + codePage);
        }
    }
}
