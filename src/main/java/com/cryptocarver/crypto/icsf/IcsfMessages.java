package com.cryptocarver.crypto.icsf;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Turns {@link IcsfText} into words, in a given language.
 *
 * <p>Uses {@code java.util.ResourceBundle} directly, so the core keeps its
 * promise of having no dependency on the desktop layer while still producing a
 * translated report for the interface and an English one for the CLI.</p>
 */
public final class IcsfMessages {

    private IcsfMessages() { }

    /** Shared with the rest of the application, so there is one place to translate. */
    public static final String BUNDLE = "i18n.messages";

    /** The language the CLI and the text reports default to. */
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    public static String resolve(IcsfText text) {
        return resolve(text, DEFAULT_LOCALE);
    }

    /**
     * Renders one piece of report text.
     *
     * <p>A key with no entry comes back as the key itself rather than as an empty
     * string: a visible {@code icsf.detail.something} in a report is a bug report,
     * whereas a blank field silently loses information. {@code IcsfDetailI18nTest}
     * makes sure that never reaches a release.</p>
     */
    public static String resolve(IcsfText text, Locale locale) {
        if (text == null || text.isEmpty()) return "";
        if (text.literal() != null) return text.literal();

        String pattern = lookup(text.key(), locale);
        if (pattern == null) pattern = lookup(text.key(), DEFAULT_LOCALE);
        if (pattern == null) return text.key();

        // MessageFormat only when there are arguments, which is the same rule
        // I18nService follows. Some of these keys are read through both paths, and
        // formatting one and not the other would show doubled quotes in one of them.
        // Consequence: only messages with arguments double their apostrophes.
        if (text.arguments().isEmpty()) return pattern;

        // An argument may itself be translatable — a Control Vector's main type
        // inside its family description, say — so resolve arguments first.
        Object[] resolved = new Object[text.arguments().size()];
        for (int index = 0; index < resolved.length; index++) {
            Object argument = text.arguments().get(index);
            resolved[index] = argument instanceof IcsfText nested
                    ? resolve(nested, locale) : argument;
        }
        try {
            return new MessageFormat(pattern, locale == null ? DEFAULT_LOCALE : locale)
                    .format(resolved);
        } catch (IllegalArgumentException malformedPattern) {
            // A broken pattern must not take a whole report down with it.
            return pattern;
        }
    }

    /** True when the bundle for {@code locale} can render this text. */
    public static boolean canResolve(IcsfText text, Locale locale) {
        if (text == null || text.isEmpty() || text.literal() != null) return true;
        if (lookup(text.key(), locale) == null) return false;
        for (Object argument : text.arguments()) {
            if (argument instanceof IcsfText nested && !canResolve(nested, locale)) return false;
        }
        return true;
    }

    /** Resolves each part and joins them, for lists built while decoding. */
    public static String join(java.util.List<IcsfText> parts, String separator, Locale locale) {
        StringBuilder out = new StringBuilder();
        for (IcsfText part : parts) {
            if (out.length() > 0) out.append(separator);
            out.append(resolve(part, locale));
        }
        return out.toString();
    }

    private static String lookup(String key, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE,
                    locale == null ? DEFAULT_LOCALE : locale,
                    IcsfMessages.class.getClassLoader());
            return bundle.containsKey(key) ? bundle.getString(key) : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
