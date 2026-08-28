package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.Scope;

/**
 * Analyser for a single IBM z/OS ICSF / CCA key token.
 *
 * <p>Entry point of the core. It has no UI dependency and is equally usable from
 * the desktop module, the CLI and the batch layer.</p>
 *
 * <p><b>Security.</b> Nothing here decrypts anything. Protected key material is
 * only recoverable inside the cryptographic coprocessor under its master key.
 * What this does produce is a report that carries the token in full, in
 * hexadecimal, so it deserves the same care as the dump it came from.</p>
 *
 * <p>Formats covered: AES fixed-length (id X'01', version X'04'), DES
 * fixed-length internal and external (X'01'/X'02', versions X'00'/X'01'), RKX
 * (X'02', version X'10'), variable-length symmetric (X'00'/X'01'/X'02', version
 * X'05') and PKA (X'00'/X'1E'/X'1F').</p>
 */
public final class IcsfTokenParser {

    private IcsfTokenParser() { }

    /** Analyses a key token without declaring a provenance. */
    public static ParseResult parse(byte[] data) {
        return parse(data, Origin.INFER);
    }

    /**
     * Analyses a key token.
     *
     * @param data   the token bytes
     * @param origin how the token reached the analyst; see {@link Origin}
     * @return always a result, never a thrown exception: an unreadable token comes
     *         back as {@link ParseResult#isOk()} false with the reason in
     *         {@link ParseResult#error()}
     */
    public static ParseResult parse(byte[] data, Origin origin) {
        if (data == null || data.length == 0) {
            return ParseResult.failure("Empty input.", 0);
        }
        if (data.length < 8) {
            return ParseResult.failure("Token too short (" + data.length + " bytes).", data.length);
        }

        Origin resolved = origin == null ? Origin.INFER : origin;
        int tokenId = IcsfHex.u8(data, 0);
        int version = IcsfHex.u8(data, 4);

        try {
            // --- PKA (identifiers 1E/1F) -----------------------------------
            if (tokenId == 0x1E || tokenId == 0x1F) {
                return PkaTokenParser.parse(data, resolved);
            }

            // --- symmetric, fixed and variable length ----------------------
            if (tokenId == 0x01) {                              // internal
                if (version == 0x04) return SymmetricFixedTokenParser.parseAes(data, resolved);
                if (version == 0x00 || version == 0x01) {
                    return SymmetricFixedTokenParser.parseDes(data, true, resolved);
                }
                if (version == 0x05) return SymmetricVariableTokenParser.parse(data, resolved);
            }
            if (tokenId == 0x02) {                              // external
                if (version == 0x10) return SymmetricFixedTokenParser.parseRkx(data, resolved);
                if (version == 0x00 || version == 0x01) {
                    return SymmetricFixedTokenParser.parseDes(data, false, resolved);
                }
                if (version == 0x05) return SymmetricVariableTokenParser.parse(data, resolved);
            }
            if (tokenId == 0x00) {                              // null: variable-length or PKA
                if (version == 0x05) return SymmetricVariableTokenParser.parse(data, resolved);
                ParseResult result = ParseResult.ok(TokenFamily.NULL, data.length);
                result.section(new IcsfSection(IcsfText.of("icsf.section.nullToken"))
                        .add(0, 1, IcsfText.of("icsf.field.tokenId"), IcsfHex.hex(data, 0, 1),
                                IcsfText.of("icsf.value.tokenId.null")));
                return SymmetricVariableTokenParser.nullSummary(result,
                        IcsfText.of("icsf.family.nullToken"))
                        .summary(SummaryKey.SCOPE, Scope.NULL, IcsfText.of("icsf.scope.nullToken"));
            }

            return ParseResult.failure(String.format(
                    "Unrecognised identifier/version: id=X'%02X' version=X'%02X'. Supported formats: "
                            + "AES/DES fixed (01/02), RKX (02/10), variable-length symmetric (01/02/00 v05), "
                            + "PKA (1E/1F/00).", tokenId, version), data.length);
        } catch (RuntimeException exception) {
            // A malformed token must come back as a failed analysis, never as a crash:
            // in a batch of thousands one bad record cannot take the whole run down.
            String reason = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            return ParseResult.failure("Error while parsing: " + reason, data.length);
        }
    }

    /** Convenience: reads linear hexadecimal and analyses it. */
    public static ParseResult parseHex(String hex, Origin origin) {
        try {
            return parse(IcsfHex.clean(hex), origin);
        } catch (IllegalArgumentException exception) {
            return ParseResult.failure(exception.getMessage(), 0);
        }
    }
}
