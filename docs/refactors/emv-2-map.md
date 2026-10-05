# EMV refactor 2 — ARQC

Base branch: `codex/emv-1` after phase 1. The phase 1 gate commit is `96cfb67`; its parent chain starts at `main` commit `f31bb8917d6a3f599c14f211da0981a5b476e101`. `EMVController.java` has 1352 lines before phase 2.

The requested original method spans were checked by matching braces in `main` at `f31bb89`: `handleGenerateARQC` L715–L846 (132 lines), and `handleVerifyARQC` L848–L932 (85 lines). After phase 1, their current spans are `handleGenerateARQC` L622–L753 (132 lines) and `handleVerifyARQC` L755–L839 (85 lines).

| Method | Current lines | State read | State written | Pure EMV logic vs UI wiring |
|---|---:|---|---|---|
| `handleGenerateARQC` | 622–753 (132) | `skARQCField`; optional `arqcTerminalDataField`; amount, amount-other, currency, country, `atcARQCField`, TVR, date, type, UN; optional ICC data; padding combo; localized messages; `StatusReporter` | `arqcResultArea`; private `lastArqcTransactionData`, `lastArqcPaddingMethod`, `lastArqcValue`; published output/details/history/status through reporter | Field parsing/defaults, report formatting, visibility and publication are UI wiring. `EMVOperations.buildARQCData` and `generateARQC` perform data validation and cryptography in `crypto/` (out of scope). |
| `handleVerifyARQC` | 755–839 (85) | session-key field; `amountField`; ARQC parsed from `arqcResultArea`; cached generated input/padding/value; fallback transaction fields including UN and optional ICC data; padding combo; localized messages; optional reporter | replaces `arqcResultArea`; publishes a verification report with validity/padding and localized status; does not change cached generated state | Parsing the generated report, fallback defaults, result formatting and publication are UI wiring. `EMVOperations.verifyARQC` validates and computes in `crypto/` (out of scope). |

## State and extraction boundary

The coordinator can take a `record View` of lazy suppliers for the fields, cached ARQC state, and result area, plus `Supplier<StatusReporter>`. It must not retain `EMVController`. The two FXML methods stay public one-line delegates; no public controller API changes. The verification cache belongs with ARQC generation/verification because it binds the generated cryptogram to its exact transaction data and padding selection. Profile data and the `loadProfile` menu remain phase 3.

All cryptographic/data validation logic is in `crypto/EMVOperations`: `buildARQCData`, `generateARQC`, and `verifyARQC`. Do not edit `crypto/`. The UI coordinator should preserve the generated report and default behavior, aside from a characterized/fixed localization or result-classification defect.

`AppSettings` is not read in these handlers. The shell classifies published results and applies visibility policy to the result viewer, inspector, history, Shelf, expanded viewer and status surfaces. `handleGenerateARQC` currently publishes raw ARQC bytes with the default `PUBLIC` output classification; `handleVerifyARQC` publishes a report containing both the entered session key and ARQC, also defaulting to `PUBLIC`. This predicts a MASKED/REDACTED leak through shared result surfaces and is recorded before digest-pinning. The error catch paths forward English `EMVOperations` details through generic localized templates; invalid UN/key-length feedback may therefore fail locale-specific characterization. No fixes have been made for these phase 2 findings yet.

## Existing tests and vector provenance

`PaymentControlValuesTest.emvArqcAndArpcMethod1` contains the repository's cross-checked EMV Book 2 A1.4.1/A1.3 house-key vector: method 1 ARQC `E8499E593250A030` and method 2 ARQC `A8DB2B65F9C821F1`. The repository marks these as cross-checked with an independent implementation, not as an explicitly published specification example; the characterization will say so and reuse only this invented key/vector. `EMVOperationsValidationTest` already covers crypto-level malformed transaction data and padding behavior and will remain unchanged. `EmvProfileStatusUITest` remains unchanged and is included in the UI suite.

`EmvArqcCharacterizationUITest` will characterize both UI actions, verify a known invented-key ARQC, verify an altered ARQC as invalid, and try malformed UN and session-key length in EN and ES. It will check output classification and the inspector, history, Shelf, status bar, expanded viewer, and captured logs under `FULL_LAB`, `MASKED`, and `REDACTED`, with invented keys only. Its UTF-8 transcript digest will exclude paths, times, unordered data, and normalize provider/JDK exception spans to `<jdk-exception>`. Record any failure here before changing the pinned digest or fixing behavior.

## Characterization failures recorded before fixes

