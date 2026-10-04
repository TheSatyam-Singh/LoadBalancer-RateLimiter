package org.example.loadbalancerratelimiter.controller;

import org.example.loadbalancerratelimiter.loadbalancer.BackendServer;
import org.example.loadbalancerratelimiter.loadbalancer.LoadBalancer;
import org.example.loadbalancerratelimiter.ratelimiter.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Load-balancer demo + rate-limit info (JSON pretty-printed). */
@RestController
@RequestMapping("/lb")
public class LoadBalancerController {

    private final LoadBalancer loadBalancer;
    private final RateLimiter rateLimiter;

    public LoadBalancerController(LoadBalancer loadBalancer, RateLimiter rateLimiter) {
        this.loadBalancer = loadBalancer;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/request")
    public ResponseEntity<Map<String, Object>> balanceRequest(
            @RequestParam(defaultValue = "demo-user") String user) {
        if (!rateLimiter.allowRequest(user)) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", 429);
            out.put("message", "Error: rate limit exceeded. Try again later.");
            out.put("user", user);
            out.put("servedBy", null);
            out.put("limit", RateLimiter.LIMIT + " requests per 10 seconds");
            out.put("remaining", 0);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(out);
        }

        BackendServer server = loadBalancer.getNextServer();
        server.handleRequest();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 200);
        out.put("message", "Success");
        out.put("user", user);
        out.put("servedBy", server.getName());
        out.put("limit", RateLimiter.LIMIT + " requests per 10 seconds");
        out.put("remaining", rateLimiter.tokensLeft(user));
        return ResponseEntity.ok(out);
    }

    // keep old URL working
    @GetMapping("/loadbalance")
    public String balanceRequestLegacy() {
        return "Request forwarded to " + loadBalancer.getNextServerName();
    }

    /** Shows how many requests each server handled -> proves round-robin. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (BackendServer s : loadBalancer.getServers()) {
            out.put(s.getName(), s.getRequestCount() + " requests");
        }
        return out;
    }

    @PostMapping("/reset")
    public String reset() {
        loadBalancer.resetCounts();
        rateLimiter.resetAll();
        return "Load balancer counters reset.";
    }
}
