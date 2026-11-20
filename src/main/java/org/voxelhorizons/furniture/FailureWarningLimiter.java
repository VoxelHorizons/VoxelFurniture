package org.voxelhorizons.furniture;

/**
 * Limits repeated warnings from periodic tasks without blocking their retries.
 *
 * The first failure, a changed failure, and a failure that persists past the
 * interval are always reported. A successful run should call reset() so a
 * subsequent regression is immediately visible.
 *
 * Accessed only from the server thread.
 */
public final class FailureWarningLimiter {
    private final long intervalMillis;
    private String lastFailure;
    private long lastWarningMillis;

    public FailureWarningLimiter(long intervalMillis) {
        if (intervalMillis <= 0L) throw new IllegalArgumentException("intervalMillis must be positive");
        this.intervalMillis = intervalMillis;
    }

    public boolean shouldWarn(String failure, long nowMillis) {
        if (failure == null) throw new IllegalArgumentException("failure must not be null");
        if (!failure.equals(lastFailure) || nowMillis < lastWarningMillis
                || nowMillis - lastWarningMillis >= intervalMillis) {
            lastFailure = failure;
            lastWarningMillis = nowMillis;
            return true;
        }
        return false;
    }

    public void reset() {
        lastFailure = null;
        lastWarningMillis = 0L;
    }
}
