package com.cryptocarver.model;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Small local repository for reusable PKCS#11 provider profiles.
 *
 * <p>The on-disk format is intentionally narrower than the application
 * settings file. Only {@code schemaVersion}, {@code name}, {@code library}
 * and {@code slotListIndex} are ever emitted. Unknown JSON properties are
 * ignored and are never copied into a subsequent save.</p>
 */
public final class Pkcs11ProfileRepository {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final Path file;
    private final AtomicMover atomicMover;
    private List<Pkcs11Profile> profiles = List.of();

    public Pkcs11ProfileRepository(Path file) {
        this(file, Pkcs11ProfileRepository::moveAtomically);
    }

    /** Package-private seam for deterministic atomic-save tests. */
    Pkcs11ProfileRepository(Path file, AtomicMover atomicMover) {
        this.file = Objects.requireNonNull(file, "PKCS#11 profile file is required")
                .toAbsolutePath().normalize();
        this.atomicMover = Objects.requireNonNull(atomicMover, "Atomic mover is required");
        reload();
    }

    public Path file() {
        return file;
    }

    public synchronized List<Pkcs11Profile> list() {
        return profiles;
    }

    public synchronized Optional<Pkcs11Profile> find(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String key = Pkcs11Profile.normalizedNameKey(name);
        return profiles.stream()
                .filter(profile -> Pkcs11Profile.normalizedNameKey(profile.name()).equals(key))
                .findFirst();
    }

    public synchronized void create(Pkcs11Profile profile) {
        Pkcs11Profile validated = requireProfile(profile);
        ensureNameAvailable(validated, -1);
        List<Pkcs11Profile> next = new ArrayList<>(profiles);
        next.add(validated);
        persistAndReplace(next);
    }

    public synchronized void update(String currentName, Pkcs11Profile replacement) {
        Pkcs11Profile validated = requireProfile(replacement);
        int index = indexOf(currentName);
        if (index < 0) {
            throw new IllegalArgumentException("PKCS#11 profile to update was not found");
        }
        ensureNameAvailable(validated, index);
        List<Pkcs11Profile> next = new ArrayList<>(profiles);
        next.set(index, validated);
        persistAndReplace(next);
    }

    /** Upserts by normalized visible name for compatibility with existing UI callers. */
    public synchronized void upsert(Pkcs11Profile profile) {
        Pkcs11Profile validated = requireProfile(profile);
        int index = indexOf(validated.name());
        List<Pkcs11Profile> next = new ArrayList<>(profiles);
        if (index < 0) {
            next.add(validated);
        } else {
            next.set(index, validated);
        }
        persistAndReplace(next);
    }

    public synchronized boolean delete(String name) {
        int index = indexOf(name);
        if (index < 0) return false;
        List<Pkcs11Profile> next = new ArrayList<>(profiles);
        next.remove(index);
        persistAndReplace(next);
        return true;
    }

    /**
     * Reloads from disk without replacing valid in-memory state on malformed
     * or unsupported input. Individual invalid entries are skipped when valid
     * entries can still be isolated.
     */
    public synchronized void reload() {
        LoadOutcome outcome = readFromDisk();
        if (outcome.replaceCurrent) {
            profiles = Collections.unmodifiableList(outcome.profiles);
        }
    }

    /** Package-private test isolation without deleting the user's profile file. */
    synchronized void resetInMemoryForTesting() {
        profiles = List.of();
    }

    private int indexOf(String name) {
        if (name == null || name.isBlank()) return -1;
        String key = Pkcs11Profile.normalizedNameKey(name);
        for (int i = 0; i < profiles.size(); i++) {
            if (Pkcs11Profile.normalizedNameKey(profiles.get(i).name()).equals(key)) return i;
        }
        return -1;
    }

    private void ensureNameAvailable(Pkcs11Profile profile, int replacingIndex) {
        String key = Pkcs11Profile.normalizedNameKey(profile.name());
        for (int i = 0; i < profiles.size(); i++) {
            if (i != replacingIndex && Pkcs11Profile.normalizedNameKey(profiles.get(i).name()).equals(key)) {
                throw new IllegalArgumentException("PKCS#11 profile name is already in use");
            }
        }
    }

    private static Pkcs11Profile requireProfile(Pkcs11Profile profile) {
        Pkcs11Profile required = Objects.requireNonNull(profile, "PKCS#11 profile is required");
        // Reconstruct to validate objects originating from Gson's historical
        // record deserialization, which may bypass the canonical constructor.
        return new Pkcs11Profile(required.name(), required.library(), required.slot());
    }

