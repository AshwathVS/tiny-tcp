package client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Encodes a request using the framing expected by server.RequestParser:
 * [HeaderLength: 4 bytes][BodyLength: 4 bytes][Header bytes][Body bytes]
 * Header bytes layout:
 * [PathLength: 2 bytes][Path bytes][HeaderCount: 2 bytes][Header1][Header2]...
 * Each header entry:
 * [KeyLength: 2 bytes][Key bytes][ValueLength: 2 bytes][Value bytes]
 */
public final class RequestEncoder {

    private RequestEncoder() {}

    public static ByteBuffer encode(String path, Map<String, String> headers, byte[] body) {
        if (path == null) throw new IllegalArgumentException("path cannot be null");
        if (headers == null) throw new IllegalArgumentException("headers cannot be null");
        if (body == null) body = new byte[0];

        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        if (pathBytes.length > 0xFFFF) {
            throw new IllegalArgumentException("path too long (max 65535 bytes in UTF-8)");
        }

        // compute header bytes size
        int headerBytesSize = getHeaderBytesSize(headers, pathBytes);

        // Full frame size
        int frameSize = 4 /*HeaderLength*/ + 4 /*BodyLength*/ + headerBytesSize + body.length;

        ByteBuffer buf = ByteBuffer.allocate(frameSize);

        // Write lengths
        buf.putInt(headerBytesSize);
        buf.putInt(body.length);

        // Write header bytes
        buf.putShort((short) pathBytes.length);
        buf.put(pathBytes);
        buf.putShort((short) headers.size());

        for (Map.Entry<String, String> e : headers.entrySet()) {
            byte[] k = e.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] v = e.getValue() == null ? new byte[0] : e.getValue().getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) k.length);
            buf.put(k);
            buf.putShort((short) v.length);
            buf.put(v);
        }

        // Write body bytes
        buf.put(body);

        buf.flip();
        return buf;
    }

    private static int getHeaderBytesSize(Map<String, String> headers, byte[] pathBytes) {
        int headerBytesSize = 0;
        headerBytesSize += 2; // PathLength
        headerBytesSize += pathBytes.length; // Path bytes
        headerBytesSize += 2; // HeaderCount

        // For each header: 2 + keyLen + 2 + valLen
        for (Map.Entry<String, String> e : headers.entrySet()) {
            byte[] k = e.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] v = e.getValue() == null ? new byte[0] : e.getValue().getBytes(StandardCharsets.UTF_8);
            if (k.length > 0xFFFF) throw new IllegalArgumentException("header key too long: " + e.getKey());
            if (v.length > 0xFFFF) throw new IllegalArgumentException("header value too long for key: " + e.getKey());
            headerBytesSize += 2 + k.length + 2 + v.length;
        }
        return headerBytesSize;
    }
}
