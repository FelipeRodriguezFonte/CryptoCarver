# UI suite memory — Encargo 46

## Result

The complete default suite passes in **one reused JVM with `-Xmx2g`**.
Three consecutive runs of the final code: **203.87 s, 198.02 s, 203.83 s**.
Each runs **2537 tests**, with **0 failures, 0 errors and 1 pre-existing skip**.
No OOM, uncaught thread exception or assertion error appears in those logs.

Maximum after-class retained heap fell from **3057.51 MiB** to
**230.43 MiB** (92.5% reduction). These are diagnostic
post-GC samples, not the maximum transient allocation during an uninstrumented run.
The normal suite does **not** force GC. `pom.xml` now defaults to 2 GiB;
`forkCount=1`, `reuseForks=true`, suite discovery and CI fork isolation are unchanged.

Branch: `luna/ui-test-memory`, based on published `main` at `552c6d6`. No push.
No `crypto/` or FXML changes, new dependencies, images, dumps, test exclusions or
new disabled tests. CSS changes only affect navigation legibility/theme tokens.

## Environment and measurement

2026-10-02, macOS arm64; Maven 3.9.11, Homebrew JDK 25, JavaFX 21.0.5.
Default G1 collector and renderer; no `low-cpu` profile. Storage is isolated by
Surefire's existing `user.home=target/test-home` setting. The interactive window
probe uses the installed packaged JDK 21.0.8 with the freshly compiled production
classes/FXML/CSS, in a separate disposable `target/manual-home`.

The initial 3 GiB run used:

```sh
mvn -o -q test '-Dsurefire.cpuArgs=-Xmx3g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=target/heap'
```

It passed in **284.56 s** (2532 tests, no failures/errors, one existing skip).
Two `jcmd GC.class_histogram` captures and JFR sampling add full GCs, so this is
an instrumented reference time. The final three runs are also below the task's
reported 5:12 default-suite time.

A temporary, autodetected JUnit extension calls `System.gc()` once after each
class and reads `MemoryMXBean.getHeapMemoryUsage().getUsed()`. The first sample
is taken before the first class. Growth is the difference between consecutive
samples, **not** a dominator size or memory allocated exclusively by a class.
A later language-changing class can grow an earlier retained scene. JUnit's last
test instance, native accessibility handles and renderer work can still be alive
at the callback; a later negative delta shows their eventual collection.

The 2 GiB diagnostic baseline reproduced `OutOfMemoryError: Java heap space`,
including `InvokeLaterDispatcher`, after 147 completed class samples. Its
3.4 GiB dump is `target/heap/java_pid40357.hprof`. The failed fork was terminated
after the dump completed, rather than continuing through dependent timeout errors.
For a complete before table, the same probe then ran the untouched compiled
baseline at its existing 5 GiB limit (**572.56 s**, 370 classes). The final
measurement uses 2 GiB (**210.95 s**, 372 classes, all tests pass). The order of
all 370 pre-existing classes is identical in both complete measurement runs.

The temporary extension and registration were removed before the three timing
runs, and again after the final measurement. Its source remains only in ignored
`target/HeapMeasurementExtension.java` for local reproduction. Registration order
places it first so its `AfterAllCallback` runs after fixture teardown.

## Root evidence, obtained without network access

A late live baseline `jcmd GC.class_histogram` contains 939,439,984 shallow bytes:
77,807 Label objects (52,286,304 bytes), 25,626 HBox objects (15,785,616 bytes),
24,383 VBox objects (14,434,736 bytes), 5 Stage objects, 5 ordinary Scenes and
1548 popup Scene subclasses. Arrays, properties, CSS state and skins occupy
much of the rest. These are **shallow** sizes, not retained totals.

JDK Flight Recorder `jdk.OldObjectSample`, collected from the 2 GiB baseline
with `JFR.dump path-to-gc-roots=true`, shows this strong chain (88 Label, 23 HBox
and 12 VBox samples in the later capture):

```
VM Global: AppClassLoader
  -> classes -> Class(com.cryptocarver.ui.Ux23AccessibilityLiveUITest)
  -> static stage -> Window.SceneModel -> Scene
  -> dirtyNodes (Node[]) -> removed/recreated labels and containers
```

A selective offline HPROF reader using Python's standard-library `mmap`/`struct`
confirms the static-field, SceneModel and Scene references in the actual dump.
It reads metadata and numeric references only, without printing control values.
The `dirtyNodes` array reached **1,676,337 entries** in that static stage, versus
225 and 30 in the other two static test stages. Hidden scenes do not receive
visible pulses, while their retained localization bindings keep changing their
controls and rebuilding containers. This explains both the memory growth and
extra FX work in later language tests.

