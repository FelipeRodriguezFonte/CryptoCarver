# JOSE capabilities — phase 1 map

Base: `a4ce4e27ec582402e9c33ce51c0a8184751b1067` on `codex/jose-capabilities`.

## Current state

- `JOSEService` routes HMAC, RSA, NIST ECDSA and EdDSA; unsupported JWS algorithms fail closed. Nimbus is pinned at 9.37.3. The module's ECDSA curve lookup currently covers ES256/384/512 only.
- `JoseKeyMaterial` imports RSA/EC/Edwards OKP keys, but its OKP bridge only accepts Ed25519/Ed448. It can parse JWK, PEM and DER, and derives public keys for RSA, EC and Edwards keys.
- `JweComposer.KEY_ALGORITHMS` offers RSA-OAEP-256/384/512, ECDH-ES with AES-KW, AES-KW/GCMKW, PBES2 and `dir`. Its policy rejects RSA1_5 and RSA-OAEP (SHA-1) encryption. The manual CEK recovery path can recover RSA1_5 and RSA-OAEP.
- `JwtValidator` always resolves a normal signer and has no explicit `none` acceptance option. `JOSEController` populates selectors from these lists. EN/ES property bundles and OperationResult public details are available extension points.
- Phase 1 also calls for user-visible, nonblocking warning text in the selector/result for unsafe choices. `JOSEController` is editable; the protected shared result/status components are not.

## Gaps and decisions

1. Implement ES256K (RFC 8812) in a new JOSE-package signer using the existing Bouncy Castle provider for secp256k1 key conversion and raw JOSE ECDSA signatures. Nimbus 9.37.3's normal ECDSA signer rejects secp256k1 (`Curve not supported`), so the custom signer is used for CryptoCarver operations; a BC-backed Nimbus signer/verifier is used as the independent interoperability check. RFC 8812 defines the algorithm but does not supply a fixed signing vector.
2. Add X25519/X448 OKP import/export and ECDH-ES/AES-KW using JDK 17 XDH and the existing JOSE package. The implementation extends Nimbus's ECDH provider for shared Concat KDF and content encryption, and performs XDH with the JDK. Single-recipient Compact/JSON is the intended supported shape; Nimbus multi-recipient `MultiEncrypter` still follows its Tink path and is not extended here.
3. Permit RSA1_5 and RSA-OAEP/SHA-1 in encryption, `none` generation/verification behind an explicit off-by-default validator option, and short HMAC keys. Preserve warning visibility without modal confirmation and avoid exposing key/plaintext material.
4. Update JOSE selectors and localization in the authorized UI/FXML/property locations, while preserving the public `JOSEController` API and avoiding protected shared UI/result files.
5. Add fixed-input crypto tests and a `JoseCapabilitiesCharacterizationUITest` with stable transcript inputs, EN/ES warnings, readable errors and all three visibility profiles. Record pre-implementation test failures below.

## Authorized files expected

Crypto production edits are limited to `JOSEService.java`, `JweComposer.java`, `JoseKeyMaterial.java`, `JwtValidator.java`, `EdDsaJws.java`, `JWEManualCekRecovery.java`, and new `Jose*` classes in that package. Tests, this map, JOSE UI/FXML and message bundles may be updated as required. `pom.xml`, other `crypto/` files, `ModernMainController`, `UiStateSnapshot`, `StatusReporter`, and `OperationResult` are out of scope.

## Pre-implementation test record

The fixed-input capability test was run red before implementation. It exposed: RSA1_5 rejected by `JweComposer.requireStrongRsa`; X25519/X448 rejected in `JoseKeyMaterial.fromOkp` with “Only Ed25519 and Ed448…”; and secp256k1 rejected by Nimbus `ECDSASigner` with `Curve not supported`. The RFC 8037 Appendix A.6/A.7 shared-secret vectors cover X25519/X448. After implementation, both curves also pass ECDH-ES and A128/192/256KW round trips, JWK/PEM conversion, and the supported JWE forms. ES256K signs and verifies with the custom JOSE implementation and passes independent Nimbus interoperability with BC.

## Verification record

- Crypto regression command: `mvn -o -q -Plow-cpu '-Dtest=JoseCapabilitiesTest,JweComposerTest,EdDsaJwsTest,JwtValidatorTest' test` — passed.
- UI characterization command: `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest=JoseCapabilitiesCharacterizationUITest test` — passed, 1 test, 0 failures/errors, with transcript digest `008d518b350ae7eb0670ff6b21f9696c1e0a28d67b6a742cfb79bb2c7b88a28a`.
- The transcript captures English/Spanish unsafe-option labels and public result warnings under FULL_LAB, MASKED and REDACTED policies; it also checks classification/readability and excludes the synthetic secret from restricted profiles.

Phase 1 Maven gates passed sequentially on 2026-10-05:

| Command | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `mvn -o -q test -Plow-cpu` | 2882 | 0 | 0 | 1 |
| `mvn -o -q test -Plow-cpu -DrunUiTests=true` | 2882 | 0 | 0 | 1 |
| `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test` | 530 | 0 | 0 | 0 |

Surefire preserves reports across invocations; the third count above sums XML reports modified during that command's execution window. This is distinct from either full suite as required.

## Phase 1 decision gate

Do not begin phase 2 until both complete Maven suites and the dedicated UI Maven run pass, digests are stable, UI counts differ from full-suite counts, and the worktree remains limited to this request.
