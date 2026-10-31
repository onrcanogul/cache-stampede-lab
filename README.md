# cache-stampede-lab

A small, dependency-free lab that measures what happens to the database when a
hot cache key expires, and what each mitigation actually buys: single-flight,
distributed lock, stale-while-revalidate, probabilistic early refresh (XFetch),
TTL jitter and versioned writes.

Redis is real. The database is simulated: every application instance has its
own 10-connection pool and every statement takes a fixed amount of time. That is
enough for a stampede to show up.

The lab accompanies the post
[Cache Stampede: Popüler Bir Key'in Süresi Dolunca Veritabanına Ne Olur?](https://blog.onurcanogul.com/posts/cache-stampede-populer-bir-keyin-suresi-dolunca-veritabanina-ne-olur)
(Turkish). Every number in the post was produced by this repository.

## Requirements

- Java 21
- Maven 3.9+
- Docker (for Redis)

## Running

Start Redis:

```bash
docker compose up -d
```

Verify the behaviours as assertions:

```bash
mvn test
```

Run the four scenarios and print the tables:

```bash
mvn -q compile exec:java
```

A single scenario can be selected by id (`burst`, `sustained`, `jitter`,
`invalidation`):

```bash
mvn -q compile exec:java -Dexec.args="burst invalidation"
```

Redis location can be overridden with `REDIS_HOST`, `REDIS_PORT` and
`REDIS_POOL_SIZE` (default `localhost`, `6379`, `64`).

## Scenarios

The absolute numbers below come from one run on a laptop and will differ on
your machine. The shape of the results does not.

### 1. Expiry burst

3 instances x 10 connections, a 200 ms query. The hot key expires and 600
requests arrive at the same instant.

| | DB queries | Max concurrent | p50 | p99 |
|---|---:|---:|---:|---:|
| Naive cache-aside | 600 | 30 | 2043 ms | 4085 ms |
| Single-flight | 3 | 3 | 198 ms | 208 ms |
| Distributed lock | 1 | 1 | 270 ms | 289 ms |
| Stale-while-revalidate | 1 | 1 | 28 ms | 32 ms |

Naive cache-aside reads one value 600 times: 600 requests over 30 connections is
20 waves of 200 ms. Single-flight collapses misses per JVM, the distributed
lock collapses them across the cluster, and stale-while-revalidate is the only
one where nobody waits for the database.

### 2. Sustained traffic

300 req/s for 10 seconds on the hot key with a 2 s TTL. This is the only fair
way to measure probabilistic early refresh, which spreads refreshes across
reads instead of reacting to a miss.

| | DB queries | Slower than 50 ms | p99 | p99.9 |
|---|---:|---:|---:|---:|
| Naive cache-aside | 235 | 235 / 3000 | 304 ms | 409 ms |
| Single-flight | 12 | 192 / 3000 | 182 ms | 204 ms |
| Distributed lock | 4 | 207 / 3000 | 190 ms | 213 ms |
| Stale-while-revalidate | 4 | 0 / 3000 | 3 ms | 7 ms |
| Probabilistic early refresh (beta=1) | 14 | 14 / 3000 | 3 ms | 206 ms |

The lock cuts queries to one per expiry but roughly 50 requests still wait each
time. XFetch makes nobody wait; only the requests that happen to recompute pay
one query, at the cost of refreshing earlier than strictly necessary.
Stale-while-revalidate wins on both axes because it accepts a little staleness.

### 3. TTL jitter

A warm-up job writes 2 000 keys at once, then 3 000 req/s hit random keys for
10 seconds with a 20 ms query. Both runs hit the same database ceiling
(3 x 10 connections / 20 ms = 150 queries per 100 ms); the difference is how
long the queue lasts.

| TTL | DB queries | Peak per 100 ms | p99 | max |
|---|---:|---:|---:|---:|
| Fixed 3 s | 4833 | 150 | 315 ms | 357 ms |
| 3 s + [0, 1 s] jitter | 4010 | 148 | 28 ms | 45 ms |

Database queries per 500 ms bucket:

```
fixed     0   0   0   0   0 206 744 741 622 111  41  35 290 443 492 365 228 110 142 263   0   0
jitter    0   0   0   0   0  12 381 693 515 250 113  47  48 139 325 441 436 283 195 132   0   0
```

With a fixed TTL the database sits at its ceiling for about 1.5 seconds and the
second wave arrives as a block again, because every reloaded key gets the same
TTL. Jitter is a one-line change and combines with every other strategy here.

### 4. Invalidation race

A reader loads the old row, a writer updates the row and deletes the key, then
the slow reader writes the old row back. The race is replayed deterministically
with latches.

| | Price in database | Price in cache | Stale? |
|---|---:|---:|---|
| Delete on write | 150 | 100 | yes, until the TTL ends |
| Versioned write | 150 | 150 | no |

With delete-on-write the stale row survives until the TTL ends. With versioned
writes every cache write goes through a Lua script that refuses to overwrite a
newer version.

## Project layout

```
src/main/java/com/onurcanogul/stampede
├── Lab.java                        entry point, runs the scenarios
├── redis/                          minimal RESP client with a bounded pool
├── domain/                         Product and its cache encoding
├── db/                             simulated database, per-instance pools, query stats
├── time/                           ManualClock for TTL logic without sleeping
├── cache/
│   ├── strategy/                   naive, single-flight, distributed lock, SWR, XFetch
│   ├── lock/                       SET NX PX lock with token-checked release
│   └── write/                      delete-on-write vs versioned write
├── metrics/                        latency percentiles
├── report/                         table output
└── scenario/                       the four measured scenarios
src/main/resources/lua              release-lock.lua, versioned-set.lua
```

## Strategies

| Strategy | What it does | Trade-off |
|----------|--------------|-----------|
| Naive cache-aside | GET, miss, query, SET | every concurrent miss hits the database |
| Single-flight | concurrent misses for a key share one load per JVM | one query per instance per expiry |
| Distributed lock | the lock holder loads, others poll Redis | one query per cluster, but everyone waits |
| Stale-while-revalidate | soft TTL in the value, hard TTL on the key; stale reads trigger one background refresh | serves stale data for up to one query duration |
| Probabilistic early refresh | each read recomputes early with a probability that grows near expiry (XFetch, beta = 1) | more queries than a lock, nobody waits |
| TTL jitter | `base + random[0, maxJitter]` on every write | none; combine with any of the above |
| Versioned write | writers store the new value; every cache write is version-checked in Lua | needs a row version |

The lock is for reducing duplicate work, not for correctness: a GC pause longer
than the lock TTL can let two holders run at once, and here that only costs one
extra query. Do not protect money with it.

## Design notes

- No runtime dependencies. A small RESP client talks to Redis so every network
  round trip stays visible. Use Lettuce or Jedis in a real project.
- The clock is injected (`java.time.Clock`). Soft TTL and XFetch logic are
  tested by advancing a `ManualClock`, not by sleeping.
- Locks are released with a Lua script that checks the token. A plain `DEL`
  would remove a lock that expired and was re-acquired by someone else.
- Cached values start with the row version so the versioned-set script can
  compare versions without decoding the payload.

## License

[MIT](LICENSE)
