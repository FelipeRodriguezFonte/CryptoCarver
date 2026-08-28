package com.cryptocarver.crypto.icsf.keywrap;

import com.cryptocarver.crypto.icsf.IcsfHex;
import com.cryptocarver.crypto.icsf.IcsfText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * The CCA native key export and import verbs, reproduced in the clear.
 *
 * <pre>
 *   CSNBKEX  Key Export        internal -&gt; external under an EXPORTER
 *   CSNBDKX  Data Key Export   the same, DATA keys only
 *   CSNBKIM  Key Import        external -&gt; operational under the master key
 *   CSNBDKM  Data Key Import   the same, DATA keys only
 * </pre>
 *
 * <p>These are the native verbs, not TR-31 / X9.143. Nothing here talks to a coprocessor:
 * it reproduces byte for byte what the hardware does, so that what leaves the host can be
 * compared against what the receiving system expects, be that another CCA box or an
 * HSM from a different vendor.</p>
 *
 * <p>Key material is handled in the clear throughout. This is a test bench for test keys.</p>
 *
 * <p>Source: <em>z/OS ICSF Application Programmer's Guide</em> (csfb400_icsf_apg_hcr77e0).
 * Page numbers in the code are the manual's printed ones.</p>
 */
public final class IcsfKeyWrapService {

    private IcsfKeyWrapService() { }

    private static final String B = "icsf.keywrap.";
    private static final byte[] ZERO8 = new byte[8];

    /** What the caller asked for, so the four operations keep tidy signatures. */
    public record ExportRequest(String keyHex, String kekHex, String keyType, String cvHex,
                                KeyWrapScheme.Variant variant, KeyWrapScheme.Mode mode,
                                boolean markNocv, boolean hostVersionByte, String randomNumberHex) { }

    public record ImportRequest(String inputHex, String kekHex, String cvHex, String keyType,
                                KeyWrapScheme.Variant variant, KeyWrapScheme.Mode mode,
                                String randomNumberHex) { }

    public record ResolveRequest(String inputHex, String kekHex, String expectedKeyHex,
                                 String expectedKcvHex, String cvHex, String keyType) { }

    // =====================================================================
    // Input handling
    // =====================================================================
    private static byte[] readHex(String text, String fieldKey) {
        if (text == null || text.isBlank()) {
            throw new InputProblem(IcsfText.of(B + "err.missing", IcsfText.of(fieldKey)));
        }
        try {
            return IcsfHex.clean(text);
        } catch (IllegalArgumentException exc) {
            throw new InputProblem(IcsfText.of(B + "err.badHex", IcsfText.of(fieldKey), exc.getMessage()));
        }
    }

    /** An input the user can fix, carried as translatable text rather than a message string. */
    private static final class InputProblem extends RuntimeException {
        private final transient IcsfText text;

        InputProblem(IcsfText text) {
            super(text.key());
            this.text = text;
        }
    }

    private static void requireKeyLength(byte[] material, String fieldKey) {
        if (material.length != 8 && material.length != 16 && material.length != 24) {
            throw new InputProblem(IcsfText.of(B + "err.keyLength", IcsfText.of(fieldKey), material.length));
        }
    }

    /** The CV pair to use, and where it came from. */
    private record CvChoice(byte[] left, byte[] right, IcsfText origin) { }

    private static CvChoice resolveCv(String cvHex, String keyType, int keyLength) {
        if (cvHex != null && !cvHex.isBlank()) {
            byte[] cv = readHex(cvHex, B + "field.cv");
            if (cv.length == 8) {
                byte[] right = keyLength == 8 ? ZERO8 : cv;
                return new CvChoice(cv, right, IcsfText.of(keyLength > 8
                        ? B + "cv.manual8Replicated" : B + "cv.manual8"));
            }
            if (cv.length == 16) {
                return new CvChoice(IcsfHex.slice(cv, 0, 8), IcsfHex.slice(cv, 8, 16),
                        IcsfText.of(B + "cv.manual16"));
            }
            throw new InputProblem(IcsfText.of(B + "err.cvLength", cv.length));
        }
        try {
            ControlVectorDefaults.Pair pair = ControlVectorDefaults.forType(keyType, keyLength);
            return new CvChoice(pair.left(), pair.right(), IcsfText.of(B + "cv.default", keyType));
        } catch (IllegalArgumentException exc) {
            throw new InputProblem(IcsfText.of(B + "err.noCvForType", keyType, keyLength));
        }
    }

    private static byte[] randomNumber(String hex) {
        if (hex == null || hex.isBlank()) return ZERO8;
        byte[] rn = readHex(hex, B + "field.randomNumber");
        if (rn.length != 8) throw new InputProblem(IcsfText.of(B + "err.rnLength", rn.length));
        return rn;
    }

    private static String hex(byte[] data) {
        return IcsfHex.hex(data);
    }

