# ISO 8583 report ordering map

## Map copies and consumers

- `Message` copies parsed fields with `Map.copyOf` in `Iso8583Operations.java:62`. Parsing inserts fields into a `LinkedHashMap` while walking bitmap positions in ascending field number. The copy does not promise to preserve that order. `report()` walks `m.fields().values()` at line 198, so field lines can be rendered in a different order.
- The direct UI consumer is `Iso8583Coordinator`: parse calls `Message.report()` and places that text in the Payments pane report area. It updates the status bar, but does not publish an `OperationResult`; the report is not automatically written to operation History. The report can be viewed or copied from the UI as text.
- `Iso8583NodeHandler` also returns `Message.report()` as a UTF-8 `FlowValue`. `ProcessEngine` retains this result for downstream process nodes. Its execution table reports output representation and size; it does not iterate the field map or regenerate the field report. No ISO-specific text export or history consumer of `Message.fields()` was found.
- `dictionary(version)` copies `COMMON`/a `LinkedHashMap` with `Map.copyOf` at line 121. Callers use it for lookups (`parse`, `build`, `fieldDefinition`); they do not iterate the returned map to create report text. This copy is still changed to an immutable numerically sorted map for a defined iteration contract.
- `build()` uses `Map.of()` only as the empty fallback for a null input map. Its bitmap sizing uses the key set, and its field encoding explicitly loops from 2 through 192; it does not emit text by iterating that map.

## Expected order

ISO 8583 data elements are identified by decimal field number. The natural report order is ascending numeric field number, e.g. DE 3, DE 4, DE 7, DE 11, DE 41, DE 49. The input order does not change the message semantics or report ordering.

## Scope of other map factories

The only `Map.copyOf`/`Map.of` occurrences in `com.cryptocarver.crypto.iso8583` are the two copies above and `Map.of()` at `build()`'s null-input fallback. No other map factory in that package is traversed to generate text. Changes are restricted to `Iso8583Operations.java` under `crypto/`.

## Characterization

The regression fixture uses invented field values and constructs a `Message` from a deliberately non-ascending `LinkedHashMap`. Before the fix, its test is expected to fail because the report's field lines do not follow numeric order. After the fix, the complete report is pinned by SHA-256 and the digest is checked across separate JVM executions.
