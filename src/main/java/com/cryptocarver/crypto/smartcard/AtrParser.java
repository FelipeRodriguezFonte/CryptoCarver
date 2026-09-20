package com.cryptocarver.crypto.smartcard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Parser for the Answer To Reset defined by ISO/IEC 7816-3.
 *
 * <p>The parser deliberately reports bytes it cannot interpret instead of
 * assigning meanings to historical bytes or proprietary interface values.
 * TS, T0, TAi/TBi/TCi/TD i, historical bytes and TCK are structural fields;
 * protocol-specific interpretation belongs to a higher layer.</p>
 *
 * <p>Source: ISO/IEC 7816-3, sections 8.2--8.3 (interface-byte groups,
 * historical bytes and TCK). The structural rules are also reproduced in
 * ETSI TS 102 221.</p>
 */
public final class AtrParser {
    private AtrParser() { }

    public static Atr parse(String hex) {
        if (hex == null || hex.isBlank()) throw new IllegalArgumentException("ATR is empty");
        String compact = hex.replaceAll("[\\s:-]", "").toUpperCase(Locale.ROOT);
        if (!compact.matches("[0-9A-F]+") || (compact.length() & 1) != 0)
            throw new IllegalArgumentException("ATR must be an even-length hexadecimal string");
        byte[] data = new byte[compact.length() / 2];
        for (int i = 0; i < data.length; i++)
            data[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        if (data.length < 2) throw new IllegalArgumentException("ATR must contain TS and T0");

        int p = 0;
        int ts = u8(data[p++]);
        if (ts != 0x3B && ts != 0x3F)
            throw new IllegalArgumentException("ATR TS must be 3B (direct) or 3F (inverse)");
        int t0 = u8(data[p++]);
        List<InterfaceGroup> groups = new ArrayList<>();
        Set<Integer> protocols = new LinkedHashSet<>();
        int current = t0;
        int group = 1;
        boolean hasTck = false;
        while (true) {
            int y = (current >>> 4) & 0x0F;
            OptionalInt ta = OptionalInt.empty();
            OptionalInt tb = OptionalInt.empty();
            OptionalInt tc = OptionalInt.empty();
            OptionalInt td = OptionalInt.empty();
            if ((y & 1) != 0) ta = OptionalInt.of(next(data, p++));
            if ((y & 2) != 0) tb = OptionalInt.of(next(data, p++));
            if ((y & 4) != 0) tc = OptionalInt.of(next(data, p++));
            if ((y & 8) != 0) {
                int value = next(data, p++);
                td = OptionalInt.of(value);
                int protocol = value & 0x0F;
                protocols.add(protocol);
                if (protocol != 0) hasTck = true;
                groups.add(new InterfaceGroup(group++, ta, tb, tc, td, protocol));
                current = value;
            } else {
                groups.add(new InterfaceGroup(group, ta, tb, tc, td, 0));
                break;
            }
        }
        int historicalLength = current & 0x0F;
        if (p + historicalLength > data.length)
            throw new IllegalArgumentException("ATR ends before all historical bytes are present");
        byte[] historical = new byte[historicalLength];
        System.arraycopy(data, p, historical, 0, historicalLength);
        p += historicalLength;

        OptionalInt tck = OptionalInt.empty();
        boolean tckValid = true;
        if (hasTck) {
            if (p >= data.length) throw new IllegalArgumentException("ATR requires TCK for a non-T=0 protocol");
            tck = OptionalInt.of(u8(data[p++]));
            int xor = 0;
            for (int i = 1; i < p; i++) xor ^= u8(data[i]);
            tckValid = xor == 0;
        }
        if (p != data.length)
            throw new IllegalArgumentException("ATR contains trailing bytes after TCK/historical bytes");
        return new Atr(compact, ts, t0, groups, historical, tck, tckValid,
                ts == 0x3B ? "direct" : "inverse", protocols);
    }

    private static int next(byte[] data, int index) {
        if (index >= data.length) throw new IllegalArgumentException("ATR is truncated in interface bytes");
        return u8(data[index]);
    }

    private static int u8(byte value) { return value & 0xFF; }

    public record InterfaceGroup(int number, OptionalInt ta, OptionalInt tb,
                                 OptionalInt tc, OptionalInt td, int protocol) { }

    public record Atr(String rawHex, int ts, int t0, List<InterfaceGroup> interfaceGroups,
                      byte[] historicalBytes, OptionalInt tck, boolean tckValid,
                      String convention, Set<Integer> protocols) {
        public Atr {
            interfaceGroups = Collections.unmodifiableList(new ArrayList<>(interfaceGroups));
            historicalBytes = historicalBytes.clone();
            protocols = Collections.unmodifiableSet(new LinkedHashSet<>(protocols));
        }

        public String historicalHex() {
            StringBuilder out = new StringBuilder();
            for (byte b : historicalBytes) out.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
            return out.toString();
        }
    }
}
