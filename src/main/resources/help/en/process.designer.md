# Process Designer node catalog

The palette and inspector are driven by all current `NodeCatalog` descriptors. Each node selection has a dedicated help action, a node-specific example, its parameter help, and a port contract. Port help lists each input port, its accepted representations, whether it is required, and the output representation produced from the descriptor's default settings. A selected node takes priority over the general Process Designer route; changing selection replaces its action and parameter context.

## Parameters, conditions, and secrets

Parameter help is generated for the live descriptor and identifies its control type, label, and catalog initial value when present. Combo options are documented per node and parameter. Conditional parameters say which controlling option makes them visible. The handler remains authoritative for validation and execution requirements; an empty text field is not described as required unless its active handler enforces that rule.

Sensitive parameters are never read to construct help. Context includes only the stable node type, parameter ID, and public selection codes; it excludes typed values, secrets, process payloads, result text, and file paths. Process files omit transient secret values. Reopening a process can therefore require entering those values again.

## Validate, simulate, and execute

Connections must use representations accepted by the target input port; required ports must be connected, and multi-input nodes require the correct target port. Preflight reports incomplete configuration, incompatible links, and missing required parameters before execution. Dry run validates and shows the plan without executing node handlers. Run executes the validated graph in dependency order, reports per-node progress/results, and supports cancellation. A conversion-to-hash flow is valid when the conversion output representation is accepted by the hash input. Save/open serializes the process graph and configuration supported by the active codec; it does not make transient secrets part of contextual help.
