# Clear PIN blocks — C080

This screen builds or interprets a PIN field/block in clear hex. A clear PIN field is not encrypted and does not prove that a block is protected. Encoding and decoding have separate format selectors; the padding selector affects only the encoder and only formats that expose it.

## Inputs and results

- PIN is decimal text. The clear-block UI checks 4–12 digits; several legacy formats impose a narrower engine range (VISA-2/ECI-3/Docutel 4–6; ECI-2 exactly 4). No PIN value is copied into help state.
- PAN is 13–19 decimal digits when present. It is required for formats with PAN binding. The clear encoder also rejects a malformed nonempty PAN for formats whose implementation ignores it. Whitespace is stripped from PAN before encoding/decoding.
- A normal block is 8 bytes / 16 hex characters. ISO-4 clear PIN and PAN fields are each 16 bytes / 32 hex characters. Hex characters are not bytes.
- Decode requires a block and, for PAN-based formats, PAN. The current ISO-4 clear-panel UI also requires a nonempty PAN and accepts a 32-hex PAN field; the ISO-4 clear PIN-field decoder itself reads the PIN field and does not use that input.
- The result panel shows block/PIN and current UI details; it can therefore display sensitive values. Help reads neither fields nor result text.

## Format behavior from the implemented catalog

| Format | PAN behavior in this implementation | Clear representation / effective PIN rule |
|---|---|---|
| Format 0 (ISO-0), ANSI X9.8, VISA-1, ECI-1 | PAN digits 2–13 from the right (12 digits before the check digit), prefixed with `0000`, then XORed with the PIN field | 8-byte ISO-0 field; control nibble 0, PIN length, digits, then fixed `F` fill |
| Format 1 (ISO-1), ECI-4 | No PAN | 8-byte field; PIN length and digits with random hexadecimal fill |
| Format 2 (ISO-2) | No PAN | 8-byte field; control nibble 2, PIN length, digits and fixed `F` fill |
| Format 3 (ISO-3) | Same rightmost 12 digits before check digit, `0000` prefix and XOR | 8-byte field with random hexadecimal PIN-field fill before XOR |
| Format 4 (ISO-4) | Whole PAN is represented: first nibble is PAN length minus 12; PAN is left-padded to 12 digits, then right-padded with zero nibbles to 32 hex characters. The check digit remains included. | 16-byte clear PIN field starts with control nibble 4 and PIN length, uses fixed `A` fill to nibble 15 and random hex for nibbles 16–31. It is not a clear ISO-4 protected block. The separate PAN field is 16 bytes. |
| IBM 3624 | Encoder ignores PAN | 8-byte field; PIN digits and fixed `F` fill, without a length/control nibble |
| VISA-2 | Encoder ignores PAN | 4–6 digits; length nibble, digits, zero fill to nibble 6, then repeated decimal padding |
| VISA-3 | Encoder ignores PAN | 4–12 digits, `F` delimiter, then repeated padding |
| ECI-2 (no PAN binding) | Encoder ignores PAN | Exactly 4 PIN digits followed by hexadecimal fill |
| ECI-3 (no PAN binding) | Encoder ignores PAN | 4–6 digits, length nibble and hexadecimal fill |
| Docutel (Format 02) | Encoder ignores PAN | 4–6 digits; length nibble, zero fill to six digits, then nine decimal padding nibbles |
| Diebold (Format 03) | Encoder ignores PAN | 4–12 digits followed by `F` nibbles; no length nibble |
| VISA-4, Plus Network (Format 04) | First 12 PAN digits from the left, excluding final check digit; `0000` prefix and XOR | 8-byte field; control nibble 0, length, PIN and `F` fill |
| Europay/MasterCard (Pay Now & Pay Later) | Same rightmost 12 PAN digits before check digit as ISO-0 | ISO-0 structure with control nibble 2 |

The padding combo is populated from `PinBlockFormat.paddingOptions()` and defaults from `defaultPadding()`. Options are a fixed nibble, `RANDOM_HEX` (0–F), or `RANDOM_DECIMAL` (0–9), but each format exposes only its supported subset. VISA-2/VISA-3 allow decimal digits and `RANDOM_DECIMAL` (default `5`); ECI-2/ECI-3/Diebold allow hex nibbles plus both random sources (default `F`); Docutel allows decimal digits plus `RANDOM_DECIMAL` (default `RANDOM_DECIMAL`). Other formats do not expose configurable padding. The encoder may use a fixed/random rule internal to the format even when the combo is disabled.

## Examples and diagnostics

Synthetic round trip: PIN `1234`, PAN `4761739001010010`, ISO-0. Encode produces an 8-byte/16-hex clear block. Decode that output with ISO-0 and the same PAN to recover `1234`. With ISO-2, the PAN is not part of the PIN-block construction.

Negative checks: `12A4` is not a decimal PIN; an empty PAN is rejected for ISO-0; selecting `A` as VISA-2 padding is unsupported. Decode rejects non-hex blocks and lengths other than 16 hex characters, or 32 for ISO-4. These are current UI/engine rules; no cryptographic behavior is changed by help.

## Sources

- `PinBlockFormat.displayNames()`, `usesPan()`, `paddingOptions()`, and `defaultPadding()`.
- `PinBlockCoordinator`: input handling, selector independence, and current UI validation.
- `PaymentOperations`: actual format encoders/decoders and PAN field construction.
- `PinBlockCharacterizationUITest`, `PaymentOperationsLegacyPinBlockTest`, and `LegacyPinBlockFormatTest`.
- ISO profiles recorded in `PinBlockFormat`; legacy formats 02–04 reference payShield Host Programmer's Manual 1270A542-038 v3.5, pp. 171–172, and captured variants are identified in `docs/CAPTURAS_PIN_BLOCKS_HEREDADOS.md`.
