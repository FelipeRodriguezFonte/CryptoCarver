package com.cryptocarver.ui;

/** Small adapter that keeps controller validation on the shared accessible error contract. */
final class InlineValidationSupport {
    private InlineValidationSupport() {}

    static void show(StatusReporter reporter, String title, String detail, String remedy,
                     String fieldKey, Throwable cause) {
        if (reporter == null) return;
        reporter.showError(new UserFacingError(
                InlineErrorPresenter.redactSecrets(title),
                InlineErrorPresenter.redactSecrets(detail),
                InlineErrorPresenter.redactSecrets(remedy),
                fieldKey,
                cause));
    }

    static void showValidation(StatusReporter reporter, String title, String detail, String remedy,
                               FieldValidationException error) {
        show(reporter, title, detail, remedy, error == null ? null : error.fieldKey(), error);
    }
}
