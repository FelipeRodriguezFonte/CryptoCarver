package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Reads and writes {@code .ccevidence} files; a review folder may hold anything, so reads never trust the file. */
public final class EvidencePackageCodec {
    public static final String EXTENSION = ".ccevidence";
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_FOLDER_DEPTH = 3;

    private final Gson writer = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Gson reader = new Gson();

    /** One file of a review folder: either a parsed package or the reason it could not be read. */
    public record Entry(Path file, EvidencePackage evidence, EvidencePackage.Integrity integrity, String error) {
        public boolean readable() { return evidence != null; }
    }

    public String serialize(EvidencePackage evidence) { return writer.toJson(evidence); }

    public EvidencePackage deserialize(String json) throws IOException {
        EvidencePackage evidence;
        try {
            evidence = reader.fromJson(json, EvidencePackage.class);
        } catch (RuntimeException malformed) {
            throw new IOException("Not a valid evidence file", malformed);
        }
        if (evidence == null || !EvidencePackage.FORMAT.equals(evidence.getFormat())) {
            throw new IOException("Not a CryptoCarver evidence file");
        }
        if (evidence.getFormatVersion() != EvidencePackage.FORMAT_VERSION) {
            throw new IOException("Unsupported evidence format version " + evidence.getFormatVersion());
        }
        return evidence;
    }

    public void write(EvidencePackage evidence, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(target, serialize(evidence), StandardCharsets.UTF_8);
    }

    public Entry load(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Evidence file is too large");
            EvidencePackage evidence = deserialize(Files.readString(file, StandardCharsets.UTF_8));
            return new Entry(file, evidence, evidence.verify(), null);
        } catch (IOException | RuntimeException error) {
            return new Entry(file, null, null, error.getMessage());
        }
    }

    public List<Entry> loadFolder(Path folder) throws IOException {
        List<Path> files;
        try (Stream<Path> found = Files.find(folder, MAX_FOLDER_DEPTH, (path, attributes) -> attributes.isRegularFile()
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))) {
            files = found.sorted().toList();
        }
        List<Entry> entries = new ArrayList<>(files.size());
        for (Path file : files) entries.add(load(file));
        return entries;
    }
}
