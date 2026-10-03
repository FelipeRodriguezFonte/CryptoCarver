package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.ThemePreference;
import com.cryptocarver.service.I18nService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;

/** Paints the localized shell controls supplied by the main controller. */
final class ShellLocalizationCoordinator {
  private final I18nService i18n;
  private final ShellTextResolver textResolver;

  ShellLocalizationCoordinator(I18nService i18n) {
    this.i18n = Objects.requireNonNull(i18n);
    this.textResolver = new ShellTextResolver(i18n::text);
  }

  /** Lazy references remain valid across module/presenter replacement. */
  record LiveState(
      Supplier<StatusReporter> reporter,
      Supplier<NavigationRail> navigationRail,
      Supplier<SidePanel> sidePanel,
      Supplier<String> activeOperation,
      Supplier<Label> statusLabel,
      Supplier<StatusBarPresenter> statusPresenter,
      Supplier<InlineErrorPresenter> errorPresenter,
      Supplier<Node> errorRoot,
      Runnable refreshSessionTrail,
      Consumer<String> updateBreadcrumbs,
      Consumer<String> updateFavorite) {}

  void applyLocalization(View view, LiveState live) {
    view.themePreference = AppSettings.getInstance().getThemePreference();
    view.finishPainting(() -> finishPainting(live));
    applyLocalization(view);
  }

  private void finishPainting(LiveState live) {
    live.refreshSessionTrail().run();
    NavigationRail rail = live.navigationRail().get();
    if (rail != null) rail.refreshLocalizedText();
    SidePanel side = live.sidePanel().get();
    if (side != null) side.refreshLocalizedText();
    live.updateBreadcrumbs().accept(live.activeOperation().get());
    live.updateFavorite().accept(live.activeOperation().get());
    Label status = live.statusLabel().get();
    if (status != null && (status.getText() == null || status.getText().isBlank()
        || status.getText().equals("Ready") || status.getText().equals("Listo"))) {
      live.reporter().get().updateStatus(i18n.text("status.ready"));
    }
    StatusBarPresenter presenter = live.statusPresenter().get();
    if (presenter != null) presenter.refreshContext(AppSettings.getInstance().getSecretVisibilityProfile());
    InlineErrorPresenter errors = live.errorPresenter().get();
    if (errors != null && errors.getCurrentError() != null) {
      errors.showError(textResolver.localizedError(errors.getCurrentError()), live.errorRoot().get());
    }
  }

