package server;

import lombok.extern.slf4j.Slf4j;
import model.RequestAccumulator;
import model.ServerResponse;
import server.interfaces.IRequestHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

@Slf4j
public record WriteCompletionHandler(
    AsynchronousSocketChannel channel,
    IRequestHandler requestHandler,
    ByteBuffer writeBuffer
) implements CompletionHandler<Integer, ServerResponse> {

    private void returnBufferToPool() {
        final ByteBufferPool bufferPool = ByteBufferPool.getInstance();
        if (writeBuffer.capacity() == bufferPool.getBufferSize()) {
            bufferPool.returnBuffer(writeBuffer);
        }
    }

    @Override
    public void completed(Integer result, ServerResponse response) {
        returnBufferToPool();

        if (response.stayAlive()) {
            // schedule next read with a fresh buffer from pool
            ByteBuffer nextReadBuffer = ByteBufferPool.getInstance().get();

            ReadCompletionHandler readHandler = new ReadCompletionHandler(channel, requestHandler, nextReadBuffer);
            channel.read(nextReadBuffer, new RequestAccumulator(), readHandler);
        } else {
            try {
                channel.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Override
    public void failed(Throwable exc, ServerResponse response) {
        returnBufferToPool();
        try {
            channel.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
