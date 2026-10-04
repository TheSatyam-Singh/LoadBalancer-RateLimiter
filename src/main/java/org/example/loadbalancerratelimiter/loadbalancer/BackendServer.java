package org.example.loadbalancerratelimiter.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

/** Represents one fake backend server + how many requests it handled. */
public class BackendServer {

    private final String id;
    private final String name;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile boolean healthy = true;

    public BackendServer(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String handleRequest() {
        int n = requestCount.incrementAndGet();
        return "Response from " + name + " (it has served " + n + " requests)";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getRequestCount() { return requestCount.get(); }
    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }
    public void resetCount() { requestCount.set(0); }
}
