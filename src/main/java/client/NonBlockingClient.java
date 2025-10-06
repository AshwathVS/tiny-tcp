package client;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@Slf4j
public class NonBlockingClient implements Client {
    public void start(int port, Scanner scanner) {
        try (SocketChannel socket = SocketChannel.open()) {
            socket.configureBlocking(false);
            socket.connect(new InetSocketAddress("localhost", port));

            Selector selector = Selector.open();

            if (!socket.finishConnect()) {
                socket.register(selector, SelectionKey.OP_CONNECT);
            } else {
                socket.register(selector, SelectionKey.OP_WRITE);
            }

            while (true) {
                if (selector.select() > 0) {
                    var keys = selector.selectedKeys();
                    for (var key : keys) {
                        if (key.isConnectable()) {
                            handleConnect(key);
                        } else if (key.isWritable()) {
                            boolean shouldContinue = write(key, scanner);
                            if (!shouldContinue) {
                                return; // exit requested
                            }
                        } else if (key.isReadable()) {
                            read(key);
                        }
                    }
                    keys.clear();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        var socket = (SocketChannel) key.channel();
        if (socket.finishConnect()) {
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private boolean write(SelectionKey key, Scanner scanner) throws IOException {
        var socketChannel = (SocketChannel) key.channel();
        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("exit")) {
            socketChannel.close();
            return false;
        }

        // Parse console input -> path, headers, body
        var req = parseInput(input);
        ByteBuffer frame = RequestEncoder.encode(req.path, req.headers, req.body);

        socketChannel.write(frame);
        // Prepare to read a length-prefixed response next
        key.attach(new ResponseAccumulator());
        key.interestOps(SelectionKey.OP_READ);
        return true;
    }

    private void read(SelectionKey key) throws IOException {
        var socketChannel = (SocketChannel) key.channel();
        var buffer = ByteBuffer.allocate(4096);
        int bytesRead = socketChannel.read(buffer);

        if (bytesRead == -1) {
            socketChannel.close();
            return;
        }

        buffer.flip();
        ResponseAccumulator acc = (ResponseAccumulator) key.attachment();
        if (acc == null) {
            // Should not happen, but avoid NPEs
            acc = new ResponseAccumulator();
            key.attach(acc);
        }
        acc.append(buffer);

        if (acc.isComplete()) {
            String response = new String(acc.getBody(), StandardCharsets.UTF_8);
            log.info("Response from server: {}", response);
            key.attach(null);
            key.interestOps(SelectionKey.OP_WRITE);
        } else {
            // keep reading
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private record ParsedRequest(String path, Map<String, String> headers, byte[] body) {
    }

    // Input format: "PATH | k1=v1;k2=v2 | body text"
    // Sections after PATH are optional. Use '|' to separate headers and body.
    private static ParsedRequest parseInput(String input) {
        String[] parts = input.split("\\|", 3);
        String path = parts[0].trim();
        if (path.isEmpty()) throw new IllegalArgumentException("Path cannot be empty");

        Map<String, String> headers = new HashMap<>();
        headers.put("Keep-Alive", "true");
        byte[] body = new byte[0];

        if (parts.length >= 2) {
            String headerPart = parts[1].trim();
            if (!headerPart.isEmpty()) {
                String[] pairs = headerPart.split(";");
                for (String p : pairs) {
                    String s = p.trim();
                    if (s.isEmpty()) continue;
                    int eq = s.indexOf('=');
                    if (eq <= 0) {
                        // header without value -> treat as empty value
                        headers.put(s, "");
                    } else {
                        String k = s.substring(0, eq).trim();
                        String v = s.substring(eq + 1).trim();
                        headers.put(k, v);
                    }
                }
            }
        }
        if (parts.length == 3) {
            body = parts[2].getBytes(StandardCharsets.UTF_8);
        }

        return new ParsedRequest(path, headers, body);
    }

    // Accumulates a length-prefixed response: [len:int][bytes...]
    private static final class ResponseAccumulator {
        private final byte[] lenPrefix = new byte[4];
        private int lenPos = 0;
        private int bodyLen = -1;
        private byte[] body;
        private int bodyPos = 0;

        void append(ByteBuffer src) {
            // Fill length prefix first
            if (lenPos < 4 && src.hasRemaining()) {
                int need = 4 - lenPos;
                int n = Math.min(need, src.remaining());
                src.get(lenPrefix, lenPos, n);
                lenPos += n;
                if (lenPos == 4) {
                    bodyLen = ByteBuffer.wrap(lenPrefix).getInt();
                    body = new byte[bodyLen];
                }
            }
            // Fill body
            if (body != null && src.hasRemaining() && bodyPos < body.length) {
                int n = Math.min(src.remaining(), body.length - bodyPos);
                src.get(body, bodyPos, n);
                bodyPos += n;
            }
        }

        boolean isComplete() {
            return body != null && bodyPos >= body.length;
        }

        byte[] getBody() {
            return body != null ? body : new byte[0];
        }
    }
}
