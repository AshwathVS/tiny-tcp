package server.interfaces;

import model.RequestAccumulator;
import model.ServerRequest;

public interface IRequestParser {
    ServerRequest parseRequest(RequestAccumulator requestContent);
}
