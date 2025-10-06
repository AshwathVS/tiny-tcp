package server.interfaces;

public interface IRequestRouteConfiguration {
    void addHandler(String path, IRequestRouteHandler requestRouteHandler);
    IRequestRouteHandler getHandler(String path);
}
