# LoadBalancer + RateLimiter — Complete Interview Guide

> Spring Boot API Gateway prototype that combines **per-user rate limiting** + **round-robin load balancing** in one request path.
> Use this file to learn the project cold and present it confidently in an interview.

---

## 1. The 30-Second Pitch (memorize this)

> "I built an API Gateway in Spring Boot that sits in front of 3 backend servers. Every request first goes through a per-user **Token Bucket rate limiter (5 requests / 10 sec)** — excess gets HTTP 429. Allowed requests are then distributed with a **thread-safe Round-Robin load balancer**. It's a single-process demo with a live UI, but the patterns — gateway filtering, token bucket, round-robin with health check — are exactly what Nginx, Cloudflare, and AWS ALB do at scale."

That one paragraph hits: problem, solution, algorithms, and real-world mapping.

---

## 2. What Problem Does This Solve?

In production, if 1 client spams you or traffic unevenly hits 1 server:

- servers get overloaded / crash,
- one abusive user starves everyone else,
- no fair usage, no DDoS protection.

This project solves both at the **gateway layer**:

1. **Rate Limiter** → protects backends from abuse (fairness + stability).
2. **Load Balancer** → spreads allowed traffic evenly (utilization + availability).

---

## 3. Architecture

```
                    ┌────────────────── API GATEWAY ──────────────────┐
                    │                                                 │
  Client ── GET ──▶ │  GatewayController (/api/request?user=alice)    │
  (browser /        │        │                                        │
   curl)           │        ▼                                        │
                    │  RateLimiter (1 TokenBucket per user/IP)        │
                    │   allowed? ── NO ──▶ HTTP 429 + JSON            │
                    │        │ YES                                    │
                    │        ▼                                        │
                    │  LoadBalancer.getNextServer() [Round-Robin]     │
                    │   Server1 → Server2 → Server3 → Server1 …       │
                    └────────┬──────────────┬─────────────┬──────────┘
                             ▼              ▼             ▼
                       BackendServer1  BackendServer2  BackendServer3
                       (in-memory fake; counts requests served)
```

**Request lifecycle (`GatewayController.java:38-72`):**

1. Identify client: `?user=alice`, else fallback to `HttpServletRequest.getRemoteAddr()` (IP).
2. `rateLimiter.allowRequest(clientId)` → if `false`, return `429 TOO_MANY_REQUESTS`.
3. `loadBalancer.getNextServer()` → pick next healthy server.
4. `server.handleRequest()` → increment its counter, build reply.
5. Return `200 OK` JSON: `{ user, servedBy, backendReply, tokensLeft }`.

**Other entry points (isolated demos):**

| URL | Controller | Purpose |
|-----|------------|---------|
| `GET /api/request?user=alice` | `GatewayController` | **Main demo: RL + LB combined** |
| `GET /api/test` | `GatewayController` | Health check string |
| `GET /lb/request` | `LoadBalancerController` | LB only, no rate limit |
| `GET /lb/status` | `LoadBalancerController` | Proves round-robin: `{ "Backend Server 1": "N requests", ... }` |
| `POST /lb/reset` | `LoadBalancerController` | Reset counters |
| `GET /ratelimit/request?user=bob` | `RateLimitController` | RL only, returns `{ allowed, tokensLeft }` |
| `POST /ratelimit/reset?user=bob` | `RateLimitController` | Clear one/all buckets |
| `GET /server/1,2,3` | `BackendController` | Fake backends (would be separate machines in real life) |
| `GET /` (`index.html`) | static | Click-button UI: Send 1 / Spam 7 / Reset |

---

## 4. Tech Stack

| Layer | Choice | Why |
|-------|--------|-----|
| Language | Java 25 toolchain (`build.gradle:11-15`) | Latest LTS features, standard for backend interviews |
| Framework | Spring Boot 4.1.1 + `spring-boot-starter-web` | Auto-config, embedded Tomcat, `@RestController`/`@Service` DI — no XML |
| Build | Gradle (`build.gradle`, `settings.gradle`) | Standard; `./gradlew bootRun` runs on port 8080 |
| Concurrency | `AtomicInteger`, `ConcurrentHashMap`, `synchronized` | Thread-safe without manual locks (see §7) |
| Frontend | Single `static/index.html` (vanilla JS + fetch) | Zero-dependency live demo interviewers can click |
| Config | `application.properties`: `server.port=8080` | Single-process demo |

