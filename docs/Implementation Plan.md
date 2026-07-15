New Features Implementation Plan: Post-Quantum & XML Security (with SD-DSS)
This plan outlines the steps to add Post-Quantum Cryptography (PQC) and XML Security (XMLDSig/XAdES) to CryptoCarver.

Goal Description
Evolve CryptoCarver by adding support for:

Post-Quantum Cryptography: Support for NIST PQC standardized algorithms (Kyber for key encapsulation, Dilithium for signatures) using Bouncy Castle.
XML Security (XAdES/XMLDSig): Support for XML Digital Signatures and XAdES using the European Commission's SD-DSS library (Digital Signature Services), ensuring full standard compliance.
User Review Required
IMPORTANT

Dependencies:

Post-Quantum: Using existing Bouncy Castle 1.78.1.
XML Security: Adding eu.europa.ec.joinup.sd-dss (SD-DSS) version 6.3 (Stable).
Modules: dss-xades, dss-utils-apache-commons, dss-token, dss-service.
Note: SD-DSS 6.x uses jakarta.* namespace. We will ensure compatibility.
Proposed Changes
1. Dependencies (
pom.xml
)
[NEW] Add SD-DSS dependencies (v6.3).
dss-xades: Core XAdES functionality.
dss-utils-apache-commons: Utils implementation.
dss-token-pkcs12 / dss-token-jks: For loading keys.
2. Post-Quantum Cryptography Module
[NEW] src/main/java/com/cryptoforge/crypto/PostQuantumOperations.java
Key Generation:
ML-KEM (Kyber): Kyber512, Kyber768, Kyber1024.
ML-DSA (Dilithium): Dilithium2, Dilithium3, Dilithium5.
SLH-DSA (SPHINCS+): SHA2-128f, etc.
Operations:
Sign/Verify: Using Dilithium / SPHINCS+.
KEM: Encapsulate/Decapsulate.
[NEW] src/main/java/com/cryptoforge/ui/PostQuantumView.java
UI Tabs: "Key Gen", "Sign/Verify", "KEM".
Clean, modern UI inputs.
3. XML Security Module (SD-DSS)
[NEW] src/main/java/com/cryptoforge/crypto/XMLSignatureOperations.java
XAdESService:
Initialize XAdESService with a certificate verifier (CommonCertificateVerifier).
Sign:
Load XML document (DSSDocument).
Load Key (Pkcs12SignatureToken or JKSSignatureToken).
Set parameters (SignatureLevel.XAdES_BASELINE_B, DigestAlgorithm, etc.).
Generate signature.
Extend: extend to T/LT/LTA (if time allows, focusing on B first).
Verify: Validate XAdES signatures using SignedDocumentValidator.
[NEW] src/main/java/com/cryptoforge/ui/XMLSignatureView.java
Sign Tab:
Select File to Sign.
Select KeyStore (P12/JKS) & Password.
Select Signature Level (XAdES-B, etc.).
"Sign" -> Save Result.
Verify Tab:
Select Signed XML.
Show Validation Report (Simple view: Valid/Invalid + checks).
4. UI Integration
SidePanel: Add "Post-Quantum" and "XML Security" (European Standard).
NavigationRail: Add icons.
Verification Plan
Automated Tests
JUnit tests for PostQuantumOperations.
JUnit tests for XMLSignatureOperations using sample P12 keys.
Manual Verification
PQC: KeyGen -> Sign -> Verify workflow.
XML: Sign a file with XAdES-BASELINE-B. Verify it with the tool. Verify it with external online XAdES verifiers if possible.