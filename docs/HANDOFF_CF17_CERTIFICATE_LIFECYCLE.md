# HANDOFF CF-17: Certificate Lifecycle Lab

## Scope and Objectives
The objective of CF-17 was to implement an offline Certificate Lifecycle Laboratory to allow users to simulate a complete Public Key Infrastructure (PKI) lifecycle. The required flows included:
1. **CSR Generation (PKCS#10):** From local PEM keys or directly from hardware/software tokens (PKCS#11) without extracting the private key.
2. **CA Issuance:** A Local Laboratory Root CA that signs incoming CSRs, assigning a random serial, applying validity constraints, copying Subject Alternative Names (SANs) from the request, and enforcing `KeyUsage`/`ExtendedKeyUsage`.
3. **Chain Validation:** An offline PKIX verification mechanism that verifies paths (Root -> Intermediate -> End-Entity) without network lookups.

## Implementation Details

### PKCS#11 CSR Generation
- Enhanced `Pkcs11Session.java` by exposing an opaque private key handle via `getOpaquePrivateKey`. This handle never exports the key material.
- Added `generateCSRWithPkcs11` in `CertificateGenerator.java`, utilizing BouncyCastle's `JcaContentSignerBuilder` configured with the PKCS#11 session provider. This delegates all cryptographic signing for the PKCS#10 request directly to the token.

### UI Integration (KeysController.java)
- **Generate CSR:** The "Generate CSR" button now supports branching. Users can select "Local PEM (Parse Area)" to generate a CSR from a previously loaded key, or "PKCS#11 Active Alias" to generate the CSR using the currently authenticated token object. Both routes ensure that private key material remains secure, explicitly categorizing generated material as `SECRET` in the `OperationResult` pipeline.
- **Issue Certificate:** The CA issuance flow (using the existing `CertificateAuthorityOperations.issueFromCsr`) was bound to the `OperationResult` publisher. It correctly identifies the output as `PUBLIC`, bypassing `SECRET` restrictions, which prevents the Shelf from blocking valid certificate exports.
- **Chain Validation:** Upgraded `handleValidateCertificate` to support parsing multiple certificates. It utilizes `CertificateGenerator.validateCertificateChain` (which now correctly traverses chains and validates signatures, usages, and constraints sequentially).

### Key Certificate Workbench
- The Workbench remains strictly an inspector and converter. The issuance and validation were intentionally separated to `KeysController` to preserve the Workbench's integrity as an analysis tool rather than an active certificate factory. Certificates and chains created in the Lab can be routed into the Workbench via the Shelf.

## Security Controls
All outputs (CSRs, Certificates, and validation reports) flow through `OperationResult.forOperation(...)` with explicit classifications (`PUBLIC` or `SECRET`). 
- Generated laboratory private keys are marked as `SECRET`.
- Certificates and Validation results are marked as `PUBLIC`.
- This ensures full compliance with the `FULL_LAB`, `MASKED`, and `REDACTED` global visibility states.

## Testing and Validation
- Created `CertificateLifecycleLabTest.java` verifying complete offline lifecycle (Generation -> CSR -> CA Issuance -> Validation) for both RSA and ECDSA keys.
- Executed `mvn test` validating all tests.
- Verified FXML integrity with `xmllint`.

## Pending/Future Enhancements
- Integration of a visual CSR inspector widget in the UI (currently handled textually).
- Support for generating CSRs with hardware-bound Ed25519 keys (pending PKCS#11 library support for EdDSA).
