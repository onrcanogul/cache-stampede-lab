package com.onurcanogul.stampede.redis;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Minimal RESP client with a bounded connection pool. Every call is a plain
 * blocking round trip so each network hop stays visible in the measurements.
 */
public final class RedisClient implements AutoCloseable {

    private final RedisConfig config;
    private final Semaphore permits;
    private final ConcurrentLinkedQueue<RedisConnection> idle = new ConcurrentLinkedQueue<>();

    private RedisClient(RedisConfig config) {
        this.config = config;
        this.permits = new Semaphore(config.poolSize(), true);
    }

    public static RedisClient connect(RedisConfig config) {
        RedisClient client = new RedisClient(config);
        client.ping();
        return client;
    }

    public RedisConfig config() {
        return config;
    }

    public String ping() {
        return (String) execute("PING");
    }

    public String get(String key) {
        return (String) execute("GET", key);
    }

    public void set(String key, String value, Duration ttl) {
        execute("SET", key, value, "PX", millis(ttl));
    }

    public boolean setNx(String key, String value, Duration ttl) {
        return "OK".equals(execute("SET", key, value, "PX", millis(ttl), "NX"));
    }

    public long del(String key) {
        return (Long) execute("DEL", key);
    }

    public long pttl(String key) {
        return (Long) execute("PTTL", key);
    }

    public void flushDb() {
        execute("FLUSHDB");
    }

    public Object eval(RedisScript script, List<String> keys, List<String> args) {
        try {
            return execute(scriptCommand("EVALSHA", script.sha1(), keys, args));
        } catch (RedisException e) {
            if (e.getMessage() == null || !e.getMessage().startsWith("NOSCRIPT")) {
                throw e;
            }
            return execute(scriptCommand("EVAL", script.source(), keys, args));
        }
    }

    public Object execute(String... command) {
        acquirePermit(command[0]);
        RedisConnection connection = idle.poll();
        try {
            if (connection == null) {
                connection = new RedisConnection(config);
            }
            Object reply = connection.execute(command);
            idle.offer(connection);
            return reply;
        } catch (RedisException e) {
            idle.offer(connection);
            throw e;
        } catch (IOException e) {
            if (connection != null) {
                connection.close();
            }
            throw new RedisException(command[0] + " failed against " + config.address(), e);
        } finally {
            permits.release();
        }
    }

    @Override
    public void close() {
        RedisConnection connection;
        while ((connection = idle.poll()) != null) {
            connection.close();
        }
    }

    private void acquirePermit(String command) {
        try {
            if (!permits.tryAcquire(config.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new RedisException("no pooled connection available for " + command);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisException("interrupted while waiting for a connection", e);
        }
    }

    private static String[] scriptCommand(String name, String script, List<String> keys, List<String> args) {
        List<String> command = new ArrayList<>(3 + keys.size() + args.size());
        command.add(name);
        command.add(script);
        command.add(Integer.toString(keys.size()));
        command.addAll(keys);
        command.addAll(args);
        return command.toArray(String[]::new);
    }

    private static String millis(Duration duration) {
        return Long.toString(duration.toMillis());
    }
}
