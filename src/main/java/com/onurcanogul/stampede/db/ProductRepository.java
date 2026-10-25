package com.onurcanogul.stampede.db;

import com.onurcanogul.stampede.domain.Product;

public interface ProductRepository {

    Product findById(long id);

    void save(Product product);
}