  /** Constructs fixed bindings only; state is consulted separately when painting. */
  static View createView(Controls controls, String commandShortcut, String favoriteShortcut) {
    View view = new View(controls.mainPane());
    view.text(controls.fileMenu(), "menu.file");
    view.text(controls.editMenu(), "menu.edit");
    view.text(controls.viewMenu(), "menu.view");
    view.text(controls.securityMenu(), "menu.security");
    view.text(controls.toolsMenu(), "menu.tools");
    view.text(controls.helpMenu(), "menu.help");
    view.text(controls.laboratoryMenu(), "menu.laboratory");
    view.text(controls.languageMenu(), "menu.language");
    view.text(controls.appearanceMenu(), "menu.appearance");
    view.text(controls.importKeyMenuItem(), "menu.importKey");
    view.text(controls.exportScreenMenuItem(), "menu.exportScreen");
    view.text(controls.importScreenMenuItem(), "menu.importScreen");
    view.text(controls.saveSessionMenuItem(), "menu.saveSession");
    view.text(controls.exportSessionTrailMenuItem(), "menu.exportSessionTrail");
    view.text(controls.exportHistoryMenuItem(), "menu.exportHistory");
    view.text(controls.exitMenuItem(), "menu.exit");
    view.text(controls.clearInputMenuItem(), "menu.clearInput");
    view.text(controls.clearOutputMenuItem(), "menu.clearOutput");
    view.text(controls.copyOutputMenuItem(), "menu.copyOutput");
    view.text(controls.addToShelfMenuItem(), "menu.addToShelf");
    view.text(controls.quickStartMenuItem(), "menu.quickStart");
    view.text(controls.clipboardShelfMenuItem(), "menu.clipboardShelf");
    view.text(controls.commandPaletteMenuItem(), "menu.commandPalette");
    view.text(controls.toggleSidePanelMenuItem(), "menu.toggleSidePanel");
    view.text(controls.toggleInspectorMenuItem(), "menu.toggleInspector");
    view.text(controls.expandResultMenuItem(), "menu.expandResult");
    view.text(controls.expandTableMenuItem(), "menu.expandTable");
    view.text(controls.zoomInMenuItem(), "menu.zoomIn");
    view.text(controls.zoomOutMenuItem(), "menu.zoomOut");
    view.text(controls.resetViewMenuItem(), "menu.resetView");
    view.text(controls.visibilityFullLabMenuItem(), "menu.visibility.full");
    view.text(controls.visibilityMaskedMenuItem(), "menu.visibility.masked");
    view.text(controls.visibilityRedactedMenuItem(), "menu.visibility.redacted");
    view.text(controls.epochMenuItem(), "menu.epoch");
    view.text(controls.jsonMenuItem(), "menu.json");
    view.text(controls.byteInspectorMenuItem(), "menu.byteInspector");
    view.text(controls.clearKeyCacheMenuItem(), "menu.clearKeyCache");
    view.text(controls.shortcutsMenuItem(), "menu.shortcuts");
    view.text(controls.diagnosticsMenuItem(), "menu.diagnostics");
    view.text(controls.aboutMenuItem(), "menu.about");
    view.text(controls.laboratoryQuickStartMenuItem(), "menu.quickStart");
    view.text(controls.languageSystemMenuItem(), "app.language.system");
    view.text(controls.languageEsMenuItem(), "app.language.es");
    view.text(controls.languageEnMenuItem(), "app.language.en");
    view.text(controls.themeSystemMenuItem(), "app.theme.system");
    view.text(controls.themeLightMenuItem(), "app.theme.light");
    view.text(controls.themeDarkMenuItem(), "app.theme.dark");
    view.text(controls.toolbarSaveSessionButton(), "menu.saveSession");
    view.text(controls.toolbarClearButton(), "toolbar.clear");
    view.text(controls.toolbarExpandButton(), "toolbar.expand");
    view.text(controls.toolbarShelfButton(), "toolbar.addShelf");
    view.text(controls.toolbarCopyButton(), "toolbar.copy");
    view.text(controls.inputFormatLabel(), "toolbar.payloadFormat");
    view.text(controls.outputFormatLabel(), "toolbar.output");
    view.text(controls.resultSaveStepButton(), "sessionTrail.saveStep");
    view.text(controls.inspectorSessionTrailTitle(), "sessionTrail.title");
    view.text(controls.inspectorAddSessionStepButton(), "sessionTrail.addCurrent");
    view.text(controls.inspectorExportSessionTrailButton(), "sessionTrail.exportAll");
    view.text(controls.inspectorClearSessionTrailButton(), "sessionTrail.clearShort");
    view.text(controls.inspectorOpenSessionStepButton(), "sessionTrail.viewData");
    view.text(controls.resultLastLabel(), "result.last");
    view.text(controls.resultAlgorithmStaticLabel(), "result.algorithm");
    view.text(controls.errorBannerTitle(), "error.failed");
    view.text(controls.errorBannerRemedy(), "error.remedy");
    view.text(controls.errorBannerGoToFieldBtn(), "error.goToField");
    view.text(controls.errorBannerCopyDetailsBtn(), "error.copyDetails");
    view.text(controls.guideBackBtn(), "guide.back");
    view.text(controls.guideNextBtn(), "guide.next");
    view.text(controls.guideSkipBtn(), "guide.skip");
    view.text(controls.guideExitBtn(), "guide.exit");
    view.text(controls.asyncProgressLabel(), "progress.working");
    view.text(controls.asyncCancelBtn(), "progress.cancel");
    view.text(controls.inspectorTitleLabel(), "inspector.title");
    view.text(controls.inspectorInputBytesTitle(), "inspector.inputBytes");
    view.text(controls.inspectorOutputBytesTitle(), "inspector.outputBytes");
    view.text(controls.inspectorAlgorithmTitle(), "inspector.algorithm");
    view.text(controls.inspectorSecurityTipsTitle(), "inspector.securityTips");
    view.text(controls.inspectorWarningTitle(), "inspector.warning");
    view.text(controls.inspectorHistoryTitle(), "inspector.history");
    view.text(controls.inspectorExportJsonButton(), "inspector.exportJson");
    view.text(controls.inspectorClearHistoryButton(), "toolbar.clear");
    view.text(controls.commandEscapeLabel(), "command.escape");
    view.text(controls.commandEmptyLabel(), "command.empty");
    view.text(controls.commandNavigateLabel(), "command.navigate");
    view.text(controls.commandSelectLabel(), "command.select");
    view.text(controls.commandCancelLabel(), "command.cancel");
    view.text(controls.commandTitleLabel(), "command.title");
    view.laboratoryMenu(controls.laboratoryMenu());
    view.languageItems(controls.languageSystemMenuItem(), controls.languageEsMenuItem(), controls.languageEnMenuItem());
    view.themeItems(controls.themeSystemMenuItem(), controls.themeLightMenuItem(), controls.themeDarkMenuItem(),
        null);
    view.toolbarSearch(controls.toolbarSearchButton(), commandShortcut);
    view.accessible(controls.toolbarSearchButton());
    view.accessible(controls.toolbarSaveSessionButton());
    view.accessible(controls.toolbarClearButton());
    view.accessible(controls.toolbarExpandButton());
    view.accessible(controls.toolbarShelfButton());
    view.accessible(controls.toolbarCopyButton());
    view.accessible(controls.asyncCancelBtn());
    view.accessible(controls.resultExpandButton(), "a11y.resultExpand");
    view.accessible(controls.resultShelfButton(), "a11y.resultShelf");
    view.accessible(controls.resultCopyButton(), "a11y.resultCopy");
    view.accessible(controls.resultSaveStepButton(), "a11y.sessionTrailSaveStep");
    view.accessible(controls.inspectorAddSessionStepButton(), "a11y.sessionTrailSaveStep");
    view.accessible(controls.inspectorPreviousSessionStepButton(), "sessionTrail.previousStep");
    view.accessible(controls.inspectorNextSessionStepButton(), "sessionTrail.nextStep");
    view.accessible(controls.inspectorOpenSessionStepButton(), "sessionTrail.viewData");
    view.accessible(controls.inspectorToggleButton(), "a11y.inspectorToggle");
    view.accessible(controls.errorBannerCloseBtn(), "a11y.errorClose");
    view.accessible(controls.inspectorExportSessionTrailButton(), "sessionTrail.exportTitle");
    view.accessible(controls.inspectorClearSessionTrailButton(), "sessionTrail.clear");
    view.accessible(controls.inputFormatCombo(), "a11y.payloadFormat");
    view.accessible(controls.outputFormatCombo(), "a11y.outputFormat");
    view.accessible(controls.commandSearchField(), "a11y.commandSearch");
    view.accessible(controls.favoriteToggleBtn(), "a11y.favorite");
    view.accessible(controls.resultStatusBadge(), "result.status");
    view.accessible(controls.errorBannerGoToFieldBtn(), "a11y.errorGoToField");
    view.accessible(controls.errorBannerCopyDetailsBtn(), "a11y.errorCopyDetails");
    view.accessibleHelp(controls.inputFormatCombo(), "toolbar.payloadTooltip");
    view.accessibleHelp(controls.outputFormatCombo(), "a11y.outputFormat");
    view.accessibleHelp(controls.commandSearchField(), "command.prompt");
    view.accessibleHelp(controls.errorBannerTitle(), "a11y.errorTitle");
    view.accessibleHelp(controls.errorBannerRemedy(), "a11y.errorRemedy");
    view.accessibleHelp(controls.errorBannerGoToFieldBtn(), "a11y.errorGoToFieldHelp");
    view.accessibleHelp(controls.errorBannerCopyDetailsBtn(), "a11y.errorCopyDetailsHelp");
    view.accessibleHelp(controls.errorBannerCloseBtn(), "a11y.errorCloseHelp");
    view.accessibleHelp(controls.favoriteToggleBtn(), "favorite.tooltip", favoriteShortcut);
    view.tooltip(controls.inputFormatLabel(), "toolbar.payloadTooltip");
    view.tooltip(controls.inputFormatCombo(), "toolbar.payloadTooltip");
    view.tooltip(controls.inspectorExportJsonButton(), "inspector.exportJsonTooltip");
    view.tooltip(controls.inspectorClearSessionTrailButton(), "sessionTrail.clear");
    view.prompt(controls.commandSearchField(), "command.prompt");
    return view;
  }

