package demo_server;

import lombok.extern.slf4j.Slf4j;
import model.ApplicationConfig;
import server.AsyncServer;
import server.BoundedVirtualThreadExecutor;
import server.ByteBufferPool;
import server.RequestHandler;
import server.RequestParser;
import server.RequestRouteConfiguration;
import server.Utility;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class Main {
    private static ApplicationConfig readConfig(String path) throws IOException {
        try (var in = Main.class.getResourceAsStream(path)) {
            if (in == null) throw new FileNotFoundException("Missing /application.json on classpath");
            return Utility.OBJECT_MAPPER.readValue(in, ApplicationConfig.class);
        }
    }

    public static void main(String[] args) throws IOException {
        var config = readConfig("/application.json");
        ByteBufferPool.initialise(config);

        var requestConfiguration = new RequestRouteConfiguration();
        requestConfiguration.addHandler("/hello", new HelloRequestHandler());
        requestConfiguration.addHandler("/delay", new DelayRequestHandler());

        var latch = new CountDownLatch(1);
        try (var server = new AsyncServer(
                config,
                new RequestHandler(
                        new RequestParser(),
                        requestConfiguration,
                        new BoundedVirtualThreadExecutor(config.serverProperties().maxWorkerThreads())
                )
        )) {
            // graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("Shutting down server...");
                } finally {
                    latch.countDown();
                }
            }));

            server.start();

            try {
                latch.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
