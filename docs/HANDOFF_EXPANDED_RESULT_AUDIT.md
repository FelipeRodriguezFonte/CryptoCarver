# Expand Result Audit Handoff

## Summary of Findings
The initial "Expand Result" audit identified that the `ModernMainController` heavily relied on UI heuristics (`isContainerVisible`, `getActiveRenderedResultText`, `getInspectorReportText`, etc.) to determine what output to show when the user pressed "Expand Result". This was insecure because it could accidentally expose private keys or raw inputs if the UI focus shifted or if a component retained old text.

## What was implemented
1. **Source of Truth Enforcement**: 
   - We updated `ModernMainController.resolveCurrentOutputText()` to **strictly** rely on the `lastPublishedResultSnapshot` and its formatted text (`lastPublishedResultText`).
   - We completely removed all UI checks. The text resolver now never queries the visible text areas.

2. **Enriched Output Integration (`OperationResult`)**:
   - We introduced `.enrichedOutput(String)` to the `OperationResult` class.
   - For modules that require a formatted output (e.g. **AES-GCM** separating CIPHERTEXT and AUTHENTICATION TAG, or **File Cipher** showing a summary), they now explicitly build this enriched string and attach it to the `OperationResult` during publication.
   - `ModernMainController.formatPublishedResult()` prefers `getEnrichedOutput()` if it is present, and only falls back to formatting raw bytes as UTF-8/Hex if no enriched output is provided.

3. **CipherController Refactoring**:
   - Stream Ciphers (ChaCha20, Salsa20, ChaCha20-Poly1305, XChaCha20-Poly1305) and AES-GCM now explicitly push their formatted outputs into `OperationResult` via `.enrichedOutput()`.
   - File Cipher operations similarly push their text summaries into `.enrichedOutput()`.

## Test Cases Validated (`ExpandResultAuditTest`)
A comprehensive integration test suite has been implemented using JUnit 5 and Java Reflection (to bypass JavaFX threading constraints during tests). The following cases are strictly validated:

| Case | Scenario | Expected Behavior | Status |
|---|---|---|---|
| A | `testA_PublishResultAThenB` | Publish result A, then publish result B. Expand Result is called. | Returns exactly B's output. A's output is completely overwritten, even if A's UI elements were left visible on screen. | ✅ PASS |
| B | `testB_KeyMaterialInspector` | Key Material Inspector runs and publishes its report text. | Expand Result yields the exact report text provided by the inspector snapshot. | ✅ PASS |
| C | `testC_AES_GCM_EnrichedOutput` | AES-GCM encryption completes. | Expand Result returns the enriched output ("=== AES-GCM ENCRYPTION RESULT ===", CIPHERTEXT, and TAG), rather than raw hex bytes. | ✅ PASS |
| D | `testD_ChaCha20_EnrichedOutput` | ChaCha20 / Poly1305 completes. | Expand Result returns its own explicitly formatted output (e.g., splitting out the tag if applicable). | ✅ PASS |
| E | `testE_SecretOperationMasked` | A private key extraction occurs (FULL_LAB vs REDACTED). | Expand Result resolver fetches the correct private key snapshot. Masking/blocking is securely enforced at the `handleExpandResult` level based on `isPrivateOutput()`. | ✅ PASS |
| F | `testF_CopyOutputAddShelfEquivalence` | Comparing the behavior of "Expand", "Copy", and "Add to Shelf". | They all uniformly rely on `resolveCurrentOutputText()`, guaranteeing exact equivalence in what gets exported versus what gets expanded. | ✅ PASS |
