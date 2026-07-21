# Handoff: CF-15A - SoftHSM PKCS#11 Integration Test

## Objective
Implement a real integration test (`SoftHsmIntegrationTest`) that delegates cryptographic operations to SoftHSM via the `SunPKCS11` provider without exposing opaque private keys, maintaining PIN safety and leaving no residual configuration files.

## Achieved
1. **Conditional Execution**: 
   - The test gracefully skips execution if the environment variables (`SOFTHSM2_MODULE`, `SOFTHSM2_CONF`, `CRYPTOCARVER_SOFTHSM_PIN`) are missing. It also requires the configured private-key/certificate alias, so it never accidentally exercises an unrelated token object.
2. **Operations Validated**:
   - **Enumeration**: Lists PKCS#11 objects and locates the first RSA key with a certificate.
   - **Sign/Verify**: Generates a standard `SHA256withRSA` signature via HSM and verifies it both using the HSM and a standard `SunRsaSign` Java provider for interoperability proof.
   - **CMS/PKCS#7**: Delegates CMS signature generation (`Pkcs11Session.signCms()`) to the token. Validates the resulting `CMSSignedData` structure via BouncyCastle.
   - **JWS**: Uses `Pkcs11JwsSigner` to generate a compact JWS. Validates it using the Nimbus `RSASSAVerifier` and the extracted token public certificate.
3. **Security & Cleanup**:
   - The transient PIN array is cleared in-memory immediately after session opening.
   - No PINs are logged or exposed.
   - `Pkcs11Session.close()` successfully releases resources.
4. **Diagnostics**:
   - The test outputs non-invasive diagnostic information (provider name, lib path, available JCA signature algorithms) when run successfully.

## How to Run the Test Locally
Follow the guide in `docs/PKCS11_SOFTHSM_QUICKSTART.md` to initialize a SoftHSM token with the alias `cryptocarver-rsa`.

Once initialized, export the required variables and run the test:

```bash
# Example for macOS with Homebrew SoftHSM
export SOFTHSM2_CONF="$HOME/.cryptocarver/softhsm/softhsm2.conf"
export SOFTHSM2_MODULE="$(find "$(brew --prefix softhsm)" -name 'libsofthsm2.*' -type f | head -1)"
export CRYPTOCARVER_SOFTHSM_PIN="123456"
export CRYPTOCARVER_SOFTHSM_ALIAS="cryptocarver-rsa" # optional; this is the default
export CRYPTOCARVER_SOFTHSM_SLOT_INDEX="0"           # optional; this is the default

# Run only the integration test
mvn test -Dtest=SoftHsmIntegrationTest
```

If any variable is omitted, the Maven run will cleanly skip the test:
`[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 1`

## Limitations & Next Steps
- **XAdES**: Not yet integrated with PKCS#11. The current test focuses on core JCA signatures, JWS, and CMS. Future tickets should bridge `Pkcs11Session` with the XAdES module.
- **Symmetric Keys**: The test deliberately covers only an RSA key with an associated X.509 certificate. Expanding it to validate MAC/encryption requires a separately provisioned AES/DES key and mechanism checks.
- **Temporary provider configuration**: `Pkcs11Session.close()` deletes its credential-free temporary SunPKCS11 configuration. CF-15A verifies functional cleanup by closing the session; a future test-only session probe could assert the file lifecycle directly without widening the production API.

## Codex review adjustment

The initial handoff selected the first certificate exposed by a token. That was unsafe and made the result non-deterministic when a developer had more than one object. The integration test now uses `CRYPTOCARVER_SOFTHSM_ALIAS` (default: `cryptocarver-rsa`) and checks that this exact alias exposes both a private key and its associated X.509 certificate. The quick-start was updated accordingly: a bare `pkcs11-tool --keypairgen` key is not enough for the CMS/JWS part of this test; `keytool -genkeypair -storetype PKCS11` creates the required key-and-certificate entry.
