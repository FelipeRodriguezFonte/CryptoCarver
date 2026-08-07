package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages persistence, import, and export of safe personal operation templates.
 *
 * <p>Persists templates to {@code ~/.cryptocarver/templates.json} using atomic file
 * replacement and owner-only POSIX permissions (0600) when supported.</p>
 */
public final class PersonalTemplateStore {

    private static final PersonalTemplateStore INSTANCE = new PersonalTemplateStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, SafeOperationTemplate> templates = new LinkedHashMap<>();

    private PersonalTemplateStore() {
        this(defaultTemplatesFile());
    }

    /** Package-visible constructor for isolated test execution. */
    PersonalTemplateStore(Path file) {
        this.file = Objects.requireNonNull(file, "Template storage file is required").toAbsolutePath().normalize();
        load();
    }

    public static PersonalTemplateStore getInstance() {
        return INSTANCE;
    }

    private static Path defaultTemplatesFile() {
        String home = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
        return Paths.get(home, ".cryptocarver", "templates.json");
    }

    public synchronized List<SafeOperationTemplate> getAllTemplates() {
        return List.copyOf(templates.values());
    }

    public synchronized List<SafeOperationTemplate> getTemplatesForModule(String module) {
        if (module == null || module.isBlank()) return List.of();
        String target = module.trim();
        List<SafeOperationTemplate> result = new ArrayList<>();
        for (SafeOperationTemplate t : templates.values()) {
            if (target.equalsIgnoreCase(t.getModule())) {
                result.add(t);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized Optional<SafeOperationTemplate> getTemplateById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(templates.get(id.trim()));
    }

    public synchronized SafeOperationTemplate saveTemplate(SafeOperationTemplate template) {
        SafeTemplateAllowlist.validateTemplate(template);

        if (template.getId() == null || template.getId().isBlank()) {
            template.setId(UUID.randomUUID().toString());
        }
        template.updateTimestamp();

        // Ensure name uniqueness within module if new
        String module = template.getModule();
        String name = template.getName();
        for (SafeOperationTemplate existing : templates.values()) {
            if (!existing.getId().equals(template.getId())
                    && existing.getModule().equalsIgnoreCase(module)
                    && existing.getName().equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("A template named '" + name + "' already exists for module '" + module + "'");
            }
        }

        templates.put(template.getId(), template);
        save();
        return template;
    }

    public synchronized void renameTemplate(String id, String newName) {
        SafeOperationTemplate template = getTemplateById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        template.setName(newName);
        template.updateTimestamp();

        // Validate updated template
        SafeTemplateAllowlist.validateTemplate(template);
        save();
    }

    public synchronized boolean deleteTemplate(String id) {
        if (id == null || id.isBlank()) return false;
        SafeOperationTemplate removed = templates.remove(id.trim());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public synchronized void exportTemplate(SafeOperationTemplate template, Path destinationFile) throws IOException {
        SafeTemplateAllowlist.validateTemplate(template);
        Objects.requireNonNull(destinationFile, "Destination path is required");

        Path target = destinationFile.toAbsolutePath().normalize();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        String json = GSON.toJson(template);
        Files.writeString(target, json);
    }

    public synchronized SafeOperationTemplate importTemplate(Path sourceFile) throws IOException {
        Objects.requireNonNull(sourceFile, "Source file path is required");
        if (!Files.exists(sourceFile)) {
            throw new IllegalArgumentException("Template file does not exist: " + sourceFile);
        }

        String json = Files.readString(sourceFile);
        SafeOperationTemplate imported;
        try {
            imported = GSON.fromJson(json, SafeOperationTemplate.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid template JSON structure: " + e.getMessage(), e);
        }

        if (imported == null) {
            throw new IllegalArgumentException("Failed to parse template from file");
        }

        // Validate strictly using allowlist
        imported.setParameters(imported.getParameters());
        SafeTemplateAllowlist.validateTemplate(imported);

        // Assign fresh ID if needed to prevent overwrite conflicts
        imported.setId(UUID.randomUUID().toString());
        imported.updateTimestamp();

        // Adjust name if duplicate exists
        String baseName = imported.getName();
        String name = baseName;
        int count = 1;
        while (hasDuplicateName(imported.getModule(), name)) {
            name = baseName + " (" + (++count) + ")";
        }
        imported.setName(name);

        templates.put(imported.getId(), imported);
        save();
        return imported;
    }

    private boolean hasDuplicateName(String module, String name) {
        for (SafeOperationTemplate t : templates.values()) {
            if (t.getModule().equalsIgnoreCase(module) && t.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void load() {
        templates.clear();
        if (!Files.exists(file)) return;
        try {
            String content = Files.readString(file);
            Type listType = new TypeToken<List<SafeOperationTemplate>>() {}.getType();
            List<SafeOperationTemplate> loaded = GSON.fromJson(content, listType);
            if (loaded != null) {
                for (SafeOperationTemplate template : loaded) {
                    try {
                        // Gson hydrates fields directly, so re-run the model
                        // setter to canonicalize legacy format aliases.
                        template.setParameters(template.getParameters());
                        SafeTemplateAllowlist.validateTemplate(template);
                        templates.put(template.getId(), template);
                    } catch (Exception e) {
                        // Skip corrupted/invalid template entries silently to protect store integrity
                    }
                }
            }
        } catch (Exception ignored) {
            // Unreadable template store does not prevent app startup
        }
    }

    private void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempFile = Files.createTempFile(parent, "templates-", ".tmp");
            String json = GSON.toJson(new ArrayList<>(templates.values()));
            Files.writeString(tempFile, json);

            // Apply owner-only POSIX permissions if supported
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                try {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                    Files.setPosixFilePermissions(tempFile, perms);
                } catch (Exception ignored) {
                }
            }

            // Atomic move
            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                try {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                    Files.setPosixFilePermissions(file, perms);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            // Non-fatal warning if save fails
        }
    }
}
