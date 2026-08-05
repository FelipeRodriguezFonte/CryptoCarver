package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class OperationExecutorTest {

    private OperationExecutor executor;
    private String originalTestMode;

    @BeforeAll
    static void initJFX() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX Platform failed to start");
    }

    @BeforeEach
    void setUp() {
        originalTestMode = System.getProperty("test.mode");
        System.clearProperty("test.mode");
        executor = new OperationExecutor();
    }

    @AfterEach
    void tearDown() {
        if (originalTestMode != null) {
            System.setProperty("test.mode", originalTestMode);
        } else {
            System.clearProperty("test.mode");
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void testSuccessfulExecutionAndCallbackOnFXThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean onFxThread = new AtomicBoolean(false);
        AtomicReference<String> resultRef = new AtomicReference<>();

        executor.execute(
                "Test Success",
                null,
                () -> "Hello Async",
                res -> {
                    onFxThread.set(Platform.isFxApplicationThread());
                    resultRef.set(res);
                    latch.countDown();
                },
                err -> fail("Task should not fail: " + err.getMessage()),
                () -> fail("Task should not be cancelled")
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Async task execution timed out");
        assertTrue(onFxThread.get(), "Success callback must be executed on JavaFX Application Thread");
        assertEquals("Hello Async", resultRef.get());
    }

    @Test
    void testFailureHandlingAndCallbackOnFXThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean onFxThread = new AtomicBoolean(false);
        AtomicBoolean taskOnFxThread = new AtomicBoolean(true);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        Button trigger = new Button("TSA");

        executor.execute(
                "Test Failure",
                trigger,
                () -> { taskOnFxThread.set(Platform.isFxApplicationThread()); throw new RuntimeException("Simulated background error"); },
                res -> fail("Task should not succeed"),
                err -> {
                    onFxThread.set(Platform.isFxApplicationThread());
                    errorRef.set(err);
                    latch.countDown();
                },
                () -> fail("Task should not be cancelled")
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Async failure execution timed out");
        assertTrue(onFxThread.get(), "Failure callback must be executed on JavaFX Application Thread");
        assertNotNull(errorRef.get());
        assertEquals("Simulated background error", errorRef.get().getMessage());
        assertFalse(taskOnFxThread.get(), "The potentially blocking task must run off the JavaFX Application Thread");
        assertFalse(trigger.isDisable(), "The trigger must be restored after an error");
    }

    @Test
    void testCancellationDoesNotPublishResultOrHistory() throws Exception {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        AtomicBoolean cancelledRef = new AtomicBoolean(false);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        executor.execute(
                "Test Cancel",
                null,
                () -> {
                    Thread.sleep(2000);
                    return "Should not reach here";
                },
                res -> successCalled.set(true),
                err -> fail("Cancelled task should not call failure callback"),
                () -> {
                    cancelledRef.set(true);
                    cancelLatch.countDown();
                }
        );

        // Cancel after 100ms
        Thread.sleep(100);
        executor.cancelCurrentOperation();

        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "Cancellation handling timed out");
        assertTrue(cancelledRef.get(), "Cancelled callback must be invoked");
        assertFalse(successCalled.get(), "Success callback must NOT be invoked when cancelled");
    }

    @Test
    void testTestModeSynchronousExecution() {
        System.setProperty("test.mode", "true");

        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicReference<String> resultRef = new AtomicReference<>();

        executor.execute(
                "Test Sync",
                null,
                () -> "Sync Result",
                res -> {
                    executed.set(true);
                    resultRef.set(res);
                },
                err -> fail("Sync task failed: " + err.getMessage()),
                () -> fail("Sync task cancelled")
        );

        assertTrue(executed.get(), "In test.mode=true, execution must be synchronous");
        assertEquals("Sync Result", resultRef.get());
    }

    @Test
    void testProgressThresholdDelay() throws Exception {
        CountDownLatch progressShowLatch = new CountDownLatch(1);
        AtomicBoolean progressShown = new AtomicBoolean(false);

        executor.setProgressHandlers(
                opName -> {
                    progressShown.set(true);
                    progressShowLatch.countDown();
                },
                () -> {}
        );

        // 1. Fast operation (<400ms) should NOT trigger progress
        CountDownLatch fastLatch = new CountDownLatch(1);
        executor.execute(
                "Fast Op",
                null,
                () -> "Fast Result",
                res -> fastLatch.countDown(),
                err -> fail(err.getMessage()),
                () -> {}
        );
        assertTrue(fastLatch.await(3, TimeUnit.SECONDS));
        assertFalse(progressShown.get(), "Operation completing under 400ms must not trigger progress UI");

        // 2. Slow operation (>400ms) SHOULD trigger progress
        CountDownLatch slowLatch = new CountDownLatch(1);
        executor.execute(
                "Slow Op",
                null,
                () -> {
                    Thread.sleep(800);
                    return "Slow Result";
                },
                res -> slowLatch.countDown(),
                err -> fail(err.getMessage()),
                () -> {}
        );

        assertTrue(progressShowLatch.await(3, TimeUnit.SECONDS), "Progress handler should be called for slow operation (>400ms)");
        assertTrue(progressShown.get());
        assertTrue(slowLatch.await(3, TimeUnit.SECONDS));
    }

    @Test
    void testFileCipherCancellationDeletesPartialFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path source = tempDir.resolve("input.txt");
        java.nio.file.Path destination = tempDir.resolve("output.enc");
        java.nio.file.Path tag = tempDir.resolve("output.tag");

        java.nio.file.Files.writeString(source, "Sample data for streaming cipher cancellation test");

        CountDownLatch cancelLatch = new CountDownLatch(1);

        executor.execute(
                "Streaming File Cipher",
                null,
                () -> {
                    // Simulate partial writing to destination and tag before cancellation
                    java.nio.file.Files.writeString(destination, "PARTIAL DESTINATION CONTENT");
                    java.nio.file.Files.writeString(tag, "PARTIAL TAG CONTENT");
                    Thread.sleep(2000);
                    return "Done";
                },
                res -> fail("Cancelled operation must not invoke success callback"),
                err -> fail("Cancelled operation must not invoke failure callback"),
                () -> {
                    try {
                        java.nio.file.Files.deleteIfExists(destination);
                        if (tag != null) java.nio.file.Files.deleteIfExists(tag);
                    } catch (Exception ignored) {}
                    cancelLatch.countDown();
                }
        );

        Thread.sleep(100);
        executor.cancelCurrentOperation();

        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "Cancellation handling timed out");
        assertFalse(java.nio.file.Files.exists(destination), "Partial destination file must be deleted upon cancellation");
        assertFalse(java.nio.file.Files.exists(tag), "Partial tag file must be deleted upon cancellation");
    }

    @Test
    void testCannotSubmitNewTaskWhileCancelling() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch blockWorkerLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        AtomicBoolean secondTaskRejected = new AtomicBoolean(false);

        executor.execute(
                "Task 1",
                null,
                () -> {
                    workerStarted.countDown();
                    // Ignore interrupts temporarily to simulate worker thread still busy unwinding
                    try {
                        blockWorkerLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        try {
                            blockWorkerLatch.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {}
                    }
                    return "Task 1 Done";
                },
                res -> {},
                err -> {},
                finishLatch::countDown
        );

        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));

        // Cancel task 1
        assertTrue(executor.cancelCurrentOperation());
        assertEquals(OperationExecutor.State.CANCELLING, executor.getState());

        CountDownLatch rejectedLatch = new CountDownLatch(1);
        // Attempting to submit Task 2 while Task 1 worker thread is still active in CANCELLING state
        executor.execute(
                "Task 2",
                null,
                () -> "Task 2 Done",
                res -> fail("Task 2 should not run"),
                err -> {
                    if (err instanceof IllegalStateException) {
                        secondTaskRejected.set(true);
                        rejectedLatch.countDown();
                    }
                },
                () -> fail("Task 2 should not be cancelled")
        );

        assertTrue(rejectedLatch.await(3, TimeUnit.SECONDS), "Rejection callback must be dispatched");
        assertTrue(secondTaskRejected.get(), "New execution must be rejected while executor is in CANCELLING state");

        // Unblock worker thread to finish unwinding
        blockWorkerLatch.countDown();
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));
        assertEquals(OperationExecutor.State.IDLE, executor.getState());
    }

    @Test
    void testCancellationBeforeWorkerStarts() throws Exception {
        CountDownLatch rawTaskStarted = new CountDownLatch(1);
        CountDownLatch pauseWorker = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        Button btn = new Button("Submit");

        // Occupy raw worker thread directly without altering OperationExecutor state
        executor.submitRawTaskForTest(() -> {
            rawTaskStarted.countDown();
            try {
                pauseWorker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        });

        assertTrue(rawTaskStarted.await(2, TimeUnit.SECONDS), "Raw worker task should be running");

        // Submit operation to executor while worker thread is busy with raw task
        executor.execute(
                "Pre-start Cancel",
                btn,
                () -> {
                    taskExecuted.set(true);
                    return "Done";
                },
                res -> fail("Should not succeed"),
                err -> fail("Should not fail"),
                cancelLatch::countDown
        );

        assertEquals(OperationExecutor.State.RUNNING, executor.getState());
        assertTrue(btn.isDisable());

        // Cancel operation while queued in worker executor before it starts
        executor.cancelCurrentOperation();

        // Release raw worker task so worker thread processes the queued Runnable
        pauseWorker.countDown();

        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "onCancelled callback must be invoked");
        assertFalse(taskExecuted.get(), "task.call() must NEVER be executed when cancelled pre-start");
        assertEquals(OperationExecutor.State.IDLE, executor.getState(), "State must reset to IDLE");
        assertFalse(btn.isDisable(), "Trigger button must be re-enabled");
    }

    @Test
    void testProgressFormattingDeterminedAndIndeterminate() {
        // Determined (total > 0)
        long bytesProcessed = (long) (42.0 / 100.0 * 29.5 * 1024 * 1024);
        long totalBytes = (long) (29.5 * 1024 * 1024);
        String determinedText = OperationExecutor.formatProgressText("Encrypting file", bytesProcessed, totalBytes, 8000);

        assertTrue(determinedText.contains("Encrypting file…"));
        assertTrue(determinedText.contains("42%"));
        assertTrue(determinedText.contains("12.4 MB / 29.5 MB"));
        assertTrue(determinedText.contains("00:08"));

        // Indeterminate (total == 0)
        String indeterminateText = OperationExecutor.formatProgressText("Generating RSA-4096", 0, 0, 8000);
        assertEquals("Generating RSA-4096… · 00:08", indeterminateText);
    }

    @Test
    void testProgressThrottlingAndFxThreadDispatch() throws Exception {
        AtomicInteger updateCount = new AtomicInteger(0);
        AtomicBoolean onFxThread = new AtomicBoolean(true);

        executor.setProgressHandlers(
                s -> {},
                details -> {
                    if (!Platform.isFxApplicationThread()) {
                        onFxThread.set(false);
                    }
                    updateCount.incrementAndGet();
                },
                () -> {}
        );

        CountDownLatch startLatch = new CountDownLatch(1);
        executor.execute("Throttling Test", null, () -> {
            startLatch.countDown();
            return "Done";
        }, res -> {}, err -> {}, () -> {});

        assertTrue(startLatch.await(2, TimeUnit.SECONDS));

        long execId = executor.getCurrentExecutionId();

        // Rapidly fire 10 progress reports within <10ms
        for (int i = 1; i <= 10; i++) {
            executor.reportProgress(execId, "Throttling Test", i * 100, 1000);
        }

        // Wait for FX thread queue to flush
        CountDownLatch fxFlush = new CountDownLatch(1);
        Platform.runLater(fxFlush::countDown);
        assertTrue(fxFlush.await(2, TimeUnit.SECONDS));

        assertTrue(onFxThread.get(), "Progress updates MUST run on FX Application Thread");
        assertTrue(updateCount.get() <= 2, "Burst updates within 120ms must be throttled");
    }

    @Test
    void testNoProgressUpdatesAfterShutdownOrCancel() throws Exception {
        AtomicInteger postShutdownUpdates = new AtomicInteger(0);
        executor.setProgressHandlers(s -> {}, details -> postShutdownUpdates.incrementAndGet(), () -> {});

        long execId = executor.getCurrentExecutionId();
        executor.shutdown();

        executor.reportProgress(execId, "Post Shutdown Test", 50, 100);

        CountDownLatch fxFlush = new CountDownLatch(1);
        Platform.runLater(fxFlush::countDown);
        assertTrue(fxFlush.await(2, TimeUnit.SECONDS));

        assertEquals(0, postShutdownUpdates.get(), "No progress update handler must be called after shutdown");
    }

    @Test
    void testFastOperationNeverShowsProgressOrUpdates() throws Exception {
        AtomicBoolean showCalled = new AtomicBoolean(false);
        AtomicBoolean updateCalled = new AtomicBoolean(false);

        executor.setProgressHandlers(
                s -> showCalled.set(true),
                d -> updateCalled.set(true),
                () -> {}
        );

        CountDownLatch doneLatch = new CountDownLatch(1);

        executor.executeWithProgress(
                "Fast Task",
                null,
                monitor -> {
                    monitor.updateProgress(50, 100);
                    return "Fast Result";
                },
                res -> doneLatch.countDown(),
                err -> fail(err),
                () -> fail("Should not cancel")
        );

        assertTrue(doneLatch.await(2, TimeUnit.SECONDS));

        // Flush FX thread
        CountDownLatch fxFlush = new CountDownLatch(1);
        Platform.runLater(fxFlush::countDown);
        assertTrue(fxFlush.await(2, TimeUnit.SECONDS));

        assertFalse(showCalled.get(), "Fast operation < 400ms MUST NEVER invoke showProgressHandler");
        assertFalse(updateCalled.get(), "Fast operation < 400ms MUST NEVER invoke updateProgressHandler");
    }

    @Test
    void testSlowOperationDispatchesAccumulatedProgressAt400msThreshold() throws Exception {
        AtomicBoolean showCalled = new AtomicBoolean(false);
        AtomicReference<OperationExecutor.ProgressDetails> firstUpdate = new AtomicReference<>();
        CountDownLatch thresholdLatch = new CountDownLatch(1);

        executor.setProgressHandlers(
                s -> showCalled.set(true),
                details -> {
                    firstUpdate.compareAndSet(null, details);
                    thresholdLatch.countDown();
                },
                () -> {}
        );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch blockWorker = new CountDownLatch(1);

        executor.executeWithProgress(
                "Slow Task",
                null,
                monitor -> {
                    workerStarted.countDown();
                    // Accumulate updates BEFORE 400ms threshold
                    monitor.updateProgress(450, 1000);
                    blockWorker.await(2, TimeUnit.SECONDS);
                    return "Done";
                },
                res -> {},
                err -> {},
                () -> {}
        );

        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));

        // Wait for 400ms threshold timer to fire
        assertTrue(thresholdLatch.await(3, TimeUnit.SECONDS), "Progress updates should dispatch when 400ms threshold is reached");
        blockWorker.countDown();

        assertTrue(showCalled.get(), "showProgressHandler MUST be invoked when 400ms threshold is reached");
        assertNotNull(firstUpdate.get(), "First progress update must not be null");
        assertEquals(450, firstUpdate.get().getBytesProcessed(), "First visual update at 400ms threshold must contain accumulated bytes, NOT 0");
        assertTrue(firstUpdate.get().getFormattedText().contains("45%"), "First visual update at 400ms threshold must contain accumulated percentage");
    }

    @Test
    void testStaleMonitorIgnoredOnSubsequentExecution() throws Exception {
        AtomicReference<OperationExecutor.ProgressDetails> reportedDetails = new AtomicReference<>();
        executor.setProgressHandlers(s -> {}, reportedDetails::set, () -> {});

        CountDownLatch task1Done = new CountDownLatch(1);
        AtomicReference<com.cryptocarver.util.ProgressMonitor> monitorA = new AtomicReference<>();

        executor.executeWithProgress(
                "Task A",
                null,
                monitor -> {
                    monitorA.set(monitor);
                    return "A Done";
                },
                res -> task1Done.countDown(),
                err -> fail(err),
                () -> {}
        );

        assertTrue(task1Done.await(2, TimeUnit.SECONDS));
        assertNotNull(monitorA.get());

        // Now start Task B
        CountDownLatch task2Started = new CountDownLatch(1);
        CountDownLatch blockTask2 = new CountDownLatch(1);

        executor.executeWithProgress(
                "Task B",
                null,
                monitor -> {
                    task2Started.countDown();
                    blockTask2.await(2, TimeUnit.SECONDS);
                    return "B Done";
                },
                res -> {},
                err -> {},
                () -> {}
        );

        assertTrue(task2Started.await(2, TimeUnit.SECONDS));

        // Attempt to report progress using stale monitorA from Task A
        monitorA.get().updateProgress(999, 1000);
        blockTask2.countDown();

        // Flush FX thread
        CountDownLatch fxFlush = new CountDownLatch(1);
        Platform.runLater(fxFlush::countDown);
        assertTrue(fxFlush.await(2, TimeUnit.SECONDS));

        assertNull(reportedDetails.get(), "Stale monitor from previous execution MUST NOT report progress on new execution");
    }

    @Test
    void testQueuedProgressDiscardedOnCancellation() throws Exception {
        AtomicInteger updateCount = new AtomicInteger(0);
        executor.setProgressHandlers(
                s -> {},
                details -> updateCount.incrementAndGet(),
                () -> {}
        );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CountDownLatch fxQueueBlockStarted = new CountDownLatch(1);
        CountDownLatch releaseFxQueue = new CountDownLatch(1);

        executor.executeWithProgress(
                "Cancel Test",
                null,
                monitor -> {
                    workerStarted.countDown();
                    blockWorker.await(2, TimeUnit.SECONDS);
                    return "Done";
                },
                res -> fail("Should not succeed"),
                err -> fail("Should not fail"),
                cancelLatch::countDown
        );

        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
        long execId = executor.getCurrentExecutionId();

        // Simulate 400ms threshold reached
        Field thresholdField = OperationExecutor.class.getDeclaredField("thresholdReached");
        thresholdField.setAccessible(true);
        thresholdField.set(executor, true);

        // Keep the FX queue blocked so this test can distinguish an enqueued
        // callback from one that was already delivered before cancellation.
        Platform.runLater(() -> {
            fxQueueBlockStarted.countDown();
            try {
                releaseFxQueue.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(fxQueueBlockStarted.await(2, TimeUnit.SECONDS));

        // Queue progress report to the blocked FX thread.
        executor.reportProgress(execId, "Cancel Test", 50, 100);

        // Cancel while the progress callback is still queued.
        executor.cancelCurrentOperation();
        blockWorker.countDown();
        releaseFxQueue.countDown();

        assertTrue(cancelLatch.await(2, TimeUnit.SECONDS));

        // Flush FX thread
        CountDownLatch fxFlush = new CountDownLatch(1);
        Platform.runLater(fxFlush::countDown);
        assertTrue(fxFlush.await(2, TimeUnit.SECONDS));

        assertEquals(0, updateCount.get(), "Queued progress reports MUST be discarded when operation is cancelled");
    }

    @Test
    void testProgressDeliveryCannotRaceCancellationAfterStateCheck() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        CountDownLatch cancellationRequested = new CountDownLatch(1);
        CountDownLatch cancelReturned = new CountDownLatch(1);
        AtomicInteger updateCount = new AtomicInteger();

        executor.setProgressHandlers(s -> {}, details -> {
            updateCount.incrementAndGet();
            handlerStarted.countDown();
            try {
                releaseHandler.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, () -> {});

        executor.executeWithProgress(
                "Progress/Cancellation Race",
                null,
                monitor -> {
                    workerStarted.countDown();
                    blockWorker.await(2, TimeUnit.SECONDS);
                    return "Done";
                },
                result -> fail("Should not succeed"),
                error -> fail("Should not fail", error),
                () -> {}
        );

        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));
        long execId = executor.getCurrentExecutionId();
        Field thresholdField = OperationExecutor.class.getDeclaredField("thresholdReached");
        thresholdField.setAccessible(true);
        thresholdField.set(executor, true);

        executor.reportProgress(execId, "Progress/Cancellation Race", 50, 100);
        assertTrue(handlerStarted.await(2, TimeUnit.SECONDS));

        Thread cancellationThread = new Thread(() -> {
            cancellationRequested.countDown();
            executor.cancelCurrentOperation();
            cancelReturned.countDown();
        }, "operation-cancellation-test");
        cancellationThread.start();
        assertTrue(cancellationRequested.await(2, TimeUnit.SECONDS));

        // The handler is still the in-flight delivery. Releasing it establishes
        // the ordering without relying on a sleep or scheduler timing.
        releaseHandler.countDown();
        assertTrue(cancelReturned.await(2, TimeUnit.SECONDS));
        blockWorker.countDown();

        assertEquals(1, updateCount.get(), "A delivery that started before cancellation must remain delivered");
    }

    @Test
    void testSlowOperationEmitsExact100PercentBeforeHideAndSuccess() throws Exception {
        java.util.List<String> eventSequence = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        AtomicReference<OperationExecutor.ProgressDetails> lastDetails = new AtomicReference<>();

        executor.setProgressHandlers(
                s -> eventSequence.add("show"),
                details -> {
                    lastDetails.set(details);
                    eventSequence.add("update:" + details.getBytesProcessed() + "/" + details.getTotalBytes());
                },
                () -> eventSequence.add("hide")
        );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch successLatch = new CountDownLatch(1);

        executor.executeWithProgress(
                "Slow 100% Test",
                null,
                monitor -> {
                    workerStarted.countDown();
                    monitor.updateProgress(500, 1000);
                    blockWorker.await(2, TimeUnit.SECONDS);
                    return "Success Result";
                },
                res -> {
                    eventSequence.add("success");
                    successLatch.countDown();
                },
                err -> fail(err),
                () -> fail("Should not cancel")
        );

        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));

        // Manually trigger 400ms threshold
        Field thresholdField = OperationExecutor.class.getDeclaredField("thresholdReached");
        thresholdField.setAccessible(true);
        thresholdField.set(executor, true);

        blockWorker.countDown();
        assertTrue(successLatch.await(3, TimeUnit.SECONDS));

        // Flush FX thread
        CountDownLatch fxFlush = new CountDownLatch(1);
        Platform.runLater(fxFlush::countDown);
        assertTrue(fxFlush.await(2, TimeUnit.SECONDS));

        assertNotNull(lastDetails.get(), "Final 100% progress details must be captured");
        assertEquals(1000, lastDetails.get().getBytesProcessed(), "Final progress must reach 100% of total bytes");
        assertEquals(1000, lastDetails.get().getTotalBytes());
        assertTrue(lastDetails.get().getFormattedText().contains("100%"), "Final progress text must contain 100%");

        int lastUpdateIdx = -1;
        int hideIdx = -1;
        int successIdx = -1;
        for (int i = 0; i < eventSequence.size(); i++) {
            String evt = eventSequence.get(i);
            if (evt.startsWith("update:1000/1000")) lastUpdateIdx = i;
            if (evt.equals("hide")) hideIdx = i;
            if (evt.equals("success")) successIdx = i;
        }

        assertTrue(lastUpdateIdx >= 0, "Final 100% update must occur");
        assertTrue(hideIdx > lastUpdateIdx, "hideProgressHandler must occur AFTER final 100% update");
        assertTrue(successIdx > hideIdx, "onSuccess must occur AFTER hideProgressHandler");
    }

    @Test
    void testNonCancellableCommitEmitsNoUpdatesAfterCompletion() throws Exception {
        AtomicInteger postCommitUpdates = new AtomicInteger(0);
        executor.setProgressHandlers(s -> {}, d -> postCommitUpdates.incrementAndGet(), () -> {});

        CountDownLatch doneLatch = new CountDownLatch(1);

        executor.executeWithProgress(
                "Commit Test",
                null,
                monitor -> {
                    boolean entered = executor.enterCommitPhase();
                    assertTrue(entered);
                    monitor.updateProgress(100, 100);
                    return "Committed";
                },
                res -> doneLatch.countDown(),
                err -> fail(err),
                () -> fail("Should not cancel")
        );

        assertTrue(doneLatch.await(2, TimeUnit.SECONDS));

        int countAtCompletion = postCommitUpdates.get();

        // Attempt report progress after completion
        long execId = executor.getCurrentExecutionId();
        executor.reportProgress(execId, "Commit Test", 100, 100);

        // Flush FX thread
        CountDownLatch fxFlush = new CountDownLatch(1);
        Platform.runLater(fxFlush::countDown);
        assertTrue(fxFlush.await(2, TimeUnit.SECONDS));

        assertEquals(countAtCompletion, postCommitUpdates.get(), "No progress updates must be emitted after operation completion");
    }
}
