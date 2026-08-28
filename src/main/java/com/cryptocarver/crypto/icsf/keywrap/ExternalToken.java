package com.cryptocarver.crypto.icsf.keywrap;

import com.cryptocarver.crypto.icsf.DesKeyAnalysis;
import com.cryptocarver.crypto.icsf.IcsfHex;
import com.cryptocarver.crypto.icsf.IcsfText;
import com.cryptocarver.crypto.icsf.IcsfTvv;
import com.cryptocarver.crypto.icsf.IcsfVocabulary;
import java.util.ArrayList;
import java.util.List;

/**
 * The DES fixed-length external key token, APG Table 616, p. 1564.
 *
 * <p>Building one and reading one back are the two halves of comparing what a host
 * produced with what the receiving system expects.</p>
 */
public final class ExternalToken {

    private ExternalToken() { }

    public static final int SIZE = 64;

    /** Wrapping methods from token byte 7, bits 0-2. */
    public static final int WRAP_ECB = 0b000;
    public static final int WRAP_ENH = 0b001;
    public static final int WRAPENH2 = 0b010;
    public static final int WRAPENH3 = 0b011;

    /**
     * Assembles a 64-byte external token (X'02') around a cryptogram, and stamps its TVV.
     *
     * @param hostVersionByte writes X'00' in byte 4 even for double and triple-length keys.
     *                        Table 616 says X'01' there, but real hosts write X'00', so this
     *                        is what has to be used to compare byte for byte against a token
     *                        that came from one. It changes the TVV, which sums that byte.
     */
    public static byte[] build(byte[] cryptogram, byte[] cvLeft, byte[] cvRight,
                               boolean nocvFlag, int wrapMethod, boolean clearKey,
                               boolean hostVersionByte) {
        if (cryptogram.length != 8 && cryptogram.length != 16 && cryptogram.length != 24) {
            throw new IllegalArgumentException(
                    "A cryptogram is 8, 16 or 24 bytes (got " + cryptogram.length + ").");
        }
        byte[] token = new byte[SIZE];
        token[0] = 0x02;
        token[4] = (byte) ((cryptogram.length == 8 || hostVersionByte) ? 0x00 : 0x01);

        boolean cvPresent = !IcsfHex.isAllZero(cvLeft, 0, cvLeft.length)
                || !IcsfHex.isAllZero(cvRight, 0, cvRight.length);
        int flag6 = 0;
        if (!clearKey) flag6 |= 0x80;      // bit 0: key is enciphered
        if (cvPresent) flag6 |= 0x40;      // bit 1: control vector present
        if (nocvFlag) flag6 |= 0x20;       // bit 2: NOCV, transport keys only
        token[6] = (byte) flag6;
        token[7] = (byte) ((wrapMethod & 0b111) << 5);

        System.arraycopy(cryptogram, 0, token, 16, 8);
        if (cryptogram.length > 8) System.arraycopy(cryptogram, 8, token, 24, 8);
        if (cryptogram.length > 16) System.arraycopy(cryptogram, 16, token, 48, 8);

        System.arraycopy(cvLeft, 0, token, 32, 8);
        if (cryptogram.length > 8) System.arraycopy(cvRight, 0, token, 40, 8);

        if (!cvPresent) {
            // Byte 59 only means anything on DATA keys with a zero CV (Table 616).
            token[59] = (byte) switch (cryptogram.length) {
                case 8 -> 0x00;
                case 16 -> 0x10;
                default -> 0x20;
            };
        }
        long tvv = IcsfTvv.compute(token);
        token[60] = (byte) ((tvv >>> 24) & 0xFF);
        token[61] = (byte) ((tvv >>> 16) & 0xFF);
        token[62] = (byte) ((tvv >>> 8) & 0xFF);
        token[63] = (byte) (tvv & 0xFF);
        return token;
    }

    /** Convenience for the common case: WRAP-ECB, enciphered key, Table 616 version byte. */
    public static byte[] build(byte[] cryptogram, byte[] cvLeft, byte[] cvRight) {
        return build(cryptogram, cvLeft, cvRight, false, WRAP_ECB, false, false);
    }

