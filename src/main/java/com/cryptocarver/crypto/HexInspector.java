package com.cryptocarver.crypto;

/** Renders byte ranges as a classic hexadecimal/ASCII diagnostic view. */
public final class HexInspector {
    private HexInspector() { }

    public static String render(byte[] bytes, int offset, int length) {
        return render(bytes, offset, length, -1, 0);
    }

    /** Renders a view range and marks a separate absolute byte selection with brackets. */
    public static String render(byte[] bytes, int offset, int length, int selectionOffset, int selectionLength) {
        if (bytes == null) throw new IllegalArgumentException("Byte buffer cannot be null");
        if (offset < 0 || offset > bytes.length) throw new IllegalArgumentException("Offset is outside the byte buffer");
        if (length < 0) throw new IllegalArgumentException("Length cannot be negative");
        int end = Math.min(bytes.length, offset + length);
        StringBuilder view = new StringBuilder("Offset    Hex bytes                                        ASCII\n");
        for (int lineStart = offset; lineStart < end; lineStart += 16) {
            int lineEnd = Math.min(end, lineStart + 16);
            view.append(String.format("%08X  ", lineStart));
            for (int i = lineStart; i < lineStart + 16; i++) {
                if (i < lineEnd) {
                    boolean selected = i >= selectionOffset && i < selectionOffset + selectionLength;
                    view.append(selected ? String.format("[%02X]", bytes[i] & 0xFF) : String.format("%02X ", bytes[i] & 0xFF));
                } else view.append("   ");
                if (i == lineStart + 7) view.append(' ');
            }
            view.append(" | ");
            for (int i = lineStart; i < lineEnd; i++) {
                int value = bytes[i] & 0xFF;
                char character = value >= 0x20 && value <= 0x7E ? (char) value : '.';
                boolean selected = i >= selectionOffset && i < selectionOffset + selectionLength;
                view.append(selected ? Character.toUpperCase(character) : character);
            }
            view.append('\n');
        }
        if (offset == end) view.append("<empty range>\n");
        if (selectionOffset >= 0) view.append("Selection: offset ").append(selectionOffset).append(", length ").append(selectionLength).append(" (bytes in brackets)\n");
        return view.toString();
    }
}
