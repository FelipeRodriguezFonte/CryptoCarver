package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultFlowI18nTest {
    @TempDir Path temporaryDirectory;

    @Test
    void eachResultFlowResolvesEnglishAndSpanish() {
        I18nService service = service();
        assertModule(service, ModuleTextCatalog.processDesigner(), "🧩 Process Designer (MVP)", "🧩 Diseñador de procesos (MVP)");
        assertModule(service, ModuleTextCatalog.history(), "Recent Operations", "Operaciones recientes");
        assertModule(service, ModuleTextCatalog.clipboardShelf(), "Clipboard Shelf / Laboratory Notebook", "Clipboard Shelf / Cuaderno de laboratorio");
        assertModule(service, ModuleTextCatalog.compareResults(), "Compare Laboratory Results", "Comparar resultados del laboratorio");
        assertModule(service, ModuleTextCatalog.generic(), "📦 Batch Runner", "📦 Ejecutor por lotes");
    }

    @Test
    void fallbackAndTechnicalContentAreStable() {
        I18nService service = service();
        service.setPreference(LanguagePreference.EN);
        assertEquals("missing.ux15b.key", service.text("missing.ux15b.key"));
        String technical = "SHA-256|A1B2C3|-----BEGIN PUBLIC KEY-----";
        String en = service.text("technical", technical);
        service.setPreference(LanguagePreference.ES);
        String es = service.text("technical", technical);
        assertNotEquals(en, es);
        assertEquals(technical, en.substring(en.indexOf(": ") + 2));
        assertEquals(technical, es.substring(es.indexOf(": ") + 2));
    }

    @Test
    void migratedFxmlDocumentsRemainWellFormed() throws Exception {
        for (String name : new String[]{"process_designer", "history", "clipboard_shelf", "compare_results"}) {
            Path path = Path.of("src/main/resources/fxml/" + name + ".fxml");
            assertTrue(Files.exists(path), name);
            assertNotNull(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile()));
        }
    }

    private I18nService service() {
        return new I18nService(new AppSettings(temporaryDirectory.resolve("settings.json")),
                I18nService.BUNDLE_BASE_NAME, Locale.ENGLISH, getClass().getClassLoader());
    }

    private void assertModule(I18nService service, Map<String, String> catalog, String source, String spanish) {
        String key = catalog.get(source);
        assertNotNull(key, "Missing catalog key for " + source);
        service.setPreference(LanguagePreference.EN);
        assertEquals(source, service.text(key));
        service.setPreference(LanguagePreference.ES);
        assertEquals(spanish, service.text(key));
    }
}
