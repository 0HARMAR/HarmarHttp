package org.example.connection;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface ReadHandler {
    void onRead(ByteBuffer buffer);
}
