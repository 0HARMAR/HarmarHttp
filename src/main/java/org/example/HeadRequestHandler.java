package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

// note : HEAD request no request body and response body
public class HeadRequestHandler {
    public void handle(OutputStream output, HttpRequest request) throws IOException {
        Map<String, String> headers = request.headers;
        String path = request.path;

        // TODO if path not exist or not register router,return 404
    }
}
