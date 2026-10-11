# File Conversion

Source/destination formats: Binary reads bytes; Text uses UTF-8, ASCII or ISO-8859-1; Hex reads text, removes whitespace and requires valid digit pairs; Base64 uses the basic decoder. Example: Text file `AB` converted to Hex produces `4142`. With no destination, up to 1000 output characters are shown on screen; writing requires a valid destination path, separate from the source if it must be preserved.

Compare reads source and comparison as bytes regardless of selectors and reports equality or the first differing offset (zero-based); both files are required. Hash File computes SHA-256 in blocks and reports byte count. Preview shows up to 4096 source bytes as Hex. These actions require a readable source; comparison also requires its comparison file. Paths and contents are not published in help context. File conversion currently reads the whole file; comparison and hashing process blocks.

Implementation: `FileConversionCoordinator`, `FileConverter`, `StreamingFileTools` and `generic.fxml`.
