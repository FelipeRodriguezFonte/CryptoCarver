# UI test retained heap — Encargo 46

## Baseline and method

Base: `main` at `552c6d6`; macOS arm64, Maven 3.9.11, Homebrew JDK 25,
JavaFX 21.0.5. Single Surefire fork, `reuseForks=true`, default collector and renderer.
No dependencies were downloaded or added. `target/heap` is ignored by Git.

The initial `mvn -o -q test` with
`-Dsurefire.cpuArgs="-Xmx3g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=target/heap"`
passed (2532 tests, 0 failures/errors, 1 pre-existing skip) in **284.56 s**.
Two `jcmd GC.class_histogram` captures and a JFR capture introduce full GCs;
this is a diagnostic time, not an uninstrumented performance comparison.

A temporary, uncommitted autodetected JUnit extension calls `System.gc()` after
each class and reads `MemoryMXBean.getHeapMemoryUsage().getUsed()`.
Growth is the difference between consecutive after-class samples, not a dominator
size or memory allocated exclusively by that class. Changes of language in a
later class can grow a scene retained by an earlier test. Samples use MiB (2^20 bytes).
The second baseline runs at 2 GiB with that extension and JFR profile recording.
The extension and its service registration will be removed before final timing runs.

## Root evidence

`jcmd GC.class_histogram` at the later 3 GiB checkpoint: 939,439,984 shallow
bytes, 77,807 Label objects (52,286,304 bytes), 25,626 HBox objects
(15,785,616 bytes), 24,383 VBox objects (14,434,736 bytes), 5 Stage objects,
5 ordinary Scene objects, 1,548 popup Scene subclasses. Arrays, properties,
CSS state and control skins account for much of the rest. These are shallow
sizes, not retained sizes.

JDK Flight Recorder `jdk.OldObjectSample`, dumped using
`jcmd <fork-pid> JFR.dump filename=target/ui-measured-late-roots.jfr path-to-gc-roots=true`,
provides the strong reference chain (88 Label, 23 HBox, 12 VBox samples):

```
VM Global: AppClassLoader
  -> classes -> Class(com.cryptocarver.ui.Ux23AccessibilityLiveUITest)
  -> static stage -> Window.SceneModel -> Scene
  -> dirtyNodes (Node[]) -> removed/recreated labels and containers
```

The static stage is only hidden by `@AfterAll`, leaving its Scene and controllers
alive. The localization bindings continue to mutate it; a hidden scene does not
drain its dirty-node queue through visible pulses. Other static stages have the
same ownership defect. JFR also shows UI nodes reached from native MacAccessible
handles while windows are alive, and ordinary CSS/font caches; those samples
alone do not establish application leaks.

## Patterns to address

* Tests only: `Ux23AccessibilityLiveUITest.stopStage`,
  `Ux22ValidationLiveUITest.stopStage`, `Ux25CmsJoseValidationLiveUITest.stopStage`
  hide static stages without detaching the Scene or clearing the field.
* Tests only: `XMLSignatureControllerUITest` keeps its root and controller in
  static fields after the class finishes.
* Tests only: `ModuleHostVisibilityUITest.everyOperationRendersSomething` shows
  a Stage and never closes it. Shared teardown must cover exceptional exits too.
* Tests only: FXML fixtures with bare scenes and asynchronous shell executors
  need an explicit teardown even when they never show a Window.
* Production: `ModernMainController.setupWindowLifecycleListeners` registers
  handlers on every attached Scene/Window but never unregisters the former
  Window handler when the Stage replaces its Scene. That handler captures the
  old shell; its executors can also survive.
* Production: expanded viewers cache hidden Stage/Scene/control graphs and
  result snapshots. Shell shutdown only shuts down the executor and Shelf;
  it does not close open result windows. Viewer dismissal/shutdown needs to
  release those graphs, with GC regression coverage.
* Existing protections: I18nService already uses weak locale listeners;
  ClipboardShelfController.dispose removes its manager listener; detached Shelf
  removes its listener on hiding. Do not erase registrations globally to conceal
  missing production disposal.

## Before: highest retained growth before failure

| Test class | Retained after (MiB) | Growth (MiB) |
| --- | ---: | ---: |
| ModernMainShellLocalizationCharacterizationTest | 1670.77 | 627.18 |
| IcsfModuleI18nTest | 1689.78 | 566.28 |
| ModernMainControllerUITest | 1030.21 | 519.19 |
| ModuleHostVisibilityUITest | 278.26 | 242.50 |
| Ux23AccessibilityLiveUITest | 469.71 | 177.62 |
| IcsfNavigationUITest | 1287.17 | 159.48 |
| ModernMainLooseActionsCharacterizationTest | 1874.52 | 106.67 |
| LabelContrastUITest | 1815.16 | 86.44 |
| HistoryManagementCharacterizationTest | 587.64 | 79.24 |
| CommandPaletteCharacterizationTest | 1949.39 | 75.80 |
| QuickNavigationTest | 1865.60 | 35.07 |
| Ux28AddToShelfUITest | 1895.83 | 30.23 |
| IncludedPaneI18nUITest | 497.18 | 29.70 |
| Ux20RPaymentsLiveUITest | 1119.88 | 22.73 |
| NavigationRouterCharacterizationTest | 1815.66 | 21.69 |

The 2 GiB diagnostic run reproduced `OutOfMemoryError: Java heap space`, including
`InvokeLaterDispatcher`, and wrote `target/heap/java_pid40357.hprof` (~3.4 GiB).
After the dump finished the failed fork was terminated rather than continuing
through timeout failures. There are 147 completed after-class samples; subsequent
classes are **not measured**, so this table ranks completed samples, not the entire
suite. Peak live retained heap before failure was already 1,895.83 MiB.
Final comparative measurements will use the same per-class probe and class order.
