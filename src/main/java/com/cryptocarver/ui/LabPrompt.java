package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;

/** Confirmations and sensitive-data notices omitted in full laboratory mode. */
enum LabPrompt {
    FILE_OVERWRITE,
    CBC_IV_REUSE,
    FILE_CIPHER_RECIPE,
    JWKS_SECRET,
    PADES_PII,
    SHELF_SENSITIVE,
    XML_REPORT_EXPORT,
    HISTORY_CLEAR,
    SESSION_TRAIL_CLEAR,
    SESSION_LOAD,
    SESSION_STEP,
    RECIPE_LOAD,
    CONFIGURATION_EXPORT,
    CONFIGURATION_IMPORT,
    KEY_ARCHIVE,
    KEY_DELETE,
    TOKEN_CERTIFICATE_UPDATE,
    TEMPLATE_DELETE;

    boolean shouldShow() {
        return !AppSettings.isFullLab();
    }
}
