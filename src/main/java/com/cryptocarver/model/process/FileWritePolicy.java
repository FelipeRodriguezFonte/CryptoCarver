package com.cryptocarver.model.process;

/** Policy for determining how to handle file writing when the target file already exists. */
public enum FileWritePolicy {
    FAIL_IF_EXISTS,
    ALLOW_OVERWRITE
}
