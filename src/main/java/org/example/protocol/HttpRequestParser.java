package org.example.protocol;

import org.example.HttpHeaderParser;
import org.example.HttpRequest;

import java.io.BufferedReader;
import java.io.IOException;

public class HttpRequestParser {
    public static HttpRequest parseRequest(BufferedReader reader) throws IOException {
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {return null;}

        String[] parts = requestLine.split("\\s+");
        if (parts.length < 2) {return null;}

        HttpRequest request = new HttpRequest();
        request.method = parts[0];
        request.path =  parts[1];
        request.protocol = parts.length > 2 ? parts[2] : "HTTP/1.0";

        request.headers = HttpHeaderParser.parseHeaders(reader);
        if ("POST".equalsIgnoreCase(request.method)) {
            request.hasBody = true;
        }

        // TODO parse request body
        return request;
    }
}
