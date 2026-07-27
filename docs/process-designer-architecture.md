# Process Designer Architecture

## Overview
The Process Designer module enables users to construct and execute complex cryptographic and data conversion workflows via a node-based interface. This document outlines the core architectural components introduced in Phase 2.

## Representation Model
To guarantee that data flows correctly between nodes, the engine uses a strong representation model (`Representation` enum). Every connection is validated before execution.
- **BINARY**: Raw byte array (`byte[]`). Used for standard crypto payloads.
- **TEXT_UTF8**: Raw string representation.
- **BASE64**: Base64 encoded text.
- **BASE64URL**: Base64URL encoded text.
- **HEX**: Hexadecimal encoded string.
- **EBCDIC**: EBCDIC encoded string.

Nodes negotiate their representations via the `ProcessNodeHandler` SPI.

## ProcessNodeHandler SPI
The `ProcessNodeHandler` is the extension point for all nodes.
```java
public interface ProcessNodeHandler {
    Set<String> supportedTypes();
    Set<Representation> acceptedInputs(ProcessDefinition.Node node);
    Representation outputRepresentation(ProcessDefinition.Node node, Representation input);
    FlowValue execute(ProcessDefinition.Node node, FlowValue input, ExecutionContext context) throws Exception;
}
```
During the validation phase, `ProcessEngine.validate(ProcessDefinition)` computes a topological sort, detects cycles, and ensures `acceptedInputs` matches the `outputRepresentation` from the source node.

## File Security Policies
File I/O operations are strictly governed by `FileWritePolicy` within the `ExecutionContext`.
- **FAIL_IF_EXISTS**: (Default) Refuses to overwrite existing files. This prevents accidental data loss during automated bulk runs.
- **ALLOW_OVERWRITE**: Prompts the user in the UI, and only allows overwriting if explicitly confirmed.

To prevent partial writes or corruption upon failure, file writes must be performed atomically (e.g., using a temporary file and `StandardCopyOption.ATOMIC_MOVE`).

## Observability and Node State
Nodes report real-time execution states (`PENDING`, `RUNNING`, `SUCCESS`, `ERROR`, `SKIPPED`) using `NodeExecutionEvent`. The UI listens to these events via the `ExecutionContext` to provide precise feedback on the progress of the workflow.

## Secure Extension for Crypto & Signatures
The architecture natively scales to support Advanced Cryptography (Encrypt, Decrypt, Sign, Verify, MAC). Extensions for WSS or XML Security should follow these guidelines:
- **Key Material**: Never persist or log sensitive key material. Inputs representing keys or passwords should only exist in-memory (`char[]` or `byte[]`) and must be cleared when no longer needed.
- **Representation Compliance**: Cryptographic nodes typically require `BINARY` inputs and yield `BINARY` outputs, which must be routed through explicit formatting nodes (e.g., Base64 Encode) for presentation.

## Execution Events and Observability (Phase 3.5)
The engine emits detailed telemetry via `NodeExecutionEvent`. The event contract tracks representations and payload sizes securely:
```java
public record NodeExecutionEvent(
    String nodeId,
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
) {}
```
See `docs/process-designer-observability.md` for details on secure tracing and UI integration.
