# Two-row compressed Hex

The screen has one input field shared by Expand and Compress. Expand reads exactly two non-empty rows: each column joins the high-row nibble with the low-row nibble. Rows must have equal length and contain only Hex. Example: `1234` / `ABCD` → `1A2B3C4D`. One row, unequal lengths or an invalid character are rejected.

Compress accepts conventional Hex with an even digit count and splits even/odd positions into the rows. `1A2B3C4D` → `1234` / `ABCD`. Whitespace and line breaks are ignored; a `0x` prefix is not accepted. Each direction’s `?` sets the action; the input `?` describes that direction’s parser. “Compressed” names the host layout, not data compression or encryption. Implementation: `CompressedHexCodec`, `CompressedHexController` and `compressed_hex.fxml`.
