package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.PreflightReport;
import com.cryptocarver.service.I18nService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModernMainReadinessCharacterizationTest {
  private ModernMainController controller;
  private LanguagePreference previousLanguage;

  @BeforeEach
  void loadProductionFxml() throws Exception {
    startJavaFx();
    previousLanguage = AppSettings.getInstance().getLanguagePreference();
    runFx(
        () -> {
          try {
            FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
            loader.load();
            controller = loader.getController();
          } catch (Exception exception) {
            throw new RuntimeException(exception);
          }
        });
  }

  @AfterEach
  void restoreLanguagePreference() throws Exception {
    runFx(() -> I18nService.getInstance().setPreference(previousLanguage));
  }

  @Test
  void symmetricEncryptionPreflightHasExpectedLiteralMessages() throws Exception {
    runFx(
        () -> {
          controller.navigateToModule("Symmetric Ciphers");
          controller.updateReadinessPanelForOperation("Symmetric Ciphers", true);
        });
    PreflightReport report = readReport();
    assertEquals("INCOMPLETE", report.getOverallStatus().name());
    assertEquals(
        "Input payload is empty. Enter or paste data to process.",
        report.getChecks().get(0).getMessage());
  }

  @Test
  void symmetricDecryptionPreflightRequiresAuthenticationTag() throws Exception {
    runFx(
        () -> {
          controller.navigateToModule("Symmetric Ciphers");
          CipherController cipher = field(controller, "cipherContainerController");
          TextArea input = field(cipher, "cipherInputArea");
          ComboBox<String> mode = field(cipher, "cipherModeCombo");
          input.setText("ciphertext");
          mode.setValue("GCM");
          controller.updateReadinessPanelForOperation("Symmetric Ciphers", false);
        });
    PreflightReport report = readReport();
    assertTrue(
        report.getChecks().stream()
            .anyMatch(
                check ->
                    "AEAD decryption requires an Authentication Tag.".equals(check.getMessage())));
  }

  @Test
  void hashingPreflightHasExpectedLiteralMessage() throws Exception {
    runFx(
        () -> {
          controller.navigateToModule("Hashing");
          controller.updateReadinessPanelForOperation("Hashing", true);
        });
    PreflightReport report = readReport();
    assertEquals(
        "Input payload is empty. Enter text to hash.", report.getChecks().get(0).getMessage());
  }

  @Test
  void operationWithoutPreflightRequirementsReturnsNoReport() throws Exception {
    runFx(() -> controller.updateReadinessPanelForOperation("Manual Conversion", true));
    assertNull(readReport());
  }

  @Test
  void readinessPanelHidesAfterHashInputBecomesComplete() throws Exception {
    AtomicReference<HBox> panel = new AtomicReference<>();
    runFx(
        () -> {
          controller.navigateToModule("Hashing");
          controller.updateReadinessPanelForOperation("Hashing", true);
          panel.set(field(controller, "readinessPanel"));
        });
    assertTrue(panel.get().isVisible());
    runFx(
        () -> {
          GenericController generic = field(controller, "genericContainerController");
          TextArea input = field(generic, "hashInputArea");
          input.setText("characterization input");
          controller.updateReadinessPanel();
        });
    assertFalse(panel.get().isVisible());
  }

  @Test
  void guidedStepMovesForwardAndBackWithNavigationButtons() throws Exception {
    AtomicReference<Label> title = new AtomicReference<>();
    AtomicReference<Button> next = new AtomicReference<>();
    AtomicReference<Button> back = new AtomicReference<>();
    runFx(
        () -> {
          controller.startGuidedWorkflow(ModernMainController.GuidedOperation.HASH);
          title.set(field(controller, "guideStepTitleLabel"));
          next.set(field(controller, "guideNextBtn"));
          back.set(field(controller, "guideBackBtn"));
        });
    assertEquals("Step 1 of 5: Choose data/input format", title.get().getText());
    runFx(() -> next.get().fire());
    assertEquals(
        "Step 2 of 5: Choose algorithm & settings (or Start from a Template)",
        title.get().getText());
    runFx(() -> back.get().fire());
    assertEquals("Step 1 of 5: Choose data/input format", title.get().getText());
  }

  @Test
  void readinessSummaryUsesSpanishAndEnglishCatalogEntries() throws Exception {
    AtomicReference<Label> summary = new AtomicReference<>();
    runFx(
        () -> {
          controller.navigateToModule("Hashing");
          I18nService.getInstance().setPreference(LanguagePreference.ES);
          controller.updateReadinessPanelForOperation("Hashing", true);
          summary.set(field(controller, "readinessSummaryLabel"));
        });
    assertEquals(
        I18nService.getInstance().text("preflight.summary.incomplete", 1), summary.get().getText());
    runFx(() -> I18nService.getInstance().setPreference(LanguagePreference.EN));
    runFx(() -> controller.updateReadinessPanel());
    assertEquals(
        I18nService.getInstance().text("preflight.summary.incomplete", 1), summary.get().getText());
  }

  private PreflightReport readReport() throws Exception {
    AtomicReference<PreflightReport> report = new AtomicReference<>();
    runFx(() -> report.set(field(controller, "currentPreflightReport")));
    return report.get();
  }

  private static void startJavaFx() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    try {
      Platform.startup(started::countDown);
    } catch (IllegalStateException alreadyStarted) {
      started.countDown();
    }
    assertTrue(started.await(15, TimeUnit.SECONDS));
  }

  private static void runFx(Runnable action) throws Exception {
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(
        () -> {
          try {
            action.run();
          } catch (Throwable throwable) {
            failure.set(throwable);
          } finally {
            completed.countDown();
          }
        });
    assertTrue(completed.await(30, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object target, String name) {
    try {
      var field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return (T) field.get(target);
    } catch (ReflectiveOperationException exception) {
      throw new RuntimeException(exception);
    }
  }
}