  /** FXML references only: no shell controller or live operation state. */
  record Controls(
      Menu fileMenu,
      Menu editMenu,
      Menu viewMenu,
      Menu securityMenu,
      Menu toolsMenu,
      Menu helpMenu,
      Menu laboratoryMenu,
      Menu languageMenu,
      Menu appearanceMenu,
      MenuItem importKeyMenuItem,
      MenuItem exportScreenMenuItem,
      MenuItem importScreenMenuItem,
      MenuItem saveSessionMenuItem,
      MenuItem exportSessionTrailMenuItem,
      MenuItem exportHistoryMenuItem,
      MenuItem exitMenuItem,
      MenuItem clearInputMenuItem,
      MenuItem clearOutputMenuItem,
      MenuItem copyOutputMenuItem,
      MenuItem addToShelfMenuItem,
      MenuItem quickStartMenuItem,
      MenuItem clipboardShelfMenuItem,
      MenuItem commandPaletteMenuItem,
      MenuItem toggleSidePanelMenuItem,
      MenuItem toggleInspectorMenuItem,
      MenuItem expandResultMenuItem,
      MenuItem expandTableMenuItem,
      MenuItem zoomInMenuItem,
      MenuItem zoomOutMenuItem,
      MenuItem resetViewMenuItem,
      RadioMenuItem visibilityFullLabMenuItem,
      RadioMenuItem visibilityMaskedMenuItem,
      RadioMenuItem visibilityRedactedMenuItem,
      MenuItem epochMenuItem,
      MenuItem jsonMenuItem,
      MenuItem byteInspectorMenuItem,
      MenuItem clearKeyCacheMenuItem,
      MenuItem shortcutsMenuItem,
      MenuItem diagnosticsMenuItem,
      MenuItem aboutMenuItem,
      MenuItem laboratoryQuickStartMenuItem,
      RadioMenuItem languageSystemMenuItem,
      RadioMenuItem languageEsMenuItem,
      RadioMenuItem languageEnMenuItem,
      RadioMenuItem themeSystemMenuItem,
      RadioMenuItem themeLightMenuItem,
      RadioMenuItem themeDarkMenuItem,
      Button toolbarSaveSessionButton,
      Button toolbarClearButton,
      Button toolbarExpandButton,
      Button toolbarShelfButton,
      Button toolbarCopyButton,
      Label inputFormatLabel,
      Label outputFormatLabel,
      Button resultSaveStepButton,
      Label inspectorSessionTrailTitle,
      Button inspectorAddSessionStepButton,
      Button inspectorExportSessionTrailButton,
      Button inspectorClearSessionTrailButton,
      Button inspectorOpenSessionStepButton,
      Label resultLastLabel,
      Label resultAlgorithmStaticLabel,
      Label errorBannerTitle,
      Label errorBannerRemedy,
      Button errorBannerGoToFieldBtn,
      Button errorBannerCopyDetailsBtn,
      Button guideBackBtn,
      Button guideNextBtn,
      Button guideSkipBtn,
      Button guideExitBtn,
      Label asyncProgressLabel,
      Button asyncCancelBtn,
      Label inspectorTitleLabel,
      Label inspectorInputBytesTitle,
      Label inspectorOutputBytesTitle,
      Label inspectorAlgorithmTitle,
      Label inspectorSecurityTipsTitle,
      Label inspectorWarningTitle,
      Label inspectorHistoryTitle,
      Button inspectorExportJsonButton,
      Button inspectorClearHistoryButton,
      Label commandEscapeLabel,
      Label commandEmptyLabel,
      Label commandNavigateLabel,
      Label commandSelectLabel,
      Label commandCancelLabel,
      Label commandTitleLabel,
      Button toolbarSearchButton,
      Button resultExpandButton,
      Button resultShelfButton,
      Button resultCopyButton,
      Button inspectorPreviousSessionStepButton,
      Button inspectorNextSessionStepButton,
      Button inspectorToggleButton,
      Button errorBannerCloseBtn,
      ComboBox<String> inputFormatCombo,
      ComboBox<String> outputFormatCombo,
      TextField commandSearchField,
      Button favoriteToggleBtn,
      Label resultStatusBadge,
      BorderPane mainPane) {}

