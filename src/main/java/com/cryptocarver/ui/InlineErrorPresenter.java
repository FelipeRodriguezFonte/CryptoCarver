package com.cryptocarver.ui;

import javafx.beans.value.ChangeListener;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presenter for the inline actionable error banner.
 * Manages banner visibility, field highlighting, field focusing, and redacted technical details clipboard copying.
 */
public class InlineErrorPresenter {

    @FunctionalInterface
    private interface ListenerUnregisterer {
        void unregister();
    }

    private final HBox errorBanner;
    private final Label errorBannerTitle;
    private final Label errorBannerRemedy;
    private final Button errorBannerGoToFieldBtn;
    private final Button errorBannerCopyDetailsBtn;
    private final Button errorBannerCloseBtn;

    private UserFacingError currentError;
    private Node focusBeforeError;
    private Node currentErrorTarget;
    private final List<Node> highlightedNodes = new ArrayList<>();
    private final List<ListenerUnregisterer> activeUnregisterers = new ArrayList<>();

    public InlineErrorPresenter(
            HBox errorBanner,
            Label errorBannerTitle,
            Label errorBannerRemedy,
            Button errorBannerGoToFieldBtn,
            Button errorBannerCopyDetailsBtn,
            Button errorBannerCloseBtn
    ) {
        this.errorBanner = errorBanner;
        this.errorBannerTitle = errorBannerTitle;
        this.errorBannerRemedy = errorBannerRemedy;
        this.errorBannerGoToFieldBtn = errorBannerGoToFieldBtn;
        this.errorBannerCopyDetailsBtn = errorBannerCopyDetailsBtn;
        this.errorBannerCloseBtn = errorBannerCloseBtn;
        hideBanner();
    }

    public void showError(UserFacingError error, Node activeSceneRoot) {
        clearFieldErrors();
        Node previousFocus = activeSceneRoot != null && activeSceneRoot.getScene() != null
                ? activeSceneRoot.getScene().getFocusOwner() : null;
        if (previousFocus != null && !isDescendantOf(errorBanner, previousFocus)) {
            focusBeforeError = previousFocus;
        }
        this.currentError = error;

        if (errorBanner == null) {
            System.err.println("SHOW_ERROR: " + safeAccessibleText(error.title()) + " - "
                    + safeAccessibleText(error.remedy()));
            return;
        }

        String safeTitle = safeAccessibleText(error.title() == null ? "Operation Error" : error.title());
        if (errorBannerTitle != null) {
            errorBannerTitle.setText(safeTitle);
            errorBannerTitle.setAccessibleText(safeTitle);
            errorBannerTitle.setAccessibleHelp(com.cryptocarver.service.I18nService.getInstance().text("a11y.errorTitle"));
        }

        String detailText = error.detail() == null ? "" : error.detail().trim();
        String remedy = error.remedy() == null ? "" : error.remedy().trim();
        // The banner must expose the specific, localized validation reason as
        // well as the remedy. Showing only a generic remedy hides the useful
        // controller feedback from users and assistive technology.
        String remedyText = detailText.isBlank() ? remedy
                : remedy.isBlank() || detailText.equals(remedy) ? detailText
                : detailText + " " + remedy;
        String safeRemedy = safeAccessibleText(remedyText);
        if (errorBannerRemedy != null) {
            errorBannerRemedy.setText(safeRemedy);
            errorBannerRemedy.setAccessibleText(safeRemedy);
            errorBannerRemedy.setAccessibleHelp(com.cryptocarver.service.I18nService.getInstance().text("a11y.errorRemedy"));
        }

        String fieldKey = error.fieldKey();
        boolean hasField = fieldKey != null && !fieldKey.isBlank();

        if (errorBannerGoToFieldBtn != null) {
            errorBannerGoToFieldBtn.setVisible(hasField);
            errorBannerGoToFieldBtn.setManaged(hasField);
        }

        if (hasField && activeSceneRoot != null) {
            Node fieldNode = findNodeByFieldKey(activeSceneRoot, fieldKey);
            if (isFocusableTarget(fieldNode)) {
                currentErrorTarget = fieldNode;
                highlightNode(fieldNode);
            } else {
                currentErrorTarget = null;
            }
        } else {
            currentErrorTarget = null;
        }

        errorBanner.setManaged(true);
        errorBanner.setVisible(true);

        // The banner is actionable but must not steal focus from a valid target.
        // ModernMainController also calls goToField for controller-level errors;
        // doing this here keeps the presenter safe for direct use and tests.
        if (isFocusableTarget(currentErrorTarget)) {
            currentErrorTarget.requestFocus();
            // A newly expanded TitledPane can claim focus during its layout
            // pulse. Reassert the target afterwards so the advertised
            // automatic focus is reliable in real JavaFX scenes.
            Platform.runLater(() -> Platform.runLater(() -> {
                if (isFocusableTarget(currentErrorTarget)) {
                    currentErrorTarget.requestFocus();
                }
            }));
        }
    }

