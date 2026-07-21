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

    private StatusReporter reporter;

    public void setReporter(StatusReporter reporter) {
        this.reporter = reporter;
    }

    @FXML
    private void handleExpandCompressedHex() {
        transform(true);
    }

    @FXML
    private void handleCompressHex() {
        transform(false);
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
                        .status(operation + " completed").build());
            }
        } catch (IllegalArgumentException exception) {
            if (reporter != null) {
                reporter.showError("Compressed Hex", exception.getMessage());
            }
        }
    }
}
