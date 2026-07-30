package com.cryptocarver.ui;

import com.cryptocarver.util.ProgressMonitor;
import javafx.application.Platform;
import javafx.scene.control.Button;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Lightweight, reusable async executor for heavy cryptographic calculations,
 * network/TSA calls, and file streaming operations without freezing the JavaFX Application Thread.
 */
public class OperationExecutor {

    public enum State {
        IDLE,
        RUNNING,
        CANCELLING
    }

    @FunctionalInterface
    public interface ProgressTask<T> {
        T run(ProgressMonitor monitor) throws Exception;
    }

    public static class ProgressDetails {
        private final String operationName;
        private final long bytesProcessed;
        private final long totalBytes;
        private final long elapsedTimeMs;
        private final String formattedText;

        public ProgressDetails(String operationName, long bytesProcessed, long totalBytes, long elapsedTimeMs, String formattedText) {
            this.operationName = operationName;
            this.bytesProcessed = bytesProcessed;
            this.totalBytes = totalBytes;
            this.elapsedTimeMs = elapsedTimeMs;
            this.formattedText = formattedText;
        }

        public String getOperationName() { return operationName; }
        public long getBytesProcessed() { return bytesProcessed; }
        public long getTotalBytes() { return totalBytes; }
        public long getElapsedTimeMs() { return elapsedTimeMs; }
        public String getFormattedText() { return formattedText; }
    }

    private final ExecutorService workerExecutor;
    private final ScheduledExecutorService timerExecutor;

    private State currentState = State.IDLE;
    private long currentExecutionId = 0;
    private Future<?> currentFuture;
    private ScheduledFuture<?> delayFuture;
    private ScheduledFuture<?> timerFuture;

    private Button currentTriggerButton;
    private Consumer<String> showProgressHandler;
    private Consumer<ProgressDetails> updateProgressHandler;
    private Runnable hideProgressHandler;
    private Runnable activeOnCancelled;

    private boolean activeWorkerStarted = false;
    private boolean inCommitPhase = false;
    private boolean thresholdReached = false;

    private String currentOperationName = "Operation";
    private long startTimeMs = 0;
    private long latestBytesProcessed = 0;
    private long latestTotalBytes = 0;
    private long lastFxUpdateTimestampMs = 0;

