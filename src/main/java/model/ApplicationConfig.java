package model;

public record ApplicationConfig(ServerProperties serverProperties, ByteBufferProperties byteBufferProperties) {
    public record ServerProperties(int port, int maxWorkerThreads) {}
    public record ByteBufferProperties(int minPoolSize, int bufferSize, int maxPoolSize) {}
}