  void applyLocalization(View view) {
    Node focusOwner = view.root() != null && view.root().getScene() != null
        ? view.root().getScene().getFocusOwner()
        : null;
    for (TextBinding binding : view.textBindings()) {
      setText(binding.control(), binding.key());
    }
    if (view.laboratoryMenu() != null) {
      localizeLaboratoryMenu(view.laboratoryMenu());
    }
    select(view.languageSystem(), i18n.getPreference() == LanguagePreference.SYSTEM);
    select(view.languageSpanish(), i18n.getPreference() == LanguagePreference.ES);
    select(view.languageEnglish(), i18n.getPreference() == LanguagePreference.EN);
    select(view.themeSystem(), view.themePreference() == ThemePreference.SYSTEM);
    select(view.themeLight(), view.themePreference() == ThemePreference.LIGHT);
    select(view.themeDark(), view.themePreference() == ThemePreference.DARK);
    if (view.toolbarSearchButton() != null) {
      view.toolbarSearchButton().setText(i18n.text("toolbar.search", view.commandPaletteShortcut()));
    }
    for (ButtonBase control : view.accessibleTextControls()) {
      setAccessibleText(control);
    }
    for (AccessibleTextBinding binding : view.accessibleTextBindings()) {
      if (binding.key() == null && binding.control() instanceof ButtonBase button) {
        setAccessibleText(button);
      } else if (binding.control() != null && binding.key() != null) {
        binding.control().setAccessibleText(i18n.text(binding.key()));
      }
    }
    for (NodeTextBinding binding : view.accessibleHelpBindings()) {
      if (binding.control() != null) {
        binding.control().setAccessibleHelp(i18n.text(binding.key(), binding.arguments()));
      }
    }
    for (TooltipBinding binding : view.tooltipBindings()) {
      if (binding.control() != null) {
        binding.control().setTooltip(new Tooltip(i18n.text(binding.key(), binding.arguments())));
      }
    }
    for (PromptBinding binding : view.promptBindings()) {
      if (binding.control() != null) {
        binding.control().setPromptText(i18n.text(binding.key(), binding.arguments()));
      }
    }
    view.finishPainting().run();
    restoreFocusAfterLocalization(focusOwner);
  }