Artifacts are local under ignored `target/`; `git check-ignore target/heap`
confirms the dump exclusion. No profiler dependencies were installed. Useful
local artifacts: `hprof-roots-summary.txt`, `ui-measured-late-roots.jfr`,
`heap-before-full.tsv`, `heap-after-final.tsv`, and `ui-memory-final-2/3/4.log`.

## Patterns and fixes

| Pattern | Scope and location | Fix / evidence |
| --- | --- | --- |
| Hidden static Stage retains Scene and dirty queue | Tests: `Ux23AccessibilityLiveUITest.java:32,49`; `Ux22ValidationLiveUITest.java:28,39`; `Ux25CmsJoseValidationLiveUITest.java:36,50` | AfterAll closes the stage, detaches its Scene and nulls the field. HPROF/JFR above identify the root. |
| Static FXML root/controller outlives its class | Tests: `XMLSignatureControllerUITest.java:25,26` | `releaseFixture` at line 55 detaches the Scene root and clears both fields. |
| Stage left open and bare FXML fixtures never disposed | Tests: `ModuleHostVisibilityUITest.java:67` and shared FXML loading | `UiTestLifecycleExtension.java:49,64` closes method/class-owned windows on exceptional exits; `UiTestFxml.java:32` shuts down shells, disposes Shelf views and detaches roots/scenes, including already-hidden stages. Weak fixture tracking introduces no new controller root. Class fixtures survive until AfterAll. |
| Global settings override and locale persist between classes | Tests: common lifecycle extension at line 34 | Disposable per-class settings, previous singleton restored after methods/classes, locale refreshed after disposal. No singleton listener registry is erased to hide disposal failures. |
| Old Window handler keeps a replaced shell alive | Production: baseline `ModernMainController.setupWindowLifecycleListeners`; current implementation at lines 679/705 | Explicit ownership of the observed Node/Scene/Window; unregister on replacement or shutdown. Detaching the shell stops both executors. `ShellWindowLifecycleUITest` keeps the replacement Stage alive and proves the old controller/root are collectible. |
| Expanded window retains a hidden graph and result snapshot; shell close leaves a viewer open | Production: `ExpandedTextViewer.java:70`, `ExpandedTableViewer.java:60`, `ModernMainController.shutdown` | On dismissal, clear snapshots/control fields and detach Scene; reconstruct on reopen. Shell shutdown disposes all three viewers. Three GC/lifecycle tests cover text, table and an open viewer during shell shutdown. |

I18nService already keeps weak locale listeners; node-local listeners do not
need global deregistration. Shelf's existing `dispose()`/onHidden cleanup removes
its manager listener and is now called for headless/bare-scene fixtures too.
HistoryManager is shell-owned (not a singleton), and the audited singleton data
stores contain value records, not Node/controller references. No blanket clearing
of their data/listeners is used to conceal application ownership defects.
Process Designer's existing close/restore path clears its static window state;
its detached view is restored rather than destroyed. No new executor or unbounded
timer was found in that secondary-window path. Toast timers are finite one-shot
transitions; OperationExecutor workers are stopped by shell disposal.

Isolation exposed a real precondition missing from
`Ux21LiveUITest.historyReopenClearsResultsSecretsAndInputMaterial`: `FULL_LAB`
legitimately restores editable input, while the assertion expected restricted
History restoration. It now selects `REDACTED` explicitly and restores the
previous value in `finally`. Its input/output assertions are unchanged.

## Fifteen highest-growth classes before, compared with final code

MiB = 2^20 bytes. A negative growth is collection of earlier fixtures.

