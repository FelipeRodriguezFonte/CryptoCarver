package com.cryptoforge.util;

/**
 * Interface to monitor progress and request cancellation of long-running streaming operations.
 */
public interface ProgressMonitor {
    
    /**
     * Called periodically during an operation to update the number of bytes processed.
     * @param bytesProcessed The total number of bytes processed so far.
     * @param totalBytes The expected total bytes, or -1 if unknown.
     */
    void updateProgress(long bytesProcessed, long totalBytes);

    /**
     * Checks whether the user or system has requested the operation to be cancelled.
     * @return true if cancelled, false otherwise.
     */
    boolean isCancelled();

    /**
     * A no-op ProgressMonitor for operations that do not need tracking.
     */
    ProgressMonitor NO_OP = new ProgressMonitor() {
        @Override
        public void updateProgress(long bytesProcessed, long totalBytes) { }

        @Override
        public boolean isCancelled() { return false; }
    };
}
