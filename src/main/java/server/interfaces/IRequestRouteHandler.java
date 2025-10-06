package server.interfaces;

import model.InternalServerResponse;
import model.ServerRequest;

import java.util.concurrent.CompletableFuture;

public interface IRequestRouteHandler {
    CompletableFuture<InternalServerResponse> handleRequest(ServerRequest serverRequest);
}