    public void goToField(Node activeSceneRoot) {
        if (currentError == null || currentError.fieldKey() == null || activeSceneRoot == null) return;
        Node target = findNodeByFieldKey(activeSceneRoot, currentError.fieldKey());
        if (isFocusableTarget(target)) {
            currentErrorTarget = target;
            target.requestFocus();
        }
    }

    public void copyTechnicalDetails(StatusReporter reporter) {
        if (currentError == null) return;

        String redactedReport = formatRedactedTechnicalDetails(currentError);
        ClipboardContent content = new ClipboardContent();
        content.putString(redactedReport);
        Clipboard.getSystemClipboard().setContent(content);

        if (reporter != null) {
            reporter.updateStatus(com.cryptocarver.service.I18nService.getInstance().text("error.detailsCopied"));
        }
    }

    public void hideBanner() {
        Node focusToRestore = isFocusableTarget(currentErrorTarget) ? currentErrorTarget : focusBeforeError;
        clearFieldErrors();
        currentError = null;
        currentErrorTarget = null;
        focusBeforeError = null;

        if (errorBanner != null) {
            errorBanner.setVisible(false);
            errorBanner.setManaged(false);
        }
        if (isFocusableTarget(focusToRestore)) {
            focusToRestore.requestFocus();
        }
    }

    public boolean isVisible() {
        return errorBanner != null && errorBanner.isVisible();
    }

    public UserFacingError getCurrentError() {
        return currentError;
    }

    /** Shared redaction boundary for visible and accessible error text. */
    static String safeAccessibleText(String text) {
        return redactSecrets(text);
    }

    private static boolean isFocusableTarget(Node node) {
        return node != null && node.isVisible() && !node.isDisabled() && node.isFocusTraversable();
    }

