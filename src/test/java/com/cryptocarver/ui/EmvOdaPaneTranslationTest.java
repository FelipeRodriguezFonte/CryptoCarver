package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * English has leaked into the Spanish window three times now, always the same
 * way: a new pane's captions and buttons are written straight into the FXML and
 * nobody adds them to the catalogue. The wallet pane has this guard; the EMV
 * pane gained one when offline data authentication went in.
 */
class EmvOdaPaneTranslationTest {

    private static final Pattern TEXT_ATTRIBUTE = Pattern
            .compile("<(?:Label|TitledPane|Button)[^>]*?text=\"([^\"]+)\"", Pattern.DOTALL);

    @Test
    void everySentenceTheEmvPaneShowsIsTranslatable() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/emv.fxml"), StandardCharsets.UTF_8);
        String spanish = Files.readString(Path.of("src/main/resources/i18n/messages_es.properties"),
                StandardCharsets.UTF_8);
        Map<String, String> catalog = ModuleTextCatalog.emv();

        List<String> untranslated = new ArrayList<>();
        Matcher matcher = TEXT_ATTRIBUTE.matcher(fxml);
        while (matcher.find()) {
            String text = matcher.group(1)
                    .replace("&quot;", "\"").replace("&apos;", "'")
                    .replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&amp;", "&");
            boolean button = matcher.group(0).startsWith("<Button");
            // Only prose and buttons. Short captions are shared across modules or
            // are field names like "TVR:" that stay as they are in any language.
            if (!button && text.length() < 25) {
                continue;
            }
            String key = catalog.get(text);
            if (key == null || spanish.lines().noneMatch(line -> line.startsWith(key + "="))) {
                untranslated.add(text.length() > 60 ? text.substring(0, 60) + "..." : text);
            }
        }

        assertTrue(untranslated.isEmpty(),
                "These strings would show in English in a Spanish window: " + untranslated);
    }

    /** The runtime strings the ODA handlers produce never appear in the FXML,
     *  so the guard above cannot see them. */
    @Test
    void theOdaRuntimeMessagesAreTranslated() throws Exception {
        for (String bundle : List.of("messages.properties", "messages_es.properties")) {
            String text = Files.readString(Path.of("src/main/resources/i18n").resolve(bundle),
                    StandardCharsets.UTF_8);
            for (String key : List.of("module.emv.oda.testCardIssued", "module.emv.oda.noIssuerKey",
                    "module.emv.oda.status", "module.emv.oda.error")) {
                assertTrue(text.lines().anyMatch(line -> line.startsWith(key + "=")),
                        "Missing " + key + " in " + bundle);
            }
        }
    }
}