  void setLanguage(LanguagePreference preference) {
    i18n.setPreference(preference);
  }

  void setText(MenuItem item, String key) {
    if (item != null) {
      item.setText(i18n.text(key));
    }
  }

  void setText(Menu menu, String key) {
    if (menu != null) {
      menu.setText(i18n.text(key));
    }
  }

  void setText(Button button, String key) {
    if (button != null) {
      button.setText(i18n.text(key));
    }
  }

  void setText(Label label, String key) {
    if (label != null) {
      label.setText(i18n.text(key));
    }
  }

  void localizeLaboratoryMenu(Menu menu) {
    menu.setText(i18n.text("menu.laboratory"));
    if (!menu.getItems().isEmpty()) {
      setText(menu.getItems().get(0), "menu.quickStart");
    }
    for (MenuItem item : menu.getItems()) {
      if (item instanceof Menu profile) {
        for (MenuItem profileItem : profile.getItems()) {
          if ("Load Data".equals(profileItem.getText()) || "Cargar datos".equals(profileItem.getText())) {
            setText(profileItem, "menu.loadData");
          } else if ("Run and Verify".equals(profileItem.getText())
              || "Ejecutar y verificar".equals(profileItem.getText())) {
            setText(profileItem, "menu.runVerify");
          }
        }
      }
    }
  }

  void setAccessibleText(ButtonBase control) {
    if (control != null && control.getText() != null) {
      control.setAccessibleText(control.getText());
    }
  }

  void setAccessibleText(ButtonBase control, String key) {
    if (control != null && key != null) {
      control.setAccessibleText(i18n.text(key));
    }
  }

  void restoreFocusAfterLocalization(Node focusOwner) {
    if (focusOwner == null) {
      return;
    }
    Platform.runLater(
        () -> {
          if (focusOwner.getScene() != null
              && focusOwner.isVisible()
              && !focusOwner.isDisabled()
              && focusOwner.isFocusTraversable()) {
            focusOwner.requestFocus();
          }
        });
  }

  private void setText(Object control, String key) {
    if (control instanceof MenuItem menuItem) {
      setText(menuItem, key);
    } else if (control instanceof Menu menu) {
      setText(menu, key);
    } else if (control instanceof Button button) {
      setText(button, key);
    } else if (control instanceof Label label) {
      setText(label, key);
    }
  }

  private static void select(RadioMenuItem item, boolean selected) {
    if (item != null) {
      item.setSelected(selected);
    }
  }

  private record TextBinding(Object control, String key) {}

  private record AccessibleTextBinding(Node control, String key) {}

  private record TooltipBinding(Control control, String key, Object[] arguments) {}

  private record NodeTextBinding(Node control, String key, Object[] arguments) {}

  private record PromptBinding(TextField control, String key, Object[] arguments) {}

