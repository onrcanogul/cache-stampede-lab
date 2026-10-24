package com.onurcanogul.stampede.redis;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RedisScript {

    private final String source;
    private final String sha1;

    public RedisScript(String source) {
        this.source = source;
        this.sha1 = sha1Hex(source);
    }

    public static RedisScript fromResource(String path) {
        try (InputStream in = RedisScript.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("script resource not found: " + path);
            }
            return new RedisScript(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read script " + path, e);
        }
    }

    public String source() {
        return source;
    }

    public String sha1() {
        return sha1;
    }

    private static String sha1Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