The first real-UI characterization run reproduced the cross-checked invented-key ARQC `A8DB2B65F9C821F1`; generated and valid-verification values were correct. In both `MASKED` and `REDACTED`, generation, valid verification, and altered-ARQC verification were classified `PUBLIC`, leaking the ARQC (and the verification report's session key) through the shared result viewer, Shelf, and expanded viewer. History and inspector showed no needle; status and captured logs were also safe. `FULL_LAB` displayed the expected invented values. These are output-classification failures to fix before extraction.

Malformed four-byte UN and short session-key inputs displayed generic English exception messages in both EN and ES, instead of locale-specific validation feedback. The altered-ARQC status message itself was localized in both languages. The first draft transcript includes the formatted status-bar text, which appends the current time; that draft digest was `5801c080d69b61ba3d547a24008817a93e4fd5f951e1604abf971e2c182afa26` and is explicitly discarded as nonportable. No digest has been pinned for phase 2 yet. The test must record only the fixed localized status text before pinning its stable transcript.

After changing the transcript to record only the matched localized status phrase (never the time-appended status label), the portable pre-fix SHA-256 is pinned at `67e1bac6d0c6dc8fb8b4c0ffa9c28da5b601f980632359a3c682afa82f86e359`. All exception spans for the two invalid-input diagnostics normalize to `Error: <jdk-exception>` before hashing. This baseline digest fixes the PUBLIC-classification leaks and EN/ES localization failures before the implementation changes.

The separate output-classification fix is commit `2333722`. The generated ARQC bytes and full verification report are now `SECRET`. The characterization no longer finds a key or cryptogram in the result viewer, inspector, history, Shelf, status bar, expanded viewer, or telemetry under either restricted profile. Its portable post-classification/pre-localization digest is `5834eed522d6d967c7d293d286c385103dd80290ef4172acb21c594106bdac26`; compared with baseline `67e1bac6d0c6dc8fb8b4c0ffa9c28da5b601f980632359a3c682afa82f86e359`, only classification and resulting shared-surface visibility states change. The digest assertion was rerun successfully; the test then fails only the four EN/ES localization checks. The localization defect remains recorded and unfixed at this point.

The separate localization fix now maps invalid session-key length and malformed four-byte UN inputs to explicit EN/ES messages in the ARQC generation catch path. This does not change `crypto/` validation. After correction, `EmvArqcCharacterizationUITest` produces portable SHA-256 `83596873ab42981366e1570edeac8c53a8a0b0f301cc496faa90aaeec49c851d`; the only transcript differences from post-classification `5834eed522d6d967c7d293d286c385103dd80290ef4172acb21c594106bdac26` are localized invalid-input diagnostics replacing the normalized exception marker. The restricted-profile checks remain safe.

## Phase 2 extraction

`EmvArqcCoordinator` now owns the two ARQC handlers and their generation-error mapping. Its `View` record holds lazy field suppliers, and it receives only `Supplier<StatusReporter>` rather than a controller. The exact-input/padding/ARQC cache moved into its package-level `State` holder, which `EMVController` owns separately. The public FXML actions remain one-line delegates. `clearModuleData` now invokes `State.clear()` so clearing the module still invalidates the generated-ARQC verification cache; the method itself and the rest of module clearing remain for phase 3. Cache clearing also restores its unused padding value to method 1.

All calculation and transaction-data validation still comes from `crypto/EMVOperations`; no crypto source changed. The ARQC characterization passed after the extraction with the same portable digest `83596873ab42981366e1570edeac8c53a8a0b0f301cc496faa90aaeec49c851d`.

Line counts: phase 2 began from the phase 1 result at 1352 lines. The two localized validation messages temporarily brought `EMVController` to 1371 lines before extraction; after extraction it is 1156 lines. The two ARQC public handlers are one-line delegates. No public API signature changed.

### Gate finding: validation-feedback source ownership

The first normal-suite gate after extraction ran 2869 tests: 1 failure, 0 errors, 1 skipped. `SpecializedFeedbackHeadlessTest.specializedValidationFeedbackHasDistinctEnglishAndSpanishKeys` still expects `module.emv.feedback.arqcRequired` to be used directly by `EMVController`; ARQC input validation now lives in `EmvArqcCoordinator`. This is a stale source-ownership assertion, not a runtime or localization failure. The same test already checks the session-required message in `EmvSessionKeyCoordinator`. Before rerunning the gate, move the ARQC-required key assertion to `EmvArqcCoordinator`, preserving its EN/ES translation checks. This is a justified existing-test adjustment for the extracted ownership boundary.

The focused headless rerun exposed the same stale ownership assumption for `module.emv.feedback.arqcAmountRequired` and `module.emv.feedback.arqcValid`. Both are also used only by `EmvArqcCoordinator`; move their source checks alongside `arqcRequired`, retaining the per-key EN/ES assertions. The focused run had 2 tests: 1 failure, 0 errors, 0 skipped, caused by this static assertion.

After moving all three ARQC-owned message assertions to the coordinator and retaining the English/Spanish resource checks, `SpecializedFeedbackHeadlessTest` passed 2/2. This existing-test update is commit `0de98b1`; it does not alter characterization output. The characterization's final portable digest remains `83596873ab42981366e1570edeac8c53a8a0b0f301cc496faa90aaeec49c851d`.

### Phase 2 gates

After the stale source-ownership assertion was recorded and adjusted, all three requested commands passed:

| Command | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `mvn -o -q test -Plow-cpu` | 2869 | 0 | 0 | 1 |
| `mvn -o -q test -Plow-cpu -DrunUiTests=true` | 2869 | 0 | 0 | 1 |
| `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test` | 525 | 0 | 0 | 0 |

The CI-filtered UI count (525) differs from the full normal-suite count (2869). `ExpandedViewerLifecycleUITest` passed 3/3 in the CI-filtered run, so no baseline comparison against `main` was necessary. No gate or CI threshold was relaxed.
