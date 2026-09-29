package com.cryptocarver.ui;

import com.cryptocarver.model.GuidedStepPolicy;
import com.cryptocarver.model.PreflightCheck;
import com.cryptocarver.model.PreflightPolicy;
import com.cryptocarver.model.PreflightReport;
import com.cryptocarver.model.PreflightStatus;
import com.cryptocarver.service.I18nService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

/** Coordinates preflight state and JavaFX rendering for the main screen. */
public final class ReadinessPanelCoordinator {
  private final Supplier<String> activeOperation;
  private final Supplier<Object> cipherController;
  private final Supplier<Object> genericController;
  private final Supplier<Object> authenticationController;
  private final Supplier<ComboBox<String>> inputFormatCombo;
  private final Supplier<HBox> readinessPanel;
  private final Supplier<Label> readinessStatusBadge;
  private final Supplier<Label> readinessSummaryLabel;
  private final Supplier<FlowPane> readinessChecksContainer;
  private final Supplier<Button> readinessToggleDetailsButton;
  private final Supplier<HBox> guidedFlowPanel;
  private final Supplier<Label> guideStepTitleLabel;
  private final Supplier<Label> guideStepDescriptionLabel;
  private final Supplier<Button> guideBackButton;
  private final Supplier<Button> guideNextButton;
  private final Supplier<PreflightReport> currentReport;
  private final Consumer<PreflightReport> storeReport;
  private final Consumer<Boolean> storeEncryptDirection;
  private final Consumer<String> focusControl;
  private final Consumer<UserFacingError> showError;
  private final Consumer<String> navigateToOperation;
  private boolean showDetails;
  private boolean panelActivated;
  private boolean encryptDirection = true;
  private ModernMainController.GuidedOperation guidedOperation;
  private int guidedStep = 1;

  public ReadinessPanelCoordinator(
      Supplier<String> activeOperation,
      Supplier<Object> cipherController,
      Supplier<Object> genericController,
      Supplier<Object> authenticationController,
      Supplier<ComboBox<String>> inputFormatCombo,
      Supplier<HBox> readinessPanel,
      Supplier<Label> readinessStatusBadge,
      Supplier<Label> readinessSummaryLabel,
      Supplier<FlowPane> readinessChecksContainer,
      Supplier<Button> readinessToggleDetailsButton,
      Supplier<HBox> guidedFlowPanel,
      Supplier<Label> guideStepTitleLabel,
      Supplier<Label> guideStepDescriptionLabel,
      Supplier<Button> guideBackButton,
      Supplier<Button> guideNextButton,
      Supplier<PreflightReport> currentReport,
      Consumer<PreflightReport> storeReport,
      Consumer<Boolean> storeEncryptDirection,
      Consumer<String> focusControl,
      Consumer<UserFacingError> showError,
      Consumer<String> navigateToOperation) {
    this.activeOperation = Objects.requireNonNull(activeOperation);
    this.cipherController = Objects.requireNonNull(cipherController);
    this.genericController = Objects.requireNonNull(genericController);
    this.authenticationController = Objects.requireNonNull(authenticationController);
    this.inputFormatCombo = Objects.requireNonNull(inputFormatCombo);
    this.readinessPanel = Objects.requireNonNull(readinessPanel);
    this.readinessStatusBadge = Objects.requireNonNull(readinessStatusBadge);
    this.readinessSummaryLabel = Objects.requireNonNull(readinessSummaryLabel);
    this.readinessChecksContainer = Objects.requireNonNull(readinessChecksContainer);
    this.readinessToggleDetailsButton = Objects.requireNonNull(readinessToggleDetailsButton);
    this.guidedFlowPanel = Objects.requireNonNull(guidedFlowPanel);
    this.guideStepTitleLabel = Objects.requireNonNull(guideStepTitleLabel);
    this.guideStepDescriptionLabel = Objects.requireNonNull(guideStepDescriptionLabel);
    this.guideBackButton = Objects.requireNonNull(guideBackButton);
    this.guideNextButton = Objects.requireNonNull(guideNextButton);
    this.currentReport = Objects.requireNonNull(currentReport);
    this.storeReport = Objects.requireNonNull(storeReport);
    this.storeEncryptDirection = Objects.requireNonNull(storeEncryptDirection);
    this.focusControl = Objects.requireNonNull(focusControl);
    this.showError = Objects.requireNonNull(showError);
    this.navigateToOperation = Objects.requireNonNull(navigateToOperation);
  }

