package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.ResultComparator;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.batch.BatchInputCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Headless contracts for UX-21 data isolation and technical-value fidelity. */
class Ux21HeadlessTest {

    @Test
    void historyAndShelfAreIndependentStores() {
        try {
            Path dir = Files.createTempDirectory("cryptocarver-ux21-");
            HistoryManager history = new HistoryManager(dir.resolve("history.json"));
            ClipboardShelfManager shelf = new ClipboardShelfManager(dir.resolve("shelf.json"));
            history.addHistoryItem(new HistoryCommand("Hashing", "ok", Map.of("algorithm", "SHA-256")));
            ClipboardEntry entry = new ClipboardEntry("technical", "001122", ClipboardEntry.Format.HEX,
                    OperationDetail.Classification.PUBLIC, "Hashing");
            shelf.addEntry(entry);
            history.clearHistory();
            assertTrue(history.getHistoryItems().isEmpty());
            assertEquals(1, shelf.getEntries().size(), "Clear History must not clear Shelf");
            shelf.clear();
            assertTrue(history.getHistoryItems().isEmpty(), "Clear Shelf must not recreate History");
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void shelfDestinationMatrixRejectsWithoutTransforming() {
        ClipboardEntry.Format[] technical = {
                ClipboardEntry.Format.TEXT, ClipboardEntry.Format.HEX,
                ClipboardEntry.Format.BASE64, ClipboardEntry.Format.BASE64URL
        };
        for (ClipboardEntry.Format format : technical) {
            assertTrue(ClipboardShelfController.supportsTarget(format, "HASHING"));
            assertTrue(ClipboardShelfController.supportsTarget(format, "MANUAL_CONVERSION"));
            assertTrue(ClipboardShelfController.supportsTarget(format, "SYMMETRIC_CIPHER"));
        }
        assertTrue(ClipboardShelfController.supportsTarget(ClipboardEntry.Format.TEXT, "XML_SECURITY"));
        assertTrue(ClipboardShelfController.supportsTarget(ClipboardEntry.Format.TEXT, "WSS_SECURITY"));
        assertTrue(ClipboardShelfController.supportsTarget(ClipboardEntry.Format.TEXT, "TR31"));
        assertTrue(ClipboardShelfController.supportsTarget(ClipboardEntry.Format.HEX, "PAYMENTS"));
        assertFalse(ClipboardShelfController.supportsTarget(ClipboardEntry.Format.PEM, "TR31"));
        assertFalse(ClipboardShelfController.supportsTarget(ClipboardEntry.Format.JSON, "PAYMENTS"));
    }

    @Test
    void batchInputCannotCarrySecretNamedColumns() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchInputCodec.parseCsv("input,key\nhello,0011\n"));
        assertThrows(IllegalArgumentException.class,
                () -> BatchInputCodec.parseJsonLines("{\"input\":\"hello\",\"password\":\"x\"}"));
        assertEquals("0011", BatchInputCodec.parseCsv("input,result\nhello,0011\n").get(0).get("result"));
    }

    @Test
    void historyPolicyCoversTr31AndPaymentSecretFamiliesButNotSafeSelectors() {
        for (String field : List.of("tr31KbpkExportField", "cvkField", "pvkField",
                "pinField", "panField", "cvvField", "keyField", "passwordField",
                "secretField", "privateCertificateField", "certInputArea", "certIssueCaKeyArea",
                "certField", "ivField", "nonceField",
                "aadField", "saltField", "tokenField", "macField", "signatureField")) {
            assertTrue(UiStateSnapshot.isHistorySensitiveField(field), field);
        }
        for (String selector : List.of("algorithmCombo", "formatChoice", "modeCombo",
                "keySizeCombo", "usageCombo", "algorithmModeCheck",
                "certAlgorithmCombo", "certFormatCombo", "certKeySizeCombo")) {
            assertFalse(UiStateSnapshot.isHistorySensitiveField(selector), selector);
        }
    }