    // =====================================================================
    // Key Export (CSNBKEX / CSNBDKX)
    // =====================================================================
    /**
     * Produces the external token a host would have produced with WRAP-ECB.
     *
     * <p>Takes the key and the KEK in the clear, which is what makes it a test bench
     * rather than a client of a coprocessor.</p>
     */
    public static KeyWrapResult export(ExportRequest request) {
        KeyWrapResult result = KeyWrapResult.success(KeyWrapResult.Operation.EXPORT);
        byte[] key;
        byte[] kek;
        CvChoice cv;
        byte[] rn;
        try {
            key = readHex(request.keyHex(), B + "field.key");
            kek = readHex(request.kekHex(), B + "field.exporter");
            requireKeyLength(key, B + "field.key");
            requireKeyLength(kek, B + "field.exporter");
            cv = resolveCv(request.cvHex(), request.keyType(), key.length);
            rn = randomNumber(request.randomNumberHex());
        } catch (InputProblem problem) {
            return KeyWrapResult.failure(KeyWrapResult.Operation.EXPORT, problem.text);
        }

        KeyWrapScheme.Wrapped wrapped = KeyWrapScheme.wrap(key, kek, cv.left(), cv.right(),
                request.variant(), request.mode());
        byte[] cvRightForToken = key.length > 8 ? cv.right() : ZERO8;
        byte[] token = ExternalToken.build(wrapped.cryptogram(), cv.left(), cvRightForToken,
                request.markNocv(), ExternalToken.WRAP_ECB, false, request.hostVersionByte());

        result.add(IcsfText.of(B + "row.operation"), IcsfText.of(B + "op.export"));
        result.add(IcsfText.of(B + "row.key"), IcsfText.of(B + "value.lengthAndStrength",
                key.length, IcsfText.of(B + "strength." + DesKeyCheck.componentAnalysis(key))));
        result.add(IcsfText.of(B + "row.keyType"), request.cvHex() == null || request.cvHex().isBlank()
                ? IcsfText.raw(request.keyType()) : IcsfText.of(B + "keyType.manualCv"));
        result.add(IcsfText.of(B + "row.controlVector"), IcsfText.of(B + "value.cvOriginAndForm",
                cv.origin(), IcsfText.of(B + "keyForm." + ControlVectorDefaults.keyForm(cv.left()))));
        result.add(IcsfText.of(B + "row.exporter"), IcsfText.of(B + "value.lengthAndKcv",
                kek.length, hex(DesKeyCheck.encZero(kek))));
        result.add(IcsfText.of(B + "row.scheme"), IcsfText.of(B + "value.schemeAndMode",
                IcsfText.of(B + "variant." + request.variant().name()),
                IcsfText.of(B + "mode." + request.mode().name())));
        result.add(IcsfText.of(B + "row.wrapMethod"), IcsfText.of(B + "wrap.WRAP_ECB"));
        result.add(IcsfText.of(B + "row.versionByte"), IcsfText.of(B + "value.versionByte",
                String.format("%02X", token[4]),
                IcsfText.of(request.hostVersionByte() ? B + "version.likeHosts" : B + "version.table616")));
        result.add(IcsfText.of(B + "row.keyKcv"), IcsfText.of(B + "value.kcvPair",
                hex(DesKeyCheck.encZero(key, 3)), hex(DesKeyCheck.encZero(key, 4))));
        result.add(IcsfText.of(B + "row.ibmVp"), IcsfText.of(B + "value.vpWithRn",
                hex(DesKeyCheck.ibmVerificationPattern(key, rn)), hex(rn)));

        result.step(IcsfText.of(B + "step.clearKey"), hex(key),
                IcsfText.of(B + "strength." + DesKeyCheck.componentAnalysis(key)));
        result.step(IcsfText.of(B + "step.clearKek"), hex(kek), IcsfText.of(B + "step.exporterDetail"));
        result.step(IcsfText.of(B + "step.cvLeft"), hex(cv.left()), cv.origin());
        if (key.length > 8) {
            result.step(IcsfText.of(B + "step.cvRight"), hex(cv.right()),
                    IcsfText.of(B + "keyForm." + ControlVectorDefaults.keyForm(cv.right())));
        }
        addVariantSteps(result, wrapped, request.variant());
        result.step(IcsfText.of(B + "step.cryptogram"), hex(wrapped.cryptogram()),
                IcsfText.of(B + "step.cryptogramDetail"));
        result.step(IcsfText.of(B + "step.token"), hex(token), IcsfText.of(B + "step.tokenDetail"));

        // The other variant, so a mismatch with the receiving side is one glance away.
        KeyWrapScheme.Variant alternate = request.variant() == KeyWrapScheme.Variant.CV
                ? KeyWrapScheme.Variant.PLAIN : KeyWrapScheme.Variant.CV;
        KeyWrapScheme.Wrapped other = KeyWrapScheme.wrap(key, kek, cv.left(), cv.right(),
                alternate, request.mode());
        byte[] otherToken = ExternalToken.build(other.cryptogram(), cv.left(), cvRightForToken);
        result.output("alternateCryptogram", hex(other.cryptogram()));
        result.output("alternateToken", hex(otherToken));
        result.step(IcsfText.of(B + "step.alternateCryptogram", IcsfText.of(B + "variant." + alternate.name())),
                hex(other.cryptogram()), IcsfText.of(B + "variant." + alternate.name()));

        result.output("token", hex(token));
        result.output("cryptogram", hex(wrapped.cryptogram()));
        result.output("keyKcv", hex(DesKeyCheck.encZero(key)));
        result.output("kekKcv", hex(DesKeyCheck.encZero(kek)));
        result.output("ibmVp", hex(DesKeyCheck.ibmVerificationPattern(key, rn)));

        if (key.length > 8) {
            if (request.hostVersionByte()) {
                result.note(KeyWrapResult.Level.OK, "VERSION_HOST",
                        IcsfText.of(B + "note.versionHost.title"), IcsfText.of(B + "note.versionHost.text"));
            } else {
                result.note(KeyWrapResult.Level.WARNING, "VERSION_TABLE616",
                        IcsfText.of(B + "note.versionTable616.title"),
                        IcsfText.of(B + "note.versionTable616.text"));
            }
        }
        addVariantNotes(result, request.variant(), cv.left(), cv.right(), key.length);
        addKeyNotes(result, key, B + "subject.key");
        addKeyNotes(result, kek, B + "subject.kek");
        addKekNotes(result, kek, key.length);
        result.note(KeyWrapResult.Level.INFO, "KCV_VS_VP", IcsfText.of(B + "note.kcvVsVp.title"),
                IcsfText.of(B + "note.kcvVsVp.text", hex(DesKeyCheck.encZero(key, 3)),
                        hex(DesKeyCheck.encZero(key, 4)),
                        hex(DesKeyCheck.ibmVerificationPattern(key, rn)), hex(rn)));
        return result;
    }

