package com.cryptocarver.crypto.icsf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.crypto.icsf.keywrap.ControlVectorDefaults;
import com.cryptocarver.crypto.icsf.keywrap.Des;
import com.cryptocarver.crypto.icsf.keywrap.ExternalToken;
import com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService;
import com.cryptocarver.crypto.icsf.keywrap.KeyWrapResult;
import com.cryptocarver.crypto.icsf.keywrap.DesKeyCheck;
import com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The wrapping is checked against fixed vectors, not against itself.
 *
 * <p>{@code keywrap-vectors.json} holds a cryptogram for every combination of key length,
 * key type, KEK variant and cipher mode, together with the Table 676 control vectors and
 * the check values. Byte layout is the kind of thing that is either exactly right or
 * useless, and a test that recomputes an expectation with the code under test proves
 * nothing, so the expected bytes are fixed data rather than derived here.</p>
 */
class KeyWrapVectorTest {

    private static final JsonObject VECTORS = load();

    private static JsonObject load() {
        var stream = KeyWrapVectorTest.class.getResourceAsStream("/icsf/keywrap-vectors.json");
        assertTrue(stream != null, "keywrap-vectors.json is missing from the test resources");
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static byte[] h(String s) {
        return HexFormat.of().parseHex(s);
    }

    private static String x(byte[] b) {
        return HexFormat.of().formatHex(b).toUpperCase();
    }

    private static JsonArray group(String name) {
        JsonArray array = VECTORS.getAsJsonArray(name);
        assertTrue(array != null && array.size() > 0, "no vectors in group " + name);
        return array;
    }

    @TestFactory
    Stream<DynamicTest> defaultControlVectorsMatchTable676() {
        return Stream.of(group("cv")).flatMap(a -> a.asList().stream()).map(element -> {
            JsonObject v = element.getAsJsonObject();
            String type = v.get("tipo").getAsString();
            int length = v.get("longitud").getAsInt();
            return DynamicTest.dynamicTest(type + " / " + length + " bytes", () -> {
                ControlVectorDefaults.Pair pair = ControlVectorDefaults.forType(type, length);
                assertEquals(v.get("izq").getAsString(), x(pair.left()), "left CV");
                assertEquals(v.get("der").getAsString(), x(pair.right()), "right CV");
                assertEquals(v.get("keyForm").getAsString().isEmpty() ? "" : v.get("keyForm").getAsString(),
                        describeKeyForm(pair.left()), "key form");
            });
        });
    }

    /** The vectors record the key form as Spanish prose; the code reports a stable code. */
    private static String describeKeyForm(byte[] cv) {
        return switch (ControlVectorDefaults.keyForm(cv)) {
            case "SIMPLE" -> "simple";
            case "LEFT_HALF_OF_DOUBLE" -> "mitad izquierda de doble";
            case "RIGHT_HALF_OF_DOUBLE" -> "mitad derecha de doble";
            case "TRIPLE" -> "triple";
            case "LEFT_HALF_GUARANTEED_UNIQUE" -> "mitad izq. de doble, partes garantizadas distintas";
            case "RIGHT_HALF_GUARANTEED_UNIQUE" -> "mitad der. de doble, partes garantizadas distintas";
            case "TRIPLE_GUARANTEED_UNIQUE" -> "triple, partes garantizadas distintas";
            default -> "reservado";
        };
    }

    @Test
    void everyKeyTypeInTheVectorsIsKnownToTheTable() {
        for (var element : group("cv")) {
            String type = element.getAsJsonObject().get("tipo").getAsString();
            assertTrue(ControlVectorDefaults.knows(type), "Table 676 is missing key type " + type);
        }
    }

    @TestFactory
    Stream<DynamicTest> wrappingMatchesTheVectorsByteForByte() {
        return group("wrap").asList().stream().map(element -> {
            JsonObject v = element.getAsJsonObject();
            String label = v.get("tipo").getAsString() + " " + v.get("clave").getAsString().length() / 2
                    + "B key / " + v.get("kek").getAsString().length() / 2 + "B KEK / "
                    + v.get("variante").getAsString() + " / " + v.get("modo").getAsString();
            return DynamicTest.dynamicTest(label, () -> {
                byte[] key = h(v.get("clave").getAsString());
                byte[] kek = h(v.get("kek").getAsString());
                byte[] cvLeft = h(v.get("cvIzq").getAsString());
                byte[] cvRight = h(v.get("cvDer").getAsString());
                var variant = variantOf(v.get("variante").getAsString());
                var mode = modeOf(v.get("modo").getAsString());

                var wrapped = KeyWrapScheme.wrap(key, kek, cvLeft, cvRight, variant, mode);
                assertEquals(v.get("cripto").getAsString(), x(wrapped.cryptogram()), "cryptogram");

                byte[] back = KeyWrapScheme.unwrap(wrapped.cryptogram(), kek, cvLeft, cvRight, variant, mode);
                assertEquals(v.get("clave").getAsString(), x(back), "unwrap must return the key");
            });
        });
    }

    private static KeyWrapScheme.Variant variantOf(String name) {
        return switch (name) {
            case "cv" -> KeyWrapScheme.Variant.CV;
            case "plano" -> KeyWrapScheme.Variant.PLAIN;
            case "cv-invertido" -> KeyWrapScheme.Variant.CV_SWAPPED;
            default -> throw new IllegalArgumentException("unknown variant " + name);
        };
    }

    private static KeyWrapScheme.Mode modeOf(String name) {
        return "cbc".equals(name) ? KeyWrapScheme.Mode.CBC : KeyWrapScheme.Mode.ECB;
    }

    @TestFactory
    Stream<DynamicTest> checkValuesMatchTheVectors() {
        return group("kcv").asList().stream().map(element -> {
            JsonObject v = element.getAsJsonObject();
            String keyHex = v.get("clave").getAsString();
            return DynamicTest.dynamicTest(keyHex.length() / 2 + "-byte key", () -> {
                byte[] key = h(keyHex);
                assertEquals(v.get("encZero3").getAsString(), x(DesKeyCheck.encZero(key, 3)), "KCV 3 bytes");
                assertEquals(v.get("encZero4").getAsString(), x(DesKeyCheck.encZero(key, 4)), "ENC-ZERO 4 bytes");
                assertEquals(v.get("vpIbm").getAsString(), x(DesKeyCheck.ibmVerificationPattern(key)),
                        "CSNBKYT verification pattern");
                assertEquals(v.get("paridadAjustada").getAsString(), x(DesKeyCheck.adjustToOddParity(key)),
                        "odd-parity adjustment");
                assertEquals(v.get("paridadUniforme").getAsString(), parityWord(key), "uniform parity");
            });
        });
    }

    private static String parityWord(byte[] key) {
        return switch (DesKeyCheck.uniformParity(key)) {
            case ODD -> "impar";
            case EVEN -> "par";
            case MIXED -> "";
        };
    }

    @Test
    void theKcvAndTheIbmVerificationPatternAreDifferentNumbers() {
        // Quoting one of these against the other never agrees, which is half the mismatches.
        byte[] key = h("0123456789ABCDEFFEDCBA9876543210");
        assertTrue(!x(DesKeyCheck.encZero(key, 4)).equals(x(DesKeyCheck.ibmVerificationPattern(key))),
                "KCV and CSNBKYT verification pattern must not coincide");
    }

    @Test
    void tripleLengthKeysReuseTheLeftCvOnTheThirdPart() {
        // APG p. 20: "For triple-length keys, the two control vectors are the same".
        byte[] key = h("0123456789ABCDEFFEDCBA98765432100F1E2D3C4B5A6978");
        byte[] kek = h("404142434445464748494A4B4C4D4E4F");
        ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType("EXPORTER", 24);
        var wrapped = KeyWrapScheme.wrap(key, kek, cv.left(), cv.right(),
                KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB);
        List<KeyWrapScheme.Step> steps = wrapped.steps();
        assertEquals(3, steps.size());
        assertEquals(x(steps.get(0).effectiveKek()), x(steps.get(2).effectiveKek()),
                "parts A and C must use the same KEK variant");
    }

    @Test
    void theNocvVariantLeavesTheKekAlone() {
        byte[] kek = h("404142434445464748494A4B4C4D4E4F");
        ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType("EXPORTER", 16);
        var wrapped = KeyWrapScheme.wrap(h("0123456789ABCDEFFEDCBA9876543210"), kek,
                cv.left(), cv.right(), KeyWrapScheme.Variant.PLAIN, KeyWrapScheme.Mode.ECB);
        for (KeyWrapScheme.Step step : wrapped.steps()) {
            assertEquals(x(kek), x(step.effectiveKek()), "NOCV must use the transport key itself");
        }
    }

    @TestFactory
    Stream<DynamicTest> externalTokensAreBuiltAndReadBackWhole() {
        return group("token").asList().stream().map(element -> {
            JsonObject v = element.getAsJsonObject();
            String label = v.get("tipo").getAsString() + " " + v.get("clave").getAsString().length() / 2
                    + "B, hostVersion=" + v.get("versionHost").getAsBoolean()
                    + ", nocv=" + v.get("nocv").getAsBoolean();
            return DynamicTest.dynamicTest(label, () -> {
                byte[] key = h(v.get("clave").getAsString());
                byte[] kek = h(v.get("kek").getAsString());
                ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType(
                        v.get("tipo").getAsString(), key.length);
                byte[] cryptogram = KeyWrapScheme.wrap(key, kek, cv.left(), cv.right(),
                        KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB).cryptogram();

                byte[] token = ExternalToken.build(cryptogram, cv.left(),
                        key.length > 8 ? cv.right() : new byte[8],
                        v.get("nocv").getAsBoolean(), ExternalToken.WRAP_ECB, false,
                        v.get("versionHost").getAsBoolean());
                assertEquals(v.get("token").getAsString(), x(token), "assembled token, TVV included");

                JsonObject expected = v.getAsJsonObject("leido");
                ExternalToken.Read read = ExternalToken.read(token);
                assertEquals(expected.get("interno").getAsBoolean(), read.internal(), "internal");
                assertEquals(expected.get("version").getAsInt(), read.versionByte(), "version byte");
                assertEquals(expected.get("metodo").getAsInt(), read.wrapMethod(), "wrap method");
                assertEquals(expected.get("cifrada").getAsBoolean(), read.enciphered(), "enciphered");
                assertEquals(expected.get("cvPresente").getAsBoolean(), read.cvPresent(), "CV present");
                assertEquals(expected.get("nocv").getAsBoolean(), read.nocv(), "NOCV flag");
                assertEquals(expected.get("cvIzq").getAsString(), x(read.cvLeft()), "left CV");
                assertEquals(expected.get("cvDer").getAsString(), x(read.cvRight()), "right CV");
                assertEquals(expected.get("longitud").getAsInt(), read.keyLength(), "key length");
                assertEquals(expected.get("cripto").getAsString(), x(read.cryptogram()), "cryptogram");
                assertEquals(expected.get("avisos").getAsInt(), read.warnings().size(), "warning count");
                assertEquals("VALID", read.tvv().name(), "TVV must verify on a token we built");

                byte[] back = KeyWrapScheme.unwrap(read.cryptogram(), kek, read.cvLeft(), read.cvRight(),
                        KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB);
                assertEquals(v.get("clave").getAsString(), x(back), "round trip through the token");
            });
        });
    }

    @Test
    void theHostVersionByteChangesOnlyTheVersionAndTheTvv() {
        // A real host writes X'00' in byte 4 for a double-length key, so the two tokens differ
        // in byte 4 and in the TVV that sums it, and nowhere else. That is what makes
        // byte-for-byte comparison against a host token possible.
        byte[] key = h("0123456789ABCDEFFEDCBA9876543210");
        byte[] kek = h("404142434445464748494A4B4C4D4E4F");
        ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType("EXPORTER", 16);
        byte[] cryptogram = KeyWrapScheme.wrap(key, kek, cv.left(), cv.right(),
                KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB).cryptogram();
        byte[] table616 = ExternalToken.build(cryptogram, cv.left(), cv.right(), false,
                ExternalToken.WRAP_ECB, false, false);
        byte[] host = ExternalToken.build(cryptogram, cv.left(), cv.right(), false,
                ExternalToken.WRAP_ECB, false, true);

        assertEquals(0x01, table616[4] & 0xFF, "Table 616 says X'01' for a double-length key");
        assertEquals(0x00, host[4] & 0xFF, "real hosts write X'00'");
        List<Integer> differing = new java.util.ArrayList<>();
        for (int i = 0; i < ExternalToken.SIZE; i++) {
            if (table616[i] != host[i]) differing.add(i);
        }
        assertEquals(List.of(4, 60), differing, "only the version byte and the TVV's top byte may differ");
    }

    @TestFactory
    Stream<DynamicTest> exportThenImportRoundTripsEndToEnd() {
        return group("roundtrip").asList().stream().map(element -> {
            JsonObject v = element.getAsJsonObject();
            String type = v.get("tipo").getAsString();
            String keyHex = v.get("clave").getAsString();
            return DynamicTest.dynamicTest(type + " / " + keyHex.length() / 2 + " bytes", () -> {
                var exported = IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                        keyHex, v.get("kek").getAsString(), type, "",
                        KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, false, true, ""));
                assertTrue(exported.ok(), "export failed");
                assertEquals(v.get("token").getAsString(), exported.outputs().get("token"), "token");
                assertEquals(v.get("cripto").getAsString(), exported.outputs().get("cryptogram"), "cryptogram");
                assertEquals(v.get("kcvClave").getAsString(), exported.outputs().get("keyKcv"), "key KCV");
                assertEquals(v.get("kcvKek").getAsString(), exported.outputs().get("kekKcv"), "KEK KCV");
                assertEquals(v.get("vpIbm").getAsString(), exported.outputs().get("ibmVp"), "IBM VP");

                var imported = IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                        exported.outputs().get("token"), v.get("kek").getAsString(), "", type,
                        KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, ""));
                assertEquals(v.get("importOk").getAsBoolean(), imported.ok(), "import outcome");
                assertEquals(v.get("claveRecuperada").getAsString(), imported.outputs().get("key"),
                        "import must recover the original key");
            });
        });
    }

    @Test
    void theResolverNamesTheSchemeThatProducedTheCryptogram() {
        // The whole point of the diagnostic: given the cryptogram and the KEK, say which
        // scheme was really used. Here the key was wrapped with no variant (a NOCV KEK).
        String keyHex = "0123456789ABCDEFFEDCBA9876543210";
        String kekHex = "404142434445464748494A4B4C4D4E4F";
        ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType("EXPORTER", 16);
        byte[] cryptogram = KeyWrapScheme.wrap(h(keyHex), h(kekHex), cv.left(), cv.right(),
                KeyWrapScheme.Variant.PLAIN, KeyWrapScheme.Mode.ECB).cryptogram();

        var resolved = IcsfKeyWrapService.resolve(new IcsfKeyWrapService.ResolveRequest(
                x(cryptogram), kekHex, keyHex, "", "", "EXPORTER"));
        assertTrue(resolved.ok(), "resolve failed");
        assertTrue(!resolved.candidates().isEmpty(), "no candidates tried");

        var best = resolved.candidates().get(0);
        assertEquals(KeyWrapResult.Verdict.MATCHES_KEY, best.verdict(), "best candidate must match the key");
        assertEquals(keyHex, best.keyHex(), "and must be the key we wrapped");

        // A zero CV under the CV variant is the same arithmetic as NOCV (KEK XOR 0 = KEK),
        // so whichever of the two the search reaches first, the other must be recorded as
        // equivalent rather than listed again as a rival finding.
        assertTrue(best.schemeCode().startsWith("CV/ECB/ZERO") || best.schemeCode().startsWith("PLAIN/ECB"),
                "expected the NOCV-equivalent scheme, was " + best.schemeCode());
        assertTrue(!best.equivalentSchemes().isEmpty(),
                "the equivalent scheme must be recorded alongside, not dropped");
    }

    @Test
    void schemesThatProduceTheSameKeyAreOneFindingWithItsEquivalents() {
        // Without this, a zero CV and a NOCV KEK look like two independent confirmations of
        // different schemes, when they are one fact stated twice.
        String keyHex = "0123456789ABCDEFFEDCBA9876543210";
        String kekHex = "404142434445464748494A4B4C4D4E4F";
        byte[] cryptogram = KeyWrapScheme.wrap(h(keyHex), h(kekHex), new byte[8], new byte[8],
                KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB).cryptogram();

        var resolved = IcsfKeyWrapService.resolve(new IcsfKeyWrapService.ResolveRequest(
                x(cryptogram), kekHex, keyHex, "", "", "EXPORTER"));

        long matching = resolved.candidates().stream()
                .filter(c -> c.verdict() == KeyWrapResult.Verdict.MATCHES_KEY)
                .count();
        assertEquals(1, matching, "the same key reached two ways must be one candidate");
    }

    @Test
    void importingAnEnhancedWrappedTokenRefusesRatherThanReturningNoise() {
        // Enhanced wrapping cannot be reproduced outside the coprocessor. Returning bytes
        // that look like a key and are not would be worse than saying so.
        byte[] cryptogram = h("00112233445566778899AABBCCDDEEFF");
        ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType("EXPORTER", 16);
        byte[] token = ExternalToken.build(cryptogram, cv.left(), cv.right(), false,
                ExternalToken.WRAPENH2, false, true);

        var imported = IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                x(token), "404142434445464748494A4B4C4D4E4F", "", "EXPORTER",
                KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, ""));
        assertTrue(imported.ok(), "it is a valid token, so the operation itself succeeds");
        assertTrue(imported.outputs().get("key") == null, "no key may be reported for enhanced wrapping");
    }

    @Test
    void inspectingATokenNeedsNoKek() {
        String keyHex = "0123456789ABCDEFFEDCBA9876543210";
        ControlVectorDefaults.Pair cv = ControlVectorDefaults.forType("PINVER", 16);
        byte[] cryptogram = KeyWrapScheme.wrap(h(keyHex), h("404142434445464748494A4B4C4D4E4F"),
                cv.left(), cv.right(), KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB).cryptogram();
        byte[] token = ExternalToken.build(cryptogram, cv.left(), cv.right(), false,
                ExternalToken.WRAP_ECB, false, true);

        var inspected = IcsfKeyWrapService.inspect(x(token));
        assertTrue(inspected.ok(), "inspect failed");
        assertEquals(x(cryptogram), inspected.outputs().get("cryptogram"), "cryptogram");
        assertEquals(x(cv.left()), inspected.outputs().get("cvLeft"), "left CV");
        assertTrue(inspected.outputs().get("key") == null, "an enciphered token must yield no key");
    }

    @Test
    void aBareCryptogramWithoutACvIsRefusedWithAnExplanation() {
        var imported = IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                "00112233445566778899AABBCCDDEEFF", "404142434445464748494A4B4C4D4E4F",
                "", "", KeyWrapScheme.Variant.CV, KeyWrapScheme.Mode.ECB, ""));
        assertTrue(!imported.ok(), "a bare cryptogram does not say which key it is");
        assertTrue(imported.error() != null, "the refusal must carry a reason");
    }

    @Test
    void desEngineMatchesTheClassicVector() {
        assertEquals("85E813540F0AB405", x(Des.encryptBlock(h("133457799BBCDFF1"), h("0123456789ABCDEF"))));
    }
}
