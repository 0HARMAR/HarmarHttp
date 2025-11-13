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
            HarmarHttpServer.sendResponse(output, HttpResponse.HttpStatus.MEDIA_TYPE_NOT_SUPPORTED.code,
                    HttpResponse.HttpStatus.MEDIA_TYPE_NOT_SUPPORTED.message, "text/html; charset=utf8",
                    "Only application/x-www-form-urlencoded and application/json are supported".getBytes());
            return;
        }

        // get request body
        char[] bodyChar = readRequestBody(reader, request);
        String bodyStr = new String(bodyChar);
        byte[] body = bodyStr.getBytes(StandardCharsets.UTF_8);

        if (body == null) {
            HarmarHttpServer.sendResponse(output, HttpResponse.HttpStatus.PAYLOAD_TOO_LARGE.code,
                    HttpResponse.HttpStatus.PAYLOAD_TOO_LARGE.message, "text/html; charset=utf8",
                    "Request body exceeds 1MB size limit".getBytes());
            return;
        }

        // according to path route process
        if ("/api/echo".equals(request.path)) {
            handleEcho(output, body, request);
        } else if ("/api/login".equals(request.path)) {
            handleLogin(output, body, request);
        } else {
            HarmarHttpServer.sendResponse(output, HttpResponse.HttpStatus.NOT_FOUND.code,
                    HttpResponse.HttpStatus.NOT_MODIFIED.message, "text/html; charset=utf8",
                    ("No Post Handler for path: " +  request.path).getBytes());
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

    }

    private void handleLogin(OutputStream output, byte[] body, HttpRequest request) throws IOException {
        String requestBody = new String (body, StandardCharsets.UTF_8);

        String response = String.format("{\"status\":\"success\",\"message\":\"Login processed: %s\"",
                requestBody.replace("\"", "\\\""));

        HarmarHttpServer.sendResponse(output, HttpResponse.HttpStatus.OK.code,
                HttpResponse.HttpStatus.OK.message, "application/json", response.getBytes());
    }
}
