package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Coordinates ARPC generation and its result publication. */
final class EmvArpcCoordinator {
    record View(Supplier<TextField> sessionKey,
            Supplier<TextField> arqc,
            Supplier<TextField> arc,
            Supplier<TextField> csu,
            Supplier<ComboBox<String>> method,
            Supplier<TextArea> resultArea) {
    }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    EmvArpcCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void handleGenerateARPC() {
        try {
            TextField sessionKeyField = view.sessionKey().get();
            TextField arqcField = view.arqc().get();
            TextField arcField = view.arc().get();
            TextField csuField = view.csu().get();
            ComboBox<String> methodCombo = view.method().get();
            TextArea resultArea = view.resultArea().get();

            String sk = sessionKeyField.getText().trim().replaceAll("\\s+", "");
            String arqc = arqcField.getText().trim().replaceAll("\\s+", "");
            String arc = arcField.getText().trim().replaceAll("\\s+", "");
            String csu = csuField.getText().trim().replaceAll("\\s+", "");

            if (sk.isEmpty() || arqc.isEmpty() || arc.isEmpty()) {
                resultArea.setText(text("module.emv.feedback.arpcRequired"));
                return;
            }

            if (sk.length() != 32 && sk.length() != 48) {
                resultArea.setText(text("module.emv.error.arpcSessionKeyLength"));
                resultArea.setVisible(true);
                resultArea.setManaged(true);
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("ARPC GENERATION (Authorization Response Cryptogram)\n");
            result.append("═══════════════════════════════════════════════════\n\n");

            String selectedMethod = methodCombo.getSelectionModel().getSelectedItem();
            String arpc;

            if (selectedMethod.contains("Method 1")) {
                result.append("Method: Method 1 (ARPC = Encrypt(ARQC ⊕ ARC))\n");
                result.append("──────────────────────────────────────────────\n");
                result.append("Session Key: ").append(sk).append("\n");
                result.append("ARQC: ").append(arqc).append("\n");
                result.append("ARC: ").append(arc).append("\n\n");

                arpc = EMVOperations.generateARPC_Method1(sk, arqc, arc);

            } else {
                result.append("Method: Method 2 (ARPC = MAC(ARQC || CSU), 4 bytes)\n");
                result.append("─────────────────────────────\n");
                result.append("Session Key: ").append(sk).append("\n");
                result.append("ARQC: ").append(arqc).append("\n");
                result.append("CSU: ").append(csu.isEmpty() ? "00000000" : csu).append("\n\n");

                arpc = EMVOperations.generateARPC_Method2(sk, arqc, csu.isEmpty() ? "00000000" : csu);
            }

            result.append("➜ ARPC: ").append(arpc).append("\n\n");
            result.append("✅ ARPC generated successfully\n");
            result.append("\nℹ️  Send this ARPC to the card in the authorization response (Tag 91)\n");

            resultArea.setText(result.toString());
            resultArea.setVisible(true);
            resultArea.setManaged(true);

            java.util.Map<String, String> details = new LinkedHashMap<>();
            details.put("Method", methodCombo == null ? "Default" : methodCombo.getValue());
            details.put("ARC", arc);
            details.put("Session Key", "[not persisted]");
            reporter.get().publish(OperationResult.forOperation("ARPC Generation")
                    .output(com.cryptocarver.util.DataConverter.hexToBytes(arpc), OperationDetail.Classification.SECRET)
                    .details(details)
                    .status(text("module.emv.status.arpc")).build());

        } catch (Exception e) {
            TextArea resultArea = view.resultArea().get();
            resultArea.setText(text("module.emv.error.generate", e.getMessage()));
            resultArea.setVisible(true);
            resultArea.setManaged(true);
        }
    }

    private String text(String key, Object... args) {
        return I18nService.getInstance().text(key, args);
    }
}
