# EMV refactor 1 — session keys

Base: `main` at `f31bb8917d6a3f599c14f211da0981a5b476e101`; GitHub Actions reports all three checks successful for this commit. The worktree branch is `codex/emv-1`. `EMVController.java` has 1469 lines at this base.

The requested method boundaries were re-counted by matching braces in the Java source. Counts include the declaration and closing brace:

| Method | Base lines | Lines | State read | State written |
|---|---:|---:|---|---|
| `handleDeriveSessionKey` | 331–398 | 68 | `imkField`, `panFieldSession`, `panSeqFieldSession`, `atcField`, `iccMethodCombo`; localized text; `StatusReporter` | `sessionKeyResultArea` text/visibility/managed; published result, history, status, and presentation surfaces through `StatusReporter.publish` |
| `handleSmDeriveSessionKeys` | 452–495 | 44 | `smSchemeCombo`, issuer/card key fields, PAN sequence, ATC, application cryptogram, command number; localized text; `StatusReporter` | derived UDK and session-key controls, `smResultArea` text/visibility/managed; published secret details and status through `smPublish` |
| `deriveLaboratorySessionKey` | 1254–1270 | 17 | `PaymentProfile.inputs` (`sessionKey`, `imk`, `pan`, `panSeq`, `atc`) | returned session-key string; no UI or shared state |

## State and extraction boundary

`handleDeriveSessionKey` parses and formats the laboratory report, but delegates ICC-master-key and session-key calculations to `crypto/EMVOperations`. It currently publishes the complete report as an unclassified output even though the report contains the supplied issuer key and derived ICC/session keys. Its detail map labels only the IMK as not persisted; that does not classify the output. This is an observed visibility risk to characterize before changing it.

`handleSmDeriveSessionKeys` chooses the Visa or Mastercard flow, parses the command number, writes the calculated keys into the controls, and formats the report. The cryptographic calculations are in `crypto/EmvSecureMessaging`; that package is outside this work. The existing `smShow` and `smPublish` helpers perform UI and publication wiring. Extraction may pass control access as lazy suppliers and the reporter as `Supplier<StatusReporter>`; it must not retain `EMVController`.

`deriveLaboratorySessionKey` is a small profile adapter. The key derivation itself is in `crypto/EMVOperations`, also outside this work. It can move with the session-key coordinator while remaining package-private for profile-state work in the later phase.

The coordinator will use a `record View` of lazy control suppliers and a lazy `StatusReporter` supplier. Public `EMVController` entry points remain one-line delegates. The controller remains the FXML owner. `AppSettings` does not participate in the calculations; the shared shell applies its visibility policy when the published result is presented, copied to Shelf, or captured in history. Tests must therefore inspect the result area, expanded viewer, inspector, history, Shelf, status, and captured logs for all three profiles.

## Existing evidence and test adjustments

`EmvProfileStatusUITest` already exercises the real laboratory menu in EN and ES and loads two positive and one negative EMV profile. Keep it as a regression test; any update must continue checking the localized profile status in both visible and accessible text.

The repository has EMV test vectors in `PaymentControlValuesTest`: its EMV Book 2 A1.4.1/A1.3 values are annotated cross-checked and use the repository's invented house key. No EMV vector there is identified as a published specification example, so characterization will reuse only those invented-key controls where applicable and will not claim a published vector. `EMVOperationsValidationTest` and `EMVOptionBTest` remain unchanged. Cryptographic behavior resides in `crypto/` and no file there is in scope.

Before extraction, `EmvSessionKeyCharacterizationUITest` will record localized validation feedback for malformed PAN, ATC, and key length, session-key derivation and the included secure-messaging session-key action. It will exercise `FULL_LAB`, `MASKED`, and `REDACTED` against the shell's result surfaces with invented keys, and pin a UTF-8 transcript digest. JDK/provider exception substrings, filesystem paths, dates, timings, and unordered collection iteration must be normalized or excluded before digesting. Any failures found will be recorded here before a separate defect-fix commit.

The requested group is separable from track 2, ODA, the rest of secure messaging, HCE, and TLV. Those behaviors and their crypto implementations remain untouched.
