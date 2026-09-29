package com.cryptocarver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

class JsonTextFormatterTest {
    @Test
    void formatsJsonWithIndentation() {
        assertEquals("{\n  \"value\": 1.0\n}", JsonTextFormatter.format("{\"value\":1}"));
    }

    @Test
    void formatsJsonNull() {
        assertEquals("null", JsonTextFormatter.format("null"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(JsonSyntaxException.class, () -> JsonTextFormatter.format("{"));
    }
}
