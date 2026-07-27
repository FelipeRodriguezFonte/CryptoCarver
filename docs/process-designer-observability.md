# Process Designer Observability

The Phase 3.5 observability implementation brings rich traceability and debugging features to the Process Designer.

## Execution Tracing
Every time a workflow executes, a highly detailed execution trace is generated. The system securely captures:
- Node ordinal and identifier
- Semantic representation of data flowing in and out (e.g. `HEX`, `BINARY`)
- Data sizes (character counts for text/hex/base64, byte counts for binary)
- Execution time
- Redacted/Safe representations of the outputs.

### Security Guarantees
- Raw Keying material (Passwords, keystore paths) are NEVER logged.
- The `CONSOLE_OUTPUT` automatically truncates values larger than 256 bytes to prevent UI thread lockups and out-of-memory errors.
- `BINARY` inputs and outputs are tracked exclusively by size and semantic label; their raw bytes are not stringified into the console.

## The Execution Panel
The observability UI is split into two visual components:

### Execution Table
A `TableView` that dynamically populates as `NodeExecutionEvents` are emitted:
- `#`: The sequence number.
- `Step`: Node label.
- `Operation`: Node type.
- `Input`: Representation and Size (e.g., `BINARY (48)`).
- `Output`: Representation and Size.
- `Status`: `SUCCESS`, `ERROR`, etc.
- `Duration`: Milliseconds.

### Console Log
A plain-text pane showing a formatted CLI-style execution log.
```
Starting execution...

[1] FILE INPUT
    input:  -
    output: BINARY, 148 bytes

[2] HASH
    input:  BINARY, 148 bytes
    output: BINARY, 32 bytes

[3] HEX ENCODE
    input:  BINARY, 32 bytes
    output: HEX, 64 characters

[4] CONSOLE OUTPUT
    input:  HEX, 64 characters
    output: HEX, 64 characters

Process completed successfully.

[Console Output 'Result']
value (HEX): a4f8e3212b...
```

## Internal Engine Tracking
The `NodeExecutionEvent` contains specific fields to capture input/output metadata. The `ProcessEngine` calculates the primary input and the resulting output at each step and propagates it.

`NodeExecutionEvent` schema includes:
```java
    int step,
    String nodeLabel,
    String nodeType,
    NodeExecutionState state,
    Duration duration,
    Representation inputRepresentation,
    int inputSize,
    Representation outputRepresentation,
    int outputSize,
    String safeMessage
```
