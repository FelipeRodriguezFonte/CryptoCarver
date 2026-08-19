package com.cryptocarver.service;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central, dependency-free localization service for the JavaFX shell.
 *
 * <p>English is deliberately the base bundle. Missing localized keys and
 * bundle loading problems therefore degrade to English (and finally to the
 * key itself) instead of preventing the application from starting.</p>
 */
public final class I18nService {
    public static final String BUNDLE_BASE_NAME = "i18n.messages";

    private static final Logger LOG = LoggerFactory.getLogger(I18nService.class);
    private static final I18nService INSTANCE = new I18nService(AppSettings.getInstance());

    private final AppSettings settings;
    private final String bundleBaseName;
    private final ClassLoader classLoader;
    private final List<Consumer<Locale>> listeners = new CopyOnWriteArrayList<>();
    private Locale systemLocale;
    private LanguagePreference preference;
    private Locale locale;
    private ResourceBundle bundle;
    private ResourceBundle englishBundle;

    public static I18nService getInstance() {
        INSTANCE.refreshFromSettings();
        return INSTANCE;
    }

    public I18nService(AppSettings settings) {
        this(settings, BUNDLE_BASE_NAME, Locale.getDefault(), I18nService.class.getClassLoader());
    }

    /** Constructor kept injectable so resolution/fallback behavior is testable without global state. */
    public I18nService(AppSettings settings, String bundleBaseName, Locale systemLocale, ClassLoader classLoader) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.bundleBaseName = Objects.requireNonNull(bundleBaseName, "bundleBaseName");
        this.systemLocale = systemLocale == null ? Locale.getDefault() : systemLocale;
        this.classLoader = classLoader == null ? I18nService.class.getClassLoader() : classLoader;
        this.preference = settings.getLanguagePreference();
        reload(false);
    }

    public synchronized LanguagePreference getPreference() {
        return preference;
    }

    /** Rebinds the singleton to the active settings instance (useful for isolated UI tests too). */
    public void refreshFromSettings() {
        LanguagePreference persisted = (this == INSTANCE ? AppSettings.getInstance() : settings).getLanguagePreference();
        boolean changed;
        synchronized (this) {
            changed = preference != persisted;
            if (changed) {
                preference = persisted;
                reload(false);
            }
        }
        if (changed) notifyListeners();
    }

    public synchronized Locale getLocale() {
        return locale;
    }

    public synchronized ResourceBundle getBundle() {
        return bundle;
    }

    public synchronized void setSystemLocaleForTesting(Locale newSystemLocale) {
        systemLocale = newSystemLocale == null ? Locale.getDefault() : newSystemLocale;
        boolean changed = preference == LanguagePreference.SYSTEM && !resolveLocale(preference, systemLocale).equals(locale);
        if (preference == LanguagePreference.SYSTEM) reload(true);
        if (changed) notifyListeners();
    }

    public void setPreference(LanguagePreference newPreference) {
        LanguagePreference normalized = newPreference == null ? LanguagePreference.SYSTEM : newPreference;
        synchronized (this) {
            if (preference == normalized) return;
            preference = normalized;
            (this == INSTANCE ? AppSettings.getInstance() : settings).setLanguagePreference(normalized);
            reload(false);
        }
        notifyListeners();
    }

    public void addLocaleChangeListener(Consumer<Locale> listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeLocaleChangeListener(Consumer<Locale> listener) {
        listeners.remove(listener);
    }

    public String text(String key) {
        return text(key, new Object[0]);
    }

    public String text(String key, Object... arguments) {
        if (key == null || key.isBlank()) return "";
        String value = lookup(bundle, key);
        if (value == null && bundle != englishBundle) value = lookup(englishBundle, key);
        if (value == null) {
            LOG.warn("Missing localization key: {} (locale={})", key, locale.toLanguageTag());
            value = key;
        }
        return arguments == null || arguments.length == 0
                ? value
                : MessageFormat.format(value, arguments);
    }

    private String lookup(ResourceBundle candidate, String key) {
        if (candidate == null || !candidate.containsKey(key)) return null;
        try {
            return candidate.getString(key);
        } catch (RuntimeException exception) {
            LOG.warn("Unable to resolve localization key: {} (locale={})", key, locale.toLanguageTag());
            return null;
        }
    }

    private synchronized void reload(boolean persist) {
        locale = resolveLocale(preference, systemLocale);
        englishBundle = loadBundle(Locale.ENGLISH);
        bundle = loadBundle(locale);
        if (bundle == null) bundle = englishBundle;
        if (bundle == null) {
            LOG.warn("Localization bundles unavailable; using keys as fallback (locale={})", locale.toLanguageTag());
        }
        if (persist) settings.setLanguagePreference(preference);
    }

    private ResourceBundle loadBundle(Locale requestedLocale) {
        try {
            return ResourceBundle.getBundle(bundleBaseName, requestedLocale, classLoader);
        } catch (RuntimeException exception) {
            LOG.warn("Unable to load localization bundle (locale={})", requestedLocale.toLanguageTag());
            return null;
        }
    }

    public static Locale resolveLocale(LanguagePreference preference, Locale systemLocale) {
        LanguagePreference selected = preference == null ? LanguagePreference.SYSTEM : preference;
        if (selected == LanguagePreference.ES) return Locale.forLanguageTag("es");
        if (selected == LanguagePreference.EN) return Locale.ENGLISH;
        Locale system = systemLocale == null ? Locale.getDefault() : systemLocale;
        return "es".equalsIgnoreCase(system.getLanguage()) ? Locale.forLanguageTag("es") : Locale.ENGLISH;
    }

    private void notifyListeners() {
        Locale changedLocale = getLocale();
        for (Consumer<Locale> listener : listeners) {
            try {
                listener.accept(changedLocale);
            } catch (RuntimeException exception) {
                LOG.warn("Localization listener failed (locale={})", changedLocale.toLanguageTag());
            }
        }
    }
}
