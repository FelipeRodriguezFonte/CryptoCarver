package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;

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

    private final ExecutorService workerExecutor;
    private final ScheduledExecutorService timerExecutor;

    private State currentState = State.IDLE;
    private long currentExecutionId = 0;
    private Future<?> currentFuture;
    private ScheduledFuture<?> delayFuture;
    private Button currentTriggerButton;
    private Consumer<String> showProgressHandler;
    private Runnable hideProgressHandler;
    private Runnable activeOnCancelled;
    private boolean activeWorkerStarted = false;
    private boolean inCommitPhase = false;

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

    public synchronized long getCurrentExecutionId() {
        return currentExecutionId;
    }

    public void setProgressHandlers(Consumer<String> showProgressHandler, Runnable hideProgressHandler) {
        this.showProgressHandler = showProgressHandler;
        this.hideProgressHandler = hideProgressHandler;
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

        final long executionId;
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
        }

        if (triggerButton != null) {
            triggerButton.setDisable(true);
        }

        // Synchronous deterministic execution for test mode
        if ("true".equals(System.getProperty("test.mode"))) {
            try {
                T result = task.call();
                finishExecution(executionId, result, null, false, onSuccess, onFailure, onCancelled);
            } catch (Throwable t) {
                finishExecution(executionId, null, t, false, onSuccess, onFailure, onCancelled);
            }
            return;
        }

        // Schedule 400ms progress threshold
        delayFuture = timerExecutor.schedule(() -> {
            synchronized (OperationExecutor.this) {
                if (executionId == currentExecutionId && currentState == State.RUNNING && showProgressHandler != null) {
                    Platform.runLater(() -> {
                        synchronized (OperationExecutor.this) {
                            if (executionId == currentExecutionId && currentState == State.RUNNING && showProgressHandler != null) {
                                showProgressHandler.accept(operationName != null ? operationName : "Operation");
                            }
                        }
                    });
                }
            }
        }, 400, TimeUnit.MILLISECONDS);

        currentFuture = workerExecutor.submit(() -> {
            synchronized (OperationExecutor.this) {
                if (executionId != currentExecutionId || currentState != State.RUNNING) {
                    return; // Pre-start cancellation: abort without calling task.call()
                }
                activeWorkerStarted = true;
            }

            T result = null;
            Throwable error = null;
            boolean cancelled = Thread.currentThread().isInterrupted() || isCancelled();

            if (!cancelled) {
                try {
                    result = task.call();
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

        synchronized (this) {
            if (executionId != currentExecutionId || currentState == State.IDLE) {
                // Ignore late callback from a previous execution or already finished task
                return;
            }

            if (delayFuture != null) {
                delayFuture.cancel(false);
                delayFuture = null;
            }

            boolean completedDuringCommit = inCommitPhase;
            boolean wasCancelling = !completedDuringCommit && (currentState == State.CANCELLING || cancelled);

            currentState = State.IDLE;
            inCommitPhase = false;
            hideProgressToRun = hideProgressHandler;
            buttonToRestore = currentTriggerButton;
            currentTriggerButton = null;

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

        // Callbacks invoked OUTSIDE synchronized block
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
    }

    void submitRawTaskForTest(Runnable runnable) {
        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.submit(runnable);
        }
    }
}
