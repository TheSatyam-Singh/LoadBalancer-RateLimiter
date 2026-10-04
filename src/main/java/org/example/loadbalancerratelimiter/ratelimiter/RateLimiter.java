package org.example.loadbalancerratelimiter.ratelimiter;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiterService - keeps one TokenBucket per client (per user / IP).
 *
 * Demo config: 5 requests per 10 seconds per user.
 * Change LIMIT / WINDOW to tune the demo.
 */
@Service
public class RateLimiter {

    public static final int LIMIT = 5;               // max requests ...
    public static final long WINDOW_MILLIS = 10_000; // ... per 10 seconds

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** Returns true if request is allowed, false if client is rate-limited. */
    public boolean allowRequest(String clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(
                clientId,
                id -> new TokenBucket(LIMIT, LIMIT, WINDOW_MILLIS)
        );
        return bucket.tryConsume();
    }

    public int tokensLeft(String clientId) {
        TokenBucket bucket = buckets.get(clientId);
        return bucket == null ? LIMIT : bucket.availableTokens();
    }

    public void reset(String clientId) {
        buckets.remove(clientId);
    }

    public void resetAll() {
        buckets.clear();
    }
}
