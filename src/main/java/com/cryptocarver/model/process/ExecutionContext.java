package com.cryptocarver.model.process;

import java.util.function.Consumer;

public record ExecutionContext(
    FileWritePolicy fileWritePolicy,
    Consumer<NodeExecutionEvent> eventListener
) {
}