    /** What a token says about itself, plus why its key length was decided that way. */
    public record Read(boolean internal, int versionByte, int wrapMethod, boolean enciphered,
                       boolean cvPresent, boolean nocv, byte[] cvLeft, byte[] cvRight,
                       byte[] cryptogram, int keyLength, IcsfText lengthBasis,
                       IcsfVocabulary.TvvState tvv, List<Warning> warnings) {

        /** A disagreement between the token's own bytes, reported rather than resolved. */
        public record Warning(String code, int keyLength, IcsfText basis) { }
    }

    /**
     * Extracts from a 64-byte DES token what is needed to wrap or unwrap it.
     *
     * <p>The key length is delegated to the token analyser so that this pane and the
     * analysis pane cannot disagree. Its order is by decreasing reliability -- byte 59 of
     * a zero-CV DATA key, then the CV key-form bits, then the token version -- and that
     * priority matters: real hosts emit double-length keys with byte 4 at X'00', so
     * trusting the version before the CV splits the cryptogram down the middle.</p>
     */
    public static Read read(byte[] token) {
        if (token.length != SIZE) {
            throw new IllegalArgumentException(
                    "A DES fixed-length token is 64 bytes (got " + token.length + ").");
        }
        int first = token[0] & 0xFF;
        if (first != 0x01 && first != 0x02) {
            throw new IllegalArgumentException(String.format(
                    "The first byte should be X'01' (internal) or X'02' (external); it is X'%02X'.", first));
        }
        int version = token[4] & 0xFF;
        if (first == 0x02 && version == 0x10) {
            throw new IllegalArgumentException("This is an RKX token (version X'10'), which only CSNDRKX uses.");
        }
        if (version == 0x04) {
            throw new IllegalArgumentException("This is an AES fixed-length token (Table 614, version X'04'). "
                    + "This pane only wraps and unwraps DES/TDES; use the token analyser to read it.");
        }

        byte[] cvLeft = IcsfHex.slice(token, 32, 40);
        byte[] cvRight = IcsfHex.slice(token, 40, 48);
        boolean enciphered = (token[6] & 0x80) != 0;
        int wrapMethod = (token[7] >> 5) & 0b111;

        DesKeyAnalysis.KeyLength length = DesKeyAnalysis.keyLength(token);
        IcsfText basis = length.basis();
        int keyLength = switch (length.form()) {
            case SINGLE -> 8;
            case TRIPLE, OBFUSCATED -> 24;
            case DOUBLE_OR_TRIPLE -> IcsfHex.isAllZero(token, 48, 56) ? 16 : 24;
            default -> 16;
        };
        if (wrapMethod == WRAPENH3) {
            // WRAPENH3 obfuscates the length; the analyser already reports OBFUSCATED,
            // and the payload occupies the full three parts either way.
            keyLength = 24;
        }

        List<Read.Warning> warnings = new ArrayList<>();
        if (keyLength != 8 && version == 0x00 && wrapMethod != WRAPENH3) {
            warnings.add(new Read.Warning("VERSION_SAYS_SINGLE", keyLength, basis));
        } else if (keyLength == 8 && version == 0x01) {
            warnings.add(new Read.Warning("VERSION_SAYS_DOUBLE", keyLength, basis));
        }

        byte[] cryptogram = new byte[keyLength];
        System.arraycopy(token, 16, cryptogram, 0, 8);
        if (keyLength >= 16) System.arraycopy(token, 24, cryptogram, 8, 8);
        if (keyLength == 24) System.arraycopy(token, 48, cryptogram, 16, 8);

        IcsfTvv.Evaluation tvv = IcsfTvv.evaluate(token);
        return new Read(first == 0x01, version, wrapMethod, enciphered,
                (token[6] & 0x40) != 0, (token[6] & 0x20) != 0,
                cvLeft, cvRight, cryptogram, keyLength, basis, tvv.state(), warnings);
    }
}
