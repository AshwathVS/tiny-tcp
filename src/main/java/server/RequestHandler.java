package server;

import lombok.extern.slf4j.Slf4j;
import model.InternalServerResponse;
import model.RequestAccumulator;
import model.ServerResponse;
import server.interfaces.IRequestHandler;
import server.interfaces.IRequestParser;
import server.interfaces.IRequestRouteConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public record RequestHandler(IRequestParser requestParser, IRequestRouteConfiguration requestRouteConfiguration, ExecutorService executorService) implements IRequestHandler {
    private static final byte[] BASE_ERROR_RESPONSE = "Unhandled server error".getBytes(StandardCharsets.UTF_8);
    private static final String KEEP_ALIVE = "Keep-Alive";

    @Override
    public CompletableFuture<ServerResponse> handleRequest(RequestAccumulator requestBody) {
        var requestContext = requestParser.parseRequest(requestBody);
        var stayAlive = "true".equalsIgnoreCase(requestContext.headers().getOrDefault(KEEP_ALIVE, "false"));
        return CompletableFuture
            .supplyAsync(() -> requestRouteConfiguration.getHandler(requestContext.path()), executorService)
            .thenCompose(requestRouteHandler -> requestRouteHandler.handleRequest(requestContext))
            .thenApply(internalServerResponse -> new ServerResponse(requestContext, internalServerResponse, stayAlive))
            .exceptionally(ex -> {
                log.error(ex.getMessage(), ex);
                var internalServerResponse = new InternalServerResponse(500, BASE_ERROR_RESPONSE);
                return new ServerResponse(requestContext, internalServerResponse, stayAlive);
            });
    }

    @Override
    public void close() {
        this.executorService.shutdown();
        try {
            if (!this.executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                this.executorService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            this.executorService.shutdownNow();
        }
    }
}
