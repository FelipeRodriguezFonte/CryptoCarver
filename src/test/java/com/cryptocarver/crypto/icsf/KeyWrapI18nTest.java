package com.cryptocarver.crypto.icsf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.crypto.icsf.keywrap.ControlVectorDefaults;
import com.cryptocarver.crypto.icsf.keywrap.ExternalToken;
import com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService;
import com.cryptocarver.crypto.icsf.keywrap.KeyWrapResult;
import com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Every reportable code has words in both languages.
 *
 * <p>Storing meaning instead of words only pays off if every key actually resolves. A
 * missing one is invisible until the moment a user switches language and finds a bundle
 * key staring back at them, so the operations are driven over inputs that between them
 * reach every note, verdict and enum-derived key, and each resulting text is resolved in
 * Spanish and in English.</p>
 */
class KeyWrapI18nTest {

    private static final Locale ES = Locale.forLanguageTag("es");
    private static final Locale EN = Locale.ENGLISH;

    private static byte[] h(String s) {
        return HexFormat.of().parseHex(s);
    }

    private static String x(byte[] b) {
        return HexFormat.of().formatHex(b).toUpperCase();
    }

    /** Results covering the interesting corners of all four operations. */
    private static List<KeyWrapResult> everyKindOfResult() {
        List<KeyWrapResult> results = new ArrayList<>();
        String key16 = "0123456789ABCDEFFEDCBA9876543210";
        String key24 = "0123456789ABCDEFFEDCBA98765432100F1E2D3C4B5A6978";
        String kek16 = "404142434445464748494A4B4C4D4E4F";

        // Export: both version-byte choices, every variant and mode, a single-DES KEK,
        // a key that collapses, and a triple-length key.
        for (KeyWrapScheme.Variant variant : KeyWrapScheme.Variant.values()) {
            for (KeyWrapScheme.Mode mode : KeyWrapScheme.Mode.values()) {
                for (boolean hostVersion : new boolean[] {true, false}) {
                    results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                            key16, kek16, "EXPORTER", "", variant, mode, false, hostVersion, "")));
                }
            }
        }
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                key24, kek16, "EXPORTER", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB,
                true, true, "1122334455667788")));
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                "0123456789ABCDEF", "0123456789ABCDEF", "MAC", "",
                KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, false, "")));
        // A key whose halves are equal collapses to single DES.
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                "0123456789ABCDEF0123456789ABCDEF", kek16, "EXPORTER", "",
                KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, true, "")));

        // Import: from a token, from a bare cryptogram, and an enhanced-wrapped token.
        ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType("EXPORTER", 16);
        byte[] cryptogram = KeyWrapScheme.wrap(h(key16), h(kek16), cv.left(), cv.right(),
                KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB).cryptogram();
        byte[] token = ExternalToken.build(cryptogram, cv.left(), cv.right(), false,
                ExternalToken.WRAP_ECB, false, true);
        results.add(IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                x(token), kek16, "", "EXPORTER", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, "")));
        results.add(IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                x(cryptogram), kek16, "", "EXPORTER", KeyWrapScheme.Variant.PLAIN, KeyWrapScheme.Mode.CBC, "")));
        for (int method : new int[] {ExternalToken.WRAP_ENH, ExternalToken.WRAPENH2, ExternalToken.WRAPENH3}) {
            byte[] enhanced = ExternalToken.build(cryptogram, cv.left(), cv.right(), false, method, false, true);
            results.add(IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                    x(enhanced), kek16, "", "EXPORTER", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, "")));
        }
        // A NOCV-marked token, an internal one, and one whose TVV was broken.
        results.add(IcsfKeyWrapService.inspect(x(ExternalToken.build(
                cryptogram, cv.left(), cv.right(), true, ExternalToken.WRAP_ECB, false, true))));
        byte[] internal = ExternalToken.build(cryptogram, cv.left(), cv.right(), false,
                ExternalToken.WRAP_ECB, false, true);
        internal[0] = 0x01;
        results.add(IcsfKeyWrapService.inspect(x(internal)));
        byte[] brokenTvv = token.clone();
        brokenTvv[63] ^= 0x5A;
        results.add(IcsfKeyWrapService.inspect(x(brokenTvv)));
        // A token carrying the key in the clear.
        results.add(IcsfKeyWrapService.inspect(x(ExternalToken.build(
                h(key16), cv.left(), cv.right(), false, ExternalToken.WRAP_ECB, true, true))));

        // Resolve: with a reference, with a KCV, and with nothing at all.
        results.add(IcsfKeyWrapService.resolve(new IcsfKeyWrapService.ResolveRequest(
                x(token), kek16, key16, "", "", "EXPORTER")));
        results.add(IcsfKeyWrapService.resolve(new IcsfKeyWrapService.ResolveRequest(
                x(cryptogram), kek16, "", "08D7B4", "", "EXPORTER")));
        results.add(IcsfKeyWrapService.resolve(new IcsfKeyWrapService.ResolveRequest(
                x(cryptogram), kek16, "", "", "", "")));
        results.add(IcsfKeyWrapService.resolve(new IcsfKeyWrapService.ResolveRequest(
                x(token), kek16, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", "", "", "EXPORTER")));

        // Every input error, so the refusals are translated too.
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                "", kek16, "EXPORTER", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, false, "")));
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                "0123", kek16, "EXPORTER", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, false, "")));
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                key16, kek16, "EXPORTER", "0011", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, false, "")));
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                key16, kek16, "DKYGENKY", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, false, "0011")));
        results.add(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                key24, kek16, "IPINENC", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, false, "")));
        results.add(IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                "001122", kek16, "", "EXPORTER", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, "")));
        results.add(IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                x(cryptogram), kek16, "", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, "")));
        results.add(IcsfKeyWrapService.inspect("001122"));
        return results;
    }

    /** Every IcsfText a result can show, flattened. */
    private static List<IcsfText> textsOf(KeyWrapResult result) {
        List<IcsfText> texts = new ArrayList<>();
        if (result.error() != null) texts.add(result.error());
        result.summary().forEach(row -> {
            texts.add(row.label());
            texts.add(row.value());
        });
        result.steps().forEach(step -> {
            texts.add(step.title());
            texts.add(step.detail());
        });
        result.notes().forEach(note -> {
            texts.add(note.title());
            texts.add(note.text());
        });
        result.candidates().forEach(candidate -> {
            texts.add(candidate.scheme());
            texts.addAll(candidate.equivalentSchemes());
        });
        return texts;
    }

    @Test
    void everyTextTheOperationsProduceResolvesInBothLanguages() {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (KeyWrapResult result : everyKindOfResult()) {
            for (IcsfText text : textsOf(result)) {
                checked++;
                for (Locale locale : List.of(ES, EN)) {
                    if (!IcsfMessages.canResolve(text, locale)) {
                        missing.add(locale.getLanguage() + ": " + text.key());
                    }
                }
            }
        }
        assertTrue(checked > 200, "the sweep should reach a few hundred texts, reached " + checked);
        assertTrue(missing.isEmpty(), "bundle keys with no words: " + missing.stream().distinct().toList());
    }

    @Test
    void theSameResultReadsDifferentlyInEachLanguage() {
        // Guards against a bundle that resolves because the Spanish file quietly holds the
        // English text: switching language would then appear to work and change nothing.
        KeyWrapResult result = IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                "0123456789ABCDEFFEDCBA9876543210", "404142434445464748494A4B4C4D4E4F",
                "EXPORTER", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, false, ""));

        long differing = result.notes().stream()
                .filter(note -> !IcsfMessages.resolve(note.text(), ES)
                        .equals(IcsfMessages.resolve(note.text(), EN)))
                .count();
        assertTrue(differing >= 3,
                "the Spanish and English notes should differ; only " + differing + " did");
    }

    @Test
    void everyKeyTypeAndKeyFormHasWordsInBothLanguages() {
        for (String type : ControlVectorDefaults.keyTypes()) {
            for (int length : ControlVectorDefaults.lengthsFor(type)) {
                ControlVectorDefaults.Pair pair = ControlVectorDefaults.forType(type, length);
                IcsfText form = IcsfText.of("icsf.keywrap.keyForm."
                        + ControlVectorDefaults.keyForm(pair.left()));
                for (Locale locale : List.of(ES, EN)) {
                    assertTrue(IcsfMessages.canResolve(form, locale),
                            "no words for " + form.key() + " in " + locale);
                }
            }
        }
    }
}
