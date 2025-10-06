package server.interfaces;

import java.io.Closeable;
import java.io.IOException;

public interface IServer extends Closeable {
    void start() throws IOException;
}
