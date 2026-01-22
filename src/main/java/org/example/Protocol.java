package org.example;

public enum Protocol {
    HTTP1_0("HTTP/1.0"),
    HTTP1_1("HTTP/1.1"),
    HTTP2_PLAINTEXT("HTTP/2 Plaintext"),
    HTTP1_1_OVER_TLS("HTTP/1.1 over TLS"),
    HTTP2_OVER_TLS("HTTP/2 over TLS");

    private final String name;

    Protocol(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

