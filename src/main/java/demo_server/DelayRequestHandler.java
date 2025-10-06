package demo_server;

import model.InternalServerResponse;
import model.ServerRequest;
import server.interfaces.IRequestRouteHandler;

import java.util.concurrent.CompletableFuture;

public class DelayRequestHandler implements IRequestRouteHandler {
    @Override
    public CompletableFuture<InternalServerResponse> handleRequest(ServerRequest serverRequest) {
        var delay = serverRequest.headers().getOrDefault("Delay", "0");
        try {
            Thread.sleep(Integer.parseInt(delay));
        } catch (InterruptedException e) {
            // do nothing
        }

        return CompletableFuture.completedFuture(new InternalServerResponse(200, ("Waited for " + delay + "ms").getBytes()));
    }
}
