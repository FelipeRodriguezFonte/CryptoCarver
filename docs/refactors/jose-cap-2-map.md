# JOSE capabilities — phase 2 map

Base: Phase 1 implementation on `codex/jose-capabilities`.

## Current state and gaps

- JWK JSON is parsed by Nimbus and the workbench can convert PEM/JWK, but the UI does not expose `key_ops` as a generation setting or present `use`/`key_ops` as a dedicated attributes view. Cryptographic operations do not consistently warn when JWK metadata conflicts with the attempted use.
- JWE already accepts arbitrary protected custom JSON parameters and displays the decoded header. JWS generation currently creates protected headers internally with only alg/typ/b64/crit; it has no equivalent user-authored header editor.
- Verification resolves a caller supplied key/JWKS only. It does not trust embedded `jwk` or `x5c`, which is the safer default.
- x5u/jku must remain inert display data: no HTTP client or network lookup will be introduced.

## Decisions

1. Keep the public `JOSEController` API and default verifier behavior unchanged. Any embedded-key resolution is opt-in per validation UI setting and adds a public security warning.
2. Use Nimbus JWK metadata types (`KeyUse`, `KeyOperation`) and preserve user-supplied metadata. Metadata mismatch produces a warning while cryptographic checks remain authoritative.
3. Expose protected JWS header JSON and reuse the existing protected JWE custom-header JSON field for `kid`, `jwk`, `x5c`, `x5t`, `x5t#S256`, `x5u`, and `jku`. URLs are displayed only; pasted key/certificate material is local input.
4. Validate x5t/x5t#S256 against the first parsed x5c certificate. A complete certificate path requires explicit trust anchors; if no reusable configured trust-anchor API fits the authorized files, report chain trust as partial instead of implying a trusted chain.
5. No dependency or `pom.xml` change. Changes remain in the authorized JOSE files, UI/FXML, bundles, tests and phase map.

## Test plan and control gate

Add a pre-implementation failing test for metadata generation/mismatch, custom JWS header round trip, embedded JWK opt-in/default-off behavior, and x5c thumbprint mismatch. Include a stable UI characterization transcript with header controls in EN/ES and all visibility profiles. Phase 3 starts only after this phase's three Maven gates pass and the UI test count is distinct from the complete suites.

## Completion notes

- Implemented JWK `use`/`key_ops` inspect, generation and PEM conversion controls. A mismatched use/operation is advisory and appears as a public `Security warning` on JWT/JWS and JWE results.
- JWT protected-header JSON now supports additional JWS headers. The existing JWE custom-header JSON remains the generator for JWE headers. The decoded header panes already display them. `jku` and `x5u` are inert values; no network code was added.
- JWT verification can use an embedded public `jwk` or `x5c` only when the off-by-default checkbox is selected. The result warns that an attacker controls the embedded key. x5t and x5t#S256 are compared with the leaf certificate; every supplied certificate is parsed and adjacent signatures are linked. No trust-anchor input exists in the authorized JOSE UI, so a cryptographic path to a trusted root is not asserted. Detached JWS custom-header editing and trust-store path validation remain partial.
- Crypto tests cover JWK metadata mismatch/preservation, protected header parse/round-trip, no HTTP lookup for URL-valued headers, explicit embedded-key opt-in/default-off, and x5t mismatch/correct thumbprints. The UI transcript digest is `7571a87dd3b1209df0c6972f6b0eeb575047c3c07525fb66329455ab2d7784ff`.
- Workflow deviation: unlike phase 1, a pre-implementation red run was not captured before phase 2 edits. The final regression tests pass, but the requested fail-first evidence is unavailable.

Phase 2 Maven gates passed sequentially on 2026-10-05:

| Command | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `mvn -o -q test -Plow-cpu` | 2886 | 0 | 0 | 1 |
| `mvn -o -q test -Plow-cpu -DrunUiTests=true` | 2886 | 0 | 0 | 1 |
| `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test` | 530 | 0 | 0 | 0 |

Surefire preserves reports between runs; the last count sums XML reports modified during the CI UI command. It differs from the complete suites.
