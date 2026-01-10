package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.example.HttpHeaderParser.getContentLength;
import static org.example.HttpHeaderParser.getContentType;

public class PostRequestHandler {
    private static final int MAX_BODY_SIZE = 1024 * 1024;

    public void handle(HttpResponse response, HttpRequest request) throws IOException {
        // check whether support file type
        if (!isContentTypeSupported(request)) {
            byte[] content = "Only application/x-www-form-urlencoded and application/json are supported"
                    .getBytes(StandardCharsets.UTF_8);

            ResponseBody body = new ResponseBody();
            body.addChunk(content);
            body.end();

            response.setStatus(HttpStatus.MEDIA_TYPE_NOT_SUPPORTED);
            response.setBody(body);

            response.setDefaultHeaders();
            response.setHeader("Content-Type", "text/html; charset=utf8");
            response.setHeader("Content-Length", String.valueOf(content.length));

            response.send();

            return;
        }

        // get request body
        byte[] body = request.body;

        if (body == null) {
            byte[] content = "Request body exceeds 1MB size limit"
                    .getBytes(StandardCharsets.UTF_8);

            ResponseBody responseBody = new ResponseBody();
            responseBody.addChunk(content);
            responseBody.end();

            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE);
            response.setBody(responseBody);

            response.setDefaultHeaders();
            response.setHeader("Content-Type", "text/html; charset=utf8");
            response.setHeader("Content-Length", String.valueOf(content.length));

            response.send();

            return;
        }
    }

    private boolean isContentTypeSupported(HttpRequest request) {
        String contentType = getContentType(request.headers);
        return "application/x-www-form-urlencoded".equals(contentType) ||
                "application/json".equals(contentType);
    }

}
