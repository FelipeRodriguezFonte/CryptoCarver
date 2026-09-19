package com.cryptocarver.crypto.hsm;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Offline codec for the common envelope of a payShield host message.
 *
 * <p>The framing rules come from {@code payShield 10K Host Programmer's Manual},
 * document 007-001518-023, revision A1, clauses 1.3–1.5. Command-specific bodies
 * are deliberately opaque: this class does not pretend to know field layouts for
 * which the project has no captured request/response pair.
 */
public final class PayShieldMessageCodec {
    public static final byte EM = 0x19;

    private final int headerLength;
    private final boolean tcpLengthPrefix;

    public PayShieldMessageCodec(int headerLength) {
        this(headerLength, false);
    }

    public PayShieldMessageCodec(int headerLength, boolean tcpLengthPrefix) {
        if (headerLength < 1 || headerLength > 255) {
            throw new IllegalArgumentException("headerLength must be 1..255");
        }
        this.headerLength = headerLength;
        this.tcpLengthPrefix = tcpLengthPrefix;
    }

    public int headerLength() {
        return headerLength;
    }

    public boolean tcpLengthPrefix() {
        return tcpLengthPrefix;
    }

    public byte[] composeCommand(String header, String commandCode, byte[] body, byte[] trailer) {
        requireCode(commandCode, "commandCode");
        return compose(header, commandCode, body, trailer);
    }

    public byte[] composeCommand(String header, String commandCode, String body) {
        return composeCommand(header, commandCode, ascii(body, "body"), new byte[0]);
    }

    public byte[] composeResponse(
            String header,
            String responseCode,
            String errorCode,
            byte[] data,
            byte[] trailer) {
        requireCode(responseCode, "responseCode");
        requireCode(errorCode, "errorCode");
        return compose(header, responseCode + errorCode, data, trailer);
    }

    public PayShieldMessage parseCommand(byte[] framed) {
        ParsedPayload parsed = parsePayload(framed);
        if (parsed.rest().length < 2) {
            throw invalid("missing command code");
        }
        return new PayShieldMessage(
                parsed.header(),
                ascii(parsed.rest(), 0, 2, "command code"),
                parsed.body(),
                parsed.trailer());
    }

    public PayShieldResponse parseResponse(byte[] framed) {
        ParsedPayload parsed = parsePayload(framed);
        if (parsed.rest().length < 4) {
            throw invalid("response requires response and error codes");
        }
        return new PayShieldResponse(
                parsed.header(),
                ascii(parsed.rest(), 0, 2, "response code"),
                ascii(parsed.rest(), 2, 2, "error code"),
                Arrays.copyOfRange(parsed.body(), 2, parsed.body().length),
                parsed.trailer());
    }

    private byte[] compose(String header, String code, byte[] body, byte[] trailer) {
        requireHeader(header);
        if (code == null || (code.length() != 2 && code.length() != 4)) {
            throw invalid("code must be two or four ASCII characters");
        }
        requireAscii(ascii(code, "code"), "code");
        if (body == null || trailer == null) {
            throw invalid("body and trailer must not be null");
        }
        requireAscii(body, "body");
        requireAscii(trailer, "trailer");
        requireNoEm(trailer);

        int trailerEnvelopeLength = trailer.length == 0 ? 0 : 1 + trailer.length;
        if (body.length > 0xffff - headerLength - code.length() - trailerEnvelopeLength) {
            throw invalid("message is too long");
        }

        byte[] payload = new byte[headerLength + code.length() + body.length + trailerEnvelopeLength];
        int offset = copyAscii(header, payload, 0);
        offset += copyAscii(code, payload, offset);
        System.arraycopy(body, 0, payload, offset, body.length);
        offset += body.length;

        // EM belongs only to the optional trailer envelope. Appending it to every
        // frame changes the common no-trailer form (for example NC).
        if (trailer.length != 0) {
            payload[offset++] = EM;
            System.arraycopy(trailer, 0, payload, offset, trailer.length);
        }

        if (!tcpLengthPrefix) {
            return payload;
        }
        byte[] result = new byte[payload.length + 2];
        result[0] = (byte) (payload.length >>> 8);
        result[1] = (byte) payload.length;
        System.arraycopy(payload, 0, result, 2, payload.length);
        return result;
    }

    private ParsedPayload parsePayload(byte[] framed) {
        if (framed == null) {
            throw invalid("message must not be null");
        }

        int start = 0;
        int length = framed.length;
        if (tcpLengthPrefix) {
            if (length < 2) {
                throw invalid("missing TCP length prefix");
            }
            int declaredLength = ((framed[0] & 0xff) << 8) | (framed[1] & 0xff);
            if (declaredLength != length - 2) {
                throw invalid("TCP length prefix does not match payload");
            }
            start = 2;
            length = declaredLength;
        }

        if (length < headerLength + 2) {
            throw invalid("message is too short");
        }
        String header = ascii(framed, start, headerLength, "header");
        int codeOffset = start + headerLength;

        int delimiter = findTrailerDelimiter(framed, codeOffset + 2, start + length);
        int messageEnd = delimiter < 0 ? start + length : delimiter;
        byte[] rest = Arrays.copyOfRange(framed, codeOffset, messageEnd);
        byte[] body = Arrays.copyOfRange(framed, codeOffset + 2, messageEnd);
        byte[] trailer = delimiter < 0
                ? new byte[0]
                : Arrays.copyOfRange(framed, delimiter + 1, start + length);

        requireAscii(rest, "message fields");
        requireAscii(trailer, "trailer");
        return new ParsedPayload(header, rest, body, trailer);
    }

    /**
     * EM is not a universal end-of-message marker. Clause 1.3 uses it to
     * introduce an optional trailer, so a frame without a trailer simply ends
     * after its command data. Searching only after the two-character command
     * code also prevents the code itself from being mistaken for payload.
     */
    private static int findTrailerDelimiter(byte[] framed, int from, int to) {
        for (int i = from; i < to; i++) {
            if (framed[i] == EM) {
                return i;
            }
        }
        return -1;
    }

    private record ParsedPayload(String header, byte[] rest, byte[] body, byte[] trailer) {
    }

    private void requireHeader(String header) {
        if (header == null || header.length() != headerLength) {
            throw invalid("header must have configured length");
        }
        requireAscii(ascii(header, "header"), "header");
    }

    private static void requireCode(String code, String label) {
        if (code == null || code.length() != 2) {
            throw invalid(label + " must be exactly two ASCII characters");
        }
        requireAscii(ascii(code, label), label);
    }

    private static byte[] ascii(String value, String label) {
        if (value == null) {
            throw invalid(label + " must not be null");
        }
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (!new String(bytes, StandardCharsets.US_ASCII).equals(value)) {
            throw invalid(label + " must be ASCII");
        }
        return bytes;
    }

    private static String ascii(byte[] bytes, int offset, int length, String label) {
        byte[] slice = Arrays.copyOfRange(bytes, offset, offset + length);
        requireAscii(slice, label);
        return new String(slice, StandardCharsets.US_ASCII);
    }

    private static void requireAscii(byte[] bytes, String label) {
        if (bytes == null) {
            throw invalid(label + " must not be null");
        }
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned < 0x20 || unsigned > 0x7e) {
                throw invalid(label + " must contain printable ASCII bytes");
            }
        }
    }

    private static void requireNoEm(byte[] trailer) {
        for (byte value : trailer) {
            if (value == EM) {
                throw invalid("trailer must not contain EM");
            }
        }
    }

    private static int copyAscii(String value, byte[] destination, int offset) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, destination, offset, bytes.length);
        return bytes.length;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