**File map:**

```
LoadBalancerRateLimiterApplication.java  → @SpringBootApplication entry
controller/GatewayController.java        → combined pipeline (THE file to walk through)
controller/LoadBalancerController.java   → LB-only demo + status/reset
controller/RateLimitController.java      → RL-only demo + reset
controller/BackendController.java        → 3 fake backends
loadbalancer/LoadBalancer.java           → @Service round-robin
loadbalancer/BackendServer.java          → id, name, AtomicInteger count, healthy flag
ratelimiter/RateLimiter.java             → @Service map user→bucket, LIMIT=5, WINDOW=10s
ratelimiter/TokenBucket.java             → token math + lazy refill
```

---

## 5. Algorithm #1 — Load Balancing: Round-Robin

**Code:** `loadbalancer/LoadBalancer.java:28-38`

```java
private final AtomicInteger currentIndex = new AtomicInteger(0);

public BackendServer getNextServer() {
    for (int i = 0; i < servers.size(); i++) {
        int idx = Math.abs(currentIndex.getAndIncrement() % servers.size());
        BackendServer s = servers.get(idx);
        if (s.isHealthy()) return s;   // skip unhealthy
    }
    return servers.get(0); // all down fallback
}
```

How it works: request N goes to `servers[N mod 3]`. Counter is atomic so 100 concurrent clicks never pick the same slot twice. Unhealthy servers are skipped (passive health check); `BackendServer.healthy` flag exists for future active checks.

**Complexity:** O(1) time per request (bounded loop over 3 servers), O(N) memory for server list.

### Why Round-Robin and NOT the others?

| Algorithm | How it works | Pros | Cons | Verdict for THIS project |
|-----------|--------------|------|------|--------------------------|
| **Round-Robin (chosen)** | Cycle 1→2→3→1… | O(1), dead simple, perfectly fair when servers equal, stateless, easy to demo with `/lb/status` | Ignores server load / capacity differences | ✅ Best fit: 3 identical fake servers, need provable even split for interview demo |
| Random | Pick uniform random | Simple, no state | Uneven short-term (clumping), non-deterministic → bad demo | ❌ Can't show clean 1-2-3-1 pattern |
| Weighted Round-Robin | Give big servers more turns (e.g. 2:1:1) | Handles heterogeneous capacity | Needs weight config + monitoring; overkill for equal servers | ❌ No capacity difference here |
| Least Connections | Track active conns, pick min | Great for long-lived / WebSocket / uneven request cost | Needs conn tracking; our requests are instant, so all counts ≈ 0 → degenerates to RR with extra code | ❌ No benefit, more state |
| Least Response Time / Latency-based | Pick fastest EWMA | Optimal for real geo-distributed fleets | Needs latency sampling, EWMA tuning; fake servers reply instantly | ❌ Over-engineering |
| IP Hash (`hash(ip) mod N`) | Same client → same server (sticky) | Session affinity / cache locality | Hot-spot if one user spams; breaks even spread — the exact thing the demo tries to *prove* | ❌ Conflicts with fairness story |
| Consistent Hashing | Hash ring, minimal remap on scale | Elastic add/remove nodes | Needed only when N changes often (Dynamo/Cassandra scale); N=3 fixed here | ❌ Unneeded complexity |

**Interview line:** *"I chose round-robin because all three backends are homogeneous and requests are uniform-cost — RR gives perfect fairness at O(1) with zero state. Weighted or least-connections only pay off with heterogeneous capacity or long-lived connections, which this demo doesn't have. I kept a health-skip loop so the design extends to real health checks."*

---

## 6. Algorithm #2 — Rate Limiting: Token Bucket (per user)

**Code:** `ratelimiter/TokenBucket.java:24-45`, `ratelimiter/RateLimiter.java:23-29`

