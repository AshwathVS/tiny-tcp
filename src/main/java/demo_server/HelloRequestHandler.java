package demo_server;

import model.InternalServerResponse;
import model.ServerRequest;
import server.interfaces.IRequestRouteHandler;

import java.util.concurrent.CompletableFuture;

public class HelloRequestHandler implements IRequestRouteHandler {
    @Override
    public CompletableFuture<InternalServerResponse> handleRequest(ServerRequest serverRequest) {
        return CompletableFuture.completedFuture(new InternalServerResponse(("Hello, World!").getBytes()));
    }
}
