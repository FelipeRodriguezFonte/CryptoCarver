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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecializedI18nTest {
    @TempDir Path temporaryDirectory;

    @Test
    void allSpecializedModulesResolveEnglishAndSpanish() {
        I18nService service = service();
        assertModule(service, ModuleTextCatalog.pqc(), "🔑 PQC Key Generation", "🔑 Generación de claves PQC");
        assertModule(service, ModuleTextCatalog.xmlSecurity(), "📝 Sign XML (XAdES)", "📝 Firmar XML (XAdES)");
        assertModule(service, ModuleTextCatalog.wssSecurity(), "🔐 Sign SOAP (WSS)", "🔐 Firmar SOAP (WSS)");
        assertModule(service, ModuleTextCatalog.jose(), "JSON Web Token (JWT)", "JSON Web Token (JWT)");
    }

    @Test
    void everySpecializedCatalogEntryHasAnEnglishFallback() {
        I18nService service = service();
        service.setPreference(LanguagePreference.EN);
        for (Map<String, String> catalog : new Map[]{
                ModuleTextCatalog.pqc(), ModuleTextCatalog.xmlSecurity(),
                ModuleTextCatalog.wssSecurity(), ModuleTextCatalog.jose()}) {
            for (Map.Entry<String, String> entry : catalog.entrySet()) {
                assertEquals(entry.getKey(), service.text(entry.getValue()), entry.getValue());
            }
        }
    }

    @Test
    void missingKeysFallbackAndTechnicalArtifactsRemainIdentical() {
        I18nService service = service();
        service.setPreference(LanguagePreference.EN);
        assertEquals("missing.ux15c.key", service.text("missing.ux15c.key"));
        String artifact = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature";
        String english = service.text("technical", artifact);
        service.setPreference(LanguagePreference.ES);
        String spanish = service.text("technical", artifact);
        assertEquals(artifact, english.substring(english.indexOf(": ") + 2));
        assertEquals(artifact, spanish.substring(spanish.indexOf(": ") + 2));
    }

    @Test
    void actionableXmlAndWssErrorsResolveInBothLanguages() {
        I18nService service = service();
        service.setPreference(LanguagePreference.EN);
        assertEquals("Please paste XML content to verify", service.text("module.xml.error.pasteXml"));
        assertEquals("Please provide the SOAP XML to sign.", service.text("module.wss.error.soapToSign"));
        service.setPreference(LanguagePreference.ES);
        assertEquals("Pega el contenido XML que quieres verificar", service.text("module.xml.error.pasteXml"));
        assertEquals("Indica el XML SOAP que quieres firmar.", service.text("module.wss.error.soapToSign"));
    }

    @Test
    void specializedFxmlDocumentsRemainWellFormed() throws Exception {
        for (String name : new String[]{"pqc", "xml_security", "wss_security", "jose"}) {
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
