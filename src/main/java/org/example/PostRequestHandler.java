package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.example.HttpHeaderParser.getContentLength;
import static org.example.HttpHeaderParser.getContentType;

public class PostRequestHandler {
    private static final int MAX_BODY_SIZE = 1024 * 1024;

    public void handle(OutputStream output, HttpRequest request, BufferedReader reader) throws IOException {
        // check whether support file type
        if (!isContentTypeSupported(request)) {
            HarmarHttpServer.sendError(output, 415, "Unsupported Media Type",
                    "Only application/x-www-form-urlencoded and application/json are supported");
            return;
        }

        // get request body
        char[] bodyChar = readRequestBody(reader, request);
        String bodyStr = new String(bodyChar);
        byte[] body = bodyStr.getBytes(StandardCharsets.UTF_8);

        if (body == null) {
            HarmarHttpServer.sendError(output, 413, "Payload Too Large",
                    "Request body exceeds 1MB size limit");
            return;
        }

        // according to path route process
        if ("/api/echo".equals(request.path)) {
            handleEcho(output, body, request);
        } else if ("/api/login".equals(request.path)) {
            handleLogin(output, body, request);
        } else {
            HarmarHttpServer.sendError(output, 404, "Not Found",
                    "No Post Handler for path: " +  request.path);
        }
    }

    private char[] readRequestBody(BufferedReader reader, HttpRequest request) throws IOException {
        int contentLength = getContentLength(request.headers);
        if (contentLength <= 0 || contentLength > MAX_BODY_SIZE) {
            return null;
        }

        char[] buffer = new char[contentLength];
        reader.read(buffer, 0, contentLength);

        System.out.println("buffer: ");
        for (int i = 0; i < contentLength; i++) {
            System.out.print(buffer[i]);
        }

        return buffer;
    }


    private boolean isContentTypeSupported(HttpRequest request) {
        String contentType = getContentType(request.headers);
        return "application/x-www-form-urlencoded".equals(contentType) ||
                "application/json".equals(contentType);
    }

    private void handleEcho(OutputStream output, byte[] body, HttpRequest request) throws IOException {
        String contentType = getContentType(request.headers);
        HarmarHttpServer.sendResponse(output, 200, "OK", contentType, body);
    }

    private void handleLogin(OutputStream output, byte[] body, HttpRequest request) throws IOException {
        String requestBody = new String (body, StandardCharsets.UTF_8);

        String response = String.format("{\"status\":\"success\",\"message\":\"Login processed: %s\"",
                requestBody.replace("\"", "\\\""));

        HarmarHttpServer.sendJson(output, 200, "OK", response);
    }
}
