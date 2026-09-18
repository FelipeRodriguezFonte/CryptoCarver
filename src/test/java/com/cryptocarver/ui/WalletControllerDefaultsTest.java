package com.cryptocarver.ui;

import com.cryptocarver.crypto.MdocOperations;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Wallet pane offers before anyone touches it.
 *
 * <p>The combo defaults are worth pinning rather than eyeballing. An algorithm
 * selector that silently starts on something other than the first entry sends a
 * user's EC key into an RSA-PSS signer, and the failure that comes back talks
 * about key types rather than about the combo — so the default is asserted here
 * instead of being trusted to a screenshot.</p>
 */
class WalletControllerDefaultsTest {

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (Exception alreadyRunning) {
            // The toolkit is process-wide; another test class may have started it.
        }
    }

    @Test
    void combosOfferExactlyWhatTheSpecificationsAllow() throws Exception {
        WalletController controller = new WalletController();
        set(controller, "walletContainer", new VBox());
        ComboBox<String> sdJwtAlgo = install(controller, "sdJwtAlgoCombo");
        ComboBox<String> statusAlgo = install(controller, "statusListAlgoCombo");
        ComboBox<String> mdocDigest = install(controller, "mdocDigestCombo");
        ComboBox<String> statusBits = install(controller, "statusListBitsCombo");
        ComboBox<String> cborView = install(controller, "cborViewCombo");
        TextField docType = installField(controller, "mdocDocTypeField");
        TextField validity = installField(controller, "mdocValidityField");
        validity.setText("365");

        controller.initialize(null, null);

        assertEquals("ES256", sdJwtAlgo.getValue(),
                "The signing combo must open on ES256, not on whichever entry happens to be first later");
        assertEquals("ES256", statusAlgo.getValue());

        // ISO/IEC 18013-5 Table 24 allows these three and no others.
        assertEquals(List.of("SHA-256", "SHA-384", "SHA-512"), mdocDigest.getItems());
        assertEquals("SHA-256", mdocDigest.getValue());

        // The Token Status List specification defines 1, 2, 4 and 8.
        assertEquals(List.of("1", "2", "4", "8"), statusBits.getItems());
        assertEquals("1", statusBits.getValue());

        assertEquals(List.of("tree", "diagnostic", "summary"), cborView.getItems());
        assertEquals(MdocOperations.MDL_DOCTYPE, docType.getText());
    }

    /** The prompt of a TextArea is drawn on one line, so a multi-line example
     *  runs its entries together. They are comma separated instead, which the
     *  parser accepts too. */
    @Test
    void multiValuePromptsAreReadableOnOneLine() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/wallet.fxml"), StandardCharsets.UTF_8);
        assertFalse(fxml.contains("&#10;"),
                "A newline in a TextArea promptText renders as nothing and glues the examples together");
    }

    @Test
    void everyPaneTitleIsTranslated() throws Exception {
        String es = Files.readString(Path.of("src/main/resources/i18n/messages_es.properties"),
                StandardCharsets.UTF_8);
        for (String title : List.of("Issue", "Present", "Verify", "Inspect",
                "Verify and inspect", "Resolve", "From JSON")) {
            String key = ModuleTextCatalog.wallet().get(title);
            assertNotNull(key, "Pane title '" + title + "' has no catalog entry, so it stays English");
            assertTrue(es.lines().anyMatch(line -> line.startsWith(key + "=")),
                    "Missing Spanish text for " + key);
        }
    }

    /**
     * Every sentence the pane shows has to be translatable. Two rounds of this
     * module shipped English text into a Spanish window — a pane title once, a
     * helper paragraph later — because a string was added to the FXML and not
     * to the catalog. Checking the FXML against the catalog catches the next
     * one without anyone having to launch the app in Spanish and read it.
     */
    @Test
    void everySentenceThePaneShowsIsTranslatable() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/wallet.fxml"), StandardCharsets.UTF_8);
        String spanish = Files.readString(Path.of("src/main/resources/i18n/messages_es.properties"),
                StandardCharsets.UTF_8);
        java.util.Map<String, String> catalog = ModuleTextCatalog.wallet();

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<(?:Label|TitledPane|Button)[^>]*?text=\"([^\"]+)\"", java.util.regex.Pattern.DOTALL)
                .matcher(fxml);
        java.util.List<String> untranslated = new java.util.ArrayList<>();
        while (matcher.find()) {
            // The catalogue is keyed by the text JavaFX renders, so the XML
            // entities the attribute carries have to come off first.
            String text = matcher.group(1)
                    .replace("&quot;", "\"").replace("&apos;", "'")
                    .replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&amp;", "&");
            // Short labels are field captions handled by the shared catalogue or
            // are proper nouns; the prose is what this is about.
            if (text.length() < 25) {
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

    /** The navigation rail label comes from `nav.<icon>`; without it the rail
     *  shows the raw key. This was caught by the app logging a missing key on
     *  its first launch. */
    @Test
    void theNavigationRailLabelIsTranslated() throws Exception {
        for (String bundle : List.of("messages.properties", "messages_es.properties")) {
            String text = Files.readString(Path.of("src/main/resources/i18n").resolve(bundle),
                    StandardCharsets.UTF_8);
            assertTrue(text.lines().anyMatch(line -> line.startsWith("nav.wallet=")),
                    "Missing nav.wallet in " + bundle);
        }
        assertEquals("wallet", NavigationRail.Section.WALLET.getIcon());
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> install(WalletController controller, String fieldName) throws Exception {
        ComboBox<String> combo = new ComboBox<>();
        set(controller, fieldName, combo);
        return combo;
    }

    private static TextField installField(WalletController controller, String fieldName) throws Exception {
        TextField field = new TextField();
        set(controller, fieldName, field);
        return field;
    }

    private static void set(WalletController controller, String fieldName, Object value) throws Exception {
        Field field = WalletController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
