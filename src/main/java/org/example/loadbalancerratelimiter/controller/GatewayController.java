package org.example.loadbalancerratelimiter.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.loadbalancerratelimiter.loadbalancer.BackendServer;
import org.example.loadbalancerratelimiter.loadbalancer.LoadBalancer;
import org.example.loadbalancerratelimiter.ratelimiter.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * THE MAIN DEMO ENTRY POINT.
 *
 * Flow for every request:
 *   Client -> Gateway (/api/request) -> RateLimiter check -> LoadBalancer -> Backend Server
 *
 * 1. Rate limiter: max 5 requests / 10 sec per user. Excess -> HTTP 429.
 * 2. Load balancer: round-robin across Server 1, 2, 3.
 */
@RestController
@RequestMapping("/api")
public class GatewayController {

    private final RateLimiter rateLimiter;
    private final LoadBalancer loadBalancer;

    public GatewayController(RateLimiter rateLimiter, LoadBalancer loadBalancer) {
        this.rateLimiter = rateLimiter;
        this.loadBalancer = loadBalancer;
    }

    @GetMapping("/request")
    public ResponseEntity<Map<String, Object>> handleRequest(
            @RequestParam(required = false) String user,
            HttpServletRequest http) {

        // Identify client: ?user=alice, otherwise fall back to IP
        String clientId = (user == null || user.isBlank())
                ? http.getRemoteAddr()
                : user.trim();

        // STEP 1: rate limit
        if (!rateLimiter.allowRequest(clientId)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 429);
            body.put("message", "RATE LIMITED: '" + clientId + "' sent more than "
                    + RateLimiter.LIMIT + " requests in 10 seconds. Wait and retry.");
            body.put("user", clientId);
            body.put("tokensLeft", 0);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
        }

        // STEP 2: load balance
        BackendServer server = loadBalancer.getNextServer();
        String backendReply = server.handleRequest();

        // STEP 3: respond
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 200);
        body.put("message", "ALLOWED by rate-limiter, forwarded by load-balancer");
        body.put("user", clientId);
        body.put("servedBy", server.getName());
        body.put("backendReply", backendReply);
        body.put("tokensLeft", rateLimiter.tokensLeft(clientId));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/test")
    public String test() {
        return "API Gateway is working! Try /api/request?user=alice";
    }
}
