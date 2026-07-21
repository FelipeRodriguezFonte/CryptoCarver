package com.cryptoforge.ui;

import java.util.Map;

/**
 * Minimal shell contract required by independently loaded operation modules.
 *
 * <p>The history view restores a saved form state and then asks the shell to
 * navigate to its operation. It must not know the concrete layout or the
 * controller that owns that layout.</p>
 */
public interface OperationNavigator {
    void restoreOperationState(Map<String, Object> state, String operation);
    
    void navigateTo(String operation);

    void updateStatus(String message);
}
