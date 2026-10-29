package com.onurcanogul.stampede.scenario;

import com.onurcanogul.stampede.domain.Product;

import java.util.ArrayList;
import java.util.List;

final class Catalog {

    static final long HOT_PRODUCT_ID = 42L;

    private Catalog() {
    }

    static Product hotProduct() {
        return new Product(HOT_PRODUCT_ID, 1, 100, "Mechanical keyboard");
    }

    static List<Product> products(int count) {
        List<Product> products = new ArrayList<>(count);
        for (long id = 1; id <= count; id++) {
            products.add(new Product(id, 1, 100 + id, "Product " + id));
        }
        return products;
    }
}