  void updateNodeFonts(javafx.scene.Node node, int fontSize) {
      if (node == null)
          return;

      if (node instanceof TextArea) {
          ((TextArea) node).setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: " + fontSize + "px;");
      } else if (node instanceof TextField) {
          ((TextField) node).setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: " + fontSize + "px;");
      }

      // Recursive traversal
      if (node instanceof ScrollPane) {
          updateNodeFonts(((ScrollPane) node).getContent(), fontSize);
      } else if (node instanceof TitledPane) {
          updateNodeFonts(((TitledPane) node).getContent(), fontSize);
      } else if (node instanceof Accordion) {
          for (TitledPane pane : ((Accordion) node).getPanes()) {
              updateNodeFonts(pane, fontSize);
          }
      } else if (node instanceof SplitPane) {
          for (javafx.scene.Node child : ((SplitPane) node).getItems()) {
              updateNodeFonts(child, fontSize);
          }
      } else if (node instanceof javafx.scene.Parent) {
          for (javafx.scene.Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
              updateNodeFonts(child, fontSize);
          }
      }
  }


  static final class View {
    private final Node root;
    private final List<TextBinding> textBindings = new ArrayList<>();
    private final List<ButtonBase> accessibleTextControls = new ArrayList<>();
    private final List<AccessibleTextBinding> accessibleTextBindings = new ArrayList<>();
    private final List<NodeTextBinding> accessibleHelpBindings = new ArrayList<>();
    private final List<PromptBinding> promptBindings = new ArrayList<>();
    private final List<TooltipBinding> tooltipBindings = new ArrayList<>();
    private Menu laboratoryMenu;
    private RadioMenuItem languageSystem;
    private RadioMenuItem languageSpanish;
    private RadioMenuItem languageEnglish;
    private RadioMenuItem themeSystem;
    private RadioMenuItem themeLight;
    private RadioMenuItem themeDark;
    private ThemePreference themePreference;
    private Button toolbarSearchButton;
    private String commandPaletteShortcut;
    private Runnable finishPainting = () -> {};

    View(Node root) {
      this.root = root;
    }

    View text(Object control, String key) {
      textBindings.add(new TextBinding(control, key));
      return this;
    }

    View accessible(ButtonBase control) {
      accessibleTextControls.add(control);
      return this;
    }

    View accessible(Node control, String key) {
      accessibleTextBindings.add(new AccessibleTextBinding(control, key));
      return this;
    }

    View tooltip(Control control, String key, Object... arguments) {
      tooltipBindings.add(new TooltipBinding(control, key, arguments));
      return this;
    }

    View accessibleHelp(Node control, String key, Object... arguments) {
      accessibleHelpBindings.add(new NodeTextBinding(control, key, arguments));
      return this;
    }

    View prompt(TextField control, String key, Object... arguments) {
      promptBindings.add(new PromptBinding(control, key, arguments));
      return this;
    }

    Node root() { return root; }
    List<TextBinding> textBindings() { return textBindings; }
    List<ButtonBase> accessibleTextControls() { return accessibleTextControls; }
    List<AccessibleTextBinding> accessibleTextBindings() { return accessibleTextBindings; }
    List<NodeTextBinding> accessibleHelpBindings() { return accessibleHelpBindings; }
    List<PromptBinding> promptBindings() { return promptBindings; }
    List<TooltipBinding> tooltipBindings() { return tooltipBindings; }
    Menu laboratoryMenu() { return laboratoryMenu; }
    RadioMenuItem languageSystem() { return languageSystem; }
    RadioMenuItem languageSpanish() { return languageSpanish; }
    RadioMenuItem languageEnglish() { return languageEnglish; }
    RadioMenuItem themeSystem() { return themeSystem; }
    RadioMenuItem themeLight() { return themeLight; }
    RadioMenuItem themeDark() { return themeDark; }
    ThemePreference themePreference() { return themePreference; }
    Button toolbarSearchButton() { return toolbarSearchButton; }
    String commandPaletteShortcut() { return commandPaletteShortcut; }
    Runnable finishPainting() { return finishPainting; }

    View laboratoryMenu(Menu value) { laboratoryMenu = value; return this; }
    View languageItems(RadioMenuItem system, RadioMenuItem spanish, RadioMenuItem english) {
      languageSystem = system;
      languageSpanish = spanish;
      languageEnglish = english;
      return this;
    }
    View themeItems(RadioMenuItem system, RadioMenuItem light, RadioMenuItem dark, ThemePreference preference) {
      themeSystem = system;
      themeLight = light;
      themeDark = dark;
      themePreference = preference;
      return this;
    }
    View toolbarSearch(Button button, String shortcut) {
      toolbarSearchButton = button;
      commandPaletteShortcut = shortcut;
      return this;
    }
    View finishPainting(Runnable value) { finishPainting = value; return this; }
  }
}
