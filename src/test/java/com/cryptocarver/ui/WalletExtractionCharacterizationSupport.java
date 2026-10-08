package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.cryptocarver.utils.OperationHistory;
import com.google.gson.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production Wallet controls and shell, with the existing isolated shell lifecycle. */
abstract class WalletExtractionCharacterizationSupport extends EmvExtractionCharacterizationSupport {
    protected WalletController wallet;
    protected RecordingReporter recorder;

    protected void withWallet(FxAction action) throws Exception {
        onFx(() -> {
            OperationHistory legacy = OperationHistory.getInstance();
            var previous = legacy.getHistory();
            Path previousPath = (Path) get(legacy, "historyFilePath");
            @SuppressWarnings("unchecked") var entries = (List<OperationHistory.OperationEntry>) get(legacy, "history");
            try {
                set(legacy, "historyFilePath", tempDir.resolve("legacy-history.json"));
                entries.clear();
                shell.navigateTo("SD-JWT VC");
                wallet = (WalletController) get(shell, "walletController");
                replaceReporter();
                action.run();
            } finally {
                entries.clear(); entries.addAll(previous);
                set(legacy, "historyFilePath", previousPath);
            }
        });
    }
    protected void replaceReporter() { recorder = new RecordingReporter(shell); wallet.setReporter(recorder); }
    protected void put(String field, String value) throws Exception { ((TextInputControl) get(wallet, field)).setText(value); }
    protected String text(String field) throws Exception { return ((TextInputControl) get(wallet, field)).getText(); }
    @SuppressWarnings("unchecked") protected ComboBox<String> combo(String field) throws Exception { return (ComboBox<String>) get(wallet, field); }
    protected void invoke(String handler) throws Exception {
        recorder.error = null; recorder.result = null;
        var method = WalletController.class.getDeclaredMethod(handler);
        method.setAccessible(true); method.invoke(wallet);
    }
    protected void showResult(String field) throws Exception {
        Node area = (Node) get(wallet, field);
        Parent container = (Parent) get(wallet, "walletContainer");
        for (Node node : container.lookupAll(".accordion")) {
            Accordion accordion = (Accordion) node;
            for (TitledPane pane : accordion.getPanes()) if (contains(pane.getContent(), area)) {
                pane.setAnimated(false); accordion.setExpandedPane(pane);
            }
        }
        root.applyCss(); root.layout(); area.requestFocus();
    }
    protected void valid(String handler, String output, List<String> transcript) throws Exception {
        showResult(output);
        int before = shell.getHistoryManager().getHistoryItems().size();
        invoke(handler);
        assertNull(recorder.error, handler);
        assertNotNull(recorder.result, handler);
        assertFalse(text(output).isBlank(), handler);
        assertEquals(before + 1, shell.getHistoryManager().getHistoryItems().size(), handler);
        assertEquals(text(output), new String(recorder.result.getOutput(), java.nio.charset.StandardCharsets.UTF_8));
        transcript.add(handler + "|" + recorder.result.getOperation() + "|" + recorder.result.getStatusMessage()
                + "|" + recorder.result.getDetails().stream().filter(d -> d.classification() == OperationDetail.Classification.PUBLIC).toList());
    }
    protected void validation(String handler, String field, List<String> transcript) throws Exception {
        int before = shell.getHistoryManager().getHistoryItems().size();
        invoke(handler);
        assertNotNull(recorder.error, handler);
        assertEquals(field, recorder.error.fieldKey());
        assertNull(recorder.result);
        assertEquals(before, shell.getHistoryManager().getHistoryItems().size());
        transcript.add(handler + "|validation|" + field + "|" + recorder.error.remedy());
    }
    protected void invalidStructure(String handler, String field, List<String> transcript) throws Exception {
        int before = shell.getHistoryManager().getHistoryItems().size();
        invoke(handler);
        assertNotNull(recorder.error);
        assertEquals(field, recorder.error.fieldKey());
        assertNull(recorder.result);
        assertEquals(before, shell.getHistoryManager().getHistoryItems().size());
        transcript.add(handler + "|invalid-structure|" + field + "|" + recorder.status);
    }
    private static boolean contains(Node node, Node target) {
        if (node == target) return true;
        return node instanceof Parent parent && parent.getChildrenUnmodifiable().stream().anyMatch(child -> contains(child, target));
    }
    protected static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END " + type + "-----\n";
    }
    /** Canonical object order; volatile fields retain their role with a stable placeholder. */
    protected static String stableJson(String json) { return canonical(JsonParser.parseString(json)).toString(); }
    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> {
                JsonElement child = value.getAsJsonObject().get(key);
                if (Set.of("iat", "exp", "validFrom", "validUntil", "signed", "sd_hash", "lst", "x", "y", "d", "kid").contains(key)) {
                    sorted.addProperty(key, "<VOLATILE>");
                } else if (key.equals("_sd")) {
                    JsonArray digests = new JsonArray(); child.getAsJsonArray().forEach(item -> digests.add("<DIGEST>")); sorted.add(key, digests);
                } else sorted.add(key, canonical(child));
            });
            return sorted;
        }
        if (value.isJsonArray()) { JsonArray array = new JsonArray(); value.getAsJsonArray().forEach(child -> array.add(canonical(child))); return array; }
        return value.deepCopy();
    }
    protected static final class RecordingReporter implements StatusReporter {
        private final StatusReporter delegate;
        UserFacingError error;
        OperationResult result;
        String status;
        RecordingReporter(StatusReporter delegate) { this.delegate = delegate; }
        public void updateStatus(String message) { status = message; delegate.updateStatus(message); }
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) { delegate.updateInspector(operation, input, output, details); }
        public void showError(String title, String message) { delegate.showError(title, message); }
        public void showError(UserFacingError value) { error = value; delegate.showError(value); }
        public void publish(OperationResult value) { result = value; delegate.publish(value); }
    }
}
