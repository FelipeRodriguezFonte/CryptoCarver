package com.cryptocarver.service;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.ThemePreference;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single typed facade for preferences used by the shell and settings views. */
public final class PreferencesService {
    private final AppSettings settings;
    private final CopyOnWriteArrayList<Consumer<Preferences>> listeners = new CopyOnWriteArrayList<>();

    public PreferencesService(AppSettings settings) { this.settings = Objects.requireNonNull(settings, "settings"); }
    public static PreferencesService global() { return new PreferencesService(AppSettings.getInstance()); }

    public Preferences read() {
        return new Preferences(settings.getLanguagePreference(), settings.getThemePreference(),
                settings.getStartupRoute(), settings.isConfirmDestructiveActions(), settings.getTextScale(),
                settings.isCompactDensity(), settings.getSecretVisibilityProfile(), settings.getHistoryRetentionDays(),
                settings.isPersistHistorySecrets(), settings.getClipboardClearSeconds(), settings.isClearKeyCacheOnExit(),
                settings.getCustomTsaUrl(), settings.getNetworkTimeoutSeconds(), settings.getProxy());
    }

    public void update(Preferences value) {
        Preferences p = Objects.requireNonNull(value, "preferences");
        settings.setLanguagePreference(p.language());
        settings.setThemePreference(p.theme());
        settings.setStartupRoute(p.startupRoute());
        settings.setConfirmDestructiveActions(p.confirmDestructiveActions());
        settings.setTextScale(p.textScale());
        settings.setCompactDensity(p.compactDensity());
        settings.setSecretVisibilityProfile(p.visibility());
        settings.setHistoryRetentionDays(p.historyRetentionDays());
        settings.setPersistHistorySecrets(p.persistHistorySecrets());
        settings.setClipboardClearSeconds(p.clipboardClearSeconds());
        settings.setClearKeyCacheOnExit(p.clearKeyCacheOnExit());
        settings.setCustomTsaUrl(p.tsaUrl());
        settings.setNetworkTimeoutSeconds(p.networkTimeoutSeconds());
        settings.setProxy(p.proxy());
        Preferences current = read();
        listeners.forEach(listener -> listener.accept(current));
    }

    public void addListener(Consumer<Preferences> listener) { if (listener != null) listeners.add(listener); }
    public void removeListener(Consumer<Preferences> listener) { listeners.remove(listener); }

    public record Preferences(LanguagePreference language, ThemePreference theme, String startupRoute,
                              boolean confirmDestructiveActions, double textScale, boolean compactDensity,
                              SecretVisibilityProfile visibility, int historyRetentionDays,
                              boolean persistHistorySecrets, int clipboardClearSeconds, boolean clearKeyCacheOnExit,
                              String tsaUrl, int networkTimeoutSeconds, String proxy) {
        public Preferences {
            language = language == null ? LanguagePreference.SYSTEM : language;
            theme = theme == null ? ThemePreference.SYSTEM : theme;
            startupRoute = startupRoute == null ? "" : startupRoute.trim();
            visibility = visibility == null ? SecretVisibilityProfile.FULL_LAB : visibility;
            textScale = Double.isFinite(textScale) ? Math.max(.8, Math.min(1.5, textScale)) : 1.0;
            historyRetentionDays = Math.max(0, Math.min(3650, historyRetentionDays));
            clipboardClearSeconds = Math.max(0, Math.min(3600, clipboardClearSeconds));
            tsaUrl = tsaUrl == null ? "" : tsaUrl.trim();
            networkTimeoutSeconds = Math.max(1, Math.min(300, networkTimeoutSeconds));
            proxy = proxy == null ? "" : proxy.trim();
        }
    }
}
