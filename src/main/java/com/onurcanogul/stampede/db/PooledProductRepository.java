package com.onurcanogul.stampede.db;

import com.onurcanogul.stampede.domain.Product;

import java.util.concurrent.Semaphore;

final class PooledProductRepository implements ProductRepository {

    private final SimulatedDatabase database;
    private final Semaphore connections;

    PooledProductRepository(SimulatedDatabase database, int poolSize) {
        this.database = database;
        this.connections = new Semaphore(poolSize, true);
    }

    @Override
    public Product findById(long id) {
        acquire();
        try {
            return database.select(id);
        } finally {
            connections.release();
        }
    }

    @Override
    public void save(Product product) {
        acquire();
        try {
            database.update(product);
        } finally {
            connections.release();
        }
    }

    private void acquire() {
        try {
            connections.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a connection", e);
        }
    }
}