    private static void addVariantSteps(KeyWrapResult result, KeyWrapScheme.Wrapped wrapped,
                                        KeyWrapScheme.Variant variant) {
        IcsfText detail = IcsfText.of(variant == KeyWrapScheme.Variant.PLAIN
                ? B + "step.kekPlainDetail" : B + "step.kekXorCvDetail");
        for (KeyWrapScheme.Step step : wrapped.steps()) {
            IcsfText title = "*".equals(step.partName())
                    ? IcsfText.of(B + "step.kekAllParts")
                    : IcsfText.of(B + "step.kekForPart", step.partName());
            result.step(title, hex(step.effectiveKek()), detail);
        }
    }

    // =====================================================================
    // Key Import (CSNBKIM / CSNBDKM)
    // =====================================================================
    /**
     * Recovers the cleartext key from an external token, or from a bare cryptogram.
     *
     * <p>A bare 8/16/24-byte cryptogram is accepted because that is what arrives from a
     * system that does not produce CCA tokens at all (APG p. 18, which is what the null
     * key token exists for).</p>
     */
    public static KeyWrapResult importKey(ImportRequest request) {
        KeyWrapResult result = KeyWrapResult.success(KeyWrapResult.Operation.IMPORT);
        byte[] kek;
        byte[] cryptogram;
        CvChoice cv;
        byte[] rn;
        ExternalToken.Read token = null;
        IcsfText inputDescription;
        try {
            byte[] data = readHex(request.inputHex(), B + "field.received");
            kek = readHex(request.kekHex(), B + "field.importer");
            requireKeyLength(kek, B + "field.importer");
            rn = randomNumber(request.randomNumberHex());

            if (data.length == ExternalToken.SIZE && (data[0] == 0x01 || data[0] == 0x02)) {
                token = ExternalToken.read(data);
                cryptogram = token.cryptogram();
                if (request.cvHex() != null && !request.cvHex().isBlank()) {
                    cv = resolveCv(request.cvHex(), request.keyType(), token.keyLength());
                } else {
                    Optional<String> named = ControlVectorDefaults.typeOf(token.cvLeft());
                    cv = new CvChoice(token.cvLeft(), token.cvRight(), named
                            .map(n -> IcsfText.of(B + "cv.fromTokenNamed", n))
                            .orElseGet(() -> IcsfText.of(B + "cv.fromToken")));
                }
                inputDescription = IcsfText.of(token.internal() ? B + "input.internalToken" : B + "input.externalToken");
            } else if (data.length == 8 || data.length == 16 || data.length == 24) {
                cryptogram = data;
                boolean noCv = request.cvHex() == null || request.cvHex().isBlank();
                boolean noType = request.keyType() == null || request.keyType().isBlank();
                if (noCv && noType) {
                    throw new InputProblem(IcsfText.of(B + "err.bareCryptogramNeedsCv"));
                }
                cv = resolveCv(request.cvHex(), noType ? "DATA" : request.keyType(), data.length);
                inputDescription = IcsfText.of(B + "input.bareCryptogram", data.length);
            } else {
                throw new InputProblem(IcsfText.of(B + "err.inputLength", data.length));
            }
        } catch (InputProblem problem) {
            return KeyWrapResult.failure(KeyWrapResult.Operation.IMPORT, problem.text);
        } catch (IllegalArgumentException exc) {
            return KeyWrapResult.failure(KeyWrapResult.Operation.IMPORT,
                    IcsfText.raw(exc.getMessage()));
        }

        // Enhanced wrapping cannot be reproduced outside the coprocessor, so say so rather
        // than returning bytes that would look like a key and are not.
        if (token != null && token.wrapMethod() != ExternalToken.WRAP_ECB) {
            result.add(IcsfText.of(B + "row.operation"), IcsfText.of(B + "op.import"));
            result.add(IcsfText.of(B + "row.input"), inputDescription);
            result.add(IcsfText.of(B + "row.wrapMethod"), wrapMethodText(token.wrapMethod()));
            result.add(IcsfText.of(B + "row.result"), IcsfText.of(B + "value.enhancedNotDecryptable"));
            addReceivedTokenNotes(result, token);
            result.note(KeyWrapResult.Level.INFO, "ENHANCED_WHAT_NOW",
                    IcsfText.of(B + "note.enhancedWhatNow.title"), IcsfText.of(B + "note.enhancedWhatNow.text"));
            return result;
        }

        byte[] key = KeyWrapScheme.unwrap(cryptogram, kek, cv.left(), cv.right(),
                request.variant(), request.mode());
        List<Integer> wrongParity = DesKeyCheck.bytesWithoutOddParity(key);

        result.add(IcsfText.of(B + "row.operation"), IcsfText.of(B + "op.import"));
        result.add(IcsfText.of(B + "row.input"), inputDescription);
        result.add(IcsfText.of(B + "row.controlVector"), IcsfText.of(B + "value.cvOriginAndHalves",
                cv.origin(), hex(cv.left()), hex(cv.right())));
        result.add(IcsfText.of(B + "row.schemeApplied"), IcsfText.of(B + "value.schemeAndMode",
                IcsfText.of(B + "variant." + request.variant().name()),
                IcsfText.of(B + "mode." + request.mode().name())));
        result.add(IcsfText.of(B + "row.importer"), IcsfText.of(B + "value.lengthAndKcv",
                kek.length, hex(DesKeyCheck.encZero(kek))));
        result.add(IcsfText.of(B + "row.recoveredKey"), IcsfText.raw(hex(key)));
        result.add(IcsfText.of(B + "row.lengthStrength"), IcsfText.of(B + "value.lengthAndStrength",
                key.length, IcsfText.of(B + "strength." + DesKeyCheck.componentAnalysis(key))));
        result.add(IcsfText.of(B + "row.keyKcv"), IcsfText.of(B + "value.kcvPair",
                hex(DesKeyCheck.encZero(key, 3)), hex(DesKeyCheck.encZero(key, 4))));
        result.add(IcsfText.of(B + "row.ibmVp"), IcsfText.of(B + "value.vpWithRn",
                hex(DesKeyCheck.ibmVerificationPattern(key, rn)), hex(rn)));
        result.add(IcsfText.of(B + "row.parity"), wrongParity.isEmpty()
                ? IcsfText.of(B + "value.parityAllOdd", key.length)
                : IcsfText.of(B + "value.parityWrong", wrongParity.size(), key.length));

        result.step(IcsfText.of(B + "step.cryptogram"), hex(cryptogram), inputDescription);
        result.step(IcsfText.of(B + "step.clearKek"), hex(kek), IcsfText.of(B + "step.importerDetail"));
        result.step(IcsfText.of(B + "step.cvLeft"), hex(cv.left()), cv.origin());
        if (cryptogram.length > 8) {
            result.step(IcsfText.of(B + "step.cvRight"), hex(cv.right()),
                    IcsfText.of(B + "keyForm." + ControlVectorDefaults.keyForm(cv.right())));
        }
        // Re-wrapping with the same scheme reproduces the KEK variant of each part.
        addVariantSteps(result, KeyWrapScheme.wrap(key, kek, cv.left(), cv.right(),
                request.variant(), request.mode()), request.variant());
        result.step(IcsfText.of(B + "step.recoveredKey"), hex(key),
                IcsfText.of(B + "strength." + DesKeyCheck.componentAnalysis(key)));

        result.output("key", hex(key));
        result.output("keyKcv", hex(DesKeyCheck.encZero(key)));
        result.output("kekKcv", hex(DesKeyCheck.encZero(kek)));
        result.output("ibmVp", hex(DesKeyCheck.ibmVerificationPattern(key, rn)));

        if (token != null) addReceivedTokenNotes(result, token);
        addKeyNotes(result, key, B + "subject.recoveredKey");
        if (!wrongParity.isEmpty() && DesKeyCheck.uniformParity(key) != DesKeyCheck.Parity.EVEN) {
            result.note(KeyWrapResult.Level.CRITICAL, "RECOVERED_KEY_NO_PARITY",
                    IcsfText.of(B + "note.noParity.title"), IcsfText.of(B + "note.noParity.text"));
        }
        addVariantNotes(result, request.variant(), cv.left(), cv.right(), key.length);
        return result;
    }

