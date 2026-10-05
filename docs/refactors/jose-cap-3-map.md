# JOSE capabilities — phase 3 map

Base: Phase 2 on `codex/jose-capabilities`.

## State and implementation choices

`JwtValidator` verified the token-selected algorithm, checked issuer/audience/time and a small strict-OIDC subset, and only understood `crit: ["b64"]`. It did not report per-check RFC 9068, OIDC hash/nonce, or confirmation-key results.

1. Add a caller-supplied algorithm allowlist. A token algorithm outside it fails signature acceptance. Detect HS* using RSA public-key material as the MAC secret, reject that validation, and produce a distinct security warning; asymmetric verification behavior is unchanged.
2. Add optional expected `typ`/`cty`, RFC 9068 profile, and OIDC inputs. RFC 9068 requires `iss`, `aud`, `exp`, `sub`, `client_id`, `iat`, `jti`, and `typ=at+jwt`; OIDC `at_hash`/`c_hash` use the hash associated with the signing algorithm and `nonce` compares to caller input.
3. Compare `cnf.jkt` and `cnf.x5t#S256` with caller-provided expected thumbprints. Display these checks independently; do not fetch metadata or certificates.
4. Generalize `crit`: a comma-separated set identifies understood header extensions. Unknown critical names fail with individual findings. An explicit ignore option permits continuation but adds a public warning.
5. Keep the legacy `Options` constructors source-compatible. Findings state each check and its reason; warning-only items do not turn a valid token into an invalid one.

## RFC references for implementation and tests

- RFC 9068 Sections 2.1, 2.2, 3, and 4; Figure 2 is an access-token header/claim example.
- OpenID Connect Core 1.0 Sections 3.1.3.6 and 3.3.2.11 describe ID-token hash/nonce validation.
- RFC 7800 Section 3 defines `cnf` confirmation claims.
- RFC 7515 Section 4.1.11 defines critical header processing.
- RFC 8725 Sections 2.1 and 3.1 describe algorithm verification and cross-JWT/algorithm confusion considerations.

## Test plan and control gate

Add fixed claims, token inputs and deterministic hash comparisons. Test each finding independently, allowlist rejection, RSA-public-key/HMAC confusion warning, expected typ/cty, RFC 9068 presence, OIDC hashes and nonce, cnf comparisons, understood/unknown/ignored crit. Characterize UI labels/results in EN/ES and all visibility profiles with a SHA-256 transcript. Run all three phase Maven gates sequentially and stop if any gate or portable digest fails.

## Completion notes

- The advanced JWT validator now supports comma-separated algorithm allowlists, expected typ/cty, RFC 9068 access-token claim/type requirements, OIDC nonce/at_hash/c_hash comparisons, `cnf.jkt` / `cnf.x5t#S256`, and per-header critical-parameter processing. Legacy `Options` constructors and the existing `Result.valid()` contract remain source compatible.
- Unknown critical headers fail individually by default. The explicit ignore setting permits signature verification after removing only the opted-out critical names from the verifier's policy check; the signed input remains untouched, and each ignored name appears as a public security warning. Warning-only results do not change a valid status.
- RSA public-key bytes supplied as an HMAC secret are rejected and returned as an algorithm-confusion warning. The UI presents that warning with the other JWT findings and as a public `Security warning` result detail. New validation controls and warnings are localized in English and Spanish.
- Added RFC 9068/OIDC, allowlist, confirmation-claim, algorithm-confusion and `crit` tests. The UI transcript digest is `bc36301eed279b7a7c7a83ac29214da2dcd1fc9e79eb3a52e034261dea5e64db`.
- Pre-implementation evidence: the first `JwtValidatorPhase3Test` run failed compilation because `Advanced` and warning output did not exist. During final regression, a wrong test assertion was caught by the complete suite and corrected before re-running the final gates.
- Scope remains validation-only for expected certificate/key thumbprints: the module does not fetch remote keys or certificates, and the Phase 2 map records that x5c trust-anchor/PKIX validation and detached-JWS header editing are partial.

Phase 3 Maven gates passed sequentially on 2026-10-05:

| Command | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `mvn -o -q test -Plow-cpu` | 2891 | 0 | 0 | 1 |
| `mvn -o -q test -Plow-cpu -DrunUiTests=true` | 2891 | 0 | 0 | 1 |
| `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test` | 511 | 0 | 0 | 0 |

Surefire preserves reports between runs; the UI group count sums XML reports modified during the final CI UI command and differs from the complete suites.
