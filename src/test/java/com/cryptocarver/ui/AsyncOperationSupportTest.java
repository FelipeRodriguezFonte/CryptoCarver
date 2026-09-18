package com.cryptocarver.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class AsyncOperationSupportTest {
    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }

    @Test
    void workRunsOffFxAndSuccessReturnsOnFx() throws Exception {
        CountDownLatch callback = new CountDownLatch(1);
        AtomicBoolean workOnFx = new AtomicBoolean(true);
        AtomicBoolean callbackOnFx = new AtomicBoolean(false);
        AtomicReference<String> result = new AtomicReference<>();

        AsyncOperationSupport.submit(
                () -> {
                    workOnFx.set(Platform.isFxApplicationThread());
                    return "done";
                },
                value -> {
                    callbackOnFx.set(Platform.isFxApplicationThread());
                    result.set(value);
                    callback.countDown();
                },
                failure -> fail(failure)
        );

        assertTrue(callback.await(5, TimeUnit.SECONDS));
        assertFalse(workOnFx.get());
        assertTrue(callbackOnFx.get());
        assertEquals("done", result.get());
    }

    @Test
    void failureIsUnwrappedBeforeFxCallback() throws Exception {
        CountDownLatch callback = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        AsyncOperationSupport.submit(
                () -> { throw new IllegalArgumentException("bad input"); },
                value -> fail("failure must not invoke success"),
                error -> {
                    failure.set(error);
                    callback.countDown();
                }
        );

        assertTrue(callback.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, failure.get());
        assertEquals("bad input", failure.get().getMessage());
    }
}