    private static IcsfText wrapMethodText(int method) {
        return IcsfText.of(B + "wrap." + switch (method) {
            case ExternalToken.WRAP_ECB -> "WRAP_ECB";
            case ExternalToken.WRAP_ENH -> "WRAP_ENH";
            case ExternalToken.WRAPENH2 -> "WRAPENH2";
            case ExternalToken.WRAPENH3 -> "WRAPENH3";
            default -> "UNKNOWN";
        });
    }

    // =====================================================================
    // Inspect: everything the token says about itself, with no KEK
    // =====================================================================
    /** Reads a token without a KEK: what it is, and which KEK variant opens each part. */
    public static KeyWrapResult inspect(String inputHex) {
        KeyWrapResult result = KeyWrapResult.success(KeyWrapResult.Operation.INSPECT);
        ExternalToken.Read token;
        byte[] data;
        try {
            data = readHex(inputHex, B + "field.received");
            if (data.length != ExternalToken.SIZE) {
                throw new InputProblem(IcsfText.of(B + "err.notAToken", data.length));
            }
            token = ExternalToken.read(data);
        } catch (InputProblem problem) {
            return KeyWrapResult.failure(KeyWrapResult.Operation.INSPECT, problem.text);
        } catch (IllegalArgumentException exc) {
            return KeyWrapResult.failure(KeyWrapResult.Operation.INSPECT, IcsfText.raw(exc.getMessage()));
        }

        Optional<String> named = ControlVectorDefaults.typeOf(token.cvLeft());
        result.add(IcsfText.of(B + "row.operation"), IcsfText.of(B + "op.inspect"));
        result.add(IcsfText.of(B + "row.scope"), IcsfText.of(token.internal()
                ? B + "value.internal" : B + "value.external"));
        result.add(IcsfText.of(B + "row.wrapMethod"), wrapMethodText(token.wrapMethod()));
        result.add(IcsfText.of(B + "row.keyState"), IcsfText.of(token.enciphered()
                ? B + "value.enciphered" : B + "value.inTheClear"));
        result.add(IcsfText.of(B + "row.lengthStrength"), IcsfText.of(B + "value.lengthFromBasis",
                token.keyLength(), token.lengthBasis()));
        result.add(IcsfText.of(B + "row.controlVector"), named
                .map(n -> IcsfText.of(B + "value.cvNamed", hex(token.cvLeft()), n))
                .orElseGet(() -> IcsfText.raw(hex(token.cvLeft()))));
        result.add(IcsfText.of(B + "row.nocv"), IcsfText.of(token.nocv()
                ? B + "value.yes" : B + "value.no"));
        result.add(IcsfText.of(B + "row.tvv"), IcsfText.of(B + "tvv." + token.tvv().name()));

        result.step(IcsfText.of(B + "step.cryptogram"), hex(token.cryptogram()),
                IcsfText.of(B + "step.cryptogramDetail"));
        result.step(IcsfText.of(B + "step.cvLeft"), hex(token.cvLeft()),
                IcsfText.of(B + "keyForm." + ControlVectorDefaults.keyForm(token.cvLeft())));
        if (token.keyLength() > 8) {
            result.step(IcsfText.of(B + "step.cvRight"), hex(token.cvRight()),
                    IcsfText.of(B + "keyForm." + ControlVectorDefaults.keyForm(token.cvRight())));
        }

        result.output("cryptogram", hex(token.cryptogram()));
        result.output("cvLeft", hex(token.cvLeft()));
        result.output("cvRight", hex(token.cvRight()));

        // With the key in the clear inside the token there is nothing to unwrap.
        if (!token.enciphered()) {
            result.add(IcsfText.of(B + "row.recoveredKey"), IcsfText.raw(hex(token.cryptogram())));
            result.output("key", hex(token.cryptogram()));
            result.note(KeyWrapResult.Level.CRITICAL, "CLEAR_KEY_IN_TOKEN",
                    IcsfText.of(B + "note.clearKey.title"), IcsfText.of(B + "note.clearKey.text"));
        } else {
            result.note(KeyWrapResult.Level.INFO, "NO_KEK_NO_KEY",
                    IcsfText.of(B + "note.noKek.title"), IcsfText.of(B + "note.noKek.text"));
        }
        addReceivedTokenNotes(result, token);
        return result;
    }

