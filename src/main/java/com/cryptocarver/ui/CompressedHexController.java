package com.cryptocarver.ui;

import com.cryptocarver.model.CompressedHexCodec;
import com.cryptocarver.model.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

import java.nio.charset.StandardCharsets;

/**
 * Small, self-contained Generic tool for the two-row hexadecimal convention
 * commonly used by host tooling.
 */
public final class CompressedHexController {
    @FXML private TextArea compressedHexInputArea;
    @FXML private TextArea compressedHexOutputArea;
    @FXML private javafx.scene.control.Button expandButton;
    @FXML private javafx.scene.control.Button compressButton;
    @FXML private javafx.scene.control.Button clearButton;

    private StatusReporter reporter;
    private ContextualHelpButtonBinder contextualHelpButtons;
    private com.cryptocarver.model.help.HelpContextState contextualHelpState;
    private boolean contextualHelpEnabled;
    private String activeDirection = "expand";

    public void configureContextualHelp(com.cryptocarver.model.help.HelpContextState state,
            java.util.function.Consumer<com.cryptocarver.model.help.HelpContext> opener) {
        if (contextualHelpButtons != null || state == null) return;
        contextualHelpState = state;
        contextualHelpButtons = new ContextualHelpButtonBinder(state, opener);
        contextualHelpButtons.bind("op_gen_compressed_hex", "op_gen_compressed_hex", "expand", "", expandButton);
        contextualHelpButtons.bind("op_gen_compressed_hex", "op_gen_compressed_hex", "compress", "", compressButton);
        contextualHelpButtons.bind("op_gen_compressed_hex", "op_gen_compressed_hex", "clear", "", clearButton);
        contextualHelpButtons.bind("op_gen_compressed_hex", "op_gen_compressed_hex", this::activeHelpDirection, "input", compressedHexInputArea);
        contextualHelpButtons.bind("op_gen_compressed_hex", "op_gen_compressed_hex", this::activeHelpDirection, "output", compressedHexOutputArea);
        setActiveContextualHelpOperation("", false);
    }

    private String activeHelpDirection() {
        if (contextualHelpState != null && "op_gen_compressed_hex".equals(contextualHelpState.current().operationId())) {
            String action = contextualHelpState.current().actionId();
            if ("expand".equals(action) || "compress".equals(action)) return action;
        }
        return activeDirection;
    }

    public void setActiveContextualHelpOperation(String operationId, boolean enabled) {
        contextualHelpEnabled = enabled;
        if (contextualHelpButtons == null) return;
        contextualHelpButtons.setActiveRoute(operationId);
        contextualHelpButtons.setEnabled(enabled && com.cryptocarver.model.AppSettings.getInstance().isContextualHelpEnabled());
    }

    public void setReporter(StatusReporter reporter) {
        this.reporter = reporter;
    }

    @FXML
    private void handleExpandCompressedHex() {
        activeDirection = "expand";
        publishDirection("expand");
        transform(true);
    }

    @FXML
    private void handleCompressHex() {
        activeDirection = "compress";
        publishDirection("compress");
        transform(false);
    }

    private void publishDirection(String direction) {
        if (contextualHelpEnabled && contextualHelpState != null) {
            contextualHelpState.setOperation("op_gen_compressed_hex");
            contextualHelpState.setAction(direction);
            contextualHelpState.setField("");
        }
    }

    @FXML
    private void handleClearCompressedHex() {
        compressedHexInputArea.clear();
        compressedHexOutputArea.clear();
        if (reporter != null) {
            reporter.updateStatus("Compressed Hex fields cleared.");
        }
    }

    private void transform(boolean expand) {
        // A rejected transformation must not leave a stale output from the last direction.
        compressedHexOutputArea.clear();
        try {
            String input = compressedHexInputArea.getText();
            String output = expand
                    ? CompressedHexCodec.expandTwoRows(input)
                    : CompressedHexCodec.compressToTwoRows(input);
            compressedHexOutputArea.setText(output);
            if (reporter != null) {
                String operation = expand ? "Compressed Hex → Hex" : "Hex → Compressed Hex";
                reporter.publish(OperationResult.forOperation(operation)
                        .input(input.getBytes(StandardCharsets.US_ASCII))
                        .output(output.getBytes(StandardCharsets.US_ASCII))
                        .enrichedOutput(output)
                        .status(operation + " completed").build());
            }
        } catch (IllegalArgumentException exception) {
            if (reporter != null) {
                reporter.showError("Compressed Hex", exception.getMessage());
            }
        }
    }
}
