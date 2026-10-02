package com.cryptocarver.ui;

/** Masks a PAN for published details and history, keeping only its last four digits. */
final class PanMask {
    private PanMask() {
    }

    static String mask(String pan) {
        if (pan == null || pan.length() < 5) return "[redacted]";
        return "*".repeat(Math.max(0, pan.length() - 4)) + pan.substring(pan.length() - 4);
    }
}