    // =====================================================================
    // Resolve: which protection scheme actually produced this
    // =====================================================================
    /**
     * Tries every reasonable scheme and reports which one reproduces the key.
     *
     * <p>This is the diagnostic. Given the cryptogram the host emitted, the test KEK in the
     * clear and, if available, the key or its KCV, it names the scheme that was really
     * used -- which is the thing to settle with the team on the other side.</p>
     */
    public static KeyWrapResult resolve(ResolveRequest request) {
        KeyWrapResult result = KeyWrapResult.success(KeyWrapResult.Operation.RESOLVE);
        byte[] cryptogram;
        byte[] kek;
        byte[] expectedKey = null;
        byte[] expectedKcv = null;
        ExternalToken.Read token = null;
        try {
            byte[] data = readHex(request.inputHex(), B + "field.received");
            kek = readHex(request.kekHex(), B + "field.kek");
            if (data.length == ExternalToken.SIZE && (data[0] == 0x01 || data[0] == 0x02)) {
                token = ExternalToken.read(data);
                cryptogram = token.cryptogram();
            } else if (data.length == 8 || data.length == 16 || data.length == 24) {
                cryptogram = data;
            } else {
                throw new InputProblem(IcsfText.of(B + "err.inputLength", data.length));
            }
            if (request.expectedKeyHex() != null && !request.expectedKeyHex().isBlank()) {
                expectedKey = readHex(request.expectedKeyHex(), B + "field.expectedKey");
            }
            if (request.expectedKcvHex() != null && !request.expectedKcvHex().isBlank()) {
                expectedKcv = readHex(request.expectedKcvHex(), B + "field.expectedKcv");
            }
        } catch (InputProblem problem) {
            return KeyWrapResult.failure(KeyWrapResult.Operation.RESOLVE, problem.text);
        } catch (IllegalArgumentException exc) {
            return KeyWrapResult.failure(KeyWrapResult.Operation.RESOLVE, IcsfText.raw(exc.getMessage()));
        }

        if (token != null && token.wrapMethod() != ExternalToken.WRAP_ECB) {
            result.note(KeyWrapResult.Level.CRITICAL, "ENHANCED_NOTHING_TO_TRY",
                    IcsfText.of(B + "note.enhancedNothing.title", wrapMethodText(token.wrapMethod())),
                    IcsfText.of(B + "note.enhancedNothing.text"));
        }

        List<CvCandidate> cvs = candidateCvs(request, token, cryptogram.length);
        Map<String, Draft> bySignature = new LinkedHashMap<>();

        for (KekCandidate kekCandidate : kekCandidates(kek)) {
            for (KeyWrapScheme.Mode mode : KeyWrapScheme.Mode.values()) {
                for (KeyWrapScheme.Variant variant : KeyWrapScheme.Variant.values()) {
                    for (CvCandidate cvCandidate : cvs) {
                        // Without a variant the CV is irrelevant, so try it once only.
                        if (variant == KeyWrapScheme.Variant.PLAIN && cvCandidate != cvs.get(0)) continue;
                        byte[] key;
                        try {
                            key = KeyWrapScheme.unwrap(cryptogram, kekCandidate.kek(), cvCandidate.left(),
                                    cvCandidate.right(), variant, mode);
                        } catch (IllegalArgumentException ignored) {
                            continue;
                        }
                        // Two schemes reaching the same key are one finding, not two: a zero
                        // CV under the CV variant is arithmetically the NOCV case.
                        String signature = mode + "|" + kekCandidate.code() + "|" + hex(key);
                        Draft existing = bySignature.get(signature);
                        if (existing != null) {
                            existing.equivalents.add(schemeText(variant, mode, cvCandidate, kekCandidate));
                            continue;
                        }
                        bySignature.put(signature, new Draft(
                                describeCandidate(key, variant, mode, cvCandidate, kekCandidate,
                                        expectedKey, expectedKcv),
                                new ArrayList<>()));
                    }
                }
            }
        }

        List<KeyWrapResult.Candidate> found = new ArrayList<>();
        for (Draft draft : bySignature.values()) {
            KeyWrapResult.Candidate c = draft.candidate;
            found.add(new KeyWrapResult.Candidate(c.schemeCode(), c.scheme(), c.keyHex(), c.kcvHex(),
                    c.parity(), c.verdict(), c.wrongParityBytes(), draft.equivalents));
        }
        found.sort(java.util.Comparator
                .comparingInt((KeyWrapResult.Candidate c) -> c.verdict().ordinal())
                .thenComparingInt(c -> c.parity() == KeyWrapResult.Parity.ODD_OK ? 0 : 1));
        found.forEach(result::candidate);

        long matches = found.stream().filter(c -> c.verdict() == KeyWrapResult.Verdict.MATCHES_KEY
                || c.verdict() == KeyWrapResult.Verdict.MATCHES_KCV).count();
        long possible = found.stream().filter(c -> c.verdict() == KeyWrapResult.Verdict.POSSIBLE_ODD
                || c.verdict() == KeyWrapResult.Verdict.POSSIBLE_EVEN).count();

        result.add(IcsfText.of(B + "row.operation"), IcsfText.of(B + "op.resolve"));
        result.add(IcsfText.of(B + "row.cryptogram"), IcsfText.of(B + "value.bytes", cryptogram.length));
        result.add(IcsfText.of(B + "row.kek"), IcsfText.of(B + "value.lengthAndKcv",
                kek.length, hex(DesKeyCheck.encZero(kek))));
        result.add(IcsfText.of(B + "row.reference"), expectedKey != null
                ? IcsfText.of(B + "value.refKey")
                : expectedKcv != null ? IcsfText.of(B + "value.refKcv", hex(expectedKcv))
                        : IcsfText.of(B + "value.refNone"));
        result.add(IcsfText.of(B + "row.combinations"), IcsfText.raw(String.valueOf(found.size())));
        if (token != null) {
            result.add(IcsfText.of(B + "row.keyLength"), IcsfText.of(B + "value.lengthFromBasis",
                    token.keyLength(), token.lengthBasis()));
        }
        result.add(IcsfText.of(B + "row.result"), IcsfText.of(B + "value.matchCounts", matches, possible));

        if (matches > 0) {
            KeyWrapResult.Candidate best = found.get(0);
            result.note(KeyWrapResult.Level.OK, "SCHEME_FOUND",
                    IcsfText.of(B + "note.schemeFound.title", best.scheme()),
                    IcsfText.of(B + "note.schemeFound.text", best.keyHex(), best.kcvHex()));
        } else if (expectedKey != null || expectedKcv != null) {
            result.note(KeyWrapResult.Level.CRITICAL, "NO_SCHEME_MATCHES",
                    IcsfText.of(B + "note.noScheme.title"), IcsfText.of(B + "note.noScheme.text"));
        } else if (possible == 1) {
            KeyWrapResult.Candidate only = found.stream()
                    .filter(c -> c.verdict() == KeyWrapResult.Verdict.POSSIBLE_ODD
                            || c.verdict() == KeyWrapResult.Verdict.POSSIBLE_EVEN)
                    .findFirst().orElseThrow();
            result.note(KeyWrapResult.Level.OK, "ONE_COHERENT_SCHEME",
                    IcsfText.of(B + "note.onlyOne.title", only.scheme()),
                    IcsfText.of(B + "note.onlyOne.text", only.keyHex(), only.kcvHex(),
                            only.keyHex().length() / 2));
        } else {
            result.note(KeyWrapResult.Level.INFO, "PARITY_ONLY",
                    IcsfText.of(B + "note.parityOnly.title"), IcsfText.of(B + "note.parityOnly.text"));
        }
        if (token != null) addReceivedTokenNotes(result, token);
        return result;
    }

