package app.morphe.extension.tiktok.download;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

/** Shared transfer, disk and deadline limits for extension-owned media jobs. */
final class MediaBudget {
    static final long MAX_TRANSFER_BYTES = 512L * 1024 * 1024;
    static final long MIN_FREE_BYTES = 32L * 1024 * 1024;
    static final long UNKNOWN_TRANSFER_RESERVATION_BYTES = 64L * 1024 * 1024;
    static final long PUBLISH_OVERHEAD_BYTES = 8L * 1024 * 1024;
    static final long JOB_DEADLINE_MS = 2L * 60 * 1000;
    static final int MAX_ATTEMPTS_PER_MIRROR = 2;
    static final long DEFAULT_RETRY_DELAY_MS = 250L;
    static final long MAX_RETRY_DELAY_MS = 2_000L;
    private static final ThreadLocal<Deadline> CURRENT_DEADLINE = new ThreadLocal<>();

    private MediaBudget() {}

    static Deadline deadline() {
        Deadline current = CURRENT_DEADLINE.get();
        if (current != null) return current;
        return new Deadline(System.nanoTime() + JOB_DEADLINE_MS * 1_000_000L);
    }

    static void runWithJobDeadline(Runnable work) {
        Deadline previous = CURRENT_DEADLINE.get();
        if (previous == null) CURRENT_DEADLINE.set(deadline());
        try {
            work.run();
        } finally {
            if (previous == null) CURRENT_DEADLINE.remove();
            else CURRENT_DEADLINE.set(previous);
        }
    }

    static void check(Deadline deadline) throws IOException {
        if (deadline == null) deadline = CURRENT_DEADLINE.get();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Media job cancelled");
        }
        if (deadline != null && deadline.expired()) {
            throw new IOException("Media job deadline exceeded");
        }
    }

    static int timeoutMillis(Deadline deadline, int configuredMillis) throws IOException {
        check(deadline);
        Deadline active = deadline == null ? CURRENT_DEADLINE.get() : deadline;
        if (active == null) return configuredMillis;
        long remainingMs = Math.max(1L,
                (active.endNanos - System.nanoTime() + 999_999L) / 1_000_000L);
        return (int) Math.min(configuredMillis, Math.min(Integer.MAX_VALUE, remainingMs));
    }

    static boolean isRetryableTransport(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof java.net.ConnectException) {
                return true;
            }
        }
        return false;
    }

    static boolean isCancellation(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException && !(current instanceof SocketTimeoutException)) {
                return true;
            }
        }
        return false;
    }

    static void checkTransferLength(long length) throws IOException {
        if (length > MAX_TRANSFER_BYTES) {
            throw new IOException("Media file is larger than the 512 MB limit");
        }
    }

    static void checkDiskSpace(File directory, long transferBytes) throws IOException {
        checkDiskSpace(directory, transferBytes, null);
    }

    static void checkDiskSpace(File directory, long transferBytes, Deadline deadline) throws IOException {
        check(deadline);
        checkTransferLength(transferBytes);
        if (directory == null) return;
        long free = directory.getUsableSpace();
        if (free <= 0) return;
        long estimate = transferBytes < 0
                ? UNKNOWN_TRANSFER_RESERVATION_BYTES
                : transferBytes;
        long required = estimate + PUBLISH_OVERHEAD_BYTES + MIN_FREE_BYTES;
        if (free < required) {
            throw new IOException("Not enough free space for this media");
        }
    }

    static boolean isTransientStatus(int status) {
        return status == 408 || status == 425 || status == 429
                || status == 500 || status == 502 || status == 503 || status == 504;
    }

    static long retryAfterMillis(String header, int attempt) {
        long fallback = Math.min(MAX_RETRY_DELAY_MS,
                DEFAULT_RETRY_DELAY_MS << Math.min(3, Math.max(0, attempt)));
        if (header == null) return fallback;
        try {
            long seconds = Long.parseLong(header.trim());
            if (seconds < 0) return fallback;
            long maxSeconds = MAX_RETRY_DELAY_MS / 1000L;
            return seconds > maxSeconds ? MAX_RETRY_DELAY_MS : seconds * 1000L;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static void waitBeforeRetry(String retryAfter, int attempt, Deadline deadline) throws IOException {
        if (deadline == null) deadline = CURRENT_DEADLINE.get();
        long delay = retryAfterMillis(retryAfter, attempt);
        if (deadline != null) {
            long remainingMs = Math.max(0L,
                    (deadline.endNanos - System.nanoTime()) / 1_000_000L);
            delay = Math.min(delay, remainingMs);
        }
        check(deadline);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Media job cancelled");
        }
        check(deadline);
    }

    static final class Deadline {
        private final long endNanos;

        private Deadline(long endNanos) {
            this.endNanos = endNanos;
        }

        boolean expired() {
            return System.nanoTime() >= endNanos;
        }
    }
}