| Test class | Before retained | Before growth | After retained | After growth |
| --- | ---: | ---: | ---: | ---: |
| IcsfModuleI18nTest | 2436.01 | 1112.55 | 55.53 | 0.01 |
| ModernMainControllerUITest | 1040.14 | 529.52 | 58.43 | 7.88 |
| ModernMainShellLocalizationCharacterizationTest | 1511.08 | 461.86 | 64.49 | 13.72 |
| IcsfNavigationUITest | 1477.31 | 354.52 | 55.48 | 3.75 |
| ModuleHostVisibilityUITest | 276.01 | 243.57 | 44.35 | 9.96 |
| ClippedTextRegressionUITest | 3057.51 | 186.65 | 56.44 | -2.19 |
| Ux23AccessibilityLiveUITest | 470.33 | 181.80 | 53.08 | 5.80 |
| ModernMainLooseActionsCharacterizationTest | 2702.33 | 140.92 | 135.09 | 81.16 |
| CommandPaletteCharacterizationTest | 2870.86 | 115.74 | 58.63 | 4.92 |
| LabelContrastUITest | 2580.10 | 89.53 | 53.90 | 1.77 |
| HistoryManagementCharacterizationTest | 587.44 | 78.45 | 50.49 | 0.48 |
| QuickNavigationTest | 2737.18 | 68.43 | 54.48 | 0.82 |
| ResultFlowsLiveI18nUITest | 2477.93 | 32.54 | 51.93 | -0.17 |
| PaymentsFormatsLiveI18nUITest | 2561.41 | 31.78 | 53.93 | 0.00 |
| IncludedPaneI18nUITest | 498.41 | 30.05 | 48.05 | 0.01 |

The former 1112.55 MiB growth in the locale test is now 0.01 MiB. Most of its
baseline growth actually belonged to the earlier static Scene's dirty queue.

### Fifteen highest-growth classes after

| Test class | Retained after (MiB) | Growth (MiB) |
| --- | ---: | ---: |
| Ux28bAsymmetricShelfLiveUITest | 230.43 | 165.93 |
| ModernMainLooseActionsCharacterizationTest | 135.09 | 81.16 |
| Ux25CmsJoseValidationLiveUITest | 69.34 | 17.41 |
| ModernMainShellLocalizationCharacterizationTest | 64.49 | 13.72 |
| ModuleHostVisibilityUITest | 44.35 | 9.96 |
| ModernMainControllerUITest | 58.43 | 7.88 |
| IcsfKeyWrapPaneQaUITest | 54.58 | 6.01 |
| Ux23AccessibilityLiveUITest | 53.08 | 5.80 |
| ProcessDesignerWindowUITest | 27.52 | 4.94 |
| CommandPaletteCharacterizationTest | 58.63 | 4.92 |
| IcsfNavigationUITest | 55.48 | 3.75 |
| SessionTrailUITest | 47.03 | 3.47 |
| DialogServiceTest | 34.39 | 2.86 |
| HistorySelectorPolicyTest | 22.52 | 2.63 |
| LabelContrastUITest | 53.90 | 1.77 |

The highest remaining class peak is temporary: later samples return to roughly
50–60 MiB instead of retaining and growing that graph for the rest of the suite.
This table includes native/JUnit callback timing effects, not just application roots.

## Final validation

| Run | Heap / forks | Time | Tests | Failures / errors / skips |
| --- | --- | ---: | ---: | --- |
| Final 1 (`ui-memory-final-2.log`) | 2 GiB / one reused JVM | 203.87 s | 2537 | 0 / 0 / 1 |
| Final 2 (`ui-memory-final-3.log`) | 2 GiB / one reused JVM | 198.02 s | 2537 | 0 / 0 / 1 |
| Final 3 (`ui-memory-final-4.log`) | 2 GiB / one reused JVM | 203.83 s | 2537 | 0 / 0 / 1 |

All three use plain `mvn -o -q test` with the final `pom.xml`, without the heap
probe or GC flags. Before the last hidden-stage teardown refinement, an additional
complete 2 GiB verification passed in 214.35 s; it is not counted in the final trio.
The final post-GC measurement also passes the entire suite in 210.95 s.
Default-suite totals exclude the separately invoked opt-in snapshot/audit tools;
their old XML reports are not added to the 2537-test count.

Five new tests: shell replacement/GC (1), expanded viewer lifecycle/GC (3),
navigation caption and arrow contrast (1). Existing repeated-opening coverage
still passes. The one existing skipped test remains unchanged.

### SidePanel computed styles and contrast

Removed the inline `-fx-text-background-color: #333333`. The navigation lookup and
normal disclosure arrow now use `-cc-navigation-text-background` in Legibility
overrides: light maps it to `#333333`, dark to `-cc-text` (`#ecf0f1`). The existing
focused selected arrow remains white. No other CSS selectors are changed.

