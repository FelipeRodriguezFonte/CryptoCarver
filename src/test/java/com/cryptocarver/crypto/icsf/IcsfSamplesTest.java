package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.EffectiveStrength;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sample tokens are the Python tool's, byte for byte, and they analyse.
 *
 * <p>{@code icsf/python-samples.json} is generated from {@code icsf_web.py} and
 * cross-checked against {@code icsf_qt.py}; it is not written by hand. Comparing
 * against it rather than against a second Java build of the same bytes is the point:
 * a transcription slip here would otherwise be copied into the expectation too.</p>
 */
class IcsfSamplesTest {

    private static Map<?, ?> python() throws IOException {
        try (InputStream in = IcsfSamplesTest.class.getResourceAsStream("/icsf/python-samples.json")) {
            assertNotNull(in, "icsf/python-samples.json must be on the test classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Map.class);
        }
    }

    @Test
    void theSingleSamplesAreByteForByteThePythonOnes() throws IOException {
        Map<?, ?> expected = python();

        assertEquals(expected.get("aesFixed"), IcsfHex.hex(IcsfSamples.aesFixed()));
        assertEquals(expected.get("variableLengthCipher"), IcsfHex.hex(IcsfSamples.variableLengthCipher()));
        assertEquals(expected.get("desTripleK1K2K1"), IcsfHex.hex(IcsfSamples.desTripleK1K2K1()));
        assertEquals(expected.get("pkaRsa2048"), IcsfHex.hex(IcsfSamples.pkaRsa2048()));
    }

    @Test
    void theSampleBatchIsThePythonOneWordForWordInSpanish() throws IOException {
        assertEquals(python().get("batchEs"), IcsfSamples.batch(Locale.forLanguageTag("es")));
    }

    @Test
    void onlyTheCommentLinesOfTheSampleBatchFollowTheLanguage() {
        List<String> english = IcsfSamples.batch(Locale.ENGLISH).lines().toList();
        List<String> spanish = IcsfSamples.batch(Locale.forLanguageTag("es")).lines().toList();

        assertEquals(english.size(), spanish.size());
        for (int index = 0; index < english.size(); index++) {
            if (english.get(index).startsWith("#")) {
                assertNotEquals(english.get(index), spanish.get(index), "comment line " + index);
            } else {
                assertEquals(english.get(index), spanish.get(index), "token line " + index);
            }
        }
    }

    @Test
    void everySampleAnalysesAsTheFormatItIsAnExampleOf() {
        ParseResult aes = IcsfTokenParser.parse(IcsfSamples.aesFixed());
        ParseResult variable = IcsfTokenParser.parse(IcsfSamples.variableLengthCipher());
        ParseResult des = IcsfTokenParser.parse(IcsfSamples.desTripleK1K2K1());
        ParseResult pka = IcsfTokenParser.parse(IcsfSamples.pkaRsa2048());

        assertEquals(TokenFamily.SYM_FIXED_AES, aes.tokenFamily());
        assertEquals(TokenFamily.SYM_VARIABLE, variable.tokenFamily(), variable.error());
        assertEquals("KEY1", variable.value(SummaryKey.KEY_NAME).orElseThrow().text());
        assertEquals(TokenFamily.SYM_FIXED_DES_INT, des.tokenFamily());
        // The point of this sample: 24 bytes that are only 2-key TDES.
        assertTrue(des.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.DOUBLE));
        assertEquals(TokenFamily.PKA, pka.tokenFamily(), pka.error());
        assertEquals("YES", pka.code(SummaryKey.PRIVATE_KEY_PRESENT, ""));
    }

    @Test
    void theSampleBatchBringsToLightWhatAnInventoryHasTo() {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfSamples.batch(Locale.ENGLISH));

        // Six labelled lines and one token in two host rows, all of them readable.
        assertEquals(7, report.total());
        assertEquals(0, report.failed().size());
        Set<FindingCode> raised = report.findings().stream()
                .map(IcsfBatchReport.AggregatedFinding::code).collect(Collectors.toSet());
        assertTrue(raised.containsAll(Arrays.asList(FindingCode.BYTE59_FUERA_DE_TABLA,
                FindingCode.DES_56_BITS, FindingCode.NO_EXPORTABLE)), "raised: " + raised);
    }
}
