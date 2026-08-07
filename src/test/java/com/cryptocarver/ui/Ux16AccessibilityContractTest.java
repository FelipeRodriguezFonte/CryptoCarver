package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ux16AccessibilityContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void SpanishAndEnglishBundlesKeepTheSameKeys() throws Exception {
        Properties english = load("/i18n/messages.properties");
        Properties spanish = load("/i18n/messages_es.properties");
        assertEquals(new TreeSet<>(english.stringPropertyNames()),
                new TreeSet<>(spanish.stringPropertyNames()));
    }

    @Test
    void representativeShellControlsExposeAccessibleNamesAndHelp() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(getClass().getResourceAsStream("/fxml/main-view-modern.fxml"));
        String xml = new String(getClass().getResourceAsStream("/fxml/main-view-modern.fxml").readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(xml.contains("fx:id=\"inputFormatCombo\""));
        assertTrue(xml.contains("accessibleText=\"Payload format selector\""));
        assertTrue(xml.contains("accessibleHelp=\"Select the format for the operation payload.\""));
        assertTrue(xml.contains("fx:id=\"resultCopyButton\""));
        assertTrue(xml.contains("fx:id=\"inspectorToggleButton\""));
        assertEquals("StackPane", document.getDocumentElement().getNodeName());
    }

    @Test
    void technicalArgumentsRemainCallerOwnedAcrossBothLocales() {
        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                java.util.Locale.ENGLISH, getClass().getClassLoader());
        String technical = "AES-GCM|A1B2C3|-----BEGIN PUBLIC KEY-----|jwt";
        service.setPreference(LanguagePreference.EN);
        String english = service.text("technical", technical);
        service.setPreference(LanguagePreference.ES);
        String spanish = service.text("technical", technical);
        assertTrue(english.endsWith(technical));
        assertTrue(spanish.endsWith(technical));
    }

    private static Properties load(String resource) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Ux16AccessibilityContractTest.class.getResourceAsStream(resource)) {
            properties.load(input);
        }
        return properties;
    }
}
