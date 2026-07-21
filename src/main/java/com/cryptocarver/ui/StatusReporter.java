package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import java.util.List;

/**
 * Interface for controllers that can report status and show errors.
 * Bridging MainController (Lead/Legacy) and ModernMainController.
 */
public interface StatusReporter {
    void updateStatus(String message);

    void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details);

    void showError(String title, String message);

    default void showInfo(String title, String message) {
        // Optional for non-JavaFX hosts and controller tests.
    }

    default void addToHistory(String operation, List<OperationDetail> details) {
        // Default implementation does nothing, for legacy compatibility
    }

    /** Publishes one coherent result to inspector, history and status. */
    default void publish(OperationResult result) {
        if (result == null) return;
        updateInspector(result.getOperation(), result.getInput(), result.getOutput(), result.getDetails());
        addToHistory(result.getOperation(), result.getDetails());
        if (result.getStatusMessage() != null && !result.getStatusMessage().isBlank()) {
            updateStatus(result.getStatusMessage());
        }
    }
}
