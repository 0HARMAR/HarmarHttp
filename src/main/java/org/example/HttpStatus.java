package org.example;

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