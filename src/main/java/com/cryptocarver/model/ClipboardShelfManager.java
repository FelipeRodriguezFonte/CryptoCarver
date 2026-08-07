package com.cryptocarver.model;

import com.cryptocarver.utils.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClipboardShelfManager {
    private static final Logger LOG = LoggerFactory.getLogger(ClipboardShelfManager.class);
    private static final String SHELF_FILE = "shelf.json";
    private static final int MAX_ENTRIES = 100;

    private final Path shelfPath;
    private final Gson gson;
    private final LinkedList<ClipboardEntry> entries = new LinkedList<>();
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private static final ClipboardShelfManager INSTANCE = new ClipboardShelfManager();

    private ClipboardShelfManager() {
        this(resolveDefaultPath());
    }

    /** Explicit path constructor for isolated tools and tests. */
    public ClipboardShelfManager(Path shelfPath) {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        this.shelfPath = shelfPath == null ? resolveDefaultPath() : shelfPath.toAbsolutePath().normalize();
        ensureParentDirectory(this.shelfPath);
        List<ClipboardEntry> loaded = loadShelf();
        boolean migrated = false;
        for (ClipboardEntry entry : loaded) {
            if (entry == null) continue;
            ClipboardEntry normalized = entry.normalizePersisted();
            migrated |= entry.getId() == null || entry.getCreatedAt() == null
                    || entry.getTags() == null || entry.getNote() == null;
            this.entries.add(normalized);
        }
        if (migrated) {
            saveShelf();
        }
    }

    public static ClipboardShelfManager getInstance() {
        return INSTANCE;
    }

    /**
     * Kept for host compatibility. Shelf mutations are notebook storage
     * actions, not cryptographic executions, so they must never be published
     * into Recent Operations.
     */
    public synchronized void setReporter(com.cryptocarver.ui.StatusReporter ignored) {
        // Intentionally unused.
    }

    /** Registers a session-only listener used by open Clipboard Shelf views. */
    public void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.addIfAbsent(listener);
        }
    }

    /** Removes an open view's listener when that view is closed. */
    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    public synchronized void addEntry(ClipboardEntry entry) {
        if (entry == null) return;
        entries.addFirst(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        saveShelf();
        notifyChanged();
    }

    public synchronized void removeEntry(UUID id) {
        if (entries.removeIf(e -> Objects.equals(e.getId(), id))) {
            saveShelf();
            notifyChanged();
        }
    }

    public synchronized void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            saveShelf();
            notifyChanged();
        }
    }

    public synchronized boolean renameEntry(UUID id, String newLabel) {
        for (int i = 0; i < entries.size(); i++) {
            ClipboardEntry e = entries.get(i);
            if (Objects.equals(e.getId(), id)) {
                entries.set(i, e.withLabel(newLabel));
                saveShelf();
                notifyChanged();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean updateTagsAndNote(UUID id, List<String> tags, String note) {
        for (int i = 0; i < entries.size(); i++) {
            ClipboardEntry e = entries.get(i);
            if (Objects.equals(e.getId(), id)) {
                entries.set(i, e.withTagsAndNote(tags, note));
                saveShelf();
                notifyChanged();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean togglePin(UUID id) {
        for (int i = 0; i < entries.size(); i++) {
            ClipboardEntry e = entries.get(i);
            if (Objects.equals(e.getId(), id)) {
                boolean newPinned = !e.isPinned();
                entries.set(i, e.withPinned(newPinned));
                saveShelf();
                notifyChanged();
                return true;
            }
        }
        return false;
    }

    public synchronized Optional<ClipboardEntry> findDuplicate(String value, String sourceOperation) {
        if (value == null || value.isBlank()) return Optional.empty();
        return entries.stream()
                .filter(e -> Objects.equals(e.getValue(), value) && Objects.equals(e.getSourceOperation(), sourceOperation))
                .findFirst();
    }

    private void notifyChanged() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A closed or faulty view must never prevent shelf storage.
            }
        }
    }

    public synchronized List<ClipboardEntry> getEntries() {
        List<ClipboardEntry> result = new ArrayList<>(entries);
        result.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) {
                return a.isPinned() ? -1 : 1;
            }
            LocalDateTime bTime = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
            LocalDateTime aTime = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
            return bTime.compareTo(aTime);
        });
        return Collections.unmodifiableList(result);
    }

    public synchronized List<ClipboardEntry> search(String query, Boolean pinned, String sourceOperation, ClipboardEntry.Format format, OperationDetail.Classification classification) {
        return getEntries().stream()
                .filter(e -> {
                    if (pinned != null && e.isPinned() != pinned) {
                        return false;
                    }
                    if (sourceOperation != null && !sourceOperation.isBlank() && !"All".equalsIgnoreCase(sourceOperation) && !"All Operations".equalsIgnoreCase(sourceOperation)) {
                        if (e.getSourceOperation() == null || !e.getSourceOperation().equalsIgnoreCase(sourceOperation)) {
                            return false;
                        }
                    }
                    if (format != null && e.getFormat() != format) {
                        return false;
                    }
                    if (classification != null && e.getClassification() != classification) {
                        return false;
                    }
                    if (query != null && !query.isBlank()) {
                        String q = query.toLowerCase(java.util.Locale.ROOT);
                        boolean matchesLabel = e.getLabel() != null && e.getLabel().toLowerCase(java.util.Locale.ROOT).contains(q);
                        boolean matchesSource = e.getSourceOperation() != null && e.getSourceOperation().toLowerCase(java.util.Locale.ROOT).contains(q);
                        boolean matchesAlg = e.getAlgorithm() != null && e.getAlgorithm().toLowerCase(java.util.Locale.ROOT).contains(q);
                        boolean matchesNote = e.getNote() != null && e.getNote().toLowerCase(java.util.Locale.ROOT).contains(q);
                        boolean matchesTag = e.getTags().stream().anyMatch(t -> t.toLowerCase(java.util.Locale.ROOT).contains(q));
                        if (!matchesLabel && !matchesSource && !matchesAlg && !matchesNote && !matchesTag) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    /** Legacy search overload for backward compatibility. */
    public synchronized List<ClipboardEntry> search(String query, ClipboardEntry.Format format, OperationDetail.Classification classification) {
        return search(query, null, null, format, classification);
    }

    private List<ClipboardEntry> loadShelf() {
        if (shelfPath == null || !Files.exists(shelfPath)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(shelfPath)) {
            List<ClipboardEntry> loaded = gson.fromJson(reader, new TypeToken<List<ClipboardEntry>>() {}.getType());
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            LOG.warn("Unable to load shelf from {}; preserving file: {}", shelfPath, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveShelf() {
        if (shelfPath == null) return;
        ensureParentDirectory(shelfPath);
        Path parent = shelfPath.getParent();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent, ".shelf-", ".tmp");
            setOwnerOnlyFilePermissions(temporary);
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(entries, writer);
            }
            setOwnerOnlyFilePermissions(temporary);
            try {
                Files.move(temporary, shelfPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, shelfPath, StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnlyFilePermissions(shelfPath);
        } catch (IOException e) {
            LOG.error("Unable to save shelf atomically to {}", shelfPath, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {}
            }
        }
    }

    private static Path resolveDefaultPath() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) userHome = System.getProperty("java.io.tmpdir");
        Path modernDirectory = Paths.get(userHome, ".cryptocarver");
        Path defaultPath = modernDirectory.resolve(SHELF_FILE);
        ensureParentDirectory(defaultPath);
        return defaultPath;
    }

    private static void ensureParentDirectory(Path path) {
        if (path == null) return;
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
                setOwnerOnlyDirectoryPermissions(parent);
            } catch (IOException e) {
                LOG.warn("Unable to create parent directory {}", parent, e);
            }
        }
    }

    private static void setOwnerOnlyDirectoryPermissions(Path path) {
        if (path == null || !Files.exists(path)) return;
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                ));
            }
        } catch (Exception ignored) {}
    }

    private static void setOwnerOnlyFilePermissions(Path path) {
        if (path == null || !Files.exists(path)) return;
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE
                ));
            }
        } catch (Exception ignored) {}
    }
}
