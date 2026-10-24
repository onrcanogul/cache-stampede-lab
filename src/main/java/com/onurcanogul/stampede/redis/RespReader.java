package com.onurcanogul.stampede.redis;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class RespReader {

    private final InputStream in;

    RespReader(InputStream in) {
        this.in = in;
    }

    Object read() throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new EOFException("connection closed by Redis");
        }
        return switch (type) {
            case '+' -> readLine();
            case '-' -> throw new RedisException(readLine());
            case ':' -> Long.parseLong(readLine());
            case '$' -> readBulkString();
            case '*' -> readArray();
            default -> throw new RedisException("unexpected RESP type: " + (char) type);
        };
    }

    private String readBulkString() throws IOException {
        int length = Integer.parseInt(readLine());
        if (length < 0) {
            return null;
        }
        byte[] payload = in.readNBytes(length);
        if (payload.length != length) {
            throw new EOFException("truncated bulk string");
        }
        expectCrlf();
        return new String(payload, StandardCharsets.UTF_8);
    }

    private List<Object> readArray() throws IOException {
        int size = Integer.parseInt(readLine());
        if (size < 0) {
            return null;
        }
        List<Object> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(read());
        }
        return items;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(32);
        int b;
        while ((b = in.read()) != '\r') {
            if (b < 0) {
                throw new EOFException("connection closed mid-reply");
            }
            line.write(b);
        }
        expectLf();
        return line.toString(StandardCharsets.UTF_8);
    }

    private void expectCrlf() throws IOException {
        if (in.read() != '\r') {
            throw new RedisException("protocol error: expected CR");
        }
        expectLf();
    }

    private void expectLf() throws IOException {
        if (in.read() != '\n') {
            throw new RedisException("protocol error: expected LF");
        }
    }
}
