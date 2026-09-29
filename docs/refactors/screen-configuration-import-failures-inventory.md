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
