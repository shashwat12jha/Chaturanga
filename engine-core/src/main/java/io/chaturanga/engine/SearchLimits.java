package io.chaturanga.engine;

/** Search limits. A zero value means the corresponding limit is disabled. */
public record SearchLimits(int depth, long moveTimeMillis, long nodeLimit) {
    public SearchLimits {
        if (depth < 0 || moveTimeMillis < 0 || nodeLimit < 0) {
            throw new IllegalArgumentException("Search limits cannot be negative");
        }
    }

    public static SearchLimits depth(int depth) {
        return new SearchLimits(depth, 0, 0);
    }

    public static SearchLimits moveTime(long milliseconds) {
        return new SearchLimits(64, milliseconds, 0);
    }
}