    public OperationExecutor() {
        this.workerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cryptocarver-async-worker");
            t.setDaemon(true);
            return t;
        });
        this.timerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cryptocarver-timer-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized State getState() {
        return currentState;
    }

    public synchronized boolean isCancelled() {
        return !inCommitPhase && currentState == State.CANCELLING;
    }

    public synchronized boolean enterCommitPhase() {
        if (currentState == State.RUNNING) {
            this.inCommitPhase = true;
            return true;
        }
        return false;
    }

    public synchronized boolean isInCommitPhase() {
        return inCommitPhase;
    }

    public synchronized boolean isThresholdReached() {
        return thresholdReached;
    }

    public synchronized long getCurrentExecutionId() {
        return currentExecutionId;
    }

    public void setProgressHandlers(Consumer<String> showProgressHandler, Runnable hideProgressHandler) {
        setProgressHandlers(showProgressHandler, null, hideProgressHandler);
    }

    public void setProgressHandlers(
            Consumer<String> showProgressHandler,
            Consumer<ProgressDetails> updateProgressHandler,
            Runnable hideProgressHandler
    ) {
        this.showProgressHandler = showProgressHandler;
        this.updateProgressHandler = updateProgressHandler;
        this.hideProgressHandler = hideProgressHandler;
    }

    public void reportProgress(long executionId, String opName, long bytesProcessed, long totalBytes) {
        synchronized (this) {
            if (executionId != currentExecutionId || currentState != State.RUNNING) {
                return; // Stale monitor or non-running execution
            }
            this.latestBytesProcessed = bytesProcessed;
            this.latestTotalBytes = totalBytes;
            if (opName != null && !opName.isBlank()) {
                this.currentOperationName = opName;
            }

            if (!thresholdReached) {
                return; // Do NOT dispatch visual updates before 400ms threshold
            }

            long now = System.currentTimeMillis();
            if (now - lastFxUpdateTimestampMs < 120) {
                return; // Throttled: max 1 FX update per 120ms
            }
            lastFxUpdateTimestampMs = now;
        }

        dispatchProgressToFX(executionId);
    }

    private void dispatchProgressToFX(long executionId) {
        final String opName;
        final long processed;
        final long total;
        final long elapsed;
        final String formatted;
        final Consumer<ProgressDetails> updateHandler;

        synchronized (this) {
            if (executionId != currentExecutionId || currentState != State.RUNNING || updateProgressHandler == null) {
                return;
            }
            opName = this.currentOperationName;
            processed = this.latestBytesProcessed;
            total = this.latestTotalBytes;
            elapsed = Math.max(0, System.currentTimeMillis() - this.startTimeMs);
            formatted = formatProgressText(opName, processed, total, elapsed);
            updateHandler = this.updateProgressHandler;
        }

        Platform.runLater(() -> {
            synchronized (OperationExecutor.this) {
                if (executionId != currentExecutionId || currentState != State.RUNNING || updateProgressHandler == null) {
                    return; // Strictly accept ONLY RUNNING state for the current executionId
                }
            }
            try {
                updateHandler.accept(new ProgressDetails(opName, processed, total, elapsed, formatted));
            } catch (Exception ignored) {}
        });
    }

    public <T> void execute(
            String operationName,
            Button triggerButton,
            Callable<T> task,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            Runnable onCancelled
    ) {
        Objects.requireNonNull(task, "Task callable cannot be null");
        executeWithProgress(operationName, triggerButton, monitor -> task.call(), onSuccess, onFailure, onCancelled);
    }

    public <T> void executeWithProgress(
            String operationName,
            Button triggerButton,
            ProgressTask<T> task,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            Runnable onCancelled
    ) {
        Objects.requireNonNull(task, "ProgressTask cannot be null");

        final long executionId;
        final ProgressMonitor progressMonitor;

        synchronized (this) {
            if (currentState != State.IDLE) {
                IllegalStateException ex = new IllegalStateException("An operation is already running or cancelling");
                if (onFailure != null) {
                    Platform.runLater(() -> onFailure.accept(ex));
                }
                return;
            }

            this.currentState = State.RUNNING;
            this.currentExecutionId++;
            executionId = this.currentExecutionId;
            this.currentTriggerButton = triggerButton;
            this.activeOnCancelled = onCancelled;
            this.activeWorkerStarted = false;
            this.inCommitPhase = false;
            this.thresholdReached = false;

            this.currentOperationName = (operationName != null && !operationName.isBlank()) ? operationName : "Operation";
            this.startTimeMs = System.currentTimeMillis();
            this.latestBytesProcessed = 0;
            this.latestTotalBytes = 0;
            this.lastFxUpdateTimestampMs = 0;

            progressMonitor = new ProgressMonitor() {
                @Override
                public void updateProgress(long bytesProcessed, long totalBytes) {
                    OperationExecutor.this.reportProgress(executionId, operationName, bytesProcessed, totalBytes);
                }

                @Override
                public boolean isCancelled() {
                    return OperationExecutor.this.isCancelled();
                }
            };
        }

        if (triggerButton != null) {
            triggerButton.setDisable(true);
        }

        // Synchronous deterministic execution for test mode
        if ("true".equals(System.getProperty("test.mode"))) {
            try {
                T result = task.run(progressMonitor);
                finishExecution(executionId, result, null, false, onSuccess, onFailure, onCancelled);
            } catch (Throwable t) {
                finishExecution(executionId, null, t, false, onSuccess, onFailure, onCancelled);
            }
            return;
        }

        // Schedule 400ms progress threshold
        delayFuture = timerExecutor.schedule(() -> {
            synchronized (OperationExecutor.this) {
                if (executionId == currentExecutionId && currentState == State.RUNNING) {
                    thresholdReached = true;
                    if (showProgressHandler != null) {
                        Platform.runLater(() -> {
                            synchronized (OperationExecutor.this) {
                                if (executionId == currentExecutionId && currentState == State.RUNNING && showProgressHandler != null) {
                                    showProgressHandler.accept(currentOperationName);
                                }
                            }
                        });
                    }

                    // Dispatch accumulated progress immediately when 400ms threshold is reached
                    dispatchProgressToFX(executionId);

                    // Start 1s periodic elapsed time timer
                    timerFuture = timerExecutor.scheduleAtFixedRate(() -> {
                        dispatchProgressToFX(executionId);
                    }, 1000, 1000, TimeUnit.MILLISECONDS);
                }
            }
        }, 400, TimeUnit.MILLISECONDS);

        currentFuture = workerExecutor.submit(() -> {
            synchronized (OperationExecutor.this) {
                if (executionId != currentExecutionId || currentState != State.RUNNING) {
                    return; // Pre-start cancellation: abort without calling task.run()
                }
                activeWorkerStarted = true;
            }

            T result = null;
            Throwable error = null;
            boolean cancelled = Thread.currentThread().isInterrupted() || isCancelled();

            if (!cancelled) {
                try {
                    result = task.run(progressMonitor);
                } catch (InterruptedException | CancellationException e) {
                    cancelled = true;
                } catch (Throwable t) {
                    if (t.getCause() instanceof InterruptedException || t.getCause() instanceof CancellationException) {
                        cancelled = true;
                    } else {
                        error = t;
                    }
                }
            }

            if (Thread.currentThread().isInterrupted() || isCancelled()) {
                cancelled = true;
            }

            final T finalResult = result;
            final Throwable finalError = error;
            final boolean finalCancelled = cancelled;

            Platform.runLater(() -> finishExecution(executionId, finalResult, finalError, finalCancelled, onSuccess, onFailure, onCancelled));
        });
    }

    private <T> void finishExecution(
            long executionId,
            T result,
            Throwable error,
            boolean cancelled,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            Runnable onCancelled
    ) {
        Consumer<T> successToInvoke = null;
        Consumer<Throwable> failureToInvoke = null;
        Runnable cancelledToInvoke = null;
        Button buttonToRestore = null;
        Runnable hideProgressToRun = null;
        ProgressDetails final100Details = null;

        synchronized (this) {
            if (executionId != currentExecutionId || currentState == State.IDLE) {
                // Ignore late callback from a previous execution or already finished task
                return;
            }

            if (delayFuture != null) {
                delayFuture.cancel(false);
                delayFuture = null;
            }
            if (timerFuture != null) {
                timerFuture.cancel(false);
                timerFuture = null;
            }

            boolean completedDuringCommit = inCommitPhase;
            boolean wasCancelling = !completedDuringCommit && (currentState == State.CANCELLING || cancelled);
            boolean wasThresholdReached = thresholdReached;

            if (wasThresholdReached && !wasCancelling && error == null && latestTotalBytes > 0) {
                long elapsed = Math.max(0, System.currentTimeMillis() - this.startTimeMs);
                String formatted = formatProgressText(this.currentOperationName, this.latestTotalBytes, this.latestTotalBytes, elapsed);
                final100Details = new ProgressDetails(this.currentOperationName, this.latestTotalBytes, this.latestTotalBytes, elapsed, formatted);
            }

            currentState = State.IDLE;
            inCommitPhase = false;
            thresholdReached = false;

            buttonToRestore = currentTriggerButton;
            currentTriggerButton = null;

            if (wasThresholdReached) {
                hideProgressToRun = hideProgressHandler;
            }

            if (wasCancelling || (currentFuture != null && currentFuture.isCancelled() && !completedDuringCommit)) {
                currentFuture = null;
                cancelledToInvoke = onCancelled != null ? onCancelled : activeOnCancelled;
                activeOnCancelled = null;
            } else {
                currentFuture = null;
                activeOnCancelled = null;
                if (error != null) {
                    failureToInvoke = onFailure;
                } else {
                    successToInvoke = onSuccess;
                }
            }
        }

        // Callbacks invoked OUTSIDE synchronized block in strict order:
        // 1. final 100% update (if task succeeded and threshold was reached)
        // 2. hide progress bar
        // 3. restore trigger button
        // 4. completion callback (onCancelled / onFailure / onSuccess)
        if (final100Details != null && updateProgressHandler != null) {
            try { updateProgressHandler.accept(final100Details); } catch (Exception ignored) {}
        }
        if (hideProgressToRun != null) {
            try { hideProgressToRun.run(); } catch (Exception ignored) {}
        }
        if (buttonToRestore != null) {
            try { buttonToRestore.setDisable(false); } catch (Exception ignored) {}
        }
        if (cancelledToInvoke != null) {
            try { cancelledToInvoke.run(); } catch (Exception ignored) {}
        } else if (failureToInvoke != null) {
            try { failureToInvoke.accept(error); } catch (Exception ignored) {}
        } else if (successToInvoke != null) {
            try { successToInvoke.accept(result); } catch (Exception ignored) {}
        }
    }

    public synchronized boolean cancelCurrentOperation() {
        if (currentState == State.RUNNING) {
            if (inCommitPhase) {
                return false; // Ignore cancel during un-cancellable promotion commit phase
            }
            currentState = State.CANCELLING;
            final long targetExecutionId = currentExecutionId;
            final boolean started = activeWorkerStarted;
            final Runnable cancelCallback = activeOnCancelled;

            if (delayFuture != null) {
                delayFuture.cancel(false);
                delayFuture = null;
            }
            if (timerFuture != null) {
                timerFuture.cancel(false);
                timerFuture = null;
            }
            if (currentFuture != null) {
                currentFuture.cancel(true);
            }

            // If worker hasn't started running, force finish execution on FX thread
            if (!started) {
                Platform.runLater(() -> finishExecution(targetExecutionId, null, null, true, null, null, cancelCallback));
            }
            return true;
        }
        return false;
    }

    public synchronized void shutdown() {
        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.shutdownNow();
        }
        if (timerExecutor != null && !timerExecutor.isShutdown()) {
            timerExecutor.shutdownNow();
        }
        currentState = State.IDLE;
        inCommitPhase = false;
        thresholdReached = false;
        showProgressHandler = null;
        updateProgressHandler = null;
        hideProgressHandler = null;
    }

    void submitRawTaskForTest(Runnable runnable) {
        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.submit(runnable);
        }
    }

    public static String formatProgressText(String operationName, long bytesProcessed, long totalBytes, long elapsedTimeMs) {
        long seconds = elapsedTimeMs / 1000;
        String timeStr = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
        String name = (operationName != null && !operationName.isBlank()) ? operationName : "Operation";

        if (totalBytes > 0) {
            double ratio = Math.min(1.0, (double) bytesProcessed / totalBytes);
            int percent = (int) Math.round(ratio * 100);
            return name + "… " + percent + "% · " + formatBytes(bytesProcessed) + " / " + formatBytes(totalBytes) + " · " + timeStr;
        } else {
            return name + "… · " + timeStr;
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
