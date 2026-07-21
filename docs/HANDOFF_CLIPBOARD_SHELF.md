# Handoff: Clipboard Shelf

## Overview
The Clipboard Shelf is an in-memory storage area designed to temporarily hold strings (e.g. keys, hashes, JSON, ciphertext) to facilitate moving data between tools within CryptoCarver. It replaces the need for the user to rely constantly on the OS clipboard.

## Architecture and Integration
- **Model (`ClipboardEntry`, `ClipboardShelfManager`)**: Pure Java models representing the data structure. `ClipboardEntry` is completely immutable, and `ClipboardShelfManager` is a thread-safe singleton providing a fixed-size FIFO queue (max 100 elements). The models use `OperationDetail.Classification` to maintain data visibility policies (e.g., masking sensitive data based on Lab settings).
- **Format Inference (`ClipboardEntry.Format.inferFormat`)**: Uses regex and strict tools like `Gson` to detect PEM, JSON, HEX, BASE64, and BASE64URL (JWT payloads). If no specific format is matched, it falls back to `TEXT`.
- **UI Component (`clipboard_shelf.fxml`, `ClipboardShelfController`)**: A dedicated view built using a JavaFX `TableView`. It enforces visibility policies: `REDACTED` and `MASKED` items are protected from copying or usage. Format validation prevents incompatible injections (e.g., injecting raw JSON into a Hex-only field).
- **Hub (`ModernMainController` y Destinos Especializados)**: 
  - The shelf is included directly in `main-view-modern.fxml` via `<fx:include fx:id="clipboardShelf" />`. 
  - It is listed with the general tools, alongside Hashing and Manual Conversion, because it is intentionally reusable across every module.
  - `ModernMainController` acts as the `OperationNavigator` and serves as the publisher for `ClipboardShelfManager`, recording history purely as metadata to prevent leaking sensitive values.
  - `createResultContextMenu` intercepts right-clicks on result `TextArea`s. It attempts to map copied text to its corresponding `Classification` via the last published snapshot. It also includes special handling for AES-GCM to concatenate ciphertext and authentication tag if both are present.
  - `fillClipboardTarget` serves as a dispatcher for the "Use in..." feature, routing the copied data to the appropriate Controller (e.g. `GenericController.fillManualConversionInput`, `CipherController.fillSymmetricCipherInput`) and unfolding the necessary JavaFX panes. These specialized controllers ensure that the JavaFX `ComboBox` (format selector) is correctly synchronized with the injected format, and display a warning if the requested format is unsupported by the destination.

## Limitations
- **In-Memory Only**: Entries are intentionally *not* persisted to disk to avoid storing sensitive material outside the current session. All clipboard entries are lost when the application is closed.
- **Max Elements**: The shelf is bounded to 100 elements. Adding the 101st element will silently evict the oldest entry.

## Future Enhancements
- Expand the heuristic for format inference (e.g., detecting `BINARY_DESCRIPTION` directly).
- Create additional targets for "Use in..." such as RSA Key Import or Signature verification.
- Enable drag-and-drop support from the shelf into text areas.
