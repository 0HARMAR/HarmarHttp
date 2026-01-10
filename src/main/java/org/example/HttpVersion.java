package org.example;

public enum HttpVersion {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1");

    private final String text;

    HttpVersion(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