  public void onOperationSelected() {
    panelActivated = false;
    showDetails = false;
    refreshReadinessPanelForOperation(activeOperation.get(), encryptDirection);
  }

  public boolean checkPreflightReadiness(String operation, boolean isEncrypt) {
    updateReadinessPanelForOperation(operation, isEncrypt);
    PreflightReport report = currentReport.get();
    if (report == null || report.isExecutable()) {
      return true;
    }
    PreflightCheck firstIssue = report.getFirstNonReadyCheck();
    if (firstIssue != null && firstIssue.getTargetControlKey() != null) {
      focusControl.accept(firstIssue.getTargetControlKey());
    }
    HBox panel = readinessPanel.get();
    if (panel != null) {
      panel.setManaged(true);
      panel.setVisible(true);
    }
    I18nService i18n = I18nService.getInstance();
    String message =
        firstIssue == null
            ? i18n.text("preflight.remedy.generic")
            : localizedPreflightMessage(firstIssue);
    String fieldKey = firstIssue == null ? null : firstIssue.getTargetControlKey();
    showError.accept(
        new UserFacingError(
            i18n.text("preflight.title"), message, localizedPreflightRemedy(firstIssue), fieldKey));
    return false;
  }

  public void updateReadinessPanel() {
    refreshReadinessPanelForOperation(activeOperation.get(), encryptDirection);
  }

  public void updateReadinessPanelForOperation(String operation, boolean isEncrypt) {
    panelActivated = true;
    refreshReadinessPanelForOperation(operation, isEncrypt);
  }

  public void toggleReadinessDetails() {
    showDetails = !showDetails;
    Button toggle = readinessToggleDetailsButton.get();
    if (toggle != null) {
      toggle.setText(showDetails ? "Hide Details" : "Show Details");
    }
    updateReadinessPanelUI();
  }

  private void refreshReadinessPanelForOperation(String operation, boolean isEncrypt) {
    HBox panel = readinessPanel.get();
    if (panel == null) {
      return;
    }
    encryptDirection = isEncrypt;
    storeEncryptDirection.accept(isEncrypt);
    PreflightReport report = evaluatePreflightForOperation(operation, isEncrypt);
    storeReport.accept(report);
    if (report == null) {
      panel.setManaged(false);
      panel.setVisible(false);
      return;
    }
    if (report.isExecutable()) {
      panelActivated = false;
    }
    boolean visible = panelActivated && !report.isExecutable();
    panel.setManaged(visible);
    panel.setVisible(visible);
    if (visible) {
      updateReadinessPanelUI();
    }
  }

  public void updateReadinessPanelUI() {
    PreflightReport report = currentReport.get();
    Label badge = readinessStatusBadge.get();
    Label summary = readinessSummaryLabel.get();
    FlowPane checksContainer = readinessChecksContainer.get();
    if (report == null || badge == null || summary == null || checksContainer == null) {
      return;
    }
    setBadge(badge, report.getOverallStatus());
    summary.setText(localizedPreflightSummary(report));
    checksContainer.getChildren().clear();
    List<PreflightCheck> checks = report.getChecks();
    int maxVisible = showDetails ? checks.size() : Math.min(3, checks.size());
    for (int index = 0; index < maxVisible; index++) {
      PreflightCheck check = checks.get(index);
      Button checkButton =
          new Button(
              checkIcon(check.getStatus())
                  + check.getName()
                  + ": "
                  + localizedPreflightMessage(check));
      checkButton.getStyleClass().add("readiness-check-button");
      checkButton.setOnAction(
          event -> {
            if (check.getTargetControlKey() != null) {
              focusControl.accept(check.getTargetControlKey());
            }
          });
      checksContainer.getChildren().add(checkButton);
    }
  }