    private void persistAndReplace(List<Pkcs11Profile> next) {
        String json = serialize(next);
        Path parent = file.getParent();
        Path temporary = null;
        try {
            if (parent != null) Files.createDirectories(parent);
            String prefix = "." + file.getFileName() + ".";
            temporary = Files.createTempFile(parent, prefix, ".tmp");
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            atomicMover.move(temporary, file);
            temporary = null;
            profiles = Collections.unmodifiableList(new ArrayList<>(next));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to persist PKCS#11 profiles atomically", failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Never replace a useful caller error with cleanup noise.
                }
            }
        }
    }

    private static String serialize(List<Pkcs11Profile> profiles) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
        JsonArray entries = new JsonArray();
        for (Pkcs11Profile profile : profiles) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", profile.name());
            entry.addProperty("library", profile.library());
            entry.addProperty("slotListIndex", profile.slot());
            entries.add(entry);
        }
        root.add("profiles", entries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private LoadOutcome readFromDisk() {
        try {
            if (!Files.exists(file)) return LoadOutcome.notPresent();
        } catch (SecurityException denied) {
            return LoadOutcome.invalid();
        }
        final String content;
        try {
            content = Files.readString(file);
        } catch (IOException | SecurityException unreadable) {
            return LoadOutcome.invalid();
        }

        try {
            JsonElement root = JsonParser.parseString(content);
            return parseRoot(root);
        } catch (RuntimeException malformedJson) {
            return LoadOutcome.invalid();
        }
    }

    private static LoadOutcome parseRoot(JsonElement root) {
        if (root == null || root.isJsonNull()) return LoadOutcome.invalid();
        int schemaVersion;
        JsonArray entries;
        if (root.isJsonArray()) {
            // Unversioned first-generation repository format.
            schemaVersion = 0;
            entries = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            JsonElement version = object.get("schemaVersion");
            schemaVersion = version == null ? 0 : integerValue(version).orElse(-1);
            JsonElement profiles = object.get("profiles");
            if (profiles == null || !profiles.isJsonArray()) return LoadOutcome.invalid();
            entries = profiles.getAsJsonArray();
        } else {
            return LoadOutcome.invalid();
        }

        if (schemaVersion < 0 || schemaVersion > CURRENT_SCHEMA_VERSION) return LoadOutcome.invalid();

        Map<String, Pkcs11Profile> unique = new LinkedHashMap<>();
        int invalidEntries = 0;
        for (JsonElement entry : entries) {
            Optional<Pkcs11Profile> parsed = parseProfile(entry, schemaVersion);
            if (parsed.isEmpty()) {
                invalidEntries++;
                continue;
            }
            Pkcs11Profile profile = parsed.get();
            String key = Pkcs11Profile.normalizedNameKey(profile.name());
            if (unique.putIfAbsent(key, profile) != null) invalidEntries++;
        }

        List<Pkcs11Profile> valid = new ArrayList<>(unique.values());
        // If every entry was malformed, retain the current in-memory set on
        // reload instead of interpreting corruption as an intentional clear.
        return new LoadOutcome(valid, invalidEntries == 0 || !valid.isEmpty());
    }

    private static Optional<Pkcs11Profile> parseProfile(JsonElement entry, int schemaVersion) {
        if (entry == null || !entry.isJsonObject()) return Optional.empty();
        JsonObject object = entry.getAsJsonObject();
        String name = stringValue(object.get("name")).orElse(null);
        String library = stringValue(object.get("library")).orElse(null);
        if (name == null || library == null) return Optional.empty();

        JsonElement slotElement = object.get("slotListIndex");
        if (slotElement == null && schemaVersion == 0) slotElement = object.get("slot");
        Optional<Integer> slot = integerValue(slotElement);
        if (slot.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new Pkcs11Profile(name, library, slot.get()));
        } catch (IllegalArgumentException invalidProfile) {
            return Optional.empty();
        }
    }

    private static Optional<String> stringValue(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        return Optional.of(value.getAsString());
    }

    private static Optional<Integer> integerValue(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        try {
            long number = value.getAsLong();
            if (number < 0 || number > Integer.MAX_VALUE) return Optional.empty();
            return Optional.of((int) number);
        } catch (RuntimeException invalidNumber) {
            return Optional.empty();
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    interface AtomicMover {
        void move(Path source, Path target) throws IOException;
    }

    private record LoadOutcome(List<Pkcs11Profile> profiles, boolean replaceCurrent) {
        private static LoadOutcome notPresent() {
            return new LoadOutcome(List.of(), true);
        }

        private static LoadOutcome invalid() {
            return new LoadOutcome(List.of(), false);
        }
    }
}
