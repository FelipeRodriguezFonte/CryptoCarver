# WSS-Security interoperability

This document records the reproducible interoperability contract for the standalone WSS-Security laboratory.

## Engines and dependency boundary

SOAP signature verification and creation use the independent JSR-105 implementation. Apache WSS4J 3.0.5 is also used as the external interoperability oracle for signatures and UsernameToken.

SOAP XML Encryption uses a deliberately narrow WSS4J production adapter because JSR-105 has no XML Encryption API. This is the newest compatible 3.x release and shares the Santuario 3.x binary line required by DSS 6.3; WSS4J 4.x is deliberately excluded because its Santuario 4.x dependency is binary-incompatible with DSS 6.3.

## Automated matrix

| Producer | Consumer | SOAP | Signature | Timestamp | Expected |
|---|---|---|---|---|---|
| CryptoCarver | Apache WSS4J | 1.1 | RSA-SHA256 | Signed | Accepted; SIGN and TS actions reported |
| CryptoCarver | Apache WSS4J | 1.2 | RSA-SHA512 | None | Accepted; SIGN action reported |
| CryptoCarver, tampered Body | Apache WSS4J | 1.1 | RSA-SHA256 | Signed | Rejected |
| Apache WSS4J | CryptoCarver | 1.1 | RSA-SHA256 | None | Accepted |
| CryptoCarver | Apache WSS4J | 1.1 | UsernameToken PasswordDigest | Nonce + Created | Accepted; UT action reported |
| Apache WSS4J | CryptoCarver | 1.1 | UsernameToken PasswordDigest | Nonce + Created | Accepted |
| CryptoCarver/WSS4J adapter | CryptoCarver/WSS4J engine | 1.1/1.2 | AES-128/256-GCM | RSA-OAEP SHA-256 | Round-trip accepted |
| CryptoCarver/WSS4J adapter | CryptoCarver/WSS4J engine | 1.1/1.2 | AES-128/256-CBC | RSA-OAEP SHA-256 | Round-trip accepted; unauthenticated warning |

Run the matrix with:

```bash
mvn -Dtest=Wss4jInteropTest test
```

The test uses these versioned repository fixtures:

- `src/test/resources/soap_test.xml`
- `src/test/resources/testks.p12`

The test keystore password is intentionally public test material (`storepass`) and must never be reused outside tests.

## BSP details enforced

- `BinarySecurityToken/@EncodingType` uses the SOAP Message Security 1.0 `#Base64Binary` URI.
- X.509 v3 `ValueType` uses the X.509 Token Profile 1.0 `#X509v3` URI.
- `SecurityTokenReference` resolves a local fragment to one unique token.
- The signature covers the direct SOAP Body and may additionally cover the declared Timestamp.
- SHA-1, external references, duplicate references and unexpected targets are rejected.

UsernameToken `PasswordDigest` necessarily uses the SHA-1 formula defined by UsernameToken Profile 1.0 (`nonce || Created || password`). This does not enable SHA-1 as an XML signature algorithm. `PasswordText` is intentionally available for protocol diagnostics, but exposes the password in the SOAP message and therefore requires authenticated TLS.

SOAP encryption uses the WSS4J 3.0.5 adapter in production because JSR-105 has no XML Encryption equivalent. The adapter is constrained by a CryptoCarver allowlist: AES-128/256 GCM or CBC for Body content and RSA-OAEP SHA-256 or the legacy RSA-OAEP SHA-1 profile for key transport. RSA v1.5 and undeclared data algorithms are rejected before private-key processing.

The Process Designer exposes SOAP Body signing, signature verification, UsernameToken creation/verification and Body encryption/decryption through typed `TEXT_UTF8` nodes. Signing accepts RSA/ECDSA SHA-2 profiles and an optional signed Timestamp; verification may pin a certificate or use the embedded token. Encryption accepts a recipient X.509 certificate and decryption accepts a PKCS#12 or JKS KeyStore. Passwords remain session-only and are stripped from serialized process definitions.

## Trust interpretation

Verification with the embedded certificate proves signature integrity only. Supplying a trusted certificate pins verification to that certificate. Full PKIX path building, revocation and organisational trust policy remain separate concerns.

## Remaining manual check

Status on 26 July 2026: **pending because SoapUI/ReadyAPI is not installed on the validation host**. Do not report this check as passed from the automated WSS4J results alone.

When SoapUI is available, perform and record all of the following:

1. Record the SoapUI/ReadyAPI version, Java runtime and operating system.
2. Create SOAP 1.1 and SOAP 1.2 requests from `src/test/resources/soap_test.xml`.
3. Import `src/test/resources/testks.p12` as an outgoing and incoming WS-Security keystore. The public laboratory password is `storepass`.
4. Verify a CryptoCarver RSA-SHA256 message with signed Timestamp and a RSA-SHA512 message without Timestamp.
5. Generate a SoapUI RSA-SHA256 Body signature and verify it in CryptoCarver.
6. Verify CryptoCarver PasswordDigest UsernameToken, including Nonce and Created; then verify a SoapUI-generated token in CryptoCarver.
7. Decrypt CryptoCarver AES-128-GCM, AES-256-GCM and AES-256-CBC messages transported with RSA-OAEP SHA-256.
8. Encrypt the SOAP Body in SoapUI with AES-256-GCM and RSA-OAEP SHA-256 and decrypt it in CryptoCarver.
9. Repeat one successful signature and encryption case with SOAP 1.2.
10. Record negative results for a modified Body, modified GCM ciphertext and expired Timestamp.

For every row capture: producer, consumer, SOAP version, algorithms, profile options, result, error text and a redacted screenshot. This manual UX check complements, but does not replace, the automated WSS4J matrix.