`ComputedStyleSnapshotTool`, both themes, Hashing shell plus its synthetic tree
states and scoped dialog: **521 records per theme**. Light has **zero differences**.
Dark has **three changed records**: the real shell tree, synthetic unselected tree,
and synthetic selected/unfocused tree. Each difference is solely the arrow paint
from `0x333333ff` to `0xecf0f1ff`; focused selection and other calculated properties
are unchanged. Local snapshots/diffs are `target/styles-before.txt`,
`target/styles-after.txt`, `target/theme-light.css.diff`, `target/theme-dark.css.diff`.

`LabelContrastUITest` now measures real category TreeCell captions as well as the
Label graphics used by leaf rows, and separately exercises the real navigation
TreeView in both themes with normal/selected states. Captions and visible
disclosure arrows meet its existing 3:1 threshold.

### Clipped text

`ClippedTextAuditTool` ran against the full route registry in all four combinations,
without route filtering:

| Locale | Theme | Findings | Time |
| --- | --- | ---: | ---: |
| en | light | 0 | 131.66 s |
| en | dark | 0 | 124.99 s |
| es | light | 0 | 128.72 s |
| es | dark | 0 | 126.96 s |

Every report ends in `TOTAL = 0`. Local results are
`target/clipped-text-memory/en-light.txt`, `en-dark.txt`, `es-light.txt`, `es-dark.txt`.
No caption exclusions were added.

### Linux / CI

`bash scripts/run-ui-tests.sh` on this Mac stops at its expected Xvfb prerequisite
check (exit 2). Docker is available, but the cached Ubuntu image has no Java,
Maven or `xvfb-run`; the offline image probe found none. The Linux/Xvfb script
could not be validated in this environment. Its CI command and per-class fork
policy are untouched.

## Interactive window check

A disposable local JavaFX host loads production `process_designer.fxml`, invokes
the production controller's detached-window action and the production expanded
text viewer. Native UI automation clicks/keystrokes—not a Java loop calling
show/hide—opened/closed Process Designer **10 times** and the viewer **10 times**,
then extended the viewer check to 20 clicks. Language and theme were toggled
five times each. Settings/data were isolated; the user's running installed app
was not changed. This tests the actual window classes in an isolated host,
not the installed application's complete shell.

Each row uses `jcmd <pid> GC.run` then `GC.heap_info` on that host:

| Checkpoint | Used heap (KiB) | MiB |
| --- | ---: | ---: |
| Before cycles | 34932 | 34.11 |
| After 10 designer cycles | 42320 | 41.33 |
| After 5 viewer cycles | 46863 | 45.76 |
| After 10 viewer cycles + language/theme changes | 53061 | 51.82 |
| After 15 viewer cycles | 54919 | 53.63 |
| After 20 viewer cycles | 56763 | 55.43 |
| Before an additional verified Space/Escape batch | 57819 | 56.46 |
| After 10 Space/Escape cycles, with one final AX observation | 58340 | 56.97 |

The raw total includes observer overhead and first-use font/CSS/renderer caches,
so it **does not establish a flat process-wide heap**. The two histograms around
viewer cycles 15→20 show 1,887,728 additional shallow bytes, of which 1,816,880
(96.3%) are `MacVariant` and `long[]`, consistent with repeated native accessibility
marshalling. Reducing the AX observations sharply reduces the measured growth.
This attribution is an inference from the object deltas, not a proven GC-root
analysis of MacVariant itself. It is not claimed that this library/observer
allocation has been fixed.

Crucially, both histograms contain **one Stage and one ordinary Scene** (the host),
and no increasing count of closed secondary windows/controllers. WeakReference
regressions independently prove that dismissed viewer stages/scenes and replaced
shells can be collected, while the viewer/shell/replacement window remains alive.
The isolated host was closed after the check; no screenshots or probe bundles
are committed. A human-driven check without AX sampling would be needed for a
strictly flat raw manual heap measurement.

## Commits

* `fb6218c` — docs: measure UI suite retained heap and identify static scene roots
* `bae69df` — test: release static JavaFX stages and FXML fixtures after each class
* `e18b7a4` — test: centralize FXML, window and settings fixture teardown
* `bfa5080` — fix: detach shell lifecycle listeners and stop executors on scene replacement
* `9315ddf` — fix: release expanded viewer windows and snapshots on dismissal
* `a292ef8` — fix: theme navigation tree paints and cover caption and arrow contrast
* `f21bda1` — test: select and restore the restricted history visibility profile explicitly
* `0dd7b5b` — test: detach scenes from already-hidden FXML fixture stages
* `09e8a25` — build: run the reused test JVM with a 2g heap

The final documentation commit contains this completed report. No commits were pushed.
