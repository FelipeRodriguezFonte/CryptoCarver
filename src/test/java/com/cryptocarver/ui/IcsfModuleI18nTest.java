package com.cryptocarver.ui;

import com.cryptocarver.crypto.icsf.FindingCode;
import com.cryptocarver.crypto.icsf.InventoryColumn;
import com.cryptocarver.crypto.icsf.SummaryKey;
import com.cryptocarver.crypto.icsf.TokenFamily;
import com.cryptocarver.crypto.icsf.IcsfVocabulary;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every code the ICSF analyser can report has to read as something in both languages.
 *
 * <p>The core deliberately emits language-invariant codes so the batch layer cannot be
 * broken by a translation. The flip side is that if a code has no bundle entry the user
 * sees a bare identifier, which these tests are here to prevent.</p>
 */
class IcsfModuleI18nTest {

    private static final List<LanguagePreference> LANGUAGES =
            List.of(LanguagePreference.EN, LanguagePreference.ES);

    private static String text(LanguagePreference language, String key) {
        I18nService.getInstance().setPreference(language);
        return I18nService.getInstance().text(key);
    }

    private static boolean has(String key) {
        return I18nService.getInstance().getBundle().containsKey(key);
    }

    @Test
    void everyFindingCarriesATitleAndAnExplanationInBothLanguages() {
        List<String> missing = new ArrayList<>();
        for (LanguagePreference language : LANGUAGES) {
            I18nService.getInstance().setPreference(language);
            for (FindingCode code : FindingCode.values()) {
                if (!has(code.titleKey())) missing.add(language + " " + code.titleKey());
                if (!has(code.detailKey())) missing.add(language + " " + code.detailKey());
            }
        }
        assertTrue(missing.isEmpty(), "findings without text: " + missing);

        // A finding whose explanation is missing would leave the report unreadable
        // without the manual, which is the whole point of the catalogue.
        for (LanguagePreference language : LANGUAGES) {
            for (FindingCode code : FindingCode.values()) {
                String detail = text(language, code.detailKey());
                assertNotEquals(code.detailKey(), detail, "untranslated: " + code);
                assertTrue(detail.length() > 30, "explanation too short for " + code + ": " + detail);
            }
        }
    }

    @Test
    void theByte59ExplanationKeepsItsPointInBothLanguages() {
        for (LanguagePreference language : LANGUAGES) {
            String detail = text(language, FindingCode.BYTE59_FUERA_DE_TABLA.detailKey());

            // The distinction that matters: on older ICSF levels that byte was subdivided,
            // so a value outside today's table can be legitimate. Saying "corrupt token"
            // instead is the difference between repairing a byte and running a key ceremony.
            assertTrue(detail.contains("SUBDIVID"), language + ": " + detail);
            assertTrue(detail.toLowerCase().contains("documentation current")
                            || detail.toLowerCase().contains("documentacion vigente"),
                    language + " must send the reader to the documentation of the day: " + detail);
        }
    }

    @Test
    void everySummaryDimensionHasALabelInBothLanguages() {
        List<String> missing = new ArrayList<>();
        for (LanguagePreference language : LANGUAGES) {
            I18nService.getInstance().setPreference(language);
            for (SummaryKey key : SummaryKey.values()) {
                if (!has(key.labelKey())) missing.add(language + " " + key.labelKey());
            }
        }
        assertTrue(missing.isEmpty(), "dimensions without a label: " + missing);
    }

    @Test
    void everyInventoryColumnHasAHeaderInBothLanguages() {
        List<String> missing = new ArrayList<>();
        for (LanguagePreference language : LANGUAGES) {
            I18nService.getInstance().setPreference(language);
            for (InventoryColumn column : InventoryColumn.values()) {
                if (!has(column.headerKey())) missing.add(language + " " + column.headerKey());
            }
        }
        assertTrue(missing.isEmpty(), "columns without a header: " + missing);
    }

