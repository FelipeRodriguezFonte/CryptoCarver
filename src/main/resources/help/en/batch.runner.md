# Batch Runner — C009 / B05.2a

The pane applies one of the 40 visible `BatchOperationCatalog` entries to the configured source field of each row. It returns a row report; it is not the record-encryption branch. The selector does not offer Encrypt Record or Decrypt Record. Their algorithm, key, IV/nonce, AAD, record encoding, charset, and compact controls are hidden and have no effect on the selectable operations.

## Input and row contract

The input-format selector parses pasted text. CSV requires a unique header, comma-separated fields, double-quoted cells, doubled quotes inside quoted cells, and equal column counts per row. Quoted cells may contain commas and line breaks. JSON Lines accepts one non-empty JSON object per line; values must be scalar, so arrays and nested objects are rejected. A whole JSON array/document is not JSONL.

Both parsers reject empty/duplicate headers or property names and sensitive source names such as `key`, `password`, `pin`, `pan`, `cvv`, `iv`, `nonce`, `aad`, `salt`, `secret`, `private`, `certificate`, `token`, `signature`, or `mac`. Every cell is limited to 100,000 characters, the input stream to 100,000,000 characters, and the parser to 10,000 data rows. JSONL also bounds one line to 5,000,000 characters. Blank CSV records and blank JSONL lines are skipped.

The source-column name must be non-empty and present on every row. The selected operation receives that value as text. A successful row adds the operation result under the destination-column name. Input parsing or missing-column errors block the run; operation errors are kept on the affected rows. With Stop on error enabled, the first worker error requests cooperative cancellation, so other rows already running can finish; with it disabled, processing continues through the remaining rows. The pane displays up to 50 results. Progress counts completed rows, including row errors.

## Actions and state

- **Load input** reads a selected file as UTF-8. The chooser accepts CSV, JSONL, NDJSON, or TXT; `.csv` chooses CSV and other extensions choose JSON Lines. Pasted input is parsed using the visible format selector.
- **Dry Run** parses rows and validates columns and operation readiness. It does not call the selected operation, write a file, or create history. The preview replaces the visible result text but preserves input.
- **Run** starts background row processing and clears the previous visible report. A completed report can be exported.
- **Cancel** requests cancellation. The coordinator discards the cancelled report and clears visible results; partial results are not exportable.
- **Reset** cancels active work, empties input and results, resets progress and report state, and clears temporary crypto controls.
- **Export** writes a completed report as UTF-8 CSV or JSON Lines. The output format is independent of the input format. CSV escapes quoted cells; JSON Lines writes one result object per row.
- **Operation, input, result, progress and status** each have direct contextual help. Selecting an operation updates only the public operation code in help context; the help view does not inspect the input, result, source path, or secrets.

## Operation-specific help

Each selectable entry has its own localized contract, concrete example, and negative case in the selector help. The example and error are shown for the selected operation while the selector, source/result field, or action help is open. The descriptions follow `BatchOperationCatalog.execute` and its live codecs/consumers; digest/checksum entries hash UTF-8 text, format conversions decode only the representation named by their entry, and structured analyzers return JSON without claiming full protocol validation. The operation matrix maps every live label to its option key and tested vector.
