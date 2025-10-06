package server;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Utility {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Utility() {
        // do nothing
    }
}
