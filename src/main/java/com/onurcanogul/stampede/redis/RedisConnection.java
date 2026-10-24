package com.onurcanogul.stampede.redis;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class RedisConnection implements Closeable {

    private static final byte[] CRLF = {'\r', '\n'};

    private final Socket socket;
    private final BufferedOutputStream out;
    private final RespReader reader;

    RedisConnection(RedisConfig config) throws IOException {
        int timeoutMillis = (int) config.timeout().toMillis();
        socket = new Socket();
        socket.connect(new InetSocketAddress(config.host(), config.port()), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        socket.setTcpNoDelay(true);
        out = new BufferedOutputStream(socket.getOutputStream());
        reader = new RespReader(new BufferedInputStream(socket.getInputStream()));
    }

    Object execute(String... command) throws IOException {
        writeCommand(command);
        return reader.read();
    }

    private void writeCommand(String[] command) throws IOException {
        out.write(('*' + Integer.toString(command.length)).getBytes(StandardCharsets.US_ASCII));
        out.write(CRLF);
        for (String argument : command) {
            byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
            out.write(('$' + Integer.toString(bytes.length)).getBytes(StandardCharsets.US_ASCII));
            out.write(CRLF);
            out.write(bytes);
            out.write(CRLF);
        }
        out.flush();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing useful to do with a failing close
        }
    }
}
