package com.onurcanogul.stampede.cache.write;

import com.onurcanogul.stampede.cache.CacheKeys;
import com.onurcanogul.stampede.cache.TtlPolicy;
import com.onurcanogul.stampede.db.ProductRepository;
import com.onurcanogul.stampede.domain.Product;

public final class VersionedProductWriter implements ProductWriter {

    private final ProductRepository repository;
    private final CacheWriter cacheWriter;
    private final TtlPolicy ttl;

    public VersionedProductWriter(ProductRepository repository, CacheWriter cacheWriter, TtlPolicy ttl) {
        this.repository = repository;
        this.cacheWriter = cacheWriter;
        this.ttl = ttl;
    }

    @Override
    public void update(Product product) {
        repository.save(product);
        cacheWriter.write(CacheKeys.product(product.id()), product, ttl.next());
    }
}
