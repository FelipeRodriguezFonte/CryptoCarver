package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.ThemePreference;
import com.cryptocarver.service.I18nService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;

/** Paints the localized shell controls supplied by the main controller. */
final class ShellLocalizationCoordinator {
  private final I18nService i18n;

  ShellLocalizationCoordinator(I18nService i18n) {
    this.i18n = Objects.requireNonNull(i18n);
  }

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
