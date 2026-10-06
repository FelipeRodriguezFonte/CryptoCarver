package com.cryptocarver.ui;

import com.nimbusds.jose.util.Base64URL;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Owns JOSE token inspection and its presentation in the inspector flow. */
final class JoseInspectorCoordinator {
    public void inspectToken(String token, TextFlow outputFlow) {
        outputFlow.getChildren().clear();
        inspectTokenRecursive(token, outputFlow, 0);
    }

    private void inspectTokenRecursive(String token, TextFlow outputFlow, int depth) {
        if (token == null || token.trim().isEmpty())
            return;

        String indent = "  ".repeat(depth);
        String prefix = depth > 0 ? indent + "↳ " : "";
        String[] parts = token.trim().split("\\.", -1);

        try {
            if (token.trim().startsWith("{")) {
                inspectJsonSerialization(token.trim(), outputFlow, depth, prefix);
            } else if (parts.length == 3) {
                // JWS
                addText(outputFlow, prefix + "[JWS Detected]\n", Color.LIGHTGREEN, true);
                addSection(outputFlow, "HEADER", parts[0], Color.RED, true, depth);
                String payload = addSection(outputFlow, "PAYLOAD", parts[1], Color.MAGENTA, true, depth);
                addSection(outputFlow, "SIGNATURE", parts[2], Color.CYAN, false, depth);

                // Recursion check on Payload
                if (payload != null && (payload.startsWith("ey") || payload.startsWith("{"))) {
                    // Check if it looks like a token
                    if (payload.split("\\.").length >= 3) {
                        addText(outputFlow, "\n" + indent + "=== NESTED TOKEN IN PAYLOAD ===\n", Color.GOLD, true);
                        inspectTokenRecursive(payload, outputFlow, depth + 1);
                    }
                }

            } else if (parts.length == 5) {
                // JWE
                addText(outputFlow, prefix + "[JWE Detected]\n", Color.LIGHTBLUE, true);
                String header = addSection(outputFlow, "HEADER", parts[0], Color.RED, true, depth);
                addSection(outputFlow, "ENCRYPTED KEY", parts[1], Color.ORANGE, false, depth);
                addSection(outputFlow, "IV", parts[2], Color.GREEN, false, depth);
                addSection(outputFlow, "CIPHERTEXT", parts[3], Color.BLUE, false, depth);
                addSection(outputFlow, "TAG", parts[4], Color.YELLOW, false, depth);

                // Hint for Nested JWE
                if (header != null && (header.contains("\"cty\":\"JWT\"") || header.contains("\"cty\": \"JWT\""))) {
                    addText(outputFlow, "\n" + indent
                            + " > NOTE: Header indicates 'cty':'JWT'. This JWE contains a Nested Token (likely Signed).\n",
                            Color.GOLD, true);
                    addText(outputFlow, indent
                            + " > Decrypt this token in the 'JWE' tab, then inspect the result to see the inner token.\n",
                            Color.GOLD, false);
                }
            } else {
                addText(outputFlow, prefix + "Unknown/Raw Data: " + token + "\n\n", Color.WHITE);
            }
        } catch (Exception e) {
            addText(outputFlow, prefix + "Error: " + e.getMessage() + "\n", Color.RED);
        }
    }

    private String addSection(TextFlow flow, String title, String part, Color color, boolean isJson, int depth) {

        String indent = "  ".repeat(depth);
        addText(flow, indent + "=== " + title + " ===\n", color, true);
        addText(flow, indent + "Raw: " + part + "\n", Color.GRAY);

        String decodedContent = null;
        try {
            if (part.isEmpty()) {
                addText(flow, indent + "(Empty)\n\n", Color.WHITE);
                return null;
            }
            Base64URL b64 = new Base64URL(part);
            String decoded = b64.decodeToString();
            decodedContent = decoded;

            if (isJson) {
                try {
                    if (title.contains("HEADER")) {
                        decoded = prettyJson(decoded);
                    } else if (title.equals("PAYLOAD")) {
                        try {
                            decoded = com.nimbusds.jwt.JWTClaimsSet.parse(decoded).toString();
                        } catch (Exception e) {
                            // Plain text or token string
                        }
                    }
                } catch (Exception e) {
                    /* ignore */ }
            } else {
                byte[] bytes = b64.decode();
                decoded = "Hex: " + bytesToHex(bytes) + " (" + bytes.length + " bytes)";
            }
            // Indent decoded outpu
            decoded = decoded.replace("\n", "\n" + indent);
            addText(flow, indent + decoded + "\n\n", Color.WHITE);

        } catch (Exception e) {
            addText(flow, indent + "Could not decode: " + e.getMessage() + "\n\n", Color.RED);
        }
        return decodedContent;
    }

