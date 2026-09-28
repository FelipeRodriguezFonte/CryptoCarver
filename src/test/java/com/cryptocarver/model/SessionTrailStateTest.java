package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionTrailStateTest {
    @Test void addAndNavigateIncludingUnsavedCurrentResult() {
        SessionTrailState state = new SessionTrailState();
        state.resultPublished();
        state.add(result("one"), "one", List.of(), Map.of());
        state.resultPublished();
        state.add(result("two"), "two", List.of(), Map.of());
        assertEquals(1, state.selectedIndex());
        state.resultPublished();
        assertEquals(1, state.previous());
        assertEquals(0, state.previous());
        assertEquals(0, state.previous());
        assertEquals(1, state.next());
        assertEquals(-1, state.next());
        assertTrue(state.hasUnsavedResult());
    }

    @Test void selectClearAndReplaceResetNavigationState() {
        SessionTrailState state = new SessionTrailState();
        state.add(result("one"), "one", List.of(), Map.of());
        assertTrue(state.select(0));
        assertFalse(state.select(1));
        state.resultPublished();
        OperationSessionLog replacement = new OperationSessionLog();
        replacement.add(result("loaded"), "loaded", List.of());
        state.replace(replacement);
        assertEquals(1, state.size());
        assertEquals(-1, state.selectedIndex());
        assertTrue(state.hasUnsavedResult());
        assertEquals("loaded", state.steps().get(0).getTitle());
        state.clear();
        assertEquals(0, state.size());
        assertEquals(-1, state.selectedIndex());
    }

    private static OperationResult result(String value) {
        return OperationResult.forOperation(value).output(value.getBytes()).build();
    }
}
