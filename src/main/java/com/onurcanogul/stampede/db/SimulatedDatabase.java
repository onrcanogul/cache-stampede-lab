package com.onurcanogul.stampede.db;

import com.onurcanogul.stampede.domain.Product;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the real database: every statement takes a fixed amount of
 * time and the shared counters make the load visible. Access goes through a
 * per-instance {@link #connectionPool(int)} so the connection limit is part of
 * the picture too.
 */
public final class SimulatedDatabase {

    private final Map<Long, Product> rows = new ConcurrentHashMap<>();
    private final Duration statementLatency;
    private final QueryStats stats = new QueryStats();

    public SimulatedDatabase(Duration statementLatency) {
        this.statementLatency = statementLatency;
    }

    public void seed(Collection<Product> products) {
        products.forEach(product -> rows.put(product.id(), product));
    }

    public void seed(Product... products) {
        for (Product product : products) {
            rows.put(product.id(), product);
        }
    }

    public Product peek(long id) {
        return rows.get(id);
    }

    public QueryStats stats() {
        return stats;
    }

    public ProductRepository connectionPool(int connections) {
        return new PooledProductRepository(this, connections);
    }

    Product select(long id) {
        stats.begin();
        try {
            pause();
            Product row = rows.get(id);
            if (row == null) {
                throw new NoSuchElementException("product " + id + " does not exist");
            }
            return row;
        } finally {
            stats.end();
        }
    }

    void update(Product product) {
        stats.begin();
        try {
            pause();
            rows.put(product.id(), product);
        } finally {
            stats.end();
        }
    }

    private void pause() {
        try {
            Thread.sleep(statementLatency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while executing a statement", e);
        }
    }
}