    /** A candidate under construction, while equivalent schemes are still being collected. */
    private static final class Draft {
        private final KeyWrapResult.Candidate candidate;
        private final List<IcsfText> equivalents;

        Draft(KeyWrapResult.Candidate candidate, List<IcsfText> equivalents) {
            this.candidate = candidate;
            this.equivalents = equivalents;
        }
    }

    private record CvCandidate(String code, IcsfText label, byte[] left, byte[] right) { }

    private record KekCandidate(String code, IcsfText label, byte[] kek) { }

    private static List<CvCandidate> candidateCvs(ResolveRequest request, ExternalToken.Read token,
                                                  int cryptogramLength) {
        List<CvCandidate> cvs = new ArrayList<>();
        if (token != null) {
            Optional<String> named = ControlVectorDefaults.typeOf(token.cvLeft());
            cvs.add(new CvCandidate("TOKEN", named
                    .map(n -> IcsfText.of(B + "cvCand.tokenNamed", n))
                    .orElseGet(() -> IcsfText.of(B + "cvCand.token")),
                    token.cvLeft(), token.cvRight()));
        }
        if (request.cvHex() != null && !request.cvHex().isBlank()) {
            try {
                CvChoice manual = resolveCv(request.cvHex(),
                        request.keyType() == null || request.keyType().isBlank() ? "DATA" : request.keyType(),
                        cryptogramLength);
                cvs.add(new CvCandidate("MANUAL", IcsfText.of(B + "cvCand.manual"), manual.left(), manual.right()));
            } catch (InputProblem ignored) {
                // A CV the user typed wrong should not stop the other candidates being tried.
            }
        }
        if (request.keyType() != null && !request.keyType().isBlank()) {
            try {
                ControlVectorDefaults.Pair pair = ControlVectorDefaults.forType(request.keyType(), cryptogramLength);
                cvs.add(new CvCandidate("TYPE", IcsfText.of(B + "cvCand.type", request.keyType()),
                        pair.left(), pair.right()));
            } catch (IllegalArgumentException ignored) {
                // Table 676 has no CV for that type at that length; nothing to add.
            }
        }
        cvs.add(new CvCandidate("ZERO", IcsfText.of(B + "cvCand.zero"), ZERO8, ZERO8));
        return cvs;
    }