    @Test
    void everyClosedVocabularyValueReadsAsSomethingInBothLanguages() {
        Map<SummaryKey, Object[]> vocabularies = Map.of(
                SummaryKey.SCOPE, IcsfVocabulary.Scope.values(),
                SummaryKey.ALGORITHM, IcsfVocabulary.Algorithm.values(),
                SummaryKey.MATERIAL_STATE, IcsfVocabulary.MaterialState.values(),
                SummaryKey.TVV, IcsfVocabulary.TvvState.values(),
                SummaryKey.MKVP, IcsfVocabulary.MkvpState.values(),
                SummaryKey.CONTROL_VECTOR, IcsfVocabulary.CvState.values(),
                SummaryKey.EXPORTABILITY, IcsfVocabulary.Exportability.values(),
                SummaryKey.KEY_LENGTH, IcsfVocabulary.DesKeyForm.values(),
                SummaryKey.EFFECTIVE_STRENGTH, IcsfVocabulary.EffectiveStrength.values(),
                SummaryKey.WRAPPING, IcsfVocabulary.WrapMethod.values());

        List<String> missing = new ArrayList<>();
        for (LanguagePreference language : LANGUAGES) {
            I18nService.getInstance().setPreference(language);
            for (Map.Entry<SummaryKey, Object[]> entry : vocabularies.entrySet()) {
                for (Object value : entry.getValue()) {
                    String code = ((Enum<?>) value).name();
                    if (!has(entry.getKey().valueKey(code))) {
                        missing.add(language + " " + entry.getKey().valueKey(code));
                    }
                }
            }
            for (TokenFamily family : TokenFamily.values()) {
                // UNKNOWN only ever appears on a failed analysis, which has no summary card.
                if (family == TokenFamily.UNKNOWN) continue;
                if (!has(SummaryKey.FAMILY.valueKey(family.name()))) {
                    missing.add(language + " " + SummaryKey.FAMILY.valueKey(family.name()));
                }
            }
        }
        assertTrue(missing.isEmpty(), "verdict codes with no reading: " + missing);
    }

    @Test
    void theResolverTranslatesClosedVocabulariesAndLeavesTechnicalIdentifiersAlone() {
        I18nService.getInstance().setPreference(LanguagePreference.ES);
        assertEquals("EN CLARO", IcsfTextResolver.value(SummaryKey.MATERIAL_STATE, "CLEAR"));
        assertEquals("INVALIDO", IcsfTextResolver.value(SummaryKey.TVV, "INVALID"));
        assertEquals("Ambito", IcsfTextResolver.dimension(SummaryKey.SCOPE));

        I18nService.getInstance().setPreference(LanguagePreference.EN);
        assertEquals("IN THE CLEAR", IcsfTextResolver.value(SummaryKey.MATERIAL_STATE, "CLEAR"));
        assertEquals("Scope", IcsfTextResolver.dimension(SummaryKey.SCOPE));

        // Open vocabularies are technical identifiers and must read the same everywhere:
        // a Table 676 key type and an RSA modulus size are not words to translate.
        for (LanguagePreference language : LANGUAGES) {
            I18nService.getInstance().setPreference(language);
            assertEquals("IMPORTER", IcsfTextResolver.value(SummaryKey.KEY_TYPE, "IMPORTER"));
            assertEquals("PINVER", IcsfTextResolver.value(SummaryKey.KEY_TYPE, "PINVER"));
            assertEquals("2048", IcsfTextResolver.value(SummaryKey.KEY_LENGTH, "2048"));
            assertEquals("128", IcsfTextResolver.value(SummaryKey.KEY_LENGTH, "128"));
        }
    }

    @Test
    void theModuleCatalogResolvesEveryEntryAndMatchesItsEnglishSource() {
        Map<String, String> catalog = ModuleTextCatalog.icsf();
        assertFalse(catalog.isEmpty());

        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            String source = entry.getKey();
            String key = entry.getValue();
            assertNotEquals(source, key, "catalog key must not be the source text");

            String english = text(LanguagePreference.EN, key);
            String spanish = text(LanguagePreference.ES, key);
            assertNotEquals(key, english, "missing English text for " + key);
            assertNotEquals(key, spanish, "missing Spanish text for " + key);

            // Entries owned by this slice must match the literal in the FXML, or the
            // pane would show one thing in English and another after a language switch.
            if (key.startsWith("icsf.")) {
                assertEquals(source, english, "English bundle must equal the FXML literal for " + key);
            }
        }
    }

    @Test
    void theModuleTitleAndSecurityNoticeAreTranslated() {
        String titleKey = ModuleTextCatalog.icsf().get("🖥 ICSF / CCA Key Token Analyzer");
        assertEquals("icsf.token.title", titleKey);
        assertTrue(text(LanguagePreference.ES, titleKey).contains("Analizador"));

        String securityKey = "icsf.token.security";
        for (LanguagePreference language : LANGUAGES) {
            String notice = text(language, securityKey);
            assertNotEquals(securityKey, notice);
            assertTrue(notice.contains("master key"), language + ": " + notice);
            // The warning has to say both things: nothing is decrypted, and the output
            // still carries whole tokens.
            assertTrue(notice.toLowerCase().contains("hexadecimal"), language + ": " + notice);
        }
    }

    @Test
    void thisSliceIsRegisteredSoNavigationCanResolveItsPaneTitle() {
        assertTrue(ModuleTextCatalog.allModules().stream()
                        .anyMatch(module -> module.containsKey("🖥 ICSF / CCA Key Token Analyzer")),
                "the slice must be in allModules() or navigating to a translated pane title fails");
    }
}
