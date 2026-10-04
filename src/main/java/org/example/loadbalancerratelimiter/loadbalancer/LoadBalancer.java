package org.example.loadbalancerratelimiter.loadbalancer;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple ROUND-ROBIN Load Balancer.
 *
 * Request 1 -> Server 1, Request 2 -> Server 2,
 * Request 3 -> Server 3, Request 4 -> Server 1, ...
 *
 * Thread-safe via AtomicInteger so concurrent demo clicks work.
 */
@Service
public class LoadBalancer {

    private final List<BackendServer> servers = List.of(
            new BackendServer("1", "Backend Server 1"),
            new BackendServer("2", "Backend Server 2"),
            new BackendServer("3", "Backend Server 3")
    );

    private final AtomicInteger currentIndex = new AtomicInteger(0);

    /** Pick next HEALTHY server in round-robin order. */
    public BackendServer getNextServer() {
        for (int i = 0; i < servers.size(); i++) {
            int idx = Math.abs(currentIndex.getAndIncrement() % servers.size());
            BackendServer s = servers.get(idx);
            if (s.isHealthy()) {
                return s;
            }
        }
        // all unhealthy -> just return first (demo fallback)
        return servers.get(0);
    }

    // kept for backward compatibility with old controller code
    public String getNextServerName() {
        return getNextServer().getName();
    }

    public List<BackendServer> getServers() {
        return servers;
    }

    public void resetCounts() {
        servers.forEach(BackendServer::resetCount);
        currentIndex.set(0);
    }
}
