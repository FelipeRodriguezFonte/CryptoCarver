# EMV refactor 4 — Track 2

Base: `codex/emv-2-v2` at `9558bb0`, based on `main`. `EMVController.java` has 1051 lines before this phase. The requested method boundaries were verified by matching the Java braces in the current source.

| Method | Current lines | State read | State written | UI wiring vs EMV logic |
|---|---:|---|---|---|
| `handleEncodeTrack2` | 719–769 (51 lines) | `panTrack2Field`, `expiryTrack2Field`, `serviceCodeFieldTrack2`, `discretionaryDataField`; localized text; lazy `StatusReporter` | `track2ResultArea` text/visibility/managed; publishes Track 2 bytes and masked PAN/expiry/service-code details, then shared result/history/Shelf/status/presentation state via `StatusReporter` | Input parsing, report layout and publication are UI wiring. `EMVOperations.encodeTrack2` validates and formats Track 2 in `crypto/` (out of scope). |
| `handleDecodeTrack2` | 771–794 (24 lines) | `track2InputField`; localized text; lazy `StatusReporter` | `track2ResultArea` text/visibility/managed; publishes the submitted Track 2 bytes, decoded report and a non-persisted PAN detail, then shared result/history/Shelf/status/presentation state via `StatusReporter` | Input parsing, report display and publication are UI wiring. `EMVOperations.decodeTrack2` parses Track 2 in `crypto/` (out of scope). |

## Extraction boundary and state coupling

Both public FXML handlers can remain unchanged as one-line delegates to `EmvTrack2Coordinator`. Its `record View` should expose lazy suppliers for the five input controls and result area, and the coordinator should receive `Supplier<StatusReporter>`. It must not retain `EMVController`. The controller's API and FXML action names stay unchanged.

`track2ResultArea` is also read by the existing `getOutputText()` accessor and cleared by the module-state coordinator; those are simple shared-control accesses, not parsing or formatting dependencies. The helper `maskPan` is used only by the encoding action and can move with it. No `AppSettings` state is read directly. `StatusReporter` owns publication and profile-dependent presentation; do not change it or shared shell/history classes. The cryptographic calls remain in `crypto/` and must not be edited.

The current `OperationResult` builders do not explicitly classify either Track 2 output as secret even though the encoded output contains the PAN and the decoded report contains PAN/expiry/service data. This is a pre-characterization risk, not yet an observed failure. The characterization records actual visibility before any correction.

## Characterization plan

`EmvTrack2CharacterizationUITest` will exercise a complete encode/decode cycle with invented card data; empty required fields and malformed Track 2 in EN and ES; and `FULL_LAB`, `MASKED`, and `REDACTED`. It will inspect the result viewer, inspector, temporary history, Clipboard Shelf, status bar, expanded viewer, and captured stdout/stderr for the invented PAN and Track 2 string. Only stable localized diagnostics and deterministic surface outcomes belong in its UTF-8 transcript. Normalize JDK/provider exception text to `<jdk-exception>` and omit paths, dates, durations, and unordered iteration before pinning SHA-256. Record any reproduced leak or other failure here before a fix.

Existing Track 2 tests, if any, remain regression checks; no test is to be weakened to accommodate the extraction. This phase is separable from TLV and other existing coordinator state. No crypto source or shared `ModernMainController`, `UiStateSnapshot`, `StatusReporter`, or `OperationResult` code is in scope.

## Characterization findings

Pending the pre-fix real-UI run. No behavior has been changed in this phase yet.