    private static List<KekCandidate> kekCandidates(byte[] kek) {
        List<KekCandidate> out = new ArrayList<>();
        out.add(new KekCandidate("AS_IS", IcsfText.of(B + "kekCand.asIs"), kek));
        // Swapping identical halves changes nothing; repeating it would only duplicate every
        // candidate and make one scheme look like two.
        if (kek.length == 16 && !java.util.Arrays.equals(IcsfHex.slice(kek, 0, 8), IcsfHex.slice(kek, 8, 16))) {
            byte[] swapped = new byte[16];
            System.arraycopy(kek, 8, swapped, 0, 8);
            System.arraycopy(kek, 0, swapped, 8, 8);
            out.add(new KekCandidate("SWAPPED", IcsfText.of(B + "kekCand.swapped"), swapped));
        }
        return out;
    }

    private static KeyWrapResult.Candidate describeCandidate(
            byte[] key, KeyWrapScheme.Variant variant, KeyWrapScheme.Mode mode,
            CvCandidate cv, KekCandidate kek, byte[] expectedKey, byte[] expectedKcv) {

        List<Integer> wrong = DesKeyCheck.bytesWithoutOddParity(key);
        DesKeyCheck.Parity uniform = DesKeyCheck.uniformParity(key);
        byte[] kcv = DesKeyCheck.encZero(key, 4);

        KeyWrapResult.Verdict verdict;
        if (expectedKey != null && java.util.Arrays.equals(key, expectedKey)) {
            verdict = KeyWrapResult.Verdict.MATCHES_KEY;
        } else if (expectedKcv != null && startsWith(kcv, expectedKcv)) {
            verdict = KeyWrapResult.Verdict.MATCHES_KCV;
        } else if (uniform == DesKeyCheck.Parity.ODD) {
            verdict = KeyWrapResult.Verdict.POSSIBLE_ODD;
        } else if (uniform == DesKeyCheck.Parity.EVEN) {
            verdict = KeyWrapResult.Verdict.POSSIBLE_EVEN;
        } else {
            verdict = KeyWrapResult.Verdict.REJECTED;
        }

        KeyWrapResult.Parity parity = switch (uniform) {
            case ODD -> KeyWrapResult.Parity.ODD_OK;
            case EVEN -> KeyWrapResult.Parity.ALL_EVEN;
            case MIXED -> KeyWrapResult.Parity.MIXED;
        };

        String schemeCode = variant.name() + "/" + mode.name() + "/" + cv.code() + "/" + kek.code();
        return new KeyWrapResult.Candidate(schemeCode, schemeText(variant, mode, cv, kek), hex(key),
                hex(IcsfHex.slice(kcv, 0, 3)), parity, verdict, wrong.size(), List.of());
    }

