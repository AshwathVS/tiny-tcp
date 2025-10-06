package server;

import server.interfaces.IRequestRouteConfiguration;
import server.interfaces.IRequestRouteHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RequestRouteConfiguration implements IRequestRouteConfiguration {
    private final Map<String, IRequestRouteHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void addHandler(String path, IRequestRouteHandler requestRouteHandler) {
        handlers.put(path, requestRouteHandler);
    }

    @Override
    public IRequestRouteHandler getHandler(String path) {
        if (path == null || !handlers.containsKey(path)) {
            throw new IllegalArgumentException("No handler found for path: " + path);
        }

        return handlers.get(path);
    }
}
