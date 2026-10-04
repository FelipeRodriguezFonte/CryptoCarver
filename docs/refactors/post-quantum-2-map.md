# Post-quantum controller extraction — phase 2 map

Phase baseline: `8669204` (`refactor: extract post-quantum key coordinator`),
branch `codex/post-quantum-1`. The controller is 414 lines at this phase
baseline. The spans below were verified with a brace matcher; end lines are
inclusive.

## Phase 2 boundary: signing and verification

| Method | Lines at phase baseline | Lines | Live state read | State and surfaces written | Logic vs UI wiring |
| --- | ---: | ---: | --- | --- | --- |
| `handlePQCSign` | 171–213 | 43 | `pqcSignAlgoCombo`, `pqcSignInputArea`, shared `PostQuantumKeyState.privateKey`; `StatusReporter` | `pqcSignOutputArea`; publishes input, raw signature bytes, public metadata and status to inspector/result snapshot/history/status | UTF-8 conversion, algorithm/key compatibility, signing, and result metadata are coordinator logic. Reading/writing FXML fields, reporting errors, and publishing the result are UI/application wiring. |
| `handlePQCVerify` | 216–264 | 49 | `pqcSignAlgoCombo`, `pqcSignInputArea`, `pqcVerifySignatureField`, shared `PostQuantumKeyState.publicKey`; `StatusReporter` | Shows valid/invalid feedback; publishes data and raw signature bytes with public metadata and status | Hex parsing, compatibility validation, verification and result metadata are coordinator logic. FXML reads, feedback/reporting, and result publication are wiring. |

## Live-state and surface map

- Key state is shared with phase 1's `PostQuantumKeyState`. Signing reads its
  private key; verification reads its public key. This phase must not copy key
  state or add a `PostQuantumController` reference to the coordinator.
- FXML is limited to the signing algorithm and message inputs, signature output
  area, and verify signature field. The generated signature is intentionally
  visible in the local output area for the operator to copy.
- Both handlers publish the signature as `OperationResult.output` via the
  default `Builder.output(byte[])` overload, whose classification is PUBLIC.
  `ResultPresentationPolicy.classifyPublishedResult` therefore treats the
  published output as public unless another detail raises it. This classification
  can affect the result viewer, expanded viewer, status, history, Shelf, and
  telemetry. The characterization must record actual exposure separately for
  `FULL_LAB`, `MASKED`, and `REDACTED`; signatures must be absent from every
  restricted surface, even though the local FXML result field remains usable.
- Inspector/history details currently contain algorithm, key algorithm,
  data/signature sizes, and verification outcome, not signature bytes directly.
  History also derives a payload detail from `OperationResult.output`; inspect
  its classification and rendered value under all profiles.
- There is no direct Key Lab access, Shelf mutation, or `AppSettings` read/write
  in either method. The shared shell owns result visibility; a `StatusReporter`
  supplier must keep the coordinator attached to the current reporter.
- The methods call `showError`/`showInfo` for validation and verification
  feedback. Several messages are hard-coded English. Characterization will
  check altered signatures and a selected algorithm incompatible with the
  loaded key in both EN and ES before any behavior change.
- There is no signature-specific telemetry call. Capture application stdout
  and stderr, and inspect the shell's published-result snapshot as well as the
  result viewer, inspector, history, Shelf, status bar, and expanded viewer.

## Characterization and gate plan

`PostQuantumSignatureCharacterizationUITest` will continue from the real shell
and `PostQuantumControllerTest` behavior. It will generate a synthetic
ML-DSA-44 key pair, sign an invented UTF-8 message, verify it, and verify an
altered signature. It will exercise an incompatible selected signature
algorithm, record readable EN/ES feedback, and inspect signature exposure in
all three visibility profiles. The generated key, message, signature, and
altered signature are test-only values. Save chooser behavior is not involved.
The test restores `AppSettings`, Shelf, and isolated history.

The transcript will normalize cryptographic bytes and preserve only stable
outcomes. Record baseline failures here before committing the SHA-256-pinned
characterization. If the signature is exposed due to PUBLIC output
classification, make a separate fix commit, then update the transcript digest
with the exact justified changes. Extract only after the characterization is
stable. After phase 2, run separately:

1. `mvn -o -q test -Plow-cpu`
2. `mvn -o -q test -Plow-cpu -DrunUiTests=true`

Record each suite's count, failures, errors, and skips. Do not begin phase 3
unless both gates pass cleanly and the counts differ.

## Phase 2 baseline characterization findings (before pinning the digest)

The first real-shell characterization run failed its strict checks on the
unmodified phase-2 baseline. Its normalized transcript digest was
`8d714330589f20b66158732e13ce884051777331101d9fdeb055dff892ca0cfe`.

- Signing and verification published both raw signature outputs as PUBLIC.
  Under both `MASKED` and `REDACTED`, the signature appeared in the shared
  result viewer, Shelf, and expanded viewer for valid sign/verify flows and for
  altered-signature verification. The inspector, history, status bar, and
  captured stdout/stderr did not contain signature bytes. The private key was
  absent from checked surfaces. The local PQC signature output field remains
  the intended operator-facing copy area.
- An altered signature produced readable English feedback in EN, but remained
  “Signature is INVALID” in ES; the generic fallback remedy was Spanish, while
  the result itself was not translated.
- Selecting ML-DSA-65 for an ML-DSA-44 key produced an unrelated symmetric-key
  length/structure message about the selected cipher and AES bit lengths in
  both EN and ES. It did not identify either PQC parameter set or explain the
  mismatch.
- These findings are baseline defects to fix in separate commits. They were
  recorded in the preceding map commit before the test pinned this baseline
  transcript digest. `PostQuantumSignatureCharacterizationUITest` pins the
  recorded transcript with SHA-256
  `8d714330589f20b66158732e13ce884051777331101d9fdeb055dff892ca0cfe`.
