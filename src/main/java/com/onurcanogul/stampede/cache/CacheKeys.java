package com.onurcanogul.stampede.cache;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String product(long id) {
        return "product:" + id;
    }

    public static String lock(String key) {
        return key + ":lock";
    }

    public static String refresh(String key) {
        return key + ":refresh";
    }
}
