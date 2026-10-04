# Post-quantum controller extraction — phase 1 map

Baseline: `49af76e79fd22d13bd04558fe349800b4448a3f8`, branch
`codex/post-quantum-1`. `PostQuantumController.java` has 835 lines at this
baseline. Ranges below were checked with a Java brace matcher that ignores
comments and string/character literals. End lines are inclusive.

## Phase 1 boundary: key generation, import, validation, export, description

| Method | Baseline lines | Lines | Live state read | State and surfaces written | Logic vs UI wiring |
| --- | ---: | ---: | --- | --- | --- |
| `handleGeneratePQCKeyPair` | 157–225 | 69 | `pqcAlgorithmCombo`; current reporter/executor; generate button | `currentPublicKey`, `currentPrivateKey`; public/private PEM areas; key status/details areas; result inspector/history/status through `StatusReporter.publish`; executor progress/cancel UI | Algorithm validation, key generation dispatch, PEM formatting and result details belong to the coordinator. Control access, executor dispatch, and reporter publication are UI/application wiring. |
| `handleImportPQCKeys` | 228–242 | 15 | Current reporter | Opens a native multi-file chooser, then delegates selected files; reports chooser/import errors | File chooser and reporting are UI wiring. |
| `importKeysFromContents` (public compatibility entry point) | 244–254 | 11 | Existing in-memory key state | Delegates to PEM parse/import; same current key and UI/reporting effects as import | Must remain on `PostQuantumController` as a one-line delegate so its public API is unchanged. |
| `importKeysFromFiles` | 256–277 | 22 | Files and existing in-memory key state | Reads PEM/DER files, then applies parsed keys and publishes success | File reads and input parsing are coordinator logic; native chooser remains separate UI wiring. |
| `importParsedKeys` | 278–369 | 92 | Parsed keys; current key pair for counterpart validation; algorithm combos | Atomically updates current keys only after parsing and pair validation; fills PEM areas and algorithm combos; writes key status/details and publishes import metadata | Algorithm detection/import and pair validation are crypto-bound application logic. Applying validated candidates to live fields, cached keys, and reporter is UI coordination. |
| `parsePemKey` | 370–409 | 40 | PEM string | Returns the detected public/private kind, DER bytes, and normalized display text; throws readable validation errors | Pure framing/base64 parsing; no controller or shell state. |
| `parseDerKey` | 410–420 | 11 | DER bytes, source filename | Returns a defensive copy and a safe display label; throws on ambiguous DER | ASN.1 shape detection and classification are pure parsing helpers. |
| `isDerSubjectPublicKeyInfo`, `isDerPrivateKeyInfo` | 421–438 | 18 | DER bytes | Boolean parse result only | Pure ASN.1 recognition helpers. |
| `validateImportedKeyPair` | 439–474 | 36 | Public/private key candidates | Throws if unsupported, incompatible, or not an actual matching pair | Calls PQC crypto operations for a round-trip proof; independent of controls/reporting. |
| `handleExportPQCPublicKey`, `handleExportPQCPrivateKey` | 477–483 | 7 | Current key cache | Delegate to save action | Keep public FXML handlers in the controller as one-line delegates. |
| `exportKey` | 485–517 | 33 | Key, filename/type, current reporter | Opens native save chooser, writes PEM; publishes key type/path; public key bytes can be output, private key bytes are deliberately omitted | PEM encoding and result metadata belong to the coordinator; chooser and filesystem interaction are UI/application boundary. Preserve the private-key omission. |
| `toPem`, `decodePem` | 519–529 | 11 | Byte input | PEM serialization or decoded bytes | Serialization/parsing helpers. `decodePem` currently has no call sites and is not part of the requested move unless a real caller is found during extraction. |
| `describeKeyPair` | 530–553 | 24 | Selected algorithm/source and both cached keys | Returns public metadata, public-key fingerprint, and private-key handling note; never embeds private-key bytes | Pure metadata composition over supplied live key state; no direct UI effects. |
| `formatDetails`, `detailsWithPublicKeyMaterial`, `fingerprint`, `safeValue` | 555–585 | 31 | Operation details and/or public key | Return display/history metadata; public PEM is included for generation, private material is not | Formatting/fingerprint helpers belong to the coordinator. |

