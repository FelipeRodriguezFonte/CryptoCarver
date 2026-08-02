package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentsFormatsI18nTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesEnglishAndSpanishForEveryUx15dModule() {
        I18nService service = service();
        assertModule(service, ModuleTextCatalog.payments(), "💳 CVV Operations (CVV/CVV2/iCVV)", "💳 Operaciones CVV (CVV/CVV2/iCVV)");
        assertModule(service, ModuleTextCatalog.emv(), "🔬 EMV TLV Inspector", "🔬 Inspector EMV TLV");
        assertModule(service, ModuleTextCatalog.openPgp(), "OpenPGP / GPG Laboratory", "Laboratorio OpenPGP / GPG");
        assertModule(service, ModuleTextCatalog.pades(), "PDF Advanced Electronic Signatures (PAdES)", "Firmas electrónicas avanzadas PDF (PAdES)");
        assertModule(service, ModuleTextCatalog.asic(), "ASiC Containers", "Contenedores ASiC");
    }

    @Test
    void missingKeyIsSafeAndTechnicalArtifactIsLanguageInvariant() {
        I18nService service = service();
        assertEquals("missing.ux15d.key", service.text("missing.ux15d.key"));

        String artifact = "9F02060000000001005F2A020978|KSN=FFFF9876543210E00001|-----BEGIN PGP MESSAGE-----";
        service.setPreference(LanguagePreference.EN);
        String english = service.text("technical", artifact);
        service.setPreference(LanguagePreference.ES);
        String spanish = service.text("technical", artifact);

        assertEquals(artifact, english.substring(english.indexOf(": ") + 2));
        assertEquals(artifact, spanish.substring(spanish.indexOf(": ") + 2));
    }

    @Test
    void everyUx15dCatalogEntryHasAnEnglishFallback() {
        I18nService service = service();
        service.setPreference(LanguagePreference.EN);
        for (Map<String, String> catalog : new Map[]{
                ModuleTextCatalog.payments(), ModuleTextCatalog.emv(), ModuleTextCatalog.openPgp(),
                ModuleTextCatalog.pades(), ModuleTextCatalog.asic()}) {
            for (Map.Entry<String, String> entry : catalog.entrySet()) {
                assertEquals(entry.getKey(), service.text(entry.getValue()), entry.getValue());
            }
        }
    }

    @Test
    void allFxmlForUx15dRemainWellFormed() {
        for (String resource : new String[]{"/fxml/payments.fxml", "/fxml/emv.fxml", "/fxml/openpgp.fxml",
                "/fxml/pades.fxml", "/fxml/asic.fxml"}) {
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertTrue(input != null, resource);
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
                assertTrue(document.getDocumentElement() != null, resource);
            } catch (Exception error) {
                throw new AssertionError("Invalid XML: " + resource, error);
            }
        }
    }

    private I18nService service() {
        return new I18nService(new AppSettings(temporaryDirectory.resolve("settings.json")),
                I18nService.BUNDLE_BASE_NAME, Locale.ENGLISH, getClass().getClassLoader());
    }

    private void assertModule(I18nService service, Map<String, String> catalog,
                              String englishSource, String spanishExpected) {
        String key = catalog.get(englishSource);
        assertTrue(key != null && !key.isBlank(), "Missing catalog key: " + englishSource);
        service.setPreference(LanguagePreference.EN);
        assertEquals(englishSource, service.text(key));
        service.setPreference(LanguagePreference.ES);
        assertEquals(spanishExpected, service.text(key));
    }
}
