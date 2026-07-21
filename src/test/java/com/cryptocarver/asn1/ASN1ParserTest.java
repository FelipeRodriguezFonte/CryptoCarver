package com.cryptocarver.asn1;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

public class ASN1ParserTest {

    @Test
    public void testLoadCustomOidNames_Invalid() throws Exception {
        Path tempFile = Files.createTempFile("custom_oids_invalid", ".json");
        try {
            // Valid and invalid (but structurally sound JSON)
            String jsonContent = "{\n" +
                    "  \"1.2.3.4.5\": \"Valid OID 1\",\n" +
                    "  \"invalid.oid\": \"Invalid OID\",\n" +
                    "  \"3.2.1\": \"Invalid Root (Must be 0-2)\",\n" +
                    "  \"2.999.1\": \"Valid OID 2\"\n" +
                    "}";
            Files.writeString(tempFile, jsonContent);

            int loaded = ASN1Parser.loadCustomOidNames(tempFile);

            // "invalid.oid" and "3.2.1" should be rejected.
            // "1.2.3.4.5" and "2.999.1" are valid.
            assertEquals(2, loaded);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testLoadCustomOidNames_Duplicate() throws Exception {
        Path tempFile = Files.createTempFile("custom_oids_duplicate", ".json");
        try {
            // Duplicate OIDs (invalid JSON for Gson map parsing)
            String jsonContent = "{\n" +
                    "  \"1.2.3.4.5\": \"Valid OID 1\",\n" +
                    "  \"1.2.3.4.5\": \"Valid OID 1 Duplicate\"\n" +
                    "}";
            Files.writeString(tempFile, jsonContent);

            assertThrows(JsonSyntaxException.class, () -> {
                ASN1Parser.loadCustomOidNames(tempFile);
            });
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