    /** How one scheme reads: the variant, the CV it used, the mode and the KEK form. */
    private static IcsfText schemeText(KeyWrapScheme.Variant variant, KeyWrapScheme.Mode mode,
                                       CvCandidate cv, KekCandidate kek) {
        return variant == KeyWrapScheme.Variant.PLAIN
                ? IcsfText.of(B + "scheme.plain", IcsfText.of(B + "mode." + mode.name()), kek.label())
                : IcsfText.of(B + "scheme.withCv", IcsfText.of(B + "variant." + variant.name()),
                        cv.label(), IcsfText.of(B + "mode." + mode.name()), kek.label());
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (prefix.length > value.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) return false;
        }
        return true;
    }

    // =====================================================================
    // Interoperability notes
    // =====================================================================
    private static void addKeyNotes(KeyWrapResult result, byte[] key, String subjectKey) {
        List<Integer> wrong = DesKeyCheck.bytesWithoutOddParity(key);
        DesKeyCheck.Parity uniform = DesKeyCheck.uniformParity(key);
        if (!wrong.isEmpty() && uniform == DesKeyCheck.Parity.EVEN) {
            result.note(KeyWrapResult.Level.WARNING, "PARITY_ALL_EVEN",
                    IcsfText.of(B + "note.parityEven.title", IcsfText.of(subjectKey), key.length),
                    IcsfText.of(B + "note.parityEven.text", hex(DesKeyCheck.adjustToOddParity(key))));
        } else if (!wrong.isEmpty()) {
            result.note(KeyWrapResult.Level.WARNING, "PARITY_MIXED",
                    IcsfText.of(B + "note.parityMixed.title", IcsfText.of(subjectKey), wrong.size(), key.length),
                    IcsfText.of(B + "note.parityMixed.text", hex(DesKeyCheck.adjustToOddParity(key))));
        }
        String strength = DesKeyCheck.componentAnalysis(key);
        if (strength.startsWith("COLLAPSES")) {
            result.note(KeyWrapResult.Level.CRITICAL, "COLLAPSES_TO_SINGLE_DES",
                    IcsfText.of(B + "note.collapses.title", IcsfText.of(subjectKey)),
                    IcsfText.of(B + "note.collapses.text"));
        }
    }

    private static void addKekNotes(KeyWrapResult result, byte[] kek, int keyLength) {
        if (kek.length == 8) {
            result.note(KeyWrapResult.Level.CRITICAL, "SINGLE_DES_KEK",
                    IcsfText.of(B + "note.singleDesKek.title"), IcsfText.of(B + "note.singleDesKek.text"));
        }
        if (kek.length < keyLength) {
            result.note(KeyWrapResult.Level.WARNING, "KEK_WEAKER_THAN_KEY",
                    IcsfText.of(B + "note.weakKek.title", kek.length, keyLength),
                    IcsfText.of(B + "note.weakKek.text"));
        }
    }

    private static void addVariantNotes(KeyWrapResult result, KeyWrapScheme.Variant variant,
                                        byte[] cvLeft, byte[] cvRight, int keyLength) {
        if (variant == KeyWrapScheme.Variant.PLAIN) {
            result.note(KeyWrapResult.Level.WARNING, "NOCV_IN_USE",
                    IcsfText.of(B + "note.nocv.title"), IcsfText.of(B + "note.nocv.text"));
        }
        if (variant == KeyWrapScheme.Variant.CV_SWAPPED) {
            result.note(KeyWrapResult.Level.WARNING, "CV_SWAPPED_IN_USE",
                    IcsfText.of(B + "note.cvSwapped.title"), IcsfText.of(B + "note.cvSwapped.text"));
        }
        if (keyLength > 8 && java.util.Arrays.equals(cvLeft, cvRight)
                && !IcsfHex.isAllZero(cvLeft, 0, cvLeft.length)) {
            result.note(KeyWrapResult.Level.INFO, "CV_HALVES_EQUAL",
                    IcsfText.of(B + "note.cvHalvesEqual.title"), IcsfText.of(B + "note.cvHalvesEqual.text"));
        }
    }

    private static void addReceivedTokenNotes(KeyWrapResult result, ExternalToken.Read token) {
        for (ExternalToken.Read.Warning warning : token.warnings()) {
            result.note(KeyWrapResult.Level.WARNING, warning.code(),
                    IcsfText.of(B + "note.tokenCoherence.title"),
                    IcsfText.of(B + "note." + warning.code(), warning.keyLength(), warning.basis()));
        }
        switch (token.tvv()) {
            case INVALID -> result.note(KeyWrapResult.Level.CRITICAL, "TVV_INVALID",
                    IcsfText.of(B + "note.tvvInvalid.title"), IcsfText.of(B + "note.tvvInvalid.text"));
            case ABSENT -> result.note(KeyWrapResult.Level.WARNING, "TVV_ABSENT",
                    IcsfText.of(B + "note.tvvAbsent.title"), IcsfText.of(B + "note.tvvAbsent.text"));
            default -> { }
        }
        if (token.nocv()) {
            result.note(KeyWrapResult.Level.WARNING, "TOKEN_MARKED_NOCV",
                    IcsfText.of(B + "note.tokenNocv.title"), IcsfText.of(B + "note.tokenNocv.text"));
        }
        if (token.internal()) {
            result.note(KeyWrapResult.Level.INFO, "INTERNAL_TOKEN",
                    IcsfText.of(B + "note.internalToken.title"), IcsfText.of(B + "note.internalToken.text"));
        }
    }
}
