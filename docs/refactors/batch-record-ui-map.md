# Batch Record UI map

## Current operation path

- `BatchRunnerCoordinator` owns the Batch Runner interactions in `ui/`; it parses CSV or JSON Lines, configures a per-row operation, runs it asynchronously, renders a report and publishes one `OperationResult`.
- `GenericController` owns the `generic.fxml` module and delegates the Batch Runner handlers to that coordinator.
- For `Encrypt Record` and `Decrypt Record`, the coordinator directly calls `LineRecordCipher.encryptRecord` / `decryptRecord` in `crypto/LineRecordCipher`. Existing callers are the Batch Runner UI and tests; no other production UI entry point was found.
- Batch Runner itself is listed as the `op_gen_batch` operation and has a navigation route to the Generic module's “Batch Runner” accordion pane. The generic module is also accessible via the module navigation.
- The selector is populated from `BatchOperationCatalog.getAvailableOperations()`, which deliberately lists data-only operations and excludes the two record ciphers. The crypto branches and controls existed in the coordinator/FXML, but could only be activated by setting a value absent from the selector; no user could select them in the interface.

## What was missing

- There was no command-palette item for either record operation. The palette only exposed the generic “Batch Runner” command, and its operation selector excluded these two options.
- Navigation ended at the accordion pane; it did not select a specific record operation. The fix adds focused palette items that reuse the generic navigation route and set the coordinator's existing operation value.
- `generic.fxml` already has the batch controls: input format and operation selectors; source/output column fields; algorithm and record encoding selectors; key, IV/nonce and AAD fields; charset, stop-on-error and compact-record controls; input and result areas; run/dry-run/cancel/reset/import/export buttons; progress and status.
- The coordinator used English literals for invalid crypto parameters and the status/report output, so those messages did not follow the EN/ES bundle pattern.

## Existing pattern to follow

Batch Runner uses `BatchRunnerCoordinator.View` to receive FXML controls from `GenericController`, and receives status reporting and window ownership through suppliers. Navigation commands are assembled in `PaletteCommandCatalog` from `OperationRegistry` descriptors and resolved by `UiNavigationRegistry`; `ModulePaneNavigator` opens and expands a module accordion pane. This change should add targeted palette actions that reuse that path and select the corresponding existing operation, while keeping batch parsing/execution in the coordinator.

## Crypto boundary

Exposing the existing operations does not require changing `crypto/`: the coordinator already invokes the public `LineRecordCipher` API and handles parameters, row errors, and task lifetime. No files under `crypto/` are to be changed.

## Characterization notes

Before the UI change, record-operation commands were absent from the command palette. The real-shell palette test failed before implementation with transcript `query Encrypt Record => []` (SHA-256 `661801c3137e418eb7adf1820a19106d56e50b26d88eb0dbe31e383c05cc84e3`). This pins the root access failure before adding direct commands. Final characterization SHA-256: `f267c69e17352e3284685ca7e637812d220e48d259c311bb82e9631241fe272b`. The transcript covers the real shortcut, record round-trip, invalid input/key messages in EN and ES, and all three visibility profiles; random ciphertext is excluded from the transcript.

During the first visibility run, the assertion saw a toy plaintext already present in a previous `FULL_LAB` history entry. The same run after clearing the isolated test history before each visibility profile passed, so this was test cross-contamination rather than a restricted-profile leak. Final characterization transcript SHA-256: `f267c69e17352e3284685ca7e637812d220e48d259c311bb82e9631241fe272b`.
