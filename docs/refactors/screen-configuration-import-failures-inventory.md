# Screen configuration import failure inventory

This inventory describes the behavior before Encargo 40.

| Failure route | Exception from decode/read boundary | Message displayed by import UI |
|---|---|---|
| Wrong password for a valid encrypted document | `IllegalArgumentException`: `Incorrect password or modified configuration file` | Generic `dialog.configuration.importFailure` |
| Encrypted document modified so AEAD authentication fails | Same `IllegalArgumentException` as wrong password; AEAD makes these indistinguishable | Generic import failure |
| Unrecognized document format / JSON which is not a screen configuration | Runtime parse/validation exception from Gson or `ScreenConfiguration.fromJson` (often `IllegalArgumentException`) | Generic import failure |
| Invalid JSON | Gson `JsonSyntaxException` (a runtime exception) | Generic import failure |
| Unsupported plain or encrypted version | `IllegalArgumentException` from configuration/envelope validation | Generic import failure |
| Damaged encrypted header/envelope | `IllegalArgumentException` (`Invalid encrypted configuration envelope` or a wrapped Base64/runtime validation cause) | Generic import failure |
| Empty file | `IllegalArgumentException` from `ScreenConfigurationCodec.decode` | Generic import failure |
| File larger than 12 MB | `IllegalArgumentException` from `ScreenConfigurationFiles.read` | Generic import failure |
| File read/path error | `IOException` from `ScreenConfigurationFiles.read` | Generic import failure |

The UI catches all exceptions from read, decode, review, and apply and hides their details. The same generic alert is therefore shown for all of the above.

## Other protected-file importers

- Saved sessions use `SavedSessionsManager` and `SavedSessionCodec`. Its encrypted-field AEAD failure is handled separately and the saved-session UI has a dedicated `savedSessions.decryptError` message that explicitly says the password may be incorrect or the session modified; it does not use the generic screen-configuration alert.
- Shelf packages are authenticated artifacts carried by clipboard entries. The inspected loading/validation paths do not prompt for a password or decrypt a password-protected file; no instance of this specific defect was found.
- History export writes history data; the inspected history import/export flow does not load a password-protected file. No instance of this specific defect was found.

No other importers are changed as part of this work.

## Final behavior

`ScreenConfigurationImportException` is a safe `IllegalArgumentException` subtype. Its message and `toString()` contain only the enum reason; it does not retain the source exception, document, field values, or password. Wrong password and authenticated modification deliberately share `WRONG_PASSWORD_OR_TAMPERED` because AEAD cannot distinguish them.

| Reason | Exception classification | English message | Spanish message |
|---|---|---|---|
| Wrong password or AEAD modification | `ScreenConfigurationImportException(WRONG_PASSWORD_OR_TAMPERED)` | The password may be incorrect or the configuration file may have been modified. | La contraseña puede ser incorrecta o el fichero de configuración puede haberse modificado. |
| Not a configuration / malformed document | `ScreenConfigurationImportException(NOT_A_CONFIGURATION)` | This file is not a valid screen configuration. | Este fichero no es una configuración de pantalla válida. |
| Unsupported plain or encrypted version | `ScreenConfigurationImportException(UNSUPPORTED_VERSION)` | This screen configuration uses an unsupported version. | Esta configuración de pantalla usa una versión no compatible. |
| Damaged encrypted envelope/header, invalid encrypted parameters, oversized or unreadable file | `ScreenConfigurationImportException(UNREADABLE)` | The screen configuration file could not be read. | No se pudo leer el fichero de configuración de pantalla. |
| Empty document | `ScreenConfigurationImportException(EMPTY)` | The screen configuration file is empty. | El fichero de configuración de pantalla está vacío. |
| Unexpected UI/application exception | Generic fallback | The screen configuration could not be imported. | No se pudo importar la configuración de pantalla. |

For the first reason, the import UI shows one error notice with Retry and Cancel. It accepts at most three password attempts, keeps the selected file, and stops on either cancellation. The user must also confirm the review dialog before navigation and restoration. Import application validates the route/module and allowed fields against the destination controller before navigating or restoring state.

## Characterization and verification evidence

Before the typed failure and UI changes, characterization ran against the existing behavior:

- Codec and existing coordinator tests: `mvn -o -q -Dtest=ScreenConfigurationImportCharacterizationTest,ScreenConfigurationCodecTest test`; 11 tests passed, 0 failures/errors, 15.8 seconds. This captures exception types and valid plain/encrypted decode behavior.
- Production FXML import flow in test mode: `mvn -o -q -DrunUiTests=true -Dtest=ScreenConfigurationImportUiCharacterizationTest test`; 7 tests passed, 0 failures/errors (clean run command time 5.9 seconds). All six malformed/wrong-password cases emitted the same generic error; valid plain and encrypted files reached the review dialog.
- Filesystem/coordinator `importFrom` without JavaFX: `mvn -o -q -DrunUiTests=false -Dtest=ScreenConfigurationImportBoundaryTest test`; 3 tests passed, 0 failures/errors, 3.5 seconds.

The characterization harness injects a selected file and password response while invoking `importScreenConfiguration` on the production FXML controller. `DialogService` records test-mode dialog output and returns scripted responses, so no native chooser or modal window is opened. Tests use synthetic values and restore `AppSettings`, `user.home`, and `test.mode` after each run.

Final directed tests were run after implementation; the full Maven suite was left for the coordinator as requested. No manual UI test was performed. No CSS or FXML file changed; no image was added.

The commit timestamps/order have one implementation bookkeeping discrepancy: the type/codec commit `8702a38` appears before the production-FXML characterization commit `36112c9`. The UI characterization itself was run against the baseline source: those implementation edits were stashed while adding the dialog/file test seams, and restored only after the clean baseline UI and `importFrom` runs. The history therefore does not imply that the UI baseline assertions observed the typed implementation.

Post-change focused verification:

- UI: `mvn -o -q -DrunUiTests=true -Dtest=ScreenConfigurationCharacterizationTest,ScreenConfigurationImportUiCharacterizationTest,ScreenConfigurationLocalizationTest test`; 25 tests passed, 0 failures/errors, 19.9 seconds.
- Model/filesystem: `mvn -o -q -DrunUiTests=false -Dtest=ScreenConfigurationCodecTest,ScreenConfigurationImportCharacterizationTest,ScreenConfigurationImportFailureTest,ScreenConfigurationImportBoundaryTest test`; 31 tests passed, 0 failures/errors, 4.5 seconds.

These focused commands cover 56 tests total. The full suite is reserved for the coordinator and has not been run here.