```java
// RateLimiter: one bucket per client
buckets.computeIfAbsent(clientId,
    id -> new TokenBucket(LIMIT=5, refill=5, period=10_000ms));

// TokenBucket: lazy refill on each request
refillTokensPerMillis = 5 / 10000 = 0.0005 token/ms;
tokens = min(capacity, tokens + elapsed * refillTokensPerMillis);
// tryConsume: if tokens >= 1 → tokens--, allow; else reject (429)
```

How it works: each user starts with 5 tokens. Each request costs 1. Tokens refill continuously (~1 token per 2 sec). Burst of 5 passes instantly, 6th within 10 s → `false` → gateway returns 429. After waiting, partial refill allows again (smooth, not cliff).

**Complexity:** O(1) per request, O(U) memory for U distinct users.

### Why Token Bucket and NOT the others?

| Algorithm | How it works | Pros | Cons | Verdict |
|-----------|--------------|------|------|---------|
| **Token Bucket (chosen)** | Bucket holds N tokens, refills at rate r; request needs 1 token | ✅ Allows **bursts** (5 at once) + smooth refill; standard at Stripe/AWS; O(1), easy per-user map | Burst can briefly stress backend; needs clock + float math | ✅ Best UX: legit users burst, abusers throttled; refill math is interview-friendly |
| Leaky Bucket | Requests enter queue, leak out at fixed rate | Perfectly smooth output (shapes traffic) | Rejects/queues bursts even if backend idle; queue adds latency & memory; worse UX for bursty humans | ❌ Too rigid — punishes the normal "click 3 times fast" case |
| Fixed Window Counter | `count[user][window]++`, reset each 10 s | Simplest (Redis INCR+EXPIRE) | **Boundary spike**: 5 reqs at 9.9s + 5 at 10.1s = 10 in 0.2s allowed. Unfair cliff at reset | ❌ Classic interview trap — never pick without mentioning this flaw |
| Sliding Window Log | Store every timestamp, count last 10 s | Exact | O(requests) memory per user, needs sorted-set trim — heavy | ❌ Overkill for 5/10s demo |
| Sliding Window Counter | Weighted sum of prev + current window | Fixes boundary spike, Redis-friendly | More code (two windows + weights), no burst advantage over token bucket | Reasonable alternative at scale (Redis), but more complex to explain live |

**Interview line:** *"I chose token bucket over fixed-window because fixed-window allows a 2× boundary burst, and over leaky-bucket because I want to allow legitimate bursts — a user clicking 5 times fast shouldn't be punished. Token bucket gives burst + steady-state control in O(1), and maps 1:1 to real gateways like AWS API Gateway throttling."*

**Why 5 per 10 seconds?** Deliberately small so the interviewer sees 429 within 7 clicks (`Spam 7` button). In production you'd use e.g. 1000/min via Redis + headers (`X-RateLimit-Remaining`, `Retry-After`).

---

## 7. Concurrency & Thread Safety (interviewers LOVE this)

| Shared state | Protection | Why it works |
|--------------|------------|--------------|
| `LoadBalancer.currentIndex` | `AtomicInteger.getAndIncrement()` | Lock-free CAS; two threads never get same index; `mod size` wraps safely |
| `BackendServer.requestCount` | `AtomicInteger.incrementAndGet()` | Exact hit counts under concurrent load; `/lb/status` stays correct |
| `BackendServer.healthy` | `volatile boolean` | Visibility: health flips seen immediately across threads |
| `RateLimiter.buckets` | `ConcurrentHashMap` + `computeIfAbsent` | Safe per-user bucket creation under race; no lost buckets |
| `TokenBucket.tryConsume/refill` | `synchronized` methods | Check-then-act (`if tokens>=1 → tokens--`) is atomic; lazy refill using `System.currentTimeMillis()` delta |

**Follow-up answer:** single-instance `synchronized` + in-memory map does NOT work across multiple gateway replicas → real fix is distributed state (Redis + Lua `EVAL` for atomic consume, or Redis Cell `CL.THROTTLE`). Mention this unprompted for bonus points.

---

## 8. How to Run + Demo Script (5-minute interview flow)

```bash
./gradlew bootRun
# open http://localhost:8080/
```

**Live script:**

