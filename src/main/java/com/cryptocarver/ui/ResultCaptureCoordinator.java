package com.cryptocarver.ui;

import javafx.scene.control.TextArea;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** Resolves active result captures using live shell state and visibility providers. */
final class ResultCaptureCoordinator {
    private final Supplier<KeysController> keysController;
    private final Supplier<GenericController> genericContainerController;
    private final Supplier<ModuleHost> genericContainer;
    private final Supplier<javafx.scene.layout.BorderPane> mainPane;
    private final Supplier<ResultAreaTracker> resultAreaTracker;
    private final Supplier<com.cryptocarver.model.OperationResult> lastPublishedResultSnapshot;
    private final Supplier<String> lastPublishedScreen;
    private final Supplier<String> currentActiveOperation;
    private final Supplier<com.cryptocarver.model.SecretVisibilityProfile> visibility;
    private final Consumer<String> status;
    private final BiConsumer<String, String> info;
    private final BiConsumer<TextArea, String> secureShelf;

    ResultCaptureCoordinator(
            Supplier<KeysController> keysController,
            Supplier<GenericController> genericContainerController,
            Supplier<ModuleHost> genericContainer,
            Supplier<javafx.scene.layout.BorderPane> mainPane,
            Supplier<ResultAreaTracker> resultAreaTracker,
            Supplier<com.cryptocarver.model.OperationResult> lastPublishedResultSnapshot,
            Supplier<String> lastPublishedScreen,
            Supplier<String> currentActiveOperation,
            Supplier<com.cryptocarver.model.SecretVisibilityProfile> visibility,
            Consumer<String> status,
            BiConsumer<String, String> info,
            BiConsumer<TextArea, String> secureShelf) {
        this.keysController = keysController;
        this.genericContainerController = genericContainerController;
        this.genericContainer = genericContainer;
        this.mainPane = mainPane;
        this.resultAreaTracker = resultAreaTracker;
        this.lastPublishedResultSnapshot = lastPublishedResultSnapshot;
        this.lastPublishedScreen = lastPublishedScreen;
        this.currentActiveOperation = currentActiveOperation;
        this.visibility = visibility;
        this.status = status;
        this.info = info;
        this.secureShelf = secureShelf;
    }

    void handleAddCurrentOutputToShelf() {
        if (keysController.get() != null && isActiveAsymmetricKeyGeneration()) {
            keysController.get().handleGlobalAsymmetricShelfAction(currentActiveOperation.get());
            return;
        }
        // The generated symmetric key lives in a TextField the result tracker does not capture.
        if (keysController.get() != null && "Key Generation".equals(currentActiveOperation.get())) {
            keysController.get().handleGlobalSymmetricShelfAction();
            return;
        }
        // An explicitly focused/updated rendered result wins over a sibling
        // Workbench that happens to remain visible in the generic accordion.
        TextArea area = resultAreaTracker.get().shelfCaptureArea(null);
        if (area == null) {
            KeyCertificateWorkbenchController workbench = activeWorkbenchForShelf();
            if (workbench != null) {
                workbench.sendCurrentMaterialToShelf();
                return;
            }
            area = resultAreaTracker.get().shelfCaptureArea(mainPane.get());
        }
        String content = resolveShelfCaptureText(area);
        if (content == null || content.isBlank()) {
            status.accept(isShelfCaptureBlockedByVisibility(area)
                    ? "Action blocked: output hidden by visibility policy."
                    : "No current output available.");
            info.accept("No result available", "Run an operation with output before adding it to Clipboard Shelf.");
            return;
        }
        secureShelf.accept(area, null);
    }

    boolean isActiveAsymmetricKeyGeneration() {
        return switch (currentActiveOperation.get()) {
            case "RSA Key Generation", "ECDSA Key Generation", "DSA Key Generation", "EdDSA Key Generation" -> true;
            default -> false;
        };
    }

    KeyCertificateWorkbenchController activeWorkbenchForShelf() {
        if (!isContainerVisible(genericContainer.get()) || genericContainerController.get() == null) return null;
        KeyCertificateWorkbenchController workbench =
                genericContainerController.get().getKeyCertificateWorkbenchController();
        return workbench != null && workbench.isShelfMaterialViewVisible() ? workbench : null;
    }

    String resolveCurrentOutputText() {
        return resolveResultText(preferredResultArea());
    }

    TextArea preferredResultArea() {
        return resultAreaTracker.get().preferred(mainPane.get(), hasPublishedPayload());
    }

    boolean hasPublishedPayload() {
        if (lastPublishedResultSnapshot.get() == null) return false;
        if (lastPublishedResultSnapshot.get().getEnrichedOutput() != null
                && !lastPublishedResultSnapshot.get().getEnrichedOutput().isBlank()) return true;
        byte[] output = lastPublishedResultSnapshot.get().getOutput();
        return output != null && output.length > 0;
    }

