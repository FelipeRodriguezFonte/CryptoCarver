package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.keywrap.ControlVectorDefaults;
import com.cryptocarver.crypto.icsf.keywrap.ExternalToken;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Toy tokens for trying the analysers without real data. None of them carries a real key.
 *
 * <p>They are byte for byte the samples of the Python tool this module was ported from
 * ({@code icsf_qt.py}, {@code icsf_web.py}), so a report produced from them here can be
 * set side by side with one produced there. {@code IcsfSamplesTest} pins that against
 * the Python output.</p>
 *
 * <p>The single tokens are assembled field by field. The sample batch needs Table 676's
 * default Control Vectors and takes them from the key-wrapping core, which already
 * ships that table: the analyser itself still never mints a CV.</p>
 */
public final class IcsfSamples {

    private IcsfSamples() { }

    /** Internal AES fixed-length DATA token declaring a 128-bit key in the clear (Table 614). */
    public static byte[] aesFixed() {
        byte[] token = new byte[64];
        token[0] = 0x01;
        token[4] = 0x04;
        token[57] = (byte) 128;
        return withTvv(token);
    }

    /** Internal variable-length AES CIPHER token, wrapped with AESKW and named KEY1 (Table 618). */
    public static byte[] variableLengthCipher() {
        Bytes token = new Bytes();
        token.add(0x01, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00);   // header; length written last
        token.add(0x03, 0x01).fill(0xAB, 8).fill(0x00, 8);           // encrypted under the AES master key
        token.add(0x02, 0x02, 0x00, 0x00);                           // AESKW, SHA-256
        token.add(0x01, 0x00, 0x00, 0x00);                           // associated data; its length below
        token.add(0x04, 0x00, 0x00, 0x00).u16(128);                  // key name of 4 bytes, 128-bit payload
        token.add(0x00, 0x02).u16(0x0001).add(0x02);                 // AES, CIPHER, two usage fields
        token.add(0xC0, 0x00, 0xFF, 0x00).add(0x03);                 // usage fields, three management fields
        token.add(0x90, 0x00, 0x00, 0x00, 0x02, 0x02).ascii("KEY1");
        token.setU16(32, token.size() - 30);
        token.fill(0x00, 16);                                        // payload
        token.setU16(2, token.size());
        return token.toArray();
    }

    /** Internal DES token of the form K1|K2|K1: triple-length, but only 2-key TDES in strength. */
    public static byte[] desTripleK1K2K1() {
        byte[] token = new byte[64];
        token[0] = 0x01;
        token[4] = 0x01;
        java.util.Arrays.fill(token, 16, 24, (byte) 0x11);
        java.util.Arrays.fill(token, 24, 32, (byte) 0x22);
        java.util.Arrays.fill(token, 48, 56, (byte) 0x11);
        token[59] = 0x20;
        return withTvv(token);
    }

    /**
     * Internal PKA token: an RSA 2048 private key with an AESKW-wrapped OPK (section X'30'),
     * its public key (X'04') and a private key name (X'10').
     */
    public static byte[] pkaRsa2048() {
        byte[] exponent = {0x01, 0x00, 0x01};

        Bytes privateKey = new Bytes().fill(0x00, 122);
        privateKey.set(0, 0x30);
        privateKey.setU16(4, 46);                   // associated data length
        privateKey.set(10, 0x04);                   // modern associated data: U-* usage and compliance bits
        privateKey.set(11, 0x02);                   // private key encrypted, internal token
        privateKey.set(12, 0x24);                   // randomly generated
        privateKey.set(13, 0x00);                   // not compliant-tagged, NO-XLATE
        privateKey.set(14, 0x02);                   // SHA-256
        privateKey.set(48, 0b10100000);             // U-DIGSIG + U-KEYENC
        privateKey.setU16(52, 256);                 // modulus of 256 bytes = 2048 bits
        privateKey.setU16(54, 256);
        privateKey.fillAt(56, 0xA5, 48);            // OPK
        privateKey.fillAt(104, 0x7B, 16);           // MKVP
        privateKey.fill(0xC3, 256);                 // modulus
        privateKey.setU16(2, privateKey.size());

        Bytes publicKey = new Bytes().fill(0x00, 12);
        publicKey.set(0, 0x04);
        publicKey.setU16(6, exponent.length);
        publicKey.setU16(8, 2048);
        publicKey.setU16(10, 256);
        publicKey.add(exponent).fill(0xC3, 256);
        publicKey.setU16(2, publicKey.size());

        Bytes name = new Bytes().fill(0x00, 4);
        name.set(0, 0x10);
        name.setU16(2, 68);
        name.ascii(String.format(Locale.ROOT, "%-64s", "EJEMPLO.RSA.PRIVADA"));

        Bytes token = new Bytes().fill(0x00, 8);
        token.set(0, 0x1F);
        token.add(privateKey.toArray()).add(publicKey.toArray()).add(name.toArray());
        token.setU16(2, token.size());
        return token.toArray();
    }

