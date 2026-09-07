# Process Designer Architecture

## Overview
The Process Designer module enables users to construct and execute complex cryptographic and data conversion workflows via a node-based interface. This document outlines the core architectural components, the node SPI, and the descriptor-driven UI model.

## Representation Model
To guarantee that data flows correctly between nodes, the engine uses a strong representation model (`Representation` enum). Every connection is validated before execution.
- **BINARY**: Raw byte array (`byte[]`). Used for standard crypto payloads.
- **TEXT_UTF8**: Raw string representation.
- **BASE64**: Base64 encoded text.
- **BASE64URL**: Base64URL encoded text.
- **HEX**: Hexadecimal encoded string.
- **HEX_COMPONENTS**: Colon-delimited hexadecimal component bundle. This is a
  private process representation accepted only by `KEY_COMBINE_XOR.components`
  and `COMPONENT_SELECT.components`; it is not a generic HEX value.
- **EBCDIC**: EBCDIC encoded string.

Nodes negotiate their representations via the `ProcessNodeHandler` SPI.

## ProcessNodeHandler SPI
The `ProcessNodeHandler` is the engine SPI for all node implementations. Multi-port negotiation and declarative descriptors are first-class constructs:

```java
public interface ProcessNodeHandler {
    Set<String> supportedTypes();
    List<PortDefinition> inputPorts(ProcessDefinition.Node node);
    Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs);
    FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception;
    default List<NodeDescriptor> descriptors() { return List.of(); }
}
```

Each input port specifies its accepted representations and whether connection is required:
```java
public record PortDefinition(String name, Set<Representation> acceptedRepresentations, boolean required) {}
```

During the preflight validation phase, `ProcessEngine.validate(ProcessDefinition)` computes a topological sort, detects cycles, and ensures every input port connects to a node yielding an accepted `Representation`.

## Declarative Parameter & Descriptor Model (Phase 5A)

### NodeCatalog
`NodeCatalog` serves as the single source of truth for the entire workbench:
- Collects `NodeDescriptor` instances from all registered `ProcessNodeHandler` implementations.
- Powers the searchable palette with categorical grouping, icons, and localized labels.
- Provides default configuration maps when nodes are instantiated.
- Exposes all sensitive parameter keys across the application to prevent secret persistence.

### NodeInspectorRenderer
Replaces hardcoded inspector form groups with dynamic JavaFX form rendering:
- Iterates over `NodeDescriptor.parameters()`.
- Generates controls matching `ParameterKind` (`TEXT`, `MULTILINE`, `NUMBER`, `HEX`, `COMBO`, `CHECKBOX`, `PASSWORD`, `FILE_OPEN`, `FILE_SAVE`).
- Evaluates conditional visibility expressions (e.g., `mode == 'CUSTOM'`).
- Binds values dynamically to `node.configuration`.

### Non-Persistence of Sensitive Parameters
- Sensitive parameters (`ParameterKind.PASSWORD` or `sensitive = true`) are masked with `PasswordField`.
- Secrets are stored exclusively in transient memory (`Map<String, Map<String, String>> transientSecrets`) during session execution.
- `ProcessDefinitionCodec` strips all sensitive keys on serialization and ignores them on deserialization.

## Workbench Canvas & UI Architecture

### Infinite Scalable Canvas
- **Zoom**: Transform scale from 0.25x (25%) to 4.0x (400%) with zoom-to-fit and zoom reset.
- **Pan & Scroll**: Infinite scrollable workspace that expands dynamically when nodes are positioned at large coordinates.
- **Port Drag Curves**: Interactive bezier curves connecting nodes smoothly, rejecting incompatible port connections with localized feedback.
- **Undo / Redo**: Command snapshot stack supporting up to 60 historical operations.
- **Detached Window**: Dedicated `ProcessDesignerWindow` stage allowing the designer to be opened in a separate window without state duplication.

## File Security Policies
File I/O operations are strictly governed by `FileWritePolicy` within the `ExecutionContext`.
- **FAIL_IF_EXISTS**: (Default) Refuses to overwrite existing files. This prevents accidental data loss during automated bulk runs.
- **ALLOW_OVERWRITE**: Prompts the user in the UI, and only allows overwriting if explicitly confirmed.

To prevent partial writes or corruption upon failure, file writes must be performed atomically (e.g., using a temporary file and `StandardCopyOption.ATOMIC_MOVE`).

## Observability and Node State
Nodes report real-time execution states (`PENDING`, `RUNNING`, `SUCCESS`, `ERROR`, `SKIPPED`) using `NodeExecutionEvent`. The event contract tracks representations and payload sizes securely:
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

## Node Families (Ola 5B.1 Additions)

Phase 5B.1 incorporates three fundamental families into the Process Designer catalog without branching the UI controller:
1. **Plumbing (`PlumbingNodeHandler`)**: Low-level stream manipulation (`CONCAT`, `SLICE`, `PAD`, `UNPAD`, `XOR`) and workflow verification (`ASSERT_EQUALS`).
2. **Conversions & Formats (`EncodingFormatNodeHandler`)**: Extended representations wrapping `CodecRegistry`, `EBCDICConverter`, `CompressionCodec`, and `Charset` (`BASE32_*`, `BASE58_*`, `BASE58CHECK_*`, `EBCDIC_*`, `COMPRESS`, `DECOMPRESS`, `CHARSET_CONVERT`).
3. **Utilities & Inspection (`UtilityInspectionNodeHandler`)**: Structural inspection and math wrapping `ASN1Parser`, `CheckDigitCalculator`, `ModularArithmetic`, `UUIDGenerator`, and `ByteStatistics` (`ASN1_DECODE`, `CHECK_DIGIT_CALC`, `CHECK_DIGIT_VERIFY`, `MODULAR_ARITHMETIC`, `UUID_GENERATE`, `BYTE_STATISTICS`).

4. **Key Operations (`KeyOperationsNodeHandler`, Phase 5B.2a)**: KCV and parity,
   XOR key sharing, HKDF/SP 800-108/X9.63/scrypt/Argon2 derivation, AES RFC
   3394/5649 wrapping, TR-31, read-only ICSF token parsing, asymmetric key-pair
   generation and key-material inspection. The handler delegates to the existing
   crypto facades and keeps sensitive key inputs in transient inspector state.

5. **Payment Operations (`PaymentOperationsNodeHandler`, Phase 5B.2b)**: PIN
   blocks, CVV/dCVV, PVV, IBM 3624, TDES/AES DUKPT, EMV derivation and
   cryptograms, EMV TLV summaries, and Track 2. PAN/PIN fields use text ports so
   their decimal contract is explicit; configured PANs are checked with the
   existing Luhn facade and sensitive material remains transient.