    String resolveResultText(TextArea requestedArea) {
        if (ResultAreaTracker.isKeyPairResultArea(requestedArea) && resultAreaTracker.get().isRegistered(requestedArea)) {
            return renderResultArea(requestedArea);
        }
        if (lastPublishedResultSnapshot.get() != null) {
            return OperationResultRenderer.render(lastPublishedResultSnapshot.get(),
                    visibility.get());
        }
        if (resultAreaTracker.get().isRegistered(requestedArea) && !requestedArea.isEditable()) {
            String rendered = renderResultArea(requestedArea);
            if (rendered != null && !rendered.isBlank()) return rendered;
        }
        TextArea fallback = resultAreaTracker.get().findVisible(mainPane.get());
        if (fallback != null && resultAreaTracker.get().isRegistered(fallback) && !fallback.isEditable()) {
            String rendered = renderResultArea(fallback);
            if (rendered != null && !rendered.isBlank()) return rendered;
        }
        return "";
    }

    String resolveShelfCaptureText(TextArea requestedArea) {
        TextArea area = requestedArea;
        if (area == null) {
            area = resultAreaTracker.get().shelfCaptureArea(mainPane.get());
        }
        if (resultAreaTracker.get().isValidShelfCaptureArea(area)) {
            String visible = renderResultArea(area);
            if (visible == null || visible.isBlank()
                    || "***MASKED***".equals(visible)
                    || isPrivateMaterialPlaceholder(visible)) {
                return "";
            }
            return visible;
        }
        com.cryptocarver.model.OperationResult snapshot = shelfSnapshot();
        if (snapshot == null) return "";
        boolean hasArtifact = (snapshot.getEnrichedOutput() != null
                && !snapshot.getEnrichedOutput().isBlank())
                || (snapshot.getOutput() != null
                && snapshot.getOutput().length > 0);
        return hasArtifact ? OperationResultRenderer.render(snapshot,
                visibility.get()) : "";
    }

    com.cryptocarver.model.OperationResult shelfSnapshot() {
        return java.util.Objects.equals(lastPublishedScreen.get(), currentActiveOperation.get()) ? lastPublishedResultSnapshot.get() : null;
    }

    boolean isShelfCaptureBlockedByVisibility(TextArea area) {
        return com.cryptocarver.model.ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(
                classificationForResultArea(area), visibility.get());
    }

    boolean isPrivateMaterialPlaceholder(String text) {
        return com.cryptocarver.model.ResultPresentationPolicy.isPrivateMaterialPlaceholder(text);
    }

    String renderResultArea(TextArea area) {
        if (area == null || area.getText() == null || area.getText().isBlank()) {
            return "";
        }
        com.cryptocarver.model.OperationDetail.Classification classification = classificationForResultArea(area);
        com.cryptocarver.model.SecretVisibilityProfile profile =
                visibility.get();
        if (classification == com.cryptocarver.model.OperationDetail.Classification.SECRET) {
            if (profile == com.cryptocarver.model.SecretVisibilityProfile.REDACTED) return "";
            if (profile == com.cryptocarver.model.SecretVisibilityProfile.MASKED) return "***MASKED***";
        } else if (classification == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE
                && visibility.get() != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            return "***MASKED***";
        }
        return area.getText();
    }

    com.cryptocarver.model.OperationDetail.Classification classificationForResultArea(TextArea area) {
        // Private material stays protected even in a generic or mislabeled result control.
        if (area != null && containsPrivateMaterial(area.getText())) {
            return com.cryptocarver.model.OperationDetail.Classification.SECRET;
        }
        if (ResultAreaTracker.isPrivateKeyResultArea(area)) {
            return com.cryptocarver.model.OperationDetail.Classification.SECRET;
        }
        if (ResultAreaTracker.isKeyPairResultArea(area)
                && area.getId().toLowerCase(java.util.Locale.ROOT).contains("publickeyarea")) {
            return com.cryptocarver.model.OperationDetail.Classification.PUBLIC;
        }
        if (lastPublishedResultSnapshot.get() != null
                && resultAreaTracker.get().isCurrentResultArea(area, true)) {
            return com.cryptocarver.model.ResultPresentationPolicy.classifyPublishedResult(lastPublishedResultSnapshot.get());
        }
        if (area != null) {
            String id = area.getId() == null ? "" : area.getId().toLowerCase(java.util.Locale.ROOT);
            if (id.contains("privatekey") || id.contains("secret") || id.contains("kdf") || id.contains("pin")
                    || id.contains("pass") || id.contains("pwd") || id.contains("cvv") || id.contains("dukpt")
                    || id.contains("keywrap")) {
                return com.cryptocarver.model.OperationDetail.Classification.SECRET;
            }
            if (id.contains("key") || id.contains("mac") || id.contains("iv") || id.contains("cipher")) {
                return com.cryptocarver.model.OperationDetail.Classification.SENSITIVE;
            }
        }
        if (lastPublishedResultSnapshot.get() != null) {
            return com.cryptocarver.model.ResultPresentationPolicy.classifyPublishedResult(lastPublishedResultSnapshot.get());
        }
        return com.cryptocarver.model.OperationDetail.Classification.PUBLIC;
    }

    private boolean containsPrivateMaterial(String text) {
        if (text == null) return false;
        return isPrivateMaterialPlaceholder(text)
                || text.toUpperCase(java.util.Locale.ROOT).contains("PRIVATE KEY-----");
    }

    private boolean isContainerVisible(javafx.scene.Node container) {
        return container != null && container.isVisible();
    }
}
