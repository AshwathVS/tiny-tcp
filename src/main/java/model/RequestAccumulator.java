package model;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * [HeaderLength: 4 bytes][BodyLength: 4 bytes][Header bytes][Body bytes]
 * Inside header bytes:
 * [PathLength: 2 bytes][Path bytes][HeaderCount: 2 bytes][Header1][Header2]...
 * Each header entry:
 * [KeyLength: 2 bytes][Key bytes][ValueLength: 2 bytes][Value bytes]
 *
 */
public class RequestAccumulator {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private Integer headerLength = null;
    private Integer bodyLength = null;

    public void append(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        outputStream.write(bytes, 0, bytes.length);
    }

    public boolean isHeaderLengthRead() {
        return outputStream.size() >= 4;
    }

    public void parseHeaderLength() {
        if (headerLength == null && isHeaderLengthRead()) {
            ByteBuffer buf = ByteBuffer.wrap(outputStream.toByteArray(), 0, 4);
            headerLength = buf.getInt();
        }
    }

    public boolean isBodyLengthRead() {
        return outputStream.size() >= 8;
    }

    public void parseBodyLength() {
        if (bodyLength == null && isBodyLengthRead()) {
            ByteBuffer buf = ByteBuffer.wrap(outputStream.toByteArray(), 4, 4);
            bodyLength = buf.getInt();
        }
    }

    public boolean isComplete() {
        return headerLength != null && bodyLength != null &&
            outputStream.size() >= 8 + headerLength + bodyLength;
    }

    public byte[] extractHeaderBytes() {
        byte[] all = outputStream.toByteArray();
        byte[] header = new byte[headerLength];
        System.arraycopy(all, 8, header, 0, headerLength);
        return header;
    }

    public byte[] extractBodyBytes() {
        byte[] all = outputStream.toByteArray();
        byte[] body = new byte[bodyLength];
        System.arraycopy(all, 8 + headerLength, body, 0, bodyLength);
        return body;
    }
}