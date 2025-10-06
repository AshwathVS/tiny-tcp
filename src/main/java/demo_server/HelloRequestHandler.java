package demo_server;

import model.InternalServerResponse;
import model.ServerRequest;
import server.interfaces.IRequestRouteHandler;

import java.util.concurrent.CompletableFuture;

public class HelloRequestHandler implements IRequestRouteHandler {
    @Override
    public CompletableFuture<InternalServerResponse> handleRequest(ServerRequest serverRequest) {
        var requestBody = new String(serverRequest.requestBody());
        var requestHeaders = serverRequest.headers();
        return CompletableFuture.completedFuture(new InternalServerResponse(("Hello from server, your body was: [%s], your headers was: [%s]".formatted(requestBody, requestHeaders)).getBytes()));
    }
}
