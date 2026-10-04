package org.example.loadbalancerratelimiter.controller;

import org.example.loadbalancerratelimiter.ratelimiter.RateLimiter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure rate-limiter demo (no load balancing here). */
@RestController
@RequestMapping("/ratelimit")
public class RateLimitController {

    private final RateLimiter rateLimiter;

    public RateLimitController(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/request")
    public Map<String, Object> tryRequest(@RequestParam(defaultValue = "demo-user") String user) {
        boolean allowed = rateLimiter.allowRequest(user);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", user);
        out.put("allowed", allowed);
        out.put("tokensLeft", rateLimiter.tokensLeft(user));
        out.put("limit", RateLimiter.LIMIT + " requests per 10 seconds");
        if (!allowed) {
            out.put("message", "BLOCKED (HTTP 429 would be returned by gateway)");
        }
        return out;
    }

    @PostMapping("/reset")
    public String reset(@RequestParam(required = false) String user) {
        if (user == null) {
            rateLimiter.resetAll();
            return "All rate-limiter buckets cleared.";
        }
        rateLimiter.reset(user);
        return "Bucket cleared for '" + user + "'.";
    }
}
