package com.cryptocarver.ui;

import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Coordinates Track 2 encoding/decoding UI and result publication. */
final class EmvTrack2Coordinator {
    record View(Supplier<TextField> pan,
            Supplier<TextField> expiry,
            Supplier<TextField> serviceCode,
            Supplier<TextField> discretionaryData,
            Supplier<TextField> track2Input,
            Supplier<TextArea> resultArea) {
    }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    EmvTrack2Coordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void handleEncodeTrack2() {
        try {
            String pan = view.pan().get().getText().trim().replaceAll("\\s+", "");
            String expiry = view.expiry().get().getText().trim();
            String serviceCode = view.serviceCode().get().getText().trim();
            String discretionaryData = view.discretionaryData().get().getText().trim();
            TextArea resultArea = view.resultArea().get();

            if (pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                resultArea.setText(text("module.emv.feedback.trackRequired"));
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("TRACK 2 ENCODING\n");
            result.append("════════════════\n\n");

            String track2 = EMVOperations.encodeTrack2(pan, expiry, serviceCode, discretionaryData);

            result.append("Input Data:\n");
            result.append("───────────\n");
            result.append("PAN: ").append(pan).append("\n");
            result.append("Expiry: ").append(expiry).append(" (YYMM)\n");
            result.append("Service Code: ").append(serviceCode).append("\n");
            if (!discretionaryData.isEmpty()) {
                result.append("Discretionary Data: ").append(discretionaryData).append("\n");
            }
            result.append("\n");

            result.append("Track 2 Equivalent Data:\n");
            result.append("────────────────────────\n");
            result.append(track2).append("\n\n");

            result.append("Format: PAN + 'D' + Expiry + Service Code + Discretionary Data\n");
            result.append("✅ Track 2 encoded successfully\n");

            resultArea.setText(result.toString());
            resultArea.setVisible(true);
            resultArea.setManaged(true);

            LinkedHashMap<String, String> details = new LinkedHashMap<>();
            details.put("PAN", maskPan(pan));
            details.put("Expiry", expiry);
            details.put("Service Code", serviceCode);
            reporter.get().publish(OperationResult.forOperation("Track 2 Encoding")
                    .output(track2.getBytes(StandardCharsets.US_ASCII), OperationDetail.Classification.SECRET)
                    .details(details)
                    .status(text("module.emv.status.trackEncoded")).build());

        } catch (Exception e) {
            view.resultArea().get().setText(text("module.emv.error.generate", e.getMessage()));
        }
    }

    void handleDecodeTrack2() {
        try {
            String track2Input = view.track2Input().get().getText().trim().replaceAll("\\s+", "");
            TextArea resultArea = view.resultArea().get();

            if (track2Input.isEmpty()) {
                resultArea.setText(text("module.emv.feedback.trackDataRequired"));
                return;
            }
            if (!track2Input.matches("(?i)[0-9]{12,19}(?:D|=)[0-9]{7}[0-9A-F]*")) {
                resultArea.setText(text("module.emv.track2.invalid"));
                resultArea.setVisible(true);
                resultArea.setManaged(true);
                return;
            }

            String result = EMVOperations.decodeTrack2(track2Input);
            resultArea.setText(result);
            resultArea.setVisible(true);
            resultArea.setManaged(true);

            reporter.get().publish(OperationResult.forOperation("Track 2 Decoding")
                    .input(track2Input.getBytes(StandardCharsets.US_ASCII))
                    .output(result.getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET)
                    .detail("PAN", "[contained in Track 2; not persisted]")
                    .status(text("module.emv.status.trackDecoded")).build());

        } catch (Exception e) {
            view.resultArea().get().setText(text("module.emv.error.generate", e.getMessage()));
        }
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 5) return "[redacted]";
        return "*".repeat(Math.max(0, pan.length() - 4)) + pan.substring(pan.length() - 4);
    }

    private String text(String key, Object... args) {
        return I18nService.getInstance().text(key, args);
    }
}
