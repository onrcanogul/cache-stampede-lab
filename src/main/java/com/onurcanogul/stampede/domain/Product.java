package com.onurcanogul.stampede.domain;

/**
 * The encoded form starts with the row version so that the versioned-set
 * script can compare versions without decoding the whole payload.
 */
public record Product(long id, long version, long price, String name) {

    private static final String SEPARATOR = "|";

    public Product {
        if (name.contains(SEPARATOR)) {
            throw new IllegalArgumentException("product name must not contain '" + SEPARATOR + "'");
        }
    }

    public Product withPrice(long newPrice) {
        return new Product(id, version + 1, newPrice, name);
    }

    public String encode() {
        return version + SEPARATOR + id + SEPARATOR + price + SEPARATOR + name;
    }

    public static Product decode(String raw) {
        String[] fields = raw.split("\\|", 4);
        if (fields.length != 4) {
            throw new IllegalArgumentException("malformed product payload: " + raw);
        }
        return new Product(
                Long.parseLong(fields[1]),
                Long.parseLong(fields[0]),
                Long.parseLong(fields[2]),
                fields[3]);
    }
}
