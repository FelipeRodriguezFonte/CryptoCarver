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

## Characterization failures recorded before fixes

Ran `EmvSessionKeyCharacterizationUITest` against the unmodified `f31bb89` behavior with invented keys. The portable transcript uses fixed operation ordering, contains no exception text, date, path, duration, or unordered collection values, and its initial SHA-256 is `f2b88fb327543532ac61caad4c4448fb3a2f11abc620b0ef43ab093e481e1eba`.

- Under `MASKED` and `REDACTED`, `handleDeriveSessionKey` published its full report with `PUBLIC` classification. The result viewer, stored history, Shelf, and expanded viewer contained the IMK, ICC master key, and session key. Inspector, status, and captured application logs did not contain those needles.
- Under `MASKED` and `REDACTED`, the secure-messaging publication correctly had `SECRET` classification and its result viewer, Shelf, expanded viewer, status, and captured logs were safe. However, the inspector displayed the secret UDK/session-key details, and the persisted history recipe kept `EMVController.smMkSmiField` and `smMkSmcField` in clear. These are separate classification omissions: inspector publication bypasses the existing visibility filter, and `UiStateSnapshot` does not recognize the standalone `mk` field-name token.
- The corresponding `FULL_LAB` results showed the invented keys, as expected. Malformed PAN, ATC, and key length produced readable messages in both EN and ES.

These observations are recorded before either classification fix. They are characterization failures, not expected behavior to preserve.

Correction from the digest-pinned assertion run: history also retained `EMVController.imkField` in clear under both restricted profiles. The saved recipe does not currently classify the field name `imk`; this finding is part of the same history-field classification gap as the two `smMk*` fields. After the separate output-classification fix, the transcript digest became `ddf435716fce8937194f7e041fd627c39c117137052927ad611746982e71bc30`; viewer, Shelf, and expanded-viewer leaks for the direct session report disappeared, while the `imkField` history leak remained. That digest change is explained by the output's classification and surface visibility changing from public to secret.

Further failure recorded after recognizing `imk` and `mk`: the `MASKED` and `REDACTED` secure-messaging history recipes still included derived `smUdk*` and `smSk*` field values. The field-name vocabulary already recognizes `mac`, so the remaining omission is the EMV abbreviations `udk` and `sk`. The resulting transcript digest was `85dc6d7a0e51f3cf16938a025dba6ec462a0a76727e70631af7681339f7bb6ab`; only the secure-messaging history surface remained leaky among the surfaces fixed so far. This failure is recorded before adding those vocabulary entries.

After adding `imk`, `mk`, `udk`, and `sk` as exact sensitive field-name tokens, the restricted-profile history checks for both session derivation and secure-messaging derivation are safe. The transcript digest at this point is `2fe5b9dd253fba2f0cf818555fe07f1c377f6d33ac019b570259720831d3cfa1`; the only remaining characterization failure is the secret-detail leak in the inspector, so that omission is recorded before its separate fix.
