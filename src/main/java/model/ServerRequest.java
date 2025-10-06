package model;

import lombok.Builder;

import java.io.ByteArrayInputStream;
import java.util.Map;

@Builder
public record ServerRequest(
    String path,
    Map<String, String> headers,
    ByteArrayInputStream requestBody
) {
}
