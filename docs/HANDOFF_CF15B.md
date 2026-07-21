# CF-15B: PKCS#11 Profiles and Enhanced Diagnostics

## Context
This task builds on CF-15A, improving the use of tokens (PKCS#11) by adding profiles persistence (without saving sensitive PINs) and adding deeper diagnostics such as enumerating the supported cryptographic mechanisms of the token.

## Changes Implemented
1. **Profiles Persistence**
   - Created `Pkcs11Profile.java` record.
   - Updated `AppSettings.java` to handle a list of PKCS#11 profiles.
   - Designed the UI (`main-view-modern.fxml`) to include a combo box and save/delete profile buttons.
   - Wired `KeysController.java` to connect the frontend interaction to `AppSettings`. PINs are dynamically prompted.

2. **Deeper Diagnostics**
   - Updated `Pkcs11Session.java` to include `getSupportedMechanisms()` method mapping to the provider's advertised JCA services for `Signature`, `Cipher`, and `Mac`. It is a compatibility diagnostic, not a direct native `C_GetMechanismList` enumeration, so a specific token key can still reject an advertised operation.
   - Enhanced `KeysController.connectPkcs11()` to retrieve these mechanisms and display them alongside token objects.
   - Highlighted which mechanisms in the UI algorithms dropdown are supported natively by the token.

3. **SoftHSM Integration Test Enhancements**
   - Added validations for `getSupportedMechanisms("Signature")`.
   - Added conditional test `testSymmetricCrypto()` for AES-CBC, if a secret key (`Secret key`) exists and the token advertises `AES/CBC/PKCS5Padding`.

4. **Testing**
   - Added unit tests in `AppSettingsTest.java` using a temporary settings file. They verify profile persistence and ensure the real user settings file is never touched or cleared by tests.
   - The symmetric SoftHSM test now selects only an AES secret key, never an arbitrary secret key.

## Verification
- Validated FXML using `xmllint`.
- Checked formatting using `git diff --check`.
- Run the complete Maven suite before considering the block closed; a UI test that only passes in isolation is a blocker, not a known limitation.

## Codex review adjustments

- Profile persistence validates that the slot is non-negative and persists no PIN field.
- The report calls these entries **JCA provider services**, rather than native token mechanisms, to avoid promising a `C_GetMechanismList` capability that has not been implemented.
- Verification after the adjustments: `mvn -q -Prelease-experimental -DrunUiTests=true test` passed in an isolated working copy; `git diff --check` and `xmllint --noout src/main/resources/fxml/main-view-modern.fxml` are clean.
