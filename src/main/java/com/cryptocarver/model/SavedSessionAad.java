package com.cryptocarver.model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Version-one binding of ciphertext to its session identity and protected structure. */
final class SavedSessionAad {
    static final int VERSION = 1;
    private static final String FORMAT = "CryptoCarver/saved-session";
    private static final String MARKER = "[REDACTED_SECRET]";

    private SavedSessionAad() { }

    static byte[] encode(SavedSession session, boolean protectedTrail) {
        Map<String, Object> state = session.getUiState();
        List<String> keys = state == null ? List.of() : state.entrySet().stream()
                .filter(entry -> MARKER.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.nullsFirst(Comparator.naturalOrder()))
                .toList();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeString(output, FORMAT);
            output.writeInt(VERSION);
            writeString(output, session.getId());
            writeString(output, session.getOperation());
            output.writeInt(keys.size());
            for (String key : keys) {
                writeString(output, key);
            }
            output.writeBoolean(protectedTrail);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to encode saved session binding");
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
