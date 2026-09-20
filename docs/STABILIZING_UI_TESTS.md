# UI Tests Stabilization Guide (task_c9e51a3f)

This document details the root causes identified and resolved for intermittent/flaky tests across the UI suite, along with guidelines and synchronization helpers for future tests.

## Root Causes Identified

### 1. Listener Lifecycle Leaks & FX Event Queue Saturation
- **Issue**: `I18nService` is a singleton application-wide service that outlives test scenes and controllers. Controllers registered locale change listeners using closures and strong references.
- **Consequence**: As test classes instantiated controllers across the suite, thousands of dead listeners remained retained in `I18nService`. Whenever any test switched language preference (e.g., to `LanguagePreference.ES`), every previously instantiated controller received the notification and posted update tasks to JavaFX's `Platform.runLater`. This saturated the FX application thread queue, causing live UI tests (such as `Ux22ValidationLiveUITest`) to time out waiting for standard UI events.
- **Fix**: `I18nService` now holds listeners weakly via `WeakReference<Consumer<Locale>>`. Controllers and bindings retain a strong reference in an instance field (`private Consumer<Locale> localeChangeListener` or `ModuleI18n.Binding`). When a controller or test fixture is garbage collected, its listener registration automatically expires and is pruned during dispatch.

### 2. Scene-Graph & Control Mutation off the JavaFX Application Thread
- **Issue**: Several controllers mutated JavaFX controls (e.g. `TableView.setPlaceholder`, `ComboBox.setValue`, `TableView.refresh`) directly inside their `I18nService` locale listener callbacks. In headless or unit tests (such as `IcsfModuleI18nTest`), `I18nService.setPreference(...)` was invoked on the JUnit test thread (`main`), triggering callbacks synchronously off the JavaFX application thread while JavaFX was actively rendering or laying out cells.
- **Consequence**: Concurrently mutating `TableView` and `VirtualFlow` off-thread caused internal data structure corruption (`VirtualFlow.addToPile` / `TableViewSkinBase.updateItemCount` throwing `AssertionError`).
- **Fix**: All locale change listener implementations ensure scene-graph and control updates are dispatched safely on the JavaFX Application Thread:
  ```java
  localeChangeListener = locale -> {
      Runnable refresh = this::refreshLocalizedRuntimeText;
      if (Platform.isFxApplicationThread()) refresh.run();
      else Platform.runLater(refresh);
  };
  I18nService.getInstance().addLocaleChangeListener(localeChangeListener);
  ```

### 3. Weak Reference Eviction Mid-Test in Test Fixtures
- **Issue**: In `Ux16AccessibilityLiveUITest`, the return value of `ModuleI18n.bind(...)` was dropped. Because `I18nService` holds listeners weakly, the listener became eligible for garbage collection immediately.
- **Consequence**: Whether a language switch took effect depended entirely on whether GC had run between registration and assertion, causing non-deterministic assertions (`expected <Aplicar> but was <Apply>`).
- **Fix**: Retain the `ModuleI18n.Binding` in an `AtomicReference<ModuleI18n.Binding>` for the lifetime of the test method.

### 4. Non-Deterministic Ciphertext Mutation in Crypto Tests
- **Issue**: In `WssEncryptionOperationsTest.wrongKeyStoreAndTamperedCiphertextAreRejected()`, ciphertext tampering was performed with `.replaceFirst("(<xenc:CipherValue>)[A-Za-z0-9+/]", "$1A")`.
- **Consequence**: Whenever the randomly generated base64 ciphertext happened to already start with `'A'`, replacing it with `'A'` produced no alteration, causing the decryption check to succeed instead of failing (a 1-in-64 random flake).
- **Fix**: Tamper the character dynamically based on the original character (`char corrupted = original == 'A' ? 'B' : 'A'`).

### 5. OperationExecutor Progress Timer Cancellation Race Condition
- **Issue**: In `OperationExecutor.executeWithProgress`, a 400ms threshold timer (`delayFuture`) is scheduled via a background executor. If the worker thread finishes in less than 400ms, it posts `finishExecution` to the JavaFX Application Thread via `Platform.runLater`. Previously, `delayFuture.cancel(false)` was only invoked when `finishExecution` actually ran on the FX thread.
- **Consequence**: If the JavaFX application thread was delayed or loaded by more than ~400ms (e.g. during heavy GC, scene layout, or test teardowns), the 400ms timer fired *before* `finishExecution` was executed by the FX event loop. The timer observed `currentState == State.RUNNING` and dispatched `updateProgressHandler`, violating the invariant that tasks finishing under 400ms must never invoke progress handlers (`testFastOperationNeverShowsProgressOrUpdates`).
- **Fix**: Immediately cancel `delayFuture` and `timerFuture` in the background worker thread upon task completion before queuing `finishExecution` onto the JavaFX thread.

### 6. OperationExecutor Synchronous Execution in UI Tests (`test.mode`)
- **Issue**: `OperationExecutor` executes background tasks asynchronously using worker thread pools by default. When `test.mode` is `"true"`, it switches to synchronous, deterministic execution so that controller operations finish inline without racing UI test assertions. Previously, `ModernMainControllerUITest` cleared `test.mode` indiscriminately in `@AfterEach`, while `pom.xml`'s `ui-tests` profile did not declare `<test.mode>true</test.mode>` (unlike `scripts/run-ui-tests.sh` which specifies `-Dtest.mode=true`).
- **Consequence**: Tests invoking asynchronous controller operations (e.g. `KeysController.handleGenerateRSA()`) could race against the background worker thread, causing assertions like `assertTrue(rsaCard.isVisible())` to check state before the operation completed.
- **Fix**: Added `<test.mode>true</test.mode>` to the `ui-tests` profile `systemPropertyVariables` in `pom.xml` (matching `scripts/run-ui-tests.sh`). In `ModernMainControllerUITest`, ensured `test.mode` is captured and restored cleanly across `@BeforeEach` and `@AfterEach` rather than unconditionally cleared.

---

## Guidelines for Authoring UI & I18n Tests

1. **Always Synchronize JavaFX Thread Interactions**:
   - Never mutate JavaFX properties, nodes, scenes, or controls directly from non-FX threads.
   - Use deterministic latch-based execution helpers when waiting for JavaFX operations:
     ```java
     private static void runAndWait(Runnable action) throws Exception {
         CountDownLatch latch = new CountDownLatch(1);
         AtomicReference<Throwable> failure = new AtomicReference<>();
         Platform.runLater(() -> {
             try {
                 action.run();
             } catch (Throwable t) {
                 failure.set(t);
             } finally {
                 latch.countDown();
             }
         });
         assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX operation timed out");
         if (failure.get() != null) throw new RuntimeException(failure.get());
     }
     ```
   - Avoid `Thread.sleep(...)` or polling loops.

2. **Hold References to Bindings and Listeners**:
   - When calling `ModuleI18n.bind(...)` or `I18nService.getInstance().addLocaleChangeListener(...)`, always store the resulting `Binding` or `Consumer<Locale>` in a field or local variable held across assertions.

3. **Reset Shared State**:
   - While `DeterministicLanguageExtension` restores `LanguagePreference.EN` before each test, test methods that toggle global singletons, system properties or preferences must clean up in an `@AfterEach` block.

