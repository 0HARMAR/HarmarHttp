package org.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PostRequestHandler {
    private static final int MAX_BODY_SIZE = 1024 * 1024;

    public void handle(OutputStream output, HttpRequest request, InputStream rawInput) throws IOException {
        // check whether support file type
        if (!isContentTypeSupported(request)) {
            HarmarHttpServer.sendError(output, 415, "Unsupported Media Type",
                    "Only application/x-www-form-urlencoded and application/json are supported");
            return;
        }

        // get request body
        byte[] body = readRequestBody(rawInput, request);
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

    private byte[] readRequestBody(InputStream input, HttpRequest request) throws IOException {
        int contentLength = getContentLength(request.headers);
        if (contentLength <= 0 || contentLength > MAX_BODY_SIZE) {
            return null;
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        int totalRead = 0;

        while(totalRead < contentLength &&
                (bytesRead = input.read(data, 0, Math.min(data.length, contentLength - totalRead))) != -1) {
            buffer.write(data, 0, bytesRead);
            totalRead += bytesRead;
        }

        return buffer.toByteArray();
    }

    private int getContentLength(List<String> headers) {
        for (String header : headers) {
            if (header.toLowerCase().startsWith("content-length")) {
                try {
                    return Integer.parseInt(header.substring(15).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private boolean isContentTypeSupported(HttpRequest request) {
        String contentType = getContentType(request.headers);
        return "application/x-www-form-urlencoded".equals(contentType) ||
                "application/json".equals(contentType);
    }

    private String getContentType(List<String> headers) {
        for (String header : headers) {
            if (header.toLowerCase().startsWith("content-type")) {
                return header.substring(13).split(";")[0].trim();
            }
        }
        return null;
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
