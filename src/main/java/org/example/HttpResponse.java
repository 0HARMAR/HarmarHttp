package org.example;

import java.util.*;

public class HttpResponse {

    private HttpVersion httpVersion = HttpVersion.HTTP_1_1;
    private HttpStatus status;
    private final Map<String, String> headers = new HashMap<>();
    private ResponseBody body;

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setDefaultHeaders() {
        setHeader("Server", "HarmarHttpServer");
        setHeader("Date", new Date().toString());
    }

    public void setBody(ResponseBody body) {
        this.body = body;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }

    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    public void send() {

    }

    public ResponseBody getBody() {
        return body;
    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] toBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append(httpVersion.toString()).append(" ").append(status.toString()).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }

        sb.append("\r\n");
        byte[] bodyBytes = body != null ? body.toBytes() : new byte[0];
        sb.append(new String(bodyBytes));
        return sb.toString().getBytes();
    }

}
