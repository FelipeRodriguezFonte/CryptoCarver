package com.cryptocarver.model.process;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public record ExecutionContext(
    FileWritePolicy fileWritePolicy,
    Consumer<NodeExecutionEvent> eventListener,
    BooleanSupplier cancellationRequested
) {
    public ExecutionContext(FileWritePolicy fileWritePolicy, Consumer<NodeExecutionEvent> eventListener) {
        this(fileWritePolicy, eventListener, null);
    }

    public boolean isCancelled() {
        return cancellationRequested != null && cancellationRequested.getAsBoolean();
    }
}
