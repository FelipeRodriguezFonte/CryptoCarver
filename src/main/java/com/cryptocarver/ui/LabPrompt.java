package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;

/** Prompts intentionally omitted in full laboratory mode. */
enum LabPrompt {
    CBC_IV_REUSE,
    FILE_CIPHER_RECIPE,
    JWKS_SECRET,
    PADES_PII,
    SHELF_SENSITIVE,
    XML_REPORT_EXPORT,
    SESSION_STEP,
    CONFIGURATION_EXPORT,
    KEY_ARCHIVE;

    boolean shouldShow() {
        return !AppSettings.isFullLab();
    }
}
