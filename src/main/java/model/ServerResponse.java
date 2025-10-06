package model;

public record ServerResponse(
    ServerRequest serverRequest,
    InternalServerResponse internalServerResponse,
    boolean stayAlive
) {
    public byte[] getBytes() {
        return internalServerResponse.responseBody();
    }

    public int size() {
        return internalServerResponse.responseBody().length;
    }
}
