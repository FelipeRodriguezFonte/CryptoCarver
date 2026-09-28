package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Pure construction policy for automatically recorded operation history. */
public final class HistoryCommandPolicy {
    private static final String REDACTED = "[REDACTED_SECRET]";
    private static final Gson DETAILS_JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private HistoryCommandPolicy() { }

    public static HistoryCommand create(String operation, List<OperationDetail> details,
                                        Map<String, Object> recipe, String inputFormat,
                                        String outputFormat, String navigationCandidate,
                                        Predicate<String> navigationTargetExists) {
        Map<String, Object> safeRecipe = recipe == null ? Map.of() : new LinkedHashMap<>(recipe);
        List<OperationDetail> safeDetails = details == null ? List.of() : List.copyOf(details);
        String detailsJson = serializeDetails(safeDetails);
        boolean redacted = safeRecipe.values().stream().anyMatch(REDACTED::equals);
        HistoryCommand.Reproducibility reproducibility = redacted
                ? HistoryCommand.Reproducibility.REPRODUCIBLE_WITH_SECRETS
                : HistoryCommand.Reproducibility.REPRODUCIBLE_WITHOUT_SECRETS;
        String reason = redacted
                ? "Sensitive secrets were redacted from the history recipe."
                : "All parameters are available.";
        String navigation = effectiveNavigationTarget(operation, navigationCandidate, navigationTargetExists);
        HistoryCommand command = new HistoryCommand(operation, detailsJson, safeRecipe, reproducibility,
                reason, inputFormat, outputFormat, navigation);
        command.setStructuredDetails(safeDetails);
        return command;
    }

    public static String effectiveNavigationTarget(String operation, String candidate,
                                                  Predicate<String> navigationTargetExists) {
        return candidate != null && !candidate.isBlank() && navigationTargetExists != null
                && navigationTargetExists.test(candidate) ? candidate : operation;
    }

    private static String serializeDetails(List<OperationDetail> details) {
        if (details.isEmpty()) return "";
        try {
            return DETAILS_JSON.toJson(details);
        } catch (RuntimeException serializationFailure) {
            return details.toString();
        }
    }
}
