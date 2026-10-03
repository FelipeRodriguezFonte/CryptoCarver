# Modern main history map

Scope: `ModernMainController`'s “State management for history Rerun” and “History Management” sections, including `reopenHistoryOperation` (the public history entry point immediately before this section) and `handleClearInput` (toolbar wiring below it).

## State and responsibilities

| Method | Live state read | Live state written / effects |
|---|---|---|
| `captureUIState` | All current module controls through `UiStateSnapshot.capture(this)`; visibility profile from `AppSettings` | None; returns a snapshot. |
| `captureHistoryState` | Current module controls and `AppSettings.getSecretVisibilityProfile()` through `UiStateSnapshot.captureHistoryRecipe(this)` | None; returns a privacy-filtered recipe. |
| `historyManager` | Controller's cached `historyManager` field | Lazily constructs and caches `HistoryManager` (persistent history). |
| `historyCoordinator` | Side panel, history view, formats, active operation, owner window, dialog and i18n services | Lazily constructs `HistoryCoordinator`; supplies callbacks for recipe capture, navigation, restoration, inspector, visible detail filtering, and status. |
| `initializeHistory` | Coordinator and its lazy dependencies | Gives the history store to the side panel, installs its selection listener, refreshes navigation and any loaded history view. |
| `refreshHistoryUI` / `refreshHistoryNavigation` | Side panel and optional history view | Refreshes their visible entries. |
| `addToHistory` overloads | `currentActiveOperation`; coordinator reads current recipe, formats, profile indirectly, and history store | Appends a history record, persists it, refreshes surfaces. The map overload treats its values as public detail. |
| `getHistoryManager` | History manager supplier | May initialize the persistent store. |
| `restoreOperationState` | Module navigation and module controllers through the coordinator callback | Navigates, restores recipe fields and updates active operation/status. |
| `restoreHistoryRecipe` | Navigation state, module controls, visibility/profile policy | Selects the saved module, restores safe recipe values, prompts for redacted values and schedules focus on the first visible redacted control. |
| `reopenHistoryOperation` | History item parameters/details and current visibility policy | Restores the item's route/recipe, updates inspector with filtered details and status. The history table's Reopen action reaches this through `HistoryController` → `OperationNavigator`. |
| `handleExportHistory` / `exportHistoryTo` | Current visibility profile for the chooser path; stored history | Chooses a destination or exports a profile-filtered JSON file. |
| `handleClearInput` | `currentActiveOperation`, currently visible module host/controller, localized status | Calls that module's clear action, clears the published result snapshot and updates status. Certificate input clearing is currently a no-op. |

History records are reopened through navigation operation names such as `Symmetric Ciphers`, `Hashing`, the selected payment operation, and `JWT (Signed)`. Navigation selects the receiving module before `UiStateSnapshot.restoreHistoryRecipe` maps recipe keys back to controller fields. The generic history table delegates the Reopen action through `OperationNavigator`; rerun means reopening the recorded recipe and invoking the module action again, not automatically executing an operation during history selection.

## Pure construction and live wiring

These controller methods do not build a JavaFX `View`. `captureUIState` and `captureHistoryState` are snapshot requests; their snapshot values depend on live controls and the current `AppSettings` privacy profile. `HistoryCommandPolicy.create` and `UiStateSnapshot` perform value construction/filtering without owning a controller, while `historyCoordinator()` is lazy wiring around live getters and callbacks. `historyManager()` is stateful lazy service construction. `reopenHistoryOperation` is an entry-point delegate; recipe restoration, inspector updates and status changes are live effects. `handleClearInput` is entirely live-state routing and cannot be moved into a pure view factory.

The appropriate extraction boundary is therefore a coordinator with a small immutable `View` of lazy getters/callbacks. Keep the JavaFX controls, active-operation ownership, navigation, inspector and `StatusReporter` access behind suppliers/callbacks; do not retain a controller. History detail values must be filtered at the point records are persisted as well as when they are presented, because a safe UI rendering alone does not protect the history JSON.
