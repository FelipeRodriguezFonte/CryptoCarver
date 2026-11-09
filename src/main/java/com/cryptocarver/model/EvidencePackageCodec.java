package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    private final long maxFileBytes;

    public EvidencePackageCodec() { this(MAX_FILE_BYTES); }

    EvidencePackageCodec(long maxFileBytes) {
        if (maxFileBytes < 0) throw new IllegalArgumentException("Maximum file size must be non-negative");
        this.maxFileBytes = maxFileBytes;
    }

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
        Path temp = Files.createTempFile(parent, ".ccevidence-", ".tmp");
        try {
            try (OutputStream output = new BoundedOutputStream(Files.newOutputStream(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING), maxFileBytes);
                 Writer text = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                try {
                    writer.toJson(evidence, text);
                } catch (JsonIOException error) {
                    if (error.getCause() instanceof IOException ioError) throw ioError;
                    throw error;
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public Entry load(Path file) {
        try {
            EvidencePackage evidence = deserialize(readBounded(file));
            return new Entry(file, evidence, evidence.verify(), null);
        } catch (IOException | RuntimeException error) {
            return new Entry(file, null, null, error.getMessage());
        }
    }

    private String readBounded(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            int probeSize = (int) Math.min(Integer.MAX_VALUE, maxFileBytes == Long.MAX_VALUE
                    ? Long.MAX_VALUE : maxFileBytes + 1);
            byte[] bytes = input.readNBytes(probeSize);
            if (bytes.length > maxFileBytes || input.read() != -1) throw new IOException("Evidence file is too large");
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Evidence loading cancelled");
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long limit;
        private long count;

        private BoundedOutputStream(OutputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override public void write(int value) throws IOException {
            ensureCapacity(1);
            delegate.write(value);
            count++;
        }

        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            ensureCapacity(length);
            delegate.write(bytes, offset, length);
            count += length;
        }

        private void ensureCapacity(int length) throws IOException {
            if (length < 0 || count > limit - length) throw new IOException("Evidence file is too large");
        }

        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
    }

    /** Lists candidate files without parsing their contents. */
    public List<Path> listFiles(Path folder) throws IOException {
        try (Stream<Path> found = Files.find(folder, MAX_FOLDER_DEPTH, (path, attributes) -> attributes.isRegularFile()
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))) {
            List<Path> files = new ArrayList<>();
            var paths = found.iterator();
            while (paths.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Evidence loading cancelled");
                files.add(paths.next());
            }
            files.sort(Path::compareTo);
            return files;
        }
    }

    public List<Entry> loadFolder(Path folder) throws IOException {
        List<Path> files = listFiles(folder);
        List<Entry> entries = new ArrayList<>(files.size());
        for (Path file : files) entries.add(load(file));
        return entries;
    }
}
