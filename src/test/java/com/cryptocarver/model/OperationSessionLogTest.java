package com.cryptocarver.model;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OperationSessionLogTest {

    @Test
    void addsOrderedStepsAndLinksEveryEntryToThePreviousHash() {
        OperationSessionLog log = new OperationSessionLog();

        SessionOperationStep first = log.add(result("Generate Key", "first"), "Generated lab key",
                List.of("keys", "lab"));
        SessionOperationStep second = log.add(result("Calculate MAC", "second"), "MAC calculation",
                List.of("mac"));

        assertEquals(2, log.size());
        assertEquals(List.of("Generated lab key", "MAC calculation"),
                log.getSteps().stream().map(SessionOperationStep::getTitle).toList());
        assertEquals(SessionOperationStep.GENESIS_HASH, first.getPreviousHash());
        assertEquals(first.getEntryHash(), second.getPreviousHash());
        assertTrue(log.verifyChain());
    }

    @Test
    void preservesAndExportsAllPayloadsAndClassifiedDetailsInClearText() {
        byte[] secretInput = "RAW-SECRET-KEY".getBytes(StandardCharsets.UTF_8);
        byte[] secretOutput = "RAW-SECRET-MAC".getBytes(StandardCharsets.UTF_8);
        OperationResult result = OperationResult.forOperation("Calculate MAC")
                .input(secretInput)
                .output(secretOutput, OperationDetail.Classification.SECRET)
                .detail(OperationDetail.publicDetail("Algorithm", "HMAC-SHA256"))
                .detail(OperationDetail.sensitiveDetail("Key reference", "SENSITIVE-KEY-VALUE"))
                .detail(OperationDetail.secretDetail("Raw key", "TOP-SECRET-VALUE"))
                .enrichedOutput("FULL-ENRICHED-SECRET", OperationDetail.Classification.SECRET)
                .status("MAC calculated")
                .build();

        OperationSessionLog log = new OperationSessionLog();
        SessionOperationStep step = log.add(result, "MAC", List.of(), Map.of(
                "AuthenticationController.authMacKeyField", "00112233445566778899AABBCCDDEEFF",
                "AuthenticationController.authInputArea", "raw message"));
        String export = log.toText();

        assertEquals("HMAC-SHA256", step.getDetails().get(0).value());
        assertEquals("SENSITIVE-KEY-VALUE", step.getDetails().get(1).value());
        assertEquals("TOP-SECRET-VALUE", step.getDetails().get(2).value());
        assertEquals("RAW-SECRET-KEY", step.getInputText());
        assertEquals("RAW-SECRET-MAC", step.getOutputText());
        assertEquals("FULL-ENRICHED-SECRET", step.getEnrichedOutput());
        assertTrue(export.contains("WARNING: UNSAFE CLEAR-TEXT LABORATORY RECORD"));
        assertTrue(export.contains("UTF-8: RAW-SECRET-KEY"));
        assertTrue(export.contains("UTF-8: RAW-SECRET-MAC"));
        assertTrue(export.contains("SENSITIVE-KEY-VALUE"));
        assertTrue(export.contains("TOP-SECRET-VALUE"));
        assertTrue(export.contains("FULL-ENRICHED-SECRET"));
        assertTrue(export.contains("00112233445566778899AABBCCDDEEFF"));
        assertTrue(export.contains("raw message"));
        assertEquals("00112233445566778899AABBCCDDEEFF",
                step.getParameters().get("AuthenticationController.authMacKeyField"));
        assertTrue(export.contains("Output [SECRET]"));
        assertTrue(export.contains("Chain: VALID"));
    }

    @Test
    void removingAStepRelinksTheRemainingChain() {
        OperationSessionLog log = new OperationSessionLog();
        SessionOperationStep first = log.add(result("One", "1"), "First", List.of());
        log.add(result("Two", "2"), "Second", List.of());
        SessionOperationStep third = log.add(result("Three", "3"), "Third", List.of());

        assertTrue(log.remove(first.getId()));

        assertEquals(2, log.size());
        assertEquals(SessionOperationStep.GENESIS_HASH, log.getSteps().get(0).getPreviousHash());
        assertNotEquals(third.getEntryHash(), log.getSteps().get(1).getEntryHash());
        assertTrue(log.verifyChain());
    }

    @Test
    void preservesArbitraryBinaryPayloadsLosslesslyAsHex() {
        OperationResult result = OperationResult.forOperation("Binary operation")
                .input(new byte[] {0x00, (byte) 0xFF, 0x10})
                .output(new byte[] {(byte) 0x80, 0x01}, OperationDetail.Classification.SECRET)
                .build();

        SessionOperationStep step = new OperationSessionLog().add(result, "Binary", List.of());

        assertEquals("00FF10", step.getInputHex());
        assertEquals("8001", step.getOutputHex());
        assertNull(step.getInputText());
        assertNull(step.getOutputText());
    }

    @Test
    void survivesJsonPersistenceInsideASavedSession() {
        OperationSessionLog original = new OperationSessionLog();
        original.add(result("Generate Key", "key"), "Generated key", List.of("lab"));
        SavedSession saved = new SavedSession("Demo", "Symmetric Keys", Map.of("format", "HEX"), original);

        String json = new Gson().toJson(saved);
        SavedSession restored = new Gson().fromJson(json, SavedSession.class);

        assertTrue(json.contains("key"), "Saved-session JSON must intentionally contain the clear-text input");
        assertTrue(json.contains("key-out"), "Saved-session JSON must intentionally contain the clear-text output");
        assertNotNull(restored.getOperationLog());
        assertEquals(1, restored.getOperationLog().size());
        assertEquals("Generated key", restored.getOperationLog().getSteps().get(0).getTitle());
        assertTrue(restored.getOperationLog().verifyChain());
    }

    @Test
    void copiesAreIndependentAndTextOutputIsStable() {
        OperationSessionLog original = new OperationSessionLog();
        original.add(result("Hash", "payload"), "Hash payload", List.of("review"));
        OperationSessionLog copy = original.copy();

        String firstExport = copy.toText();
        assertEquals(firstExport, copy.toText());
        copy.clear();

        assertEquals(1, original.size());
        assertEquals(0, copy.size());
    }

    private static OperationResult result(String operation, String value) {
        return OperationResult.forOperation(operation)
                .input(value.getBytes(StandardCharsets.UTF_8))
                .output((value + "-out").getBytes(StandardCharsets.UTF_8))
                .detail("Algorithm", "TEST")
                .status("Completed")
                .build();
    }
}
