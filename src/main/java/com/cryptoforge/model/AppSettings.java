package com.cryptoforge.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Small persistent settings store for non-secret user preferences. */
public final class AppSettings {
    private static final AppSettings INSTANCE = new AppSettings();
    private final Path file;
    private Settings data = new Settings();

    private AppSettings() {
        String home = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
        file = Paths.get(home, ".cryptocarver", "settings.json");
        load();
    }

    public static AppSettings getInstance() { return INSTANCE; }

    public synchronized SecretVisibility getSecretVisibility() {
        return data.secretVisibility == null ? SecretVisibility.FULL_LAB : data.secretVisibility;
    }

    public synchronized void setSecretVisibility(SecretVisibility visibility) {
        data.secretVisibility = visibility == null ? SecretVisibility.FULL_LAB : visibility;
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
        private SecretVisibility secretVisibility = SecretVisibility.FULL_LAB;
    }
}
