package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import com.cryptocarver.model.AppSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ModuleResetPolicyTest {
    @TempDir Path temporaryDirectory;

    @Test
    void clearAndResetDefaultsHaveDifferentLocalContracts() {
        AtomicBoolean clearCalled = new AtomicBoolean();
        AtomicBoolean defaultsCalled = new AtomicBoolean();
        ArrayList<String> history = new ArrayList<>(java.util.List.of("technical result"));
        ArrayList<String> shelf = new ArrayList<>(java.util.List.of("clipboard artifact"));

        ModuleResetPolicy.Result reset = ModuleResetPolicy.apply(null, ModuleResetPolicy.Action.RESET_DEFAULTS,
                () -> clearCalled.set(true), () -> defaultsCalled.set(true));
        assertFalse(clearCalled.get(), "Reset Defaults must not clear module data");
        assertTrue(defaultsCalled.get());
        assertTrue(reset.sharedStatePreserved());
        assertEquals(java.util.List.of("technical result"), history);
        assertEquals(java.util.List.of("clipboard artifact"), shelf);

        clearCalled.set(false);
        defaultsCalled.set(false);
        ModuleResetPolicy.Result clear = ModuleResetPolicy.apply(null, ModuleResetPolicy.Action.CLEAR,
                () -> clearCalled.set(true), () -> defaultsCalled.set(true));
        assertTrue(clearCalled.get());
        assertFalse(defaultsCalled.get());
        assertEquals(ModuleResetPolicy.Action.CLEAR, clear.action());
    }

    @Test
    void localizedDynamicFeedbackKeepsTechnicalArgumentsAndFallsBackSafely() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());
        service.setPreference(LanguagePreference.ES);
        assertEquals("Datos del módulo Payments limpiados", service.text("module.payments.clearStatus"));
        assertEquals("Error: ABC123", service.text("module.emv.error.generate", "ABC123"));
        assertEquals("missing.ux20.key", service.text("missing.ux20.key"));
        service.setPreference(LanguagePreference.EN);
        assertEquals("Payments module data cleared", service.text("module.payments.clearStatus"));
        assertTrue(service.text("module.emv.error.generate", "9F0206").endsWith("9F0206"));
    }
}
