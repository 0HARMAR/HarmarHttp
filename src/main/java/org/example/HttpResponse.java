package org.example;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class HttpResponse {
    public enum HttpStatus {
        OK(200, "OK"),
        CREATED(201, "Created"),
        NO_CONTENT(204, "No Content"),
        MOVED_PERMANENTLY(301, "Moved Permanently"),
        FOUND(302, "Found"),
        NOT_MODIFIED(304, "Not Modified"),
        BAD_REQUEST(400, "Bad Request"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        NOT_FOUND(404, "Not Found"),
        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
        MEDIA_TYPE_NOT_SUPPORTED(415, "Media Type Not Supported"),
        TOO_MANY_REQUESTS(429, "Too Many Requests"),
        INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
        NOT_IMPLEMENTED(501, "Not Implemented"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable");

        public final int code;
        public final String message;

        HttpStatus(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public static HttpStatus fromCode(int code) {
            for (HttpStatus status : HttpStatus.values()) {
                if (status.code == code) {return status;}
            }
            return null;
        }
    }

    private int statusCode;

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    private String statusMessage;
    private final Map<String, String> headers = new HashMap<>();
    private byte[] body;

    public HttpResponse(int statusCode) {
        this.statusCode = statusCode;
        this.statusMessage = HttpStatus.fromCode(statusCode).toString();
        setDefaultheaders();
    }

    public void setDefaultheaders() {
        setHeader("Date", getHttpDate());
        setHeader("Server", "HemarHttpServer");
        setHeader("X-Content-Type-Options", "nosniff");

        setConnection("close");
    }

    public void removeHeader(String headerName) {
        headers.remove(headerName);
    }

    public void setContent(byte[] body, String contentType) {
        this.body = body;
        setHeader("Content-Type", contentType);
        setHeader("Content-Length", String.valueOf(body.length));
    }

    public void setContent(String body, String contentType) {
        setContent(body.getBytes(StandardCharsets.UTF_8), contentType + "; charset=UTF-8");
    }

    public void setJsonContent(String json) {
        setContent(json, "application/json");
    }

    private void setConnection(String type) {
        setHeader("Connection", type);
    }

    public void enableKeepAlive(int timeout) {
        setConnection("keep-alive");
        setHeader("Keep-Alive", "timeout=" + String.valueOf(timeout));
    }

    public void setCacheControl(int directive) {
        setHeader("Cache-Control", directive + "");
    }

    public void setRedirect(String location, boolean permanent) {
        statusCode = permanent ? 301 : 302;
        statusMessage = HttpStatus.fromCode(statusCode).toString();
        setHeader("Location", location);
    }

    public void send(OutputStream output) throws IOException {
        // 构造头部
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.0 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            header.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        header.append("\r\n"); // 头部结束

        // 写头部
        output.write(header.toString().getBytes(StandardCharsets.ISO_8859_1));

        // 写正文（支持二进制）
        if (body != null && body.length > 0) {
            output.write(body);
        }

        output.flush();
    }

    private static String getHttpDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    public void setHeader(String headerName, String headerValue) {
        headers.put(headerName, headerValue);
    }
}
