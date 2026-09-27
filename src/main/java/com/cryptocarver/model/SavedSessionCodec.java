package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/** JSON serialization for saved workspaces, kept independent of JavaFX. */
public final class SavedSessionCodec {
    private static final Type SESSION_LIST_TYPE = new TypeToken<List<SavedSession>>() { }.getType();
    private final Gson writer = new GsonBuilder().setPrettyPrinting().create();
    private final Gson reader = new Gson();

    public String serialize(List<SavedSession> sessions) {
        return writer.toJson(sessions == null ? List.of() : sessions);
    }

    /** Reads the current list format; malformed JSON propagates Gson's parse exception. */
    public List<SavedSession> deserialize(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<SavedSession> parsed = reader.fromJson(json, SESSION_LIST_TYPE);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }
}
