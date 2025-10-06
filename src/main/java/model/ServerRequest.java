package model;

import lombok.Builder;

import java.util.Map;

@Builder
public record ServerRequest(
    String path,
    Map<String, String> headers,
    byte[] requestBody
) {
}
