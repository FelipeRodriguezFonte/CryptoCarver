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