  private PreflightReport evaluatePreflightForOperation(String operation, boolean isEncrypt) {
    if (operation == null) {
      return null;
    }
    Object cipher = cipherController.get();
    Object generic = genericController.get();
    Object authentication = authenticationController.get();
    String normalizedOperation = com.cryptocarver.model.FormatProfilePolicy.operation(operation);
    String selectedInputFormat =
        inputFormatCombo.get() == null ? "Text (UTF-8)" : inputFormatCombo.get().getValue();
    PreflightPolicy.Inputs inputs =
        switch (normalizedOperation) {
          case "Symmetric Ciphers" -> symmetricInputs(cipher, selectedInputFormat);
          case "Hashing" -> hashingInputs(generic, selectedInputFormat);
          case "Digital Signatures" -> signatureInputs(authentication, isEncrypt);
          case "Message Authentication Codes" -> macInputs(authentication);
          case "Asymmetric Ciphers" -> asymmetricInputs(cipher, isEncrypt, selectedInputFormat);
          default -> null;
        };
    return inputs == null ? null : PreflightPolicy.evaluate(normalizedOperation, isEncrypt, inputs);
  }

  private PreflightPolicy.Inputs symmetricInputs(Object controller, String inputFormat) {
    String keyReference = comboValue(controller, "symHsmKeyCombo");
    String keySource = comboValue(controller, "symKeySourceCombo");
    return new PreflightPolicy.Inputs(
        fieldText(controller, "cipherInputArea"),
        inputFormat,
        comboValue(controller, "symmetricAlgorithmCombo"),
        comboValue(controller, "cipherModeCombo"),
        comboValue(controller, "paddingCombo"),
        keySource,
        fieldText(controller, "symmetricKeyField"),
        keyReference,
        isHsmKeyMetadataOnly(keyReference),
        fieldText(controller, "ivField"),
        fieldText(controller, "gcmTagField"),
        fieldText(controller, "aadField"),
        null);
  }

  private PreflightPolicy.Inputs hashingInputs(Object controller, String inputFormat) {
    return new PreflightPolicy.Inputs(
        fieldText(controller, "hashInputArea"),
        inputFormat,
        comboValue(controller, "hashAlgorithmCombo"),
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        null);
  }

  private PreflightPolicy.Inputs signatureInputs(Object controller, boolean isEncrypt) {
    String keyText =
        isEncrypt
            ? fieldText(controller, "signaturePrivateKeyArea")
            : fieldText(controller, "signaturePublicKeyArea");
    return new PreflightPolicy.Inputs(
        fieldText(controller, "authInputArea"),
        null,
        comboValue(controller, "signatureAlgorithmCombo"),
        null,
        null,
        null,
        keyText,
        null,
        false,
        null,
        null,
        null,
        fieldText(controller, "signatureVerifyField"));
  }

  private PreflightPolicy.Inputs macInputs(Object controller) {
    String keySource = comboValue(controller, "macKeySourceCombo");
    String keyReference = comboValue(controller, "macHsmKeyCombo");
    boolean metadataOnly =
        "Simulated HSM".equalsIgnoreCase(keySource) && isHsmKeyMetadataOnly(keyReference);
    return new PreflightPolicy.Inputs(
        fieldText(controller, "authInputArea"),
        null,
        comboValue(controller, "authMacAlgorithmCombo"),
        null,
        null,
        keySource,
        fieldText(controller, "authMacKeyField"),
        keyReference,
        metadataOnly,
        null,
        null,
        null,
        fieldText(controller, "authMacVerifyField"));
  }

  private PreflightPolicy.Inputs asymmetricInputs(
      Object controller, boolean isEncrypt, String inputFormat) {
    String keyText =
        isEncrypt
            ? fieldText(controller, "publicKeyArea")
            : fieldText(controller, "privateKeyArea");
    if ((keyText == null || keyText.isBlank())
        && controller instanceof CipherController cipher
        && cipher.hasAsymmetricKeyAvailable(isEncrypt)) {
      keyText = "[loaded key pair]";
    }
    return new PreflightPolicy.Inputs(
        fieldText(controller, "cipherInputArea"),
        inputFormat,
        null,
        null,
        comboValue(controller, "rsaPaddingCombo"),
        null,
        keyText,
        null,
        false,
        null,
        null,
        null,
        null);
  }

