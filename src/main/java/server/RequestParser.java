package server;

import model.RequestAccumulator;
import model.ServerRequest;
import server.interfaces.IRequestParser;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * [HeaderLength: 4 bytes][BodyLength: 4 bytes][Header bytes][Body bytes]
 * Inside header bytes:
 * [PathLength: 2 bytes][Path bytes][HeaderCount: 2 bytes][Header1][Header2]...
 * Each header entry:
 * [KeyLength: 2 bytes][Key bytes][ValueLength: 2 bytes][Value bytes]
 *
 */
public class RequestParser implements IRequestParser {
    @Override
    public ServerRequest parseRequest(RequestAccumulator requestContent) {
        // Extract header and body bytes
        byte[] headerBytes = requestContent.extractHeaderBytes();
        byte[] bodyBytes = requestContent.extractBodyBytes();

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);

        // --- Parse path ---
        short pathLength = buffer.getShort(); // 2 bytes for path length
        byte[] pathBytes = new byte[pathLength];
        buffer.get(pathBytes);
        String path = new String(pathBytes, StandardCharsets.UTF_8);

        // --- Parse headers ---
        short headerCount = buffer.getShort(); // 2 bytes for number of headers
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < headerCount; i++) {
            short keyLen = buffer.getShort();
            byte[] keyBytes = new byte[keyLen];
            buffer.get(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);

            short valLen = buffer.getShort();
            byte[] valBytes = new byte[valLen];
            buffer.get(valBytes);
            String value = new String(valBytes, StandardCharsets.UTF_8);

            headers.put(key, value);
        }

        // --- Build ServerRequest ---
        return ServerRequest.builder()
            .path(path)
            .headers(headers)
            .requestBody(bodyBytes)
            .build();
    }
}
