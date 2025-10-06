package model;

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
    // First 8 bytes carry lengths: [headerLen:int][bodyLen:int]
    private final byte[] headerPrefix = new byte[8];
    private int headerPos = 0; // how many of the first 8 bytes we have

    private int headerLength = -1;
    private int bodyLength = -1;

    // After both lengths are known, we allocate a single payload buffer of size headerLen + bodyLen
    private byte[] payload;
    private int payloadWritePos = 0;

    public void append(ByteBuffer buffer) {
        // 1) Fill the 8-byte header prefix first
        if (headerPos < 8) {
            int need = 8 - headerPos;
            int n = Math.min(need, buffer.remaining());
            buffer.get(headerPrefix, headerPos, n);
            headerPos += n;

            // parse header length if first 4 bytes available
            if (headerLength == -1 && headerPos >= 4) {
                headerLength = ByteBuffer.wrap(headerPrefix, 0, 4).getInt();
            }
            // parse body length and allocate payload if both ints available
            if (bodyLength == -1 && headerPos >= 8) {
                bodyLength = ByteBuffer.wrap(headerPrefix, 4, 4).getInt();
                payload = new byte[headerLength + bodyLength];
            }
        }

        // 2) Copy remaining bytes into payload (if allocated)
        if (payload != null && buffer.hasRemaining() && payloadWritePos < payload.length) {
            int n = Math.min(buffer.remaining(), payload.length - payloadWritePos);
            buffer.get(payload, payloadWritePos, n);
            payloadWritePos += n;
        }
    }

    public boolean isHeaderLengthRead() {
        return headerPos >= 4; // first integer value is header length
    }

    public void parseHeaderLength() {
        if (headerLength == -1 && isHeaderLengthRead()) {
            headerLength = ByteBuffer.wrap(headerPrefix, 0, 4).getInt();
        }
    }

    public boolean isBodyLengthRead() {
        return headerPos >= 8; // first integer value is header length and the second one is body length (total 2 integers, 8 bytes)
    }

    public void parseBodyLength() {
        if (bodyLength == -1 && isBodyLengthRead()) {
            bodyLength = ByteBuffer.wrap(headerPrefix, 4, 4).getInt();
            if (payload == null) {
                payload = new byte[headerLength + bodyLength];
            }
        }
    }

    public boolean isComplete() {
        return headerLength >= 0 && bodyLength >= 0 && payload != null && payloadWritePos >= payload.length;
    }

    public byte[] extractHeaderBytes() {
        byte[] header = new byte[headerLength];
        System.arraycopy(payload, 0, header, 0, headerLength);
        return header;
    }

    public byte[] extractBodyBytes() {
        byte[] body = new byte[bodyLength];
        System.arraycopy(payload, headerLength, body, 0, bodyLength);
        return body;
    }
}