1. `GET /api/test` → "API Gateway is working!" (proves wiring).
2. UI: set user `alice`, click **Send 1 request** 3× → show `servedBy` rotates Server 1→2→3, `tokensLeft` 4→3→2.
3. Click **Spam 7 requests** → first ~2 pass, rest `⛔ 429 RATE-LIMITED`. Point at `tokensLeft: 0`.
4. Change user to `bob`, send 1 → **passes** (proves per-user isolation — buckets are per key, not global).
5. `GET /lb/status` → counts like `{"Backend Server 1":"3 requests", ...}` — proves round-robin fairness.
6. `POST /lb/reset` + `POST /ratelimit/reset` → clean slate.
7. Optional: `curl` loop for terminal lovers:
   ```bash
   for i in $(seq 1 7); do curl -s "http://localhost:8080/api/request?user=alice"; echo; done
   ```

---

## 9. Likely Interview Q&A (with short answers)

1. **Where does rate limiting happen — before or after LB, and why?**
   Before. Reject junk cheaply at gateway without wasting a backend pick. Code order in `GatewayController:49-60` proves it.
2. **Why HTTP 429?**
   Standard `TOO_MANY_REQUESTS`. Returns JSON with `Retry` guidance; production adds `Retry-After` header.
3. **Per-user vs per-IP?**
   `?user=` for demo clarity, IP fallback for anonymous. Production: API key / JWT `sub` claim (IPs are shared behind NAT).
4. **How do you scale beyond one JVM?**
   LB → register real hosts + active health checks + consistent hashing for elasticity. RL → Redis token bucket (Lua atomic), gateway replicas stateless.
5. **Round-robin weakness?**
   Assumes equal servers/requests. Fix: weighted RR (capacity) or least-connections (long requests).
6. **Thundering herd / counter overflow?**
   `AtomicInteger` wraps after 2B — `Math.abs()` guards negativity (note `Integer.MIN_VALUE` edge; production uses `getAndIncrement() & Integer.MAX_VALUE` or `AtomicLong`).
7. **How do you test it?**
   Unit: `TokenBucket` burst then reject then refill-after-sleep; `LoadBalancer` 6 calls → each server count == 2. Integration: spam 7 → expect 5×200 + 2×429. (Only `contextLoads` exists now — call this out as next work.)
8. **Fixed-window vs token bucket boundary problem?**
   Draw the 9.9s/10.1s spike example — shows you know the classic trap.
9. **Leaky vs token bucket in one sentence?**
   Leaky shapes to constant output (no bursts); token allows bursts up to capacity then steady rate.
10. **What would you add next?**
    Redis distributed limiting, `Retry-After`/`X-RateLimit-*` headers, weighted LB + active health checks, Micrometer metrics, per-endpoint limits, tests.

---

## 10. Limitations & Honest Next Steps (say this — honesty scores)

- In-memory only → restart wipes buckets/counters; multi-replica gateways disagree.
- No auth — `?user=` is spoofable; needs API key/JWT.
- No `Retry-After` headers, no per-endpoint tiers (free vs paid).
- Health flag exists but nothing flips it (no active prober).
- Fake backends are same-JVM method calls, not HTTP/load-tested.
- One test (`contextLoads`) — needs unit + integration tests.
- **Roadmap:** Redis+LUA rate limiter → active health checks + weighted RR → metrics/dashboard → per-route limits → Docker Compose with 3 real backend containers.

---

## 11. One-Page Cheat Sheet (glance before entering)

- Flow: **Client → Gateway → RateLimiter? → LoadBalancer → Backend**.
- RL: **TokenBucket, 5/10s/user, burst OK, O(1), `ConcurrentHashMap` + `synchronized`**. Beats fixed-window (boundary spike) & leaky (no burst).
- LB: **Round-Robin, `AtomicInteger mod N`, skip unhealthy, O(1)**. Beats random (clumpy), weighted/LC (unneeded for equal servers), IP-hash (hot spots).
- Demo: alice×3 rotates servers → spam 7 → 429s → bob still passes → `/lb/status` proves fairness.
- Scale story: Redis+LUA for RL, real health checks + weighted/consistent-hash for LB.
- Files to open live: `GatewayController.java` (flow), `TokenBucket.java` (refill math), `LoadBalancer.java` (atomic RR).
