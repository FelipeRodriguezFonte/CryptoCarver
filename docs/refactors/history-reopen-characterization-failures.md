# History reopen characterization: baseline failure

Recorded before the privacy fix, on `codex/modern-main-history` at `a383c84f18f60724f2ed2a13527464d523a62474` plus the history map commit.

Command:

```text
mvn -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dtest=HistoryReopenCharacterizationUITest test
```

Observed baseline failure:

```text
HistoryReopenCharacterizationUITest.reopenRerunClearAndPrivacyProfilesMatchPinnedTranscript
private details persisted under MASKED ==> expected: <false> but was: <true>
```

The fixture stored only the synthetic value `SYNTHETIC_HISTORY_PRIVATE_FIXTURE_55`. `HistoryCoordinator.addToHistory` passed classified details directly into `HistoryCommandPolicy.create`, whose result is written to the plain-text history file. Presentation filtering was applied later, so it did not protect persisted structured details or their serialized `details` field. `FULL_LAB` intentionally permits this data; the failing assertion is limited to `MASKED` and `REDACTED`.

An earlier harness run also failed because its assertion incorrectly prohibited storage under `FULL_LAB`; that was corrected before recording this baseline. No production code was changed before the failure above was captured.

## Input-clear failure

After the privacy fix, the same characterization advanced to the clear-input check and exposed a second baseline failure:

```text
HistoryReopenCharacterizationUITest.reopenRerunClearAndPrivacyProfilesMatchPinnedTranscript
expected: <> but was: <abc>
```

The test reopened Hashing, entered the public synthetic input `abc`, invoked the shell's Clear Input action, and found the input still present. `GenericController.handleClear()` clears several generic tools but omits the hash input/output controls. This note is committed before the corresponding fix.
