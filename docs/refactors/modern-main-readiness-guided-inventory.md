# Modern main readiness and guided flow inventory

## Scope and callers

`evaluatePreflightForOperation` is private and has one direct caller, `refreshReadinessPanelForOperation`. That refresh is reached by the public `updateReadinessPanelForOperation`, public `updateReadinessPanel`, route selection in `handleItemSelected`, and the `StatusReporter.checkPreflightReadiness` contract. Cipher, generic, and authentication module controllers call the public readiness methods after input changes or before running operations. The navigator and Command Palette reach `handleItemSelected` through the public `OperationNavigator` methods and `CommandRegistry` actions; they do not call the evaluator directly. Tests currently reach preflight through `checkPreflightReadiness` and verify the guided button handler reflectively.

`updateReadinessPanelUI` is private; its callers are `refreshReadinessPanelForOperation` and `handleToggleReadinessDetails` (FXML action). The FXML `readinessPanel`, badge, summary, checks container, and details button are display targets only. Its state is also used by `checkPreflightReadiness` to present an error and focus the first failing field.

`updateGuidedStepUI` is private. It is called by `startGuidedWorkflow`, `handleGuideNext`, `handleGuideBack`, and `handleGuideSkip`; the last three handlers are wired from the production FXML. The five start actions are FXML handlers for Encrypt, Hash, Sign, Certificate, and Convert. The Command Palette and navigation registry do not directly invoke the step renderer. `setupGuidedFlowKeyboardAndTooltips` binds Escape and Enter to the same FXML handlers.

## Inputs, state, and operation dependency

The evaluator reads module-controller fields by reflection: cipher input, algorithm, mode, padding, key source/key/HSM reference, IV, tag, AAD and RSA padding; generic hash input and algorithm; authentication input, signature algorithm and key/verification fields, or MAC algorithm/source/key/reference/verification fields. It also reads the root `inputFormatCombo` and simulated-HSM key metadata. It returns a `PreflightReport` from `OperationPreflightEngine`; it does not need JavaFX nodes beyond reading current controls.

The five readiness FXML fields are `readinessPanel`, `readinessStatusBadge`, `readinessSummaryLabel`, `readinessChecksContainer`, and `readinessToggleDetailsBtn`. Shared state is `currentPreflightReport`, `currentPreflightEncrypt`, `readinessShowDetails`, and `readinessPanelActivated`. `currentActiveOperation` is read only by the no-argument `updateReadinessPanel` and on route selection, where the selected route is passed to the refresh. Module input change callbacks use the current operation through that no-argument contract. An explicit `checkPreflightReadiness(operation, direction)` or `updateReadinessPanelForOperation(operation, direction)` does not otherwise depend on `currentActiveOperation`.

The guided FXML fields are `guidedFlowPanel`, `guideStepTitleLabel`, `guideStepDescLabel`, `guideBackBtn`, and `guideNextBtn`; step 1 additionally focuses `inputFormatCombo`. Shared state is `currentGuidedOp` and `currentGuidedStep`. Rendering depends on those two values, not on `currentActiveOperation`. Starting the flow selects a route as a separate action, then shows the panel. Step strings are hard-coded English in the renderer while keyboard tooltips are localized through `i18n`.

## Pure policy versus JavaFX rendering

The operation-to-report policy and report evaluation belong in JavaFX-free code: normalize the operation name, obtain explicit values from a readiness input snapshot, and call `OperationPreflightEngine`. Likewise, guided-step title and description are derivable from `(GuidedOperation, step)` and can be represented as a pure value. The coordinator should own activation/detail state, route-driven refresh, report localization, FXML node visibility/text/style/children, focus actions, and guided keyboard/focus behavior. Module controllers and the main controller must be supplied lazily so construction does not capture or cache them.
