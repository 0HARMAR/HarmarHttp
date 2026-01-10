package org.example;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ResponseBody {
    private final ConcurrentLinkedQueue<ByteBuffer> chunks = new ConcurrentLinkedQueue<>();

    private volatile boolean end = false;

    public void end() {
        end = true;
    }

    public void addChunk(byte[] data) {
        chunks.add(ByteBuffer.wrap(data));
    }

    ByteBuffer poll() {
        return chunks.poll();
    }

    boolean isEnd() {
        return end;
    }
}