    /**
     * A small batch in all the shapes the reader accepts: labelled lines, comments and a
     * token in two host rows. It includes what a real inventory has to bring to light — a
     * byte 59 outside today's table and a single-length DES key.
     *
     * <p>The labels are kept as the Python tool writes them, so the two reports can be
     * compared line by line; only the comment lines follow {@code locale}.</p>
     */
    public static String batch(Locale locale) {
        List<String> lines = new ArrayList<>();
        lines.add("# " + text("icsf.sample.batch.oneLine", locale));
        lines.add("# " + text("icsf.sample.batch.comments", locale));
        lines.add("EJ.KGUP.IMPORTER|" + IcsfHex.hex(des("IMPORTER", 16, 0x40, false)));
        lines.add("EJ.DATA.SIMPLE|" + IcsfHex.hex(des(null, 8, null, false)));
        lines.add("EJ.PINVER.DOBLE|" + IcsfHex.hex(des("PINVER", 16, null, false)));
        lines.add("EJ.MAC.NO-XPORT|" + IcsfHex.hex(des("MAC", 16, null, true)));
        lines.add("EJ.AES.FIXED|" + IcsfHex.hex(aesFixed()));
        lines.add("EJ.VAR.CIPHER|" + IcsfHex.hex(variableLengthCipher()));
        lines.add("");
        lines.add("# " + text("icsf.sample.batch.twoRows", locale));
        lines.add(IcsfHex.toTwoRows(des("IPINENC", 16, null, false)));
        return String.join("\n", lines);
    }

    /**
     * An external DES token with Table 676's default CV for {@code type}, or a zero CV
     * when {@code type} is {@code null}.
     *
     * @param byte59   overrides byte 59 and recalculates the TVV; {@code null} leaves it
     * @param noExport clears CV bit 17 (NO-XPORT), compensating byte 2's parity with bit 23
     */
    private static byte[] des(String type, int keyLength, Integer byte59, boolean noExport) {
        byte[] left = new byte[8];
        byte[] right = new byte[8];
        if (type != null) {
            ControlVectorDefaults.Pair pair = ControlVectorDefaults.forType(type, keyLength);
            left = pair.left();
            right = pair.right();
        }
        if (noExport) {
            left[2] ^= 0x41;
            right[2] ^= 0x41;
        }
        byte[] material = new byte[keyLength];
        for (int index = 0; index < keyLength; index++) {
            material[index] = (byte) ((0x11 * (index + 1)) & 0xFF);
        }
        byte[] token = ExternalToken.build(material, left, right);
        if (byte59 != null) {
            token[59] = byte59.byteValue();
            withTvv(token);
        }
        return token;
    }

    private static byte[] withTvv(byte[] token) {
        long tvv = IcsfTvv.compute(token);
        token[60] = (byte) ((tvv >>> 24) & 0xFF);
        token[61] = (byte) ((tvv >>> 16) & 0xFF);
        token[62] = (byte) ((tvv >>> 8) & 0xFF);
        token[63] = (byte) (tvv & 0xFF);
        return token;
    }

    private static String text(String key, Locale locale) {
        return IcsfMessages.resolve(IcsfText.of(key), locale);
    }

    /** Just enough of a growable byte buffer to write the samples the way the tables read. */
    private static final class Bytes {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private byte[] patched;

        Bytes add(int... values) {
            flush();
            for (int value : values) out.write(value);
            return this;
        }

        Bytes add(byte[] values) {
            flush();
            out.writeBytes(values);
            return this;
        }

        Bytes u16(int value) {
            return add((value >> 8) & 0xFF, value & 0xFF);
        }

        Bytes fill(int value, int count) {
            flush();
            for (int index = 0; index < count; index++) out.write(value);
            return this;
        }

        Bytes ascii(String value) {
            return add(value.getBytes(StandardCharsets.US_ASCII));
        }

        void set(int offset, int value) {
            array()[offset] = (byte) value;
        }

        void setU16(int offset, int value) {
            byte[] data = array();
            data[offset] = (byte) ((value >> 8) & 0xFF);
            data[offset + 1] = (byte) (value & 0xFF);
        }

        void fillAt(int offset, int value, int count) {
            java.util.Arrays.fill(array(), offset, offset + count, (byte) value);
        }

        int size() {
            return patched != null ? patched.length : out.size();
        }

        byte[] toArray() {
            return array().clone();
        }

        /** The bytes written so far, patchable in place until the next append. */
        private byte[] array() {
            if (patched == null) patched = out.toByteArray();
            return patched;
        }

        /** Carries in-place patches over before appending more bytes. */
        private void flush() {
            if (patched == null) return;
            out.reset();
            out.writeBytes(patched);
            patched = null;
        }
    }
}
