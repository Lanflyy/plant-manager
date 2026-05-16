package extension.util;

public final class SleepRateLimit {
    // Default rate limit sleep in milliseconds. Can be changed at runtime via UI.
    private static volatile long RATE_LIMIT_MS = 250L;

    private SleepRateLimit() {}

    public static long getRateLimitMs() {
        return RATE_LIMIT_MS;
    }

    public static void setRateLimitMs(long ms) {
        RATE_LIMIT_MS = ms;
    }

    public static void sleepRateLimit() {
        sleepRateLimit(RATE_LIMIT_MS);
    }

    public static void sleepRateLimit(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
