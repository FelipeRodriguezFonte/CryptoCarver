package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationSessionLog;
import com.cryptocarver.model.SavedSession;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.SessionOperationStep;
import com.cryptocarver.service.I18nService;

/** Text for reviewing saved operations without changing the active workspace. */
final class SessionTrailViewFormatter {
    private SessionTrailViewFormatter() { }

    static String step(SessionOperationStep step, SecretVisibilityProfile visibility, I18nService i18n) {
        StringBuilder text = new StringBuilder();
        text.append(step.getTitle()).append("\n")
                .append(step.getOperation()).append(" · ").append(step.getTimestamp()).append("\n");
        if (step.getStatus() != null && !step.getStatus().isBlank()) {
            text.append(step.getStatus()).append("\n");
        }
        if (!step.getTags().isEmpty()) text.append(String.join(", ", step.getTags())).append("\n");
        appendPayload(text, i18n.text("sessionTrail.input"), step.isInputPresent(),
                step.getInputText(), step.getInputHex(), OperationDetail.Classification.SECRET, visibility, i18n);
        appendPayload(text, i18n.text("sessionTrail.output"), step.isOutputPresent(),
                step.getOutputText(), step.getOutputHex(), step.getOutputClassification(), visibility, i18n);
        if (step.getEnrichedOutput() != null) {
            text.append("\n").append(i18n.text("sessionTrail.enrichedOutput")).append("\n")
                    .append(project(step.getEnrichedOutput(), step.getEnrichedOutputClassification(), visibility))
                    .append("\n");
        }
        if (!step.getDetails().isEmpty()) {
            text.append("\n").append(i18n.text("sessionTrail.details")).append("\n");
            for (OperationDetail detail : step.getDetails()) {
                text.append(detail.name()).append(": ")
                        .append(project(detail.value(), detail.classification(), visibility)).append("\n");
            }
        }
        if (!step.getParameters().isEmpty()) {
            text.append("\n").append(i18n.text("sessionTrail.parameters")).append("\n");
            step.getParameters().forEach((name, value) -> text.append(name).append(": ")
                    .append(project(value, OperationDetail.Classification.SECRET, visibility)).append("\n"));
        }
        return text.toString();
    }

    static String preview(SavedSession session, I18nService i18n) {
        StringBuilder text = new StringBuilder();
        text.append(session.getName()).append("\n")
                .append(session.getTimestamp()).append(" · ").append(session.getOperation()).append("\n\n");
        OperationSessionLog log = session.getOperationLog();
        if (log == null || log.isEmpty()) {
            text.append(i18n.text("sessionTrail.empty"));
        } else {
            int index = 1;
            for (SessionOperationStep step : log.getSteps()) {
                text.append(index++).append(". ").append(step.getTitle())
                        .append(" · ").append(step.getOperation())
                        .append(" · ").append(step.getTimestamp()).append("\n");
            }
        }
        return text.toString();
    }

    private static void appendPayload(StringBuilder text, String title, boolean present, String utf8,
                                      String hex, OperationDetail.Classification classification,
                                      SecretVisibilityProfile visibility, I18nService i18n) {
        text.append("\n").append(title).append("\n");
        if (!present) {
            text.append(i18n.text("sessionTrail.notPresent")).append("\n");
        } else if (visibility != SecretVisibilityProfile.FULL_LAB
                && classification != OperationDetail.Classification.PUBLIC) {
            text.append(project("", classification, visibility)).append("\n");
        } else {
            if (utf8 != null) text.append("UTF-8: ").append(utf8).append("\n");
            if (hex != null) text.append("HEX: ").append(hex).append("\n");
        }
    }

    private static String project(String value, OperationDetail.Classification classification,
                                  SecretVisibilityProfile visibility) {
        if (visibility == SecretVisibilityProfile.FULL_LAB
                || classification == OperationDetail.Classification.PUBLIC) return value == null ? "" : value;
        return visibility == SecretVisibilityProfile.REDACTED
                && classification == OperationDetail.Classification.SECRET ? "***REDACTED***" : "***MASKED***";
    }
}
