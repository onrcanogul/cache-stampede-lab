package com.onurcanogul.stampede.db;

import com.onurcanogul.stampede.domain.Product;

import java.util.concurrent.CountDownLatch;

/**
 * Lets a test hold a reader between "row read from the database" and
 * "row returned to the caller", which is exactly the window an invalidation
 * race needs.
 */
public final class GatedProductRepository implements ProductRepository {

    private final ProductRepository delegate;
    private final CountDownLatch rowRead = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);

    public GatedProductRepository(ProductRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Product findById(long id) {
        Product row = delegate.findById(id);
        rowRead.countDown();
        await(resume);
        return row;
    }

    @Override
    public void save(Product product) {
        delegate.save(product);
    }

    public void awaitRowRead() {
        await(rowRead);
    }

    public void resumeReader() {
        resume.countDown();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting at the gate", e);
        }
    }
}
