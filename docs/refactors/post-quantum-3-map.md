# Post-quantum controller extraction — phase 3 map

Phase baseline: `e0fe0ef` (`refactor: extract post-quantum signature
coordinator`), branch `codex/post-quantum-1`. The controller is 335 lines at
this baseline. The spans below were verified with a Java brace matcher; end
lines are inclusive.

## Phase 3 boundary: KEM and benchmark

| Method | Lines at phase baseline | Lines | Live state read | State and surfaces written | Logic vs UI wiring |
| --- | ---: | ---: | --- | --- | --- |
| `handlePQCEncapsulate` | 188–217 | 30 | `pqcKemAlgoCombo`; shared public key and current key pair; `StatusReporter` | Ciphertext, Bob secret, status label/style; writes `PostQuantumKeyState.bobSecret`; publishes ciphertext and details to result surfaces | Algorithm/key compatibility, KEM call, result details and secret handling are coordinator logic. FXML reads/writes and reporter publication are wiring. |
| `handlePQCDecapsulate` | 220–263 | 44 | `pqcKemCiphertextArea`, KEM algorithm, shared private/public keys and Bob secret; `StatusReporter` | Alice secret field, match/mismatch status label/style; publishes ciphertext input and recovered secret output, details and status | Hex parsing, decapsulation, secret comparison, and result metadata are coordinator logic. Control access and status/result publication are wiring. |
| `requireKemKeyPair`, `isKemAlgorithm` | 265–270 | 6 | shared public/private keys and public-key algorithm | Throws a user-facing precondition error or returns a KEM-family predicate | The key-pair precondition and family predicate belong with the KEM coordinator, without changing the public controller API. |
| `handlePQCBenchmark` | 273–315 | 43 | `pqcBenchmarkAlgoCombo`, benchmark controls, `StatusReporter` and optional `OperationExecutor` | Progress visibility, benchmark text, success/failure/cancel status; runs a fixed 1000-iteration `PQCBenchmark` | Algorithm validation and benchmark result normalization belong in a benchmark coordinator. Task creation/execution lifecycle, progress/result callbacks, and reporter feedback are application/UI wiring. |

## Live-state and surface map

- KEM uses the shared `PostQuantumKeyState` from phase 1: public/private key
  pair plus Bob's encapsulated secret. The coordinator must use that state
  rather than copying it, and must not retain the controller.
- FXML KEM controls are the algorithm selector, ciphertext area, Bob and Alice
  secret fields, status label, and Shelf-related menus. The shared secret fields
  are local operator-facing fields. Keep them out of result history, the
  shared result viewer/expanded viewer, status, Shelf, and logs in `MASKED` and
  `REDACTED`.
- Encapsulation publishes ciphertext output and includes a SECRET-classified
  `Secret Size` detail. Decapsulation currently publishes its recovered
  shared secret through default `Builder.output(byte[])` (PUBLIC), and its
  “Shared Secret” history detail is also public. Characterization must verify
  every surface under all visibility profiles before changing this behavior.
- Both methods report through `StatusReporter`. There is no direct Key Lab
  access. No method directly reads/writes `AppSettings`; the shell consumes
  result classifications to apply its active visibility profile and Shelf
  policy.
- Capture stdout/stderr and inspect the result viewer, inspector, history,
  Shelf, status bar, expanded viewer, and result presentation for the shared
  secret. Inspect the raw published snapshot's classification separately from
  visible surfaces.
- Benchmark is independently extractable: it has its own algorithm selector,
  button, progress indicator, result text, fixed iteration count and
  `OperationExecutor` callbacks, and reads no key state. Therefore phase 3 uses
  both `PostQuantumKemCoordinator` and
  `PostQuantumBenchmarkCoordinator`.
- Benchmark transcript must pin stable facts only (selected algorithm,
  iteration count, result sections and completion). It must not pin measured
  timing values. Use invented data and keys throughout.

## Characterization and gate plan

`PostQuantumKemCharacterizationUITest` will run the real FXML/shell flow using
invented ML-KEM keys: encapsulate, decapsulate, and compare Alice/Bob secrets.
It will check secret exposure for both KEM result publications in
`FULL_LAB`, `MASKED`, and `REDACTED`; test a selected algorithm that does not
match the loaded key in EN and ES; and exercise malformed ciphertext input.
It will run the benchmark and record deterministic metadata while ignoring all
timing measurements. The test restores `AppSettings`, Shelf, and isolated
history. Existing signature characterization covers signature verification,
altered signatures, wrong signing parameter sets and their EN/ES messages.

Record baseline findings in this map before pinning the characterization's
stable UTF-8 SHA-256. Any secret classification issue is a separate fix commit
from extraction. After phase 3, run separately:

1. `mvn -o -q test -Plow-cpu`
2. `mvn -o -q test -Plow-cpu -DrunUiTests=true`

Record each total and status count; the test counts must differ. Do not claim
phase 3 complete without both gates passing cleanly.

## Phase 3 baseline findings (before pinning the transcript)

