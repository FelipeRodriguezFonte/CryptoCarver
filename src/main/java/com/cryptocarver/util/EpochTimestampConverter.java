package com.cryptocarver.util;

import java.time.Instant;

/** Pure conversion between Unix epoch seconds and the canonical UTC timestamp. */
public final class EpochTimestampConverter {
    private EpochTimestampConverter() {
    }

    public static String toUtc(String epochSeconds) {
        return Instant.ofEpochSecond(Long.parseLong(epochSeconds.trim())).toString();
    }
}
