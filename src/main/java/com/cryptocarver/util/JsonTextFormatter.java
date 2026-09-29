package com.cryptocarver.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Pure JSON parsing and pretty-printing for text input. */
public final class JsonTextFormatter {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson PARSING_GSON = new Gson();

    private JsonTextFormatter() {
    }

    public static String format(String input) {
        Object json = PARSING_GSON.fromJson(input, Object.class);
        return PRETTY_GSON.toJson(json);
    }
}
