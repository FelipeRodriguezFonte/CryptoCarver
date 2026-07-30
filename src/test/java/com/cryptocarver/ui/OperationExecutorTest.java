package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        executor.execute(
                "Test Failure",
                null,
                () -> { throw new RuntimeException("Simulated background error"); },
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
}
