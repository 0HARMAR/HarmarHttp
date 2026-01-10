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

}