  private boolean isHsmKeyMetadataOnly(String keyId) {
    if (keyId == null || keyId.isEmpty()) {
      return false;
    }
    try {
      var metadata =
          com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(keyId);
      return metadata != null && !metadata.hasKeyMaterial();
    } catch (Exception ignored) {
      return false;
    }
  }

  private String fieldText(Object controller, String fieldName) {
    Object value = fieldValue(controller, fieldName);
    return value instanceof TextInputControl textInput ? textInput.getText() : "";
  }

  private String comboValue(Object controller, String fieldName) {
    Object value = fieldValue(controller, fieldName);
    if (!(value instanceof ComboBox<?> comboBox)) {
      return null;
    }
    Object selected = comboBox.getValue();
    return selected == null ? null : selected.toString();
  }

  private Object fieldValue(Object controller, String fieldName) {
    if (controller == null || fieldName == null) {
      return null;
    }
    try {
      var field = controller.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(controller);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private String localizedPreflightSummary(PreflightReport report) {
    long issues =
        report.getChecks().stream()
            .filter(check -> check.getStatus() != PreflightStatus.READY)
            .count();
    I18nService i18n = I18nService.getInstance();
    return switch (report.getOverallStatus()) {
      case READY -> i18n.text("preflight.summary.ready");
      case BLOCKED -> i18n.text("preflight.summary.blocked", issues);
      case INCOMPLETE -> i18n.text("preflight.summary.incomplete", issues);
      case WARNING -> i18n.text("preflight.summary.warning", issues);
    };
  }

  private String localizedPreflightMessage(PreflightCheck check) {
    I18nService i18n = I18nService.getInstance();
    if (check == null) {
      return i18n.text("preflight.remedy.generic");
    }
    String message = check.getMessage() == null ? "" : check.getMessage();
    String lower = message.toLowerCase(Locale.ROOT);
    String target =
        check.getTargetControlKey() == null
            ? ""
            : check.getTargetControlKey().toLowerCase(Locale.ROOT);
    if (lower.contains("empty") || lower.contains("required") || lower.contains("missing")) {
      if (target.contains("tag")) return i18n.text("preflight.tag.required");
      if (target.contains("signature")) return i18n.text("preflight.signature.required");
      if (target.contains("algorithm")) return i18n.text("preflight.algorithm.required");
      if (target.contains("mode")) return i18n.text("preflight.mode.required");
      if (target.contains("key")) return i18n.text("preflight.key.required");
      if (target.contains("iv") || target.contains("nonce"))
        return i18n.text("preflight.iv.required");
      return i18n.text("preflight.input.required");
    }
    if (lower.contains("non-hexadecimal") || lower.contains("invalid characters")) {
      if (target.contains("key")) return i18n.text("preflight.key.invalid");
      if (target.contains("iv") || target.contains("nonce"))
        return i18n.text("preflight.iv.invalid");
      if (target.contains("tag")) return i18n.text("preflight.tag.invalid");
      return i18n.text("preflight.input.hex.invalid");
    }
    if (lower.contains("odd number")) return i18n.text("preflight.input.hex.odd");
    if (lower.contains("base64")) return i18n.text("preflight.input.base64.invalid");
    return message;
  }

  private String localizedPreflightRemedy(PreflightCheck check) {
    I18nService i18n = I18nService.getInstance();
    if (check == null || check.getTargetControlKey() == null) {
      return i18n.text("preflight.remedy.generic");
    }
    String target = check.getTargetControlKey().toLowerCase(Locale.ROOT);
    if (target.contains("algorithm")) return i18n.text("preflight.remedy.algorithm");
    if (target.contains("mode")) return i18n.text("preflight.remedy.mode");
    if (target.contains("key")) return i18n.text("preflight.remedy.key");
    if (target.contains("iv") || target.contains("nonce")) return i18n.text("preflight.remedy.iv");
    if (target.contains("tag")) return i18n.text("preflight.remedy.tag");
    if (target.contains("input") || target.contains("data"))
      return i18n.text("preflight.remedy.input");
    return i18n.text("preflight.remedy.generic");
  }

  private void setBadge(Label badge, PreflightStatus status) {
    switch (status) {
      case READY -> setBadge(badge, "✔ READY", "readiness-status-ready");
      case WARNING -> setBadge(badge, "⚠️ WARNING", "readiness-status-warning");
      case INCOMPLETE -> setBadge(badge, "❓ INCOMPLETE", "readiness-status-incomplete");
      case BLOCKED -> setBadge(badge, "⛔ BLOCKED", "readiness-status-blocked");
    }
  }

  private void setBadge(Label badge, String text, String styleClass) {
    badge.setText(text);
    badge.getStyleClass().setAll(styleClass);
    badge.setStyle("");
  }

  private String checkIcon(PreflightStatus status) {
    return switch (status) {
      case READY -> "✔ ";
      case WARNING -> "⚠️ ";
      case INCOMPLETE -> "❓ ";
      case BLOCKED -> "⛔ ";
    };
  }

  public void startGuidedWorkflow(ModernMainController.GuidedOperation operation) {
    guidedOperation = operation;
    guidedStep = 1;
    String route =
        switch (operation) {
          case ENCRYPT -> "Symmetric Encryption";
          case HASH -> "Hashing";
          case SIGN -> "Digital Signatures";
          case CERT -> "Parse Certificate";
          case CONVERT -> "Manual Conversion";
        };
    navigateToOperation.accept(route);
    HBox panel = guidedFlowPanel.get();
    if (panel != null) {
      panel.setVisible(true);
      panel.setManaged(true);
      setupGuidedFlowKeyboardAndTooltips();
    }
    updateGuidedStepUI();
  }

  public void handleGuideNext() {
    if (guidedStep < 5) {
      guidedStep++;
      updateGuidedStepUI();
    }
  }

  public void handleGuideBack() {
    if (guidedStep > 1) {
      guidedStep--;
      updateGuidedStepUI();
    }
  }

  public void handleGuideSkip() {
    guidedStep = 4;
    updateGuidedStepUI();
  }

  public void handleGuideExit() {
    HBox panel = guidedFlowPanel.get();
    if (panel != null) {
      panel.setVisible(false);
      panel.setManaged(false);
    }
  }

  public void updateGuidedStepUI() {
    Label title = guideStepTitleLabel.get();
    Label description = guideStepDescriptionLabel.get();
    if (title == null || description == null || guidedOperation == null) {
      return;
    }
    Button back = guideBackButton.get();
    Button next = guideNextButton.get();
    if (back != null) back.setDisable(guidedStep <= 1);
    if (next != null) next.setDisable(guidedStep >= 5);
    GuidedStepPolicy.StepText stepText = GuidedStepPolicy.text(guidedOperation.name(), guidedStep);
    title.setText(stepText.title());
    description.setText(stepText.description());
    if (guidedStep == 1 && inputFormatCombo.get() != null) {
      inputFormatCombo.get().requestFocus();
    }
  }

  public void setupGuidedFlowKeyboardAndTooltips() {
    HBox panel = guidedFlowPanel.get();
    if (panel == null) {
      return;
    }
    Button back = guideBackButton.get();
    Button next = guideNextButton.get();
    I18nService i18n = I18nService.getInstance();
    if (back != null) back.setTooltip(new Tooltip(i18n.text("guide.backTooltip")));
    if (next != null) next.setTooltip(new Tooltip(i18n.text("guide.nextTooltip")));
    panel.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            handleGuideExit();
            event.consume();
          } else if (event.getCode() == KeyCode.ENTER && guidedStep < 4) {
            handleGuideNext();
            event.consume();
          }
        });
  }
}
