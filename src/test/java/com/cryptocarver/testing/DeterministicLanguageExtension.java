package com.cryptocarver.testing;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Gives every test a known starting language.
 *
 * <p>The language preference is a persisted global. Several classes switch it to Spanish and
 * deliberately leave it there, so whether a test asserting English text passed depended on the
 * order the suite happened to run in and on what a previous run had written to disk. Resetting
 * before each test removes that coupling; classes that need another language still select it in
 * their own setup, which runs after this callback.</p>
 */
public final class DeterministicLanguageExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        I18nService.getInstance().setPreference(LanguagePreference.EN);
    }
}
