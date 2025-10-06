package server.interfaces;

import model.RequestAccumulator;
import model.ServerResponse;

import java.util.concurrent.CompletableFuture;

public interface IRequestHandler extends AutoCloseable {
    CompletableFuture<ServerResponse> handleRequest(RequestAccumulator requestBody);
}
