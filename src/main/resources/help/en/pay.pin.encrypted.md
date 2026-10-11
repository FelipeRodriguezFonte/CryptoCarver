# Protected PIN blocks — C081

The format selector is shared by protection and decoding. This screen builds/reports the selected PIN field/block. For non-ISO-4, a supplied key encrypts that clear block. ISO-4 with a key calls its dedicated AES helper, which generates a random-padded PIN field for encryption. The no-key path is existing behavior: it publishes a clear PIN field/block and reports `No key supplied`. Do not call that result encrypted.

## Fields and protection routes

- PIN: decimal digits; accepted length depends on the selected `PinBlockFormat` implementation. PAN is required only for formats whose catalog says `usesPan()`, although ISO-4 protection binds the PAN into AES.
- Encode key and decode key are separate Hex inputs. For non-ISO-4 formats the actual route is ECB with no padding: an 8-byte key selects DES; 16- or 24-byte keys select TDES. Hex representations are 16, 32 or 48 characters. The UI placeholder says `16/32/48 hex (TDES)` but the 16-character case takes the DES branch.
- ISO-4 uses AES via `PinBlock.encipherPinblockIso4`; accepted AES key sizes are 16, 24 or 32 bytes (32/48/64 Hex characters). The output block is 16 bytes / 32 Hex characters. ISO-4 uses a 16-byte PIN field and 16-byte PAN field in the specified AES transformation.
- Other protected blocks are 8 bytes / 16 Hex characters. The same selector controls both directions, but the encode and decode key fields are independent.
- C081 has no padding selector: protection uses the format's default, independently of C080's selector. VISA-2/3 use 5; ECI-2/3 use F; Docutel uses `RANDOM_DECIMAL`. Formats without a declared default follow their internal behavior. Decoding follows the selected format's construction; changing the clear-block padding selector does not reinterpret this input.
- Decode without a key treats the supplied input as a clear field/block. For ISO-4, a protected block requires AES key and matching PAN. A clear ISO-4 PIN field can be read without AES by the current engine path.
- Result text can contain clear or protected PIN material. Contextual help sees only the format code and independent booleans for key-field emptiness; it never reads key, PIN, PAN, block or result text.

## Examples and diagnostics

TDES round trip: use the ISO-0 format, synthetic PIN `1234`, PAN `4761739001010010`, and a valid 16-byte TDES test key. Protect to an 8-byte block, then decode with the same format, PAN and matching decode key; the PIN returns as `1234`.

AES round trip: use ISO-4, PIN `1234`, the same PAN and a valid 16-byte AES test key. Protect to a 16-byte/32-hex block and decode with the same PAN and decode key.

No-key state: with ISO-0 and no encode key, the screen returns a 16-hex clear block and reports `No key supplied`. With ISO-4 and no key, it returns the 32-hex clear PIN field, not a protected block.

Negative checks: a 10-byte TDES key is rejected; malformed/non-hex block input fails decode; an ISO-4 block of the wrong size fails; using a different PAN/key will not recover the expected PIN. Help itself does not run any handler.

## Sources

- `PinBlockCoordinator.handleEncodeEncryptedPinBlock()` and `handleDecodeEncryptedPinBlock()` establish key-field separation, no-key behavior, and result state.
- `PaymentOperations.encryptDesEcb()` / `decryptDesEcb()` choose DES only for 8-byte keys and TDES otherwise; `encipherPinBlockISO4()` / `decipherPinBlockISO4()` call the ISO-4 AES path.
- `com.cryptocarver.pin.PinBlock` defines ISO-4 AES validation and transformation.
- `PinBlockCharacterizationUITest` covers TDES/AES round trips, missing/invalid keys, malformed blocks and no-key output; `PaymentControlValuesTest` contains ISO-4 AES vectors.
