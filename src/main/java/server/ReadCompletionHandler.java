package server;

import lombok.extern.slf4j.Slf4j;
import model.RequestAccumulator;
import server.interfaces.IRequestHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

@Slf4j
public record ReadCompletionHandler(
    AsynchronousSocketChannel channel,
    IRequestHandler requestHandler,
    ByteBuffer readBuffer
) implements CompletionHandler<Integer, RequestAccumulator> {

    @Override
    public void completed(Integer bytesRead, RequestAccumulator accumulator) {
        if (bytesRead == -1) {
            closeSocket();
            return;
        }

        readBuffer.flip();
        accumulator.append(readBuffer);
        readBuffer.clear();

        // parse lengths if not yet parsed
        accumulator.parseHeaderLength();
        accumulator.parseBodyLength();

        if (accumulator.isComplete()) {
            final ByteBufferPool bufferPool = ByteBufferPool.getInstance();

            requestHandler.handleRequest(accumulator)
                .thenAccept(response -> {
                    ByteBuffer writeBuffer;
                    final int responseSize = response.size();
                    final int poolBufferSize = bufferPool.getBufferSize();

                    // choose buffer: pool or temp
                    if (responseSize <= poolBufferSize) {
                        writeBuffer = bufferPool.get();
                    } else {
                        writeBuffer = ByteBuffer.allocate(responseSize);
                    }

                    writeBuffer.clear();
                    writeBuffer.put(response.getBytes());
                    writeBuffer.flip();

                    channel.write(
                        writeBuffer,
                        response,
                        new WriteCompletionHandler(channel, requestHandler, writeBuffer)
                    );
                });

            // Return the read buffer immediately after scheduling the write
            bufferPool.returnBuffer(readBuffer);

        } else {
            // keep reading more bytes until complete
            channel.read(readBuffer, accumulator, this);
        }
    }

    @Override
    public void failed(Throwable exc, RequestAccumulator accumulator) {
        ByteBufferPool.getInstance().returnBuffer(readBuffer);
        closeSocket();
        log.error(exc.getMessage(), exc);
    }

    private void closeSocket() {
        try {
            channel.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
