# Modern main shell localization inventory

## Call paths

- `applyLocalization` is public. `initialize` calls it after the root controls, navigation shell and module-host wiring exist. It also registers itself as the `I18nService` locale-change listener. `handleLanguageSystem`, `handleLanguageEs`, and `handleLanguageEn` update the service preference; a locale change calls `applyLocalization` on the FX thread. `Ux23AccessibilityLiveUITest` calls the public method directly.
- Preference/configuration import: repository search finds `PreferencesService` only as a model/service API and `ScreenConfigurationCoordinator.importScreenConfiguration` for operation-specific screen data. That import does not import language preference and does not call `applyLocalization`. No global preferences import path was found.
- `OperationNavigator` navigation changes route/header/breadcrumb text through `NavigationChromeCoordinator`; it does not call `applyLocalization`. `localizedSectionText` and `localizedModuleText` in `ModernMainController` have no callers. Their live equivalents are in `NavigationChromeCoordinator`.
- Other controllers use `I18nService` for their own localized content. The shell coordinator's provider for module controllers must remain lazy so a module loaded after a locale switch initializes in the current locale.
- `setText(MenuItem|Menu|Button|Label, key)` is called only by `applyLocalization` and its dynamic Laboratory-menu helper. `localizeLaboratoryMenu` is reached from `applyLocalization` after finding the menu tagged with `userData="laboratory"`.
- `restoreFocusAfterLocalization` has one caller, at the end of `applyLocalization`. `localizedError` has two callers: shell refresh of the currently visible error banner and `showError(UserFacingError)` before first presentation.

## FXML controls and shared state

`applyLocalization` paints the top-level menu bar, file/edit/view/security/tools/help/laboratory/language/appearance menus and their menu items; main toolbar buttons; input/output format controls; result actions/status; inspector and session-trail controls; error-banner labels and actions; guided-flow controls; progress controls; command-palette labels/search; navigation rail and side panel; breadcrumbs and favorite control; and the status bar. Most top-level controls are injected `@FXML` fields in `ModernMainController`; the navigation rail and side panel expose `refreshLocalizedText()` and own their internal nodes. The method also refreshes breadcrumb/favorite state, menu selections for language/theme, status fallback text, status visibility context, and an existing inline error.

The shared state read by painting is `i18n` preference/locale, theme preference, `currentActiveOperation`, the currently visible status text, the current inline error, and the active scene focus owner. Focus is saved from `mainPane.getScene()` before painting and restored asynchronously if the same node remains in a scene, visible, enabled, and focus traversable. `localizedError` preserves the original error object if no known title family matches; known tag, padding, key, hex, Base64, certificate/key-format, and TSA titles are mapped to localized title/detail/remedy while retaining field key and cause.

## Pure text resolution versus JavaFX painting

- Pure: `localizedSectionText`, `localizedModuleText`, and `localizedError` select translation keys or return the original text/error. `I18nService.text` supplies the actual translated strings.
- JavaFX painting: the `setText` overloads, `localizeLaboratoryMenu`, `restoreFocusAfterLocalization`, and the control mutations in `applyLocalization`. Breadcrumb/menu selection, accessibility text, tooltips, status refresh, and node focus are also presentation work.

## Shell text assigned outside `applyLocalization`

- Dynamic progress strings (`title`, formatted progress details, cancelling, finishing) reflect task state and can contain operation-specific detail; they are refreshed by the progress presenter, not the shell locale pass.
- Route and operation titles, subtitles, breadcrumbs, descriptors, and favorite stars are updated by `NavigationChromeCoordinator`; route names and cryptographic labels are stable identifiers or domain text.
- `Laboratory`, `Quick Start`, `Load Data`, and `Run and Verify` are initial English labels used while constructing the dynamic menu. The localization pass replaces them; profile submenu names are data (`type` and profile name) and remain as supplied.
- Invalid date/JSON messages and JSON/result contents are field or operation feedback, not static shell labels.
- Empty command search values are state resets, not display copy. FXML `text` attributes provide startup fallbacks until `initialize` applies the selected locale.
