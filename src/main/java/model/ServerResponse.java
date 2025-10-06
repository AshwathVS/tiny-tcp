package model;

public record ServerResponse(
    ServerRequest serverRequest,
    InternalServerResponse internalServerResponse,
    boolean stayAlive
) {
    public byte[] getBytes() {
        return internalServerResponse.getResponseBody();
    }

    public int size() {
        return internalServerResponse.getResponseBody().length;
    }

    public int statusCode() {
        return internalServerResponse.getStatusCode();
    }
}
