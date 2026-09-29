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
