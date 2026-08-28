package com.cryptocarver.crypto.icsf;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The field-by-field detail has to read in both languages.
 *
 * <p>The analyser stores keys, not words, so a missing bundle entry does not fail
 * anywhere — it just surfaces a bare {@code icsf.detail.something} in the middle
 * of a report. These tests walk a corpus covering every format and demand that
 * every piece of text it produces resolves in Spanish and in English.</p>
 */
class IcsfDetailI18nTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es");
    private static final List<Locale> LANGUAGES = List.of(Locale.ENGLISH, SPANISH);

    /** Tokens covering every format and the branches that produce their own text. */
    private static List<byte[]> corpus() {
        List<byte[]> tokens = new ArrayList<>();
        tokens.add(IcsfTestTokens.aesFixed());
        tokens.add(IcsfTestTokens.des("IMPORTER", 16));
        tokens.add(IcsfTestTokens.des("EXPORTER", 16, null, true, null));
        tokens.add(IcsfTestTokens.des("MAC", 16));
        tokens.add(IcsfTestTokens.des("PINVER", 16));
        tokens.add(IcsfTestTokens.des("IPINENC", 16));
        tokens.add(IcsfTestTokens.des("CIPHER", 24));
        tokens.add(IcsfTestTokens.des("IMPORTER", 24));
        tokens.add(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8));
        tokens.add(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24));
        tokens.add(IcsfTestTokens.des("IMPORTER", 16, 0x40, false, null));
        tokens.add(IcsfTestTokens.unmaterialisedInternalDes());
        tokens.add(IcsfTestTokens.truncatedDes());
        tokens.add(IcsfTestTokens.variableLength(true, true));
        tokens.add(IcsfTestTokens.variableLength(false, false));
        tokens.add(IcsfTestTokens.pkaPublicRsa());
        tokens.add(new byte[16]);

        byte[] cvLeft = IcsfTestTokens.hex("00427D0003410000");
        byte[] cvRight = IcsfTestTokens.hex("00427D0003210000");
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, true, 0, false));
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 3, false));
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 0, true));

        byte[] rkx = new byte[64];
        rkx[0] = 0x02;
        rkx[4] = 0x10;
        rkx[7] = 24;
        System.arraycopy("TESTRULE".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0,
                rkx, 40, 8);
        tokens.add(rkx);

        byte[] brokenCv = IcsfTestTokens.des("IMPORTER", 16);
        brokenCv[32] = 0x01;
        IcsfTestTokens.writeTvv(brokenCv);
        tokens.add(brokenCv);

        byte[] wrongTvv = IcsfTestTokens.des("IMPORTER", 16);
        wrongTvv[16] ^= 0xFF;
        tokens.add(wrongTvv);
        return tokens;
    }

    /** Every piece of translatable text one analysis produces. */
    private static List<IcsfText> textsOf(ParseResult result) {
        List<IcsfText> texts = new ArrayList<>();
        result.summary().values().forEach(value -> texts.add(value.detail()));
        for (IcsfSection section : result.sections()) {
            texts.add(section.title());
            for (IcsfSection.Field field : section.fields()) {
                texts.add(field.name());
                texts.add(field.value());
            }
            for (IcsfSection.Flag flag : section.flags()) {
                texts.add(flag.name());
                texts.add(flag.detail());
            }
        }
        result.warnings().forEach(warning -> texts.add(warning.message()));
        texts.addAll(result.provenanceNotes());
        return texts;
    }

    @Test
    void everyPieceOfDetailResolvesInBothLanguages() {
        Set<String> unresolved = new LinkedHashSet<>();
        for (Origin origin : Origin.values()) {
            for (byte[] token : corpus()) {
                ParseResult result = IcsfTokenParser.parse(token, origin);
                for (IcsfText text : textsOf(result)) {
                    for (Locale locale : LANGUAGES) {
                        if (!IcsfMessages.canResolve(text, locale)) {
                            unresolved.add(locale + " " + text.key());
                        }
                    }
                }
            }
        }
        assertTrue(unresolved.isEmpty(), "text with no bundle entry: " + unresolved);
    }

    @Test
    void noResolvedTextEverLeaksItsBundleKey() {
        for (Origin origin : Origin.values()) {
            for (byte[] token : corpus()) {
                ParseResult result = IcsfTokenParser.parse(token, origin);
                for (IcsfText text : textsOf(result)) {
                    for (Locale locale : LANGUAGES) {
                        String rendered = IcsfMessages.resolve(text, locale);
                        assertFalse(rendered.startsWith("icsf."),
                                "a bundle key reached the report: " + rendered);
                    }
                }
            }
        }
    }

    @Test
    void noResolvedTextShowsTheDoubledQuotesOfMessageFormat() {
        for (byte[] token : corpus()) {
            ParseResult result = IcsfTokenParser.parse(token, Origin.RAW_KDS);
            for (IcsfText text : textsOf(result)) {
                for (Locale locale : LANGUAGES) {
                    String rendered = IcsfMessages.resolve(text, locale);
                    // A doubled quote surviving into the output means the message was
                    // escaped for MessageFormat but never went through it.
                    assertFalse(rendered.contains("''"),
                            "doubled quote left in the output: " + rendered);
                    assertFalse(rendered.contains("{0}"),
                            "unsubstituted placeholder: " + rendered);
                }
            }
        }
    }

    @Test
    void theProvenanceNotesKeepTheirIndentation() {
        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.unmaterialisedInternalDes(), Origin.INFER);

        for (Locale locale : LANGUAGES) {
            List<String> notes = new ArrayList<>();
            for (IcsfText note : result.provenanceNotes()) {
                notes.add(IcsfMessages.resolve(note, locale));
            }
            // Properties strips leading whitespace unless it is escaped; the sub-items
            // of the infer list are indented on purpose to sit under their heading.
            assertTrue(notes.stream().anyMatch(note -> note.startsWith("  ")),
                    locale + ": the indented notes lost their indentation: " + notes);
        }
    }

    @Test
    void theSameTokenRendersDifferentlyInEachLanguageButMeansTheSame() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        ParseResult result = IcsfTokenParser.parse(token, Origin.INFER);

        String english = IcsfTokenReport.renderText(result, Origin.INFER, token, Locale.ENGLISH);
        String spanish = IcsfTokenReport.renderText(result, Origin.INFER, token, SPANISH);

        assertNotEquals(english, spanish);
        assertTrue(english.contains("Header and flags"), "English section title");
        assertTrue(spanish.contains("Cabecera y flags"), "Spanish section title");
        assertTrue(english.contains("Control Vector present"));
        assertTrue(spanish.contains("Control Vector presente"));

        // The verdict codes and the technical values are language-invariant, so both
        // reports have to agree on them exactly.
        assertTrue(english.contains("IMPORTER") && spanish.contains("IMPORTER"));
        assertTrue(english.contains(IcsfHex.hex(token, 32, 40)));
        assertTrue(spanish.contains(IcsfHex.hex(token, 32, 40)));
        assertTrue(english.contains("SYM_FIXED_DES_EXT") && spanish.contains("SYM_FIXED_DES_EXT"));
    }

    @Test
    void theBatchReportAlsoFollowsTheLanguage() {
        String input = String.join("\n",
                IcsfTestTokens.hex(IcsfTestTokens.des("IMPORTER", 16)),
                IcsfTestTokens.hex(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8)));
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(input, BatchInputFormat.LINE, Origin.INFER);

        String english = IcsfBatchRenderer.renderSummary(report, Locale.ENGLISH);
        String spanish = IcsfBatchRenderer.renderSummary(report, SPANISH);

        assertNotEquals(english, spanish);
        assertTrue(english.contains("Single-length DES key"), "English finding title");
        assertTrue(spanish.contains("Clave DES de longitud simple"), "Spanish finding title");
        // The codes stay put: they are identifiers that appear in the CSV.
        assertTrue(english.contains("DES-56-BITS") && spanish.contains("DES-56-BITS"));
    }

    @Test
    void theFullReportWithDetailRendersInSpanishThroughout() {
        String input = IcsfTestTokens.hex(IcsfTestTokens.des("IMPORTER", 16));
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(input, BatchInputFormat.LINE, Origin.RAW_KDS);

        String spanish = IcsfBatchRenderer.renderFull(report, true, SPANISH);

        assertTrue(spanish.contains("Cuerpo de la clave"));
        assertTrue(spanish.contains("Identificador de token"));
        assertTrue(spanish.contains("PROCEDENCIA INCOHERENTE"));
        assertFalse(spanish.contains("icsf."), "no bundle key may reach the report");
    }

    @Test
    void theCliPathStaysInEnglishWhateverTheDesktopLanguageIs() {
        // The CLI has no language preference; the default locale is the contract.
        assertEquals(Locale.ENGLISH, IcsfMessages.DEFAULT_LOCALE);

        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.des("IMPORTER", 16));
        assertEquals(IcsfMessages.resolve(result.value(SummaryKey.KEY_TYPE).orElseThrow().detail(),
                        Locale.ENGLISH),
                result.value(SummaryKey.KEY_TYPE).orElseThrow().text());
    }

    @Test
    void textThatIsDataRatherThanWordsIsPassedThroughUnchanged() {
        // Component patterns, hex and decoded names are values, not sentences.
        IcsfText pattern = IcsfText.raw("K1 = K3 != K2");
        for (Locale locale : LANGUAGES) {
            assertEquals("K1 = K3 != K2", IcsfMessages.resolve(pattern, locale));
        }
        assertEquals("", IcsfMessages.resolve(IcsfText.EMPTY, SPANISH));
        assertEquals("", IcsfMessages.resolve(null, SPANISH));
    }

    @Test
    void theCorpusReachesEveryFormatSoThisIsWorthSomething() {
        Set<TokenFamily> families = new LinkedHashSet<>();
        for (byte[] token : corpus()) {
            ParseResult result = IcsfTokenParser.parse(token);
            if (result.isOk()) families.add(result.tokenFamily());
        }
        assertTrue(families.containsAll(Arrays.asList(
                        TokenFamily.SYM_FIXED_AES, TokenFamily.SYM_FIXED_DES_INT,
                        TokenFamily.SYM_FIXED_DES_EXT, TokenFamily.RKX_DES_EXT,
                        TokenFamily.SYM_VARIABLE, TokenFamily.PKA, TokenFamily.NULL)),
                "the corpus must exercise every format: " + families);
    }
}
