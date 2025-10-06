package server;

import model.ApplicationConfig;
import server.interfaces.IRequestHandler;
import server.interfaces.IServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncServer implements IServer {
    private final ApplicationConfig.ServerProperties serverProperties;
    private final AsynchronousServerSocketChannel serverSocketChannel;
    private final IRequestHandler requestHandler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final long connectionIdleTimeoutMs;

    public AsyncServer(ApplicationConfig config, IRequestHandler requestHandler) throws IOException {
        this.serverProperties = config.serverProperties();
        this.serverSocketChannel = AsynchronousServerSocketChannel.open();
        this.requestHandler = requestHandler;
        this.connectionIdleTimeoutMs = serverProperties.connectionIdleTimeoutMs();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void start() throws IOException {
        serverSocketChannel.bind(new InetSocketAddress(serverProperties.port()));
        serverSocketChannel.accept(this.requestHandler, new AcceptCompletionHandler(serverSocketChannel));
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            try {
                serverSocketChannel.close();
            } catch (Exception ignore) {
                // ignore
            }

            try {
                requestHandler.close();
            } catch (Exception e) {
                throw new IOException(e);
            }
            try {
                scheduler.shutdownNow();
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
