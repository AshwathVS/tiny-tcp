package server;

import lombok.extern.slf4j.Slf4j;
import model.RequestAccumulator;
import server.interfaces.IRequestHandler;

import java.io.IOException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

@Slf4j
public record AcceptCompletionHandler(AsynchronousServerSocketChannel serverSocketChannel) implements CompletionHandler<AsynchronousSocketChannel, IRequestHandler> {

    @Override
    public void completed(AsynchronousSocketChannel socketChannel, IRequestHandler requestHandler) {
        this.serverSocketChannel.accept(requestHandler, this);
        log.info("Client connected");
        var bufferPool = ByteBufferPool.getInstance();
        var readBuffer = bufferPool.get();

        try {
            ReadCompletionHandler readCompletionHandler = new ReadCompletionHandler(socketChannel, requestHandler, readBuffer);
            socketChannel.read(readBuffer, new RequestAccumulator(), readCompletionHandler);
        } catch (Exception t) {
            log.error("Failed to start read for new client: {}", t.getMessage(), t);
            bufferPool.returnBuffer(readBuffer);
            try {
                socketChannel.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    @Override
    public void failed(Throwable exc, IRequestHandler requestHandler) {
        log.error("Exception while awaiting client connection", exc);
    }
}