    @Test
    void technicalFormatsAndResultsRemainByteComparable() {
        ClipboardEntry a = new ClipboardEntry("a", "00AA", ClipboardEntry.Format.HEX,
                OperationDetail.Classification.PUBLIC, "TR-31");
        ClipboardEntry b = new ClipboardEntry("b", "00aa", ClipboardEntry.Format.HEX,
                OperationDetail.Classification.PUBLIC, "TR-31");
        ResultComparator.ComparisonDetails details = ResultComparator.compare(a, b, SecretVisibilityProfile.FULL_LAB);
        assertEquals(ResultComparator.Status.EQUAL, details.status());
        assertEquals(2, details.length1());
        assertEquals(2, details.length2());
        assertEquals("00AA", a.getValue());
        assertEquals(ClipboardEntry.Format.HEX, a.getFormat());

        String keyBlock = "B0096P0TE00E000000000000000000000000000";
        ClipboardEntry tr31 = new ClipboardEntry("tr31", keyBlock, ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.PUBLIC, "TR-31");
        ClipboardEntry hash = new ClipboardEntry("hash", "001122AABB", ClipboardEntry.Format.HEX,
                OperationDetail.Classification.PUBLIC, "Hashing");
        ClipboardEntry bytes = new ClipboardEntry("bytes", "AQID", ClipboardEntry.Format.BASE64,
                OperationDetail.Classification.PUBLIC, "Bytes");
        ClipboardEntry result = new ClipboardEntry("result", "{\"kcv\":\"A1B2C3\"}", ClipboardEntry.Format.JSON,
                OperationDetail.Classification.PUBLIC, "TR-31");
        assertEquals(ResultComparator.Status.EQUAL,
                ResultComparator.compare(tr31, tr31, SecretVisibilityProfile.FULL_LAB).status());
        assertEquals(ResultComparator.Status.EQUAL,
                ResultComparator.compare(hash, hash, SecretVisibilityProfile.FULL_LAB).status());
        assertEquals(ResultComparator.Status.EQUAL,
                ResultComparator.compare(bytes, bytes, SecretVisibilityProfile.FULL_LAB).status());
        assertEquals(ResultComparator.Status.EQUAL,
                ResultComparator.compare(result, result, SecretVisibilityProfile.FULL_LAB).status());
        assertEquals(keyBlock, tr31.getValue());
        assertEquals("001122AABB", hash.getValue());
        assertEquals("AQID", bytes.getValue());
        assertEquals("{\"kcv\":\"A1B2C3\"}", result.getValue());
    }

    @Test
    void ux21FxmlAndTranslationsExposeResetAndDestinationContracts() throws Exception {
        String generic = Files.readString(Path.of("src/main/resources/fxml/generic.fxml"), StandardCharsets.UTF_8);
        String compare = Files.readString(Path.of("src/main/resources/fxml/compare_results.fxml"), StandardCharsets.UTF_8);
        String keys = Files.readString(Path.of("src/main/resources/fxml/keys.fxml"), StandardCharsets.UTF_8);
        assertTrue(generic.contains("onAction=\"#handleResetBatch\""));
        assertTrue(compare.contains("onAction=\"#handleReset\""));
        assertTrue(keys.contains("onAction=\"#handleTR31Clear\""));
        assertTrue(keys.contains("onAction=\"#handleTR31Reset\""));
        String en = Files.readString(Path.of("src/main/resources/i18n/messages.properties"));
        String es = Files.readString(Path.of("src/main/resources/i18n/messages_es.properties"));
        for (String key : List.of("module.shelf.incompatible", "module.compare.emptyHelp",
                "module.batch.reset", "module.keys.tr31ResetStatus")) {
            assertTrue(en.lines().anyMatch(line -> line.startsWith(key + "=")), "EN " + key);
            assertTrue(es.lines().anyMatch(line -> line.startsWith(key + "=")), "ES " + key);
        }
    }
}
