# Manual Conversion

The local toolbar offers Text UTF-8, Hexadecimal, Base64, Base64URL, Base94, Binary and Decimal. Convert parses input with that selector and renders the same bytes using the output format. Example: Hex `4142` to Base64 produces `QUI=`. Hex input must contain an even number of digits.

Special actions have their own parsers: Base64URL encodes UTF-8 text without padding and rejects symbols outside its alphabet; Base32 encodes UTF-8 text with padding (`A` → `IE======`); URL uses UTF-8 form escapes (`a b` → `a+b`, and `+` decodes to a space). Endian reverses bytes per 16/32/64/128-bit word: `01020304` with 32-bit words → `04030201`; lengths not divisible by the selected size are invalid.

EBCDIC uses the conversion checkbox and selected direction/page; UTF-8 `AB` to IBM037 with Hex output produces `C1C2`. Compression uses gzip/zlib/deflate over bytes from the local format; decompression must select the matching wrapper and is limited to 64 MiB. It does not encrypt. BCD packs decimal digits two per byte (an odd count is prefixed with zero); COMP-3 accepts a signed decimal integer and uses trailing C/D sign nibbles. Shift parses Hex and preserves width; its bit count must be a non-negative integer. Extract Trace Hex finds runs of at least 4 bytes and ignores short groups/conventional offsets.

Applying/managing templates does not execute conversion. Read the active button’s help: not every action uses the toolbar formats. Implementation: `ManualConversionCoordinator`, `SafeTransformations`, `DataConverter` and `generic.fxml`.
