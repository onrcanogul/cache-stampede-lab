package com.onurcanogul.stampede.cache;

import com.onurcanogul.stampede.domain.Product;

public interface ProductCache {

    Product get(long id);
}
