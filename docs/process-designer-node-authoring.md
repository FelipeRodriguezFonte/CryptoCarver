# Process Designer — Node Authoring Guide (Phase 5B Contract)

This guide documents how to author and register new process node types within CryptoCarver using the declarative descriptor model introduced in Phase 5A.

---

## Architectural Principles

1. **Declarative Specification**: Node metadata (category, labels, icons, parameters, visibility rules) is declared via `NodeDescriptor` and `NodeParameter`.
2. **Zero Inspector Wiring**: Controllers and UI inspectors automatically render parameter controls based on descriptors. No manual `@FXML` fields or conditional branches in `select()` or `saveSelectedNodeSettings()` are needed.
3. **Automatic Secret Shielding**: Parameters marked with `ParameterKind.PASSWORD` or `sensitive = true` are rendered masked and **never** serialized to `.cfprocess.json`.
4. **Unified Catalog**: Node types registered with `ProcessEngine` automatically populate the searchable palette, default parameter generators, serialization allowlists, and execution pipelines.

---

## Step-by-Step Implementation Guide

### Step 1: Define Supported Node Types

In your handler implementing `ProcessNodeHandler`, declare the node type identifiers:

```java
public final class MyCustomNodeHandler implements ProcessNodeHandler {

    private static final String TYPE_CUSTOM = "MY_CUSTOM_NODE";

    @Override
    public Set<String> supportedTypes() {
        return Set.of(TYPE_CUSTOM);
    }
```

### Step 2: Implement Port Definitions and Representations

Declare the input and output port contracts:

```java
    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return List.of(
            new PortDefinition("input", Set.of(Representation.TEXT, Representation.BINARY), true),
            new PortDefinition("salt", Set.of(Representation.HEX, Representation.BINARY), false)
        );
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.BINARY;
    }
```

### Step 3: Declare Node Descriptors & Parameters

Define parameter schemas with appropriate `ParameterKind`, default values, options, and conditional visibility expressions:

```java
    @Override
    public List<NodeDescriptor> descriptors() {
        return List.of(
            new NodeDescriptor(
                TYPE_CUSTOM,
                "Crypto / Custom",
                "module.process.type.custom",
                "module.process.desc.custom",
                "⚙",
                List.of(
                    new NodeParameter(
                        "mode",
                        "module.process.param.mode",
                        ParameterKind.COMBO,
                        List.of("FAST", "SECURE"),
                        "FAST"
                    ),
                    new NodeParameter(
                        "iterations",
                        "module.process.param.iterations",
                        ParameterKind.NUMBER,
                        List.of(),
                        "10000",
                        "mode == 'SECURE'" // Only shown when mode is SECURE
                    ),
                    new NodeParameter(
                        "secretKey",
                        "module.process.param.secretKey",
                        ParameterKind.PASSWORD,
                        List.of(),
                        "",
                        null,
                        true // Sensitive: never written to disk
                    )
                )
            )
        );
    }
```

### Step 4: Implement Execution and Validation

Implement `execute` and `validate`:

```java
    @Override
    public NodeExecutionResult execute(ProcessExecutionContext context) throws Exception {
        byte[] input = context.requireSingleInput();
        String mode = context.config("mode", "FAST");
        // Execute cryptographic / processing logic
        byte[] output = process(input, mode);
        return NodeExecutionResult.success(output, Representation.BINARY);
    }

    @Override
    public ValidationResult validate(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        if (!inputs.containsKey("input")) {
            return ValidationResult.error("Missing required input port");
        }
        return ValidationResult.valid();
    }
}
```

### Step 5: Register Handler in `ProcessEngine`

Add your handler instance to the static registration list in `ProcessEngine.java`:

```java
static {
    registerHandler(new MyCustomNodeHandler());
}
```

### Step 6: Add Internationalization Keys

Add localized strings to both `messages.properties` and `messages_es.properties`:

```properties
# English
module.process.type.custom=Custom Processor
module.process.desc.custom=Processes data with customizable security parameters.
module.process.param.mode=Processing Mode
module.process.param.iterations=Iterations
module.process.param.secretKey=Secret Key

# Spanish
module.process.type.custom=Procesador Personalizado
module.process.desc.custom=Procesa datos con parámetros de seguridad personalizables.
module.process.param.mode=Modo de procesamiento
module.process.param.iterations=Iteraciones
module.process.param.secretKey=Clave secreta
```

---

## Parameter Kinds Reference

| ParameterKind | Control Rendered | Serialized to Disk | Example Use Cases |
| :--- | :--- | :---: | :--- |
| `TEXT` | Single-line TextField | Yes | Names, labels, IDs |
| `MULTILINE` | TextArea (scrollable) | Yes | Direct text input, certificates |
| `NUMBER` | TextField (numeric filter) | Yes | Key length, iterations, tag bits |
| `HEX` | Monospace TextField | Yes | Nonces, IVs, salt, manual keys |
| `COMBO` | ComboBox with dropdown | Yes | Algorithms, modes, encodings |
| `CHECKBOX` | CheckBox | Yes | Feature flags, padding toggles |
| `PASSWORD` | PasswordField (masked) | **NO** | Passphrases, private keys, secrets |
| `FILE_OPEN` | File path + Browse button | Yes | Input files, keystores |
| `FILE_SAVE` | File path + Browse button | Yes | Output file destinations |

---

## Parameter Visibility Rules & Conditional Expressions

Parameters can declare a conditional visibility rule using the `visibleWhen` expression:

- **Exact value match**: `"paramKey == 'SPECIFIC_VALUE'"`
- **Negative match**: `"paramKey != 'SPECIFIC_VALUE'"`
- **Boolean toggle**: `"enableFeature == 'true'"` or `"!legacyMode"`
- **Multiple conditions**: `"type == 'AES' || type == 'CHACHA'"`

The `NodeInspectorRenderer` automatically installs reactive change listeners on all dependency controls. When the source control's value changes, dependent inspector groups (`VBox`) automatically toggle their `visible` and `managed` properties, keeping the inspector clean and responsive without bespoke controller wiring.

### Programmatic Inspector Access

Tests and automation components can access dynamic inspector controls directly through the `ProcessDesignerController` API without brittle DOM lookups:

```java
// Retrieve the concrete JavaFX Control (TextField, ComboBox, CheckBox, etc.)
Control control = controller.getInspectorControl("algorithm");

// Retrieve the enclosing VBox group (containing label + control + validation hint)
VBox group = controller.getInspectorGroup("keyLength");
```

---

## Sensitive Parameters & Transient Secrets

- Any parameter configured with `ParameterKind.PASSWORD` or flagged `sensitive = true` is classified as sensitive.
- **Strict In-Memory Separation**: Sensitive parameters are **never written into `node.configuration`** within the persistent process model, live canvas node structures, or undo/redo snapshot stacks.
- **Inspector Isolation**: When saving node settings from the inspector (`NodeInspectorRenderer.save`), sensitive values are stored exclusively in a private session secrets map (`Map<String, char[]>`) and systematically removed from `node.configuration`.
- **Ephemeral Execution Injection**: During runtime execution (`handleRunProcess`, `handleDryRunProcess`), an ephemeral snapshot copy of the definition is constructed, secrets are temporarily injected solely for the duration of execution, and the copy is discarded/sanitized immediately upon completion.
- **Export & Serialization Protection**: On file export or serialization (`ProcessDefinitionCodec.serialize`), all sensitive keys (collected dynamically via `NodeCatalog.allSensitiveKeys()`) are stripped, ensuring exported process definitions never contain secret key material.

