package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import java.util.List;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModernMainShellLocalizationCharacterizationTest {
  private ModernMainController controller;
  private Stage stage;
  private LanguagePreference previousLanguage;
  private String previousRoute;

  @BeforeEach
  void loadProductionFxmlInEnglish() throws Exception {
    startJavaFx();
    previousLanguage = AppSettings.getInstance().getLanguagePreference();
    previousRoute = AppSettings.getInstance().getLastRoute();
    runFx(
        () -> {
          I18nService.getInstance().setPreference(LanguagePreference.EN);
          AppSettings.getInstance().setLastRoute("Hashing");
          try {
            FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
            Parent root = loader.load();
            controller = loader.getController();
            stage = new Stage();
            stage.setTitle("CryptoCarver");
            Scene scene = new Scene(root, 1400, 900);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
          } catch (Exception exception) {
            throw new RuntimeException(exception);
          }
        });
  }

  @AfterEach
  void restoreLanguageAndCloseWindow() throws Exception {
    runFx(
        () -> {
          I18nService.getInstance().setPreference(previousLanguage);
          AppSettings.getInstance().setLastRoute(previousRoute);
          if (stage != null) {
            stage.close();
          }
        });
  }

  @Test
  void shellLabelsSwitchFromEnglishToSpanishAndBack() throws Exception {
    AtomicReference<Menu> fileMenu = new AtomicReference<>();
    AtomicReference<TextField> sideSearch = new AtomicReference<>();
    AtomicReference<Button> searchButton = new AtomicReference<>();
    runFx(
        () -> {
          fileMenu.set(field(controller, "fileMenu"));
          SidePanel sidePanel = field(controller, "sidePanel");
          sideSearch.set(field(sidePanel, "searchField"));
          searchButton.set(field(controller, "toolbarSearchButton"));
        });
    assertEquals("File", fileMenu.get().getText());
    assertEquals("Search", sideSearch.get().getPromptText());
    assertEquals("Search operations (⌘K)", searchButton.get().getText());
    assertEquals("CryptoCarver", stage.getTitle());

    runFx(() -> I18nService.getInstance().setPreference(LanguagePreference.ES));
    assertEquals("Archivo", fileMenu.get().getText());
    assertEquals("Buscar", sideSearch.get().getPromptText());
    assertEquals("Buscar operaciones (⌘K)", searchButton.get().getText());
    assertEquals("CryptoCarver", stage.getTitle());

    runFx(() -> I18nService.getInstance().setPreference(LanguagePreference.EN));
    assertEquals("File", fileMenu.get().getText());
    assertEquals("Search", sideSearch.get().getPromptText());
    assertEquals("Search operations (⌘K)", searchButton.get().getText());
    assertEquals("CryptoCarver", stage.getTitle());
  }

  @Test
  void laboratoryMenuLocalizesItsLabelAndQuickStartAction() throws Exception {
    AtomicReference<Menu> laboratory = new AtomicReference<>();
    runFx(
        () -> {
          MenuBar menuBar = field(controller, "mainMenuBar");
          laboratory.set(
              menuBar.getMenus().stream()
                  .filter(menu -> "laboratory".equals(menu.getUserData()))
                  .findFirst()
                  .orElseThrow());
          I18nService.getInstance().setPreference(LanguagePreference.ES);
        });
    assertEquals("Laboratorio", laboratory.get().getText());
    assertEquals("Inicio rápido", laboratory.get().getItems().get(0).getText());
  }

  @Test
  void laboratoryMenuListsEveryProfileOnceAndLocalizesTheirActions() throws Exception {
    AtomicReference<Menu> laboratory = new AtomicReference<>();
    runFx(
        () -> {
          MenuBar menuBar = field(controller, "mainMenuBar");
          laboratory.set(
              menuBar.getMenus().stream()
                  .filter(menu -> "laboratory".equals(menu.getUserData()))
                  .findFirst()
                  .orElseThrow());
          I18nService.getInstance().setPreference(LanguagePreference.ES);
        });
    List<Menu> profiles =
        laboratory.get().getItems().stream()
            .filter(item -> item instanceof Menu)
            .map(item -> (Menu) item)
            .toList();
    assertEquals(
        com.cryptocarver.model.payments.PaymentProfileManager.getAllProfiles().size(), profiles.size());
    assertEquals("Cargar datos", profiles.get(0).getItems().get(0).getText());
    assertEquals("Ejecutar y verificar", profiles.get(0).getItems().get(1).getText());
  }

  @Test
  void everyLaboratoryProfileLoadsIntoItsScreen() throws Exception {
    List<String> outcomes = new java.util.ArrayList<>();
    runFx(
        () -> {
          MenuBar menuBar = field(controller, "mainMenuBar");
          Menu laboratory =
              menuBar.getMenus().stream()
                  .filter(menu -> "laboratory".equals(menu.getUserData()))
                  .findFirst()
                  .orElseThrow();
          for (javafx.scene.control.MenuItem item : laboratory.getItems()) {
            if (item instanceof Menu profile) {
              try {
                profile.getItems().get(0).fire();
                outcomes.add(profile.getText() + " -> " + ((javafx.scene.control.Label) field(controller, "statusLabel")).getText());
              } catch (RuntimeException error) {
                outcomes.add(profile.getText() + " FAILED " + error);
              }
            }
          }
        });
    assertTrue(outcomes.stream().noneMatch(line -> line.contains(" FAILED ")), String.join("\n", outcomes));
  }

  @Test
  void focusedControlRegainsFocusAfterLocaleChange() throws Exception {
    AtomicReference<TextField> sideSearch = new AtomicReference<>();
    runFx(
        () -> {
          SidePanel sidePanel = field(controller, "sidePanel");
          sideSearch.set(field(sidePanel, "searchField"));
          sideSearch.get().requestFocus();
        });
    runFx(() -> I18nService.getInstance().setPreference(LanguagePreference.ES));
    runFx(() -> assertSame(sideSearch.get(), stage.getScene().getFocusOwner()));
  }

  @Test
  void loadedAndLazyModulesUseTheCurrentLanguage() throws Exception {
    AtomicReference<ModuleHost> cipherHost = new AtomicReference<>();
    runFx(
        () -> {
          cipherHost.set(field(controller, "cipherContainer"));
          assertNull(field(controller, "authenticationContainerController"));
          assertEquals("File", ((Menu) field(controller, "fileMenu")).getText());
          controller.navigateToModule("Symmetric Ciphers");
        });
    assertEquals(
        "Data Encryption & Decryption",
        findText(cipherHost.get().root(), "Data Encryption & Decryption"));

    runFx(() -> I18nService.getInstance().setPreference(LanguagePreference.ES));
    assertEquals(
        "Cifrado y descifrado de datos",
        findText(cipherHost.get().root(), "Cifrado y descifrado de datos"));
    AtomicReference<AuthenticationController> authenticationController = new AtomicReference<>();
    runFx(
        () -> {
          controller.navigateToModule("Digital Signatures");
          authenticationController.set(field(controller, "authenticationContainerController"));
        });
    assertEquals(
        "🔏 Firmas digitales",
        findPaneText(
            field(authenticationController.get(), "authenticationRoot"), "🔏 Firmas digitales"));
  }

  @Test
  void keyedErrorTitleIsReplacedWithLocalizedErrorText() throws Exception {
    AtomicReference<UserFacingError> localized = new AtomicReference<>();
    runFx(
        () -> {
          I18nService.getInstance().setPreference(LanguagePreference.ES);
          UserFacingError source =
              new UserFacingError(
                  "Authentication Tag Verification Failed",
                  "source detail",
                  "source remedy",
                  "tagField");
          localized.set(invokeLocalizedError(controller, source));
        });
    assertEquals("Error al verificar el tag de autenticación", localized.get().title());
    assertEquals(
        "El tag de autenticación no coincide con el ciphertext, la clave o los datos asociados"
            + " (AAD).",
        localized.get().detail());
    assertEquals(
        "Comprueba que la clave, el IV/nonce, el AAD y el tag coincidan con la operación de"
            + " cifrado.",
        localized.get().remedy());
    assertEquals("tagField", localized.get().fieldKey());
  }

  @Test
  void unrecognizedErrorIsPreservedWithoutTranslation() throws Exception {
    UserFacingError source =
        new UserFacingError("Unmapped failure", "private details", "retry", "field");
    AtomicReference<UserFacingError> localized = new AtomicReference<>();
    runFx(() -> localized.set(invokeLocalizedError(controller, source)));
    assertSame(source, localized.get());
  }

  @Test
  void sectionAndModuleLabelsTranslateKnownValuesAndPreserveUnknownValues() throws Exception {
    AtomicReference<String> section = new AtomicReference<>();
    AtomicReference<String> unknownSection = new AtomicReference<>();
    AtomicReference<String> module = new AtomicReference<>();
    AtomicReference<String> unknownModule = new AtomicReference<>();
    runFx(
        () -> {
          I18nService.getInstance().setPreference(LanguagePreference.ES);
          section.set(invokeTextResolver(controller, "localizedSectionText", "Symmetric Keys"));
          unknownSection.set(
              invokeTextResolver(controller, "localizedSectionText", "Unmapped section"));
          module.set(invokeTextResolver(controller, "localizedModuleText", "Symmetric"));
          unknownModule.set(
              invokeTextResolver(controller, "localizedModuleText", "Unmapped module"));
        });
    assertEquals("Claves simétricas", section.get());
    assertEquals("Unmapped section", unknownSection.get());
    assertEquals("Simétrico", module.get());
    assertEquals("Unmapped module", unknownModule.get());
  }

  private static String findText(Parent root, String expected) {
    for (Node child : root.getChildrenUnmodifiable()) {
      if (child instanceof Label label && expected.equals(label.getText())) {
        return label.getText();
      }
      if (child instanceof Parent parent) {
        try {
          return findText(parent, expected);
        } catch (IllegalArgumentException notFoundInChild) {
          // Continue searching the remaining module nodes.
        }
      }
    }
    throw new IllegalArgumentException("Label not found: " + expected);
  }

  private static String findPaneText(Parent root, String expected) {
    for (Node child : root.getChildrenUnmodifiable()) {
      if (child instanceof TitledPane pane && expected.equals(pane.getText())) {
        return pane.getText();
      }
      if (child instanceof Accordion accordion) {
        for (TitledPane pane : accordion.getPanes()) {
          if (expected.equals(pane.getText())) {
            return pane.getText();
          }
        }
      }
      if (child instanceof Parent parent) {
        try {
          return findPaneText(parent, expected);
        } catch (IllegalArgumentException notFoundInChild) {
          // Continue searching the remaining module nodes.
        }
      }
    }
    throw new IllegalArgumentException("Pane not found: " + expected);
  }

  private static UserFacingError invokeLocalizedError(
      ModernMainController target, UserFacingError error) {
    return invoke(target, "localizedError", new Class<?>[] {UserFacingError.class}, error);
  }

  private static String invokeTextResolver(
      ModernMainController target, String method, String value) {
    return invoke(target, method, new Class<?>[] {String.class}, value);
  }

  @SuppressWarnings("unchecked")
  private static <T> T invoke(
      Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) {
    try {
      var method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
      method.setAccessible(true);
      return (T) method.invoke(target, arguments);
    } catch (ReflectiveOperationException exception) {
      throw new RuntimeException(exception);
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

  private static void startJavaFx() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    try {
      Platform.startup(
          () -> {
            Platform.setImplicitExit(false);
            started.countDown();
          });
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
}