`isKemAlgorithm` (765–770) is a shared pure predicate needed by key-pair
validation/description and the later KEM extraction. Move it to a package-local
post-quantum helper during phase 1 so the later coordinator does not depend on
`PostQuantumController`.

## Cross-component state map

- Key cache: `currentPublicKey` and `currentPrivateKey` are private controller
  fields. Phase 1 must preserve their lifetime and keep the existing package
  getters working. Later signature and KEM coordinators need live access to the
  same keys, so the coordinator view must expose suppliers/setters or a shared
  state holder; it must not retain a `PostQuantumController`.
- FXML: generation reads `pqcAlgorithmCombo` and writes public/private key,
  status, and details controls. Import additionally updates KEM/signing
  algorithm controls. Export and import handlers own native chooser setup.
- Result and status: generation publishes the public key as output and adds
  public-key PEM metadata; import publishes details only; public export may
  publish public bytes; private export must continue to publish no private
  bytes. `StatusReporter.publish` updates the shell result/inspector, history,
  and status surfaces.
- Key Lab: no direct Key Lab reads or writes exist in these methods. Shelf use
  is in the separate `handlePopulatePqcKeyShelf` handler and is outside phase 1.
- `AppSettings`: no direct reads or writes exist in these methods.
- `StatusReporter`: generation and import publish results; validation errors
  call `showError`; export publishes only after a successful file write.
- Pure vs wiring: PEM/DER framing, metadata formatting, fingerprinting, and
  matching-key proof are coordinator work. FXML access, chooser lifecycle,
  file selection, operation-executor callbacks, and reporter publication are
  wired through a `View` and a lazy `Supplier<StatusReporter>`.

## Characterization and gate plan

`PostQuantumKeyCharacterizationUITest` will cover invented generated key
material, public/private export serialization followed by import, one-side
imports against cached counterpart state, malformed PEM, non-PQC keys,
algorithm-mismatched pairs, and safe history/Shelf/status/expanded-viewer/log
surfaces under `FULL_LAB`, `MASKED`, and `REDACTED`. Error cases will run in EN
and ES. Since a native save chooser cannot be driven reliably by an unattended
UI test, the export round-trip will exercise the exact PEM serialization used
by export and then the public import API; the chooser itself remains a thin
manual boundary. The test must restore `AppSettings`, Shelf, and history.

The test transcript will normalize generated key bytes and fingerprints,
record only stable operation/error/surface outcomes, and pin its complete
UTF-8 SHA-256 digest. Any baseline failure will be recorded here before a fix
commit. After phase 1, run separately:

1. `mvn -o -q test -Plow-cpu`
2. `mvn -o -q test -Plow-cpu -DrunUiTests=true`

Record both total test counts and the UI-tagged test count. Do not begin phase
2 unless both gates pass and their selected test counts differ.

## Phase 1 initial characterization observation

Before pinning the test digest or changing production behavior, the first
targeted UI run recorded:

- Generated ML-DSA-44 and serialized/re-imported both key files successfully.
- The private key was absent from the result viewer, inspector, history, Shelf,
  status bar, expanded viewer, published result snapshot, and captured logs in
  `FULL_LAB`, `MASKED`, and `REDACTED`.
- The three invalid-import cases were readable in EN (3/3), but were not
  localized in ES (0/3): malformed PEM, different parameter sets, and a
  non-matching same-algorithm pair. The messages originate in hard-coded
  English validation branches. This is the phase-1 defect to fix after the
  characterization commit.

Initial stable transcript, SHA-256
`f5dcccdbce3260cdb79321da02c3d53ae36302ed999c4875fa251ccb05928871`:

```text
generated algorithm=ML-DSA-44 public=present private=held-in-memory
export public/private PEM=valid file-roundtrip=valid
FULL_LAB private-key=absent history=checked shelf=checked status=checked expanded=checked
MASKED private-key=absent history=checked shelf=checked status=checked expanded=checked
REDACTED private-key=absent history=checked shelf=checked status=checked expanded=checked
import public/private=ML-DSA-44 state=updated atomically
EN invalid inputs=3 readable messages=3
ES invalid inputs=3 readable messages=0
telemetry/logs=private-key-absent
```
