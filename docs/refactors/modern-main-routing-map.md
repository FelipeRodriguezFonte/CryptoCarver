# ModernMainController routing map (Encargo 34)

Base: `437e74a`. Inventory gathered with `rg` in `ModernMainController`, `UiNavigationRegistry`, the UI package and FXML.

## Entry points and callers

- `navigateTo` and `navigateToModule`: public contracts implementing `OperationNavigator` / module links. Called by `CommandRegistry`, `ClipboardShelfController`, `KeysController`, `CipherController`, `AuthenticationController`, `GenericController`, FXML navigation actions and UI tests. Both forward to `handleItemSelected`.
- `handleItemSelected`: FXML sidebar item selection, `CommandRegistry` actions, `OperationNavigator` callers, quick start actions, breadcrumb handlers, saved session restore, session trail, and navigation tests. It normalizes operation aliases, changes active operation, clears published result, updates chrome/inspector, activates route, or displays placeholder.
- `restoreStartupLastRoute`: called from controller initialization; reads `AppSettings.lastRoute` and calls `navigateToModule` only for a registered route.
- `activateNavigationRoute`: called only from `handleItemSelected`; dispatches every `UiNavigationRegistry.Module`.
- `hideAllContainers`: called by each module-specific `show*` method.

Relevant discovery commands: `rg -n 'navigateTo\\(|navigateToModule\\(|handleItemSelected\\(|activateNavigationRoute\\(|hideAllContainers\\(|restoreStartupLastRoute\\(' src`; `rg -n 'handleItemSelected|navigateToModule|navigateTo\\(' src/main/resources/fxml src/main/java/com/cryptocarver/ui`.

## FXML and shared state

The route logic touches module hosts and their `@FXML` controllers: `jose`, `cose`, `wallet`, `epochConverter`, `jsonFormatter`, `keysContainer`, `certificatesContainer`, `genericContainer`, `postQuantumContainer`, `xmlSecurityContainer`, `wssSecurityContainer`, `emvContainer`, `historyView`, `clipboardShelf`, saved-session host, `cipherContainer`, `authenticationContainer`, `paymentsContainer`, and `processDesignerContainer`. Some hosts/controllers are injected eagerly; others are loaded through `ensureModule(host, Controller.class)` on first access. Existing show methods cache controllers in `ModernMainController`; extraction must instead resolve host/controller providers for every router invocation, each provider calling `ensureModule`.

Shared routing state: `currentActiveOperation`; all module hosts/controllers; `ensureModule`; Process Designer exit/workspace behavior; preparation/readiness panel refresh. Route selection also clears the published result snapshot, updates navigation chrome/header/status and inspector.

## Generic vs module-specific behavior

Generic: hide all module hosts, reveal one host, and expand the target `TitledPane` by its section title. This includes `show*` host visibility and accordion selection for symmetric/asymmetric keys, certificates, generic, PQC, XML, WSS, EMV, cipher, authentication and payments.

Module-specific callbacks: Certificates selects ASN.1 encode/decode tabs; History focuses export actions and sets export guidance; Epoch switches its own converter UI; JSON selects/configures its formatter; Process Designer enters/exits its workspace and preparation behavior. JOSE/COSE/Wallet also call controller `showSection` and need small module callbacks rather than generic host handling.

## Dead format state audit

`rg -n 'rememberedInputFormats|rememberedOutputFormats|currentFormatProfileOperation' src/main/java/com/cryptocarver/ui/ModernMainController.java` finds declarations at lines 222–224 and writes at 1187–1189 only. No reads exist. `NavigationChromeCoordinator` maintains and reads its own independent remembered format maps.

## Characterization and extraction

`NavigationRouterCharacterizationTest` loads `/fxml/main-view-modern.fxml` through `Fxml.loader`. It covers all 20 registry modules, visible destination, expanded section (and the cipher workspace / Process Designer workspace variants), ASN.1 decode/encode, History export, clearing the published snapshot, dynamic `Hashing: SHA-256`, placeholder handling, and startup restore with valid and invalid routes. `lastRoute` is restored in `@AfterEach`. The route labels used are actual `UiNavigationRegistry` keys: `PQC Key Generation`, `XML Security`, and `WSS Security` (the unregistered prose labels were initially tested and correctly fell through to the placeholder).

The production FXML characterization passed both against the pre-extraction controller and after extraction. `NavigationRouter` owns route resolution, common host visibility, container hiding, and title-based generic expansion. `ModernMainController` retains the `OperationNavigator` and FXML-facing methods as delegates. A module host and controller are obtained by providers on each activation; controller resolution calls `ensureModule` each time, and the router stores only providers and module callbacks. Module callbacks retain ASN.1 tab selection, History export focus, Epoch/JSON setup, module initialization and pane behavior, clipboard refresh, and Process Designer workspace setup.

## Style snapshot verification

Snapshots were generated with `ComputedStyleSnapshotTool` before and after. The original capture pair differed in 135 diff lines, all under the History route: action-button disabled opacity and virtualized table header/placeholder nodes. This was attributable to persisted History state changing between captures, not the extraction. To isolate code from that state, I reran the final post-extraction snapshot and then ran the original controller against the same persisted state. The matched control diff is empty: `diff -u /tmp/modern-routing-after-final.txt /tmp/modern-routing-baseline-final.txt` produced no output (both 16,984 lines). No stylesheet or FXML changed.

## Call-site details from the `rg` inventory

- `OperationNavigator` is implemented by `ModernMainController`; `ClipboardShelfController` calls it to reopen the producing workspace. Module/quick actions use `navigateToModule` from `CommandRegistry` and `ModernMainController` menu handlers.
- `SidePanel` publishes leaf selection through `setOnItemSelected`; `ModernMainController.initialize` wires it to `handleItemSelected`. `NavigationController.navigate` synchronizes the rail/tree after external route selection without re-entering the route handler.
- ⌘K commands are in `CommandRegistry` and invoke `navigateToModule`; breadcrumbs call the module callback wired by `NavigationChromeCoordinator` and `selectBreadcrumbSection`.
- History re-run/restore wiring uses `HistoryCoordinator` and `ScreenConfigurationCoordinator` callbacks in `ModernMainController`; saved-session restore uses `SavedSessionsCoordinator` and the same item handler.
- FXML declares the navigation rail/side panel, while `ModernMainController` installs their callbacks in `initialize`; there is no direct FXML `onAction="#handleItemSelected"` binding. Tests call the public contracts and private legacy entry point through reflection; the new characterization uses only the production FXML loader plus those public contracts.

## Verification

- `mvn -o -q test -Dtest=NavigationRouterCharacterizationTest`: passed before extraction and after extraction.
- `mvn -o -q test`: 2,383 tests, 0 failures, 0 errors, 1 skipped.
- Manual navigation / relaunch is not covered by the automated FXML characterization.