The first strict real-shell run observed these failures before any production
change:

- KEM encapsulation stored the Bob secret locally but did not expose it through
  shared result surfaces. KEM decapsulation published its output as PUBLIC.
  Under `MASKED` and `REDACTED`, the shared secret appeared in the shared
  result viewer, Shelf and expanded viewer. Inspector, history, status bar,
  captured stdout/stderr and checked private-key surfaces had no secret bytes.
- Selecting ML-KEM-768 with an ML-KEM-512 pair showed the English
  “Generate a key pair for the selected ML-KEM/Kyber algorithm first.” message
  under both EN and ES. Only the generic fallback remedy changed to Spanish.
- The malformed-ciphertext response was readable in both EN and ES.
- The benchmark result area was blank after the task returned. The controller
  called `Task.run()` and then read `Task.getValue()` before the JavaFX task
  value property had been published; the user-visible result was therefore
  null. This needs a separate benchmark-result fix.

The initial test predicate treated English “Generate” as Spanish because of
the shared prefix “genera”. The predicate now checks the localized message
phrase rather than that ambiguous substring. After this baseline finding was
recorded, the test pinned its normalized transcript with SHA-256
`966775804cfbe1aef70e67ee937dded76eedc2a63b8a466f6110c5bfe7043d4e`.

## Fix 1 — classify the decapsulated secret

The baseline defect was fixed independently before coordinator extraction:
`handlePQCDecapsulate` now publishes its recovered output as SECRET, matching
its secret detail and the existing encapsulation policy. The characterization
checks the raw output classification and all MASKED/REDACTED surfaces again.
The post-fix transcript SHA-256 is
`cbc6ecb8aa880d65b899199f0b853489812ddbcf3b37606bdf3c5ff8c6686368`; the
initially remaining defects were untranslated KEM mismatch feedback and the
blank benchmark result.

## Fix 2 — localize KEM algorithm mismatch feedback

The selected-versus-loaded algorithm error and title now use EN/ES message
catalog entries with both algorithm names. The characterization checks that
both languages render the mismatch meaning and title correctly. Its transcript
SHA-256 after localization is
`20caaf8a3c033c1b4cab90ee5484cb6db874bf45f158a5fa51ca35f4ff7088b6`.

## Fix 3 — publish benchmark output

`PQCBenchmark.call()` builds its complete or partial output via
`getPartialResult()`, while `Task.getValue()` is only populated through JavaFX
task state publication. The benchmark handler now returns `getPartialResult()`
after `run()`. The characterization verifies stable report sections and
completion without pinning measured timings. Its final phase-3 transcript
SHA-256 is
`a3887b8dea0bce62dc6a4be1ad2b6d868d5e02d8c2600acf81dae0eb2829aab4`.

## Phase 3 extraction

`PostQuantumController` went from 335 lines at the phase baseline to 227 lines
after extraction. Its public FXML handlers now delegate with one-line methods;
the public signatures were compared with commit `49af76e` and are unchanged.

- `PostQuantumKemCoordinator.View` holds the KEM controls and the shared
  `PostQuantumKeyState`. It owns encapsulation, decapsulation, compatibility
  checks, secret classification, and result publication.
- `PostQuantumBenchmarkCoordinator.View` holds only benchmark controls. It
  owns algorithm validation, the fixed 1000-iteration task and its progress,
  completion, failure, and cancellation callbacks. Benchmark result retrieval
  uses `PQCBenchmark.getPartialResult()` after `run()`.
- The controller lazily creates each coordinator once. Both receive
  `coordinatorStatusReporter::get`; neither receives nor captures a controller.
  Their `View` records contain direct references to the already injected JavaFX
  controls and shared key state.
- The benchmark coordinator is separate because it has an independent selector,
  progress/result controls, executor lifecycle, and no KEM key-state access.

Focused post-extraction verification passed for all three characterization
classes and `PostQuantumControllerTest`: 5 tests, 4 suites, 0 failures, 0
errors, 0 skipped. All three pinned transcript files retained their digests:
key `7831be3ff9b4b83497f849c73ffe77c918abc8ba0e610e785c1835d7bc9a1a40`,
signature `d622604c232a47c738c86f97b3d5f6a6579d21c88ea0098dea404cc3ba67138f`,
and KEM/benchmark
`a3887b8dea0bce62dc6a4be1ad2b6d868d5e02d8c2600acf81dae0eb2829aab4`.

## Phase 3 gates

Both required gates passed after extraction. The UI gate was repeated after
clearing stale Surefire XML so its count reflects only the `ui` tag profile.

- `mvn -o -q test -Plow-cpu`: 2867 tests across 425 suites, 0 failures,
  0 errors, 1 skipped.
- `mvn -o -q test -Plow-cpu -DrunUiTests=true`: 523 tests across 110 suites,
  0 failures, 0 errors, 0 skipped.
- The selected counts differ (2867 vs. 523). `ExpandedViewerLifecycleUITest`
  passed (3 tests) in the UI gate.

No pre-existing test failure required comparison against `main`.
