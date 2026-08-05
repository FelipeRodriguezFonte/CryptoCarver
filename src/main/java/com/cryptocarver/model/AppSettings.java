package com.cryptocarver.model;

import com.cryptocarver.ui.UiNavigationRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Small persistent settings store for non-secret user preferences. */
public final class AppSettings {
    private static final AppSettings INSTANCE = new AppSettings();
    private final Path file;
    private final Pkcs11ProfileRepository pkcs11ProfileRepository;
    private Settings data = new Settings();

    private AppSettings() {
        this(defaultSettingsFile());
    }

    /** Constructor for isolated settings instances (e.g. tests or custom paths). */
    public AppSettings(Path file) {
        this.file = Objects.requireNonNull(file, "Settings file is required").toAbsolutePath().normalize();
        this.pkcs11ProfileRepository = new Pkcs11ProfileRepository(pkcs11ProfileFile(this.file));
        load();
        migrateLegacyPkcs11Profiles();
    }

    private static Path defaultSettingsFile() {
        String home = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
        return Paths.get(home, ".cryptocarver", "settings.json");
    }

    private static volatile AppSettings instanceOverride;

    public static AppSettings getInstance() {
        return instanceOverride != null ? instanceOverride : INSTANCE;
    }

    public static void setInstanceForTesting(AppSettings override) {
        instanceOverride = override;
    }

    public static void resetInstanceForTesting() {
        instanceOverride = null;
    }

    public synchronized void resetForTesting() {
        data = new Settings();
        pkcs11ProfileRepository.resetInMemoryForTesting();
    }

    private static Path pkcs11ProfileFile(Path settingsFile) {
        Path parent = settingsFile.getParent();
        return (parent == null ? Path.of(".") : parent).resolve("pkcs11-profiles.json")
                .toAbsolutePath().normalize();
    }

    public synchronized LanguagePreference getLanguagePreference() {
        return data.languagePreference == null ? LanguagePreference.SYSTEM : data.languagePreference;
    }

    public synchronized void setLanguagePreference(LanguagePreference preference) {
        data.languagePreference = preference == null ? LanguagePreference.SYSTEM : preference;
        save();
    }

    public synchronized SecretVisibilityProfile getSecretVisibilityProfile() {
        return data.secretVisibility == null ? SecretVisibilityProfile.FULL_LAB : data.secretVisibility;
    }

    public synchronized void setSecretVisibilityProfile(SecretVisibilityProfile visibility) {
        data.secretVisibility = visibility == null ? SecretVisibilityProfile.FULL_LAB : visibility;
        save();
    }

    public synchronized String getCustomTsaUrl() { return data.customTsaUrl == null ? "" : data.customTsaUrl; }

    public synchronized void setCustomTsaUrl(String value) {
        data.customTsaUrl = value == null ? "" : value.trim();
        save();
    }

    /** Non-secret TSA endpoint profiles. Credentials are deliberately never persisted. */
    public synchronized List<TsaProfile> getTsaProfiles() {
        if (data.tsaProfiles == null) return List.of();
        return data.tsaProfiles.stream().map(profile -> new TsaProfile(profile.name, profile.url)).toList();
    }

