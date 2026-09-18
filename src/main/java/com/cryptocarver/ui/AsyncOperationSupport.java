package com.cryptocarver.ui;

import javafx.application.Platform;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/** Small adapter for migrating isolated controller operations to async execution. */
public final class AsyncOperationSupport {
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool(daemonFactory());

    private AsyncOperationSupport() {
    }

    /** Submits work to a daemon pool and never runs the work on the JavaFX thread. */
    public static <T> CompletableFuture<T> submit(Callable<T> work) {
        return submit(work, DEFAULT_EXECUTOR);
    }

    public static <T> CompletableFuture<T> submit(Callable<T> work, Executor executor) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Throwable failure) {
                throw new CompletionException(failure);
            }
        }, executor);
    }

    /** Adds callbacks with an explicit JavaFX-thread delivery boundary. */
    public static <T> CompletableFuture<T> submit(
            Callable<T> work,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure
    ) {
        CompletableFuture<T> result = submit(work);
        result.whenComplete((value, failure) -> runOnFxThread(() -> {
            if (failure == null) {
                if (onSuccess != null) onSuccess.accept(value);
            } else if (onFailure != null) {
                onFailure.accept(unwrap(failure));
            }
        }));
        return result;
    }

    public static void runOnFxThread(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "cryptocarver-async-support");
            thread.setDaemon(true);
            return thread;
        };
    }
}