    private static boolean isDescendantOf(Node ancestor, Node candidate) {
        if (ancestor == null || candidate == null) return false;
        Node current = candidate;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent();
        }
        return false;
    }

    private void highlightNode(Node node) {
        if (!node.getStyleClass().contains("field-error")) {
            node.getStyleClass().add("field-error");
            highlightedNodes.add(node);
        }

        if (node instanceof TextInputControl textControl) {
            ChangeListener<String> listener = (obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    node.getStyleClass().remove("field-error");
                }
            };
            textControl.textProperty().addListener(listener);
            activeUnregisterers.add(() -> textControl.textProperty().removeListener(listener));
        } else if (node instanceof ComboBox<?> comboBox) {
            ChangeListener<Object> listener = (obs, oldVal, newVal) -> {
                node.getStyleClass().remove("field-error");
            };
            comboBox.valueProperty().addListener(listener);
            activeUnregisterers.add(() -> comboBox.valueProperty().removeListener(listener));
        }
    }

    private void clearFieldErrors() {
        for (Node node : highlightedNodes) {
            if (node != null) {
                node.getStyleClass().remove("field-error");
            }
        }
        highlightedNodes.clear();

        for (ListenerUnregisterer unregisterer : activeUnregisterers) {
            try {
                unregisterer.unregister();
            } catch (Exception ignored) {}
        }
        activeUnregisterers.clear();
    }

    public static Node findNodeByFieldKey(Node root, String fieldKey) {
        if (root == null || fieldKey == null || fieldKey.isBlank()) return null;
        String cleanKey = fieldKey.trim().toLowerCase();

        // Complete the exact fx:id search before considering semantic aliases.
        // A recursive alias fallback can otherwise select an earlier "input" field
        // and prevent a later exact match from being reached.
        Node exact = findNodeByExactId(root, fieldKey);
        if (exact != null) return exact;

        // Fallback semantic alias search across scene graph.
        return findNodeByAlias(root, cleanKey);
    }

    private static Node findNodeByExactId(Node root, String fieldKey) {
        if (Objects.equals(root.getId(), fieldKey)) return root;
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findNodeByExactId(child, fieldKey);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Node findNodeByAlias(Node root, String alias) {
        if (root == null) return null;
        String id = root.getId() == null ? "" : root.getId().toLowerCase();

        if ((alias.contains("input") || alias.contains("data")) && (id.contains("input") || id.contains("data") || id.contains("message"))) {
            if (root instanceof TextInputControl) return root;
        }
        if (alias.contains("key") && (id.contains("key") || id.contains("priv") || id.contains("pub"))) {
            if (root instanceof TextInputControl) return root;
        }
        if ((alias.contains("iv") || alias.contains("nonce")) && (id.contains("iv") || id.contains("nonce"))) {
            if (root instanceof TextInputControl) return root;
        }
        if ((alias.contains("tag") || alias.contains("mac")) && (id.contains("tag") || id.contains("verify") || id.contains("mac"))) {
            if (root instanceof TextInputControl) return root;
        }
        if (alias.contains("cert") && (id.contains("cert") || id.contains("csr"))) {
            if (root instanceof TextInputControl) return root;
        }

        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findNodeByAlias(child, alias);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static String formatRedactedTechnicalDetails(UserFacingError error) {
        if (error == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[CryptoCarver Error Technical Report]\n");
        sb.append("Title: ").append(redactSecrets(error.title())).append("\n");
        if (error.detail() != null) {
            sb.append("Detail: ").append(redactSecrets(error.detail())).append("\n");
        }
        if (error.remedy() != null) {
            sb.append("Remedy: ").append(redactSecrets(error.remedy())).append("\n");
        }
        if (error.fieldKey() != null) {
            sb.append("Field: ").append(error.fieldKey()).append("\n");
        }
        if (error.cause() != null) {
            Throwable cause = error.cause();
            sb.append("Exception Class: ").append(cause.getClass().getName()).append("\n");
            if (cause.getMessage() != null) {
                sb.append("Exception Message: ").append(redactSecrets(cause.getMessage())).append("\n");
            }
            sb.append("Stack Trace:\n");
            StackTraceElement[] stack = cause.getStackTrace();
            int limit = Math.min(stack.length, 15);
            for (int i = 0; i < limit; i++) {
                sb.append("  at ").append(stack[i].toString()).append("\n");
            }
            if (stack.length > limit) {
                sb.append("  ... ").append(stack.length - limit).append(" more frames\n");
            }
        }
        return sb.toString();
    }

    public static String redactSecrets(String text) {
        if (text == null) return "";
        // Redact PEM blocks: -----BEGIN ... -----END ...
        String redacted = text.replaceAll("-----BEGIN[^-]+-----[\\s\\S]*?-----END[^-]+-----", "[REDACTED_PEM_BLOCK]");
        // Redact key/secret/iv/nonce/aad/password/pin/tag/token/payload parameter patterns
        redacted = redacted.replaceAll("(?i)(key|secret|secretkey|privatekey|publickey|iv|nonce|aad|password|passphrase|pass|pin|tag|token|payload|signature|cert|certificate|plaintext|ciphertext)[:=]\\s*\\S+", "$1=[REDACTED]");
        // Redact long Base64 blocks (40+ chars of A-Za-z0-9+/=)
        redacted = redacted.replaceAll("\\b[A-Za-z0-9+/]{40,}={0,2}\\b", "[REDACTED_BASE64]");
        // Redact long hexadecimal strings (32+ chars)
        redacted = redacted.replaceAll("\\b[0-9a-fA-F]{32,}\\b", "[REDACTED_HEX]");
        return redacted;
    }
}
