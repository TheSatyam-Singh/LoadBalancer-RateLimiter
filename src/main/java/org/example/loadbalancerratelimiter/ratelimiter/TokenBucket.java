package org.example.loadbalancerratelimiter.ratelimiter;

/**
 * Simple Token Bucket.
 * Each client gets a bucket with e.g. 5 tokens.
 * Every request costs 1 token. Tokens refill over time.
 * If bucket is empty -> request is rejected (HTTP 429).
 */
public class TokenBucket {

    private final int capacity;
    private double tokens;
    private final double refillTokensPerMillis;
    private long lastRefillTime;

    public TokenBucket(int capacity, int refillTokens, long refillPeriodMillis) {
        this.capacity = capacity;
        this.tokens = capacity; // start full
        this.refillTokensPerMillis = (double) refillTokens / refillPeriodMillis;
        this.lastRefillTime = System.currentTimeMillis();
    }

    /** Try to take 1 token. Returns true if allowed, false if rate-limited. */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillTokensPerMillis);
            lastRefillTime = now;
        }
    }

    public synchronized int availableTokens() {
        refill();
        return (int) tokens;
    }

    public int getCapacity() {
        return capacity;
    }
}
