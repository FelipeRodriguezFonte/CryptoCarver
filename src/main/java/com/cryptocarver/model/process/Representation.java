package com.cryptocarver.model.process;

import java.util.Set;

public enum Representation {
    BINARY, TEXT_UTF8, HEX, HEX_COMPONENTS, BASE64, BASE64URL;

    /** Standard wire representations; component bundles are intentionally excluded. */
    public static Set<Representation> standardValues() {
        return Set.of(BINARY, TEXT_UTF8, HEX, BASE64, BASE64URL);
    }
}
