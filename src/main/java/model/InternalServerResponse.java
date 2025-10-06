package model;

import lombok.Getter;

@Getter
public class InternalServerResponse {
    private final int statusCode;
    private final byte[] responseBody;

    public InternalServerResponse(int statusCode, byte[] responseBody) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
