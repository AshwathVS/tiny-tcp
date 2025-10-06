package server;

import model.ApplicationConfig;
import server.interfaces.IRequestHandler;
import server.interfaces.IServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;

public class AsyncServer implements IServer {
    private final ApplicationConfig.ServerProperties serverProperties;
    private final AsynchronousServerSocketChannel serverSocketChannel;
    private final IRequestHandler requestHandler;

    public AsyncServer(ApplicationConfig config, IRequestHandler requestHandler) throws IOException {
        this.serverProperties = config.serverProperties();
        this.serverSocketChannel = AsynchronousServerSocketChannel.open();
        this.requestHandler = requestHandler;
    }

    @Override
    public void start() throws IOException {
        serverSocketChannel.bind(new InetSocketAddress(serverProperties.port()));
        serverSocketChannel.accept(this.requestHandler, new AcceptCompletionHandler(serverSocketChannel));
    }

    @Override
    public void close() throws IOException {
        try {
            serverSocketChannel.close();
            requestHandler.close();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