    public synchronized void saveTsaProfile(String name, String url) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedUrl = url == null ? "" : url.trim();
        if (normalizedName.isEmpty() || normalizedUrl.isEmpty()) {
            throw new IllegalArgumentException("Profile name and TSA URL are required");
        }
        if (data.tsaProfiles == null) data.tsaProfiles = new ArrayList<>();
        data.tsaProfiles.removeIf(profile -> normalizedName.equalsIgnoreCase(profile.name));
        data.tsaProfiles.add(new TsaProfile(normalizedName, normalizedUrl));
        save();
    }

    public synchronized void removeTsaProfile(String name) {
        if (data.tsaProfiles == null || name == null) return;
        data.tsaProfiles.removeIf(profile -> name.trim().equalsIgnoreCase(profile.name));
        save();
    }

    public record TsaProfile(String name, String url) { }

    public synchronized String getEBCDICCodePage() { return data.ebcdicCodePage == null ? "" : data.ebcdicCodePage; }

    public synchronized void setEBCDICCodePage(String value) {
        data.ebcdicCodePage = value == null ? "" : value;
        save();
    }

    public synchronized String getEBCDICDirection() { return data.ebcdicDirection == null ? "" : data.ebcdicDirection; }

    public synchronized void setEBCDICDirection(String value) {
        data.ebcdicDirection = value == null ? "" : value;
        save();
    }

    /** Non-secret reusable truststore location. Passwords are deliberately never persisted. */
    public synchronized List<TrustStoreProfile> getTrustStoreProfiles() {
        if (data.trustStoreProfiles == null) return List.of();
        return data.trustStoreProfiles.stream().map(profile -> new TrustStoreProfile(profile.name, profile.path, profile.type)).toList();
    }

    public synchronized void saveTrustStoreProfile(String name, String path, String type) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedName.isEmpty() || normalizedPath.isEmpty()) throw new IllegalArgumentException("Profile name and path are required");
        if (data.trustStoreProfiles == null) data.trustStoreProfiles = new ArrayList<>();
        data.trustStoreProfiles.removeIf(profile -> normalizedName.equalsIgnoreCase(profile.name));
        data.trustStoreProfiles.add(new TrustStoreProfile(normalizedName, normalizedPath, type == null ? "Auto" : type));
        save();
    }

    public record TrustStoreProfile(String name, String path, String type) { }

    public synchronized List<Pkcs11Profile> getPkcs11Profiles() {
        return pkcs11ProfileRepository.list();
    }

    /** Exposes the dedicated non-secret repository to feature presenters. */
    public Pkcs11ProfileRepository getPkcs11ProfileRepository() {
        return pkcs11ProfileRepository;
    }

    public synchronized void savePkcs11Profile(String name, String library, int slot) {
        pkcs11ProfileRepository.upsert(new Pkcs11Profile(name, library, slot));
        // Keep the legacy settings file present for callers that use it as a
        // general settings anchor; profile data now lives in its own atomic,
        // versioned file.
        data.pkcs11Profiles = new ArrayList<>();
        save();
    }

    public synchronized void removePkcs11Profile(String name) {
        pkcs11ProfileRepository.delete(name);
        data.pkcs11Profiles = new ArrayList<>();
        save();
    }

    // --- FAVORITES & LAST ROUTE PERSISTENCE (UX-07) ---

    public synchronized List<String> getFavorites() {
        if (data.favorites == null) return List.of();
        // Purge any favorites that no longer resolve in UiNavigationRegistry
        List<String> valid = data.favorites.stream()
                .filter(fav -> fav != null && !fav.isBlank() && UiNavigationRegistry.resolve(fav).isPresent())
                .distinct()
                .limit(12)
                .toList();
        if (valid.size() != data.favorites.size()) {
            data.favorites = new ArrayList<>(valid);
            save();
        }
        return valid;
    }

    public synchronized boolean isFavorite(String routeId) {
        if (routeId == null || routeId.isBlank()) return false;
        return getFavorites().contains(routeId.trim());
    }

    public synchronized void toggleFavorite(String routeId) {
        if (routeId == null || routeId.isBlank()) return;
        String clean = routeId.trim();
        if (UiNavigationRegistry.resolve(clean).isEmpty()) return; // Must resolve safely

        List<String> list = new ArrayList<>(getFavorites());
        if (list.contains(clean)) {
            list.remove(clean);
        } else {
            if (list.size() < 12) {
                list.add(clean);
            }
        }
        data.favorites = list;
        save();
    }

    public synchronized String getLastRoute() {
        if (data.lastRoute == null || data.lastRoute.isBlank()) return "";
        String clean = data.lastRoute.trim();
        if (UiNavigationRegistry.resolve(clean).isPresent()) {
            return clean;
        }
        return "";
    }

    public synchronized void setLastRoute(String routeId) {
        if (routeId == null || routeId.isBlank()) return;
        String clean = routeId.trim();
        if (UiNavigationRegistry.resolve(clean).isPresent()) {
            data.lastRoute = clean;
            save();
        }
    }

    private void load() {
        try {
            if (Files.exists(file)) {
                Settings loaded = new Gson().fromJson(Files.readString(file), Settings.class);
                if (loaded != null) data = loaded;
            }
        } catch (Exception ignored) {
            // Preferences must never prevent the application from starting.
        }
    }

    /**
     * Migrates the original profiles embedded in settings.json once. Invalid
     * legacy entries are rejected by Pkcs11Profile and never copied to the
     * dedicated repository.
     */
    private void migrateLegacyPkcs11Profiles() {
        if (data.pkcs11Profiles == null || data.pkcs11Profiles.isEmpty()
                || Files.exists(pkcs11ProfileRepository.file())) {
            return;
        }
        boolean migrated = false;
        for (Pkcs11Profile profile : data.pkcs11Profiles) {
            try {
                pkcs11ProfileRepository.upsert(profile);
                migrated = true;
            } catch (IllegalArgumentException ignored) {
                // Keep valid legacy profiles and isolate invalid entries.
            }
        }
        data.pkcs11Profiles = new ArrayList<>();
        if (migrated) save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(data));
        } catch (Exception ignored) {
            // A non-secret convenience preference is optional.
        }
    }

    private static final class Settings {
        private String customTsaUrl = "";
        private List<TsaProfile> tsaProfiles = new ArrayList<>();
        private String ebcdicCodePage = "";
        private String ebcdicDirection = "";
        private List<TrustStoreProfile> trustStoreProfiles = new ArrayList<>();
        private List<Pkcs11Profile> pkcs11Profiles = new ArrayList<>();
        private SecretVisibilityProfile secretVisibility = SecretVisibilityProfile.FULL_LAB;
        private LanguagePreference languagePreference = LanguagePreference.SYSTEM;
        private List<String> favorites = new ArrayList<>();
        private String lastRoute = "";
    }
}