    /** Breaks down JWS or JWE JSON serialization (RFC 7515 §7.2, RFC 7516 §7.2). */
    private void inspectJsonSerialization(String json, TextFlow flow, int depth, String prefix) throws Exception {
        Map<String, Object> members = com.nimbusds.jose.util.JSONObjectUtils.parse(json);
        String indent = "  ".repeat(depth);
        if (members.containsKey("ciphertext")) {
            boolean general = members.containsKey("recipients");
            addText(flow, prefix + "[JWE JSON Detected — " + (general ? "General" : "Flattened") + "]\n", Color.LIGHTBLUE, true);
            addOptionalSection(flow, "PROTECTED HEADER", members.get("protected"), Color.RED, true, depth);
            if (members.get("unprotected") != null) addJsonMember(flow, "SHARED UNPROTECTED HEADER", members.get("unprotected"), depth);
            addOptionalSection(flow, "AAD", members.get("aad"), Color.MAGENTA, true, depth);
            List<Map<String, Object>> recipients = new ArrayList<>();
            if (general) {
                for (Map<String, Object> recipient : com.nimbusds.jose.util.JSONObjectUtils.getJSONObjectArray(members, "recipients")) {
                    recipients.add(recipient);
                }
            } else {
                recipients.add(members);
            }
            for (int i = 0; i < recipients.size(); i++) {
                String label = general ? "RECIPIENT " + (i + 1) + " " : "";
                if (recipients.get(i).get("header") != null) addJsonMember(flow, label + "HEADER", recipients.get(i).get("header"), depth);
                addOptionalSection(flow, label + "ENCRYPTED KEY", recipients.get(i).get("encrypted_key"), Color.ORANGE, false, depth);
            }
            addOptionalSection(flow, "IV", members.get("iv"), Color.GREEN, false, depth);
            addOptionalSection(flow, "CIPHERTEXT", members.get("ciphertext"), Color.BLUE, false, depth);
            addOptionalSection(flow, "TAG", members.get("tag"), Color.YELLOW, false, depth);
        } else if (members.containsKey("signatures") || members.containsKey("signature")) {
            boolean general = members.containsKey("signatures");
            addText(flow, prefix + "[JWS JSON Detected — " + (general ? "General" : "Flattened") + "]\n", Color.LIGHTGREEN, true);
            if (members.get("payload") == null) {
                addText(flow, indent + "=== PAYLOAD ===\n" + indent + "(Detached — not included)\n\n", Color.MAGENTA, true);
            } else {
                addSection(flow, "PAYLOAD", String.valueOf(members.get("payload")), Color.MAGENTA, true, depth);
            }
            List<Map<String, Object>> signatures = new ArrayList<>();
            if (general) {
                for (Map<String, Object> signature : com.nimbusds.jose.util.JSONObjectUtils.getJSONObjectArray(members, "signatures")) {
                    signatures.add(signature);
                }
            } else {
                signatures.add(members);
            }
            for (int i = 0; i < signatures.size(); i++) {
                String label = general ? "SIGNATURE " + (i + 1) + " " : "";
                addOptionalSection(flow, label + "PROTECTED HEADER", signatures.get(i).get("protected"), Color.RED, true, depth);
                if (signatures.get(i).get("header") != null) addJsonMember(flow, label + "UNPROTECTED HEADER", signatures.get(i).get("header"), depth);
                addOptionalSection(flow, label + "SIGNATURE", signatures.get(i).get("signature"), Color.CYAN, false, depth);
            }
        } else {
            addText(flow, prefix + "JSON without JWS/JWE members (no 'ciphertext', 'signature' or 'signatures').\n\n", Color.WHITE);
        }
    }

    private void addOptionalSection(TextFlow flow, String title, Object value, Color color, boolean isJson, int depth) {
        if (value != null) addSection(flow, title, String.valueOf(value), color, isJson, depth);
    }

    private void addJsonMember(TextFlow flow, String title, Object value, int depth) {
        String indent = "  ".repeat(depth);
        addText(flow, indent + "=== " + title + " ===\n", Color.RED, true);
        addText(flow, indent + prettyJson(new com.google.gson.Gson().toJson(value)).replace("\n", "\n" + indent) + "\n\n", Color.WHITE);
    }

    private static String prettyJson(String json) {
        try {
            return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(com.google.gson.JsonParser.parseString(json));
        } catch (Exception notJson) {
            return json;
        }
    }

    private void addText(TextFlow flow, String text, Color color) {
        addText(flow, text, color, false);
    }

    private void addText(TextFlow flow, String text, Color color, boolean bold) {
        Text t = new Text(text);
        t.setFill(color);
        t.setFont(Font.font("Monospaced", bold ? FontWeight.BOLD : FontWeight.NORMAL, 13));
        flow.getChildren().add(t);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

}
